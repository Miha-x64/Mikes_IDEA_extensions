package net.aquadc.mike.plugin.android.res

import it.unimi.dsi.fastutil.ints.IntArrayList
import org.jetbrains.annotations.TestOnly
import java.math.BigDecimal
import kotlin.math.atan2
import kotlin.math.max

class Cmd internal constructor(
    val startX: BigDecimal, // We use these values when rewriting,
    val startY: BigDecimal, // so here's my $0.05000000074505806 to avoid screwing up precision.
    val cmd: Char,
    val args: Array<out BigDecimal>,
    // TODO the following should be optional after adding `args`
    val pathData: CharSequence,
    val floatRanges: IntArrayList,
    val rangesOffset: Int
) {
    init {
        require(paramsOf(cmd) != null)
        if (params.isNotEmpty()) require(rangesOffset >= 0)
        // else Zz, no rangesOffset, we're literally nowhere
    }

    val startXF get() = startX.toFloat()
    val startYF get() = startY.toFloat()

    val params: Array<Param>
        get() = paramsOf(cmd)!!

    fun appendTo(context: Cmd?, buf: StringBuilder, precision: Int) {
        var afterFractional = false
        if (params.isEmpty()) {
            buf.append(cmd) // Zz
        } else {
            // We may skip cmd char e.g. when chaining "M0 0L-1 -1" → "M0 0-1-1";
            // if not possible, preserve original cmd character or divider,
            // e.g. "M0 0 1 1" remains unchanged, "M0 0L 1 1" → "M0 0L1 1"
            var divider: Char
            if (context != null) {
                if (when (context.cmd) { // “If a moveto is followed by multiple pairs of coordinates,
                        'M' -> cmd == 'L' // the subsequent pairs are treated as implicit lineto commands.”
                        'm' -> cmd == 'l' // https://www.w3.org/TR/SVG/paths.html#PathDataMovetoCommands
                        else -> context.cmd == cmd
                    }
                ) { // then commands can be chained, preserve divider (or drop later, if useless)
                    divider = cmd
                    for (i in floatRanges.getInt(rangesOffset) - 1 downTo 0) {
                        val ch = pathData[i]
                        if (ch.isCWSP()) {
                            divider = ch
                            continue // e.g. in "L 1" we preserve 'L', not the following ' '
                        } else if (ch == cmd) {
                            divider = ch
                            break
                        } else {
                            break
                        }
                    }
                } else {
                    buf.append(cmd) // Different commands, no chaining.
                    divider = '\u0000'
                }

                context.params.takeIf { it.isNotEmpty() }?.let { ctxParams ->
                    afterFractional = context.pathData.containsDot(
                        context.floatRanges.getInt(context.rangesOffset + 2 * ctxParams.size - 2),
                        context.floatRanges.getInt(context.rangesOffset + 2 * ctxParams.size - 1),
                    )
                }

            } else {
                buf.append(cmd) // First command, no chaining.
                divider = '\u0000'
            }

            // Now command is added if it is absolutely necessary,
            // or preserved (probably as a whitespace) in `divider` and its necessity depends on adjacent floats' format

            var offset = rangesOffset
            for (param in params) {
                val floatStart = floatRanges.getInt(offset++)
                val floatEnd = floatRanges.getInt(offset++)
                param.appendArgValue(buf, pathData, divider, afterFractional, floatStart, floatEnd, precision)
                divider = pathData.takeIf { it.length > floatEnd }?.get(floatEnd)?.takeIf(Char::isCWSP) ?: ' '
                afterFractional = pathData.containsDot(floatStart, floatEnd)
            }
        }
    }

    fun maxPrecision(): Int {
        var max = 0
        forEachFloat { _, _, precision ->
            max = max(max, precision)
        }
        return max
    }

    fun firstFloatStart(ofPrecision: Int): Int {
        forEachFloat { floatStart, _, precision ->
            if (precision > ofPrecision)
                return floatStart
        }
        throw NoSuchElementException()
    }

    fun lastFloatEnd(ofPrecision: Int): Int {
        var last = -1
        forEachFloat { _, floatEnd, precision ->
            if (precision > ofPrecision)
                last = floatEnd
        }
        if (last < 0) throw NoSuchElementException()
        return last
    }

    private inline fun forEachFloat(block: (floatStart: Int, floatEnd: Int, precision: Int) -> Unit) {
        var i = 0
        val ints = params.size * 2
        while (i < ints) {
            val floatStart = floatRanges.getInt(rangesOffset + i++)
            val floatEnd = floatRanges.getInt(rangesOffset + i++)
            val iod = pathData.indexOfDot(floatStart, floatEnd)
            val precision =
                if (iod >= 0 && !pathData.containsE(floatStart, floatEnd)) floatEnd - iod - 1
                else -1
            block(floatStart, floatEnd, precision)
        }
    }

    @TestOnly
    override fun toString(): String {
        val buf = StringBuilder()
        if (cmd != 'M') {
            Cmd(BigDecimal.ZERO, BigDecimal.ZERO, 'M', startX, startY)
                .appendTo(null, buf, Int.MAX_VALUE)
            buf.replace(0, 1 /* eat 'M'*/, "@(").append(')')
        }
        appendTo(null, buf, Int.MAX_VALUE)
        return buf.toString()
    }

    fun argsF(): FloatArray =
        FloatArray(params.size) { i -> args[i].toFloat() }

    companion object {
        private val PARAMS: Array<Array<Param>?> = arrayOfNulls<Array<Param>?>(26).also { params ->
            val coordinate = arrayOf(Param.Coord)
            val coordinatePair = arrayOf(Param.Coord, Param.Coord)
            val coordinate2Pairs = arrayOf(Param.Coord, Param.Coord, Param.Coord, Param.Coord)
            val coordinate3Pairs = arrayOf(Param.Coord, Param.Coord, Param.Coord, Param.Coord, Param.Coord, Param.Coord)
            params['A'.code - 'A'.code] = arrayOf(
                Param.Number, Param.Number, Param.Number, Param.Flag, Param.Flag, Param.Coord, Param.Coord,
                //    radius        radius        angle
            )
            params['C'.code - 'A'.code] = coordinate3Pairs
            params['H'.code - 'A'.code] = coordinate
            params['L'.code - 'A'.code] = coordinatePair
            params['M'.code - 'A'.code] = coordinatePair
            params['Q'.code - 'A'.code] = coordinate2Pairs
            params['S'.code - 'A'.code] = coordinate2Pairs
            params['T'.code - 'A'.code] = coordinatePair
            params['V'.code - 'A'.code] = coordinate
            params['Z'.code - 'A'.code] = emptyArray()
        }

        @JvmStatic
        fun paramsOf(cmd: Char): Array<Param>? {
            return PARAMS[cmd.code - (if (cmd > 'Z') 'a' else 'A').code]
        }
    }

    enum class Param {
        Coord,
        Number,
        Flag {
            override fun appendArgValue(
                dst: StringBuilder,
                pathData: CharSequence,
                divider: Char,
                afterFractional: Boolean,
                floatStart: Int,
                floatEnd: Int,
                precision: Int
            ) {
                if (divider != '\u0000') dst.append(divider)

                val flag = pathData[floatStart]
                dst.append(
                    if (floatStart + 1 == floatEnd && (flag == '0' || flag == '1')) flag
                    else if (pathData.subSequence(floatStart, floatEnd).toString().toFloat() == 0f) '0' else '1'
                )
            }
        },
        ;

        open fun appendArgValue(
            dst: StringBuilder,
            pathData: CharSequence,
            divider: Char,
            afterFractional: Boolean,
            floatStart: Int,
            floatEnd: Int,
            precision: Int
        ) {
            val iod = pathData.indexOfDot(floatStart, floatEnd)
            var pathData = pathData
            var floatStart = floatStart
            var floatEnd = floatEnd
            if (pathData.containsE(floatStart, floatEnd)) { // get rid of exponential, some parsers can't handle it
                pathData = StringBuilder(BigDecimal(pathData.substring(floatStart, floatEnd)).toPlainString())
                pathData.trimToPrecision(precision)
                floatStart = 0
                floatEnd = pathData.length
            } else {
                if (((iod > 0 && floatEnd - iod > precision) || // excessive precision
                        (pathData[floatStart] == '0' && floatEnd - floatStart > 1) || // leading zero(es)
                        pathData.startsWith("-0", floatStart) || // another case of leading zero
                        (iod >= 0 && pathData[floatEnd - 1] == '0'))
                ) { // trailing zeroes in fractional part
                    pathData = StringBuilder(floatEnd - floatStart).also { it.append(pathData, floatStart, floatEnd) }
                    pathData.trimToPrecision(precision)
                    floatStart = 0
                    floatEnd = pathData.length
                }
            }

            // add divider if absolutely necessary
            if (pathData[floatStart] == '-') {}
            else if (pathData[floatStart] == '.' && afterFractional) {}
            else if (divider != '\u0000') dst.append(divider)

            dst.append(pathData, floatStart, floatEnd)
        }
    }

}

fun Iterable<Cmd>.maxPrecision(): Int =
    maxOf(Cmd::maxPrecision)

fun List<Cmd>.shorten(): List<Cmd> {
    val cmds = toMutableList()
    var i = 0
    var lastMove: Cmd? = null
    while (i < cmds.lastIndex) {
        if (cmds[i].cmd.let { it == 'M' || it == 'm' })
            lastMove = cmds[i]
        val cmd = collapse(lastMove, cmds[i], cmds[i + 1])
        if (cmd == null) {
            i++
        } else {
            cmds[i] = cmd
            cmds.removeAt(i + 1)
            if (i > 0) i-- // re-test previous command against this one
        }
    }

    // TODO shorten (cmd1, cmd).shortenRight -> (cmd1, shortened cmd2) e.g. use shorter Bézier version when using ctrl point from the previous cmd

    // TODO shorten individual commands e.g. m0 1 ⇒ v1

    return cmds
}

private fun collapse(lastMove: Cmd?, a: Cmd, b: Cmd): Cmd? = when {

    (a.cmd == 'M' || a.cmd == 'm') && b.cmd == 'M' -> {
        Cmd(
            a.startX, a.startY, // useless for M, preserve for consistency
            b.cmd, *b.args
        )
    }
    (a.cmd == 'M' || a.cmd == 'm') && b.cmd == 'm' -> {
        val (x1, y1) = a.args
        val (x2, y2) = b.args
        Cmd(a.startX, a.startY, a.cmd, x1 + x2, y1 + y2)
    }

    a.cmd in LINE_CMDS && b.cmd in LINE_CMDS -> {
        val d1 = a.lineVec()
        if (d1.is0()) b else { // Handle (rare) special cases because “atan2(0.0, 0.0) is 0.0”
            val d2 = b.lineVec() // and safely drop a command which moves by 0.
            if (d2.is0()) a else {
                if (d1.angle() != d2.angle()) null else { // Different angles. Preserve both.
                    val (ex, ey) = b.end()
                    Cmd(a.startX, a.startY, 'L', ex, ey) // converted to lHhVv later, when optimizing single cmd
                }
            }
        }
    }

    // TODO[200IQ MODE]: join arcs and Béziers

    lastMove != null && a.cmd in LINE_CMDS && (b.cmd == 'Z' || b.cmd == 'z') -> { // [MX,Y ..notMm] L0,0 Z ⇒ Z
        val (initX, initY) = lastMove.end()
        val (endX, endY) = a.end()
        if (initX == endX && initY == endY) b else null // drop LlHhVv
    }

    else -> null
}

private operator fun Cmd.Companion.invoke(
    startX: BigDecimal, startY: BigDecimal,
    cmd: Char, vararg args: BigDecimal,
): Cmd {
    val ranges = IntArrayList(2 * args.size)
    var pos = 0
    val str = args.joinToString(separator = "") {
        ranges.add(pos)
        val str = it.toPlainString()
        pos += str.length
        ranges.add(pos)
        str
    }
    return Cmd(startX, startY, cmd, args, str, ranges, 0).also {
        check(2 * it.params.size == ranges.size)
    }
}

private val LINE_CMDS = charArrayOf('L', 'l', 'H', 'h', 'V', 'v')
/** @return `[dx, dy]` */
private fun Cmd.lineVec(): Vec2F = when (cmd) {
    'L' -> argsF().let { (ex, ey) -> Vec2F(ex - startXF, ey - startYF) }
    'l' -> argsF()
    'H' -> Vec2F(argsF().single() - startXF, 0f)
    'h' -> Vec2F(argsF().single(), 0f)
    'V' -> Vec2F(0f, argsF().single() - startYF)
    'v' -> Vec2F(0f, argsF().single())
    else -> throw UnsupportedOperationException()
}
private fun FloatArray.angle(): Float =
    atan2(this[1], this[0])

typealias Vec2F = FloatArray
private fun Vec2F(x: Float, y: Float): Vec2F =
    floatArrayOf(x, y) // too lazy to make an inline class here, dear JVM, scalarize plzzzzz

private fun Vec2F.is0(): Boolean {
    val (x, y) = this
    return x == 0f && y == 0f
}

private fun Cmd.end(): Pair<BigDecimal, BigDecimal> = when (cmd) {
    'L', 'M' -> args.let { (ex, ey) -> ex to ey }
    'l', 'm' -> args.let { (dx, dy) -> (startX + dx) to (startY + dy) }
    'H' -> args.single() to startY
    'h' -> (startX + args.single()) to startY
    'V' -> startX to args.single()
    'v' -> startX to (startY + args.single())
    else -> throw UnsupportedOperationException()
}

fun List<Cmd>.appendTo(buf: StringBuilder, precision: Int) {
    var last: Cmd? = null
    for (i in indices) {
        val curr = this[i]
        curr.appendTo(last, buf, precision)
        last = curr
    }
}
