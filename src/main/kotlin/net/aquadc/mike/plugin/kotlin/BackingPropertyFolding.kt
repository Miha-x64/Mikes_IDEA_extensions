package net.aquadc.mike.plugin.kotlin

import com.intellij.application.options.CodeStyle
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.SmartList
import gnu.trove.TIntArrayList
import net.aquadc.mike.plugin.not
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.structuralsearch.visitor.KotlinRecursiveElementWalkingVisitor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier
import java.util.regex.Pattern
import kotlin.math.min

class BackingPropertyFolding : FoldingBuilderEx() {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (quick || root !is KtFile) return FoldingDescriptor.EMPTY

        val regions = SmartList<FoldingDescriptor>()
        root.accept(object : KotlinRecursiveElementWalkingVisitor() {
            override fun visitProperty(property: KtProperty) {
                val propName = property.takeIf { !it.isLocal && it.isPublic && it.initializer == null }?.name ?: return
                val backing =
                    ((property.getter?.bodyExpression as? KtReferenceExpression)?.resolve() as? KtProperty)
                        ?.takeIf { it.hasModifier(KtTokens.PRIVATE_KEYWORD) && it.parent == property.parent }
                        ?: return
                val backingName = backing.name?.takeIf { it.contains(propName, ignoreCase = true) } ?: return
                val publicType = property.typeReference ?: property.getter?.returnTypeReference ?: return
                val preceding = property.siblings(false, false).filter(IS_CODE).firstOrNull().takeIf { it == backing }
                val following = property.siblings(true, false).filter(IS_CODE).firstOrNull().takeIf { it == backing }
                if (preceding == null && following == null) return

                val colonPrivateType = (backing.typeReference ?: backing.getter?.returnTypeReference)
                    ?.text?.let { ": $it" }
                    ?: ""
                val propSetterVisibility = property.setter?.visibilityModifier()?.text?.plus(' ') ?: ""
                regions.addFoldings(
                    preceding ?: property,
                    following ?: property,
                    buildString {
                        append(property.valOrVarKeyword.text).append(' ').append(propName).append(colonPrivateType)
                        backing.initializer?.let { append(" = ").append(it.text) }
                    },
                    "get: " + publicType.text, // getter body is known to be a reference to backing property
                    property.takeIf { it.isVar }?.let { "${propSetterVisibility}set${property.setterParam(true)}${property.setter.bodyAsSingleLine()}" },
                    "private $backingName.get${backing.getter.bodyAsSingleLine().let { if (it.isBlank()) "" else "()$it" }}",
                    backing.takeIf { it.isVar }?.let { "private $backingName.set${backing.setterParam(false)}${backing.setter.bodyAsSingleLine()}" },
                )
            }

            private fun KtProperty.setterParam(withType: Boolean) =
                setter?.parameter?.let {
                    val name = it.name
                    val type = (it.typeReference ?: typeReference)?.text
                    when {
                        name == null && type == null -> "()"
                        type == null || !withType -> "($name)"
                        else -> "(${name ?: ""}: $type)"
                    }
                } ?: ""
            private fun KtPropertyAccessor?.bodyAsSingleLine() =
                this?.bodyBlockExpression?.let {
                    val text = it.text
                    val start = text.indexOf('{')
                    val end = text.indexOf('}')
                    " { " + LINE_BREAK.matcher(
                        text.substring(if (start < 0) 0 else start + 1, if (end < 0) text.length else end).trim()
                    ).replaceAll(" ↵ ") + " }"
                }
                    ?: this?.bodyExpression?.let { " = ${it.text}" }
                    ?: ""

            private val KtProperty.isPublic: Boolean
                get() = visibilityModifier() == null || hasModifier(KtTokens.PUBLIC_KEYWORD)

            private val IS_SPACE: (PsiElement) -> Boolean = { it is PsiWhiteSpace || it is PsiComment }
            private val IS_CODE: (PsiElement) -> Boolean = !IS_SPACE

            private fun SmartList<FoldingDescriptor>.addFoldings(
                startElement: PsiElement, endElement: PsiElement, vararg foldings: String?
            ) {
                val lineStarts = TIntArrayList()
                lineStarts.add(startElement.startOffset)
                (startElement.siblings(forward = true, withItself = true).takeWhile { it != endElement } + endElement).forEach {
                    if (it.textContains('\n')) {
                        val text = it.text
                        var iof = -1
                        while (text.indexOf('\n', startIndex = iof).also { iof = it } >= 0)
                            lineStarts.add(it.startOffset + ++iof)
                    }
                }

                val actualFoldings = foldings.filterNotNull()
                val indent = " ".repeat(
                    (startElement.prevSibling?.text?.let { it.length - it.lastIndexOf('\n') - 1 } ?: 0) +
                        CodeStyle.getSettings(startElement.containingFile).getIndentSize(KotlinFileType.INSTANCE)
                )
                val group = FoldingGroup.newGroup("backingProperty")
                val last = min(actualFoldings.size, lineStarts.size()) - 1
                repeat(last + 1) { i ->
                    val hasNext = i != last
                    val text = if (hasNext) actualFoldings[i] else actualFoldings.drop(i).joinToString("; ")
                    val lineEnd = if (hasNext) lineStarts[i + 1] - 1 else endElement.endOffset
                    add(FoldingDescriptor(
                        startElement,
                        lineStarts[i], lineEnd,
                        group, if (i == 0) text else indent + text,
                    ))
                }
            }
        })

        return if (regions.isEmpty()) FoldingDescriptor.EMPTY else regions.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String? = null
    override fun isCollapsedByDefault(node: ASTNode): Boolean = true

    private companion object {
        private val LINE_BREAK = Pattern.compile("\\s*\\n\\s*", Pattern.MULTILINE)
    }
}
