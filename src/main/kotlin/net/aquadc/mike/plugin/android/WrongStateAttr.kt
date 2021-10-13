package net.aquadc.mike.plugin.android

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiExpressionStatement
import net.aquadc.mike.plugin.NamedReplacementFix
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.register
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.UastCallKind.Companion.METHOD_CALL
import org.jetbrains.uast.UastCallKind.Companion.NESTED_ARRAY_INITIALIZER
import org.jetbrains.uast.UastCallKind.Companion.NEW_ARRAY_WITH_INITIALIZER
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.kotlin.idea.KotlinLanguage.INSTANCE as Kotlin

/**
 * @author Mike Gorünóv
 */
class WrongStateAttr : UastInspection() {
    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean
    ): AbstractUastNonRecursiveVisitor = object : AbstractUastNonRecursiveVisitor() {
        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean =
            node.sourcePsi?.language === Kotlin // ugly workaround to skip KtDotQualifiedExpression
        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) =
            true
        override fun visitCallExpression(node: UCallExpression): Boolean =
            if (node.sourcePsi is PsiExpressionStatement) {
                true // some wisdom from IDEA sources, not sure whether it is useful
            } else if (node.kind === UastCallKind.CONSTRUCTOR_CALL &&
                node.resolvedClassFqn == "android.content.res.ColorStateList"
            ) {
                // android.content.res.ColorStateList.ColorStateList(int[][], int[])
                node.valueArguments.firstOrNull()?.asArrayOf()?.valueArguments?.forEach { it.check(holder) }
                true
            } else if (node.kind === METHOD_CALL && node.methodName == "addState" &&
                node.resolvedClassFqn.let {
                    it == "android.animation.StateListAnimator" || it == "android.graphics.drawable.StateListDrawable"
                }
            ) {
                // android.animation.StateListAnimator.addState(int[], android.animation.Animator)
                // android.graphics.drawable.StateListDrawable.addState(int[], android.graphics.drawable.Drawable)
                node.valueArguments.firstOrNull()?.check(holder)
                true
            } else {
                true // skip any other expression
            }

        private val UCallExpression.resolvedClassFqn: String?
            get() = resolve()?.containingClass?.qualifiedName

        private fun UExpression.check(holder: ProblemsHolder) {
            asArrayOf("int")?.valueArguments?.forEach {
                it.sourcePsi?.let { src ->
                    (it.evaluate() as? Int)?.takeIf { it !in STATES }?.let { value ->
                        val name = NAMES.getOrNull(COLLISIONS.indexOf(value))
                        val subj = name?.let { "android.R.attr.$name" } ?: "0x" + value.toString(16)
                        holder.register(
                            src, "$subj is not a state",
                            name?.let { NamedReplacementFix("android.R.attr.state_$name") },
                        )
                    }
                }
            }
        }

        private fun UExpression.asArrayOf(type: String? = null): UCallExpression? =
            if (this is UCallExpression &&
                (kind === NEW_ARRAY_WITH_INITIALIZER || kind === NESTED_ARRAY_INITIALIZER ||
                        (kind === METHOD_CALL && methodName == if (type == null) "arrayOf" else type + "ArrayOf"))
            ) this else null

    }

    private companion object {
        private val NAMES = arrayOf(
            "checkable", "checked", "enabled"
        )
        private val COLLISIONS = intArrayOf(
            16843237, 16843014, 16842766
        )
        private val STATES = intArrayOf(
            16842911, 16842912, 16842910, // state_checkable, state_checked, state_enabled go first, with same indices
            16842922, 16843547, 16843518, 16842914, 16843624, 16843625, 16842921, 16842920, 16842916, 16842908,
            16843623, 16842918, 16843324, 16842917, 16843597, 16842919, 16842913, 16842915, 16842909,
        )
    }

}

/*
android.R$attr excerpt

state_above_anchor = 16842922
state_accelerated = 16843547
state_activated = 16843518
state_active = 16842914
state_checkable = 16842911
state_checked = 16842912
state_drag_can_accept = 16843624
state_drag_hovered = 16843625
state_empty = 16842921
state_enabled = 16842910
state_expanded = 16842920
state_first = 16842916
state_focused = 16842908
state_hovered = 16843623
state_last = 16842918
state_long_pressable = 16843324
state_middle = 16842917
state_multiline = 16843597
state_pressed = 16842919
state_selected = 16842913
state_single = 16842915
state_window_focused = 16842909

checkable = 16843237
checked = 16843014
enabled = 16842766
 */
