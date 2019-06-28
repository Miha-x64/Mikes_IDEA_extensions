package net.aquadc.mike.plugin.inspection

import com.android.resources.ResourceFolderType
import com.intellij.codeInspection.*
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers


class AndroidIncludeLayoutByThemeAttrInspection : LocalInspectionTool() {

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? =
        if (file.isLayoutXml) {
            Vis(manager, isOnTheFly).also(file::accept).result.toArray(ProblemDescriptor.EMPTY_ARRAY)
        } else {
            ProblemDescriptor.EMPTY_ARRAY
        }

    private val PsiFile.isLayoutXml: Boolean
        get() =
            this is XmlFile && AndroidFacet.getInstance(this).let { facet ->
                facet != null && ModuleResourceManagers.getInstance(facet).localResourceManager.getFileResourceFolderType(this) == ResourceFolderType.LAYOUT
            }

    private class Vis(
        private val manager: InspectionManager,
        private val onTheFly: Boolean
    ) : XmlRecursiveElementVisitor() {
        val result = ArrayList<ProblemDescriptor>()
        override fun visitXmlAttribute(attribute: XmlAttribute) {
            if (attribute.value?.startsWith('?') == true && attribute.parent.name == "include" && attribute.name == "layout") {
                result += manager.createProblemDescriptor(
                    attribute.originalElement, "<include layout=\"?themeAttribute\"> support added only since Marshmallow",
                    onTheFly, LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR
                )
            }
        }
    }

}
