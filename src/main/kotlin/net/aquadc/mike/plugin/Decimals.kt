package net.aquadc.mike.plugin

import java.math.BigDecimal
import java.math.RoundingMode


internal fun Array<out BigDecimal>.trimmed(
    precision: Int, rounding: RoundingMode, outRounded: Array<Boolean>?,
): Array<out BigDecimal> {
    var i = 0
    val size = size
    val out: Array<BigDecimal?>
    while (true) {
        val d = this[i]
        val trd = d.trimmed(precision, rounding, outRounded)
        if (d !== trd) {
            out = arrayOfNulls(size)
            copyInto(out, endIndex = i)
            out[i] = trd
            break
        }
        if (++i == size)
            return this // no excessive decimals encountered
    }
    // previous executed line: out[i] = trd
    while (++i < size) out[i] = this[i].trimmed(precision, rounding, outRounded)
    @Suppress("UNCHECKED_CAST") return out as Array<BigDecimal>
}

/**
 * @return
 *     nothing changed ⇒ this
 *     only stripped ⇒ new BD
 *     rounded ⇒ new BD, outTrimmed[0] = true
 */
private fun BigDecimal.trimmed(
    precision: Int, rounding: RoundingMode, outRounded: Array<Boolean>?,
): BigDecimal =
    stripTrailingZeros().let { stripped ->
        return if (stripped.scale() > precision) {
            outRounded?.set(0, true)
            stripped.setScale(precision, rounding).stripTrailingZeros()
        } else if (this != stripped) stripped else this
    }
