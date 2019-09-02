package net.aquadc.mike.plugin.bigdecimal

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiExpression
import com.siyeh.ig.PsiReplacementUtil

/**
 * @author stokito
 */
class BigDecimalConstantQuickFix(private val replacementText: String) : LocalQuickFix {

    override fun getFamilyName(): String {
        return "Replace BigDecimal instantiation with constant"
    }

    override fun applyFix(project: Project, problemDescriptor: ProblemDescriptor) {
        val psiElement = problemDescriptor.psiElement
        if (psiElement !is PsiExpression) {
            LOG.error("WTF?")
            return
        }
        PsiReplacementUtil.replaceExpression(psiElement, replacementText)
    }

    companion object {
        private val LOG = Logger.getInstance(BigDecimalConstantQuickFix::class.java)
    }
}
