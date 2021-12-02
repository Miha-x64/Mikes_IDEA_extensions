package net.aquadc.mike.plugin.android

import com.android.resources.ResourceFolderType.DRAWABLE
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
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import net.aquadc.mike.plugin.NamedLocalQuickFix
import net.aquadc.mike.plugin.resTypeOf

/**
 * @author Mike Gorünóv
 */
class UselessDrawableElement : LocalInspectionTool(), CleanupLocalInspectionTool {
    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitFile(file: PsiFile) {
            if (file is XmlFile && file.androidFacet?.resTypeOf(file) == DRAWABLE)
                file.rootTag?.let(::checkTag)
        }

        private fun checkTag(tag: XmlTag) {
            when (tag.name) {
                "layer-list" -> checkLayerList(tag)
                "inset" -> checkInset(tag)
                "shape" -> checkShape(tag)
                "vector" -> holder.checkVector(tag)
                "clip", "scale" ->
                    tag.subTags.singleOrNull()?.let(::checkTag)
                "selector", "level-list", "transition" ->
                    tag.subTags.forEach { if (it.name == "item") it.subTags.singleOrNull()?.let(::checkTag) }
            }
        }

        private fun checkLayerList(tag: XmlTag) {
            val children = tag.subTags
            children.singleOrNull()?.let { item ->
                // assume you know what you're doing if a layer has ID
                if (item.getAttributeValue("id", ANDROID_NS) == null &&
                    undocumentedLayerProps.all { item.getAttributeValue(it, ANDROID_NS) == null }) {
                    val insets = layerInsets.foldIndexed(0) { i, acc, inset ->
                        acc or if (item.getAttribute(inset, ANDROID_NS) == null) 0 else 1 shl i
                    }
                    val itemChild/*: XmlAttribute | XmlTag */ = item.getAttribute("drawable", ANDROID_NS).let { ref ->
                        if (ref != null) ref.takeIf { item.subTags.isEmpty() } else item.subTags.singleOrNull()
                    }
                    holder.report(
                        item,
                        if (insets == 0) "The layer-list with single item is useless"
                        else "The layer-list with single item having insets should be replaced with <inset>",
                        if (itemChild == null
                            || (itemChild is XmlAttribute && insets == 0) // oops, no easy way to inline
                            || (itemChild is XmlTag && insets != 0 &&
                                    (insets and insetInsets.foldIndexed(0) { i, acc, inset ->
                                        acc or if (itemChild.getAttribute(inset, ANDROID_NS) == null) 0 else 1 shl i
                                    }) != 0) // <item left="x"><inset insetLeft="y"> could be hard or impossible to merge
                        ) null else object : NamedLocalQuickFix(
                            if (insets == 0) "Replace <layer-list> with <item> contents"
                            else "Replace <layer-list> with <inset>"
                        ) {
                            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                                val layerList = descriptor.psiElement as XmlTag
                                if (insets == 0) {
                                    itemChild as XmlTag
                                    layerList.transferNamespacesTo(itemChild)
                                    layerList.replace(itemChild)
                                } else {
                                    layerList.name = "inset"
                                    item.attributes.forEach {
                                        val name = it.localName
                                        val iof = layerInsets.indexOf(name)
                                        layerList.setAttribute(
                                            if (iof < 0) it.name else "${it.namespacePrefix}:${insetInsets[iof]}",
                                            it.value
                                        )
                                    }

                                    if (itemChild is XmlTag)
                                        layerList.addSubTag(itemChild, true)

                                    item.delete()

                                    if (itemChild is XmlAttribute)
                                        layerList.collapseIfEmpty()
                                }
                            }
                        }
                    )
                }
            }
            children.forEach { item ->
                item.subTags.singleOrNull()?.let(::checkTag)
            }
        }
        private fun checkInset(tag: XmlTag) {
            if (insetInsets.all { inset -> tag.getAttribute(inset, ANDROID_NS) == null })
                holder.report(
                    tag, "The inset with no insets is useless",
                    tag.subTags.singleOrNull()?.takeIf { tag.getAttribute("drawable", ANDROID_NS) == null }?.let {
                        object : NamedLocalQuickFix("Inline <inset> content") {
                            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                                val inset = descriptor.psiElement as XmlTag
                                val child = inset.subTags.single()
                                inset.transferNamespacesTo(child)
                                inset.replace(child)
                            }
                        }
                    }
                )
            tag.subTags.singleOrNull()?.let(::checkTag)
        }
        private fun checkShape(tag: XmlTag) {
            if (tag.subTags.none { it.name in necessaryShapeTags && it.attributes.isNotEmpty() })
                holder.report(
                    tag,
                    "The shape is useless: no size, no fill, no stroke (only outline, maybe). " +
                            "Just use <code>@null</code> to override a value in XML, " +
                            "0 in place of <code>@DrawableRes int</code>, " +
                            "or <code>null</code> in place of <code>Drawable</code>",
                    null
                )
        }

        private fun XmlTag.transferNamespacesTo(dest: XmlTag) {
            attributes.forEach {
                if (it.isNamespaceDeclaration)
                    dest.setAttribute(it.name, it.value)
            }
        }
    }
}

internal fun ProblemsHolder.report(el: XmlElement, message: String, fix: LocalQuickFix?) {
    registerProblem(manager.createProblemDescriptor(
        // < tag-name ...attribute="value" >
        el.firstChild ?: el, (el as? XmlTag)?.subTags?.firstOrNull()?.prevSibling ?: el.lastChild ?: el,
        message,
        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
        isOnTheFly,
        fix
    ))
}

internal const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private val undocumentedLayerProps = arrayOf("start", "end", "width", "height", "gravity")
private val layerInsets = arrayOf("left", "top", "right", "bottom")
private val insetInsets = arrayOf("insetLeft", "insetTop", "insetRight", "insetBottom")
private val necessaryShapeTags = arrayOf("gradient", "size", "solid", "stroke")
