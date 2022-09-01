package net.aquadc.mike.plugin.android

import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPrimitiveType
import com.siyeh.ig.callMatcher.CallMatcher
import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.test
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * @author Mike Gorünóv
 */
class UnsupportedFeatureInspection : UastInspection() {
    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean
    ): AbstractUastNonRecursiveVisitor = object : FunctionCallVisitor() {

        override fun visitImportStatement(node: UImportStatement): Boolean {
            val psi = (node.sourcePsi as? KtImportDirective)
            if (psi != null && psi.importPath?.pathStr?.startsWith("kotlinx.android.synthetic") == true) {
                holder.registerProblem(
                    psi.importedReference ?: psi,
                    "Kotlin Android Extensions are deprecated",
                    ProblemHighlightType.LIKE_DEPRECATED,
                )
            }
            return true
        }

        override fun visitCallExpr(
            node: UExpression, src: PsiElement, kind: UastCallKind, operator: String?,
            declaringClassFqn: String, receiver: UExpression?, methodName: String, valueArguments: List<UExpression>,
        ): Boolean {
            (node as? UCallExpression)?.let { checkListenerSupport(it, src) }
            checkObtainStyled(src, declaringClassFqn, methodName, valueArguments)
            return true
        }
        private fun checkListenerSupport(node: UCallExpression, src: PsiElement) {
            src.containingFile?.androidFacet?.androidMinSdk()?.apiLevel?.takeIf { it < 26 } ?: return
            val className = (node.receiverType as? PsiClassType)?.resolve()?.qualifiedName ?: return
            if (className == "android.widget.VideoView" && View_setOnClickListener.test(src))
                holder.registerProblem(
                    node.methodIdentifier?.sourcePsi ?: src,
                    "VideoView click listener does not work before SDK 26",
                    // clicks were ignored by not calling super:
                    // https://github.com/aosp-mirror/platform_frameworks_base/blob/lollipop-mr1-release/core/java/android/widget/VideoView.java#L661
                )
            else if (className == "androidx.recyclerview.widget.RecyclerView" && View_setOnClickListener.test(src))
                holder.registerProblem(
                    node.methodIdentifier?.sourcePsi ?: src,
                    "RecyclerView click listener does not work"
                    // clicks are ignored by not calling super:
                    // https://android.googlesource.com/platform/frameworks/support/+/64cde70e6cbe6ec9b743786aad419a854b18d89f/recyclerview/recyclerview/src/main/java/androidx/recyclerview/widget/RecyclerView.java#3709
                )
        }
        private fun checkObtainStyled(src: PsiElement, declaringClassFqn: String, method: String, args: List<UExpression>) {
            if (declaringClassFqn == "android.content.Context" && method == "obtainStyledAttributes") {
                val attrs = (args.singleOrNull() ?: args.getOrNull(1) ?: return)
                attrs.intArrayElementExpressions()?.takeIf { it.size > 1 }?.let { attrExprs ->
                    val attrValues = attrExprs.evaluateIntElements()
                    var prev = attrValues.first()
                    if (prev != Long.MIN_VALUE) {
                        for (i in 1 until attrValues.size) {
                            val curr = attrValues[i]
                            if (curr == Long.MIN_VALUE) break
                            if (curr < prev) {
                                val prevSrc = attrExprs[i - 1].sourcePsi?.takeIf { it.containingFile == src.containingFile }
                                val currSrc = attrExprs[i].sourcePsi?.takeIf { it.containingFile == src.containingFile }
                                if (prevSrc != null && currSrc != null) {
                                    holder.registerProblem(holder.manager.createProblemDescriptor(
                                        prevSrc, currSrc,
                                        "Context.obtainStyledAttributes() needs a sorted array. " +
                                                "Swap these elements",
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                        isOnTheFly
                                    ))
                                } else {
                                    holder.registerProblem(
                                        src,
                                        "Context.obtainStyledAttributes() needs a sorted array. " +
                                                "Swap elements at ${i-1} and $i",
                                    )
                                }
                                break
                            }
                            prev = curr
                        }
                    }
                }
            }
        }

        private fun UExpression.intArrayElementExpressions(): List<UExpression>? {
            var expr = this
            val log = Logger.getInstance(UnsupportedFeatureInspection::class.java)
            while (true) {
                expr = when (expr) {
                    is UParenthesizedExpression -> expr.expression
                    is UCallExpression ->
                        when (expr.kind) {
                            UastCallKind.METHOD_CALL -> {
                                if (expr.methodName == "intArrayOf" && expr.receiver == null)
                                    return expr.valueArguments
                                else {
                                    val resolved = expr.tryResolve()
                                    return expr.sourcePsi?.let { log.error("$it | ${expr.javaClass} | ${resolved?.javaClass}"); null }
                                }
                            }
                            UastCallKind.CONSTRUCTOR_CALL ->
                                return expr.sourcePsi?.let { log.error("$it | ${expr.javaClass}"); null }
                            else -> {
                                if (expr.returnType == INT_ARRAY)
                                    return expr.valueArguments
                                else
                                    return expr.sourcePsi?.let { log.error("$it | ${expr.javaClass}"); null }
                            }
                        }

                    is UReferenceExpression -> {
                        val referrent = expr.resolveToUElement()
                        (referrent as? UVariable)?.uastInitializer
                            ?: ((referrent as? UMethod)?.sourcePsi as? KtProperty)?.initializer
                                ?.toUElementOfType<UExpression>()?.also { expr = it }
                            ?: return expr.sourcePsi?.let { log.error("$it | ${expr.javaClass} | $referrent"); null }
                    }
                    else -> {
                        return expr.sourcePsi?.let { log.error("$it | ${expr.javaClass}"); null }
                    }
                }
            }
        }
        private fun List<UExpression>.evaluateIntElements(): LongArray = LongArray(size) {
            (get(it).evaluate() as? Int)?.toLong() ?: Long.MIN_VALUE
        }
    }

    private companion object {
        private val View_setOnClickListener =
            CallMatcher.instanceCall("android.view.View", "setOnClickListener")
                .parameterTypes("android.view.View.OnClickListener")

        private val INT_ARRAY = PsiPrimitiveType.INT.createArrayType()
    }
}
