package net.aquadc.mike.plugin.interfaces

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.hints.ElementProcessingHintPass
import com.intellij.codeInsight.hints.ModificationStampHolder
import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class UpcastHints(
    project: Project, registrar: TextEditorHighlightingPassRegistrar
) : AbstractProjectComponent(project), TextEditorHighlightingPassFactory {

    init {
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
        if (element is PsiMethodCallExpression) {
            val args = element.argumentList.expressions
            if (args.isEmpty()) return

            val params = element.methodExpression.reference?.resolve()?.functionParams ?: return
            args.forEachIndexed { idx, arg ->
                params[idx].second?.let { type ->
                    collector.invoke(arg.endOffset, " as $type")
                }
            }
        } else if (element is KtCallExpression) {
            val args = element.valueArguments
            if (args.isEmpty()) return
            val params = element.calleeExpression?.mainReference?.resolve()?.functionParams ?: return

            args.forEachIndexed { idx, arg ->
                (if (arg.name != null) params.firstOrNull { it.first == arg.name } else params.getOrNull(idx))
                    ?.second?.let { type ->
                        collector.invoke(arg.endOffset, " as $type")
                    }
            }
        }
    }

    private val PsiElement.functionParams
        get() =
            if (this is PsiMethod) parameters.map { null to (it.type as? JvmReferenceType)?.name }
            else if (this is KtNamedFunction) valueParameters.map { it.name to it.typeReference?.typeElement.simpleName }
            else null

    private val KtTypeElement?.simpleName
        get() =
            if (this is KtUserType) referencedName
            else if (this is KtNullableType) (innerType as? KtUserType)?.referencedName
            else null

    override fun getHintKey(): Key<Boolean> = UPCAST_HINTS_INLAY_KEY
    override fun createRenderer(text: String): HintRenderer = MethodChainHintRenderer(text)

    private class MethodChainHintRenderer(text: String) : HintRenderer(text) {
        override fun getContextMenuGroupId() = "UpcastHintsContextMenu"
    }

    companion object {
        private val UPCAST_HINTS_INLAY_KEY = Key.create<Boolean>("UPCAST_HINTS_INLAY_KEY")
    }
}
