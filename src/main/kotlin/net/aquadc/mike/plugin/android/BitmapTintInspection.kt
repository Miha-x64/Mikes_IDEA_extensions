package net.aquadc.mike.plugin.android

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute


class BitmapTintInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file.androidFacet?.isLowerThan(21) == true && file.isDrawableXml) {
                    file.accept(object : XmlRecursiveElementVisitor() {
                        override fun visitXmlAttribute(attribute: XmlAttribute) {
                            if (attribute.parent.name == "bitmap") {
                                if (attribute.name.endsWith(":tint")) {
                                    holder.registerProblem(
                                        attribute.originalElement,
                                        "<bitmap android:tint> require Lollipop"
                                    )
                                } else if (attribute.name.endsWith(":tintMode")) {
                                    holder.registerProblem(
                                        attribute.originalElement,
                                        "<bitmap android:tintMode> require Lollipop"
                                    )
                                }
                            }
                        }
                    })
                }
            }
        }

}
