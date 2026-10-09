package net.aquadc.mike.plugin.gradle

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.parents
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

/**
 * @author maximk0
 * @author codex
 * */
class CopyPasteGradleDependency : CopyPastePostProcessor<CopyPasteGradleDependency.PasteData>() {
    // Read stringFlavor so copying from a browser or another editor also triggers the postprocessor.
    override fun collectTransferableData(file: PsiFile, editor: Editor, startOffsets: IntArray, endOffsets: IntArray): List<PasteData> = emptyList()

    override fun extractTransferableData(content: Transferable): List<PasteData> =
        if (content.isDataFlavorSupported(DataFlavor.stringFlavor)) listOf(PasteData()) else emptyList()

    override fun processTransferableData(
        project: Project, editor: Editor, bounds: RangeMarker, caretOffset: Int,
        indented: Ref<in Boolean>, values: List<PasteData>
    ) {
        if (!bounds.isValid || project.isDisposed) return
        val manager = FileDocumentManager.getInstance()
        val buildFile = manager.getFile(editor.document) ?: return
        if (buildFile.name != "build.gradle" && buildFile.name != "build.gradle.kts") return
        val psiManager = PsiDocumentManager.getInstance(project)
        psiManager.commitDocument(editor.document)
        val psiFile = psiManager.getPsiFile(editor.document) ?: return
        val pasted = GradleDependencyPaste.find(editor.document.text, bounds.startOffset, bounds.endOffset).filter { paste ->
            // Exclude Groovy slashy strings (and other lexical constructs) that contain dependency-like text.
            psiFile.findElementAt(paste.start)?.parents(withSelf = true)?.any {
                it.textRange.startOffset == paste.start && it.textRange.endOffset == paste.end
            } == true
        }
        if (pasted.isEmpty()) return
        val root = buildRoot(buildFile) ?: return
        val existing = root.findFileByRelativePath("gradle/libs.versions.toml")
        val catalogDocument = existing?.let(manager::getDocument)
        if (existing != null && (catalogDocument == null || !existing.isWritable)) return
        val gradleDirectory = root.findChild("gradle")
        if (existing == null && (!root.isWritable || gradleDirectory?.isWritable == false || gradleDirectory?.isDirectory == false)) return
        val catalog = VersionCatalog.parse(project, catalogDocument?.text.orEmpty(), buildFile.name.endsWith(".kts")) ?: return
        val replacements = pasted.map { it to catalog.reference(it.dependency) }
        val updatedCatalog = catalog.updatedText()

        // The paste command groups both document changes, including catalog creation, into one Undo.
        WriteCommandAction.runWriteCommandAction(project, "Paste dependency into version catalog", null, Runnable {
            val document = catalogDocument ?: run {
                val directory = VfsUtil.createDirectoryIfMissing(root, "gradle") ?: return@Runnable
                val file = directory.findChild("libs.versions.toml") ?: directory.createChildData(this, "libs.versions.toml")
                manager.getDocument(file) ?: return@Runnable
            }
            if (document.text != updatedCatalog) document.setText(updatedCatalog)
            for ((paste, reference) in replacements.asReversed()) editor.document.replaceString(paste.start, paste.end, reference)
            indented.set(false)
        })
    }

    private fun buildRoot(buildFile: VirtualFile): VirtualFile? {
        var directory: VirtualFile? = buildFile.parent
        while (directory != null) {
            // Stop at the nearest settings file so included builds/buildSrc cannot accidentally use the outer catalog.
            if (directory.findChild("settings.gradle") != null || directory.findChild("settings.gradle.kts") != null) return directory
            if (directory.name == "buildSrc") return null // libs is not imported here automatically
            directory = directory.parent
        }
        return null
    }

    class PasteData : TextBlockTransferableData {
        override fun getFlavor(): DataFlavor = DataFlavor(PasteData::class.java, "Gradle dependency paste")
        override fun getOffsetCount(): Int = 0
        override fun getOffsets(offsets: IntArray, index: Int): Int = index
        override fun setOffsets(offsets: IntArray, index: Int): Int = index
    }
}
