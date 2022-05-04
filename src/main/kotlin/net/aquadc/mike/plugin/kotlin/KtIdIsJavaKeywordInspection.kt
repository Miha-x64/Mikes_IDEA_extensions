package net.aquadc.mike.plugin.kotlin

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import com.siyeh.ig.fixes.RenameFix
import net.aquadc.mike.plugin.SortedArray
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

/**
 * @author Mike Gorünóv
 */
class KtIdIsJavaKeywordInspection : LocalInspectionTool() {

    private val javaKeywords = SortedArray.from((
            "abstract continue for new switch default package synchronized boolean do if private this break double " +
                    "implements protected throw byte else import public throws case instanceof return transient " +
                    "catch extends int short try char final interface static void class finally long volatile float " +
                    "native super while " + // 1.0
                    "goto const " + // reserved
                    "strictfp " + // 1.2
                    "assert " + // 1.4
                    "enum " + // 1.5
                    "_" // 9
            ).split(' ')
    )

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitPackageDirective(directive: KtPackageDirective) {
            directive.packageNames.forEach {
                check(it.getReferencedName(), it)
            }
        }
        override fun visitDeclaration(dcl: KtDeclaration) {
            if (dcl is KtParameter) return // named parameters are unusable from Java
            if (dcl.isFunctionWithReified) return // `reified` makes function invisible for Java

            var container: KtDeclaration = dcl
            do {
                if (KtPsiUtil.isLocal(container)) return
                when (container.visibilityModifierType()) {
                    KtTokens.PRIVATE_KEYWORD, KtTokens.INTERNAL_KEYWORD -> return
                }
            } while (container.containingClassOrObject?.also { container = it } != null)

            if (dcl is KtProperty && !dcl.hasModifier(KtTokens.CONST_KEYWORD) && dcl.jvmField == null) {
                // keep eye on get/set if field if private
                dcl.getter?.let(::analyze)
                dcl.setter?.let(::analyze)
            } else {
                // const property || JvmField property || class || function
                analyze(dcl)
            }
        }

        private val KtProperty.jvmField get(): KtAnnotationEntry? =
            annotationEntries.firstOrNull {
                it.calleeExpression?.constructorReferenceExpression?.getReferencedName() == "JvmField"
            }

        private val KtDeclaration.isFunctionWithReified get(): Boolean =
            this is KtFunction && typeParameters.any { it.hasModifier(KtTokens.REIFIED_KEYWORD) }

        private fun analyze(dcl: KtDeclaration) {
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

            check(name, jvmNameExpr ?: (dcl as? PsiNameIdentifierOwner)?.nameIdentifier ?: dcl)
        }

        private fun check(name: String, highlight: PsiElement) {
            KtPsiUtil.unquoteIdentifier(name).takeIf { it in javaKeywords }?.let { id ->
                holder.registerProblem(highlight, "Identifier \"$id\" is a Java keyword", RenameFix())
            }
        }
    }

}
