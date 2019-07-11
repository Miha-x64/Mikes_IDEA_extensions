package net.aquadc.mike.plugin.inspection.bigdecimal

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.callMatcher.CallMatcher
import com.siyeh.ig.psiutils.ExpressionUtils

import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.PsiType.*

/**
 * BigDecimal instantiation can be replaced with constant
 * <pre>
 * BigDecimal.valueOf(0) -> BigDecimal.ZERO
 * new BigDecimal(0) -> BigDecimal.ZERO
 * </pre>
 * @author stokito
 */
class BigDecimalConstantInspection : AbstractBaseJavaLocalInspectionTool(), CleanupLocalInspectionTool {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return BigDecimalInspectionVisitor(holder)
    }

    private class BigDecimalInspectionVisitor(
        private val problemsHolder: ProblemsHolder
    ) : JavaElementVisitor() {

        /**
         * Use constant instead of BigDecimal.valueOf()
         */
        override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
            if (!CONSTRUCTOR_METHOD.test(call)) return
            val bigDecimalLiteral = getBigDecimalLiteral(call)
            if (bigDecimalLiteral != null) {
                val fix = BigDecimalConstantQuickFix(bigDecimalLiteral)
                problemsHolder.registerProblem(call, "Replace with constant", fix)
            }
        }

        /**
         * Use constant instead of new BigDecimal()
         */
        override fun visitNewExpression(expression: PsiNewExpression) {
            val classReference = expression.classReference ?: return
            if ("java.math.BigDecimal" != classReference.qualifiedName) {
                return
            }
            val argumentList = expression.argumentList
            val arg = argumentList!!.expressions.singleOrNull()
                ?: return // that's ok

            val type = arg.type
            if (INT != type && LONG != type && FLOAT != type && DOUBLE != type
                && !type!!.equalsToText(JAVA_LANG_STRING)
            ) {
                // that's ok because we may have char[] or BigInteger
                return
            }
            if (!PsiUtil.isConstantExpression(arg)) {
                // that's ok if here is a variable
                return
            }
            val constVal = ExpressionUtils.computeConstantExpression(arg)
            val bigDecimalLiteral: String?
            if (constVal is Number) {
                bigDecimalLiteral = numberToBigDecimalLiteral(constVal)
            } else if (constVal is String) {
                bigDecimalLiteral = strToBigDecimalLiteral(constVal)
            } else {
                bigDecimalLiteral = null // that's ok e.g. 12.34
            }

            if (bigDecimalLiteral != null) {
                val fix = BigDecimalConstantQuickFix(bigDecimalLiteral)
                problemsHolder.registerProblem(expression, "Replace with constant", fix)
            }
        }

        private fun getBigDecimalLiteral(call: PsiMethodCallExpression): String? {
            val argumentList = call.argumentList
            val arguments = argumentList.expressions
            if (arguments.size != 1) {
                LOG.error("WTF? " + arguments.size)
                return null
            }
            val arg = arguments[0]
            val type = arg.type
            if (INT != type && LONG != type && FLOAT != type && DOUBLE != type) {
                LOG.error("WTF? " + type!!)
                return null
            }
            if (!PsiUtil.isConstantExpression(arg)) {
                // that's ok if here is a variable
                return null
            }

            val constVal = ExpressionUtils.computeConstantExpression(arg)
            if (constVal !is Number) {
                LOG.error("WTF? " + constVal!!.javaClass.simpleName)
                return null
            }

            return numberToBigDecimalLiteral(constVal)
        }

        private fun numberToBigDecimalLiteral(number: Number): String? {
            val doubleValue = number.toDouble()
            if (!isInt(doubleValue)) {
                // that's ok
                return null
            }
            return when (number.toInt()) {
                0 -> "BigDecimal.ZERO"
                1 -> "BigDecimal.ONE"
                10 -> "BigDecimal.TEN"
                else -> null // that's ok
            }
        }

        fun isInt(d: Double): Boolean {
            return d == d.toInt().toDouble()
        }

        private fun strToBigDecimalLiteral(str: String): String? {
            return when (str) {
                "0" -> "BigDecimal.ZERO"
                "1" -> "BigDecimal.ONE"
                "10" -> "BigDecimal.TEN"
                else -> null // that's ok
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(BigDecimalConstantInspection::class.java)

        private val CONSTRUCTOR_METHOD = CallMatcher.anyOf(
            CallMatcher.staticCall("java.math.BigDecimal", "valueOf").parameterTypes("long"),
            CallMatcher.staticCall("java.math.BigDecimal", "valueOf").parameterTypes("double")
        )
    }
}
