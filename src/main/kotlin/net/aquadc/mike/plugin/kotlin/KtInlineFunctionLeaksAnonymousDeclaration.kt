package net.aquadc.mike.plugin.kotlin

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import net.aquadc.mike.plugin.KtFunctionObjectVisitor
import net.aquadc.mike.plugin.containingFunction
import net.aquadc.mike.plugin.isInline
import net.aquadc.mike.plugin.noinlineMessage
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.isPublic

class KtInlineFunctionLeaksAnonymousDeclaration : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtFunctionObjectVisitor() {

            override fun visitFunctionObject(expression: KtExpression) {
                expression.containingFunction
                    ?.takeIf(KtFunction::isInline)
                    ?.takeIf(KtFunction::isPublic)
                    ?.takeIf { noinlineMessage(expression) != null }
                    ?.let {
                        holder.registerProblem(expression,
                            "This anonymous declaration will be copied to the call-site of enclosing inline function if called from another module"
                        )
                    }
            }

        }

}
