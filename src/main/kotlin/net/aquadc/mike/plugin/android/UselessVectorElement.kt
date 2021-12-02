package net.aquadc.mike.plugin.android

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
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

private val vectorTransforms = arrayOf("scaleX", "scaleY", "rotation", "translateX", "translateY")
private val vectorTransformDefaults = floatArrayOf(1f, 1f, 0f, 0f, 0f)

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
        tag, if (isRoot) "Empty vector" else "Empty group", if (isRoot) null else RemoveElementQuickFix("Remove group")
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
            parse(pathData, matrix, pathTag.getAttributeValue("fillType", ANDROID_NS))?.let { outline ->
                checkPath(pathTag, pathTag.toArea(outline), parentClip, commonClip, clips, usefulClips)
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
                report(clipTags[i], "The path doesn't clip any of its siblings", RemoveElementQuickFix("Remove tag"))
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
            if (isRoot) null else RemoveElementQuickFix("Remove group")
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
                report(
                    clipTags[i],
                    "The clip-path is superseded by its siblings",
                    RemoveElementQuickFix("Remove clip-path")
                )
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
                if (isRoot) null else RemoveElementQuickFix("Remove group", PsiElement::getParent)
            )
            clipTagsIter.remove()
        } else {
            val clipArea = parse(pathData, matrix, null)?.let(::Area)
            if (clipArea == null) {
                clipTagsIter.remove() // bad path, ignore
            } else if (clipArea.isEmpty) {
                registerProblem(
                    nextTag,
                    "The clip-path is empty, thus the whole tag is invisible",
                    if (isRoot) null else RemoveElementQuickFix("Remove group", PsiElement::getParent)
                )
                clipTagsIter.remove()
            } else if (viewport == null) {
                clipsTo.add(clipArea) // proceed with less precision
            } else if (clipArea.also { it.intersect(viewport) }.isEmpty) {
                registerProblem(
                    nextTag,
                    "The clip-path has empty intersection with inherited clip-path or viewport. Thus it is useless, and the whole tag is invisible",
                    if (isRoot) null else RemoveElementQuickFix("Remove group", PsiElement::getParent),
                    RemoveElementQuickFix("Remove clip-path")
                )
                clipTagsIter.remove()
            } else if (Area(viewport).also { it.subtract(clipArea) }.isEmpty) {
                registerProblem(
                    nextTag,
                    "The clip-path is superseded by inherited clip-path or viewport",
                    RemoveElementQuickFix("Remove clip-path")
                )
                clipTagsIter.remove()
            } else {
                clipsTo.add(clipArea)
            }
        }
    }
}

private fun parse(pathData: String, matrix: AffineTransform?, rule: String?) = try {
    val path = PathDelegate.parse(pathData, if (rule == "evenOdd") Path2D.WIND_EVEN_ODD else Path2D.WIND_NON_ZERO)
    if (matrix != null) path.transform(matrix)
    path
} catch (e: Exception) {
    e.printStackTrace()
    null
}

private fun ProblemsHolder.checkPath(tag: XmlTag, path: Area, viewport: Area?, clipPath: Area?, clips: List<Area>, usefulClips: TIntHashSet) {
    if (path.isEmpty)
        return report(tag, "Empty path", RemoveElementQuickFix("Remove tag"))

    if (viewport != null && path.also { it.intersect(viewport) }.isEmpty)
        return report(tag, "The path is clipped away by viewport or parent clip-path", RemoveElementQuickFix("Remove tag"))

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
        return report(tag, "The path is clipped away", RemoveElementQuickFix("Remove tag"))
}

private fun XmlTag.toArea(outline: Path2D): Area {
    val strokeWidth = getAttributeValue("strokeWidth", ANDROID_NS)?.takeIf {
        getAttributeValue("strokeColor", ANDROID_NS) != null
    }?.toFloatOrNull() ?: 0f

    return Area(
        if (strokeWidth != 0f) {
            val cap = when (getAttributeValue("strokeLineCap", ANDROID_NS)) {
                "round" -> BasicStroke.CAP_ROUND
                "square" -> BasicStroke.CAP_SQUARE
                else -> BasicStroke.CAP_BUTT
            }
            val join = when (getAttributeValue("strokeLineJoin", ANDROID_NS)) {
                "round" -> BasicStroke.JOIN_ROUND
                "bevel" -> BasicStroke.JOIN_BEVEL
                else -> BasicStroke.JOIN_MITER
            }
            val miter = getAttributeValue("strokeMiterLimit", ANDROID_NS)?.toFloatOrNull() ?: 4f
            BasicStroke(strokeWidth, cap, join, miter).createStrokedShape(outline)
        } else outline
    )
}
