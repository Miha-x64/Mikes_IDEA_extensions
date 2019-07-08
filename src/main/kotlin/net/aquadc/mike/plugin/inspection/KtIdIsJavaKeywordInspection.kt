package net.aquadc.mike.plugin.inspection

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import net.aquadc.mike.plugin.SortedArray
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.quickfix.RenameIdentifierFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPublic


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

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitDeclaration(dcl: KtDeclaration) {
                if (dcl is KtParameter) return // named parameters are unusable from Java
                if (!dcl.isPublic) return // also ignore local and private variables and functions

                val jvmNameExpr = dcl.annotationEntries
                    .firstOrNull { it.calleeExpression?.constructorReferenceExpression?.getReferencedName() == "JvmName" }
                    ?.valueArguments?.singleOrNull()?.getArgumentExpression()

                val name =
                    if (jvmNameExpr == null) dcl.name ?: return // invalid (or anonymous?)
                    else (jvmNameExpr as? KtStringTemplateExpression)?.entries?.let { entries ->
                        buildString {
                            for (e in entries) {
                                if (e is KtLiteralStringTemplateEntry) append(e.node.text)
                                else return
                            }
                        }
                    } ?: return // string template (invalid) or complex compile-time expression (rare; give up)

                val identifier = KtPsiUtil.unquoteIdentifier(name)
                if (identifier in javaKeywords) {
                    val highlight = jvmNameExpr ?: (dcl as? PsiNameIdentifierOwner)?.nameIdentifier ?: dcl

                    holder.registerProblem(
                        highlight, "Identifier \"$identifier\" is a Java keyword", RenameIdentifierFix()
                    )
                }
            }
        }

}
