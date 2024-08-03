package net.aquadc.mike.plugin.android.res

import it.unimi.dsi.fastutil.ints.IntArrayList
import org.jetbrains.annotations.TestOnly
import java.math.BigDecimal
import kotlin.math.max

class Cmd internal constructor(
    private val startX: Float,
    private val startY: Float,
    val cmd: Char,
    val pathData: CharSequence,
    val floatRanges: IntArrayList,
    val rangesOffset: Int
) {
    init {
        require(paramsOf(cmd) != null)
        if (params.isNotEmpty()) require(rangesOffset >= 0)
        // else Zz, no rangesOffset, we're literally nowhere
    }
    private val params: Array<Param>
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
        // TODO shorten command when appending
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
            buf.append(startX)
            val mid = buf.length
            buf.append(startY)
            val end = buf.length
            Cmd(0f, 0f, 'M', buf, IntArrayList.of(0, mid, mid, end), 0).appendTo(null, buf, Int.MAX_VALUE)
            buf.replace(0, end + 1 /* eat 'M'*/, "@(").append(')')
        }
        appendTo(null, buf, Int.MAX_VALUE)
        return buf.toString()
    }

    @TestOnly fun args(): FloatArray =
        FloatArray(params.size) { i ->
            pathData.subSequence(
                floatRanges.getInt(rangesOffset + 2 * i),
                floatRanges.getInt(rangesOffset + 2 * i + 1),
            ).toString().toFloat()
        }

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

fun List<Cmd>.shorten(): List<Cmd> =
    this // TODO

fun List<Cmd>.appendTo(buf: StringBuilder, precision: Int) {
    var last: Cmd? = null
    for (i in indices) {
        val curr = this[i]
        curr.appendTo(last, buf, precision)
        last = curr
    }
}
