package net.aquadc.mike.plugin.kotlin

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import net.aquadc.mike.plugin.KtFunctionObjectVisitor
import net.aquadc.mike.plugin.containingFunction
import net.aquadc.mike.plugin.isInline
import net.aquadc.mike.plugin.noinlineMessage
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import net.aquadc.mike.plugin.miserlyFilter as filter

/**
 * @author Mike Gorünóv
 */
class KtInlineFunctionLeaksAnonymousDeclaration : AbstractKotlinInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): PsiElementVisitor = object : KtFunctionObjectVisitor() {

        override fun visitFunctionObject(expression: KtExpression): Unit = expression.containingFunction
            ?.takeIf { it.isInline && it.isPublic }
            ?.takeIf { noinlineMessage(expression) != null }
            ?.takeIf { !expression.containsCrossinlinesFrom(it) }
            ?.let { holder.registerProblem(
                expression,
                "This anonymous declaration will be copied to the call-site of enclosing inline function " +
                        "if called from another module"
            ) } ?: Unit

        private fun KtExpression.containsCrossinlinesFrom(func: KtFunction): Boolean {
            val xInlines = func.valueParameters.filter { it.hasModifier(KtTokens.CROSSINLINE_KEYWORD) }
            if (xInlines.isEmpty()) return false

            // todo

            return true
        }
    }
}
