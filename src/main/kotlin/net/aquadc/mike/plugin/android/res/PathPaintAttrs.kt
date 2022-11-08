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
    val fillColorOpacity: Int
    val fillOpacity: Int

    val strokeColorEl: XmlElement?
    val strokeAlphaEl: XmlAttribute?
    val strokeLineCapEl: XmlAttribute?
    val strokeLineJoinEl: XmlAttribute?
    val strokeWidthEl: XmlAttribute?
    val strokeMiterEl: XmlAttribute?
    val stroke: BasicStroke?
    val strokeAlpha: Float
    val strokeColorOpacity: Int
    val strokeOpacity: Int
    val strokeWidth: Float

    init {
        val (fCol, fType, fA, sCol, sWidth, sCap, sJoin, sMiter, sA) = pathAttrs.map { tag.getAttribute(it, ANDROID_NS) }
        fillColorEl = fCol ?: tag.findAaptAttrTag("fillColor")
        fillAlphaEl = fA
        fillTypeEl = fType
        fillTypeEvenOdd = holder.toString(rr, fType, "nonZero") == "evenOdd"
        val fillColor = rr.color(fillColorEl)
        val fillAlpha = holder.toFloat(rr, fA, 1f)
        fillColorOpacity = opacity(fillColor)
        fillOpacity = opacity(fillColorOpacity, fillAlpha)

        strokeColorEl = sCol ?: tag.findAaptAttrTag("strokeColor")
        strokeAlphaEl = sA
        strokeLineCapEl = sCap
        strokeLineJoinEl = sJoin
        strokeWidthEl = sWidth
        strokeMiterEl = sMiter
        val strokeColor = rr.color(strokeColorEl)
        strokeAlpha = holder.toFloat(rr, sA, 1f)
        strokeColorOpacity = opacity(strokeColor)
        strokeOpacity = opacity(strokeColorOpacity, strokeAlpha)
        strokeWidth = holder.toFloat(rr, sWidth, 0f)
        stroke = holder.stroke(rr, strokeWidth, sCap, sJoin, sMiter, strokeOpacity)
    }

    private fun ResourceResolver?.color(el: XmlElement?) =
        when (el) {
            null -> "#0000"
            is XmlAttribute -> resolve(el)?.takeIf(String::isNotBlank)
            is XmlTag -> null //TODO resolve gradients
            else -> throw IllegalArgumentException()
        }

    /**
     * @return stroke and opacity
     */
    private fun ProblemsHolder.stroke(
        rr: ResourceResolver?,
        strokeWidth: Float,
        cap: XmlAttribute?, join: XmlAttribute?, miter: XmlAttribute?,
        opacity: Int,
    ): BasicStroke? {
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
            )
        } else {
            null
        }
    }
    private fun opacity(color: String?) =
        if (color == null) PixelFormat.UNKNOWN
        else when (color.length.takeIf { _ -> color.startsWith('#') }) { // TODO handle .xml <selector>s
            4, 7 ->
                PixelFormat.OPAQUE
            5 ->
                if (color[1] == '0') PixelFormat.TRANSPARENT
                else if (color[1].equals('f', true)) PixelFormat.OPAQUE
                else PixelFormat.TRANSLUCENT
            9 ->
                if (color[1] == '0' && color[2] == '0') PixelFormat.TRANSPARENT
                else if (color[1].equals('f', true) && color[2].equals('f', true)) PixelFormat.OPAQUE
                else PixelFormat.TRANSLUCENT
            else ->
                PixelFormat.UNKNOWN
        }
    private fun opacity(colorOpacity: Int, alpha: Float) =
        if (alpha == 0f) PixelFormat.TRANSPARENT
        else if (colorOpacity == PixelFormat.OPAQUE && alpha != 1f) PixelFormat.TRANSLUCENT // weaken
        else colorOpacity // UNKNOWN, TRANSPARENT, TRANSLUCENT remain the same

    private companion object {
        private val pathAttrs = arrayOf(
            "fillColor", "fillType", "fillAlpha",
            "strokeColor", "strokeWidth", "strokeLineCap", "strokeLineJoin", "strokeMiterLimit", "strokeAlpha",
        )
    }
}