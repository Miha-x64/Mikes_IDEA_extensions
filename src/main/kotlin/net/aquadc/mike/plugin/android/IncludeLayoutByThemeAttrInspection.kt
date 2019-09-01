package net.aquadc.mike.plugin.android

import com.android.resources.ResourceFolderType
import com.intellij.codeInspection.*
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers


class IncludeLayoutByThemeAttrInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file.isLayoutXml)
                    file.accept(object : XmlRecursiveElementVisitor() {
                        override fun visitXmlAttribute(attribute: XmlAttribute) {
                            if (attribute.value?.startsWith('?') == true && attribute.parent.name == "include" && attribute.name == "layout") {
                                holder.registerProblem(
                                    attribute.originalElement,
                                    "<include layout=\"?themeAttribute\"> support added only since Marshmallow"
                                )
                            }
                        }
                    })
            }
        }

    private val PsiFile.isLayoutXml: Boolean
        get() =
            this is XmlFile && AndroidFacet.getInstance(this).let { facet ->
                facet != null && ModuleResourceManagers.getInstance(facet).localResourceManager.getFileResourceFolderType(this) == ResourceFolderType.LAYOUT
            }

}
