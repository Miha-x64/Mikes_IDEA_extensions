package net.aquadc.mike.plugin.gradle

import org.junit.Assert.*
import org.junit.Test

class GradleDependencyPasteTest {
    @Test fun namesUseGroupTailAndArtifactWithoutDuplication() {
        val examples = mapOf(
            "androidx.appcompat:appcompat:1.8.0" to "appcompat",
            "androidx.core:core-ktx:1.0" to "core-ktx",
            "com.squareup.okhttp3:okhttp:1.0" to "okhttp3",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0" to "kotlinx-coroutines-core",
            "com.google.guava:guava:1.0" to "guava",
            "com.foo:bar:1.0" to "foo-bar",
            "com.foo:foo_bar:1.0" to "foo-bar",
            "class:class:1.0" to "classLib",
            "123:123:1.0" to "lib-123"
        )
        for ((coordinates, expected) in examples) assertEquals(expected, MavenDependency.parse(coordinates)!!.alias())
    }

    @Test fun findsKotlinGroovyWrappersAndMultipleDependencies() {
        val text = """
            dependencies {
                implementation("androidx.appcompat:appcompat:1.8.0")
                api 'androidx.core:core-ktx:1.0'
                debugImplementation(platform("com.foo:bom:2.0"))
                ksp("com.foo:processor:3.0")
            }
        """.trimIndent()
        val found = GradleDependencyPaste.find(text, 0, text.length)
        assertEquals(listOf("appcompat", "core-ktx", "bom", "processor"), found.map { it.dependency.artifact })
        for (paste in found) assertEquals(paste.dependency, MavenDependency.parse(text.substring(paste.start + 1, paste.end - 1)))
    }

    @Test fun changesOnlyPastedLiterals() {
        val text = "dependencies { implementation(\"g:a:1\"); api(\"g:b:2\") }"
        val start = text.indexOf("api")
        assertEquals(listOf("b"), GradleDependencyPaste.find(text, start, text.length).map { it.dependency.artifact })
        assertTrue(GradleDependencyPaste.find(text, text.indexOf("g:a"), text.indexOf("g:a") + 5).isEmpty())
    }

    @Test fun ignoresCommentsOtherBlocksInterpolationClassifiersAndConcatenation() {
        val text = """
            val unrelated = "g:a:1"
            implementation("g:a:1")
            buildscript { dependencies { implementation("g:a:1") } }
            dependencies {
                // implementation("g:a:1")
                /* implementation("g:a:1") */
                val example = "implementation('g:a:1')"
                implementation("g:a:${'$'}version")
                implementation("g:a:1:tests")
                implementation("g:a:1@aar")
                implementation("g:a:1" + "-suffix")
                constraints { api("g:a:1") }
            }
        """.trimIndent()
        assertTrue(GradleDependencyPaste.find(text, 0, text.length).isEmpty())
    }
}
