package net.aquadc.mike.plugin.inspection

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.propertyVisitor


class KtPropByInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        propertyVisitor { prop ->
            prop.delegate?.let { delegate ->
                holder.registerProblem(delegate, "Property delegation")
            }
        }

}
