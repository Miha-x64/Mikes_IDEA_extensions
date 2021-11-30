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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import net.aquadc.mike.plugin.NamedLocalQuickFix
import net.aquadc.mike.plugin.resTypeOf
import org.jetbrains.plugins.groovy.codeInspection.fixes.RemoveElementQuickFix
import java.awt.BasicStroke
import java.awt.Shape
import java.awt.geom.Rectangle2D
import java.awt.geom.Area

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
                "vector" -> checkVector(tag)
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
                if (item.getAttributeValue("id", ANDROID_NS) == null) {
                    val insets = layerInsets.foldIndexed(0) { i, acc, inset ->
                        acc or if (item.getAttribute(inset, ANDROID_NS) == null) 0 else 1 shl i
                    }
                    val itemChild/*: XmlAttribute | XmlTag */ = item.getAttribute("drawable", ANDROID_NS).let { ref ->
                        if (ref != null) ref.takeIf { item.subTags.isEmpty() } else item.subTags.singleOrNull()
                    }
                    report(
                        item,
                        if (insets == 0) "This <layer-list> with single <item> is useless"
                        else "The whole <layer-list> with single <item> having insets should be replaced with <inset>",
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
                report(
                    tag, "This <inset> with no insets is useless",
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
                report(
                    tag,
                    "The shape is useless: no size, no fill, no stroke (only outline, maybe). " +
                            "Just use <code>@null</code> to override a value in XML, " +
                            "0 in place of <code>@DrawableRes int</code>, " +
                            "or <code>null</code> in place of <code>Drawable</code>",
                    null
                )
        }
        private fun checkVector(tag: XmlTag) {
            val width = tag.getAttributeValue("viewportWidth", ANDROID_NS)?.toFloatOrNull()
            val height = tag.getAttributeValue("viewportHeight", ANDROID_NS)?.toFloatOrNull()
            val viewport =
                if (width != null && height != null)
                    Area(Rectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble()))
                else null
            tag.subTags.forEach { checkVectorSubTag(it, viewport) }
        }
        private fun checkVectorSubTag(it: XmlTag, viewport: Area?) {
            when (it.name) {
                "group" -> checkVectorGroup(it, viewport)
                "clip-path" -> checkVectorClipPath(it, viewport)
                "path" -> checkVectorPath(it, viewport)
            }
        }
        private fun checkVectorGroup(tag: XmlTag, viewport: Area?) {
            val hasTransforms = vectorTransformations.foldIndexed(false) { i, acc, attr ->
                acc || tag.getAttributeValue(attr, ANDROID_NS)
                    .let { it != null && it.toFloatOrNull() != vectorTransformDefaults[i] }
            }
            if (tag.getAttributeValue("name", ANDROID_NS) == null &&
                !hasTransforms &&
                tag.subTags.none { it.name == "clip-path" })
                report(
                    tag, "This group is useless: no transformations, no <clip-path>s.",
                    object : NamedLocalQuickFix(if (tag.subTags.isEmpty()) "Remove <group>" else "Inline contents") {
                        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                            val group = descriptor.psiElement as XmlTag
                            val children = group.subTags
                            if (children.isNotEmpty())
                                group.parent.addRangeAfter(children.first(), children.last(), group)
                            group.delete()
                        }
                    }
                )

            tag.subTags.forEach { checkVectorSubTag(it, viewport) }
        }
        private fun checkVectorClipPath(tag: XmlTag, viewport: Area?) {
            val pathData = tag.getAttributeValue("pathData", ANDROID_NS)
            if (pathData == null) {
                report(tag, "<clip-path> without pathData", RemoveElementQuickFix("Remove tag"))
            } else try {
                val clipPath = Area(PathDelegate.parse(pathData))
                viewport?.let(clipPath::intersect)
                if (clipPath.isEmpty) {
                    holder.registerProblem(
                        tag,
                        "The clip is empty, and the whole <group> is invisible",
                        RemoveElementQuickFix("Remove group", PsiElement::getParent)
                    )
                } else if ((tag.parent as XmlTag).subTags.all { child ->
                        when (child.name) {
                            "path" -> isPathUnclipped(child, viewport, clipPath)
                            "group" -> false // TODO this would involve transforming paths and intersecting clips,
                            else -> true     //      but now just give up when a group encountered
                        }
                }) {
                    report(tag, "The path doesn't clip any of its siblings", RemoveElementQuickFix("Remove tag"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // bad path, ignore
            }
        }
        private fun isPathUnclipped(pathTag: XmlTag, viewport: Area?, clipPath: Area): Boolean {
            val path = parseArea(pathTag) ?: return true // nothing to clip
            viewport?.let(path::intersect) // don't mind anything clipped away by viewport
            if (path.isEmpty) return true
            path.subtract(clipPath)
            return path.isEmpty
        }
        private fun checkVectorPath(pathTag: XmlTag, viewport: Area?) {
            val path = parseArea(pathTag) ?: return // nothing to check
            if (path.isEmpty)
                return report(pathTag, "Empty path", RemoveElementQuickFix("Remove tag"))
            if (viewport != null) {
                path.intersect(viewport)
                if (path.isEmpty)
                    report(pathTag, "The path is outside of viewport", RemoveElementQuickFix("Remove tag"))
            }
        }

        private fun parseArea(pathTag: XmlTag): Area? {
            val outline = PathDelegate.parse(pathTag.getAttributeValue("pathData", ANDROID_NS) ?: return null)

            val strokeWidth = pathTag.getAttributeValue("strokeWidth", ANDROID_NS)?.takeIf {
                pathTag.getAttributeValue("strokeColor", ANDROID_NS) != null
            }?.toFloatOrNull() ?: 0f

            return Area(
                if (strokeWidth != 0f) {
                    val cap = when (pathTag.getAttributeValue("strokeLineCap", ANDROID_NS)) {
                        "round" -> BasicStroke.CAP_ROUND
                        "square" -> BasicStroke.CAP_SQUARE
                        else -> BasicStroke.CAP_BUTT
                    }
                    val join = when (pathTag.getAttributeValue("strokeLineJoin", ANDROID_NS)) {
                        "round" -> BasicStroke.JOIN_ROUND
                        "bevel" -> BasicStroke.JOIN_BEVEL
                        else -> BasicStroke.JOIN_MITER
                    }
                    val miter = pathTag.getAttributeValue("strokeMiterLimit", ANDROID_NS)?.toFloatOrNull() ?: 4f
                    BasicStroke(strokeWidth, cap, join, miter).createStrokedShape(outline)
                } else outline
            )
        }


        private fun report(tag: XmlTag, message: String, fix: LocalQuickFix?) {
            holder.registerProblem(holder.manager.createProblemDescriptor(
                // < tag-name ...attribute="value" >
                tag.firstChild, tag.subTags.firstOrNull()?.prevSibling ?: tag.lastChild,
                message,
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                isOnTheFly,
                fix
            ))
        }

        private fun XmlTag.transferNamespacesTo(dest: XmlTag) {
            attributes.filter(XmlAttribute::isNamespaceDeclaration).forEach {
                dest.setAttribute(it.name, it.value)
            }
        }
    }

    private companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private val layerInsets = arrayOf("left", "top", "right", "bottom")
        private val insetInsets = arrayOf("insetLeft", "insetTop", "insetRight", "insetBottom")
        private val necessaryShapeTags = arrayOf("gradient", "size", "solid", "stroke")
        private val vectorTransformations = arrayOf("rotation", "scaleX", "scaleY", "translateX", "translateY")
        private val vectorTransformDefaults = floatArrayOf(0f, 1f, 1f, 0f, 0f)
    }
}