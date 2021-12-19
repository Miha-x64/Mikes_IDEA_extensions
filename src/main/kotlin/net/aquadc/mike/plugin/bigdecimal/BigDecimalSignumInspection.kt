package net.aquadc.mike.plugin.bigdecimal

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiField
import com.siyeh.ig.callMatcher.CallMatcher.anyOf
import com.siyeh.ig.callMatcher.CallMatcher.instanceCall
import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.NamedReplacementFix
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.test
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

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
 * @author stokito (original BigDecimal inspection for Java)
 * @author Mike Gorünóv (Kotlin and BigInteger support, inspection description)
 */
class BigDecimalSignumInspection : UastInspection(), CleanupLocalInspectionTool {

    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): AbstractUastNonRecursiveVisitor = object : FunctionCallVisitor() {

        override fun visitCallExpr(node: UCallExpression): Boolean {
            val src = node.sourcePsi?.takeIf(COMPARE_METHOD::test)
                ?: return true
            val arg = node.takeIf { it.valueArgumentCount == 1 }?.valueArguments?.single() as? UResolvable ?: return true
            val field = (arg.resolve() as? PsiField)
                ?.takeIf { it.name == "ZERO" && it.type.canonicalText.isBigNumber() } ?: return true
            val cls = field.containingClass?.qualifiedName?.takeIf(String::isBigNumber) ?: return true
            val unqualified = cls.substring(cls.lastIndexOf('.') + 1)
            holder.registerProblem(
                src,
                "$unqualified.compareTo(ZERO) can be replaced with signum()",
                NamedReplacementFix(
                    "Replace .compareTo(ZERO) with .signum()",
                    (node.receiver?.sourcePsi?.text?.plus('.') ?: "") + "signum()",
                    "signum()",
                )
            )
            return true
        }
    }

    companion object {
        private val COMPARE_METHOD = anyOf(
            instanceCall(TBigDecimal, "compareTo").parameterTypes(TBigDecimal),
            instanceCall(TBigInteger, "compareTo").parameterTypes(TBigInteger),
        )
    }
}
