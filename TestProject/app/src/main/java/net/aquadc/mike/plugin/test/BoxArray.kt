package net.aquadc.mike.plugin.test

@Suppress("unused")
fun boxArrays() {
    arrayOf(1, 2, 3)
    arrayOf(1, 2, 3, null)
    Array(5) { it.toFloat() }
    Array(5) { it.toString().toFloatOrNull() }
}
