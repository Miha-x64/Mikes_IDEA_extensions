package net.aquadc.mike.plugin.android

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilCore
import com.siyeh.ig.PsiReplacementUtil
import com.siyeh.ig.callMatcher.CallMatcher
import net.aquadc.mike.plugin.*
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.wrapULiteral
import com.intellij.codeInsight.FileModificationService.getInstance as fileModificationService
import com.intellij.lang.java.JavaLanguage.INSTANCE as Java
import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT as TObject
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING as TString
import net.aquadc.mike.plugin.miserlyMapNullize as mapNullize
import org.jetbrains.kotlin.idea.KotlinLanguage.INSTANCE as Kotlin
import org.jetbrains.kotlin.idea.core.ShortenReferences.Companion.DEFAULT as ShortenRefs


typealias ArgsPredicate = (args: List<UExpression>) -> Boolean

/**
 * @author Mike Gorünóv
 */
class ReflectPropAnimInspection : UastInspection(), CleanupLocalInspectionTool {

    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): AbstractUastNonRecursiveVisitor = object : FunctionCallVisitor() {

        override fun visitCallExpr(
            node: UExpression, src: PsiElement, kind: UastCallKind, operator: String?,
            declaringClassFqn: String, receiver: UExpression?, methodName: String, valueArguments: List<UExpression>,
        ): Boolean {
            node.sourcePsi?.let { srcPsi ->
                MATCHERS_TO_FIXES.firstOrNull { it.tryReport(holder, valueArguments, srcPsi) }
            }

            return true
        }
    }

    private class MatcherToFix(
        private val matcher: CallMatcher,
        private val isStandardViewProperty: ArgsPredicate,
        private val argIndices: IntArray,
        private val replaceMethodWith: String?, // null: don't; "": get rid of; "$name": name
    ) {
        fun tryReport(holder: ProblemsHolder, args: List<UExpression>, srcPsi: PsiElement): Boolean {
            if (!matcher.test(srcPsi)) return false

            val replacements = if (isStandardViewProperty(args) && argIndices.all { i -> args[i].sourcePsi != null })
                argIndices.mapNullize { argIndex ->
                    args[argIndex].evaluateString()?.let { VIEW_PROPERTY_FIXES[it.replaceFirstChar(Char::lowercase)] }
                } else null


            val start = args[argIndices.first()].sourcePsi
            val end = args[argIndices.last()].sourcePsi
            holder.registerProblem(holder.manager.createProblemDescriptor(
                start ?: srcPsi,
                end.takeIf { start != null } ?: srcPsi,
                "Reflective property animation",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                holder.isOnTheFly,
                *fixes(replacements?.let { fix(it) })
            ))

            return true
        }
        private fun fix(
            replacements: Array<String>
        ) = object : NamedLocalQuickFix("Replace property name with explicit Property instance") {
            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                val uCall = descriptor.startElement.toUElement()?.getParentOfType<UCallExpression>(false) ?: return
                val call: PsiElement = uCall.sourcePsi ?: return
                if (fileModificationService().preparePsiElementForWrite(call)) {
                    val args = uCall.valueArguments
                    val lang = call.language
                    if (replaceMethodWith == "") {
                        if (lang == Java) PsiReplacementUtil.replaceExpression(
                            call as PsiExpression,
                            TView + '.' + replacements.single()
                        ) else if (lang == Kotlin) CodeStyleManager.getInstance(call.project).reformat(
                            ShortenRefs.process(
                                (call.parent as? KtDotQualifiedExpression ?: call).replace(
                                    KtPsiFactory(call.project).createExpression(TView + '.' + replacements.single())
                                ) as KtElement
                            )
                        )
                    } else {
                        if (lang == Java) {
                            argIndices.forEachIndexed { i, argIdx ->
                                (args[argIdx].sourcePsi as? PsiExpression)?.let { srcArg ->
                                    val replacement = replacements[i]
                                    PsiReplacementUtil.replaceExpressionAndShorten(srcArg, "$TView.$replacement")
                                }
                            }
                        } else if (lang == Kotlin) {
                            val kt = KtPsiFactory((call as KtElement).project)
                            argIndices.forEachIndexed { i, argIdx ->
                                val replacement = replacements[i]
                                wrapULiteral(args[argIdx]).sourcePsi?.replace(
                                    kt.createExpression("$TView.$replacement")
                                )
                            }
                            CodeStyleManager.getInstance(call.project).reformat(ShortenRefs.process(call))
                        }
                        if (replaceMethodWith != null) {
                            if (lang == Java) {
                                PsiReplacementUtil.replaceExpressionAndShorten(
                                    (call as PsiMethodCallExpression).methodExpression,
                                    (call.methodExpression.qualifierExpression?.text?.let { "$it." } ?: "") +
                                            replaceMethodWith
                                )
                            } else if (lang == Kotlin) {
                                (call as KtCallExpression).calleeExpression
                                    ?.replace(KtPsiFactory(call.project).createExpression(replaceMethodWith))
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        private const val AA = "android.animation"
        private const val TObjectAnimator = "$AA.ObjectAnimator"
        private const val TPropValsHolder = "$AA.PropertyValuesHolder"
        private const val TPath = "android.graphics.Path"
        private const val TView = "android.view.View"
        private val dontMindArgs: ArgsPredicate = { true }
        private val firstArgMustBeView: ArgsPredicate = { (arg,) ->
            arg.getExpressionType()?.let { arg.getTypeByName(TView)?.isAssignableFrom(it) } == true
        }
        private val firstArgMustBeViewClass: ArgsPredicate = { (arg,) ->
            ((arg.getExpressionType() as? PsiClassType)?.typeArguments()?.firstOrNull() as? PsiType)
                ?.let { arg.getTypeByName(TView)?.isAssignableFrom(it) } == true
        }
        private val MATCHERS_TO_FIXES = arrayOf(
            MatcherToFix(
                CallMatcher.anyOf(
                    CallMatcher.exactInstanceCall(TObjectAnimator, "setPropertyName").parameterTypes(TString),
                    CallMatcher.exactInstanceCall(TPropValsHolder, "setPropertyName").parameterTypes(TString),
                ), dontMindArgs, intArrayOf(0) /* raw type, nothing to check */, "setProperty"
            ),
            MatcherToFix(
                CallMatcher.anyOf(
                    CallMatcher.staticCall(TObjectAnimator, "ofInt").parameterTypes(TObject, TString, "int[]"),
                    CallMatcher.staticCall(TObjectAnimator, "ofMultiInt").parameterTypes(TObject, TString, null /* int[][] | Path */),
                    CallMatcher.staticCall(TObjectAnimator, "ofMultiInt").parameterTypes(TObject, TString, "$AA.TypeConverter<T,int[]>", "$AA.TypeEvaluator<T>", "T[]"),
                    CallMatcher.staticCall(TObjectAnimator, "ofArgb").parameterTypes(TObject, TString, "int[]"),
                    CallMatcher.staticCall(TObjectAnimator, "ofFloat").parameterTypes(TObject, TString, "float[]"),
                    CallMatcher.staticCall(TObjectAnimator, "ofMultiFloat").parameterTypes(TObject, TString, null /* float[][] | Path */),
                    CallMatcher.staticCall(TObjectAnimator, "ofMultiFloat").parameterTypes(TObject, TString, "$AA.TypeConverter<T,float[]>", "$AA.TypeEvaluator<T>", "T[]"),
                    CallMatcher.staticCall(TObjectAnimator, "ofObject").parameterTypes(TObject, TString, "$AA.TypeEvaluator", "java.lang.Object[]"),
                    CallMatcher.staticCall(TObjectAnimator, "ofObject").parameterTypes(TObject, TString, "$AA.TypeConverter<android.graphics.PointF,?>", TPath),
                ), firstArgMustBeView, intArrayOf(1), null
            ),
            MatcherToFix(
                CallMatcher.anyOf(
                    CallMatcher.staticCall(TObjectAnimator, "ofInt").parameterTypes(TObject, TString, TString, TPath),
                    CallMatcher.staticCall(TObjectAnimator, "ofFloat").parameterTypes(TObject, TString, TString, TPath),
                ), firstArgMustBeView, intArrayOf(1, 2), null
            ),
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
            ), dontMindArgs /* too complicated, just don't mind :) */, intArrayOf(0), null
            ),
            MatcherToFix(
                CallMatcher.anyOf(
                    CallMatcher.staticCall("android.util.Property", "of").parameterTypes("java.lang.Class<T>", "java.lang.Class<V>", TString),
                ), firstArgMustBeViewClass, intArrayOf(2), ""
            ),
        )

        private fun UElement.getTypeByName(name: String): PsiClassType? =
            sourcePsi?.let(PsiUtilCore::getProjectInReadAction)
                ?.let { PsiType.getTypeByName(name, it, GlobalSearchScope.allScope(it)) }

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
    }

}
