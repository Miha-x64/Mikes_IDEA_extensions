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


class UncachedAllocInspection : UastInspection() {

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

                val ref = when (expr) {
                    is PsiMethodCallExpression ->
                        if (expr.looksLikeEnumValues) expr.methodExpression.reference else null
                    is PsiNewExpression ->
                        if (expr.looksLikeNewGson) expr.resolveConstructor() else null
                    is KtCallExpression ->
                        if (expr.looksLikeEnumValues || expr.looksLineNewGson) expr.referenceExpression()!!.mainReference
                        else null
                    else ->
                        null
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

                // resolve (b): resolve call expression reference
                if (ref is PsiReference && ref.resolve()?.let { method -> method.isJavaEnumValuesMethod || method.isKotlinEnumValuesMethod || method.isNewGson } == true ||
                        ref is PsiMethod && ref.isNewGson) {
                    holder.registerProblem(expr, "This allocation should be cached")
                }
            }

            private val PsiMethodCallExpression.looksLikeEnumValues: Boolean
                get() = methodExpression.referenceName == "values" && typeArguments.isEmpty() && argumentList.isEmpty

            private val KtCallExpression.looksLikeEnumValues: Boolean
                get() = typeArguments.isEmpty() && valueArguments.isEmpty() && lambdaArguments.isEmpty() &&
                        (referenceExpression() as? KtNameReferenceExpression)?.getReferencedName() == "values"


            private val PsiNewExpression.looksLikeNewGson: Boolean
                get() = typeArguments.isEmpty() && argumentList.let { it == null || it.isEmpty } &&
                        classReference.let { it != null && it.referenceName == "Gson" }

            private val KtCallExpression.looksLineNewGson: Boolean
                get() = typeArguments.isEmpty() && valueArguments.isEmpty() && lambdaArguments.isEmpty() &&
                        (referenceExpression() as? KtNameReferenceExpression)?.getReferencedName() == "Gson"


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

            private val PsiElement.isNewGson: Boolean
                get() = this is PsiMethod && isConstructor &&
                        containingClass?.qualifiedName == "com.google.gson.Gson" &&
                        typeParameters.isEmpty() && parameterList.isEmpty

        }

}
