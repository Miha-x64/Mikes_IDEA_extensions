package net.aquadc.mike.plugin.kotlin

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import net.aquadc.mike.plugin.referencedName
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.js.descriptorUtils.getJetTypeFqName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoBefore
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.isNullable

/**
 * @author Mike Gorünóv
 */
class ConcatNullable : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            if (expression.operationToken.let { it == KtTokens.PLUS || it == KtTokens.PLUSEQ }) {
                val left = expression.left
                val right = expression.right
                if ((left != null || right != null) && expression.resolveCalleeType() == "kotlin.String") {
                    left?.let { checkNullability(holder, it, "Nullable operand of String concatenation") }
                    right?.let { checkNullability(holder, it, "Nullable operand of String concatenation") }
                }
            }
        }

        override fun visitCallExpression(expression: KtCallExpression) {
            val fn = expression.referenceExpression()?.referencedName ?: return
            val ret = expression.resolveCalleeType() ?: return
            val arg = expression.valueArguments.singleOrNull()?.getArgumentExpression() ?: return // expect plus(1 arg) or append(1 arg)
            when (ret) {
                "kotlin.String" -> {
                    if (fn == "plus") {
                        (expression.parent as? KtQualifiedExpression)?.receiverExpression // null.plus(…)
                            ?.let { checkNullability(holder, it, "Nullable receiver of String concatenation") }
                            ?: expression.getResolvedCall(expression.analyze(BodyResolveMode.PARTIAL))
                                ?.extensionReceiver?.type?.takeIf { it.isNullable() }?.let { // null.apply { plus(…) }
                                    holder.registerProblem(
                                        expression.calleeExpression ?: expression,
                                        "Nullable receiver of String concatenation",
                                    )
                                }
                        checkNullability(holder, arg, "Nullable argument to String concatenation")
                    } // TODO else if fn == "format"
                }
                "java.lang.StringBuilder" -> {
                    if (fn == "append") {
                        checkNullability(holder, arg, "Appending nullable value to StringBuilder")
                    }
                }
            }
        }

        // += will not return value, but could resolve to .plus which returns
        private fun KtElement.resolveCalleeType() =
            resolveToCall(BodyResolveMode.PARTIAL)?.candidateDescriptor?.returnType?.unwrap()?.getJetTypeFqName(false)

        private fun checkNullability(holder: ProblemsHolder, expr: KtExpression, message: String) {
            val ctx = expr.analyze(BodyResolveMode.FULL)
            val ni = ctx.getDataFlowInfoBefore(expr).completeNullabilityInfo
            if ((ni.values().firstOrNull()?.canBeNull() ?: ctx.getType(expr)?.isNullable()) == true)
                holder.registerProblem(expr, message)
        }
    }
}
