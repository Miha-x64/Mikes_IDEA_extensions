package net.aquadc.mike.plugin.android

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute


class IncludeLayoutByThemeAttrInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        LayoutXmlAttrVisitor(
            holder, 23, "include", "layout", charArrayOf('?'),
            "<include layout=\"?themeAttribute\"> support requires Marshmallow"
        )
}

class ViewClassFromResourcesInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        LayoutXmlAttrVisitor(
            holder, -1, "view", "class", charArrayOf('?', '@'),
            "<view class=\"@resource or ?themeAttribute\"> is not supported"
        )
}

private class LayoutXmlAttrVisitor(
    private val holder: ProblemsHolder,
    private val requiredSdk: Int,
    private val tag: String,
    private val attr: String,
    private val prohibitedChars: CharArray,
    private val message: String
) : PsiElementVisitor() {

    override fun visitFile(file: PsiFile) {
        if (requiredSdk < 0 || file.androidFacet?.isLowerThan(requiredSdk) == true) {
            if (file.isLayoutXml) {
                file.accept(object : XmlRecursiveElementVisitor() {
                    override fun visitXmlAttribute(attribute: XmlAttribute) {
                        if (attribute.parent.name == tag && attribute.name == attr) {
                            if (attribute.value.let { it != null && it.isNotEmpty() && it[0] in prohibitedChars }) {
                                holder.registerProblem(attribute.originalElement, message)
                            }
                        }
                    }
                })
            }
        }
    }
}
