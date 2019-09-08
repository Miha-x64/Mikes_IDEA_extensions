package net.aquadc.mike.plugin.memory

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.TreeElement
import net.aquadc.mike.plugin.UastInspection
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor


class EnumValuesUncachedInspection : UastInspection() {

    override fun uVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): AbstractUastNonRecursiveVisitor =
        object : AbstractUastNonRecursiveVisitor() {

            override fun visitExpression(node: UExpression): Boolean {
                node.sourcePsi?.let(::checkExpression)
                return super.visitExpression(node)
            }

            private fun checkExpression(expr: PsiElement) {
                // skip static/object/file field/property declarations
                if (expr.getParentOfType<PsiField>(true)?.isStaticFinalField == true ||
                    expr.getParentOfType<KtProperty>(true)?.isFileOrObjVal == true) {
                    return
                }

                val methodRef = when (expr) {
                    is PsiMethodCallExpression -> {
                        if (expr.methodExpression.referenceName != "values") return
                        if (expr.typeArguments.isNotEmpty() || !expr.argumentList.isEmpty) return
                        expr.methodExpression.reference
                    }
                    is KtCallExpression -> {
                        val reference = expr.referenceExpression()
                        if ((reference as? KtNameReferenceExpression)?.getReferencedName() != "values") return
                        if (expr.typeArguments.isNotEmpty() || expr.valueArguments.isNotEmpty() || expr.lambdaArguments.isNotEmpty()) return
                        reference.mainReference
                    }
                    else -> null
                } ?: return

                // resolve

                // resolve (a): skip assignment to static/object/file field/property which stands apart from declaration
                if (expr.getParentOfType<PsiAssignmentExpression>(true).let {
                        it != null && it.lExpression.reference?.resolve().let {
                            it == null || it.isStaticFinalField || it.isFileOrObjVal
                        }
                    } || expr.getParentOfType<KtBinaryExpression>(true).let {
                        it != null && it.operationToken == KtTokens.EQ && it.left.let {
                            it is KtNameReferenceExpression && it.reference?.resolve().let { it == null || it.isStaticFinalField || it.isFileOrObjVal }
                        }
                    }) return

                // resolve (b): resolve values() method
                val method = methodRef.resolve() ?: return
                if (method.isJavaEnumValuesMethod || method.isKotlinEnumValuesMethod) {
                    holder.registerProblem(expr, "Calling Enum values() without caching")
                }
            }

            private val PsiElement.isStaticFinalField: Boolean
                get() = this is PsiField && hasModifierProperty(PsiModifier.STATIC) && hasModifierProperty(PsiModifier.FINAL)

            private val PsiElement.isFileOrObjVal: Boolean
                get() = this is KtProperty &&
                        valOrVarKeyword.let { it is TreeElement && it.elementType == KtTokens.VAL_KEYWORD } &&
                        parent.let { it is KtFile || it is KtClassBody && it.parent is KtObjectDeclaration }


            private val PsiElement.isJavaEnumValuesMethod: Boolean
                get() = this is PsiMethod && containingClass?.isEnum == true && hasModifierProperty(PsiModifier.STATIC)

            private val PsiElement.isKotlinEnumValuesMethod: Boolean
                get() = /* wtf?! */ this is KtClass && isEnum()

        }

}
