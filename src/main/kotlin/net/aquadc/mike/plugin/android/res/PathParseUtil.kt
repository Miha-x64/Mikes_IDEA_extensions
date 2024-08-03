package net.aquadc.mike.plugin.android.res


internal fun Char.isCommand(): Boolean =
    (code - 'A'.code) * (code - 'Z'.code) <= 0 && this != 'E' ||
        (code - 'a'.code) * (code - 'z'.code) <= 0 && this != 'e'

internal fun Char.isCWSP(): Boolean =
    this in CWSP

internal fun CharSequence.containsDot(startIndex: Int, endIndex: Int): Boolean =
    this.indexOfDot(startIndex, endIndex) >= 0

internal fun CharSequence.indexOfDot(startIndex: Int, endIndex: Int): Int {
    return this.indexOf('.', startIndex, endIndex)
}

internal fun CharSequence.containsE(startIndex: Int, endIndex: Int): Boolean {
    return this.contains('e', 'E', startIndex, endIndex)
}

private val CWSP =
    charArrayOf('\t', ' ', '\n', 12.toChar() /*\f*/, '\r', ',') // https://www.w3.org/TR/SVG/paths.html#PathDataBNF

private fun CharSequence.indexOf(needle: Char, startIndex: Int, endIndex: Int): Int {
    var startIndex = startIndex
    while (startIndex < endIndex) {
        if (this[startIndex] == needle) return startIndex
        startIndex++
    }
    return -1
}

private fun CharSequence.contains(n1: Char, n2: Char, startIndex: Int, endIndex: Int): Boolean {
    var startIndex = startIndex
    while (startIndex < endIndex) {
        if (this[startIndex] == n1 || this[startIndex] == n2) return true
        startIndex++
    }
    return false
}
