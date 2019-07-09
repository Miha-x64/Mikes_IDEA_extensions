package net.aquadc.mike.plugin.inspection.bigdecimal

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.*

import com.siyeh.ig.callMatcher.CallMatcher.instanceCall

/**
 * BigDecimal.compareTo(ZERO) can be replaced with signum()
 * Converts price.compareTo(BigDecimal.ZERO) <= 0 to price.signum() <= 0
 * <pre>
 * BigDecimal bd = BigDecimal.ONE;
 * int i1 = bd.compareTo(BigDecimal.ZERO);
 * int i2 = bd.compareTo(ZERO); // ZERO is static imported
 * int i3 = BigDecimal.TEN.signum();
 * BigDecimal.ZERO.multiply(BigDecimal.ONE).compareTo(BigDecimal.ZERO);
 * ZERO.compareTo(bd) //TODO -1 * one.signum()
 * Ignored: int i4 = bd.compareTo(BigDecimal.valueOf(2));
 * </pre>
 */
class BigDecimalSignumInspection : AbstractBaseJavaLocalInspectionTool(), CleanupLocalInspectionTool {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return BigDecimalInspectionVisitor(holder, isOnTheFly)
    }

    internal inner class BigDecimalInspectionVisitor(
        private val problemsHolder: ProblemsHolder,
        private val onTheFly: Boolean
    ) : JavaElementVisitor() {

        override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
            if (!COMPARE_METHOD.test(call)) return
            val argumentList = call.argumentList
            val arguments = argumentList.expressions
            if (arguments.size != 1) {
                LOG.error("WTF? " + arguments.size)
                return
            }
            val arg = arguments[0] as? PsiReferenceExpression ?: return
            LOG.info("BINGO " + arg.text)
            val psiElement = arg.resolve()
            if (psiElement !is PsiField) {
                LOG.info("not a field " + arg.text)
                return
            }
            val psiField = psiElement as PsiField?
            val psiType = psiField!!.type
            if ("java.math.BigDecimal" != psiType.canonicalText || "ZERO" != psiField.name) {
                LOG.info("not BG.ZERO " + arg.text)
                return
            }
            problemsHolder.registerProblem(
                call,
                "BigDecimal.compareTo(ZERO) can be replaced with signum()",
                BIG_DECIMAL_SIGNUM_QUICK_FIX
            )
        }
    }

    companion object {
        private val LOG = Logger.getInstance(BigDecimalSignumInspection::class.java)

        private val COMPARE_METHOD =
            instanceCall("java.math.BigDecimal", "compareTo").parameterTypes("java.math.BigDecimal")
        private val BIG_DECIMAL_SIGNUM_QUICK_FIX =
            BigDecimalSignumQuickFix()
    }
}

