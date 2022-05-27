package net.aquadc.mike.plugin.bigdecimal

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.siyeh.ig.callMatcher.CallMatcher

import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.NamedReplacementFix
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.test
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastCallKind
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

        override fun visitCallExpr(
            node: UExpression, src: PsiElement, kind: UastCallKind, operator: String?,
            declaringClassFqn: String, receiver: UExpression?, methodName: String, valueArguments: List<UExpression>,
        ): Boolean {
            when (kind) {
                METHOD_CALL -> visitMethodCall(src, declaringClassFqn, valueArguments)
                CONSTRUCTOR_CALL -> visitNewExpression(src, declaringClassFqn, valueArguments)
            }
            return true
        }

        /**
         * Use constant instead of BigDecimal.valueOf()
         */
        private fun visitMethodCall(src: PsiElement, declaringClassFqn: String, args: List<UExpression>) {
            if (CONSTRUCTOR_METHOD.test(src)) {
                (args.singleOrNull()?.let(UExpression::evaluate) as? Number)
                    ?.let(::constantOfNumber)?.let { replacement ->
                        complain(src, declaringClassFqn, replacement)
                    }
            }
        }

        /**
         * Use constant instead of new BigDecimal()
         */
        fun visitNewExpression(src: PsiElement, declaringClassFqn: String, args: List<UExpression>) {
            val argVal = args.takeIf { declaringClassFqn.isBigNumber() }?.singleOrNull()?.evaluate() ?: return

            when (argVal) {
                is Number -> constantOfNumber(argVal)
                is String -> constantOfString(argVal)
                else -> null // that's ok e.g. 12.34
            }?.let { replacement ->
                complain(src, declaringClassFqn, replacement)
            }
        }

        private fun complain(src: PsiElement, prefix: String, replacement: String) {
            val call = (src as? KtElement)?.parent as? KtDotQualifiedExpression ?: src
            val unqualified = prefix.substring(prefix.lastIndexOf('.') + 1) + '.' + replacement
            holder.registerProblem(
                call,
                "${call.text} should be replaced with $unqualified constant",
                NamedReplacementFix("Replace instantiation with constant", "$prefix.$replacement")
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

}

fun String.isBigNumber() = this == TBigDecimal || this == TBigInteger

const val TBigDecimal = "java.math.BigDecimal"
const val TBigInteger = "java.math.BigInteger"

private val CONSTRUCTOR_METHOD = CallMatcher.staticCall(TBigDecimal, "valueOf").let {
    CallMatcher.anyOf(
        it.parameterTypes("long"),
        it.parameterTypes("double"),
        CallMatcher.staticCall(TBigInteger, "valueOf").parameterTypes("long"),
    )
}