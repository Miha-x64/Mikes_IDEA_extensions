package net.aquadc.mike.plugin.inspection

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*


class KtNoinlineFuncInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {

            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                if (function.isLocal) visitAnonymousFunction(function)
            }
            override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
                super.visitCallableReferenceExpression(expression)
                visitAnonymousFunction(expression)
            }
            override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                super.visitLambdaExpression(lambdaExpression)
                visitAnonymousFunction(lambdaExpression)
            }

            private fun visitAnonymousFunction(expression: KtExpression) {
                when (val parent = expression.context) {
                    is KtProperty -> {
                        report(expression, "This function cannot be inlined as it is assigned to variable ${parent.name}")
                    }
                    is KtValueArgument -> {
                        val argList = parent.parent as? KtValueArgumentList
                        (argList?.parent as? KtCallExpression ?: parent.parent as? KtCallExpression)
                            ?.let { (it.calleeExpression as? KtReferenceExpression) }
                            ?.mainReference?.resolve()?.let { it as? KtNamedFunction }
                            ?.let { callee ->
                                if (callee.isInline) {
                                    val param = parent.getArgumentName()?.text
                                        ?.let { parameterName -> callee.valueParameters.first { it.name == parameterName } }
                                        ?: callee.valueParameters[
                                                argList?.arguments?.indexOf(parent)
                                                    ?: callee.valueParameters.lastIndex // for `func(...) { }` we won't get KtValueArgumentList
                                        ]

                                    if (param.isNoinline) {
                                        reportNoinlineParamOfInline(expression, callee, param)
                                    }
                                } else {
                                    reportParamOfNoinline(expression, callee)
                                }
                            }
                    }
                    is KtReturnExpression -> {
                        report(expression, "This function cannot be inlined as it is returned from a function")
                    }
                    is KtBlockExpression -> {
                        report(expression, "This function cannot be inlined") // looks like a named function
                    }
                    is KtBinaryExpression -> {
                        // left expression is a receiver and cannot be inlined
                        if (expression == parent.right) {
                            parent.operationReference.mainReference.resolve()?.let { it as? KtNamedFunction }
                                ?.let { callee ->
                                    if (callee.isInline) {
                                        if (callee.valueParameters[0].isNoinline) {
                                            reportNoinlineParamOfInline(expression, callee, callee.valueParameters[0])
                                        }
                                    } else {
                                        reportParamOfNoinline(expression, callee)
                                    }
                                }
                        }
                    }
                }
            }

            private fun reportParamOfNoinline(expression: KtExpression, callee: KtNamedFunction): Unit =
                report(expression, "This function cannot be inlined as it is passed to a non-inline function ${callee.name}")
            private fun reportNoinlineParamOfInline(expression: KtExpression, callee: KtNamedFunction, param: KtParameter): Unit =
                report(expression, "This function cannot be inlined as it is passed to inline function ${callee.name} as a value for noinline parameter ${param.name}")
            private fun report(expression: KtExpression, text: String) {
                val isCallableRef = expression is KtCallableReferenceExpression
                holder.registerProblem(
                    expression,
                    if (isCallableRef) "$text; noinline callable references are a bit more expensive than noinline lambdas." else text,
                    if (isCallableRef) ProblemHighlightType.GENERIC_ERROR_OR_WARNING else ProblemHighlightType.WEAK_WARNING
                )
            }

            private val KtFunction.isInline: Boolean
                get() = modifierList?.hasModifier(KtTokens.INLINE_KEYWORD) == true

            private val KtParameter.isNoinline: Boolean
                get() = modifierList?.hasModifier(KtTokens.NOINLINE_KEYWORD) == true

            override fun visitFunctionType(type: KtFunctionType) {
                super.visitFunctionType(type)

                type.context
                    .let { it as? KtTypeReference }
                    ?.let { it.context as? KtNamedFunction } // this means that we're receiver
                    ?.takeIf { it.isInline }
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
