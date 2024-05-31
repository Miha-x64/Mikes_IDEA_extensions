package net.aquadc.mike.plugin.memory

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.HorizontalConstraints
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.RecursivelyUpdatingRootPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.nextLeafs
import com.intellij.psi.util.prevLeafs
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import net.aquadc.mike.plugin.DumbHintsConfigurable
import net.aquadc.mike.plugin.hint
import net.aquadc.mike.plugin.miserlyMap as map
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.utils.indexOfFirst
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.kotlin.KotlinReceiverUParameter
import org.jetbrains.uast.toUElementOfType

class VarargAllocHintsJava : VarargAllocationHintsProvider(settingsKey) {
    private companion object {
        private val settingsKey = SettingsKey<VarargAllocHintsSettings>("net.aquadc.mike.plugin.memory.varargAllocHints.java")
    }
}
class VarargAllocHintsKotlin : VarargAllocationHintsProvider(settingsKey) {
    private companion object {
        private val settingsKey = SettingsKey<VarargAllocHintsSettings>("net.aquadc.mike.plugin.memory.varargAllocHints.kotlin")
    }
}

abstract class VarargAllocationHintsProvider(
    override val key: SettingsKey<VarargAllocHintsSettings>,
) : InlayHintsProvider<VarargAllocHintsSettings> {

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: VarargAllocHintsSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? =
        VarargAllocationHintsCollector(file, editor, settings)

    override fun createSettings(): VarargAllocHintsSettings =
        VarargAllocHintsSettings()

    override val name: String
        get() = "Vararg allocation hints"
    override val group: InlayGroup
        get() = InlayGroup.PARAMETERS_GROUP
    override val description: String?
        get() = "Hints highlighting array allocations in varargs"
    override val previewText: String?
        get() = null
    override fun getCaseDescription(case: ImmediateConfigurable.Case): String? =
        "<p>Hints arguments which are implicitly passed as vararg parameters.</p><br/>" +
            "<p><small>The feature is provided by " +
            "<a href=\"https://github.com/Miha-x64/Mikes_IDEA_extensions\">Mike's IDEA Extensions</a>." +
            "</small></p>"
    override fun createConfigurable(settings: VarargAllocHintsSettings): ImmediateConfigurable = object : DumbHintsConfigurable() {
        override val cases: List<ImmediateConfigurable.Case> get() = listOf(
            Case("Vararg allocation hints", settings::enabled),
        )
    }

}

private class VarargAllocationHintsCollector(
    private val file: PsiFile,
    editor: Editor,
    private val settings: VarargAllocHintsSettings,
) : FactoryInlayHintsCollector(editor) {

    private companion object {
        private val HINTS = arrayOf("new[]{", "}", "*[", "]", "*")
        private val CONSTRAINTS = HorizontalConstraints(0, true) // in case of two hints, like `rest...` `new[] {` we wanna be last
    }
    private val hints: Array<InlayPresentation> = HINTS.map { factory.hint(it) }

    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (!settings.enabled || file.project.let { DumbService.isDumb(it) || it.isDefault }) return false
        if (element is PsiCallExpression) {
            collectJava(element, sink)
            return true
            // Avoid returning `false` because it makes the whole pass to be skipped, not only children
        } else if (element is KtCallExpression) {
            return collectKotlin(element, sink)
        }
        return true
    }

    private fun collectJava(element: PsiCallExpression, sink: InlayHintsSink) {
        val args = element.argumentList
        if (args?.isEmpty != false)
            return

        val callee = element.resolveMethod() ?: return
        val paramList = callee.parameterList
        if (!paramList.isEmpty &&
            paramList.parameters.last().isVarArgs &&
            args.expressionCount >= paramList.parametersCount) {
            val firstVarArg = args.expressions[paramList.parametersCount - 1]
            if (firstVarArg !is PsiNewExpression || !firstVarArg.isArrayCreation) {
                // Java spread is implicit. Yea, we're gonna false-negative on vararg of arrays, but fuck that
                sink.addInlineElement(firstVarArg.startOffset, RecursivelyUpdatingRootPresentation(hints[0]), CONSTRAINTS)
                sink.addInlineElement(args.expressions.last().endOffset, RecursivelyUpdatingRootPresentation(hints[1]), CONSTRAINTS)
            }
        }
    }

    private fun collectKotlin(element: KtCallExpression, sink: InlayHintsSink): Boolean {
        if (element.valueArgumentList.let { it == null || it.arguments.isEmpty() }) return false
        val callee = element.calleeExpression?.mainReference?.resolve()?.toUElementOfType<UMethod>() ?: return true

        val uastParameters = callee.uastParameters
        var variadicParamIndex = uastParameters.indexOfFirst {
            it.isVarArgs || // dafuq? https://twitter.com/miha_x64/status/1796637009336815855
                it.sourcePsi.let { it is KtParameter && it.isVarArg }
        }
        val variadicParamName = uastParameters.getOrNull(variadicParamIndex)?.name
        if (callee.sourcePsi?.isExtensionDeclaration() == true || // not sure what should I check
            uastParameters.first() is KotlinReceiverUParameter)
            variadicParamIndex--

        if (variadicParamIndex < 0) return true

        val args = element.valueArguments
        val namedVarArg = args.firstOrNull { it.getArgumentName()?.asName?.asString() == variadicParamName }
        if (namedVarArg != null) {
            if (!namedVarArg.isSpread) {
                // maxOf(    1, other =  intArrayOf(2, 3, 4))
                // maxOf(a = 1, other =  intArrayOf(2, 3, 4))
                //                 draw * here
                namedVarArg.getArgumentExpression()?.let { ae ->
                    sink.addInlineElement(ae.startOffset, RecursivelyUpdatingRootPresentation(hints[4]), CONSTRAINTS)
                }
            }
        } else {
            // maxOf(    1,   2, 3, 4 )
            // maxOf(a = 1,   2, 3, 4 )
            //         draw *[       ]
            if (args.getOrNull(variadicParamIndex)?.isSpread != false) return true
            // ?.isSpread == false means arg is specified (!= null) and not spread (!= true), go on

            if (args
                    .subList(0, variadicParamIndex) // check that preceding arguments are in their positions, if named
                    .zip(uastParameters) // https://github.com/Kotlin/KEEP/blob/master/proposals/named-arguments-in-their-own-position.md
                    .all { (arg, param) -> // (sorry for an allocation hog. `args.allIndexed(endIndex) { i, it ->`, maybe? )
                        arg.getArgumentName()?.asName?.asString().let { it == null || it == param.name }
                    }
            ) {

                var varArgEndIndex = args.indexOfFirst(variadicParamIndex + 1) {
                    it.getArgumentName() != null // stop on named parameter after vararg
                }
                if (varArgEndIndex < 0)
                    varArgEndIndex = args.size - element.lambdaArguments.size

                // OMG, we've found vararg parameters range
                if (varArgEndIndex - variadicParamIndex > 0) {
                    val firstVarArg = args[variadicParamIndex]
                    sink.addInlineElement(
                        firstVarArg.prevLeafs.firstOrNull { it !is PsiWhiteSpace && it !is PsiComment }
                            ?.takeIf { it.node.elementType === KtTokens.LPAR }?.endOffset
                            ?: firstVarArg.startOffset,
                        RecursivelyUpdatingRootPresentation(hints[2]),
                        CONSTRAINTS,
                    )
                    val lastVarArg = args[varArgEndIndex - 1]
                    sink.addInlineElement(
                        lastVarArg.nextLeafs.firstOrNull { it !is PsiWhiteSpace && it !is PsiComment && it.node.elementType !== KtTokens.COMMA }
                            ?.takeIf { it.node.elementType == KtTokens.RPAR }
                            ?.startOffset ?: lastVarArg.endOffset,
                        RecursivelyUpdatingRootPresentation(hints[3]),
                        CONSTRAINTS,
                    )
                    /*
                    variadic(
                        *[1,
                        2],
                    )
                    + siblings.firstOrNull { ... }.takeIf { ... } ⇒
                    variadic(*[
                        1,
                        2,
                    ])
                    */
                }

            }
        }
        return true
    }

}

data class VarargAllocHintsSettings(var enabled: Boolean = true)
