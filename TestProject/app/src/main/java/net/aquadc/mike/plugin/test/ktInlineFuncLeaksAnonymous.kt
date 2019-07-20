@file:Suppress("ktNoinlineFunc", "unused") // another inspection
package net.aquadc.mike.plugin.test


inline fun leaks(zzz: () -> Unit) {
    val a = { }
    val b = ::dontLeakMe
}

fun dontLeakMe(zzz: () -> Unit) {
    val a = { }
    val b = ::dontLeakMe
}
