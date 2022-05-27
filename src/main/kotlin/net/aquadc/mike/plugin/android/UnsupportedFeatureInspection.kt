package net.aquadc.mike.plugin.android

import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.siyeh.ig.callMatcher.CallMatcher
import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.test
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * @author Mike Gorünóv
 */
class UnsupportedFeatureInspection : UastInspection() {
    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean
    ): AbstractUastNonRecursiveVisitor = object : FunctionCallVisitor() {

        override fun visitImportStatement(node: UImportStatement): Boolean {
            val psi = (node.sourcePsi as? KtImportDirective)
            if (psi != null && psi.importPath?.pathStr?.startsWith("kotlinx.android.synthetic") == true) {
                holder.registerProblem(
                    psi.importedReference ?: psi,
                    "Kotlin Android Extensions are deprecated",
                    ProblemHighlightType.LIKE_DEPRECATED,
                )
            }
            return true
        }

        override fun visitCallExpr(
            node: UExpression, src: PsiElement, kind: UastCallKind, operator: String?,
            declaringClassFqn: String, receiver: UExpression?, methodName: String, valueArguments: List<UExpression>,
        ): Boolean {
            (node as? UCallExpression)?.let(::check)
            return true
        }

        private fun check(node: UCallExpression) {
            val src = node.sourcePsi ?: return
            src.containingFile?.androidFacet?.androidMinSdk()?.apiLevel?.takeIf { it < 26 } ?: return
            val className = (node.receiverType as? PsiClassType)?.resolve()?.qualifiedName ?: return
            if (className == "android.widget.VideoView" && VideoView_setListener.test(src))
                holder.registerProblem(
                    node.methodIdentifier?.sourcePsi ?: src,
                    "VideoView click and touch listeners do not work before SDK 26",
                )
        }
    }

    private companion object {
        private val VideoView_setListener =
            CallMatcher.anyOf(
                CallMatcher.instanceCall("android.view.View", "setOnClickListener")
                    .parameterTypes("android.view.View.OnClickListener"),
                CallMatcher.instanceCall("android.view.View", "setOnTouchListener")
                    .parameterTypes("android.view.View.OnTouchListener"),
            )
    }
}
