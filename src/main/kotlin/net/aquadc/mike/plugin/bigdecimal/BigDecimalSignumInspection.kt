package net.aquadc.mike.plugin.bigdecimal

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.NamedReplacementFix
import net.aquadc.mike.plugin.UastInspection
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UastCallKind
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
    ): AbstractUastNonRecursiveVisitor = object : FunctionCallVisitor(comparison = true) {

        override fun visitCallExpr(
            node: UExpression, src: PsiElement, kind: UastCallKind, operator: String?,
            declaringClassFqn: String, receiver: UExpression?, methodName: String, valueArguments: List<UExpression>,
        ): Boolean {
            check(src, operator, declaringClassFqn, receiver, methodName, valueArguments)
            return true
        }
        private fun check(
            src: PsiElement, op: String?,
            declaringClassFqn: String, receiver: UExpression?, methodName: String, valueArguments: List<UExpression>,
        ) {
            if (declaringClassFqn != TBigDecimal && declaringClassFqn != TBigInteger) return
            if (methodName != "compareTo") return
            val arg = valueArguments.singleOrNull() as? UResolvable ?: return
            val field = (arg.resolve() as? PsiField)
                ?.takeIf { it.name == "ZERO" && it.type.canonicalText.isBigNumber() } ?: return
            val cls = field.containingClass?.qualifiedName?.takeIf(String::isBigNumber) ?: return
            val unqualified = cls.substring(cls.lastIndexOf('.') + 1)
            holder.registerProblem(
                src,
                "$unqualified.compareTo(ZERO) can be replaced with signum()",
                NamedReplacementFix(
                    "Replace .compareTo(ZERO) with .signum()",
                    (receiver?.sourcePsi?.text?.plus('.') ?: "") + "signum()",
                    (op?.let { (receiver?.sourcePsi?.text?.plus('.') ?: "") + "signum() $it 0" } ?: "signum()")
                )
            )
        }
    }
}
