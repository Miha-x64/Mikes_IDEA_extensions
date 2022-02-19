package net.aquadc.mike.plugin.android

import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClassType
import com.siyeh.ig.callMatcher.CallMatcher
import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.test
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import com.android.tools.idea.gradle.project.model.AndroidModuleModel.get as androidModelModule

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

        override fun visitCallExpr(node: UCallExpression): Boolean {
            check(node)
            return true
        }

        private fun check(node: UCallExpression) {
            val src = node.sourcePsi ?: return
            androidModelModule(src.containingFile?.androidFacet ?: return)
                ?.minSdkVersion?.apiLevel?.takeIf { it < 26 } ?: return
            val className = (node.receiverType as? PsiClassType)?.resolve()?.qualifiedName ?: return
            if (className == "android.widget.VideoView" && VideoView_setOnClickListener.test(src))
                holder.registerProblem(node.methodIdentifier?.sourcePsi ?: src, "This call is useless before SDK 26")
        }
    }

    private companion object {
        private val VideoView_setOnClickListener =
            CallMatcher.anyOf(
                CallMatcher.instanceCall("android.view.View", "setOnClickListener")
                    .parameterTypes("android.view.View.OnClickListener"),
                CallMatcher.instanceCall("android.view.View", "setOnTouchListener")
                    .parameterTypes("android.view.View.OnTouchListener"),
            )
    }
}
