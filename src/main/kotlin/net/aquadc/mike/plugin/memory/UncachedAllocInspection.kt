package net.aquadc.mike.plugin.memory

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.WEAK_WARNING
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.util.PsiEditorUtil.findEditor
import com.siyeh.ig.fixes.IntroduceConstantFix
import net.aquadc.mike.plugin.NamedLocalQuickFix
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.referencedName
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceConstant.KtIntroduceConstantHandlerCompat
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceConstant.INTRODUCE_CONSTANT
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceConstant.IntroduceConstantExtractionOptions
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.textRangeIn
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * @author Mike Gorünóv
 */
class UncachedAllocInspection : UastInspection() {

    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): AbstractUastNonRecursiveVisitor = object : AbstractUastNonRecursiveVisitor() {

        override fun visitExpression(node: UExpression): Boolean {
            node.sourcePsi?.let(::checkExpression)
            return true
        }

        private fun checkExpression(expr: PsiElement) {
            // skip static/object/file field/property declarations
            if (expr.getParentOfType<PsiField>(true)?.isStaticFinalField == true ||
                expr.getParentOfType<KtProperty>(true)?.isFileOrObjVal == true) {
                return
            }

            val ref = when (expr) {
                is PsiMethodCallExpression ->
                    if (expr.looksLikeEnumValues || expr.looksLikeGsonBuild) expr.methodExpression.reference else null
                is PsiNewExpression ->
                    if (expr.looksLikeNewGson) expr.resolveConstructor() else null
                is KtCallExpression ->
                    if (expr.typeArguments.isEmpty() && expr.valueArguments.isEmpty() && expr.lambdaArguments.isEmpty())
                        expr.referenceExpression()?.takeIf { rex ->
                            rex.referencedName.let { it == "values" || it == "Gson" || it == "create" }
                        }?.mainReference
                    else null
                else ->
                    null
            } ?: return

            // resolve

            // resolve (a): skip assignment to static/object/file field/property which stands apart from declaration
            if (expr.getParentOfType<PsiClassInitializer>(true)?.hasModifierProperty(PsiModifier.STATIC) == true ||
                (expr.getParentOfType<KtClassInitializer>(true)?.parent as? KtClassBody)?.parent is KtObjectDeclaration
            ) return

            // resolve (b): resolve call expression reference
            val method = ref as? PsiMethod ?: (ref as PsiReference).resolve() ?: return
            val isEnumVals = method.isJavaEnumValuesMethod || method.isKotlinEnumValuesMethod
            if (isEnumVals || (method as? PsiMethod)?.let { it.isNewGson || it.isGsonBuild } == true) {
                holder.registerProblem(holder.manager.createProblemDescriptor(
                    expr, // remember the whole expression (for quickfix) but highlight only the relevant part
                    (expr as? PsiMethodCallExpression)?.methodExpression?.referenceNameElement?.textRangeIn(expr),
                    "This allocation should be cached",
                    if (isEnumVals) WEAK_WARNING else GENERIC_ERROR_OR_WARNING,
                    isOnTheFly,
                    if (expr.language == JavaLanguage.INSTANCE) IntroduceConstantFix()
                    else IntroduceConstantExtractionOptions?.let {
                        object : NamedLocalQuickFix(INTRODUCE_CONSTANT) {
                            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                                val el = descriptor.psiElement
                                KtIntroduceConstantHandlerCompat
                                    .invoke(project, findEditor(el) ?: return, el.containingFile ?: return, null)
                            }
                        }
                    }
                ))
            }
        }

        private val PsiMethodCallExpression.looksLikeEnumValues: Boolean
            get() = methodExpression.referenceName == "values" && typeArguments.isEmpty() && argumentList.isEmpty
        private val PsiMethodCallExpression.looksLikeGsonBuild: Boolean
            get() = methodExpression.referenceName == "create" && typeArguments.isEmpty() && argumentList.isEmpty
        private val PsiNewExpression.looksLikeNewGson: Boolean
            get() = typeArguments.isEmpty() && argumentList.let { it == null || it.isEmpty } &&
                    classReference.let { it != null && it.referenceName == "Gson" }


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

        private val PsiMethod.isNewGson: Boolean
            get() = isConstructor &&
                    containingClass?.qualifiedName == "com.google.gson.Gson" &&
                    typeParameters.isEmpty() && parameterList.isEmpty
        private val PsiMethod.isGsonBuild: Boolean
            get() = !hasModifierProperty(PsiModifier.STATIC) &&
                    containingClass?.qualifiedName == "com.google.gson.GsonBuilder" &&
                    name == "create" &&
                    typeParameters.isEmpty() && parameterList.isEmpty

    }

}
