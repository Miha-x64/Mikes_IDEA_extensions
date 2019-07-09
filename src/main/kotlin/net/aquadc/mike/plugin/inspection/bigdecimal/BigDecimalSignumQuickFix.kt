package net.aquadc.mike.plugin.inspection.bigdecimal

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethodCallExpression
import com.siyeh.ig.PsiReplacementUtil

class BigDecimalSignumQuickFix : LocalQuickFix {

    override fun getFamilyName(): String {
        return "Replace BigDecimal instantiation with constant"
    }

    override fun applyFix(project: Project, problemDescriptor: ProblemDescriptor) {
        val psiElement = problemDescriptor.psiElement
        if (psiElement !is PsiMethodCallExpression) {
            LOG.error("WTF?")
            return
        }
        PsiReplacementUtil.replaceExpression(psiElement, getReplacementText(psiElement))
    }

    private fun getReplacementText(expression: PsiMethodCallExpression): String {
        val methodExpression = expression.methodExpression
        val `var` = methodExpression.qualifier!!.text
        return "$`var`.signum()"
    }

    companion object {
        private val LOG = Logger.getInstance(BigDecimalSignumQuickFix::class.java)
    }
}
