package net.aquadc.mike.plugin.test

class VarargsKotlin(
    i: Int,
    vararg j: Int,
)
fun varargsKotlin() {
    maxOf(1, 2, 3)
    maxOf(1, 2, 3, 4)
    maxOf(a = 1, 2, 3, 4)
    maxOf(a = 1, other = intArrayOf(2, 3, 4))
    maxOf(other = intArrayOf(2, 3, 4), a = 1)
    "%d".format(1)
    "%d".f0rmat(1, 2)
    VarargsJava(1, 2, 3)
    VarargsJava(1, *intArrayOf(2, 3))
    VarargsKotlin(1, 2, 3)
    VarargsKotlin(1, *intArrayOf(2, 3))

    remember("") {}
    remember(*arrayOf("")) {}
    remember("", calculation = {})

    checkNoOob(0)

    remember( // check multiline appearance
        1,
        2,
    ) {}
}

inline fun remember(vararg keys: Any, calculation: () -> Unit) {
}

fun checkNoOob(a: Int, b: Int = 2, vararg c: Int) {
}

fun String.f0rmat(vararg args: Any?) = format(*args)
