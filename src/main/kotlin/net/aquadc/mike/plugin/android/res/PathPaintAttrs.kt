package net.aquadc.mike.plugin.android.res

import android.graphics.PixelFormat
import com.android.ide.common.resources.ResourceResolver
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import net.aquadc.mike.plugin.component6
import net.aquadc.mike.plugin.component7
import net.aquadc.mike.plugin.component8
import net.aquadc.mike.plugin.component9
import net.aquadc.mike.plugin.miserlyMap as map
import java.awt.BasicStroke

internal class PathPaintAttrs(tag: XmlTag, holder: ProblemsHolder, rr: ResourceResolver?) {
    val fillColorEl: XmlElement?
    val fillAlphaEl: XmlAttribute?
    val fillTypeEl: XmlAttribute?
    val fillTypeEvenOdd: Boolean
    val fillOpacity: Int

    val strokeColorEl: XmlElement?
    val strokeAlphaEl: XmlAttribute?
    val strokeLineCapEl: XmlAttribute?
    val strokeLineJoinEl: XmlAttribute?
    val strokeWidthEl: XmlAttribute?
    val strokeMiterEl: XmlAttribute?
    val stroke: BasicStroke?
    val strokeOpacity: Int

    init {
        val (fCol, fType, fA, sCol, sWidth, sCap, sJoin, sMiter, sA) = pathAttrs.map { tag.getAttribute(it, ANDROID_NS) }
        fillColorEl = fCol ?: tag.findAaptAttrTag("fillColor")
        fillAlphaEl = fA
        fillTypeEl = fType
        fillTypeEvenOdd = holder.toString(rr, fType, "nonZero") == "evenOdd"
        fillOpacity = fillColorEl.opacity(rr, holder.toFloat(rr, fA, 1f))

        strokeColorEl = sCol ?: tag.findAaptAttrTag("strokeColor")
        strokeAlphaEl = sA
        strokeLineCapEl = sCap
        strokeLineJoinEl = sJoin
        strokeWidthEl = sWidth
        strokeMiterEl = sMiter
        val (str, strOp) = holder.stroke(rr, sWidth, strokeColorEl, sCap, sJoin, sMiter, sA)
        stroke = str
        strokeOpacity = strOp
    }

    /**
     * @return stroke and opacity
     */
    private fun ProblemsHolder.stroke(
        rr: ResourceResolver?,
        width: XmlAttribute?,
        col: XmlElement?, cap: XmlAttribute?, join: XmlAttribute?, miter: XmlAttribute?, a: XmlAttribute?
    ): Pair<BasicStroke?, Int> {
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
            nullToZero
        }
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

    private companion object {
        private val pathAttrs = arrayOf(
            "fillColor", "fillType", "fillAlpha",
            "strokeColor", "strokeWidth", "strokeLineCap", "strokeLineJoin", "strokeMiterLimit", "strokeAlpha",
        )
        private val nullToZero = null to 0
    }
}