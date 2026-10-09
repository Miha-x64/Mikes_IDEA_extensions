package net.aquadc.mike.plugin.gradle

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreProjectEnvironment
import com.intellij.lang.LanguageASTFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.toml.lang.TomlLanguage
import org.toml.lang.parse.TomlParserDefinition
import org.toml.lang.psi.TomlFileType
import org.toml.lang.psi.impl.TomlASTFactory

class VersionCatalogTest {
    private val appcompat = MavenDependency("androidx.appcompat", "appcompat", "1.8.0")

    @Test fun createsSeparateSectionsAndRepeatedPasteIsIdempotent() {
        val catalog = VersionCatalog.parse(project, "")!!
        assertEquals("libs.appcompat", catalog.reference(appcompat))
        val text = catalog.updatedText()
        assertEquals("""
            [versions]
            appcompat = "1.8.0"

            [libraries]
            appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
        """.trimIndent() + "\n", text)
        val again = VersionCatalog.parse(project, text)!!
        assertEquals("libs.appcompat", again.reference(appcompat))
        assertEquals(text, again.updatedText())
    }

    @Test fun reusesModuleGroupNameAndStringNotationWithCustomAliases() {
        val forms = listOf(
            "{ module = 'androidx.appcompat:appcompat', version.ref = 'androidx' }",
            "{ group = 'androidx.appcompat', name = 'appcompat', version = '1.8.0' }",
            "'androidx.appcompat:appcompat:1.8.0'",
            "{ module = 'androidx.appcompat:appcompat', version = { ref = 'androidx' } }"
        )
        for (form in forms) {
            val text = "[versions]\nandroidx = '1.8.0'\n[libraries]\ncustom-name = $form\n"
            val catalog = VersionCatalog.parse(project, text)!!
            assertEquals("libs.custom.name", catalog.reference(appcompat))
            assertEquals(text, catalog.updatedText())
        }
    }

    @Test fun differentVersionCreatesNewAliasesWithoutChangingExistingDependency() {
        val text = """
            [versions]
            appcompat = "1.7.0"
            [libraries]
            appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
        """.trimIndent() + "\n"
        val catalog = VersionCatalog.parse(project, text)!!
        assertEquals("libs.appcompat2", catalog.reference(appcompat))
        val updated = catalog.updatedText()
        assertTrue(updated.contains("appcompat = \"1.7.0\""))
        assertTrue(updated.contains("appcompat2 = \"1.8.0\""))
        assertTrue(updated.contains("appcompat2 = { module = \"androidx.appcompat:appcompat\", version.ref = \"appcompat2\" }"))
        assertEquals("libs.appcompat2", VersionCatalog.parse(project, updated)!!.reference(appcompat))
    }

    @Test fun preservesCommentsPluginAndBundleSectionsAndAddsMissingSection() {
        val text = """
            # keep this comment
            [versions]
            kotlin = "2.3.0" # and this one

            [plugins]
            kotlin = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
        """.trimIndent()
        val catalog = VersionCatalog.parse(project, text)!!
        catalog.reference(appcompat)
        val updated = catalog.updatedText()
        assertTrue(updated.startsWith("# keep this comment\n[versions]\nkotlin = \"2.3.0\" # and this one\nappcompat = \"1.8.0\"\n"))
        assertTrue(updated.contains("[plugins]\nkotlin = { id = \"org.jetbrains.kotlin.jvm\", version.ref = \"kotlin\" }"))
        assertNotNull(VersionCatalog.parse(project, updated))
    }

    @Test fun avoidsNormalizedAndPrefixCollisionsAndReusesNormalizedVersionAlias() {
        val catalog = VersionCatalog.parse(project, """
            [versions]
            core_ktx = "1.0"
            [libraries]
            core = "g:a:2"
            core_ktx = "g:b:2"
        """.trimIndent())!!
        assertEquals("libs.core2.ktx", catalog.reference(MavenDependency("androidx.core", "core-ktx", "1.0")))
        val text = catalog.updatedText()
        assertTrue(text.contains("version.ref = \"core_ktx\""))
        assertFalse(text.contains("core-ktx = \"1.0\""))
    }

    @Test fun rejectsInvalidDuplicateAndSubtableCatalogs() {
        for (text in listOf(
            "[versions]\na = \"unterminated",
            "[libraries]\na = 'g:a:1'\na = 'g:b:2'",
            "[libraries]\ncore-ktx = 'g:a:1'\ncore_ktx = 'g:b:2'",
            "[libraries.foo]\nmodule = 'g:a'\nversion = '1'",
            "[versions]\na = '1'\n[versions]\nb = '2'"
        )) assertNull(text, VersionCatalog.parse(project, text))
    }

    @Test fun addsEntriesBeforeMissingSectionsEvenWhenExistingSectionEndsAtEof() {
        for (text in listOf("[libraries]", "[libraries]\nother = 'g:a:1'", "[libraries]\n", "[versions]")) {
            val catalog = VersionCatalog.parse(project, text)!!
            assertEquals("libs.appcompat", catalog.reference(appcompat))
            val updated = catalog.updatedText()
            val again = VersionCatalog.parse(project, updated)!!
            assertEquals("libs.appcompat", again.reference(appcompat))
            assertEquals(updated, again.updatedText())
        }
    }

    @Test fun escapesKeywordAndNumericAccessorsForEachDslAndReusesParentProvider() {
        val text = "[libraries]\nwhen-123 = 'androidx.appcompat:appcompat:1.8.0'\n"
        assertEquals("libs.`when`.`123`", VersionCatalog.parse(project, text)!!.reference(appcompat))
        assertEquals("libs.'when'.'123'", VersionCatalog.parse(project, text, kotlinDsl = false)!!.reference(appcompat))
        val parent = "[libraries]\nappcompat = 'androidx.appcompat:appcompat:1.8.0'\nappcompat-other = 'g:a:1'\n"
        assertEquals("libs.appcompat.asProvider()", VersionCatalog.parse(project, parent)!!.reference(appcompat))
    }

    companion object {
        private lateinit var disposable: Disposable
        private lateinit var project: Project

        @BeforeClass @JvmStatic fun setUpParser() {
            disposable = Disposer.newDisposable()
            val app = CoreApplicationEnvironment(disposable)
            app.registerFileType(TomlFileType, "toml")
            app.registerParserDefinition(TomlParserDefinition())
            app.addExplicitExtension(LanguageASTFactory.INSTANCE, TomlLanguage, TomlASTFactory())
            project = CoreProjectEnvironment(disposable, app).project
        }

        @AfterClass @JvmStatic fun tearDownParser() { Disposer.dispose(disposable) }
    }
}
