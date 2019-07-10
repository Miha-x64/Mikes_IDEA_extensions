package net.aquadc.mike.plugin.inspection

import com.intellij.codeInspection.*
import com.intellij.openapi.util.Conditions
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.Processor
import net.aquadc.mike.plugin.SortedArray
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UField
import org.jetbrains.uast.kotlin.KotlinUField
import java.util.concurrent.atomic.*


class AtomicAsVolatileInspection : AbstractBaseUastLocalInspectionTool() {

    private val atomics = SortedArray.of(
        // boxes
        AtomicBoolean::class.java.name, AtomicInteger::class.java.name,
        AtomicLong::class.java.name, AtomicReference::class.java.name,
        // updaters
        AtomicIntegerFieldUpdater::class.java.name,
        AtomicLongFieldUpdater::class.java.name,
        AtomicReferenceFieldUpdater::class.java.name
    )
    private val volatileActions = arrayOf(
        "get", "set"
    )

    override fun getProblemElement(psiElement: PsiElement): PsiNamedElement? {
        return PsiTreeUtil.findFirstParent(psiElement, Conditions.instanceOf(PsiField::class.java)) as PsiNamedElement?
    }

    override fun checkField(field: UField, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        val src = field.sourceElement ?: return null
        val qualifiedName = PsiUtil.resolveClassInType(field.type)?.qualifiedName ?: return null

        return if (qualifiedName in atomics && isAtomicAbused(src)) {
            problem(field, manager, isOnTheFly, "${field.type.presentableText} can be replaced with volatile")
        } else null
    }

    private fun isAtomicAbused(src: PsiElement): Boolean {
        var volatile = false
        val complete = ReferencesSearch.search(src).forEach(Processor { usage: PsiReference ->
            val name = usage.outerMethodName
            when (name) {
                null -> true // continue
                in volatileActions -> {
                    volatile = true
                    true
                }
                else -> false // stop execution, non-volatile action found
            }
        })
        return complete && volatile
    }

    private fun problem(field: UField, manager: InspectionManager, isOnTheFly: Boolean, text: String) = arrayOf(
        manager.createProblemDescriptor(
            field.uTypeElement, text, isOnTheFly,
            LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        )
    )

    private val UField.uTypeElement: PsiElement get() =
        ((this as? KotlinUField)?.sourcePsi as? KtProperty)?.typeReference?.typeElement ?: // Kotlin explicit type, or
        typeReference?.sourcePsi ?: // Java type, or
        uastInitializer?.sourcePsi ?: // initializer expression (let's think that type is inferred), or
        this // the whole field declaration, if something went wrong

    private val PsiReference.outerMethodName: String? get() {
        val el = element

        // Java
        PsiTreeUtil.getParentOfType(el, PsiMethodCallExpression::class.java)?.methodExpression
            ?.takeIf { it.qualifierExpression === this }
            ?.let { (it.reference?.resolve() as PsiMethod?)?.name }
            ?.let { return it }

        // Kotlin
        PsiTreeUtil.getParentOfType(el, KtDotQualifiedExpression::class.java)
            ?.takeIf { it.receiverExpression.references.any { it.element == el } }
            ?.let { (it.selectorExpression as? KtCallExpression)?.calleeExpression?.references }
            ?.forEach { (it.resolve() as? PsiMethod)?.name?.let { return it } }

        return null
    }

}
