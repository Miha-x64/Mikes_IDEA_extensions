package net.aquadc.mike.plugin.memory

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.psi.PsiClassInitializer
import com.intellij.psi.PsiConstructorCall
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.refactoring.RefactoringActionHandler
import com.siyeh.ig.fixes.IntroduceConstantFix
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.referencedName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElementOfType
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
            if (expr.getParentOfType<PsiField>(true)?.isStaticFinal == true ||
                expr.getParentOfType<KtProperty>(true)?.isFileOrObjVal == true) {
                return
            }

            val ref = when (expr) {
                is PsiNewExpression ->
                    expr.takeIf {
                        it.noTypeOrValueArguments &&
                            it.classReference?.referenceName in unqualifiedNoArgConstructors
                    }
                is KtCallExpression ->
                    expr.takeIf { it.noTypeOrValueArguments }?.referenceExpression()
                        ?.takeIf { rex -> rex.referencedName in unqualifiedNoArgConstructors }
                        ?.mainReference
                else ->
                    null
            } ?: return

            // resolve

            // resolve (a): skip assignment to static/object/file field/property which stands apart from declaration
            if (expr.getParentOfType<PsiClassInitializer>(true)?.hasModifierProperty(PsiModifier.STATIC) == true ||
                (expr.getParentOfType<KtClassInitializer>(true)?.parent as? KtClassBody)?.parent is KtObjectDeclaration
            ) return

            // resolve (b): resolve call expression reference
            val method = when (ref) {
                is PsiConstructorCall -> ref.resolveConstructor()
                is PsiReference -> ref.resolve()
                else -> throw AssertionError("unexpected $ref | ${ref.javaClass}")
            } ?: return

            if (method.toUElementOfType<UMethod>()?.let {
                it.isConstructorWithoutTypeOrValueParameters &&
                    it.getContainingUClass()?.qualifiedName in noArgConstructors
            } == true) {
                holder.registerProblem(
                    expr, // remember the whole expression (for quickfix) but highlight only the relevant part
                    "This allocation should be cached",
                    LanguageRefactoringSupport.INSTANCE.forContext(expr)?.introduceConstantHandler?.let {
                        object : IntroduceConstantFix() {
                            override fun getHandler(): RefactoringActionHandler = it
                        }
                    }
                )
            }
        }

        private val PsiField.isStaticFinal: Boolean
            get() = hasModifierProperty(PsiModifier.STATIC) && hasModifierProperty(PsiModifier.FINAL)

        private val KtProperty.isFileOrObjVal: Boolean
            get() = valOrVarKeyword.let { it is TreeElement && it.elementType == KtTokens.VAL_KEYWORD } &&
                parent.let { it is KtFile || it is KtClassBody && it.parent is KtObjectDeclaration }


        private val PsiNewExpression.noTypeOrValueArguments: Boolean
            get() = typeArguments.isEmpty() && argumentList?.isEmpty != false

        private val KtCallExpression.noTypeOrValueArguments: Boolean
            get() = typeArguments.isEmpty() && valueArguments.isEmpty() && lambdaArguments.isEmpty()


        private val UMethod.isConstructorWithoutTypeOrValueParameters: Boolean
            get() = isConstructor && uastParameters.isEmpty() && !hasTypeParameters()

    }

    companion object {
        private val noArgConstructors = listOf("com.google.gson.Gson", "okhttp3.OkHttpClient")
        private val unqualifiedNoArgConstructors = noArgConstructors.map { it.substring(it.lastIndexOf('.') + 1) }
    }

}
