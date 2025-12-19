package net.aquadc.mike.plugin.regexp

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import net.aquadc.mike.plugin.NamedLocalQuickFix
import net.aquadc.mike.plugin.regexp.BadCyrillicRegexp.Fix.detectAYaRange
import org.intellij.lang.regexp.inspection.RegExpReplacementUtil
import org.intellij.lang.regexp.psi.RegExpCharRange
import org.intellij.lang.regexp.psi.RegExpElementVisitor

/**
 * @author Mike Gorünóv
 */
class BadCyrillicRegexp : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RegExpElementVisitor() {
            override fun visitRegExpCharRange(range: RegExpCharRange) {
                if (range.detectAYaRange() != 0) {
                    holder.registerProblem(
                        range,
                        "Ranges А-Я, а-я do not fully cover any of Cyrillic alphabets, some letters are out of range",
                        Fix,
                    )
                }
            }
        }

    private object Fix : NamedLocalQuickFix("Match letters from all Cyrillic alphabets") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val subj = descriptor.psiElement as RegExpCharRange
            var detection = subj.detectAYaRange()
            var start = subj.textRangeInParent.startOffset
            var end = subj.textRangeInParent.endOffset

            // detect sibling ranges to hit them in one shot
            var tmp = 0
            var sib = subj.prevSibling
            if (sib is RegExpCharRange && sib.detectAYaRange().also { tmp = it } != 0) {
                start = sib.textRangeInParent.startOffset
                if (tmp != detection) detection = 0
            }
            sib = subj.nextSibling
            if (sib is RegExpCharRange && sib.detectAYaRange().also { tmp = it } != 0) {
                end = sib.textRangeInParent.endOffset
                if (tmp != detection) detection = 0
            }

            // eat useless []
            val original = subj.parent.text
            if (start > 0 && original[start-1] == '[' && original.length > end && original[end] == ']') {
                start--
                end++
            }

            // build replacement
            val replacement = StringBuilder()
                .append(original, 0, start)
                .appendCategory("IsCyrillic")
                .also {
                    when (detection) {
                        -1 -> it.append("&&").appendCategory("IsLowercase")
                        +1 -> it.append("&&").appendCategory("IsUppercase")
                    }
                }
                .append(original, end, original.length)
                .toString()
                // fix double escaping in the rest of regexp:
                .replace("\\\\", "\\") // I'd like to do this on Builder but too lazy to re-implement it

            // commit
            RegExpReplacementUtil.replaceInContext(subj.parent, replacement)
        }

        /**
         * @return +1 for upper (А-Я), -1 for lower (а-я), 0 if not detected
         */
        fun RegExpCharRange.detectAYaRange(): Int {
            val from = from.value
            val to = (to ?: return 0).value
            return when {
                from == 0x0430 && to == 0x044F -> -1
                from == 0x0410 && to == 0x042F -> +1
                else -> 0
            }
        }

        private fun StringBuilder.appendCategory(name: String): StringBuilder =
            append('\\').append('p').append('{').append(name).append('}')
    }
}
