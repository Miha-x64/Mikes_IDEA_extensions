@file:Suppress("UnstableApiUsage")

package net.aquadc.mike.plugin.interfaces

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.hints.ElementProcessingHintPass
import com.intellij.codeInsight.hints.ModificationStampHolder
import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import net.aquadc.mike.plugin.maxByIf
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.j2k.getContainingClass
import org.jetbrains.kotlin.j2k.getContainingMethod
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

class UpcastHints : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {

    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
    }

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? =
        if (!editor.isOneLineMode && file.isJKFile && isChanged(editor, file)) UpcastHintsPass(modificationStampHolder, file, editor) else null

    private fun isChanged(editor: Editor, file: PsiFile) =
        !modificationStampHolder.isNotChanged(editor, file)

    private val PsiFile.isJKFile: Boolean
            get() = this is PsiJavaFile || this is KtFile

    private companion object {
        private val modificationStampHolder: ModificationStampHolder =
            ModificationStampHolder(Key.create("UPCAST_HINTS_PASS_LAST_MODIFICATION_TIMESTAMP"))
    }

}

class UpcastHintsPass(
    modificationStampHolder: ModificationStampHolder,
    rootElement: PsiElement,
    editor: Editor
) : ElementProcessingHintPass(rootElement, editor, modificationStampHolder) {

    override fun isAvailable(virtualFile: VirtualFile): Boolean = true

    override fun collectElementHints(element: PsiElement, collector: (offset: Int, hint: String) -> Unit) {
        when (element) {
            is PsiMethodCallExpression -> {
                val args = element.argumentList.expressions
                if (args.isEmpty()) return

                val params = element.methodExpression.reference?.resolve()?.functionParams ?: return
                args.forEachIndexed(fun(idx: Int, arg: PsiExpression) {
                    val parameterType = params.getOrNull(idx)?.second ?: return
                    if (element.methodExpression.referenceName?.contains(parameterType.typeName ?: return) ?: return) return
                    visitParameter(parameterType, arg, arg.endOffset, collector)
                })
            }
            /*is KtCallExpression -> {
                val args = element.valueArguments
                if (args.isEmpty()) return
                val params = element.calleeExpression?.mainReference?.resolve()?.functionParams ?: return

                args.forEachIndexed(fun(idx: Int, arg: KtValueArgument) {
                    val type =
                        (if (arg.name != null) params.firstOrNull { it.first == arg.name } else params.getOrNull(idx))?.second ?: return
                    visitParameter(
                        type,
                        arg.getArgumentExpression()?.toUElementOfType<UExpression>()?.javaPsi as? PsiExpression ?: return,
                        //                          this does not work and will always be null ^^^^^^^
                        arg.endOffset,
                        collector
                    )
                })
            }*/
            is PsiAnnotation -> {
                if (element.nameReferenceElement?.qualifiedName != "java.lang.Override") return
                val meth = element.getContainingMethod() ?: return
                val declaringType = meth.findSuperMethods().firstOrNull()?.containingClass ?: return
                visitOverride(element, declaringType, collector, element, true)
            }
            is KtModifierList -> {
                val override = element.getModifier(KtTokens.OVERRIDE_KEYWORD) ?: return
                val func = element.parent as? KtNamedFunction ?: return
                if (func.receiverTypeReference != null) return // ignore extensions
                val asJavaMethod = func.toUElementOfType<UMethod>()?.javaPsi
                val declaringType = asJavaMethod?.findSuperMethods()?.firstOrNull()?.containingClass ?: return
                visitOverride(asJavaMethod, declaringType, collector, override, false)
            }
        }
    }

    private fun visitOverride(
        javaElement: PsiElement,
        declaringType: PsiClass,
        collector: (offset: Int, hint: String) -> Unit,
        hintAfter: PsiElement,
        java: Boolean
    ) {
        if (!declaringType.isInterface) return // such a hint is useless in single class inheritance model
        (javaElement.getContainingClass() ?: return).mainInterface?.let { main ->
            if (main == declaringType || main.isInheritor(declaringType, true)) return
        }
        val className = declaringType.name ?: return
        collector(hintAfter.endOffset, if (java) "from $className" else className)
    }

    private val PsiElement.functionParams
        get() =
            if (this is PsiMethod) parameters.map {
                null to (it.type as? JvmReferenceType)?.resolve() as? PsiClass
            } else if (this is KtNamedFunction) valueParameters.map {
                val type = it.typeReference?.typeElement
                it.name to ((type as? KtUserType)?.referenceExpression?.mainReference?.resolve() ?: type as? KtFunctionType ?: type?.mainReference?.resolve())
            } // also may be typealias, ignore this
            else null

    private fun visitParameter(parameterType: PsiElement, argument: PsiExpression, offset: Int, collector: (offset: Int, hint: String) -> Unit) {
        if (parameterType is PsiClass) {
            if (!parameterType.isInterface) return
            val argumentType = when (val it = argument.type) {
                PsiType.NULL -> null
                is PsiClassType -> it.resolve() ?: return
                is PsiPrimitiveType -> it.getBoxedType(argument)?.resolve() ?: return
                else -> return
            }
            argumentType?.mainInterface?.let { main ->
                if (main == parameterType || main.isInheritor(parameterType, true)) return
            }
        }
        collector(offset, "as ${parameterType.typeName}")
    }

    private val PsiClass.mainInterface: PsiClass?
        get() =
            (if (qualifiedName == null) interfaces.singleOrNull() else null) // anonymous types typically implement single interface
                ?: mainInterfaceByName ?: mainInterfaceByImplCount ?: interfaces.takeIf { superClass == null }?.singleOrNull()

    private val PsiClass.mainInterfaceByName: PsiClass?
        get() {
            if (isInterface) return this
            val name = name ?: return null
            return interfaces.firstOrNull {
                val iName = it.name
                iName != null && name.contains(iName) // i. e. ArrayList's main interface is List
            } ?: superClass?.mainInterfaceByName

            // TODO: WhateverHandler's main is IdleHandler
        }

    // LOL, very simple, ignores how many of them were actually implemented
    private val PsiClass.mainInterfaceByImplCount: PsiClass?
        get() = interfaces // ignore single- or two-method interfaces
            .maxByIf({ it.methods.count { it.modifierList.hasModifierProperty(PsiModifier.ABSTRACT) } }, { it > 2 })

    private val PsiElement.typeName: String? get() = when (this) {
        is PsiClass -> name
        is KtFunctionType -> toString()
        else -> null
    }

    override fun getHintKey(): Key<Boolean> = UPCAST_HINTS_INLAY_KEY
    override fun createRenderer(text: String): HintRenderer = MethodChainHintRenderer(text)

    private class MethodChainHintRenderer(text: String) : HintRenderer(text) {
        override fun getContextMenuGroupId(inlay: Inlay<*>) = "UpcastHintsContextMenu"
    }

    companion object {
        private val UPCAST_HINTS_INLAY_KEY = Key.create<Boolean>("UPCAST_HINTS_INLAY_KEY")
    }
}
