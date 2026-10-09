package net.aquadc.mike.plugin.gradle

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import org.toml.lang.psi.ext.name

/** A paste-local catalog snapshot. Existing entries are never overwritten. */
internal class VersionCatalog private constructor(private val file: TomlFile, private val kotlinDsl: Boolean) {
    private val tables = file.children.filterIsInstance<TomlTable>().associateBy { it.header.key?.name }
    private val versions = tables["versions"]?.entries.orEmpty().associate { it.key.name!! to it.value.string() }.toMutableMap()
    private val libraries = tables["libraries"]?.entries.orEmpty().associate {
        it.key.name!! to library(it.value)
    }.toMutableMap()
    private val newVersions = ArrayList<String>()
    private val newLibraries = ArrayList<String>()

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
        private val kotlinKeywords = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
            "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while"
        )
    }
}
