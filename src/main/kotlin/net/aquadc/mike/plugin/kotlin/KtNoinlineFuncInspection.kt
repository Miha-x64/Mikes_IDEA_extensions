package net.aquadc.mike.plugin.kotlin

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import net.aquadc.mike.plugin.isInline
import net.aquadc.mike.plugin.noinlineMessage
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.*


class KtNoinlineFuncInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {

            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                function
                    .takeIf(KtNamedFunction::isLocal)
                    ?.funKeyword
                    ?.let { _fun ->
                        noinlineMessage(function)?.let { message ->
                            holder.registerProblem(_fun, message, ProblemHighlightType.WEAK_WARNING)
                        }
                    }
            }
            override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
                super.visitCallableReferenceExpression(expression)
                noinlineMessage(expression)?.let { message ->
                    holder.registerProblem(expression.doubleColonTokenReference, "$message; noinline callable references are a bit more expensive than noinline lambdas.", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                }
            }
            override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                super.visitLambdaExpression(lambdaExpression)
                noinlineMessage(lambdaExpression)?.let {
                    holder.registerProblem(lambdaExpression.functionLiteral.lBrace, it, ProblemHighlightType.WEAK_WARNING)
                }
            }

            override fun visitFunctionType(type: KtFunctionType) {
                super.visitFunctionType(type)

                val typeRef = type.context as? KtTypeReference ?: return
                typeRef
                    .let { it.context as? KtNamedFunction } // this means that we're receiver or return type
                    ?.takeIf { it.receiverTypeReference == typeRef }
                    ?.takeIf(KtNamedFunction::isInline)
                    ?.let { _ ->
                        holder.registerProblem(
                            type,
                            "This function cannot be inlined as it is passed via receiver (see KT-5837)",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING // let this be yellow
                        )
                    }
            }
        }

}
