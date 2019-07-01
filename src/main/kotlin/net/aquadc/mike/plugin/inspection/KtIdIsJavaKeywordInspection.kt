package net.aquadc.mike.plugin.inspection

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import net.aquadc.mike.plugin.SortedArray
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.quickfix.RenameIdentifierFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtVisitorVoid


class KtIdIsJavaKeywordInspection : AbstractKotlinInspection() {

    private val javaKeywords = SortedArray.from((
            "abstract continue for new switch default package synchronized boolean do if private this break double " +
                    "implements protected throw byte else import public throws case instanceof return transient catch " +
                    "extends int short try char final interface static void class finally long volatile float native " +
                    "super while " + // 1.0
                    "goto const " + // reserved
                    "strictfp " + // 1.2
                    "assert " + // 1.4
                    "enum " + // 5
                    "_" // 9
            ).split(' ')
    )

    // partially copied from https://github.com/JetBrains/kotlin/blob/ba6da7c40a6cc502508faf6e04fa105b96bc7777/idea/idea-android/src/org/jetbrains/kotlin/android/inspection/IllegalIdentifierInspection.kt

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                if (element.node?.elementType != KtTokens.IDENTIFIER) return

                val identifier = KtPsiUtil.unquoteIdentifier(element.text)
                if (identifier in javaKeywords) {
                    holder.registerProblem(
                        element, "Identifier \"$identifier\" is a Java keyword", RenameIdentifierFix()
                    )
                }
            }
        }

}
