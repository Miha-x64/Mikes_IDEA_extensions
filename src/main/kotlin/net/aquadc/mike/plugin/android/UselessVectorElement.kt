package net.aquadc.mike.plugin.android

import android.graphics.PathDelegate
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
import net.aquadc.mike.plugin.miserlyFilter as filter

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
    val maxIntrinsicWidthPx = tag.getAttributeValue("width", ANDROID_NS).toPixels()
    val maxIntrinsicHeightPx = tag.getAttributeValue("height", ANDROID_NS).toPixels()
    val vWidth = tag.getAttributeValue("viewportWidth", ANDROID_NS)?.toFloatOrNull() ?: Float.NaN
    val vHeight = tag.getAttributeValue("viewportHeight", ANDROID_NS)?.toFloatOrNull() ?: Float.NaN

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
        tag, null, viewport, null, usefulPrecision,
        pathTags, paths, SmartList(), SmartList(), TIntHashSet(),
    )

    // TODO also check the opposite: when the path adds nothing to the image
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
private fun String.scaled(skip: Int, factor: Float) = (substring(0, skip).toFloatOrNull() ?: Float.NaN) * factor

private fun ProblemsHolder.checkVectorGroup(
    tag: XmlTag,
    parentMatrix: AffineTransform?,
    viewport: Area?,
    parentClip: Area?,
    usefulPrecision: Int,
    pathTags: SmartList<XmlTag>,
    paths: SmartList<Area>,
    clipTags: SmartList<XmlTag>,
    clips: SmartList<Area>,
    usefulClips: TIntHashSet,
) {
    val isRoot = tag.name == "vector"
    val myPathsFrom = pathTags.size
    tag.subTags.filterTo(pathTags) { it.name == "path" }
    val groupTags = tag.subTags.filter { it.name == "group" }
    val visualTagsCount = (pathTags.size - myPathsFrom) + groupTags.size
    if (visualTagsCount == 0) return report(
        tag, if (isRoot) "Empty vector" else "Empty group", if (isRoot) null else removeGroupFix
    )

    val transformations =
        if (isRoot) null
        else FloatArray(vectorTransforms.size) { getFloat(tag, vectorTransforms[it], vectorTransformDefaults[it]) }

    val maxClipTagCount = tag.subTags.size - visualTagsCount
    var clipTagCount = -1
    if (transformations?.contentEquals(vectorTransformDefaults) == true &&
        tag.getAttributeValue("name", ANDROID_NS) == null) {
        val noClips =
            maxClipTagCount == 0 || tag.subTags.count { it.name == "clip-path" }.also { clipTagCount = it } == 0
        val noSiblings = tag.parentTag?.subTags?.size == 1
        if (noClips && noSiblings)
            report(tag, "Useless group: no name, no transformations, no clip-paths, no siblings", inlineGroupFix)
        else if (noClips)
            report(tag, "Useless group: no name, no transformations, no clip-paths", inlineGroupFix)
        else if (noSiblings)
            report(tag, "Useless group: no name, no transformations, no siblings", inlineGroupFix)
        // TODO also reports groups with mergeable transforms etc
    }

    val matrix = localMatrix(transformations, getFloat(tag, "pivotX", 0f), getFloat(tag, "pivotY", 0f), parentMatrix)

    val myClipsFrom = clipTags.size
    val commonClip = if (maxClipTagCount > 0 && clipTagCount != 0) {
        gatherClips(tag, matrix, isRoot, parentClip, clipTags, clips, usefulPrecision)
        check(clipTags.size == clips.size)
        when (clipTags.size - myClipsFrom) {
            0 -> null
            1 -> clips[myClipsFrom]
            else -> intersectAndCheckClips(clips, tag, isRoot, clipTags, myClipsFrom, usefulPrecision)
        }
    } else null

    val pathTagsIter = pathTags.listIterator(myPathsFrom)
    while (pathTagsIter.hasNext()) { // TODO propose merging pathDatas of sibling paths with same attrs
        val pathTag = pathTagsIter.next()

        pathTag.getAttribute("pathData", ANDROID_NS)?.valueElement?.let { pathData ->
            parse(pathData, matrix, usefulPrecision)?.let { outline ->
                toArea(pathTag, outline)?.let { (area, opaqueArea) ->
                    checkPath(pathTag, area, viewport, commonClip, clips, usefulClips, usefulPrecision)
                    if (opaqueArea != null && paths.isNotEmpty()) {
                        paths.forEach { it?.subtract(opaqueArea) }
                    }
                    paths.add(area)
                }
            }
        } ?: run {
            // let's conservatively think that an invalid path marks all clips as useful
            if (usefulClips.size() < clips.size)
                clips.indices.forEach(usefulClips::add)
            pathTagsIter.remove()
        }
    }

    groupTags.forEach {
        checkVectorGroup(
            it, matrix, viewport, commonClip ?: parentClip, usefulPrecision,
            pathTags, paths,
            clipTags, clips, usefulClips,
        )
    }

    if (clips.size > myClipsFrom) {
        for (i in myClipsFrom until clips.size)
            if (!usefulClips.remove(i))
                report(clipTags[i], "The path doesn't clip any of its siblings", removeTagFix)
        clips.subList(myClipsFrom, clips.size).clear()
        clipTags.subList(myClipsFrom, clipTags.size).clear()
    }
}

private fun localMatrix(transforms: FloatArray?, px: Float, py: Float, outerMatrix: AffineTransform?): AffineTransform? {
// borrowed from com.android.ide.common.vectordrawable.VdGroup.updateLocalMatrix
    if (transforms == null) return outerMatrix

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

private fun ProblemsHolder.getFloat(tag: XmlTag, name: String, default: Float): Float =
    toFloat(tag.getAttribute(name, ANDROID_NS), default)

internal fun ProblemsHolder.toFloat(attr: XmlAttribute?, default: Float): Float {
    val value = attr?.value?.toFloatOrNull()
    if (value == default) report(attr, "Attribute has default value", removeAttrFix)
    return value ?: default
}
internal fun ProblemsHolder.toString(attr: XmlAttribute?, default: String): String {
    val value = attr?.value
    if (value == default) report(attr, "Attribute has default value", removeAttrFix)
    return value ?: default
}

private fun ProblemsHolder.intersectAndCheckClips(
    clips: SmartList<Area>,
    tag: XmlTag, isRoot: Boolean,
    clipTags: SmartList<XmlTag>, ourClipsFrom: Int,
    usefulPrecision: Int,
): Area? {
    val intersection = Area()
    clips.intersect(intersection, ourClipsFrom)
    return if (intersection.effectivelyEmpty(usefulPrecision)) {
        registerProblem(
            tag,
            "Intersection of clip-paths is empty, thus the whole tag is invisible",
            if (isRoot) null else removeGroupFix
        )
        null // ignore all clips, we've already reported on them
    } else {
        val victim = Area()
        var i = ourClipsFrom
        var met = false
        while (i < clips.size) {
            victim.add(intersection)
            victim.subtract(clips[i])
            if (victim.effectivelyEmpty(usefulPrecision)) {
                report(clipTags[i], "The clip-path is superseded by its siblings", removeTagFix)
                clipTags.removeAt(i)
                clips.removeAt(i) // never analyze this path further
                met = true
            } else i++
        }
        if (met) check(Area().also { clips.intersect(it, ourClipsFrom) }.equals(intersection))
        intersection
    }
}

private fun List<Area>.intersect(into: Area, from: Int) {
    var first = true
    for (i in from until size)
        if (first) into.add(this[i]).also { first = false }
        else into.intersect(this[i])
}

private fun ProblemsHolder.gatherClips(
    tag: XmlTag, matrix: AffineTransform?, isRoot: Boolean, parentClip: Area?,
    tagsTo: SmartList<XmlTag>, clipsTo: SmartList<Area>, usefulPrecision: Int
) {
    val tagsFrom = tagsTo.size
    val clipTags = tag.subTags.filterTo(tagsTo) { it.name == "clip-path" }
    val clipTagsIter = clipTags.listIterator(tagsFrom)
    while (clipTagsIter.hasNext()) {
        val nextTag = clipTagsIter.next()
        val pathData = nextTag.getAttribute("pathData", ANDROID_NS)?.valueElement
        if (pathData == null) {
            registerProblem(
                nextTag,
                "The clip-path without pathData makes this tag invisible",
                if (isRoot) null else removeParentGroupFix
            )
            clipTagsIter.remove()
        } else {
            val clipArea = parse(pathData, matrix, usefulPrecision)?.let(::Area)
            if (clipArea == null) {
                clipTagsIter.remove() // bad path, ignore
            } else if (clipArea.effectivelyEmpty(usefulPrecision)) {
                registerProblem(
                    nextTag,
                    "The clip-path is empty, thus the whole tag is invisible",
                    if (isRoot) null else removeParentGroupFix
                )
                clipTagsIter.remove()
            } else if (parentClip == null) {
                clipsTo.add(clipArea) // proceed with less precision
            } else if (clipArea.also { it.intersect(parentClip) }.effectivelyEmpty(usefulPrecision)) {
                registerProblem(
                    nextTag,
                    "The clip-path has empty intersection with inherited clip-path or viewport. Thus it is useless, and the whole tag is invisible",
                    if (isRoot) null else removeParentGroupFix,
                    removeTagFix
                )
                clipTagsIter.remove()
            } else if (Area(parentClip).also { it.subtract(clipArea) }.effectivelyEmpty(usefulPrecision)) {
                registerProblem(nextTag, "The clip-path is superseded by inherited clip-path or viewport", removeTagFix)
                clipTagsIter.remove()
            } else {
                clipsTo.add(clipArea)
            }
        }
    }
}

private fun ProblemsHolder.parse(pathAttr: XmlAttributeValue, matrix: AffineTransform?, usefulPrecision: Int): Path2D? {
    val outRanges = TIntArrayList()
    val pathData = pathAttr.value
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
            val beginValueAt = pathAttr.text.indexOf(pathData)
            registerProblem(
                pathAttr,
                "subpixel precision" + if (rangeCount == 1) "" else " ($rangeCount items)",
                if (canTrimCarefully) ProblemHighlightType.WEAK_WARNING else ProblemHighlightType.INFORMATION,
                TextRange.from(beginValueAt + outRanges[0], beginValueAt + outRanges[rangeNodeCount - 1] - outRanges[0] - 1),

                // “Trim heavily” was “aggressively” before but IDEA sorts options alphabetically.
                if (canTrimCarefully) TrimFix(outRanges, "Trim carefully", usefulPrecision + 1) else null,
                TrimFix(outRanges, "Trim heavily (may decrease accuracy)", usefulPrecision),
            )
        }
    } // TODO check for useless operations

    if (matrix != null) path.transform(matrix)
    return path
}

private class TrimFix(
    private val ranges: TIntArrayList,
    name: String,
    private val targetPrecision: Int,
) : NamedLocalQuickFix(name) {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val value = StringBuilder((descriptor.psiElement as XmlAttributeValue).value)
        val tmpFloat = StringBuilder(10)
        for (i in (ranges.size()-2) downTo 0 step 2) {
            val start = ranges[i]
            val end = ranges[i + 1]
            value.replace(start, end, tmpFloat.append(value, start, end).trimToPrecision())
            tmpFloat.clear()
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

    private fun StringBuilder.replace(from: Int, to: Int, with: CharSequence) {
        val victimLen = to - from
        val replLen = with.length
        repeat(min(victimLen, replLen)) { this[from + it] = with[it] }
        if (replLen > victimLen) insert(from + victimLen, with, victimLen, replLen)
        else delete(from + replLen, from + victimLen)
    }

    private inline val Boolean.asInt get() = if (this) 1 else 0
}

private fun ProblemsHolder.checkPath(
    tag: XmlTag, path: Area, viewport: Area?, clipPath: Area?, clips: List<Area>,
    usefulClips: TIntHashSet, usefulPrecision: Int,
) {
    if (viewport != null && path.also { it.intersect(viewport) }.effectivelyEmpty(usefulPrecision))
        return report(tag, "The path is outside of viewport", removeTagFix)

    if (usefulClips.size() < clips.size) {
        val reduced = Area()
        clips.forEachIndexed { index, clip ->
            if (index !in usefulClips) {
                reduced.add(path)
                reduced.subtract(clip)
                // now `reduced` is a clipped-away part of path
                if (!reduced.effectivelyEmpty(usefulPrecision))
                    usefulClips.add(index)
                reduced.reset()
            }
        }
    }

    if (clipPath != null && path.also { it.intersect(clipPath) }.effectivelyEmpty(usefulPrecision))
        return report(tag, "The path is clipped away", removeTagFix)
}

private fun Area.effectivelyEmpty(usefulPrecision: Int): Boolean =
    isEmpty || (usefulPrecision >= 0 && effectiveAreaEmpty(usefulPrecision))

private fun Area.effectiveAreaEmpty(usefulPrecision: Int): Boolean {
    var sqPx = 1 / 10f.pow(usefulPrecision)
    sqPx *= sqPx
    return bounds2D.let { it.width * it.height } < sqPx ||
            computeSubShapes(this).all { computeSignedArea(it, 0.0).absoluteValue < sqPx }
}
