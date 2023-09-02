package net.aquadc.mike.plugin.android

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import net.aquadc.mike.plugin.NamedReplacementFix
import net.aquadc.mike.plugin.UastInspection
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * @author Mike Gorünóv
 */
class TargetApiInspection : UastInspection(), CleanupLocalInspectionTool {

    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): AbstractUastNonRecursiveVisitor = object : AbstractUastNonRecursiveVisitor() {
        override fun visitDeclaration(node: UDeclaration): Boolean {
            check(node)
            return true
        }
        private fun check(node: UDeclaration) {
            val psi = node.uAnnotations
                .firstOrNull { it.qualifiedName == "android.annotation.TargetApi" }
                ?.sourcePsi
                ?: return
            val fix = psi.annotationText()?.let {
                NamedReplacementFix("Replace with @RequiresApi", "@androidx.annotation.RequiresApi($it)")
            }
            holder.registerProblem(psi, "@TargetApi should be replaced with @RequiresApi", fix)
        }

        private fun PsiElement.annotationText() = when (this) {
            is PsiAnnotation ->
                findAttributeValue("value")?.text ?: ""
            is KtAnnotationEntry ->
                valueArguments.singleOrNull()?.asElement()?.text ?: ""
            else ->
                null
        }
    }
}
