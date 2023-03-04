package net.aquadc.mike.plugin.memory

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import net.aquadc.mike.plugin.NamedReplacementFix
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.js.descriptorUtils.nameIfStandardType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.getClassFqNameUnsafe
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.kotlin.types.typeUtil.nullability
import java.util.*

/**
 * @author Mike Gorünóv
 */
class BoxArray : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            val ref = expression.calleeExpression ?: return
            val callee = ref.mainReference?.resolve() ?: return
            if (callee.getParentOfType<KtFile>(true)?.packageDirective?.fqName?.asString() != "kotlin") return
            val name = callee.namedUnwrappedElement?.name
            val of = name == "arrayOf"
            if (!of && name != "Array") return
            val type = expression.analyze().getType(expression) ?: return
            if (type.constructor.getClassFqNameUnsafe().asString() != "kotlin.Array") return
            val elType = type.arguments.singleOrNull()?.takeIf { it.projectionKind == Variance.INVARIANT }
                ?.type?.takeIf { it.nullability() == TypeNullability.NOT_NULL } ?: return
            val elTypeName = elType.nameIfStandardType?.asString()?.takeIf { it in typeNames } ?: return
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

    private companion object {
        private val typeNames = arrayOf("Boolean", "Byte", "Short", "Char", "Int", "Long", "Float", "Double")
    }

}
