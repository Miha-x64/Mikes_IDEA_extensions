package net.aquadc.mike.plugin.kotlin

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.util.OperatorNameConventions

/**
 * @author Mike Gorünóv
 */
class KtPropByInspection : AbstractKotlinInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitProperty(property: KtProperty) {
            property.delegate?.let { delegate ->
                if (delegate.references.any { (it.resolve() as? KtNamedFunction)?.hasModifier(KtTokens.INLINE_KEYWORD) == false }) {
                    holder.registerProblem(delegate, TextRange.from(0, 2), "Heavyweight property delegation")
                }
            }
        }
        override fun visitNamedFunction(function: KtNamedFunction) {
            if (function.isDelegationFunction && !function.hasModifier(KtTokens.INLINE_KEYWORD)) {
                holder.registerProblem(
                    function.nameIdentifier ?: function,
                    "Delegation functions should be inline",
                    AddModifierFix(function, KtTokens.INLINE_KEYWORD)
                )
            }
        }

        private val KtNamedFunction.isDelegationFunction: Boolean
            get() = hasModifier(KtTokens.OPERATOR_KEYWORD) && name?.let {
                it == OperatorNameConventions.PROVIDE_DELEGATE.asString() ||
                        it == OperatorNameConventions.GET_VALUE.asString() ||
                        it == OperatorNameConventions.SET_VALUE.asString()
            } ?: false
    }

}
