package net.aquadc.mike.plugin.svg

import android.graphics.PathDelegate
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import it.unimi.dsi.fastutil.floats.FloatArrayList
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.aquadc.mike.plugin.StringVisitor
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.android.res.Cmd
import net.aquadc.mike.plugin.android.res.tryProposeShortening
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import java.awt.geom.Path2D

/**
 * @author Mike Gorünóv
 */
class SvgPathDataShortenInspection : UastInspection() {
    override fun uVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): AbstractUastNonRecursiveVisitor =
        object : StringVisitor() {
            override fun visitString(src: PsiElement, pathData: String): Boolean {
                try {
                    val paths = CountingList<Path2D.Float>()
                    val cmds = ArrayList<Cmd>()
                    val rawText = src.text
                    val beginValueAt = rawText.indexOf(pathData)
                    val floatRanges = IntArrayList()
                    val rawPathData = if (beginValueAt >= 0) pathData else rawText
                    val pathStarts = if (rawPathData == pathData) IntArrayList() else null
                    val endPositions = if (rawPathData == pathData) FloatArrayList() else null
                    PathDelegate.parse(pathData, paths, cmds, pathStarts, floatRanges, endPositions, /*evenOdd: whatever*/ false)
                    if (paths.isNotEmpty()) {
                        holder.tryProposeShortening(cmds, pathData, usefulPrecision = -1, src, rawPathData, beginValueAt.coerceAtLeast(0))
                    }
                } catch (_: PathDelegate.PathError) {
                    return true // String is not pathData, it's OK
                }
                return true
            }

        }

    private class CountingList<T> : AbstractMutableList<T>() {
        override fun set(index: Int, element: T): T =
            throw UnsupportedOperationException()

        override fun removeAt(index: Int): T =
            throw UnsupportedOperationException()

        override fun add(index: Int, element: T) {
            if (index !in 0..size)
                throw IndexOutOfBoundsException("Index $index is OOB for size $size")
            size++
        }

        override var size: Int = 0
            private set

        override fun get(index: Int): T =
            throw UnsupportedOperationException()
    }
}