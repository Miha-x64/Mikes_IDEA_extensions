package net.aquadc.mike.plugin.android.res

import android.graphics.PathDelegate
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.junit.Test
import java.awt.geom.Path2D
import kotlin.math.absoluteValue

class PathCmdTest {

    private val pathsBlackHole = object : ArrayList<Path2D.Float>() {
        override fun add(element: Path2D.Float): Boolean = true
    }
    private fun checkBlackHoles() { // don't wanna override all add() methods, just necessary ones
        check(pathsBlackHole.isEmpty())
    }

    @Test
    fun reconstructShortened() {
        val cmds = ArrayList<Cmd>()
        val expect = "M17.6 11.48L19.44 8.3a0.63 0.63 0 0 0-1.09-0.63l-1.88 3.24a11.43 11.43 0 0 0-8.94 0L5.65 7.67a0.63 0.63 0 0 0-1.09 0.63L6.4 11.48A10.81 10.81 0 0 0 1 20H23A10.81 10.81 0 0 0 17.6 11.48ZM7 17.25A1.25 1.25 0 1 1 8.25 16A1.25 1.25 0 0 1 7 17.25ZM17 17.25A1.25 1.25 0 1 1 18.25 16A1.25 1.25 0 0 1 17 17.25Z"
        PathDelegate.parse(
            "M17.6,11.48 L19.44,8.3a0.63,0.63 0,0 0,-1.09 -0.63l-1.88,3.24a11.43,11.43 0,0 0,-8.94 0L5.65,7.67a0.63,0.63 0,0 0,-1.09 0.63L6.4,11.48A10.81,10.81 0,0 0,1 20L23,20A10.81,10.81 0,0 0,17.6 11.48ZM7,17.25A1.25,1.25 0,1 1,8.25 16,1.25 1.25,0 0,1 7,17.25ZM17,17.25A1.25,1.25 0,2 .3,18.25 16,1.25 1.25,0 0,1 17,17.25Z",
            pathsBlackHole, cmds, //                                                                                                                                                                                              NaUgHtY FlAgS (non-compliant but work) (originally '1 1') -^^^^
            null, IntArrayList(), null,
            false,
        )
        val reconstruction = StringBuilder()
        cmds.shortened(Int.MAX_VALUE).appendTo(reconstruction)
        assert(reconstruction.toString().replace(',', ' ').contentEquals(expect.replace(',', ' '))) { reconstruction }
        checkBlackHoles()
    }

    @Test
    fun reconstruct2() {
        val cmds1 = ArrayList<Cmd>()
        PathDelegate.parse(
            "M240.005,314.391c0,6.676 0.007,80.18 -0.006,80.18c-36.752,0 -70.879,-13.121 -97.398,-34.536c-35.558,-28.856 -57.167,-72.67 -57.167,-120.027c0,-85.361 69.203,-154.564 154.564,-154.564c85.361,0 154.564,69.203 154.564,154.564c0,85.361 -69.202,154.563 -154.563,154.563",
            pathsBlackHole, cmds1, null, IntArrayList(), null, false,
        )
        val reconstruction = StringBuilder()
        cmds1.appendTo(reconstruction)

        val rs = reconstruction.toString()
        reconstruction.clear()
        val cmds2 = ArrayList<Cmd>()
        PathDelegate.parse(rs, pathsBlackHole, cmds2, null, IntArrayList(), null, false)

        assert(cmds1.size == cmds2.size)
        cmds1.zip(cmds2).forEachIndexed { cmdIdx, (old, new) ->
            val oa = old.argsF()
            val na = new.argsF()
            assert(oa.size == na.size)
            oa.zip(na).forEachIndexed { fIdx, (of, nf) ->
                assert((of - nf).absoluteValue < .05) {
                    printErr(old, fIdx)
                    printErr(new, fIdx)
                    "$of != $nf, cmd#$cmdIdx, float#$fIdx"
                }
            }
        }
        checkBlackHoles()
    }

    @Test fun recognizesML() {
        val cmds = ArrayList<Cmd>()
        PathDelegate.parse("M0 0 5 5 4 4", pathsBlackHole, cmds, null, IntArrayList(), null, false)
        assert(StringBuilder().also { cmds.appendTo(it) }.toString() == "M0 0 5 5 4 4")

        cmds.clear()
        PathDelegate.parse("M0 0L5 5 0 0", pathsBlackHole, cmds, null, IntArrayList(), null, false)
        assert(StringBuilder().also { cmds.appendTo(it) }.toString() == "M0 0L5 5 0 0")
        checkBlackHoles()
    }

    @Test fun autoChains() {
        val cmds = ArrayList<Cmd>()
        PathDelegate.parse("M0 0L-1 -1.5L-.5 .5", pathsBlackHole, cmds, null, IntArrayList(), null, false)
        val shortened = StringBuilder().also { cmds.appendTo(it) }.toString()
        assert(shortened == "M0 0-1-1.5-0.5 0.5") { shortened }
    }

    private fun printErr(cmd: Cmd, fIdx: Int) {
        val cmd = cmd.src!!
        println(cmd.pathData)
        val fRangeAt = cmd.rangesOffset + 2 * fIdx
        println(
            " ".repeat(cmd.floatRanges.getInt(fRangeAt)) +
                "^".repeat(cmd.floatRanges.getInt(fRangeAt + 1) - cmd.floatRanges.getInt(fRangeAt))
        )
    }

}
