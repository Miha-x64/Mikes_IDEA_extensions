package net.aquadc.mike.plugin.android

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames.*
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.parentOfType
import com.siyeh.ig.callMatcher.CallMatcher
import net.aquadc.mike.plugin.register
import net.aquadc.mike.plugin.test
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator.Companion.getConstant
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.TypeUtils
import java.util.regex.Pattern

/**
 * @author Mike Gorünóv
 */
class ReplaceAndroidMethodWithKotlinAnalog : LocalInspectionTool(), CleanupLocalInspectionTool {

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): PsiElementVisitor = callExpressionVisitor { call: KtCallExpression ->
        val which = matchers.indexOfFirst { it.test(call) }
        when (which) {
            0, 1 -> call.valueArgumentList!!.arguments.let { (delimiterArg, tokensArg) ->
                val delimiter = delimiterArg.getArgumentExpression()
                val tokens = tokensArg.getArgumentExpression()
                if (delimiter == null || tokens == null) null
                else "(${tokens.text}).joinToString(" +
                        (delimiter.takeIf {
                            getConstant(it, it.analyze(BodyResolveMode.PARTIAL))
                                ?.getValue(TypeUtils.NO_EXPECTED_TYPE) != ", "
                        }?.text ?: "") +
                        ")"
            }
            2, 3 -> call.valueArgumentList!!.arguments.let { (textArg, patternArg) ->
                val text = textArg.getArgumentExpression()
                val pattern = patternArg.getArgumentExpression()
                if (text == null || pattern == null) null
                else "(${text.text}).split(${if (which == 2) "Regex(${pattern.text})" else pattern.text})"
            }
            4 -> call.valueArgumentList!!.arguments.let { (strArg) ->
                val str = strArg.getArgumentExpression()
                if (str == null) null
                else "(${str.text}).isNullOrEmpty()"
            }
            5 -> call.valueArgumentList!!.arguments.let { (aArg, bArg) ->
                val a = aArg.getArgumentExpression()
                val b = bArg.getArgumentExpression()
                if (a == null || b == null) null
                else "(${a.text}).contentEquals(${b.text})"
            }
            else -> null
        }?.let { replacement ->
            holder.register(
                call.calleeExpression ?: call,
                KotlinBundle.message("should.be.replaced.with.kotlin.function"),
                object : LocalQuickFix {
                    override fun getName() =
                     // KotlinBundle.message("replace.with.kotlin.analog.function.text", shortNames[which])
                     // was showing "Replace with '[Ljava.lang.Object@blahblahblah' function"
                        "Replace with '${shortNames[which]}' function"

                    override fun getFamilyName() =
                        KotlinBundle.message("replace.with.kotlin.analog.function.family.name")

                    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                        val psi = descriptor.psiElement.parentOfType<KtCallExpression>(withSelf = true)!!
                            .let { it.parent as? KtDotQualifiedExpression ?: it }
                        val expr = CodeStyleManager.getInstance(project).reformat(ShortenReferences.DEFAULT.process(
                            psi.replace(KtPsiFactory(psi).createExpression(replacement)) as KtExpression
                        ))
                        (expr.firstChild as? KtParenthesizedExpression)?.let {
                            if (KtPsiUtil.areParenthesesUseless(it))
                                it.replace(it.expression!!)
                        }
                    }
                }
            )
        }
    }

    private companion object {
        private const val TextUtils = "android.text.TextUtils"
        private val join = CallMatcher.staticCall(TextUtils, "join") // -> String
        private val joinArray = join.parameterTypes(JAVA_LANG_CHAR_SEQUENCE, "$JAVA_LANG_OBJECT[]")
        private val joinIterable = join.parameterTypes(JAVA_LANG_CHAR_SEQUENCE, JAVA_LANG_ITERABLE)

        private val split = CallMatcher.staticCall(TextUtils, "split") // -> String[]
        private val splitWithStr = split.parameterTypes(JAVA_LANG_STRING, /*regexp*/JAVA_LANG_STRING)
        private val splitWithPat = split.parameterTypes(JAVA_LANG_STRING, Pattern::class.java.name)

        private val isEmpty = CallMatcher.staticCall(TextUtils, "isEmpty").parameterTypes(JAVA_LANG_CHAR_SEQUENCE/*?*/) // -> boolean
        private val equals = CallMatcher.staticCall(TextUtils, "equals").parameterTypes(JAVA_LANG_CHAR_SEQUENCE/*?*/, JAVA_LANG_CHAR_SEQUENCE/*?*/) // -> boolean

        private val matchers = arrayOf(
            joinArray, joinIterable,
            splitWithStr, splitWithPat,
            isEmpty, equals,
        )
        private val shortNames = arrayOf(
            "joinToString", "joinToString",
            "split", "split",
            "isNullOrEmpty", "contentEquals",
        )
    }
}