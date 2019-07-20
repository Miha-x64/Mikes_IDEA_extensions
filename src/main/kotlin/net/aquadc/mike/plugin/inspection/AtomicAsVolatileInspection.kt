package net.aquadc.mike.plugin.inspection

import com.intellij.codeInspection.*
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
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import java.util.concurrent.atomic.*


class AtomicAsVolatileInspection : UastInspection() {

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

    override fun uVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): AbstractUastNonRecursiveVisitor =
        object : AbstractUastNonRecursiveVisitor() {
            override fun visitField(node: UField): Boolean {
                checkField(holder, node)
                return true
            }

        }

    private fun checkField(holder: ProblemsHolder, field: UField) {
        val src = field.sourceElement ?: return
        val qualifiedName = PsiUtil.resolveClassInType(field.type)?.qualifiedName ?: return

        if (qualifiedName in atomics && isAtomicAbused(src)) {
            holder.registerProblem(
                field.uTypeElement,
                "${field.type.presentableText} can be replaced with volatile"
            )
        }
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
