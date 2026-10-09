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
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents
import com.intellij.util.SmartList
import net.aquadc.mike.plugin.SortedArray
import org.toml.lang.psi.TomlArrayTable
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlFileType
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlValue
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import org.toml.lang.psi.ext.name
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import kotlin.collections.associate
import kotlin.collections.contains
import kotlin.collections.get
import kotlin.collections.orEmpty

/**
 * @author maximk0
 * @author codex
 * @author Mike Gorünóv (review, typos, bulkTo, nonSelfDescriptiveAliases, SortedArray, SmartList, testing)
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
        WriteCommandAction.runWriteCommandAction(project, "Paste Dependency into Version Catalog", null, Runnable {
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
            if (directory.name == "buildSrc") return null // 'libs' is not imported here automatically
            directory = directory.parent
        }
        return null
    }

    class PasteData : TextBlockTransferableData {
        override fun getFlavor(): DataFlavor = dataFlavor
        private companion object {
            private val dataFlavor = DataFlavor(PasteData::class.java, "Gradle dependency paste")
        }
    }
}

internal class MavenDependency(val group: String, val artifact: String, val version: String) {
    val module: String get() = "$group:$artifact"

    fun alias(): String {
        val strings = group.split('.') + artifact.split('.')
        val parts = strings.mapTo(ArrayList(strings.size)) { it.lowercase().replace(separators, "-").trim('-') }
        var index = 0
        while (index < parts.size) {
            val string = parts[index]
            if (string in parts.getOrNull(index - 1).orEmpty() || string in parts.getOrNull(index + 1).orEmpty())
                parts.removeAt(index)
            else index++
        }
        repeat(parts.indexOfLast { it !in nonSelfDescriptiveAliases }.coerceAtLeast(0)) {
            parts.removeFirst()
        }
        val safe = parts.joinToString("-") { if (it in reservedSegments) "${it}Lib" else it }
        return if (safe.firstOrNull()?.isLetter() == true) safe else "lib-${safe.ifEmpty { "dependency" }}"
    }

    override fun equals(other: Any?): Boolean = // tests
        other is MavenDependency && group == other.group && artifact == other.artifact && version == other.version

    companion object {
        private val coordinates = Regex("([\\w.-]+):([\\w.-]+):([\\w.+,-]+)")
        private val separators = Regex("[^a-z0-9]+")
        private val reservedSegments = SortedArray.of("class", "extensions", "convention", "bundles", "versions", "plugins")
        private val nonSelfDescriptiveAliases = SortedArray.of("runtime", "annotation", "compiler")
        fun parse(value: String): MavenDependency? =
            coordinates.matchEntire(value)?.destructured?.let {
                (group, artifact, version) -> MavenDependency(group, artifact, version)
            }
    }
}

internal class DependencyPaste(val start: Int, val end: Int, val dependency: MavenDependency)

/** Finds literal dependency arguments in dependencies blocks, preserving calls and wrappers. */
internal object GradleDependencyPaste {
    private val call = Regex(
        "(?<![\\w.])(?:api|implementation|compileOnly|compileOnlyApi|runtimeOnly|annotationProcessor|" +
                "kapt|ksp|testImplementation|testRuntimeOnly|testCompileOnly|" +
                "[A-Za-z_]\\w*(?:Api|Implementation|CompileOnly|RuntimeOnly|AnnotationProcessor|Kapt|Ksp))" +
                "\\s*(?:\\(\\s*|[ \\t]+)(?:(?:platform|enforcedPlatform|testFixtures)\\s*\\(\\s*)?$"
    )
    private val block = Regex("([A-Za-z_]\\w*)\\s*(?:\\(\\s*\\))?\\s*$")

    fun find(text: String, pasteStart: Int, pasteEnd: Int): List<DependencyPaste> {
        val code = StringBuilder(text)
        val blocks = SmartList<String>()
        val result = SmartList<DependencyPaste>()
        var i = 0
        while (i < text.length) {
            val start = i
            when {
                text.startsWith("//", i) -> {
                    i = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    mask(code, start, i)
                }
                text.startsWith("/*", i) -> {
                    i += 2
                    var depth = 1
                    while (i < text.length && depth > 0) {
                        when {
                            text.startsWith("/*", i) -> { depth++; i += 2 }
                            text.startsWith("*/", i) -> { depth--; i += 2 }
                            else -> i++
                        }
                    }
                    mask(code, start, i)
                }
                text[i] == '\'' || text[i] == '"' -> {
                    val quote = text[i]
                    val triple = text.startsWith("$quote$quote$quote", i)
                    val delimiter = if (triple) "$quote$quote$quote" else "$quote"
                    i += delimiter.length
                    val contentStart = i
                    while (i < text.length && !text.startsWith(delimiter, i)) {
                        i += if (text[i] == '\\' && !triple && i + 1 < text.length) 2 else 1
                    }
                    val closed = i < text.length
                    val contentEnd = i
                    if (closed) i += delimiter.length
                    if (closed && !triple && start >= pasteStart && i <= pasteEnd &&
                        "dependencies" in blocks && "buildscript" !in blocks && "constraints" !in blocks &&
                        call.containsMatchIn(code.substring(0, start)) && argumentEnds(text, i)
                    ) {
                        MavenDependency.parse(text.substring(contentStart, contentEnd))?.let {
                            result += DependencyPaste(start, i, it)
                        }
                    }
                    mask(code, start, i)
                }
                text[i] == '{' -> {
                    blocks += block.find(code.substring(0, i))?.groupValues?.get(1).orEmpty()
                    i++
                }
                text[i] == '}' -> { if (blocks.isNotEmpty()) blocks.removeAt(blocks.lastIndex); i++ }
                else -> i++
            }
        }
        return result
    }

    private fun mask(code: StringBuilder, start: Int, end: Int) {
        for (i in start until end) if (code[i] != '\n' && code[i] != '\r') code.setCharAt(i, ' ')
    }

    private fun argumentEnds(text: String, end: Int): Boolean {
        var i = end
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return i == text.length || text[i] in ")\n\r;{}" || text.startsWith("//", i)
    }
}

/** A paste-local catalog snapshot. Existing entries are never overwritten. */
internal class VersionCatalog private constructor(private val file: TomlFile, private val kotlinDsl: Boolean) {
    private val tables = file.children.filterIsInstance<TomlTable>().associateByTo(HashMap()) { it.header.key?.name }
    private val versions = tables["versions"]?.entries.orEmpty().associateTo(HashMap()) { it.key.name!! to it.value.string() }
    private val libraries = tables["libraries"]?.entries.orEmpty().associateTo(HashMap()) { it.key.name!! to library(it.value) }
    private val newVersions = SmartList<String>()
    private val newLibraries = SmartList<String>()

    fun reference(dependency: MavenDependency): String {
        libraries.entries.firstOrNull { it.value == dependency }?.let { return accessor(it.key) }
        val base = dependency.alias()
        val alias = available(base, libraries.keys)
        var versionAlias = base
        var suffix = 2
        var existingVersion = versions.keys.firstOrNull { normalized(it) == normalized(versionAlias) }
        while (existingVersion != null && versions[existingVersion] != dependency.version) {
            versionAlias = "$base${suffix++}"
            existingVersion = versions.keys.firstOrNull { normalized(it) == normalized(versionAlias) }
        }
        if (existingVersion != null) versionAlias = existingVersion
        else {
            versions[versionAlias] = dependency.version
            newVersions += "$versionAlias = \"${dependency.version}\""
        }
        libraries[alias] = dependency
        newLibraries += "$alias = { module = \"${dependency.module}\", version.ref = \"$versionAlias\" }"
        return accessor(alias)
    }

    fun updatedText(): String {
        val text = StringBuilder(file.text)
        val additions = listOf("versions" to newVersions, "libraries" to newLibraries)
        val insertions = additions.mapNotNull { (name, entries) ->
            if (entries.isEmpty()) return@mapNotNull null
            val table = tables[name] ?: return@mapNotNull null
            // Insert after the last entry's whole line, preserving trailing comments and other sections.
            val end = table.entries.lastOrNull()?.textRange?.endOffset ?: table.header.textRange.endOffset
            val newline = file.text.indexOf('\n', end)
            val offset = if (newline < 0) file.textLength else newline + 1
            offset to (if (newline < 0) "\n" else "") + entries.joinToString("\n", postfix = "\n")
        }
        for ((offset, addition) in insertions.sortedByDescending { it.first }) text.insert(offset, addition)
        for ((name, entries) in additions) {
            if (entries.isEmpty() || name in tables) continue
            if (text.isNotEmpty()) {
                if (text.last() != '\n') text.append('\n')
                text.append('\n')
            }
            text.append("[$name]\n").append(entries.joinToString("\n", postfix = "\n"))
        }
        return text.toString()
    }

    private fun library(value: TomlValue?): MavenDependency? {
        value.string()?.let { return MavenDependency.parse(it) }
        val fields = (value as? TomlInlineTable)?.entries?.associate {
            it.key.segments.joinToString(".") { segment -> segment.name.orEmpty() } to it.value
        } ?: return null
        val module = fields["module"].string() ?: run {
            val group = fields["group"].string() ?: return null
            val name = fields["name"].string() ?: return null
            "$group:$name"
        }
        val version = fields["version"].string() ?: run {
            val ref = fields["version.ref"].string() ?: (fields["version"] as? TomlInlineTable)
                ?.entries?.firstOrNull { it.key.name == "ref" }?.value.string()
            versions[ref] ?: return null
        }
        return MavenDependency.parse("$module:$version")
    }

    private fun accessor(alias: String): String {
        val path = normalized(alias)
        val escaped = path.split('.').joinToString(".") {
            if (it in kotlinKeywords || !identifier.matches(it)) {
                if (kotlinDsl) "`$it`" else "'$it'"
            } else it
        }
        val provider = libraries.keys.any { normalized(it).startsWith("$path.") }
        return "libs.$escaped" + if (provider) ".asProvider()" else ""
    }

    companion object {
        fun parse(project: Project, text: String, kotlinDsl: Boolean = true): VersionCatalog? {
            val file = PsiFileFactory.getInstance(project).createFileFromText("libs.versions.toml", TomlFileType, text) as? TomlFile
                ?: return null
            if (PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null) return null
            if (PsiTreeUtil.findChildrenOfType(file, TomlLiteral::class.java).any { literal ->
                    val kind = literal.kind as? TomlLiteralKind.String
                    kind != null && kind.offsets.closeDelim == null
                }) return null
            val tables = file.children.filterIsInstance<TomlTable>()
            // Subtables and duplicate keys require more involved merging; leave such catalogs untouched.
            if (file.children.any { it is TomlArrayTable } ||
                tables.any { it.header.key?.segments?.size != 1 || it.header.key?.name == null } ||
                tables.map { it.header.key?.name }.distinct().size != tables.size ||
                tables.any { table ->
                    table.entries.any { it.key.segments.size != 1 || it.key.name == null } ||
                            table.entries.map { normalized(it.key.name!!) }.distinct().size != table.entries.size
                }
            ) return null
            return VersionCatalog(file, kotlinDsl)
        }

        private fun TomlValue?.string(): String? = ((this as? TomlLiteral)?.kind as? TomlLiteralKind.String)?.value
        private fun normalized(alias: String): String = alias.replace('-', '.').replace('_', '.')
        private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private fun available(base: String, aliases: Set<String>): String {
            val paths = aliases.map(::normalized)
            var candidate = base
            var suffix = 2
            while (paths.any {
                    val path = normalized(candidate)
                    it == path || it.startsWith("$path.") || path.startsWith("$it.")
                }) {
                val head = base.substringBefore('-')
                candidate = "$head${suffix++}" + base.removePrefix(head)
            }
            return candidate
        }
        private val kotlinKeywords = SortedArray.of(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
            "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while"
        )
    }
}
