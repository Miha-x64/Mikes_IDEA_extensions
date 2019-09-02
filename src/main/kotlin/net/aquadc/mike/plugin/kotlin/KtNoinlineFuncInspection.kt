package net.aquadc.mike.plugin.kotlin

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import net.aquadc.mike.plugin.KtAnonymousFunctionVisitor
import net.aquadc.mike.plugin.isInline
import net.aquadc.mike.plugin.noinlineMessage
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.*


class KtNoinlineFuncInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtAnonymousFunctionVisitor() {

            override fun visitAnonymousFunction(expression: KtExpression) {
                noinlineMessage(expression)?.let { report(expression, it) }
            }

            private fun report(expression: KtExpression, text: String) {
                val isCallableRef = expression is KtCallableReferenceExpression
                holder.registerProblem(
                    expression,
                    if (isCallableRef) "$text; noinline callable references are a bit more expensive than noinline lambdas." else text,
                    if (isCallableRef) ProblemHighlightType.GENERIC_ERROR_OR_WARNING else ProblemHighlightType.WEAK_WARNING
                )
            }

            override fun visitFunctionType(type: KtFunctionType) {
                super.visitFunctionType(type)

                type.context
                    .let { it as? KtTypeReference }
                    ?.let { it.context as? KtNamedFunction } // this means that we're receiver
                    ?.takeIf(KtNamedFunction::isInline)
                    ?.let { function ->
                        holder.registerProblem(
                            type,
                            "This function cannot be inlined as it is passed via receiver (see KT-5837)",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING // let this be yellow
                        )
                    }
            }
        }

}
