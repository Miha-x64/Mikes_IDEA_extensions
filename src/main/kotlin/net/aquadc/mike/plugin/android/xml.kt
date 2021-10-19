package net.aquadc.mike.plugin.android

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.DRAWABLE
import com.android.resources.ResourceFolderType.LAYOUT
import com.intellij.codeInspection.*
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import java.text.MessageFormat
import java.util.*
import com.android.tools.idea.gradle.project.model.AndroidModuleModel.get as androidModelModule
import org.jetbrains.android.facet.AndroidFacet.getInstance as androidFacetOf
import org.jetbrains.android.resourceManagers.ModuleResourceManagers.getInstance as moduleResManager
import java.lang.Character.MIN_VALUE as nullChar

/**
 * @author Mike Gorünóv
 */
class UnsupportedAttrInspection : LocalInspectionTool() {

    private class Crap(
        val resType: ResourceFolderType,
        private val tag: String,
        private val attr: String,
        val message: String,
        val requiredSdk: Int = Int.MAX_VALUE,
        private val prohibitedFirstChar: Char = nullChar,
    ) {
        fun test(minSdk: Int, resType: ResourceFolderType, tag: String, attr: String, xml: XmlAttribute): Boolean {
            if (this.requiredSdk <= minSdk || this.resType != resType || this.tag != tag || this.attr != attr)
                return false

            if (prohibitedFirstChar == nullChar)
                return true // bad attribute, don't even check value

            val value = xml.value?.takeIf(String::isNotEmpty) ?: return false
            return value[0] == prohibitedFirstChar // bad prefix
        }

    }

    private companion object {
        private val knownSdks = intArrayOf(21, 23)
        private val knownSdkNames = arrayOf("Lollipop", "Marshmallow")
        private val shitBunch = arrayOf(
            Crap(
                DRAWABLE, "bitmap", "tint", requiredSdk = 21,
                message = "<bitmap android:{0}> {1}",
            ),
            Crap(
                LAYOUT, "include", "layout", requiredSdk = 23, prohibitedFirstChar = '?',
                message = "<include layout=\"?themeAttribute\"> {1}",
            ),
        )
        private val resTypes = shitBunch.mapTo(EnumSet.noneOf(ResourceFolderType::class.java), Crap::resType)
    }

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitFile(file: PsiFile) {
            if (file !is XmlFile) return
            val af = androidFacetOf(file) ?: return
            val minSdk = androidModelModule(af)?.minSdkVersion?.apiLevel ?: return
            val resType = moduleResManager(af).localResourceManager.getFileResourceFolderType(file)
                ?.takeIf(resTypes::contains) ?: return
            file.accept(object : XmlRecursiveElementVisitor() {
                override fun visitXmlAttribute(attribute: XmlAttribute) {
                    val tag = attribute.parent.name
                    val attr = attribute.localName
                    shitBunch.firstOrNull { it.test(minSdk, resType, tag, attr, attribute) }?.let {
                        val sdkIdx = knownSdks.indexOf(it.requiredSdk)
                        val cries =
                            if (sdkIdx < 0) "support is not implemented"
                            else "support requires SDK ${it.requiredSdk} (${knownSdkNames[sdkIdx]})"
                        holder.registerProblem(attribute.originalElement, MessageFormat.format(it.message, attr, cries))
                    }
                }

            })
        }

    }
}
