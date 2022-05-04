@file:Suppress(
    "unused", "NOTHING_TO_INLINE", "ktNoinlineFunc", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER",
    "ClassName", "MayBeConstant", "FunctionName", "PackageDirectoryMismatch"
)
package com.native.goto.const.float

class enum

val int = 10
const val long = 10
@JvmField val double = 10

var float = 10
    @JvmName("float") get
    @JvmName("double") set

object goto

fun `package`() {}

@JvmName("float")
fun iAmNotAKeyword() {
    fun long() {}
    val double = 1
}

fun `const`() {}
inline fun goto() {}
inline fun <reified T> `while`() {}


fun Const(default: Unit) {
    `package`()
    goto.hashCode()
}

@JvmName("renamed")
fun short() {}

fun zzz() {}

val zzz: (Enum<*>) -> Unit = { enum ->
}

object Public {
    object public
    @JvmField val enum = Thread.State.NEW

    internal val protected = ""
    internal object private {
        @JvmField val enum = Thread.State.NEW
    }
}
