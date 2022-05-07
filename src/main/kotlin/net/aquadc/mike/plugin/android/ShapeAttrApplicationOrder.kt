package net.aquadc.mike.plugin.android

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.SortedArray
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.getParentUntil
import net.aquadc.mike.plugin.resolvedClassFqn
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * @author Mike Gorünóv
 */
class ShapeAttrApplicationOrder : UastInspection() {

    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): AbstractUastNonRecursiveVisitor = object : FunctionCallVisitor() {

        override fun visitCallExpr(node: UCallExpression): Boolean {
            checkForRadius(holder, node)
            return true
        }

        private fun checkForRadius(holder: ProblemsHolder, node: UCallExpression) {
            val src = node.sourcePsi
            val af = src?.containingFile?.androidFacet ?: return
            if ((AndroidModuleModel.get(af)?.minSdkVersion?.apiLevel ?: Int.MAX_VALUE) >= 28) return
            if (node.kind == UastCallKind.METHOD_CALL &&
                node.methodName.let { it == "setCornerRadius" || it == "setCornerRadii" } &&
                node.resolvedClassFqn == "android.graphics.drawable.GradientDrawable"
            ) {
                var nextNode: PsiElement? = src.getParentUntil(PsiCodeBlock::class.java, KtBlockExpression::class.java)
                while (nextNode?.getNextSiblingIgnoringWhitespace(false).also { nextNode = it } != null) {
                    var nextCallExpr = nextNode.toUElement()
                    if (nextCallExpr is UQualifiedReferenceExpression) nextCallExpr = nextCallExpr.selector
                    nextCallExpr as? UCallExpression ?: continue
                    if (nextCallExpr.kind == UastCallKind.METHOD_CALL &&
                        (nextCallExpr.methodName ?: "") in computeOpacityCallers &&
                        (nextCallExpr.receiver == node.receiver ||
                                nextCallExpr.receiver?.tryResolve() == node.receiver?.tryResolve())) {
                        return
                    }
                }

                holder.registerProblem(src, "Corner radius should be set before shape, color, or stroke. " +
                        "Otherwise, opacity may not be computed properly")
            }
        }
    }

    private companion object {
        private val computeOpacityCallers = SortedArray.of("setShape", "setColors", "setColor", "setStroke")
    }

}