package net.aquadc.mike.plugin.bigdecimal

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.siyeh.ig.callMatcher.CallMatcher

import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.NamedReplacementFix
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.resolvedClassFqn
import net.aquadc.mike.plugin.test
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastCallKind.Companion.CONSTRUCTOR_CALL
import org.jetbrains.uast.UastCallKind.Companion.METHOD_CALL
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * BigDecimal instantiation can be replaced with constant
 * <pre>
 * BigDecimal.valueOf(0) -> BigDecimal.ZERO
 * new BigDecimal(0) -> BigDecimal.ZERO
 * </pre>
 * @author stokito (original BigDecimal inspection for Java)
 * @author Mike Gorünóv (Kotlin and BigInteger support, inspection description)
 */
class BigDecimalConstantInspection : UastInspection(), CleanupLocalInspectionTool {

    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): AbstractUastNonRecursiveVisitor = object : FunctionCallVisitor() {

        override fun visitCallExpr(node: UCallExpression): Boolean {
            when (node.kind) {
                METHOD_CALL -> visitMethodCall(node)
                CONSTRUCTOR_CALL -> visitNewExpression(node)
            }
            return true
        }

        /**
         * Use constant instead of BigDecimal.valueOf()
         */
        private fun visitMethodCall(node: UCallExpression) {
            node.sourcePsi?.let { src ->
                if (CONSTRUCTOR_METHOD.test(src)) {
                    node.resolve()?.containingClass?.qualifiedName?.let { className ->
                        val constVal = node.takeIf { it.valueArgumentCount == 1 }
                            ?.valueArguments?.single()?.let(UExpression::evaluate) as? Number
                        constVal?.let(::constantOfNumber)?.let { replacement ->
                            complain(src, className, replacement)
                        }
                    }
                }
            }
        }

        /**
         * Use constant instead of new BigDecimal()
         */
        fun visitNewExpression(expression: UCallExpression) { // TODO gets triggered several times for single expression
            val src = expression.sourcePsi ?: return
            val className = expression.resolvedClassFqn ?: return
            val argVal = expression
                .takeIf { className == TBigDecimal || className == TBigInteger }
                ?.takeIf { it.valueArgumentCount == 1 }?.valueArguments?.single()
                ?.evaluate() ?: return

            when (argVal) {
                is Number -> constantOfNumber(argVal)
                is String -> constantOfString(argVal)
                else -> null // that's ok e.g. 12.34
            }?.let { replacement ->
                complain(src, className, replacement)
            }
        }

        private fun complain(src: PsiElement, prefix: String, replacement: String) {
            val call = (src as? KtElement)?.getParentOfType<KtDotQualifiedExpression>(true) ?: src
            val unqualified = prefix.substring(prefix.lastIndexOf('.') + 1) + '.' + replacement
            holder.registerProblem(
                call,
                "${call.text} should be replaced with $unqualified constant",
                NamedReplacementFix("$prefix.$replacement", name = "Replace with $unqualified")
            )
        }

        private fun constantOfNumber(number: Number): String? {
            if (!number.toDouble().isInt) {
                // that's ok
                return null
            }
            return when (number.toInt()) {
                0 -> "ZERO"
                1 -> "ONE"
                // TODO "TWO" for BigInteger and Java 9
                10 -> "TEN"
                else -> null // that's ok
            }
        }

        val Double.isInt: Boolean
            get() = this == toInt().toDouble()

        private fun constantOfString(str: String): String? = when (str) {
            "0" -> "ZERO"
            "1" -> "ONE"
            // TODO "TWO" for BigInteger and Java 9
            "10" -> "TEN"
            else -> null // that's ok
        }
    }

    companion object {
        private const val TBigDecimal = "java.math.BigDecimal"
        private const val TBigInteger = "java.math.BigInteger"
        private val CONSTRUCTOR_METHOD = CallMatcher.staticCall(TBigDecimal, "valueOf").let {
            CallMatcher.anyOf(
                it.parameterTypes("long"),
                it.parameterTypes("double"),
                CallMatcher.staticCall(TBigInteger, "valueOf").parameterTypes("long"),
            )
        }
    }
}
