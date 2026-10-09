package net.aquadc.mike.plugin.gradle

internal data class MavenDependency(val group: String, val artifact: String, val version: String) {
    val module: String get() = "$group:$artifact"

    fun alias(): String {
        val groupTail = normalize(group.substringAfterLast('.'))
        val name = normalize(artifact)
        val alias = when {
            groupTail in name -> name
            name in groupTail -> groupTail
            else -> "$groupTail-$name"
        }
        val safe = alias.split('-').joinToString("-") { if (it in reservedSegments) "${it}Lib" else it }
        return if (safe.firstOrNull()?.isLetter() == true) safe else "lib-${safe.ifEmpty { "dependency" }}"
    }

    companion object {
        private val coordinates = Regex("([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([A-Za-z0-9_.+,-]+)")
        private val separators = Regex("[^a-z0-9]+")
        private fun normalize(value: String): String = value.lowercase().replace(separators, "-").trim('-')
        private val reservedSegments = setOf("class", "extensions", "convention", "bundles", "versions", "plugins")
        fun parse(value: String): MavenDependency? = coordinates.matchEntire(value)?.destructured?.let {
            (group, artifact, version) -> MavenDependency(group, artifact, version)
        }
    }
}

internal data class DependencyPaste(val start: Int, val end: Int, val dependency: MavenDependency)

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
        val blocks = ArrayList<String>()
        val result = ArrayList<DependencyPaste>()
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
