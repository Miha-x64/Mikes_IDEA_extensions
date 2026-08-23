package net.aquadc.mike.plugin.kotlin

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModCommandService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import net.aquadc.mike.plugin.isInline
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.idea.references.AbstractKtReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.util.OperatorNameConventions

/**
 * @author Mike Gorünóv
 */
class KtPropByInspection : LocalInspectionTool() {

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitProperty(property: KtProperty) {
            property.delegate?.let { delegate ->
                (delegate.reference as? AbstractKtReference<*>)?.let { reference ->
                    if (reference.multiResolve(true)
                        .any { (it.element as? KtNamedFunction)?.isInline == false }) {
                        holder.registerProblem(delegate, TextRange.from(0, 2), "Heavyweight property delegation")
                    }
                }
            }
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            if (function.isDelegationFunction && !function.isInline) {
                holder.registerProblem(
                    function.nameIdentifier ?: function,
                    "Delegation functions should be inline",
                    ModCommandService.getInstance().wrapToQuickFix(AddModifierFix(function, KtTokens.INLINE_KEYWORD)),
                )
            }
        }

        private val KtNamedFunction.isDelegationFunction: Boolean
            get() = isPotentiallyOperator && name?.let {
                it == OperatorNameConventions.PROVIDE_DELEGATE.asString() ||
                        it == OperatorNameConventions.GET_VALUE.asString() ||
                        it == OperatorNameConventions.SET_VALUE.asString()
            } ?: false

        // org.jetbrains.kotlin.idea.search.isPotentiallyOperator
        val PsiElement?.isPotentiallyOperator: Boolean
            get() =
                this is KtNamedFunction && // operator modifier could be omitted for overriding function
                    (hasModifier(KtTokens.OPERATOR_KEYWORD) || hasModifier(KtTokens.OVERRIDE_KEYWORD))
    }

}
