package net.aquadc.mike.plugin.bigdecimal

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
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
import java.math.BigDecimal
import java.math.BigInteger

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
         * Use constant instead of Big(Decimal|Integer).valueOf(long | double)
         */
        private fun visitMethodCall(src: PsiElement, declaringClassFqn: String, args: List<UExpression>) {
            if (CONSTRUCTOR_METHOD.test(src)) {
                (args.singleOrNull()?.let(UExpression::evaluate) as? Number)?.let {
                    constantOfNumber(it, PsiUtil.getLanguageLevel(src), BI_CONSTRUCTOR_METHOD.test(src))
                }?.let { replacement ->
                    complain(src, declaringClassFqn, replacement)
                }
            }
        }

        /**
         * Use constant instead of new Big(Decimal|Integer)(String)
         */
        fun visitNewExpression(src: PsiElement, declaringClassFqn: String, args: List<UExpression>) {
            val argVal = args.takeIf { declaringClassFqn.isBigNumber() }?.singleOrNull()?.evaluate() ?: return

            when (argVal) {
                is Number ->
                    constantOfNumber(argVal, PsiUtil.getLanguageLevel(src), declaringClassFqn == TBigInteger)
                is String ->
                    try { constantOfNumber(BigDecimal(argVal).toBigIntegerExact(), PsiUtil.getLanguageLevel(src), declaringClassFqn == TBigInteger) }
                    catch (e: NumberFormatException) { null }
                    catch (e: ArithmeticException) { null }
                else ->
                    null // that's ok e.g. 12.34
            }?.let { replacement ->
                complain(src, declaringClassFqn, replacement)
            }
        }

        private fun complain(src: PsiElement, prefix: String, replacement: String) {
            val call = (src as? KtElement)?.parent as? KtDotQualifiedExpression ?: src
            val unqualified = prefix.substring(prefix.lastIndexOf('.') + 1) + '.' + replacement
            holder.registerProblem(
                call,
                "Instantiation should be replaced with a constant",
                NamedReplacementFix("Replace ${call.text} with '$unqualified'", "$prefix.$replacement")
            )
        }

        private fun constantOfNumber(number: Number, languageLevel: LanguageLevel, isBigInt: Boolean): String? {
            return when (number) {
                0, 0L, 0.0, BigInteger.ZERO -> "ZERO"
                1, 1L, 1.0, BigInteger.ONE -> "ONE"
                2, 2L, 2.0, BigInteger.TWO -> "TWO".takeIf { isBigInt && languageLevel >= LanguageLevel.JDK_1_9 }
                10, 10L, 10.0, BigInteger.TEN -> "TEN"
                else -> null // that's ok
            }
        }
    }

}

fun String.isBigNumber() = this == TBigDecimal || this == TBigInteger

const val TBigDecimal = "java.math.BigDecimal"
const val TBigInteger = "java.math.BigInteger"

private val BD_CONSTRUCTOR_METHOD = CallMatcher.staticCall(TBigDecimal, "valueOf").let {
    CallMatcher.anyOf(it.parameterTypes("long"), it.parameterTypes("double"))
}
private val BI_CONSTRUCTOR_METHOD = CallMatcher.staticCall(TBigInteger, "valueOf").parameterTypes("long")
private val CONSTRUCTOR_METHOD = CallMatcher.anyOf(BD_CONSTRUCTOR_METHOD, BI_CONSTRUCTOR_METHOD)