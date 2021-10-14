package net.aquadc.mike.plugin.kotlin

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.js.descriptorUtils.getJetTypeFqName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable

/**
 * @author Mike Gorünóv
 */
class ConcatNullable : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            val token = expression.operationToken
            if (token == KtTokens.PLUS || token == KtTokens.PLUSEQ) {
                val left = expression.left
                val right = expression.right
                if (left != null || right != null) {
                    if (expression.resolveCalleeType() == "kotlin.String") {
                        left?.let { checkNullability(holder, it, "operand") }
                        right?.let { checkNullability(holder, it, "operand") }
                    }
                }
            }
        }

        override fun visitCallExpression(expression: KtCallExpression) {
            val arg = expression.valueArguments.singleOrNull() ?: return // expect plus(1 arg) or append(1 arg)

            val fn = (expression.referenceExpression() as? KtNameReferenceExpression)?.getReferencedName()
            val ret = expression.resolveCalleeType() ?: return
            if (ret == "kotlin.String" && fn == "plus") {
                expression.getResolvedCall(expression.analyze(BodyResolveMode.PARTIAL))
                    ?.let { it.dispatchReceiver ?: it.extensionReceiver }?.type?.let { type ->
                    val receiver = (expression.parent as? KtQualifiedExpression)?.receiverExpression
                    (receiver ?: expression.calleeExpression)?.let { highlight ->
                        checkNullability(holder, highlight, "receiver of", type)
                    }
                }
                arg.getArgumentExpression()?.let { checkNullability(holder, it, "argument") }
            } else if (ret == "java.lang.StringBuilder" && fn == "append") {
                arg.getArgumentExpression()?.let { checkNullability(holder, it, "argument") }
            }
        }

        // += will not return value, but could resolve to .plus which returns
        private fun KtElement.resolveCalleeType() =
            resolveToCall(BodyResolveMode.PARTIAL)?.candidateDescriptor?.returnType?.unwrap()?.getJetTypeFqName(false)

        private fun checkNullability(holder: ProblemsHolder, a: KtExpression, what: String) {
            checkNullability(holder, a, what, a.analyze(BodyResolveMode.PARTIAL).getType(a) ?: return)
        }
        private fun checkNullability(holder: ProblemsHolder, el: PsiElement, what: String, type: KotlinType) {
            // TODO smartcast
            if (type.isNullable()) holder.registerProblem(el, "Nullable $what to string concatenation")
        }
    }
}
