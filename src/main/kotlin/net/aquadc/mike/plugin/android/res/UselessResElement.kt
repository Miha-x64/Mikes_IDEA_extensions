package net.aquadc.mike.plugin.android.res

import com.android.resources.ResourceFolderType.*
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.tree.RoleFinder
import com.intellij.psi.xml.XmlChildRole
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import net.aquadc.mike.plugin.NamedLocalQuickFix
import net.aquadc.mike.plugin.android.androidMinSdk
import net.aquadc.mike.plugin.android.resTypeOf

/**
 * @author Mike Gorünóv
 */
class UselessResElement : LocalInspectionTool(), CleanupLocalInspectionTool {
    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitFile(file: PsiFile) {
            val af = (file as? XmlFile)?.androidFacet
            when (af?.resTypeOf(file)) {
                DRAWABLE -> file.rootTag?.let(holder::checkDrawableTag)
                ANIM, ANIMATOR -> file.rootTag?.let(::checkAnim)
                XML -> file.rootTag?.let(::checkXml)
                LAYOUT -> file.rootTag?.let {
                    MarginsPaddings.checkLayoutTag(holder, af.androidMinSdk()?.apiLevel ?: -1, isOnTheFly, it)
                }
                else -> {} // nothing to do here
            }
        }
        private fun checkAnim(tag: XmlTag) {
            if (tag.name == "set") when (tag.subTags.size) {
                0 -> holder.report(tag, "Empty set", null)
                1 -> holder.report(tag, "Single-item set", null)
            }
        }
        private fun checkXml(tag: XmlTag) {
            // https://developer.android.com/guide/topics/graphics/prop-animation#ViewState
            if (tag.name == "selector" && tag.getPrefixByNamespace(ANDROID_NS) != null)
                tag.subTags.forEach { item ->
                    item.takeIf { it.name == "item" }?.subTags?.single()?.let(::checkAnim)
                }
        }
    }
}

internal fun XmlTag.findAaptAttrTag(name: String) =
    subTags.firstOrNull { it.isAaptInlineAttr(name) }
internal fun XmlTag.isAaptInlineAttr(name: String): Boolean {
    if (namespace == AAPT_NS && localName == "attr") {
        val actualName = getAttributeValue("name") ?: return false
        val android = getPrefixByNamespace(ANDROID_NS) ?: return false
        return if (android.isEmpty()) actualName == name else
            actualName.length == (android.length + 1 + name.length) &&
                    actualName.startsWith(android) &&
                    actualName[android.length] == ':' &&
                    actualName.endsWith(name)
    }
    return false
}

internal fun ProblemsHolder.report(
    el: XmlElement, message: String, fix: LocalQuickFix?,
    hl: ProblemHighlightType = ProblemHighlightType.LIKE_UNUSED_SYMBOL,
    finder: RoleFinder = XmlChildRole.START_TAG_END_FINDER,
) {
    registerProblem(manager.createProblemDescriptor(
        // < tag-name ...attribute="value" >
        el.firstChild ?: el,
        (el as? XmlTag)?.node?.let { finder.findChild(it)?.psi } ?: el.lastChild ?: el,
        message, hl, isOnTheFly, fix,
    ))
}

internal const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
internal const val AAPT_NS = "http://schemas.android.com/aapt"

internal val inlineContentsFix = object : NamedLocalQuickFix("Inline contents") {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val parent = descriptor.psiElement as XmlTag
        var child = parent.subTags.single()
        if (child.name == "item") child = child.subTags.single()
        parent.transferNamespacesTo(child)
        val repl = parent.replace(child)
         CodeStyleManager.getInstance(project).reformat(repl)
    }
}
internal fun XmlTag.transferNamespacesTo(dest: XmlTag) {
    attributes.forEach {
        if (it.isNamespaceDeclaration)
            dest.setAttribute(it.name, it.value)
    }
}
