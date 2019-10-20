package net.aquadc.mike.plugin.android

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute


class IncludeLayoutByThemeAttrInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file.androidFacet?.isLowerThan(23) == true && file.isLayoutXml) {
                    file.accept(object : XmlRecursiveElementVisitor() {
                        override fun visitXmlAttribute(attribute: XmlAttribute) {
                            if (attribute.value?.startsWith('?') == true && attribute.parent.name == "include" && attribute.name == "layout") {
                                holder.registerProblem(
                                    attribute.originalElement,
                                    "<include layout=\"?themeAttribute\"> support requires Marshmallow"
                                )
                            }
                        }
                    })
                }
            }
        }

}
