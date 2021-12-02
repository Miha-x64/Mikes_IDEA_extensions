package net.aquadc.mike.plugin.android

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import gnu.trove.TIntHashSet
import net.aquadc.mike.plugin.NamedLocalQuickFix
import org.jetbrains.plugins.groovy.codeInspection.fixes.RemoveElementQuickFix
import java.awt.BasicStroke
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import net.aquadc.mike.plugin.miserlyFilter as filter
import net.aquadc.mike.plugin.miserlyMap as map

private val vectorTransforms = arrayOf("scaleX", "scaleY", "rotation", "translateX", "translateY")
private val vectorTransformDefaults = floatArrayOf(1f, 1f, 0f, 0f, 0f)

private val removeGroupFix = RemoveElementQuickFix("Remove group")
private val removeParentGroupFix = RemoveElementQuickFix("Remove group", PsiElement::getParent)
private val removeAttrFix = RemoveElementQuickFix("Remove attribute")
private val removeTagFix = RemoveElementQuickFix("Remove tag")

internal fun ProblemsHolder.checkVector(tag: XmlTag) {
    val width = tag.getAttributeValue("viewportWidth", ANDROID_NS)?.toFloatOrNull()
    val height = tag.getAttributeValue("viewportHeight", ANDROID_NS)?.toFloatOrNull()
    val viewport =
        if (width == null || height == null) null
        else Area(Rectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat()))
    checkVectorGroup(tag, null, viewport, SmartList(), SmartList(), TIntHashSet())
}
private fun ProblemsHolder.checkVectorGroup(
    tag: XmlTag,
    parentMatrix: AffineTransform?,
    parentClip: Area?,
    clipTags: SmartList<XmlTag>,
    clips: SmartList<Area>,
    usefulClips: TIntHashSet
) {
    val isRoot = tag.name == "vector"
    val pathTags = tag.subTags.filter { it.name == "path" }
    val groupTags = tag.subTags.filter { it.name == "group" }
    val visualTagsCount = pathTags.size + groupTags.size
    if (visualTagsCount == 0) return report(
        tag, if (isRoot) "Empty vector" else "Empty group", if (isRoot) null else removeGroupFix
    )

    val transformations =
        if (isRoot) null
        else FloatArray(vectorTransforms.size) { tag.getFloat(vectorTransforms[it], vectorTransformDefaults[it]) }

    val maxClipTagCount = tag.subTags.size - visualTagsCount
    var clipTagCount = -1
    if (transformations?.contentEquals(vectorTransformDefaults) == true &&
        tag.getAttributeValue("name", ANDROID_NS) == null &&
        (maxClipTagCount == 0 || tag.subTags.count { it.name == "clip-path" }.also { clipTagCount = it } == 0)
    ) report(
        tag, "Useless group: no name, no transformations, no clip-paths",
        object : NamedLocalQuickFix("Inline contents") {
            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                val group = descriptor.psiElement as XmlTag
                val children = group.subTags
                group.parent.addRangeAfter(children.first(), children.last(), group)
                group.delete()
            }
        })

    val matrix = localMatrix(transformations, tag.getFloat("pivotX", 0f), tag.getFloat("pivotY", 0f), parentMatrix)

    val myClipsFrom = clipTags.size
    val commonClip = if (maxClipTagCount > 0 && clipTagCount != 0) {
        gatherClips(tag, matrix, isRoot, parentClip, clipTags, clips)
        check(clipTags.size == clips.size)
        when (clipTags.size - myClipsFrom) {
            0 -> null
            1 -> clips[myClipsFrom]
            else -> intersectAndCheckClips(clips, tag, isRoot, clipTags, myClipsFrom)
        }
    } else null

    pathTags.forEach { pathTag ->
        pathTag.getAttributeValue("pathData", ANDROID_NS)?.let { pathData ->
            parse(pathData, matrix)?.let { outline ->
                toArea(pathTag, outline)?.let { area ->
                    checkPath(pathTag, area, parentClip, commonClip, clips, usefulClips)
                }
            } // let's conservatively think that an invalid path marks all clips as useful
                ?: if (usefulClips.size() < clips.size) clips.indices.forEach(usefulClips::add)
        }
    }

    groupTags.forEach {
        checkVectorGroup(it, matrix, commonClip ?: parentClip, clipTags, clips, usefulClips)
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

private fun XmlTag.getFloat(name: String, default: Float): Float =
    getAttributeValue(name, ANDROID_NS)?.toFloatOrNull() ?: default

private fun ProblemsHolder.intersectAndCheckClips(
    clips: SmartList<Area>, tag: XmlTag, isRoot: Boolean, clipTags: SmartList<XmlTag>, ourClipsFrom: Int
): Area? {
    val intersection = Area()
    clips.intersect(intersection, ourClipsFrom)
    return if (intersection.isEmpty) {
        registerProblem(
            tag,
            "intersection of clip-paths is empty, thus the whole tag is invisible",
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
            if (victim.isEmpty) {
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
    tag: XmlTag, matrix: AffineTransform?, isRoot: Boolean, viewport: Area?,
    tagsTo: SmartList<XmlTag>, clipsTo: SmartList<Area>
) {
    val tagsFrom = tagsTo.size
    val clipTags = tag.subTags.filterTo(tagsTo) { it.name == "clip-path" }
    val clipTagsIter = clipTags.listIterator(tagsFrom)
    while (clipTagsIter.hasNext()) {
        val nextTag = clipTagsIter.next()
        val pathData = nextTag.getAttributeValue("pathData", ANDROID_NS)
        if (pathData == null) {
            registerProblem(
                nextTag,
                "The clip-path without pathData makes this tag invisible",
                if (isRoot) null else removeParentGroupFix
            )
            clipTagsIter.remove()
        } else {
            val clipArea = parse(pathData, matrix)?.let(::Area)
            if (clipArea == null) {
                clipTagsIter.remove() // bad path, ignore
            } else if (clipArea.isEmpty) {
                registerProblem(
                    nextTag,
                    "The clip-path is empty, thus the whole tag is invisible",
                    if (isRoot) null else removeParentGroupFix
                )
                clipTagsIter.remove()
            } else if (viewport == null) {
                clipsTo.add(clipArea) // proceed with less precision
            } else if (clipArea.also { it.intersect(viewport) }.isEmpty) {
                registerProblem(
                    nextTag,
                    "The clip-path has empty intersection with inherited clip-path or viewport. Thus it is useless, and the whole tag is invisible",
                    if (isRoot) null else removeParentGroupFix,
                    removeTagFix
                )
                clipTagsIter.remove()
            } else if (Area(viewport).also { it.subtract(clipArea) }.isEmpty) {
                registerProblem(nextTag, "The clip-path is superseded by inherited clip-path or viewport", removeTagFix)
                clipTagsIter.remove()
            } else {
                clipsTo.add(clipArea)
            }
        }
    }
}

private fun parse(pathData: String, matrix: AffineTransform?) = try {
    val path = PathDelegate.parse(pathData)
    if (matrix != null) path.transform(matrix)
    path
} catch (e: Exception) {
    e.printStackTrace()
    null
}

private fun ProblemsHolder.checkPath(tag: XmlTag, path: Area, viewport: Area?, clipPath: Area?, clips: List<Area>, usefulClips: TIntHashSet) {
    if (viewport != null && path.also { it.intersect(viewport) }.isEmpty)
        return report(tag, "The path is clipped away by viewport or parent clip-path", removeTagFix)

    if (usefulClips.size() < clips.size) {
        val reduced = Area()
        clips.forEachIndexed { index, clip ->
            if (index !in usefulClips) {
                reduced.add(path)
                reduced.subtract(clip)
                // now `reduced` is a clipped-away part of path
                if (!reduced.isEmpty) {
                    usefulClips.add(index)
                    reduced.reset()
                }
            }
        }
    }

    if (clipPath != null && path.also { it.intersect(clipPath) }.isEmpty)
        return report(tag, "The path is clipped away", removeTagFix)
}

private val pathAttrs = arrayOf(
    "fillColor", "fillType", "fillAlpha",
    "strokeColor", "strokeWidth", "strokeLineCap", "strokeLineJoin", "strokeMiterLimit", "strokeAlpha",
)
@Suppress("NOTHING_TO_INLINE") private inline operator fun <T> Array<out T>.component6(): T = this[5]
@Suppress("NOTHING_TO_INLINE") private inline operator fun <T> Array<out T>.component7(): T = this[6]
@Suppress("NOTHING_TO_INLINE") private inline operator fun <T> Array<out T>.component8(): T = this[7]
@Suppress("NOTHING_TO_INLINE") private inline operator fun <T> Array<out T>.component9(): T = this[8]
private fun ProblemsHolder.toArea(tag: XmlTag, outline: Path2D): Area? {
    val (fCol, fType, fA, sCol, sWidth, sCap, sJoin, sMiter, sA) =
        pathAttrs.map<String, XmlAttribute?>(XmlAttribute.EMPTY_ARRAY) { tag.getAttribute(it, ANDROID_NS) }
    val fill = fill(outline, fCol, fType, fA)
    val stroke = stroke(sWidth, sCol, sCap, sJoin, sMiter, sA)?.createStrokedShape(outline)?.let(::Area)
    if (fill != null && stroke != null) fill.add(stroke)
    return fill ?: stroke
}

private fun ProblemsHolder.fill(outline: Path2D, col: XmlAttribute?, type: XmlAttribute?, a: XmlAttribute?): Area? {
    val uncolored = !col.hasColor()
    val transparent = (a?.value?.toFloatOrNull() ?: 1f) == 0f
    if (uncolored || transparent) {
        reportNoFill(col, type, a, "attribute has no effect with uncolored or transparent fill")
        return null
    }

    val evenOdd = type?.value == "evenOdd"
    if (evenOdd)
        outline.windingRule = Path2D.WIND_EVEN_ODD
    val area = Area(outline)

    if (area.isEmpty) {
        reportNoFill(col, type, a, "attribute has no effect with open path")
        return null
    } else if (evenOdd && Area(outline.also { it.windingRule = Path2D.WIND_NON_ZERO }).equals(area)) {
        report(type!!, "evenOdd has no effect", removeAttrFix)
    }
    return area
}
private fun ProblemsHolder.reportNoFill(col: XmlAttribute?, type: XmlAttribute?, a: XmlAttribute?, complaint: String) {
    col?.let { report(it, complaint, removeAttrFix) }
    type?.let { report(it, complaint, removeAttrFix) }
    a?.let { report(it, complaint, removeAttrFix) }
}
private fun ProblemsHolder.stroke(
    width: XmlAttribute?,
    col: XmlAttribute?,
    cap: XmlAttribute?,
    join: XmlAttribute?,
    miter: XmlAttribute?,
    a: XmlAttribute?
): BasicStroke? {
    val strokeWidth = width?.value?.toFloatOrNull() ?: 0f
    val colored = col.hasColor()
    val opaque = (a?.value?.toFloatOrNull() ?: 1f) > 0f
    return if (strokeWidth != 0f && colored && opaque) BasicStroke(
        strokeWidth,
        when (cap?.value) {
            "round" -> BasicStroke.CAP_ROUND
            "square" -> BasicStroke.CAP_SQUARE
            else -> BasicStroke.CAP_BUTT
        },
        when (join?.value) {
            "round" -> BasicStroke.JOIN_ROUND
            "bevel" -> BasicStroke.JOIN_BEVEL
            else -> BasicStroke.JOIN_MITER
        },
        miter?.value?.toFloatOrNull() ?: 4f
    ) else {
        cap?.let { report(it, "attribute has no effect", removeAttrFix) }
        join?.let { report(it, "attribute has no effect", removeAttrFix) }
        miter?.let { report(it, "attribute has no effect", removeAttrFix) }
        a?.let { report(it, "attribute has no effect", removeAttrFix) }
        null
    }
}
fun XmlAttribute?.hasColor() =
    this?.value?.takeIf {
        it.isNotBlank() &&
                !(it.length == 5 && it.startsWith("#0")) &&
                !(it.length == 9 && it.startsWith("#00")) &&
                it != "@android:color/transparent"
    } != null
