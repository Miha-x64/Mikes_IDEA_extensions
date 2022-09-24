package net.aquadc.mike.plugin.svg

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.codeInsight.template.TextResult
import com.intellij.codeInsight.template.macro.MacroBase
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlToken
import com.intellij.psi.xml.XmlTokenType
import net.aquadc.mike.plugin.android.res.ANDROID_NS
import java.math.BigDecimal
import kotlin.math.min

internal class MultiplyMacro :
    FoldNumMacro("multiply", "multiply(Number...)", BigDecimal::multiply, TextResult("1"))
internal class MinMacro :
    FoldNumMacro("min", "min(Number...)", BigDecimal::min, null)
internal class AddMacro :
    FoldNumMacro("add", "add(Number...)", BigDecimal::add, TextResult("0"))
internal abstract class FoldNumMacro(
    name: String,
    description: String,
    private val operation: (BigDecimal, BigDecimal) -> BigDecimal,
    private val identity: Result?
) : MacroBase(name, description) {

    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext?, quick: Boolean): Result? {
        return when (params.size) {
            0 -> identity
            1 -> params[0].calculateResult(context)
            else -> {
                var acc = operation(
                    params[0].toBigDecimal(context) ?: return null,
                    params[1].toBigDecimal(context) ?: return null,
                )
                for (i in 2 until params.lastIndex)
                    acc = operation(acc, params[i].toBigDecimal(context) ?: return null)
                acc.stripTrailingZeros().toPlainString().let(::TextResult)
            }
        }
    }
    private fun Expression.toBigDecimal(context: ExpressionContext?) =
        try { calculateResult(context)?.toString()?.let(::BigDecimal) }
        catch (e: NumberFormatException) { null }
}

/** A data extraction function for live templates required for vector shape completion.*/
internal class AvdExtractMacro : MacroBase("svgExtract", "avdExtract(what)") {

    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext?, quick: Boolean): Result? {
        val rootTag = (context?.psiElementAtStartOffset?.containingFile as? XmlFile)?.rootTag
        val (w, h) = when (rootTag?.name) {
            "svg" -> {
                val vb = rootTag.getAttributeValue("viewBox", "http://www.w3.org/2000/svg")?.split(' ', ',')
                    ?: return null
                vb.getOrNull(2)?.toIntOrNull() to vb.getOrNull(3)?.toIntOrNull()
            }
            "vector" ->
                rootTag.getAttributeValue("viewportWidth", ANDROID_NS)?.toIntOrNull() to
                        rootTag.getAttributeValue("viewportHeight", ANDROID_NS)?.toIntOrNull()
            else -> return null
        }
        return TextResult(when (params.single().calculateResult(context).toString()) {
            "viewportWidth" -> w
            "viewportHeight" -> h
            "viewportMinSize" ->
                if (w == null) h else if (h == null) w else min(w, h)
            else -> null
        }?.toString() ?: return null)
    }
}

internal class SvgPathDataContext : TemplateContextType("text/svg+xml; context=pathData", "SVG pathData attribute value") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean =
        !templateActionContext.isSurrounding && (templateActionContext.file as? XmlFile)?.let { file ->
            // TODO detect SVG inside HTML?
            when (file.rootTag?.name) {
                "svg" -> file.findElementAt(templateActionContext.startOffset).let { attrVal ->
                    attrVal is XmlToken && attrVal.tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN &&
                            (attrVal.parent.parent as? XmlAttribute)?.let { attr ->
                                attr.localName == "d" && attr.parent.name == "path"
                            } == true
                }
                "vector" -> file.findElementAt(templateActionContext.startOffset).let { attrVal ->
                    attrVal is XmlToken && attrVal.tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN &&
                            (attrVal.parent.parent as? XmlAttribute)?.let { attr ->
                                attr.localName == "pathData" && attr.parent.name.let { it == "path" || it == "clip-path" }
                            } == true
                }
                else -> false
            }
        } == true
}
