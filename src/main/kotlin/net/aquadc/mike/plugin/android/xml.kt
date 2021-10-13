package net.aquadc.mike.plugin.android

import com.android.resources.ResourceFolderType
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.intellij.codeInspection.*
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import java.text.MessageFormat

/**
 * @author Mike Gorünóv
 */
class IncludeLayoutByThemeAttrInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        XmlAttrVisitor(
            holder, ResourceFolderType.LAYOUT, 23, "include", arrayOf("layout"), charArrayOf('?'),
            "<include layout=\"?themeAttribute\"> support requires Marshmallow"
        )
}

/**
 * @author Mike Gorünóv
 */
class ViewClassFromResourcesInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        XmlAttrVisitor(
            holder, ResourceFolderType.LAYOUT, -1, "view", arrayOf("class"), charArrayOf('?', '@'),
            "<view class=\"@resource or ?themeAttribute\"> is not supported"
        )
}

/**
 * @author Mike Gorünóv
 */
class BitmapTintInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        XmlAttrVisitor(
            holder, ResourceFolderType.DRAWABLE, 21, "bitmap", arrayOf("tint", "tintMode"), null,
            "<bitmap android:{0}> support requires Lollipop"
        )
}

private class XmlAttrVisitor(
    private val holder: ProblemsHolder,
    private val resType: ResourceFolderType,
    private val requiredSdk: Int,
    private val tag: String,
    private val attrs: Array<String>,
    private val prohibitedPrefixes: CharArray?,
    private val message: String
) : PsiElementVisitor() {

    override fun visitFile(file: PsiFile) {
        if ((requiredSdk < 0 || file.androidFacet?.isLowerThan(requiredSdk) == true) &&
            file is XmlFile && file.hasExpectedType())
            file.accept(object : XmlRecursiveElementVisitor() {
                override fun visitXmlAttribute(attribute: XmlAttribute) {
                    if (attribute.parent.name == tag) {
                        val attr = attribute.findExpectedAttr()
                        if (attr != null) {
                            if (prohibitedPrefixes == null || attribute.value.isProhibited(prohibitedPrefixes)) {
                                holder.registerProblem(
                                    attribute.originalElement,
                                    MessageFormat.format(message, attr)
                                )
                            }
                        }
                    }
                }

                private fun XmlAttribute.findExpectedAttr(): String? = name.let { name ->
                    val colon = name.indexOf(':') + 1
                    val cleanName = if (colon > 1) name.substring(colon) else name
                    attrs.firstOrNull { it == cleanName }
                }

                private fun String?.isProhibited(prohibitedPrefixes: CharArray) =
                    this != null && this.isNotEmpty() && this[0] in prohibitedPrefixes
            })
    }

    private fun PsiFile.hasExpectedType() =
        androidFacet?.moduleResManagers?.localResourceManager?.getFileResourceFolderType(this) == resType
}

private inline val PsiFile.androidFacet: AndroidFacet?
    get() = AndroidFacet.getInstance(this)

private inline val AndroidFacet.moduleResManagers: ModuleResourceManagers
    get() = ModuleResourceManagers.getInstance(this)

private fun AndroidFacet.isLowerThan(expected: Int): Boolean? =
    AndroidModuleModel.get(this)?.minSdkVersion?.apiLevel?.let { it < expected }
