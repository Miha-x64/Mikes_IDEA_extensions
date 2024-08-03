@file:Suppress("NOTHING_TO_INLINE")

package net.aquadc.mike.plugin.android.res

import android.graphics.PathDelegate
import android.graphics.PixelFormat
import com.android.ide.common.resources.ResourceResolver
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import de.javagl.geom.Shapes
import it.unimi.dsi.fastutil.floats.FloatArrayList
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import net.aquadc.mike.plugin.NamedLocalQuickFix
import net.aquadc.mike.plugin.fixes
import net.aquadc.mike.plugin.indexOfFirst
import net.aquadc.mike.plugin.toInt
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


internal class PathTag private constructor(
    private val pathDataAttr: XmlAttributeValue,
    private val outlines: MutableList<Path2D.Float>,
    private val subPathRanges: List<IntArrayList>?,
    private val startPositions: List<FloatArrayList>?,
    private val endPositions: List<FloatArrayList>?,
    private val floatRanges: IntArrayList,
    private val valueShiftInText: Int,
    private val paint: PathPaintAttrs?,
) {
    private val pathTag get() = pathDataAttr.parentOfType<XmlTag>()!!
    private var fills: MutableList<Area?>? = null
    private var strokes: MutableList<Area?>? = null
    private var opaqueArea: Area? = null
//    private var filledSubPaths: BitSet? = null
//    private var strokedSubPaths: BitSet? = null
//    private var clippedAwaySubPaths: BitSet? = null
//    private var overdrawnFills: BitSet? = null
//    private var overdrawnStrokes: BitSet? = null
//    private var underdrawnFills: BitSet? = null
//    private var underdrawnStrokes: BitSet? = null

    companion object {
        private val T000 = Triple(null, null, null)
        fun parse(
            holder: ProblemsHolder,
            rr: ResourceResolver?,
            pathAttr: XmlAttributeValue,
            matrix: AffineTransform?,
            usefulPrecision: Int,
        ): PathTag? {
            val rawPathData = pathAttr.value // FIXME we need value and text, extract them without copying
            val pathData = rr.resolve(rawPathData).second ?: return null

            val floatRanges = IntArrayList()

            // can't report on sub-paths somewhere in strings.xml or wherever
            val pathStarts = if (rawPathData == pathData) IntArrayList() else null
            val endPositions = if (rawPathData == pathData) FloatArrayList() else null
            val tag = pathAttr.parentOfType<XmlTag>()!!

            val paint = if (tag.name == "path") PathPaintAttrs(tag, holder, rr) else null
            val evenOdd = paint?.fillTypeEvenOdd == true
            val paths = SmartList<Path2D.Float>()
            val cmds = ArrayList<Cmd>()
            val beginValueAt = pathAttr.text.indexOf(rawPathData)
            try {
                PathDelegate.parse(pathData, paths, cmds, pathStarts, floatRanges, endPositions, evenOdd)
            } catch (e: PathDelegate.PathError) {
                holder.registerProblem(
                    pathAttr, "Invalid path. ${e.message}", ProblemHighlightType.ERROR,
                    if (pathData === rawPathData) e.at.shiftRight(beginValueAt) else null
                )
                return null
            }
            if (paths.isEmpty())
                return null
            if (pathStarts != null && paths.size != pathStarts.size - 1 &&
                pathStarts.getInt(pathStarts.size - 1) != rawPathData.length) {
                // heal ranges for a broken path, may happen when editing path by hand on the fly
                pathStarts.add(rawPathData.length)
            }
            check(pathStarts == null || paths.size == pathStarts.size - 1) {
                "${paths.size} paths but ${pathStarts!!.size} start indices in path $pathData: $pathStarts"
            }
            val (subPathRanges, startPoss, endPoss) =
                if (paths.size > 1 && paint?.fillOpacity != PixelFormat.TRANSPARENT) {
                    merge(paths, pathStarts, endPositions, evenOdd)
                } else if (paths.size > 1 && pathStarts != null) {
                    val ends = endPositions?.let {
                        List(it.size / 2) { i ->
                            FloatArrayList().apply { add(it.getFloat(2 * i)); add(it.getFloat(2 * i + 1)) }
                        }
                    }
                    Triple(
                        ranges(pathStarts, paths),
                        ends?.let { listOf(FloatArrayList().apply { add(0f); add(0f) }) + it.dropLast(1) },
                        ends,
                    )
                } else if (pathStarts != null) {
                    Triple(
                        listOf(pathStarts.also { check(it.size == 2) }),
                        listOf(FloatArrayList().apply { add(0f); add(0f) }),
                        endPositions?.let(::listOf),
                    )
                } else {
                    T000
                }

            holder.tryProposeShortening(cmds, pathData, usefulPrecision, pathAttr, rawPathData, beginValueAt)

            if (matrix != null) paths.forEach { it.transform(matrix) }
            return PathTag(pathAttr, paths, subPathRanges, startPoss, endPoss, floatRanges, beginValueAt, paint)
        }

        /** Handles intersections which can lead to “donut holes” and invert the whole sub-path meaning. */
        private fun merge(
            paths: SmartList<Path2D.Float>, pathStarts: IntArrayList?, endPositions: FloatArrayList?, /*TODO*/ @Suppress("UNUSED_PARAMETER") evenOdd: Boolean,
        ): Triple<ArrayList<IntArrayList>?, ArrayList<FloatArrayList>?, ArrayList<FloatArrayList>?> {
            val areas = paths.mapTo(ArrayList(), ::Area)
            val ranges: ArrayList<IntArrayList>? = ranges(pathStarts, paths)
            var startPoss: ArrayList<FloatArrayList>? = null
            var endPoss: ArrayList<FloatArrayList>? = null
            if (endPositions != null) {
                startPoss = ArrayList(paths.size)
                endPoss = ArrayList(paths.size)
                startPoss.add(FloatArrayList().also { it.add(0f); it.add(0f) })
                repeat(paths.size - 1) { i ->
                    val p = FloatArrayList(2).apply { add(endPositions.getFloat(2 * i)); add(endPositions.getFloat(2 * i + 1)) }
                    startPoss.add(p)
                    endPoss.add(p.clone())
                }
                val i = paths.size - 1
                endPoss.add(FloatArrayList(2).apply { add(endPositions.getFloat(2 * i)); add(endPositions.getFloat(2 * i + 1)) })
            }
            var i = 0
            while (i < paths.size) {
                var victim: Area? = null
                var j = i + 1
                while (j < paths.size) {
                    if (victim == null) victim = Area(areas[i])
                    victim.subtract(areas[j])
                    if (!areas[i].equals(victim)) {
                        victim = null
                        merge(paths, areas, ranges, startPoss, endPoss, i, j)
                        j = i
                    }
                    j++
                }
                i++
            }
            return Triple(ranges, startPoss, endPoss)
        }
        private fun ranges(
            pathStarts: IntArrayList?,
            paths: SmartList<Path2D.Float>
        ): ArrayList<IntArrayList>? {
            var ranges: ArrayList<IntArrayList>? = null
            if (pathStarts != null)
                ranges = paths.indices.mapTo(ArrayList(paths.size)) { i ->
                    IntArrayList(2).apply { add(pathStarts.getInt(i)); add(pathStarts.getInt(i + 1)) }
                }
            return ranges
        }

        private fun merge(
            paths: SmartList<Path2D.Float>, areas: ArrayList<Area>,
            ranges: ArrayList<IntArrayList>?,
            startPositions: ArrayList<FloatArrayList>?, endPositions: ArrayList<FloatArrayList>?,
            i: Int, j: Int,
        ) {
            paths[i].append(paths[j], false)
            paths.removeAt(j)
            areas[i] = Area(paths[i])
            areas.removeAt(j)
            if (ranges != null) {
                val myRanges = ranges[i]
                val victimRanges = ranges[j]
                if (myRanges.getInt(myRanges.size - 1) == victimRanges.getInt(0)) {
                    myRanges.removeInt(myRanges.size - 1)
                    victimRanges.removeInt(0)
                    startPositions?.get(j)?.removeElements(0, 2)
                    endPositions?.get(i)?.let { it.removeElements(it.size - 2, it.size) }
                }
                myRanges.addAll(victimRanges)
                ranges.removeAt(j)
            }
            if (startPositions != null) {
                startPositions[i].addAll(startPositions[j])
                startPositions.removeAt(j)
            }
            if (endPositions != null) {
                endPositions[i].addAll(endPositions[j])
                endPositions.removeAt(j)
            }
        }

        private fun ProblemsHolder.tryProposeShortening(
            cmds: List<Cmd>,
            pathData: String,
            usefulPrecision: Int,
            pathAttr: XmlAttributeValue,
            rawPathData: String,
            beginValueAt: Int
        ) {
            val maxPrecision = cmds.maxPrecision()
            val canTrimCarefully = maxPrecision > usefulPrecision
            val canTrimAggressively = maxPrecision > usefulPrecision + 1
            val shortened = cmds.shorten()
            val rewritten = StringBuilder().also { shortened.appendTo(it, Int.MAX_VALUE) } // we will re-invoke this in "Compact path" fix but there's not much work to do
            val rewriteStart = pathData.indices.firstOrNull { pathData[it] != rewritten.getOrNull(it) } ?: -1
            val rewriteEnd = pathData.indices
                .firstOrNull { pathData[pathData.length - it - 1] != rewritten.getOrNull(rewritten.length - it - 1) }
                ?.let { pathData.length - it }
                ?: -1

            if (canTrimAggressively || rewriteStart >= 0) {
                val andMaybeShorten = if (rewriteStart >= 0) " & shorten path" else ""
                registerProblem(
                    pathAttr,
                    if (canTrimAggressively) "Subpixel precision" else "Excessive pathData",
                    ProblemHighlightType.WEAK_WARNING,
                    if (pathData === rawPathData) {
                        val highlightStart =
                            min(cmds.firstOrNull { it.maxPrecision() > usefulPrecision }?.firstFloatStart(usefulPrecision) ?: Int.MAX_VALUE, rewriteStart.takeIf { it >= 0 } ?: Int.MAX_VALUE)
                        assert(highlightStart != Int.MAX_VALUE) { "at least one of min() should be valid, got maxPrecision=$maxPrecision, canTrim=$canTrimCarefully || $canTrimAggressively" }
                        val highlightEnd = max(cmds.lastOrNull { it.maxPrecision() > usefulPrecision }?.lastFloatEnd(usefulPrecision) ?: -1, rewriteEnd)
                        assert(highlightEnd != -1) { "at least one of max() should be valid, got maxPrecision=$maxPrecision, canTrim=$canTrimCarefully || $canTrimAggressively" }
                        TextRange(beginValueAt + highlightStart, beginValueAt + highlightEnd)
                    } else TextRange.from(beginValueAt, rawPathData.length),
                    *fixes(
                        // quickfixes are sorted alphabetically by IntelliJ, mind names so order is preserved
                        if (isOnTheFly && canTrimAggressively) OptimizePathFix(shortened, "Reduce precision aggressively$andMaybeShorten", usefulPrecision) else null,
                        if (canTrimCarefully) OptimizePathFix(shortened, "Reduce precision carefully$andMaybeShorten", usefulPrecision + 1) else null,
                        if (rewriteStart >= 0) OptimizePathFix(shortened, "Shorten path", Int.MAX_VALUE) else null,
                    )
                )
            }
        }

        private val dummyFloats = FloatArray(6) // don't mind synchronization, values are never read
    }

    fun toAreas(holder: ProblemsHolder, usefulPrecision: Int) {
        val stroke = paint!!.stroke
        val sCap = paint.strokeLineCapEl
        val sJoin = paint.strokeLineJoinEl
        val fType = paint.fillTypeEl

        var noFill = paint.fillOpacity == PixelFormat.TRANSPARENT
        var noStroke = stroke == null
//        var filledSubPaths = if (noFill) null else BitSet(outlines.size)
//        var strokedSubPaths = if (noStroke) null else BitSet(outlines.size)
        if (noFill && noStroke) {
            return holder.report(pathTag, "Invisible path: no fill, no stroke", removeTagFix)
        }

        val strokeCaps = if (noStroke || sCap == null) null else BitSet(outlines.size)
        val strokeJoins = if (noStroke || sJoin == null) null else BitSet(outlines.size)

        var reallyEvenOdd = false

        var opaque: Area? = null
        var fills = if (noFill) null else ArrayList<Area?>(outlines.size)
        var strokes = if (noStroke) null else ArrayList<Area?>(outlines.size)
        for (index in outlines.indices) {
            val outline = outlines[index]
            val strokeArea = stroke?.createStrokedShape(outline)
                ?.also {
                    if (strokeCaps != null || strokeJoins != null)
                        checkStroke(outline, strokeCaps, strokeJoins, index)
                }
                ?.toArea()
                ?.takeIf { !it.effectivelyEmpty(usefulPrecision) }
//                ?.also { strokedSubPaths!!.set(index) }
            val opaqueStrokeArea = strokeArea
                ?.takeIf { paint.strokeOpacity == PixelFormat.OPAQUE }
                ?.also { opaque += it }

            val fillArea = outline.takeIf { !noFill }
                ?.toArea()?.without(opaqueStrokeArea) // stroke is drawn on top of fill
                ?.takeIf { !it.effectivelyEmpty(usefulPrecision) }
                ?.also { fillArea ->
//                    filledSubPaths!!.set(index)
                    if (paint.fillOpacity == PixelFormat.OPAQUE) opaque += fillArea
                    if (paint.fillTypeEvenOdd && !reallyEvenOdd)
                        if (!outline.also { it.windingRule = Path2D.WIND_NON_ZERO }.toArea().equals(fillArea))
                            reallyEvenOdd = true
                }

            if (fills == null) check(fillArea == null) else fills.add(fillArea)
            if (strokes == null) check(strokeArea == null) else strokes.add(strokeArea)
        }

        if (fills != null && fills.all { it == null }) {
            fills = null
            noFill = true
        }
        if (strokes != null && strokes.all { it == null }) {
            strokes = null
            noStroke = true
        }
        if (noFill && noStroke) {
            return holder.report(pathTag, "Invisible path: none of sub-paths are filled or stroked", removeTagFix)
        }

        if (noFill) {
            holder.reportNoFill("attribute has no effect with unfilled path")
        } else if (paint.fillTypeEvenOdd && !reallyEvenOdd) {
            holder.report(fType!!, "attribute has no effect", removeAttrFix)
        }

        if (noStroke) {
            holder.reportNoStroke()
        } else {
            if (strokeCaps?.cardinality() == 0) holder.report(sCap!!, "attribute has no effect", removeAttrFix)
            if (strokeJoins?.cardinality() == 0) holder.report(sJoin!!, "attribute has no effect", removeAttrFix)
        }

        this.fills = fills
        this.strokes = strokes
        opaqueArea = opaque
//        this.filledSubPaths = filledSubPaths
//        this.strokedSubPaths = strokedSubPaths
    }

    fun merged(): Area? = // TODO split clip-paths, too
        outlines.map(::Area).takeIf(List<*>::isNotEmpty)?.reduce { acc, area -> acc.add(area); acc }

    fun applyClip(
        clipPath: Area?, clips: List<Area>, usefulClips: IntOpenHashSet,
        usefulPrecision: Int,
    ) {
        val fills = fills
        val strokes = strokes
        if (fills == null && strokes == null)
            return

        val subCount = fills?.size ?: strokes!!.size
        if (usefulClips.size < clips.size) {
            val clippedAway = Area()
            for (index in clips.indices) {
                val clip = clips[index]
                var areaIdx = 0
                while (index !in usefulClips && areaIdx < subCount) {
                    if ((fills != null &&
                                !clippedAway.with(fills[areaIdx++]).without(clip).effectivelyEmpty(usefulPrecision) ||
                                strokes != null &&
                                !clippedAway.with(strokes[areaIdx++]).without(clip).effectivelyEmpty(usefulPrecision)
                            ) &&
                        usefulClips.add(index) && usefulClips.size == clips.size)
                        break
                    clippedAway.reset()
                }
            }
        }

        if (clipPath != null) {
            for (i in 0 until subCount) {
                val subFill = fills?.getOrNull(i)
                val subStroke = strokes?.getOrNull(i)
                if (subFill != null || subStroke != null) {
                    if (subFill != null && subFill.also { it.intersect(clipPath) }.effectivelyEmpty(usefulPrecision))
                        fills[i] = null
                    if (subStroke != null && subStroke.also { it.intersect(clipPath) }.effectivelyEmpty(usefulPrecision))
                        strokes[i] = null
                    /*if (fills?.get(i) == null && strokes?.get(i) == null)
                        (clippedAwaySubPaths ?: BitSet().also { clippedAwaySubPaths = it }).set(i)*/
                }
            }
            opaqueArea?.intersect(clipPath)
        }
    }

    private fun ProblemsHolder.reportNoFill(complaint: String) {
        val za = paint!!.fillAlpha == 0f
        val tc = paint.fillColorOpacity == PixelFormat.TRANSPARENT
        if (za) paint.fillColorEl?.let { report(it, complaint, removeAttrFix) }
        if (tc) paint.fillAlphaEl?.let { report(it, complaint, removeAttrFix) }
        // else fillAlpha=0 solely makes this fill useless.
        // Removing attribute will change drawable contents making it visible.
        // We'll revenge after removing fillColor attribute

        paint.fillTypeEl?.let { report(it, complaint, removeAttrFix) }
    }

    private fun ProblemsHolder.reportNoStroke() {
        val za = paint!!.strokeAlpha == 0f
        val tc = paint.strokeColorOpacity == PixelFormat.TRANSPARENT
        val zw = paint.strokeWidth == 0f
        if (tc || za) paint.strokeWidthEl?.let { report(it, "attribute has no effect", removeAttrFix) }
        if (zw || za) paint.strokeColorEl?.let { report(it, "attribute has no effect", removeAttrFix) }
        paint.strokeLineCapEl?.let { report(it, "attribute has no effect", removeAttrFix) }
        paint.strokeLineJoinEl?.let { report(it, "attribute has no effect", removeAttrFix) }
        paint.strokeMiterEl?.let { report(it, "attribute has no effect", removeAttrFix) }
        if (tc || zw) paint.strokeAlphaEl?.let { report(it, "attribute has no effect", removeAttrFix) }
    }

    private fun checkStroke(outline: Path2D, cap: BitSet?, join: BitSet?, index: Int) {
        val iter = outline.getPathIterator(null)
        var prevState: Int
        var state = PathIterator.SEG_CLOSE
        var gaps = false
        var joins = false
        while (!iter.isDone) {
            prevState = state
            state = iter.currentSegment(dummyFloats)
            gaps = gaps || (prevState != PathIterator.SEG_CLOSE && prevState != PathIterator.SEG_MOVETO && state == PathIterator.SEG_MOVETO)
            joins = joins || (prevState != PathIterator.SEG_MOVETO && state != PathIterator.SEG_MOVETO)
            if ((gaps || cap == null) && (joins || join == null)) break
            iter.next()
        }
        gaps = gaps || (state != PathIterator.SEG_CLOSE && state != PathIterator.SEG_MOVETO)
        if (gaps) cap?.set(index)
        if (joins) join?.set(index)
    }

    fun overdraw(paths: List<PathTag>, usefulPrecision: Int, colorToArea: HashMap<String, Area>) {

        // check for underdraw first
        if (fills != null || strokes != null) {
            var fillVisited = false
            var strokeVisited = false
            val tmpArea = Area()
            @Suppress("JavaMapForEach") // come on, original HashMap::forEach is more efficient
            colorToArea.forEach { color, area ->
                fills?.let { fills ->
                    if (colorsEq(color, paint!!.canonicalFillColor)) {
                        fillVisited = true
                        for (i in fills.indices) {
                            val fill = fills[i] ?: continue
                            if (area.equals(tmpArea.with(area).with(fill))) {
                                fills[i] = null
//                                filledSubPaths?.clear(i)
//                                (underdrawnFills ?: BitSet().also { underdrawnFills = it }).set(i)
                            } else if (paint.fillOpacity == PixelFormat.OPAQUE) {
                                area.add(fill)
                            }
                            tmpArea.reset()
                        }
                    } else {
                        for (i in fills.indices) {
                            val fill = fills[i] ?: continue
                            area.subtract(fill)
                        }
                    }
                }
                strokes?.let { strokes ->
                    if (colorsEq(color, paint!!.canonicalStrokeColor)) {
                        strokeVisited = true
                        for (i in strokes.indices) {
                            val stroke = strokes[i] ?: continue
                            if (area.equals(tmpArea.with(area).with(stroke))) {
                                strokes[i] = null
//                                strokedSubPaths?.clear(i)
//                                (underdrawnStrokes ?: BitSet().also { underdrawnStrokes = it }).set(i)
                            } else if (paint.strokeOpacity == PixelFormat.OPAQUE) {
                                area.add(stroke)
                            }
                            tmpArea.reset()
                        }
                    } else {
                        for (i in strokes.indices) {
                            val stroke = strokes[i] ?: continue
                            area.subtract(stroke)
                        }
                    }
                }
            }
            fills?.takeIf { !fillVisited && paint!!.canonicalFillColor != null && paint.fillOpacity == PixelFormat.OPAQUE }?.let { fills ->
                fills.forEach { tmpArea.with(it) }
                if (!tmpArea.isEmpty)
                    colorToArea[paint!!.canonicalFillColor!!] = tmpArea
            }
            strokes?.takeIf { !strokeVisited && paint!!.canonicalStrokeColor != null && paint.strokeOpacity == PixelFormat.OPAQUE }?.let { strokes ->
                val area = tmpArea.takeIf { it.isEmpty || paint!!.canonicalStrokeColor == paint.canonicalFillColor } ?: Area()
                strokes.forEach { area.with(it) }
                if (!area.isEmpty)
                    colorToArea[paint!!.canonicalStrokeColor!!] = area
            }
        }


        if (opaqueArea != null) {
            for (path in paths) {
                val fills = path.fills
                val strokes = path.strokes
                if (fills == null && strokes == null)
                    continue

                val count = fills?.size ?: strokes!!.size
                for (i in 0 until count) {
                    val fill = fills?.getOrNull(i)
                    val stroke = strokes?.getOrNull(i)

                    if (fill != null && fill.without(opaqueArea).effectivelyEmpty(usefulPrecision)) {
                        fills[i] = null
//                        path.filledSubPaths?.clear(i)
//                        (path.overdrawnFills ?: BitSet().also { path.overdrawnFills = it }).set(i)
                    }
                    if (stroke != null && stroke.without(opaqueArea).effectivelyEmpty(usefulPrecision)) {
                        strokes[i] = null
//                        path.strokedSubPaths?.clear(i)
//                        (path.overdrawnStrokes ?: BitSet().also { path.overdrawnStrokes = it }).set(i)
                    }
                }
            }
        }
    }
    private fun colorsEq(c1: String, c2: String?): Boolean =
        c1 == c2 || // check for #??RRGGBB equality
                (c1.length == 9 && c2?.length == 9 && c1[0] == '#' && c2[0] == '#' && c1.regionMatches(3, c2, 3, 6))

    fun report(holder: ProblemsHolder) {
        val subAreas = fills?.size ?: strokes?.size
            ?: return // nothing to do here, the path is crippled from the very beginning

        tryProposeSplit(holder)

        val deadCount =
            (0 until subAreas).count { fills?.get(it) == null && strokes?.get(it) == null }

        when (deadCount) {
            0 -> return // everything is perfect
            subAreas -> {
                /*val what = invisiblePathDiagnosis(
                    (overdrawnSubPaths?.cardinality() ?: 0) > 0,
                    (clippedAwaySubPaths?.cardinality() ?: 0) > 0,
                )*/
                return holder.report(pathTag, "The path is fully invisible"/* TODO due to under-, overdraw, clip*/, removeTagFix)
            }
        }

        // some areas are dead, some others are not. Analyze!
        for (i in 0 until subAreas) {
            // TODO glue sibling paths together
            if (fills?.get(i) == null && strokes?.get(i) == null) {
                if (!holder.reportSubPath(i, "invisible sub-path"))
                    break
            }
        }
    }
    private fun tryProposeSplit(holder: ProblemsHolder) {
        if (holder.isOnTheFly && startPositions != null &&
            subPathRanges != null && subPathRanges.size > 1 &&
            pathTag.name == "path" && pathTag.subTags.isEmpty()) {
            holder.registerProblem(
                pathTag, "", ProblemHighlightType.INFORMATION,
                splitPathFix(subPathRanges, startPositions)
            )
        }
    }
    private fun splitPathFix(
        subPathRanges: List<IntArrayList>, startPositions: List<FloatArrayList>,
    ) = object : NamedLocalQuickFix("Split path") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val tag = descriptor.psiElement as? XmlTag
            val parent = tag?.parentOfType<XmlTag>() ?: return
            val pathData = tag.getAttributeValue("pathData", ANDROID_NS) ?: return
            val splits = subPathRanges.mapIndexed { i, ranges ->
                buildString(pathData.length / subPathRanges.size) {
                    repeat(ranges.size / 2) {
                        val viLen = pathStartReplacement(pathData, floatRanges, ranges.getInt(2 * it), startPositions[i], it)
                        if (viLen < 0) return
                        append(pathData, ranges.getInt(2 * it) + viLen, ranges.getInt(2 * it + 1))
                    }
                }
            }
            tag.setAttribute("pathData", ANDROID_NS, splits.first())

            splits.drop(1).asReversed().forEach { split ->
                val child = parent.createChildTag("path", null, null, false)
                tag.attributes.forEach {
                    child.setAttribute(it.name, it.value)
                }
                child.setAttribute(parent.getPrefixByNamespace(ANDROID_NS) + ":pathData", split)
                parent.addAfter(child, tag)
            }
        }
    }

    /*private fun invisiblePathDiagnosis(overdrawn: Boolean, clippedAway: Boolean): String = when {
        overdrawn && clippedAway -> "overdrawn and clipped away"
        overdrawn -> "overdrawn"
        clippedAway -> "clipped away"
        else -> logger<PathTag>().error("invalid invisiblePathMessage(false, false)").let { "" }
    }*/

    private fun ProblemsHolder.reportSubPath(at: Int, complaint: String): Boolean =
        if (subPathRanges == null) {
            registerProblem(
                pathDataAttr, "pathData contains $complaint", ProblemHighlightType.WEAK_WARNING,
            )
            false
        } else {
            val myRanges = subPathRanges[at]
            val deadRange =
                TextRange.create(valueShiftInText + myRanges.getInt(0), valueShiftInText + myRanges.getInt(myRanges.size - 1))
            registerProblem(
                pathDataAttr, complaint,
                if (myRanges.size == 2) ProblemHighlightType.LIKE_UNUSED_SYMBOL else ProblemHighlightType.WEAK_WARNING,
                deadRange,
                *fixes(removeSubPathFix(pathDataAttr.value, at, subPathRanges, endPositions, floatRanges)),
            )
            true
        }

    fun isBoundedBy(clip: Area): Boolean {
        if (paint!!.strokeOpacity != PixelFormat.TRANSPARENT)
            return false // stroke geometry is different from pathData itself
        return merged()?.also { it.intersect(clip) }?.equals(clip) == true
    }

    private inline fun Area.with(a: Area?) =
        apply { a?.let(this::add) }

    private inline fun Area.without(a: Area?) =
        apply { a?.let(this::subtract) }

    private inline operator fun Area?.plus(other: Area): Area =
        (this ?: Area()).also { it.add(other) }

    private inline fun Shape.toArea(): Area =
        Area(this)

}

private class OptimizePathFix(
    @FileModifier.SafeFieldForPreview private val cmds: List<Cmd>,
    name: String,
    private val targetPrecision: Int,
) : NamedLocalQuickFix(name) {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        (descriptor.psiElement.parent as XmlAttribute)
            .setValue(StringBuilder().also { cmds.appendTo(it, targetPrecision) }.toString())
    }
}

internal fun StringBuilder.trimToPrecision(targetPrecision: Int): StringBuilder {
    var carry = false
    run { // lower precision
        val precision = length - 1 - indexOf('.')
        if (precision > targetPrecision) {
            val trim = precision - targetPrecision
            val iol = lastIndex
            repeat(trim) { i ->
                carry = this[iol - i] - '0' + carry.asInt > 4
            }
            delete(length - trim, length)
        }
    }
    run<Unit> { // clean up and carry fractional part
        var fractional = true
        while (isNotEmpty()) {
            val iol = lastIndex
            val ch = this[iol]
            if (ch == '.') {
                fractional = false
            } else if (fractional && (carry && ch == '9' || !carry && ch == '0')) {
                // carry over to the previous digit
            } else if (fractional && carry && ch in '0'..'8') {
                this[iol]++
                carry = false
                break
            } else break
            deleteCharAt(iol)
        }

        var iol = lastIndex
        if (!fractional) { // carry whole part
            while (iol >= 0 && carry) {
                val ch = this[iol]
                if (ch == '9') {
                    this[iol--] = '0'
                } else if (ch in '0'..'8') {
                    this[iol] = ch + 1
                    carry = false
                } else {
                    check(ch == '-')
                    break
                }
            }
        }

        if (carry) insert(max(iol, 0), '1')
        else if (isEmpty()) append('0')
        else if (length == 1 && this[0] == '-') setCharAt(0, '0') // -0 -> 0
    }

    // drop leading zeroes
    val start = if (this[0] == '-') 1 else 0
    while (length > start + 2 &&
        this[start] == '0' &&
        this[start+1] == '0' /*this[start+1].let { it == '0' || it == '.' }*/)
        deleteCharAt(start) // ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^- this term would allow `.fractional` form.
    // Save one leading zero instead, “to avoid crashes on some pre-Marshmallow devices” Ⓒ InvalidVectorPath lint rule

    if (length == 2 && this[0] == '-' && this[1] == '0')
        deleteCharAt(0)

    return this
}

private inline val Boolean.asInt get() = if (this) 1 else 0

private fun removeSubPathFix(
    pathData: String,
    at: Int,
    subPathRanges: List<IntArrayList>, endPositions: List<FloatArrayList>?, floatRanges: IntArrayList,
): LocalQuickFix? {
    val myRanges = subPathRanges[at]
    val myEndPositions = endPositions?.get(at)
    val replacementRanges = myRanges.clone()
    val sb = StringBuilder()
    val replacements = List(myRanges.size / 2) {
        val endOffset = myRanges.getInt(2 * it + 1)
        sb.clear()
        val victimLen = sb.pathStartReplacement(pathData, floatRanges, endOffset, myEndPositions, it)
        if (victimLen < 0) return null
        replacementRanges[2 * it + 1] = endOffset + victimLen
        sb.toString()
    }
    return object : NamedLocalQuickFix("Remove sub-path") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val attrVal = (descriptor.psiElement as? XmlAttributeValue) ?: return
            val value = attrVal.value
            val fixed =
                if (replacements.size == 1) value.replaceRange(replacementRanges.getInt(0), replacementRanges.getInt(1), replacements[0])
                else StringBuilder(value).apply {
                    for (i in replacements.lastIndex downTo 0)
                        replace(replacementRanges.getInt(2 * i), replacementRanges.getInt(2 * i + 1), replacements[i])
                }.toString()
            (attrVal.parent as XmlAttribute).setValue(fixed)
        }
    }
}

private fun StringBuilder.pathStartReplacement(
    pathData: String,
    floatRanges: IntArrayList,
    pathStartOffset: Int,
    startPositions: FloatArrayList?,
    startPosAt: Int
): Int {
    val pathStart = if (pathStartOffset < pathData.length) pathData[pathStartOffset] else 0.toChar()
    return if (!pathStart.isLetter() || pathStart.isUpperCase()) 0
    else if (pathStart == 'm' && startPositions != null) {
        // So here we are: got path like M1 2m3 4 5 6, wanna get M(1+3) (2+4)l5 6
        // 1. "M1 2" are startPositions of "m3 4 5 6"
        // 2. Find these "3 4"
        var cursor = floatRanges.indexOfFirst { v -> v > pathStartOffset }
        check((cursor and 1) == 0) { // they are pairs
            "The failed failure failingly failed."
        }
        val mx = pathData.substring(floatRanges.getInt(cursor++), floatRanges.getInt(cursor++)).toFloat()
        val my = pathData.substring(floatRanges.getInt(cursor++), floatRanges.getInt(cursor++)).toFloat()
        val x = startPositions.getFloat(2 * startPosAt) + mx
        val y = startPositions.getFloat(2 * startPosAt + 1) + my
        // 3. Whether we need "l"?
        var nextCmd = false
        val lastIndex = if (floatRanges.size > cursor) cursor else {
            // a sub-path starts and ends with a single moveto ...M[x)[y)$
            //                                                          ^ so we stop here
            nextCmd = true // and think that a command follows, so we don't need lineto
            cursor - 1
        }
        for (i in floatRanges.getInt(cursor - 1) until floatRanges.getInt(lastIndex))
            if (pathData[i].isLetter()) {
                nextCmd = true
                break
            }

        append('M')
        if (x == x.toInt().toFloat()) append(x.toInt()) else append(x)
        append(',')
        if (y == y.toInt().toFloat()) append(y.toInt()) else append(y)
        if (!nextCmd) append('l')

        // 4. eat this "m3 4 "
        floatRanges.getInt(min(cursor - nextCmd.toInt(), lastIndex)) - pathStartOffset
    } else -1
}

private fun StringBuilder.replace(
    at: Int, till: Int,
    with: CharSequence, from: Int = 0, to: Int = with.length,
): StringBuilder {
    val victimLen = till - at
    val replLen = to - from
    repeat(min(victimLen, replLen)) { this[at + it] = with[from + it] }
    return if (replLen > victimLen)
        insert(at + victimLen, with, from + victimLen, from + replLen)
    else
        delete(at + replLen, at + victimLen)
}


internal fun Area.effectivelyEmpty(usefulPrecision: Int): Boolean {
    if (isEmpty) return true
    else if (usefulPrecision < 0) return false

    val px = 1 / 10f.pow(usefulPrecision)
    val minArea = 3 * px * px // at least 3 square pixels on xxxxxxxhhhhdpi, maybe?
    return bounds2D.let { it.width < px || it.height < px || it.width * it.height < minArea } ||
            Shapes.computeSubShapes(this).all { Shapes.computeSignedArea(it, 0.0).absoluteValue < minArea }
//  FIXME   ^^^^^^^^^^^^^^^^ this step can be skipped safely under certain conditions
}
