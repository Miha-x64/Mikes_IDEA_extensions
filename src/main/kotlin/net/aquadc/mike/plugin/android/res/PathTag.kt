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
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import de.javagl.geom.Shapes
import gnu.trove.TIntArrayList
import gnu.trove.TIntHashSet
import net.aquadc.mike.plugin.NamedLocalQuickFix
import net.aquadc.mike.plugin.component6
import net.aquadc.mike.plugin.component7
import net.aquadc.mike.plugin.component8
import net.aquadc.mike.plugin.component9
import java.awt.BasicStroke
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.math.BigDecimal
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.pow
import net.aquadc.mike.plugin.miserlyMap as map


internal class PathTag private constructor(
    private val pathDataAttr: XmlAttributeValue,
    private val outlines: MutableList<Path2D.Float>,
    private val subPathRanges: TIntArrayList?,
) {
    private val pathTag get() = pathDataAttr.parentOfType<XmlTag>()!!
    private var subAreas: MutableList<Area?>? = null
    private var opaqueArea: Area? = null
    private var filledSubPaths: BitSet? = null
    private var strokedSubPaths: BitSet? = null
    private var clippedAwaySubPaths: BitSet? = null
    private var overdrawnSubPaths: BitSet? = null

    companion object {
        fun parse(
            holder: ProblemsHolder,
            rr: ResourceResolver?,
            pathAttr: XmlAttributeValue,
            matrix: AffineTransform?,
            usefulPrecision: Int,
        ): PathTag? {
            val rawPathData = pathAttr.value // FIXME we need value and text, extract them without copying
            val pathData = rr.resolve(rawPathData) ?: return null

            val floatRanges = TIntArrayList()

            // can't report on sub-paths somewhere in strings.xml or wherever
            val pathStarts = if (rawPathData == pathData) TIntArrayList() else null
            val evenOdd = pathAttr.parentOfType<XmlTag>()!!.let { tag ->
                tag.name == "path" && // no fillType for clipPath
                        holder.toString(rr, tag.getAttribute("fillType", ANDROID_NS), null) == "evenOdd"
            }
            val paths = SmartList<Path2D.Float>().also {
                PathDelegate.parse(pathData, it, pathStarts, floatRanges, usefulPrecision, evenOdd)
            }
            when (paths.size) {
                0 -> return null
                1 -> {}
                else -> merge(paths, pathStarts, evenOdd)
            }
            check(pathStarts == null || paths.size == pathStarts.size() - 1) {
                "${paths.size} paths but ${pathStarts!!.size()} start indices in path $pathData"
            }

            val beginValueAt = pathAttr.text.indexOf(rawPathData)
            pathStarts?.let { // offset by XML attribute quote (")
                repeat(pathStarts.size()) {
                    pathStarts[it] += beginValueAt
                }
                pathStarts.add(beginValueAt + rawPathData.length)
            }

            if (floatRanges.size() > 0) {
                holder.proposeTrimming(floatRanges, pathData, usefulPrecision, pathAttr, rawPathData, beginValueAt)
            }

            if (matrix != null) paths.forEach { it.transform(matrix) }
            return PathTag(pathAttr, paths, pathStarts)
        }

        /** Handles intersections which can lead to “donut holes” and invert the whole sub-path meaning. */
        private fun merge(paths: SmartList<Path2D.Float>, pathStarts: TIntArrayList?, /*TODO*/evenOdd: Boolean) {
            val areas = paths.mapTo(SmartList(), ::Area)
            var i = 0
            while (i < paths.size) {
                var victim: Area? = null
                var j = i + 1
                while (j < paths.size) {
                    if (victim == null) victim = Area(areas[i])
                    victim.subtract(areas[j])
                    if (!areas[i].equals(victim)) {
                        victim = null
                        merge(paths, areas, pathStarts, i, j)
                        j = i
                    }
                    j++
                }
                i++
            }
        }
        private fun merge(
            paths: SmartList<Path2D.Float>, areas: SmartList<Area>,
            pathStarts: TIntArrayList?,
            from: Int, till: Int,
        ) {
            for (i in (from + 1) .. till) paths[from].append(paths[i], false)
            areas[from] = Area(paths[from])
            val diff = till - from
            if (diff == 1) {
                paths.removeAt(from + 1)
                areas.removeAt(from + 1)
            } else {
                paths.subList(from + 1, till + 1).clear()
                areas.subList(from + 1, till + 1).clear()
            }
            pathStarts?.remove(from + 1, diff)
        }

        private fun ProblemsHolder.proposeTrimming(
            floatRanges: TIntArrayList,
            pathData: String,
            usefulPrecision: Int,
            pathAttr: XmlAttributeValue,
            rawPathData: String,
            beginValueAt: Int
        ) {
            val rangeNodeCount = floatRanges.size()
            val rangeCount = rangeNodeCount / 2
            var canTrimCarefully = false
            for (i in 0 until rangeNodeCount step 2) {
                if (floatRanges[i + 1] - pathData.indexOf('.', floatRanges[i]) > usefulPrecision + 2) {
                    canTrimCarefully = true  //                        plus dot plus extra digit ^^^
                    break
                }
            }

            if (canTrimCarefully || isOnTheFly) {
                registerProblem(
                    pathAttr,
                    "subpixel precision" + if (rangeCount == 1) "" else " ($rangeCount items)",
                    ProblemHighlightType.WEAK_WARNING,
                    if (pathData === rawPathData)
                        TextRange(beginValueAt + floatRanges[0], beginValueAt + floatRanges[rangeNodeCount - 1])
                    else TextRange.from(beginValueAt, rawPathData.length),
                    if (isOnTheFly) TrimFix(floatRanges, "Trim aggressively", usefulPrecision, pathData) else null,
                    if (canTrimCarefully) TrimFix(floatRanges, "Trim carefully", usefulPrecision + 1, pathData) else null,
                )
            }
        }

        private val pathAttrs = arrayOf(
            "fillColor", "fillType", "fillAlpha",
            "strokeColor", "strokeWidth", "strokeLineCap", "strokeLineJoin", "strokeMiterLimit", "strokeAlpha",
        )
        private val dummyFloats = FloatArray(6) // don't mind synchronization, values are never read
        private val nullToZero = null to 0
    }

    fun toAreas(holder: ProblemsHolder, rr: ResourceResolver?, usefulPrecision: Int) {
        val tag = pathTag
        val (fCol, fType, fA, sCol, sWidth, sCap, sJoin, sMiter, sA) = pathAttrs.map { tag.getAttribute(it, ANDROID_NS) }
        val fillColor = fCol ?: tag.findAaptAttrTag("fillColor")
        val strokeColor = sCol ?: tag.findAaptAttrTag("strokeColor")

        val fillOpacity = fillColor.opacity(rr, holder.toFloat(rr, fA, 1f))
        val (stroke, strokeOpacity) = holder.stroke(rr, sWidth, strokeColor, sCap, sJoin, sMiter, sA) ?: nullToZero

        var filledSubPaths = if (fillOpacity == PixelFormat.TRANSPARENT) null else BitSet(outlines.size)
        var strokedSubPaths = if (stroke == null) null else BitSet(outlines.size)
        if (filledSubPaths == null && strokedSubPaths == null) {
            return holder.report(tag, "Invisible path: no fill, no stroke", removeTagFix)
        }

        val strokeCaps = if (stroke == null || sCap == null) null else BitSet(outlines.size)
        val strokeJoins = if (stroke == null || sJoin == null) null else BitSet(outlines.size)

        val evenOdd = holder.toString(rr, fType, "nonZero") == "evenOdd"
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
                ?.takeIf { strokeOpacity == PixelFormat.OPAQUE }
                ?.also { opaque += it }

            val fillArea = outline.takeIf { filledSubPaths != null }
                ?.toArea()?.without(opaqueStrokeArea) // stroke is drawn on top of fill
                ?.takeIf { !it.effectivelyEmpty(usefulPrecision) }
                ?.also { fillArea ->
                    filledSubPaths!!.set(index)
                    if (fillOpacity == PixelFormat.OPAQUE) opaque += fillArea
                    if (evenOdd && !reallyEvenOdd)
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
            return holder.report(tag, "Invisible path: none of sub-paths are filled or stroked", removeTagFix)
        }

        if (noFill) {
            holder.reportNoFill(
                fillColor, fType, fA,
                if (filledSubPaths == null) "attribute has no effect with uncolored or transparent fill"
                else "attribute has no effect with open path",
            )
            filledSubPaths = null
        } else if (evenOdd && !reallyEvenOdd) {
            holder.report(fType!!, "attribute has no effect", removeAttrFix)
        }

        if (noStroke) {
            holder.reportNoStroke(sWidth, sCol, sCap, sJoin, sMiter, sA)
            strokedSubPaths = null
        } else {
            if (strokeCaps?.cardinality() == 0) holder.report(sCap!!, "attribute has no effect", removeAttrFix)
            if (strokeJoins?.cardinality() == 0) holder.report(sJoin!!, "attribute has no effect", removeAttrFix)
        }

        if (filledSubPaths == null && strokedSubPaths == null) {
            return
        }

        subAreas = areas
        opaqueArea = opaque
        this.filledSubPaths = filledSubPaths
        this.strokedSubPaths = strokedSubPaths
    }

    fun merged(): Area? = // TODO split clip-paths, too
        outlines.map(::Area).takeIf(List<*>::isNotEmpty)?.reduce { acc, area -> acc.add(area); acc }

    fun applyClip(
        clipPath: Area?, clips: List<Area>, usefulClips: TIntHashSet,
        usefulPrecision: Int,
    ) {
        val subAreas = subAreas ?: return
        if (usefulClips.size() < clips.size) {
            val clippedAway = Area()
            var index = 0
            while (index < clips.size) {
                val clip = clips[index]
                var areaIdx = 0
                while (index !in usefulClips && areaIdx < subAreas.size) {
                    if (!clippedAway.with(subAreas[areaIdx++]).without(clip).effectivelyEmpty(usefulPrecision) &&
                        usefulClips.add(index) && usefulClips.size() == clips.size)
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

    private fun ProblemsHolder.reportNoFill(col: XmlElement?, type: XmlAttribute?, a: XmlAttribute?, complaint: String) {
        col?.let { report(it, complaint, removeAttrFix) }
        type?.let { report(it, complaint, removeAttrFix) }
        a?.let { report(it, complaint, removeAttrFix) }
    }

    /**
     * @return stroke and opacity
     */
    private fun ProblemsHolder.stroke(
        rr: ResourceResolver?,
        width: XmlAttribute?,
        col: XmlElement?, cap: XmlAttribute?, join: XmlAttribute?, miter: XmlAttribute?, a: XmlAttribute?
    ): Pair<BasicStroke, Int>? {
        val strokeWidth = toFloat(rr, width, 0f)
        val opacity = col.opacity(rr, toFloat(rr, a, 1f))
        return if (strokeWidth != 0f && opacity != PixelFormat.TRANSPARENT) {
            val capName = toString(rr, cap, "butt")
            val joinName = toString(rr, join, "miter")
            BasicStroke(
                strokeWidth,
                when (capName) {
                    "round" -> BasicStroke.CAP_ROUND
                    "square" -> BasicStroke.CAP_SQUARE
                    else -> BasicStroke.CAP_BUTT
                },
                when (joinName) {
                    "round" -> BasicStroke.JOIN_ROUND
                    "bevel" -> BasicStroke.JOIN_BEVEL
                    else -> BasicStroke.JOIN_MITER
                },
                toFloat(rr, miter, 4f)
            ) to opacity
        } else {
            null
        }
    }
    private fun ProblemsHolder.reportNoStroke(
        width: XmlAttribute?, col: XmlElement?,
        cap: XmlAttribute?, join: XmlAttribute?, miter: XmlAttribute?,
        a: XmlAttribute?
    ) {
        width?.let { report(it, "attribute has no effect", removeAttrFix) }
        col?.let { report(it, "attribute has no effect", removeAttrFix) }
        cap?.let { report(it, "attribute has no effect", removeAttrFix) }
        join?.let { report(it, "attribute has no effect", removeAttrFix) }
        miter?.let { report(it, "attribute has no effect", removeAttrFix) }
        a?.let { report(it, "attribute has no effect", removeAttrFix) }
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

    private fun XmlElement?.opacity(rr: ResourceResolver?, alpha: Float) =
        if (alpha == 0f || this == null) PixelFormat.TRANSPARENT else when (this) {
            is XmlAttribute -> rr.resolve(this)?.takeIf { it.isNotBlank() && !it.endsWith(".xml") }?.let {
                when (it.length.takeIf { _ -> it.startsWith('#') }) { //              TODO ^^^^ resolve color state lists and gradients
                    4, 7 ->
                        if (alpha == 1f) PixelFormat.OPAQUE
                        else PixelFormat.TRANSLUCENT
                    5 ->
                        if (it[1] == '0') PixelFormat.TRANSPARENT
                        else if (it[1].equals('f', true) && alpha == 1f) PixelFormat.OPAQUE
                        else PixelFormat.TRANSLUCENT
                    9 ->
                        if (it[1] == '0' && it[2] == '0') PixelFormat.TRANSPARENT
                        else if (it[1].equals('f', true) && it[2].equals('f', true) && alpha == 1f) PixelFormat.OPAQUE
                        else PixelFormat.TRANSLUCENT
                    else ->
                        null
                }
            } ?: PixelFormat.UNKNOWN
            is XmlTag -> PixelFormat.UNKNOWN
            else -> throw IllegalArgumentException()
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
            val deadRange = TextRange.create(subPathRanges[at], subPathRanges[at + 1])
            registerProblem(
                pathDataAttr, complaint, ProblemHighlightType.LIKE_UNUSED_SYMBOL, deadRange,
                removeSubPathFix(pathDataAttr.text, deadRange),
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
    @FileModifier.SafeFieldForPreview private val ranges: TIntArrayList,
    name: String,
    private val targetPrecision: Int,
    private val pathData: String,
) : NamedLocalQuickFix(name) {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val value = StringBuilder(pathData)
        val tmpFloat = StringBuilder(10)
        for (i in (ranges.size()-2) downTo 0 step 2) {
            val start = ranges[i]
            val end = ranges[i + 1]
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

private fun removeSubPathFix(pathDataAttrValueText: String, deadRange: TextRange): LocalQuickFix? {
    for (i in deadRange.endOffset until pathDataAttrValueText.length) {
        val ch = pathDataAttrValueText[i]
        if (ch.isLowerCase()) return null // TODO heal relative paths, too
    }
    return object : NamedLocalQuickFix("Remove sub-path") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val attrVal = (descriptor.psiElement as? XmlAttributeValue) ?: return
            val value = attrVal.value
            val shift = attrVal.text.indexOf(value)
            (attrVal.parent as XmlAttribute).setValue(
                value.removeRange(deadRange.startOffset - shift, deadRange.endOffset - shift)
            )
        }
    }
}

private fun StringBuilder.replace(
    at: Int, till: Int,
    with: CharSequence, from: Int = 0, to: Int = with.length,
): StringBuilder {
    val victimLen = till - at
    val replLen = to - from
    repeat(kotlin.math.min(victimLen, replLen)) { this[at + it] = with[from + it] }
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
