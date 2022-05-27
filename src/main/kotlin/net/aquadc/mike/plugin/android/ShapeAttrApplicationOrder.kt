package net.aquadc.mike.plugin.android

import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.SortedArray
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.getParentUntil
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UThisExpression
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
    ): AbstractUastNonRecursiveVisitor = object : FunctionCallVisitor(assignment = true) {

        override fun visitCallExpr(
            node: UExpression, src: PsiElement, kind: UastCallKind, operator: String?,
            declaringClassFqn: String, receiver: UExpression?, methodName: String, valueArguments: List<UExpression>,
        ): Boolean {
            checkForRadius(src, kind, declaringClassFqn, receiver, methodName)
            return true
        }

        private fun checkForRadius(
            src: PsiElement, kind: UastCallKind,
            declaringClassFqn: String, receiver: UExpression?, methodName: String,
        ) {
            val af = src.containingFile?.androidFacet ?: return
            if ((af.androidMinSdk()?.apiLevel ?: Int.MAX_VALUE) >= 28) return

            if (kind == UastCallKind.METHOD_CALL &&
                methodName.let { it == "setCornerRadius" || it == "setCornerRadii" } &&
                declaringClassFqn == "android.graphics.drawable.GradientDrawable") {
                val nodeReceiver = receiver?.takeIf { it !is UThisExpression }
                var nextNode: PsiElement? = src.getParentUntil(PsiCodeBlock::class.java, KtBlockExpression::class.java)
                while (nextNode?.getNextSiblingIgnoringWhitespace(false).also { nextNode = it } != null) {
                    var nextCallExpr = nextNode.toUElement()
                    if (nextCallExpr is UQualifiedReferenceExpression) nextCallExpr = nextCallExpr.selector
                    if (nextCallExpr is UCallExpression &&
                        nextCallExpr.kind == UastCallKind.METHOD_CALL &&
                        (nextCallExpr.methodName ?: "") in computeOpacityCallers &&
                        sameReceiver(nextCallExpr, nodeReceiver)
                    ) {
                        return
                    } else if (nextCallExpr is UBinaryExpression &&
                        nextCallExpr.leftOperand.also { nextCallExpr = it } is UReferenceExpression &&
                            ((nextCallExpr as UReferenceExpression).resolvedName ?: "") in computeOpacityCallers &&
                            sameReceiver(nextCallExpr as UReferenceExpression, nodeReceiver)) {
                        return
                    }
                }

                holder.registerProblem(src, "Corner radius should be set before shape, color, or stroke. " +
                        "Otherwise, opacity may not be computed properly")
            }
        }

        private fun sameReceiver(nextCallExpr: UExpression, nodeReceiver: UExpression?) =
            when (nextCallExpr) {
                is UCallExpression -> {
                    val receiver = nextCallExpr.receiver?.takeIf { it !is UThisExpression }
                    (receiver == nodeReceiver || receiver?.tryResolve() == nodeReceiver?.tryResolve())
                }
                is UQualifiedReferenceExpression -> {
                    val receiver = nextCallExpr.receiver.takeIf { it !is UThisExpression }
                    (receiver == nodeReceiver || receiver?.tryResolve() == nodeReceiver?.tryResolve())
                }
                else ->
                    nodeReceiver == null
            }
    }

    private companion object {
        private val computeOpacityCallers = SortedArray.of("setShape", "setColors", "setColor", "setStroke")
    }

}