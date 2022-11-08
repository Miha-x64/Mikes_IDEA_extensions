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
import com.intellij.openapi.diagnostic.logger
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
import net.aquadc.mike.plugin.indexOfFirst
import net.aquadc.mike.plugin.toInt
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.math.BigDecimal
import java.util.*
import kotlin.math.absoluteValue
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
    private var subAreas: MutableList<Area?>? = null
    private var opaqueArea: Area? = null
    private var filledSubPaths: BitSet? = null
    private var strokedSubPaths: BitSet? = null
    private var clippedAwaySubPaths: BitSet? = null
    private var overdrawnSubPaths: BitSet? = null

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
            val pathData = rr.resolve(rawPathData) ?: return null

            val floatRanges = IntArrayList()

            // can't report on sub-paths somewhere in strings.xml or wherever
            val pathStarts = if (rawPathData == pathData) IntArrayList() else null
            val endPositions = if (rawPathData == pathData) FloatArrayList() else null
            val tag = pathAttr.parentOfType<XmlTag>()!!

            val paint = if (tag.name == "path") PathPaintAttrs(tag, holder, rr) else null
            val evenOdd = paint?.fillTypeEvenOdd == true
            val paths = SmartList<Path2D.Float>().also {
                PathDelegate.parse(pathData, it, pathStarts, floatRanges, endPositions, usefulPrecision, evenOdd)
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

            val beginValueAt = pathAttr.text.indexOf(rawPathData)

            holder.tryProposeTrimming(floatRanges, pathData, usefulPrecision, pathAttr, rawPathData, beginValueAt)

            if (matrix != null) paths.forEach { it.transform(matrix) }
            return PathTag(pathAttr, paths, subPathRanges, startPoss, endPoss, floatRanges, beginValueAt, paint)
        }

        /** Handles intersections which can lead to “donut holes” and invert the whole sub-path meaning. */
        private fun merge(
            paths: SmartList<Path2D.Float>, pathStarts: IntArrayList?, endPositions: FloatArrayList?, /*TODO*/evenOdd: Boolean,
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

        private fun ProblemsHolder.tryProposeTrimming(
            floatRanges: IntArrayList,
            pathData: String,
            usefulPrecision: Int,
            pathAttr: XmlAttributeValue,
            rawPathData: String,
            beginValueAt: Int
        ) {
            val rangeNodeCount = floatRanges.size
            var canTrimCarefully = false
            var trimmableFloats: IntArrayList? = null
            for (i in 0 until rangeNodeCount step 2) {
                val iod = pathData.indexOf('.', floatRanges.getInt(i))
                if (iod < 0 || iod > floatRanges.getInt(i + 1)) continue
                val precision = floatRanges.getInt(i + 1) - iod
                if (precision > usefulPrecision + 1) {
                    (trimmableFloats ?: IntArrayList().also { trimmableFloats = it }).apply {
                        add(floatRanges.getInt(i))
                        add(floatRanges.getInt(i + 1))
                    }
                    if (precision > usefulPrecision + 2) {
                        canTrimCarefully = true  //   ^ plus dot plus extra digit
                    }
                }
            }

            trimmableFloats?.takeIf { canTrimCarefully || isOnTheFly }?.let { tfs ->
                registerProblem(
                    pathAttr,
                    "subpixel precision" + if (tfs.size == 2) "" else " (${tfs.size / 2} items)",
                    ProblemHighlightType.WEAK_WARNING,
                    if (pathData === rawPathData)
                        TextRange(beginValueAt + tfs.getInt(0), beginValueAt + tfs.getInt(tfs.size - 1))
                    else TextRange.from(beginValueAt, rawPathData.length),
                    if (isOnTheFly) TrimFix(tfs, "Trim aggressively", usefulPrecision, pathData) else null,
                    if (canTrimCarefully) TrimFix(tfs, "Trim carefully", usefulPrecision + 1, pathData) else null,
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

        var filledSubPaths = if (paint.fillOpacity == PixelFormat.TRANSPARENT) null else BitSet(outlines.size)
        var strokedSubPaths = if (stroke == null) null else BitSet(outlines.size)
        if (filledSubPaths == null && strokedSubPaths == null) {
            return holder.report(pathTag, "Invisible path: no fill, no stroke", removeTagFix)
        }

        val strokeCaps = if (stroke == null || sCap == null) null else BitSet(outlines.size)
        val strokeJoins = if (stroke == null || sJoin == null) null else BitSet(outlines.size)

        var reallyEvenOdd = false

        var opaque: Area? = null
        val areas = outlines.mapIndexedTo(ArrayList(outlines.size)) { index, outline ->
            val strokeArea = stroke?.createStrokedShape(outline)
                ?.also {
                    if (strokeCaps != null || strokeJoins != null)
                        checkStroke(outline, strokeCaps, strokeJoins, index)
                }
                ?.toArea()
                ?.takeIf { !it.effectivelyEmpty(usefulPrecision) }
                ?.also { strokedSubPaths!!.set(index) }
            val opaqueStrokeArea = strokeArea
                ?.takeIf { paint.strokeOpacity == PixelFormat.OPAQUE }
                ?.also { opaque += it }

            val fillArea = outline.takeIf { filledSubPaths != null }
                ?.toArea()?.without(opaqueStrokeArea) // stroke is drawn on top of fill
                ?.takeIf { !it.effectivelyEmpty(usefulPrecision) }
                ?.also { fillArea ->
                    filledSubPaths!!.set(index)
                    if (paint.fillOpacity == PixelFormat.OPAQUE) opaque += fillArea
                    if (paint.fillTypeEvenOdd && !reallyEvenOdd)
                        if (!outline.also { it.windingRule = Path2D.WIND_NON_ZERO }.toArea().equals(fillArea))
                            reallyEvenOdd = true
                }

            when {
                fillArea == null && strokeArea == null -> null
                fillArea == null -> strokeArea
                strokeArea == null -> fillArea
                else -> fillArea.also { it.add(strokeArea) }
            }
        }

        val noFill = filledSubPaths == null || filledSubPaths.cardinality() == 0
        val noStroke = strokedSubPaths == null || strokedSubPaths.cardinality() == 0
        if (noFill && noStroke) {
            return holder.report(pathTag, "Invisible path: none of sub-paths are filled or stroked", removeTagFix)
        }

        if (noFill) {
            holder.reportNoFill(
                if (filledSubPaths == null) "attribute has no effect with uncolored or transparent fill"
                else "attribute has no effect with open path",
            )
            filledSubPaths = null
        } else if (paint.fillTypeEvenOdd && !reallyEvenOdd) {
            holder.report(fType!!, "attribute has no effect", removeAttrFix)
        }

        if (noStroke) {
            holder.reportNoStroke()
            strokedSubPaths = null
        } else {
            if (strokeCaps?.cardinality() == 0) holder.report(sCap!!, "attribute has no effect", removeAttrFix)
            if (strokeJoins?.cardinality() == 0) holder.report(sJoin!!, "attribute has no effect", removeAttrFix)
        }

        subAreas = areas
        opaqueArea = opaque
        this.filledSubPaths = filledSubPaths
        this.strokedSubPaths = strokedSubPaths
    }

    fun merged(): Area? = // TODO split clip-paths, too
        outlines.map(::Area).takeIf(List<*>::isNotEmpty)?.reduce { acc, area -> acc.add(area); acc }

    fun applyClip(
        clipPath: Area?, clips: List<Area>, usefulClips: IntOpenHashSet,
        usefulPrecision: Int,
    ) {
        val subAreas = subAreas ?: return
        if (usefulClips.size < clips.size) {
            val clippedAway = Area()
            var index = 0
            while (index < clips.size) {
                val clip = clips[index]
                var areaIdx = 0
                while (index !in usefulClips && areaIdx < subAreas.size) {
                    if (!clippedAway.with(subAreas[areaIdx++]).without(clip).effectivelyEmpty(usefulPrecision) &&
                        usefulClips.add(index) && usefulClips.size == clips.size)
                        break
                    clippedAway.reset()
                }
                index++
            }
        }

        if (clipPath != null) {
            for (i in subAreas.indices) {
                subAreas[i]?.let { subArea ->
                    subArea.intersect(clipPath)
                    if (subArea.effectivelyEmpty(usefulPrecision)) {
                        (clippedAwaySubPaths ?: BitSet().also { clippedAwaySubPaths = it }).set(i)
                        subAreas[i] = null
                    }
                }
            }
            opaqueArea?.intersect(clipPath)
        }
    }

    private fun ProblemsHolder.reportNoFill(complaint: String) {
        paint!!.fillColorEl?.let { report(it, complaint, removeAttrFix) }
        paint.fillTypeEl?.let { report(it, complaint, removeAttrFix) }
        if (paint.fillColorOpacity == PixelFormat.TRANSPARENT)
            paint.fillAlphaEl?.let { report(it, complaint, removeAttrFix) }
        // else fillAlpha=0 solely makes this fill useless.
        // Removing attribute will change drawable contents making it visible.
        // We'll revenge after removing fillColor attribute
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

    fun overdraw(paths: List<PathTag>, usefulPrecision: Int) {
        val opaqueArea = opaqueArea ?: return
        paths.forEach { path ->
            path.subAreas?.let { subAreas ->
                for (i in subAreas.indices) {
                    subAreas[i]?.let { subArea ->
                        subArea.subtract(opaqueArea)
                        if (subArea.effectivelyEmpty(usefulPrecision)) {
                            subAreas[i] = null
                            (path.overdrawnSubPaths ?: BitSet().also { path.overdrawnSubPaths = it }).set(i)
                        }
                    }
                }
            }
        }
    }

    fun report(holder: ProblemsHolder) {
        if (filledSubPaths == null && strokedSubPaths == null)
            return // nothing to do here, the path is crippled from the very beginning

        tryProposeSplit(holder)

        val subAreas = subAreas ?: return
        when (subAreas.count { it == null }) {
            0 -> return // everything is perfect
            subAreas.size -> {
                val what = invisiblePathDiagnosis(
                    (overdrawnSubPaths?.cardinality() ?: 0) > 0,
                    (clippedAwaySubPaths?.cardinality() ?: 0) > 0,
                )
                return holder.report(pathTag, "The path is fully $what", removeTagFix)
            }
        }

        // some areas are empty, some others are not. Analyze!
        for (i in subAreas.indices) {
            // TODO glue sibling paths together
            if (filledSubPaths?.get(i) != true && strokedSubPaths?.get(i) != true) {
                if (!holder.reportSubPath(i, "invisible sub-path"))
                    break
            } else if (clippedAwaySubPaths?.get(i) == true || overdrawnSubPaths?.get(i) == true) {
                val what = invisiblePathDiagnosis(clippedAwaySubPaths?.get(i) == true, overdrawnSubPaths?.get(i) == true)
                if (!holder.reportSubPath(i, "$what sub-path"))
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

    private fun invisiblePathDiagnosis(overdrawn: Boolean, clippedAway: Boolean): String = when {
        overdrawn && clippedAway -> "overdrawn and clipped away"
        overdrawn -> "overdrawn"
        clippedAway -> "clipped away"
        else -> logger<PathTag>().error("invalid invisiblePathMessage(false, false)").let { "" }
    }

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
                removeSubPathFix(pathDataAttr.value, at, subPathRanges, endPositions, floatRanges),
            )
            true
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

private class TrimFix(
    @FileModifier.SafeFieldForPreview private val ranges: IntArrayList,
    name: String,
    private val targetPrecision: Int,
    private val pathData: String,
) : NamedLocalQuickFix(name) {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val value = StringBuilder(pathData)
        val tmpFloat = StringBuilder(10)
        for (i in (ranges.size - 2) downTo 0 step 2) {
            val start = ranges.getInt(i)
            val end = ranges.getInt(i + 1)
            val minus = tmpFloat.replace(0, tmpFloat.length, value, start, end)[0] == '-'
            if (tmpFloat.contains('e')) {
                val bd = BigDecimal(tmpFloat.toString())
                tmpFloat.clear()
                tmpFloat.append(bd.toPlainString())
            }
            if (tmpFloat.trimToPrecision()[0] != '-' && minus && value[start - 1].isDigit())
                tmpFloat.insert(0, ' ')
            value.replace(start, end, tmpFloat)
        }
        (descriptor.psiElement.parent as XmlAttribute).setValue(value.toString())
    }

    private fun StringBuilder.trimToPrecision(): StringBuilder {
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
        run<Unit> { // clean up and carry
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

            if (carry)
                insert(kotlin.math.max(iol, 0), '1')
            else if (isEmpty() || (length == 1 && this[0] == '-')) {
                append('0')
            }
        }

        if (length == 2 && this[0] == '-' && this[1] == '0')
            deleteCharAt(0)

        return this
    }

    private inline val Boolean.asInt get() = if (this) 1 else 0
}

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
