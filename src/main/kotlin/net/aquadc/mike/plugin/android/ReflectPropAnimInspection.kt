package net.aquadc.mike.plugin.android

import com.android.tools.idea.kotlin.tryEvaluateConstant
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.PsiUtilCore
import com.siyeh.ig.PsiReplacementUtil
import com.siyeh.ig.callMatcher.CallMatcher
import com.siyeh.ig.psiutils.ExpressionUtils
import net.aquadc.mike.plugin.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor


typealias ReplacementBuilder =
        (qualifier: String?, callee: String, args: List<PsiElement>, argIndices: IntArray, replacements: Array<out String>) -> String

private interface ArgsPredicate {
    fun java(args: Array<PsiExpression>): Boolean
    fun kotlin(args: List<KtExpression>): Boolean
}
private val dontMindArgs = object : ArgsPredicate {
    override fun java(args: Array<PsiExpression>): Boolean = true
    override fun kotlin(args: List<KtExpression>): Boolean = true
}

class ReflectPropAnimInspection : UastInspection() {

    override fun uVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): AbstractUastNonRecursiveVisitor =
        object : AbstractUastNonRecursiveVisitor() {

            override fun visitExpression(node: UExpression): Boolean {
                val srcPsi = node.sourcePsi
                if (srcPsi != null) {
                    MATCHERS_TO_FIXES
                        .firstOrNull { it.matcher.test(srcPsi) }
                        ?.let { fixer ->
                            report(when (srcPsi) {
                                is PsiMethodCallExpression -> fixer.create(srcPsi)
                                is KtExpression -> fixer.create(srcPsi)
                                else -> null
                            }, srcPsi)
                        }
                }

                return super.visitExpression(node)
            }

            private fun report(fix: LocalQuickFix?, srcPsi: PsiElement) {
                if (fix == null) {
                    holder.registerProblem(srcPsi, "Reflective property animation")
                } else {
                    holder.registerProblem(srcPsi, "Reflective property animation", fix)
                }
            }

        }

    private class MatcherToFix(
        val matcher: CallMatcher,
        private val canReplace: ArgsPredicate,
        private val argIndices: IntArray,
        private val buildReplacement: ReplacementBuilder
    ) {
        fun create(call: PsiMethodCallExpression): LocalQuickFix? {
            val args = call.argumentList.expressions
            val replacements = arrayOfNulls<String>(argIndices.size)
            for ((index, argIndex) in argIndices.withIndex()) {
                val arg = args[argIndex]
                if (arg is PsiExpression && PsiUtil.isConstantExpression(arg)) {
                    replacements[index] =
                        VIEW_PROPERTY_FIXES[ExpressionUtils.computeConstantExpression(arg)]
                } else {
                    return null
                }
            }

            @Suppress("UNCHECKED_CAST")
            replacements as Array<String>

            return if (canReplace.java(args)) {
                val qual = call.methodExpression.qualifierExpression
                call.methodExpression.referenceName?.let { callee ->
                    val repl = buildReplacement(qual?.text, callee, args.asList(), argIndices, replacements)
                    object : NamedLocalQuickFix(FIX_NAME) {
                        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                            PsiReplacementUtil.replaceExpressionAndShorten(
                                descriptor.psiElement as PsiExpression,
                                repl
                            )
                        }
                    }
                }
            } else null
        }

        @Suppress("UNCHECKED_CAST")
        fun create(expr: KtExpression): LocalQuickFix? {
            val args = when (expr) {
                is KtCallExpression -> expr.valueArguments.map(KtValueArgument::getArgumentExpression)
                is KtBinaryExpression -> listOf(expr.right)
                else -> return null
            }
            val replacements = arrayOfNulls<String>(argIndices.size)
            for ((index, argIndex) in argIndices.withIndex()) {
                args[argIndex]?.let { arg ->
                    replacements[index] = VIEW_PROPERTY_FIXES[arg.tryEvaluateConstant() ?: return null]
                } ?: return null
            }

            args as List<KtExpression/*!!*/>
            replacements as Array<String/*!!*/>

            return if (canReplace.kotlin(args)) {
                val qual: String?
                val callee: KtExpression?
                when (expr) {
                    is KtCallExpression -> {
                        qual = expr.getQualifiedExpressionForReceiver()?.text
                        callee = expr.calleeExpression
                    }
                    is KtBinaryExpression -> {
                        qual = expr.leftQual?.text
                        callee = expr.leftRef
                    }
                    else -> error("unreachable")
                }
                (callee as? KtNameReferenceExpression)?.getReferencedName()?.let { callee ->
                    val repl = buildReplacement(qual, callee, args, argIndices, replacements) // todo: replacement could be binary expressions, too
                    object : NamedLocalQuickFix(FIX_NAME) {
                        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                            descriptor.psiElement.let {
                                it.replace(KtPsiFactory(it).createExpression(repl))
                            }
                        }
                    }
                }
            } else null
        }
    }

    private companion object {
        private const val FIX_NAME = "Replace property name with explicit Property instance"

        private const val AA = "android.animation"
        private const val TObjectAnimator = "$AA.ObjectAnimator"
        private const val TPropValsHolder = "$AA.PropertyValuesHolder"
        private const val TPath = "android.graphics.Path"
        private const val TView = "android.view.View"
        private const val TObject = "java.lang.Object"
        private const val TString = "java.lang.String"
        private val overload: ReplacementBuilder = { qual, meth, args, indices, replacements ->
            buildString {
                appendQualifier(qual).append(meth).appendArgs(args, indices, replacements)
            }
        }
        private val firstArgMustBeView = object : ArgsPredicate {
            override fun java(args: Array<PsiExpression>): Boolean = args[0].let { arg ->
                arg.type?.let { arg.getTypeByName(TView).isAssignableFrom(it) } == true
            }
            override fun kotlin(args: List<KtExpression>): Boolean = true // TODO
        }
        private val firstArgMustBeViewClass = object : ArgsPredicate {
            override fun java(args: Array<PsiExpression>): Boolean = args[0].let { arg ->
                ((arg.type as? PsiClassType)?.typeArguments()?.firstOrNull() as? PsiType)?.let {
                    arg.getTypeByName(TView).isAssignableFrom(it)
                } == true
            }
            override fun kotlin(args: List<KtExpression>): Boolean = true // TODO
        }
        private val MATCHERS_TO_FIXES = arrayOf(
            MatcherToFix(CallMatcher.anyOf(
                CallMatcher.exactInstanceCall(TObjectAnimator, "setPropertyName").parameterTypes(TString),
                CallMatcher.exactInstanceCall(TPropValsHolder, "setPropertyName").parameterTypes(TString),
            ), dontMindArgs, intArrayOf(0) /* raw type, nothing to check */) { qual, _, args, indices, replacements ->
                buildString { appendQualifier(qual).append("setProperty").appendArgs(args, indices, replacements) }
            },
            MatcherToFix(CallMatcher.anyOf(
                CallMatcher.staticCall(TObjectAnimator, "ofInt").parameterTypes(TObject, TString, "int[]"),
                CallMatcher.staticCall(TObjectAnimator, "ofMultiInt").parameterTypes(TObject, TString, null /* int[][] | Path */),
                CallMatcher.staticCall(TObjectAnimator, "ofMultiInt").parameterTypes(TObject, TString, "$AA.TypeConverter<T,int[]>", "$AA.TypeEvaluator<T>", "T[]"),
                CallMatcher.staticCall(TObjectAnimator, "ofArgb").parameterTypes(TObject, TString, "int[]"),
                CallMatcher.staticCall(TObjectAnimator, "ofFloat").parameterTypes(TObject, TString, "float[]"),
                CallMatcher.staticCall(TObjectAnimator, "ofMultiFloat").parameterTypes(TObject, TString, null /* float[][] | Path */),
                CallMatcher.staticCall(TObjectAnimator, "ofMultiFloat").parameterTypes(TObject, TString, "$AA.TypeConverter<T,float[]>", "$AA.TypeEvaluator<T>", "T[]"),
                CallMatcher.staticCall(TObjectAnimator, "ofObject").parameterTypes(TObject, TString, "$AA.TypeEvaluator", "java.lang.Object[]"),
                CallMatcher.staticCall(TObjectAnimator, "ofObject").parameterTypes(TObject, TString, "$AA.TypeConverter<android.graphics.PointF,?>", TPath),
            ), firstArgMustBeView, intArrayOf(1), overload),
            MatcherToFix(CallMatcher.anyOf(
                CallMatcher.staticCall(TObjectAnimator, "ofInt").parameterTypes(TObject, TString, TString, TPath),
                CallMatcher.staticCall(TObjectAnimator, "ofFloat").parameterTypes(TObject, TString, TString, TPath),
            ), firstArgMustBeView, intArrayOf(1, 2), overload),
            MatcherToFix(CallMatcher.anyOf(
                CallMatcher.staticCall(TPropValsHolder, "ofInt").parameterTypes(TString, "int[]"),
                CallMatcher.staticCall(TPropValsHolder, "ofMultiInt").parameterTypes(TString, null /* int[][] | Path */),
                CallMatcher.staticCall(TPropValsHolder, "ofMultiInt").parameterTypes(TString, "$AA.TypeConverter<V,int[]>", "$AA.TypeEvaluator<V>", "V[]"),
                CallMatcher.staticCall(TPropValsHolder, "ofMultiInt").parameterTypes(TString, "$AA.TypeConverter<T,int[]>", "$AA.TypeEvaluator<T>", "$AA.Keyframe[]"),
                CallMatcher.staticCall(TPropValsHolder, "ofFloat").parameterTypes(TString, "float[]"),
                CallMatcher.staticCall(TPropValsHolder, "ofMultiFloat").parameterTypes(TString, null /* float[][] | Path */),
                CallMatcher.staticCall(TPropValsHolder, "ofMultiFloat").parameterTypes(TString, "$AA.TypeConverter<V,float[]>", "$AA.TypeEvaluator<V>", "V[]"),
                CallMatcher.staticCall(TPropValsHolder, "ofMultiFloat").parameterTypes(TString, "$AA.TypeConverter<T,float[]>", "$AA.TypeEvaluator<T>", "$AA.Keyframe[]"),
                CallMatcher.staticCall(TPropValsHolder, "ofObject").parameterTypes(TString, "$AA.TypeEvaluator", "java.lang.Object[]"),
                CallMatcher.staticCall(TPropValsHolder, "ofObject").parameterTypes(TString, "$AA.TypeConverter<android.graphics.PointF,?>", TPath),
                CallMatcher.staticCall(TPropValsHolder, "ofKeyframe").parameterTypes(TString, "$AA.Keyframe[]"),
            ), dontMindArgs /* too complicated, just don't mind :) */, intArrayOf(0), overload),
            MatcherToFix(CallMatcher.anyOf(
                CallMatcher.staticCall("android.util.Property", "of").parameterTypes("java.lang.Class<T>", "java.lang.Class<V>", TString),
            ), firstArgMustBeViewClass, intArrayOf(2)) { _, _, _, _, repl -> TView + '.' + repl[0] },
        )

        private fun PsiElement.getTypeByName(name: String): PsiClassType =
            PsiUtilCore.getProjectInReadAction(this).let {
                PsiType.getTypeByName(name, it, GlobalSearchScope.allScope(it))
            }

        private val VIEW_PROPERTY_FIXES = hashMapOf(
            "alpha" to "ALPHA",
            "translationX" to "TRANSLATION_X",
            "translationY" to "TRANSLATION_Y",
            "translationZ" to "TRANSLATION_Z",
            "x" to "X",
            "y" to "Y",
            "z" to "Z",
            "rotation" to "ROTATION",
            "rotationX" to "ROTATION_X",
            "rotationY" to "ROTATION_Y",
            "scaleX" to "SCALE_X",
            "scaleY" to "SCALE_Y",
        )

        private fun StringBuilder.appendQualifier(qual: String?): StringBuilder {
            qual?.let { append(it).append('.') }
            return this
        }
        private fun StringBuilder.appendArgs(
            args: List<PsiElement>, indices: IntArray, replacements: Array<out String>
        ) {
            append('(')
            if (args.isNotEmpty()) {
                args.forEachIndexed { index, expression ->
                    val pos = indices.indexOf(index)
                    (if (pos >= 0) append(TView).append('.').append(replacements[pos]) else append(expression.text)).append(", ")
                }
                setLength(length - 2)
            }
            append(')')
        }
    }

}
