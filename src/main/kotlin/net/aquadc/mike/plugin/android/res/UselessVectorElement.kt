package net.aquadc.mike.plugin.android.res

import android.graphics.PathDelegate
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import de.javagl.geom.Shapes.computeSignedArea
import de.javagl.geom.Shapes.computeSubShapes
import gnu.trove.TIntArrayList
import gnu.trove.TIntHashSet
import net.aquadc.mike.plugin.NamedLocalQuickFix
import org.jetbrains.plugins.groovy.codeInspection.fixes.RemoveElementQuickFix
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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

internal fun ProblemsHolder.checkVector(tag: XmlTag) {
    val rr =
        tag.androidFacet?.let { ConfigurationManager.getOrCreateInstance(it).getConfiguration(file.virtualFile).resourceResolver }

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
    val pathTags = SmartList<XmlTag>()
    val paths = SmartList<Area>()
    checkVectorGroup(
        rr, tag, null, viewport, usefulPrecision,
        pathTags, paths, SmartList(), SmartList(), TIntHashSet(),
    )

    // TODO also check when the path adds nothing to the image
    check(pathTags.size == paths.size) { pathTags + " | " + paths }
    paths.forEachIndexed { i, path ->
        if (path?.effectivelyEmpty(usefulPrecision) == true) {
            report(pathTags[i], "The path is fully overdrawn", removeTagFix)
        }
    }
    paths.clear()
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
    pathTags: SmartList<XmlTag>, paths: SmartList<Area>,
    clipTags: SmartList<XmlTag>, clips: SmartList<Area>, usefulClips: TIntHashSet,
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
    val myPathsFrom = pathTags.size
    var localClip: Area? = parentClip

    tag.subTags.forEach { subTag ->
        when (subTag.name) {
            "clip-path" ->
                checkClip(subTag, isRoot, rr, matrix, usefulPrecision, localClip)?.let { clip ->
                    clipTags.add(subTag)
                    clips.add(clip)
                    localClip = clip
                }
            "group" ->
                checkVectorGroup(
                    rr, subTag, matrix, localClip, usefulPrecision, pathTags, paths, clipTags, clips, usefulClips,
                )
            "path" -> { // TODO propose merging pathDatas of sibling paths with same attrs
                subTag.getAttribute("pathData", ANDROID_NS)?.valueElement?.let { pathData ->
                    parse(rr, pathData, matrix, usefulPrecision)
                        ?.let { outline -> toArea(rr, subTag, outline) }
                        ?.takeIf { (area, _) -> checkPathArea(subTag, area, localClip, clips, usefulClips, usefulPrecision) }
                        ?.let { (area, opaqueArea) ->
                            paths.takeIf { opaqueArea != null && it.isNotEmpty() }?.forEach { it.subtract(opaqueArea) }
                            pathTags.add(subTag)
                            paths.add(area)
                        }
                } ?: run {
                    // let's conservatively think that an invalid path marks all clips as useful
                    if (usefulClips.size() < clips.size)
                        clips.indices.forEach(usefulClips::add)
                }
            }
        }
    }

    if (myPathsFrom == pathTags.size) {
        report(tag, if (isRoot) "Empty vector" else "Empty group", if (isRoot) null else removeGroupFix)
    } else {
        val hasClips = clips.size > myClipsFrom
        if (hasClips) {
            check(clipTags.size == clips.size)
            for (i in myClipsFrom until clips.size)
                if (!usefulClips.remove(i))
                    report(clipTags[i], "The path doesn't clip any of its siblings", removeTagFix)
            clips.subList(myClipsFrom, clips.size).clear()
            clipTags.subList(myClipsFrom, clipTags.size).clear()
        }

        if (!isRoot && transformations == null && tag.getAttributeValue("name", ANDROID_NS) == null) {
            val noSiblings = tag.parentTag?.subTags?.size == 1
            if (!hasClips && noSiblings)
                report(tag, "Useless group: no name, no transformations, no useful clip-paths, no siblings", inlineGroupFix)
            else if (!hasClips)
                report(tag, "Useless group: no name, no transformations, no useful clip-paths", inlineGroupFix)
            else if (noSiblings)
                report(tag, "Useless group: no name, no transformations, no siblings", inlineGroupFix)
            // TODO also report single-group groups and propose merging
        }

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
internal fun ProblemsHolder.toString(rr: ResourceResolver?, attr: XmlAttribute?, default: String): String {
    val value = attr?.let(rr::resolve)
    if (value == default) report(attr, "Attribute has default value", removeAttrFix)
    return value ?: default
}

private fun List<Area>.subIntersection(from: Int): Area {
    var i = from
    val area = Area(this[i++])
    while (i < size) area.intersect(this[i++])
    return area
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
            removeParentGroupFix, removeTagFix,
        )
        null
    } else {
        val clipArea = parse(rr, pathData, matrix, usefulPrecision)?.let(::Area)
        if (clipArea == null) {
            null // bad path, ignore
        } else if (clipArea.effectivelyEmpty(usefulPrecision)) {
            registerProblem(
                clipTag,
                "The clip-path is empty making the whole $parent invisible",
                removeParentGroupFix, removeTagFix,
            )
            null
        } else if (parentClip == null) {
            clipArea // proceed with less precision
        } else if (clipArea.also { it.intersect(parentClip) }.effectivelyEmpty(usefulPrecision)) {
            registerProblem(
                clipTag,
                "The clip-path has empty intersection with inherited clip-path or viewport making the whole $parent invisible",
                removeParentGroupFix, removeTagFix,
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

private fun ProblemsHolder.parse(rr: ResourceResolver?, pathAttr: XmlAttributeValue, matrix: AffineTransform?, usefulPrecision: Int): Path2D? {
    val outRanges = TIntArrayList()
    val rawPathData = pathAttr.value
    val pathData = rr.resolve(rawPathData) ?: return null
    val path = PathDelegate.parse(pathData, outRanges, usefulPrecision)
    val rangeNodeCount = outRanges.size()
    val rangeCount = rangeNodeCount / 2
    if (rangeCount > 0) {
        var canTrimCarefully = false
        for (i in 0 until rangeNodeCount step 2) {
            if (outRanges[i + 1] - pathData.indexOf('.', outRanges[i]) > usefulPrecision + 2) { // plus dot plus extra digit
                canTrimCarefully = true
                break
            }
        }

        if (canTrimCarefully || isOnTheFly) {
            val beginValueAt = pathAttr.text.indexOf(rawPathData)
            registerProblem(
                pathAttr,
                "subpixel precision" + if (rangeCount == 1) "" else " ($rangeCount items)",
                ProblemHighlightType.WEAK_WARNING,
                if (pathData === rawPathData)
                    TextRange(beginValueAt + outRanges[0], beginValueAt + outRanges[rangeNodeCount - 1])
                else TextRange.from(beginValueAt, rawPathData.length),

                if (isOnTheFly) TrimFix(outRanges, "Trim aggressively", usefulPrecision, pathData) else null,
                if (canTrimCarefully) TrimFix(outRanges, "Trim carefully", usefulPrecision + 1, pathData) else null,
            )
        }
    } // TODO check for useless operations

    if (matrix != null) path?.transform(matrix)
    return path
}

private class TrimFix(
    private val ranges: TIntArrayList,
    name: String,
    private val targetPrecision: Int,
    private val pathData: String,
) : NamedLocalQuickFix(name) {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val value = StringBuilder(pathData)
        val tmpFloat = StringBuilder(10)
        for (i in (ranges.size()-2) downTo 0 step 2) {
            val start = ranges[i]
            val end = ranges[i + 1]
            val minus = tmpFloat.replace(0, tmpFloat.length, value, start, end)[0] == '-'
            if (tmpFloat.trimToPrecision()[0] != '-' && minus && value[start - 1].isDigit())
                tmpFloat.insert(0, ' ')
            value.replace(start, end, tmpFloat)
        }
        (descriptor.psiElement.parent as XmlAttribute).setValue(value.toString())
    }

    private fun StringBuilder.trimToPrecision(): StringBuilder {
        var carry = false
        run { // lower precision
            val precision = length - 1 - indexOf('.')
            if (precision > targetPrecision) {
                val trim = precision - targetPrecision
                val iol = lastIndex
                repeat(trim) { i ->
                    carry = this[iol - i] - '0' + carry.asInt > 4
                }
                delete(length - trim, length)
            }
        }
        run<Unit> { // clean up and carry
            var fractional = true
            while (isNotEmpty()) {
                val iol = lastIndex
                val ch = this[iol]
                if (ch == '.') {
                    fractional = false
                } else if (fractional && (carry && ch == '9' || !carry && ch == '0')) {
                    // carry over to the previous digit
                } else if (fractional && carry && ch in '0'..'8') {
                    this[iol]++
                    carry = false
                    break
                } else break
                deleteCharAt(iol)
            }

            var iol = lastIndex
            if (!fractional) { // carry whole part
                while (iol >= 0 && carry) {
                    val ch = this[iol]
                    if (ch == '9') {
                        this[iol--] = '0'
                    } else if (ch in '0'..'8') {
                        this[iol] = ch + 1
                        carry = false
                    } else {
                        check(ch == '-')
                        break
                    }
                }
            }

            if (carry)
                insert(max(iol, 0), '1')
            else if (isEmpty() || (length == 1 && this[0] == '-')) {
                append('0')
            }
        }

        if (length == 2 && this[0] == '-' && this[1] == '0')
            deleteCharAt(0)

        return this
    }

    private fun StringBuilder.replace(
        at: Int, till: Int,
        with: CharSequence, from: Int = 0, to: Int = with.length,
    ): StringBuilder {
        val victimLen = till - at
        val replLen = to - from
        repeat(min(victimLen, replLen)) { this[at + it] = with[from + it] }
        return if (replLen > victimLen)
            insert(at + victimLen, with, from + victimLen, from + replLen)
        else
            delete(at + replLen, at + victimLen)
    }

    private inline val Boolean.asInt get() = if (this) 1 else 0
}

private fun ProblemsHolder.checkPathArea(
    tag: XmlTag, path: Area, clipPath: Area?, clips: List<Area>,
    usefulClips: TIntHashSet, usefulPrecision: Int,
): Boolean {
    if (usefulClips.size() < clips.size) {
        val reduced = Area()
        var index = 0
        while (index < clips.size) {
            val clip = clips[index]
            if (index !in usefulClips) {
                reduced.add(path)
                reduced.subtract(clip)
                // now `reduced` is a clipped-away part of path
                if (!reduced.effectivelyEmpty(usefulPrecision) &&
                    usefulClips.add(index) &&
                    usefulClips.size() == clips.size)
                        break
                reduced.reset()
            }
            index++
        }
    }

    if (path.effectivelyEmpty(usefulPrecision))
        report(tag, "The path is effectively empty", removeTagFix)
    else if (clipPath != null && path.also { it.intersect(clipPath) }.effectivelyEmpty(usefulPrecision))
        report(tag, "The path is clipped away or outside of viewport", removeTagFix)
    else
        return true

    return false
}

private fun Area.effectivelyEmpty(usefulPrecision: Int): Boolean {
    if (isEmpty) return true
    else if (usefulPrecision < 0) return false

    val px = 1 / 10f.pow(usefulPrecision)
    val minArea = 3 * px * px // at least 3 square pixels on xxxxxxxhhhhdpi, maybe?
    return bounds2D.let { it.width < px || it.height < px || it.width * it.height < minArea } ||
            computeSubShapes(this).all { computeSignedArea(it, 0.0).absoluteValue < minArea }
}
