package net.aquadc.mike.plugin.android

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import net.aquadc.mike.plugin.NamedLocalQuickFix
import net.aquadc.mike.plugin.UastInspection
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.sourcePsiElement
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * @author Mike Gorünóv
 */
class TargetApiInspection : UastInspection() {

    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): AbstractUastNonRecursiveVisitor = object : AbstractUastNonRecursiveVisitor() {
        override fun visitDeclaration(node: UDeclaration): Boolean {
            node.uAnnotations
                .firstOrNull { it.qualifiedName == "android.annotation.TargetApi" }
                ?.let { anno ->
                    anno.sourcePsiElement?.let { psi ->
                        holder.registerProblem(
                            psi, "@TargetApi should be replaced with @RequiresApi", *fixes
                        )
                    }
                }

            return true
        }
    }

    private val fixes = arrayOf<LocalQuickFix>(
        ReplaceTargetWithRequires("@androidx.annotation.RequiresApi"),
        ReplaceTargetWithRequires("@android.support.annotation.RequiresApi")
    )

    private class ReplaceTargetWithRequires(
        private val annotation: String
    ) : NamedLocalQuickFix("Replace with $annotation") {

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            when (val el = descriptor.psiElement) {
                is PsiAnnotation ->
                    JavaCodeStyleManager.getInstance(project).shortenClassReferences(
                        el.replace(
                            JavaPsiFacade.getElementFactory(el.project).createAnnotationFromText(
                                "$annotation(${el.findAttributeValue("value")?.text ?: ""})", null
                            )
                        )
                    )
                is KtAnnotationEntry ->
                    el.replace(KtPsiFactory(el).createAnnotationEntry(
                        "$annotation(${el.valueArguments.getOrNull(0)?.asElement()?.text ?: ""})"
                    ))
                else ->
                    Logger.getInstance(TargetApiInspection::class.java).error("Not an annotation: ${el.javaClass.name}; $el")
                        .let { null }
            }?.let { replacement ->
                CodeStyleManager.getInstance(project).reformat(replacement)
            }
        }

    }

}
