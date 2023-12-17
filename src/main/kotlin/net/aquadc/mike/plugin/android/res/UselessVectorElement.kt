package net.aquadc.mike.plugin.android.res

import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.configurations.ConfigurationManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlChildRole
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.util.SmartList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import net.aquadc.mike.plugin.NamedLocalQuickFix
import net.aquadc.mike.plugin.android.resourceResolver
import net.aquadc.mike.plugin.fixes
import org.jetbrains.backported.RemoveElementQuickFix
import org.jetbrains.kotlin.idea.base.util.module
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import kotlin.math.max

private val vectorTransforms = arrayOf("scaleX", "scaleY", "rotation", "translateX", "translateY")
private val vectorTransformDefaults = floatArrayOf(1f, 1f, 0f, 0f, 0f)

private val removeGroupFix = RemoveElementQuickFix("Remove group")
private val removeParentGroupFix = RemoveElementQuickFix("Remove group", PsiElement::getParent)
internal val removeAttrFix = RemoveElementQuickFix("Remove attribute")
internal val removeTagFix = RemoveElementQuickFix("Remove tag")
private val inlineGroupFix = object : NamedLocalQuickFix("Inline contents") {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val group = descriptor.psiElement as XmlTag
        val children = group.subTags
        group.parent.addRangeAfter(children.first(), children.last(), group)
        group.delete()
    }
}

internal fun ProblemsHolder.checkVector(tag: XmlTag) { // TODO check for broken isStateful
    val rr = tag.module?.let {
        ConfigurationManager.getOrCreateInstance(it).resourceResolver(file.virtualFile)
    }

    val maxIntrinsicWidthPx = rr.resolve(tag, "width", ANDROID_NS).toPixels()
    val maxIntrinsicHeightPx = rr.resolve(tag, "height", ANDROID_NS).toPixels()
    val vWidth = rr.resolve(tag, "viewportWidth", ANDROID_NS)?.toFloatOrNull() ?: Float.NaN
    val vHeight = rr.resolve(tag, "viewportHeight", ANDROID_NS)?.toFloatOrNull() ?: Float.NaN

    val xDensity = maxIntrinsicWidthPx / vWidth
    val yDensity = maxIntrinsicHeightPx / vHeight
    var px =
        1 / (if (xDensity.isFinite()) (if (yDensity.isFinite()) max(xDensity, yDensity) else xDensity) else yDensity)
    var usefulPrecision = -1
    if (px > 0) { // and implicit NaN-check
        usefulPrecision = 0
        while (px < 1) {
            usefulPrecision++
            px *= 10
        }
    }


    val viewport = if (vWidth.isNaN() || vHeight.isNaN()) null else Area(Rectangle2D.Float(0f, 0f, vWidth, vHeight))
    val paths = SmartList<PathTag>()
    checkVectorGroup(
        rr, tag, null, viewport, usefulPrecision,
        paths, SmartList(), SmartList(), IntOpenHashSet(),
        HashMap(),
    )

    for (i in paths.indices) {
        paths[i].report(this)
    }
}

private const val MAX_DP = 4f // xxxhdpi
private const val MAX_SCALE = 1.5f // https://android.googlesource.com/platform/frameworks/base/+/fcad09a/packages/SettingsLib/src/com/android/settingslib/display/DisplayDensityUtils.java#47
private const val MAX_TEXT_SCALE = 1.35f // https://github.com/aosp-mirror/platform_packages_apps_settings/blob/c5a500bf07f33e02ff3d315d0ceddf9c2d31d000/res/values/arrays.xml#L156-L161
private const val MAX_DPI = 640f // https://developer.android.com/training/multiscreen/screendensities
private const val PT = 1 / 72f
private const val MM = 1 / 25.4f
private fun String?.toPixels() = when {
    this == null -> Float.NaN
    endsWith("dp") -> scaled(2, MAX_DP * MAX_SCALE)
    endsWith("dip") -> scaled(3, MAX_DP * MAX_SCALE)
    endsWith("sp") -> scaled(2, MAX_DP * MAX_SCALE * MAX_TEXT_SCALE)
    endsWith("pt") -> scaled(2, MAX_DPI * MAX_SCALE * PT)
    endsWith("in") -> scaled(2, MAX_DPI * MAX_SCALE)
    endsWith("mm") -> scaled(2, MAX_DPI * MAX_SCALE * MM)
    endsWith("px") -> substring(0, 2).toFloatOrNull() ?: Float.NaN
    else -> Float.NaN
}
private fun String.scaled(skip: Int, factor: Float) =
    (substring(0, length - skip).toFloatOrNull() ?: Float.NaN) * factor

private fun ProblemsHolder.checkVectorGroup(
    rr: ResourceResolver?,
    tag: XmlTag,
    parentMatrix: AffineTransform?, parentClip: Area?,
    usefulPrecision: Int,
    paths: SmartList<PathTag>,
    clipTags: SmartList<XmlTag>, clips: SmartList<Area>, usefulClips: IntOpenHashSet,
    colorToArea: HashMap<String, Area>,
) {
    val isRoot = tag.name == "vector"

    val transformations =
        if (isRoot) null
        else FloatArray(vectorTransforms.size) { getFloat(rr, tag, vectorTransforms[it], vectorTransformDefaults[it]) }
            .takeIf { !it.contentEquals(vectorTransformDefaults) }

    val matrix = transformations?.let {
        localMatrix(it, getFloat(rr, tag, "pivotX", 0f), getFloat(rr, tag, "pivotY", 0f), parentMatrix)
    } ?: parentMatrix

    val myClipsFrom = clipTags.size
    val myPathsFrom = paths.size
    var localClip: Area? = parentClip

    val subTags = tag.subTags
    subTags.forEachIndexed { index, subTag ->
        when (subTag.name) {
            "clip-path" ->
                checkClip(subTag, isRoot, rr, matrix, usefulPrecision, localClip)?.let { clip ->
                    clipTags.add(subTag)
                    clips.add(clip)
                    localClip = clip
                }
            "group" ->
                checkVectorGroup(
                    rr, subTag, matrix, localClip, usefulPrecision, paths, clipTags, clips, usefulClips, colorToArea,
                )
            "path" -> { // TODO propose merging pathDatas of sibling paths with same attrs
                subTag.getAttribute("pathData", ANDROID_NS)?.valueElement?.let { pathData ->
                    PathTag.parse(this, rr, pathData, matrix, usefulPrecision)?.also { pathTag ->
                        pathTag.toAreas(this, usefulPrecision)

                        // Propose transferring clip-path's pathData to the only clipped path.
                        // Do this before applying clip which mutates geometry.
                        if (index > 0 && index == subTags.lastIndex && localClip != null &&
                            subTags[index - 1].name == "clip-path" && pathTag.isBoundedBy(localClip!!)) {
                            report(
                                subTag, "pathData of the preceding clip-path can be transferred here",
                                hl = ProblemHighlightType.WEAK_WARNING, finder = XmlChildRole.ATTRIBUTE_NAME_FINDER,
                                fix = object : NamedLocalQuickFix("Transfer preceding clip-path's pathData here") {
                                    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                                        val path = descriptor.psiElement as? XmlTag ?: return
                                        var clipPath = path.prevSibling ?: return
                                        while (clipPath is XmlText)
                                            clipPath = clipPath.prevSibling ?: return

                                        path.setAttribute(
                                            "pathData", ANDROID_NS,
                                            (clipPath as? XmlTag)?.getAttributeValue("pathData", ANDROID_NS) ?: return,
                                        )
                                        clipPath.delete()
                                    }
                                }
                            )
                        }

                        pathTag.applyClip(localClip, clips, usefulClips, usefulPrecision)
                        pathTag.overdraw(paths, usefulPrecision, colorToArea)
                        paths.add(pathTag)
                    }
                } ?: run {
                    // let's conservatively think that an invalid path marks all clips as useful
                    if (usefulClips.size < clips.size)
                        clips.indices.forEach(usefulClips::add)
                }
            }
        }
    }

    val hasPaths = myPathsFrom != paths.size
    if (!hasPaths) {
        report(tag, if (isRoot) "Empty vector" else "Empty group", if (isRoot) null else removeGroupFix)
    }

    val hasClips = clips.size > myClipsFrom
    if (hasClips) {
        check(clipTags.size == clips.size)
        for (i in myClipsFrom until clips.size)
            if (!usefulClips.remove(i) && hasPaths) // remove all my clips from set reporting useless ones
                report(clipTags[i], "The path doesn't clip any of its successors", removeTagFix)
        clips.subList(myClipsFrom, clips.size).clear()
        clipTags.subList(myClipsFrom, clipTags.size).clear()
    }

    if (hasPaths && !isRoot && transformations == null && tag.getAttributeValue("name", ANDROID_NS) == null) {
        val noSuccessors = tag.parentTag?.subTags?.let { it.indexOf(tag) == it.lastIndex } ?: false
        if (!hasClips && noSuccessors)
            report(tag, "Useless group: no name, no transformations, no useful clip-paths, no successors", inlineGroupFix)
        else if (!hasClips)
            report(tag, "Useless group: no name, no transformations, no useful clip-paths", inlineGroupFix)
        else if (noSuccessors)
            report(tag, "Useless group: no name, no transformations, no successors", inlineGroupFix)
        // TODO also report single-group groups and propose merging
    }
}

private fun localMatrix(transforms: FloatArray, px: Float, py: Float, outerMatrix: AffineTransform?): AffineTransform {
// borrowed from com.android.ide.common.vectordrawable.VdGroup.updateLocalMatrix
    val tempTrans = AffineTransform()
    val localMatrix = AffineTransform()
    tempTrans.translate(-px.toDouble(), -py.toDouble())
    localMatrix.preConcatenate(tempTrans)
    tempTrans.setToIdentity()
    val (sx, sy, rot, tx, ty) = transforms
    tempTrans.scale(sx.toDouble(), sy.toDouble())
    localMatrix.preConcatenate(tempTrans)
    tempTrans.setToIdentity()
    tempTrans.rotate(Math.toRadians(rot.toDouble()), 0.0, 0.0)
    localMatrix.preConcatenate(tempTrans)
    tempTrans.setToIdentity()
    tempTrans.translate((tx + px).toDouble(), (ty + py).toDouble())
    localMatrix.preConcatenate(tempTrans)

    return if (outerMatrix != null) {
        tempTrans.setTransform(outerMatrix)
        tempTrans.concatenate(localMatrix)
        tempTrans
    } else {
        localMatrix
    }
}

private fun ProblemsHolder.getFloat(rr: ResourceResolver?, tag: XmlTag, name: String, default: Float): Float =
    toFloat(rr, tag.getAttribute(name, ANDROID_NS), default)

internal fun ProblemsHolder.toFloat(rr: ResourceResolver?, attr: XmlAttribute?, default: Float): Float {
    val value = attr?.let(rr::resolve)?.toFloatOrNull()
    if (value == default) report(attr, "Attribute has default value", removeAttrFix)
    return value ?: default
}
internal fun ProblemsHolder.toString(rr: ResourceResolver?, attr: XmlAttribute?, default: String?): String? {
    val value = attr?.let(rr::resolve)
    if (default != null && value == default) report(attr, "Attribute has default value", removeAttrFix)
    return value ?: default
}

private fun ProblemsHolder.checkClip(
    clipTag: XmlTag,
    isRoot: Boolean,
    rr: ResourceResolver?,
    matrix: AffineTransform?,
    usefulPrecision: Int,
    parentClip: Area?
): Area? {
    val parent = if (isRoot) "vector" else "group"
    val pathData = clipTag.getAttribute("pathData", ANDROID_NS)?.valueElement
    val removeParentGroupFix = if (isRoot) null else removeParentGroupFix
    return if (pathData == null) {
        registerProblem(
            clipTag,
            "The clip-path without pathData makes the whole $parent invisible",
            *fixes(removeParentGroupFix, removeTagFix),
        )
        null
    } else {
        val clipArea = PathTag.parse(this, rr, pathData, matrix, usefulPrecision)?.merged()
        if (clipArea == null) {
            null // bad path, ignore
        } else if (clipArea.effectivelyEmpty(usefulPrecision)) {
            registerProblem(
                clipTag, "The clip-path is empty making the whole $parent invisible",
                *fixes(removeParentGroupFix, removeTagFix),
            )
            null
        } else if (parentClip == null) {
            clipArea // proceed with less precision
        } else if (clipArea.also { it.intersect(parentClip) }.effectivelyEmpty(usefulPrecision)) {
            registerProblem(
                clipTag,
                "The clip-path has empty intersection with inherited clip-path or viewport making the whole $parent invisible",
                *fixes(removeParentGroupFix, removeTagFix),
            )
            null
        } else if (Area(parentClip).also { it.subtract(clipArea) }.effectivelyEmpty(usefulPrecision)) {
            report(clipTag, "The clip-path clips nothing", removeTagFix)
            null
        } else {
            clipArea
        }
    }
}
