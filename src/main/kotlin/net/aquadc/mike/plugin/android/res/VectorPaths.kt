package net.aquadc.mike.plugin.android.res

import android.graphics.PixelFormat
import com.android.ide.common.resources.ResourceResolver
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import net.aquadc.mike.plugin.component6
import net.aquadc.mike.plugin.component7
import net.aquadc.mike.plugin.component8
import net.aquadc.mike.plugin.component9
import java.awt.BasicStroke
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import net.aquadc.mike.plugin.miserlyMap as map

private val pathAttrs = arrayOf(
    "fillColor", "fillType", "fillAlpha",
    "strokeColor", "strokeWidth", "strokeLineCap", "strokeLineJoin", "strokeMiterLimit", "strokeAlpha",
)
private val nullToZero = null to 0

/**
 * @return merged area, opaque merged area
 */
internal fun ProblemsHolder.toArea(rr: ResourceResolver?, tag: XmlTag, outline: Path2D): Pair<Area, Area?>? {
    val (fCol, fType, fA, sCol, sWidth, sCap, sJoin, sMiter, sA) =
        pathAttrs.map<String, XmlAttribute?>(XmlAttribute.EMPTY_ARRAY) { tag.getAttribute(it, ANDROID_NS) }
    val fillColor = fCol ?: tag.findAaptAttrTag("fillColor")
    val (fillArea, fillOpacity) = fill(rr, outline, fillColor, fType, fA) ?: nullToZero
    val strokeColor = sCol ?: tag.findAaptAttrTag("strokeColor")
    val (stroke, strokeOpacity) = stroke(rr, outline, sWidth, strokeColor, sCap, sJoin, sMiter, sA) ?: nullToZero
    val strokeArea = stroke?.createStrokedShape(outline)?.let(::Area)

    if (fillArea == null && strokeArea == null) {
        report(tag, "Invisible path: no fill, no stroke", removeTagFix)
        return null
    }

    val opaqueFill = fillArea?.takeIf { fillOpacity == PixelFormat.OPAQUE }
    val opaqueStroke = strokeArea?.takeIf { strokeOpacity == PixelFormat.OPAQUE }

    // TODO detect when stroke overdraws fill

    // Dear reader, I'm very sorry for the crap following:
    return if (fillArea != null && strokeArea != null) {
        if (opaqueFill != null && opaqueStroke == null) strokeArea.also { it.add(fillArea) } to opaqueFill
        else fillArea.also { it.add(strokeArea) }
            .let { it to (it.takeIf { opaqueFill != null } ?: opaqueStroke) }
    } else (fillArea ?: strokeArea)!! to (opaqueFill ?: opaqueStroke)
}

/**
 * @return filled area and its opacity
 */
private fun ProblemsHolder.fill(rr: ResourceResolver?, outline: Path2D, col: XmlElement?, type: XmlAttribute?, a: XmlAttribute?): Pair<Area, Int>? {
    val opacity = col.opacity(rr, toFloat(rr, a, 1f))
    if (opacity == PixelFormat.TRANSPARENT) {
        reportNoFill(col, type, a, "attribute has no effect with uncolored or transparent fill")
        return null
    }

    val evenOdd = toString(rr, type, "nonZero") == "evenOdd"
    if (evenOdd)
        outline.windingRule = Path2D.WIND_EVEN_ODD
    val area = Area(outline)

    if (area.isEmpty) { // TODO check for empty sub-paths
        reportNoFill(col, type, a, "attribute has no effect with open path")
        return null
    } else if (evenOdd && Area(outline.also { it.windingRule = Path2D.WIND_NON_ZERO }).equals(area)) {
        report(type!!, "attribute has no effect", removeAttrFix)
    }
    return area to opacity
}
private fun ProblemsHolder.reportNoFill(col: XmlElement?, type: XmlAttribute?, a: XmlAttribute?, complaint: String) {
    col?.let { report(it, complaint, removeAttrFix) }
    type?.let { report(it, complaint, removeAttrFix) }
    a?.let { report(it, complaint, removeAttrFix) }
}

private val dummyFloats = FloatArray(6)

/**
 * @return stroke and opacity
 */
private fun ProblemsHolder.stroke(
    rr: ResourceResolver?,
    outline: Path2D,
    width: XmlAttribute?, col: XmlElement?, cap: XmlAttribute?, join: XmlAttribute?, miter: XmlAttribute?,
    a: XmlAttribute?
): Pair<BasicStroke, Int>? {
    val strokeWidth = toFloat(rr, width, 0f)
    val opacity = col.opacity(rr, toFloat(rr, a, 1f))
    return if (strokeWidth != 0f && opacity != PixelFormat.TRANSPARENT) {
        val capName = toString(rr, cap, "butt")
        val joinName = toString(rr, join, "miter")
        checkStroke(outline, cap, join)
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
        width?.let { report(it, "attribute has no effect", removeAttrFix) }
        col?.let { report(it, "attribute has no effect", removeAttrFix) }
        cap?.let { report(it, "attribute has no effect", removeAttrFix) }
        join?.let { report(it, "attribute has no effect", removeAttrFix) }
        miter?.let { report(it, "attribute has no effect", removeAttrFix) }
        a?.let { report(it, "attribute has no effect", removeAttrFix) }
        null
    }
}
private fun ProblemsHolder.checkStroke(outline: Path2D, cap: XmlAttribute?, join: XmlAttribute?) {
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
    if (!gaps && cap != null) report(cap, "attribute has no effect", removeAttrFix)
    if (!joins && join != null) report(join, "attribute has no effect", removeAttrFix)
}

private fun XmlElement?.opacity(rr: ResourceResolver?, alpha: Float) =
    if (alpha == 0f || this == null) PixelFormat.TRANSPARENT else when (this) {
        is XmlAttribute -> rr.resolve(this)?.takeIf { it.isNotBlank() && !it.endsWith(".xml") }?.let {
            when (it.length.takeIf { _ -> it.startsWith('#') }) { //              TODO ^^^^ resolve color state lists
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
                    Logger.getInstance("Mike's IDEA Extensions: VectorPaths").error("Unexpected color format: $it")
                        .let { null }
            }
        } ?: PixelFormat.UNKNOWN
        is XmlTag -> PixelFormat.UNKNOWN
        else -> throw IllegalArgumentException()
    }
