package net.aquadc.mike.plugin.regexp

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import net.aquadc.mike.plugin.NamedLocalQuickFix
import org.intellij.lang.regexp.inspection.RegExpReplacementUtil
import org.intellij.lang.regexp.psi.RegExpCharRange
import org.intellij.lang.regexp.psi.RegExpElementVisitor

class BadCyrillicRegexp : LocalInspectionTool() {

    private val MSG_LOWER = "Range [а-я] does not correspond to any of Cyrillic alphabets because of missing letters"
    private val MSG_UPPER = "Range [А-Я] does not correspond to any of Cyrillic alphabets because of missing letters"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RegExpElementVisitor() {
            override fun visitRegExpCharRange(range: RegExpCharRange) {
                val (isLower, isUpper) = range.isAYaRange()
                if (isLower || isUpper) {
                    holder.registerProblem(
                        range,
                        if (isLower) MSG_LOWER else MSG_UPPER,
                        Fix(null), Fix(isUpper)
                    )
                }
            }
        }

    class Fix(private val upper: Boolean?) : NamedLocalQuickFix(when (upper) {
        null -> "Match any Cyrillic letter"
        true -> "Match UPPERCASE Cyrillic letter"
        false -> "Match lowercase Cyrillic letter"
        // titleCase is omitted intentionally
    }) {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val badRange = descriptor.psiElement as? RegExpCharRange ?: return

            var start = badRange.startOffsetInParent
            var end = start + badRange.textLength

            // fix а-яА-Я with one shot:
            (badRange.prevSibling as? RegExpCharRange)?.let { if (it.isAYaRange().any()) start = it.startOffsetInParent }
            (badRange.nextSibling as? RegExpCharRange)?.let { if (it.isAYaRange().any()) end += it.textLength }

            // replace our range:
            val badRegexpPsi = badRange.parent
            val badRegexp = badRegexpPsi.text
            val replacement = StringBuilder()
                .append(badRegexp, 0, start)
                .append("\\p{IsCyrillic}")
                .also { when (upper) {
                    true -> it.append("&&").append("\\p{IsUppercase}")
                    false -> it.append("&&").append("\\p{IsLowercase}")
                    // null -> do nothing
                } }
                .append(badRegexp, end, badRegexp.length)
                .toString()
                // fix double escaping in the rest of regexp:
                .replace("\\\\", "\\") // I'd like to do this on Builder but too lazy to re-implement it

            RegExpReplacementUtil.replaceInContext(badRegexpPsi, replacement)
        }
    }
}

private fun RegExpCharRange.isAYaRange(): Pair<Boolean, Boolean> {
    val from = from.value
    val to = (to ?: return false to false).value
    return (from == 0x0430 && to == 0x044F) to (from == 0x0410 && to == 0x042F)
}

private fun Pair<Boolean, Boolean>.any(): Boolean =
    first || second
