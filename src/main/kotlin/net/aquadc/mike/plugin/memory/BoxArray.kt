package net.aquadc.mike.plugin.memory

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import net.aquadc.mike.plugin.NamedReplacementFix
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.util.*

/**
 * @author Mike Gorünóv
 */
class BoxArray : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            // ensure callee comes from `kotlin` package
            val ref = expression.calleeExpression ?: return
            val callee = ref.mainReference?.resolve() ?: return
            if (callee.getParentOfType<KtFile>(true)?.packageDirective?.fqName?.asString() != "kotlin") return

            // ensure callee is either `arrayOf()` or `Array() {}`
            val name = callee.namedUnwrappedElement?.name
            val of = name == "arrayOf"
            if (!of && name != "Array") return

            // check that expression type is really array and figure out element type if primitive and not nullable
            val elTypeName = analyze(expression) {
                val t = expression.expressionType
                if (t?.isClassType(StandardClassIds.Array) == true)
                    t.arrayElementType
                        ?.takeIf { !it.isNullable && it.isPrimitive }
                        ?.expandedSymbol?.classId?.shortClassName?.identifier
                else null
            } ?: return

            // done
            holder.registerProblem(
                ref,
                "Array of boxed ${elTypeName}s",
                ProblemHighlightType.WEAK_WARNING,
                NamedReplacementFix(
                    "Replace with array of primitives",
                    if (of) "${elTypeName.lowercase(Locale.ROOT)}ArrayOf" else "${elTypeName}Array"
                ),
            )
        }
    }

}
