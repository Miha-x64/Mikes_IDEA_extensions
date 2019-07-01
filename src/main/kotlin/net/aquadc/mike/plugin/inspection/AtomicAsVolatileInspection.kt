package net.aquadc.mike.plugin.inspection

import com.intellij.codeInspection.*
import com.intellij.openapi.util.Conditions
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import net.aquadc.mike.plugin.SortedArray
import org.jetbrains.kotlin.psi.*
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
        val rawType = (field.type as? PsiClassType)?.rawType() ?: return null

        return if (rawType.canonicalText in atomics && ReferencesSearch.search(src).forEach(isNotSpecificAction)) {
            problem(field, manager, isOnTheFly, "${rawType.presentableText} can be replaced with volatile")
        } else null
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
        if (el is PsiExpression) {
            ((el.parent as? PsiReferenceExpression)?.parent as? PsiMethodCallExpression)?.methodExpression?.let {
                if (this === it.qualifierExpression) {
                    return it.referenceName }
            }
        }

        if (el is KtExpression) {
            el.context?.children?.let {
                if (it.size == 2 && (it[0] as? KtNameReferenceExpression)?.references?.firstOrNull() === this) {
                    val text = (it[1] as? KtCallExpression)?.firstChild?.text
                    return text
                }
            }
        }

        return null
    }

    private val isNotSpecificAction = Processor { usage: PsiReference ->
        usage.outerMethodName.let { it == null || it in volatileActions }
    }

}
