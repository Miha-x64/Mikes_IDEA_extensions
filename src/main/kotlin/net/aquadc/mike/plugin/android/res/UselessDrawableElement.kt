package net.aquadc.mike.plugin.android.res

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import net.aquadc.mike.plugin.NamedLocalQuickFix

internal fun ProblemsHolder.checkDrawableTag(tag: XmlTag) {
    when (tag.name) {
        "layer-list" -> checkLayerList(tag)
        "inset" -> checkInset(tag)
        "shape" -> checkShape(tag)
        "vector" -> checkVector(tag)
        "animated-vector" -> // TODO propose inlining vector and animator if never used elsewhere
            tag.findAaptAttrTag("drawable")?.subTags?.singleOrNull()?.let(this::checkDrawableTag)
        "clip", "scale" ->
            tag.subTags.singleOrNull()?.let(this::checkDrawableTag)
        "selector" -> checkSelector(tag)
        "level-list", "transition" ->
            tag.subTags.forEach { if (it.name == "item") it.subTags.singleOrNull()?.let(this::checkDrawableTag) }
    }
}

private fun ProblemsHolder.checkLayerList(tag: XmlTag) {
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
            report(
                tag,
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
                            val repl = layerList.replace(itemChild)
                            CodeStyleManager.getInstance(project).reformat(repl)
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

                            CodeStyleManager.getInstance(project).reformat(layerList)
                        }
                    }
                }
            )
        }
    }
    children.forEach { item ->
        item.subTags.singleOrNull()?.let(this::checkDrawableTag)
    }
}
private fun ProblemsHolder.checkInset(tag: XmlTag) {
    if (insetInsets.all { inset -> tag.getAttribute(inset, ANDROID_NS) == null })
        report(
            tag, "The inset with no insets is useless",
            tag.subTags.singleOrNull()?.takeIf { tag.getAttribute("drawable", ANDROID_NS) == null }?.let {
                inlineContentsFix
            }
        )
    tag.subTags.singleOrNull()?.let(this::checkDrawableTag)
}
private fun ProblemsHolder.checkShape(tag: XmlTag) {
    if (tag.subTags.none { it.name in necessaryShapeTags && it.attributes.isNotEmpty() })
        report(
            tag,
            "The shape is useless: no size, no fill, no stroke (only outline, maybe). " +
                    "Just use <code>@null</code> to override a value in XML, " +
                    "0 in place of <code>@DrawableRes int</code>, " +
                    "or <code>null</code> in place of <code>Drawable</code>",
            null
        )
}
private fun ProblemsHolder.checkSelector(tag: XmlTag) {
    tag.subTags.singleOrNull()?.let { item ->
        val onlyDrawableRef =
            item.attributes.singleOrNull()
                ?.let { it.namespace == ANDROID_NS && it.localName == "drawable" } == true
        if (onlyDrawableRef || item.attributes.isEmpty())
            report(
                tag,
                "The selector with single stateless item is useless",
                inlineContentsFix.takeIf { !onlyDrawableRef && item.subTags.size == 1 },
            )
    }
    tag.subTags.forEach { if (it.name == "item") it.subTags.singleOrNull()?.let(this::checkDrawableTag) }
}

private val undocumentedLayerProps = arrayOf("start", "end", "width", "height", "gravity")
private val layerInsets = arrayOf("left", "top", "right", "bottom")
private val insetInsets = arrayOf("insetLeft", "insetTop", "insetRight", "insetBottom", "inset")
private val necessaryShapeTags = arrayOf("gradient", "size", "solid", "stroke")
