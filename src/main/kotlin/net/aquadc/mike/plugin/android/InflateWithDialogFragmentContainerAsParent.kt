package net.aquadc.mike.plugin.android

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import net.aquadc.mike.plugin.UastInspection
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class InflateWithDialogFragmentContainerAsParent : UastInspection() {
    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean
    ): AbstractUastNonRecursiveVisitor = object : AbstractUastNonRecursiveVisitor() {

        private var interestingClass = false
        override fun visitClass(node: UClass): Boolean {
            if (interestingClass) return true // Nested class of an interesting class. Skip, we'll visit it later.
            if (node.javaPsi.superClass?.let(::isDialogFragment) == true) {
                interestingClass = true
                return false
            }
            return true
        }

        private fun isDialogFragment(ref: PsiClass): Boolean =
            ref.qualifiedName
                ?.let { it == "android.app.DialogFragment" || it == "androidx.fragment.app.DialogFragment" } == true ||
                    ref.superClass?.let(::isDialogFragment) == true

        private var containerParam: UParameter? = null
        override fun visitMethod(node: UMethod): Boolean {
            var params: List<UParameter>? = null
            if (interestingClass && node.name == "onCreateView" && node.uastParameters.also { params = it }.size == 3 &&
                params!![0].qn == "android.view.LayoutInflater" &&
                params!![1].qn == "android.view.ViewGroup" &&
                params!![2].qn == "android.os.Bundle") {
                containerParam = params!![1]
                return false
            }
            return true
        }
        private val UParameter.qn get() = typeReference?.getQualifiedName()

        override fun visitExpression(node: UExpression): Boolean =
            containerParam == null

        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
            if (containerParam?.let { it == node.resolveToUElement() } == true) {
                node.sourcePsi?.let {
                    holder.registerProblem(it, "Container of DialogFragment is always null")
                }

                containerParam = null
            }
            return true
        }

        override fun afterVisitMethod(node: UMethod) {
            containerParam = null
        }

        override fun afterVisitClass(node: UClass) {
            interestingClass = false
        }
    }

}
