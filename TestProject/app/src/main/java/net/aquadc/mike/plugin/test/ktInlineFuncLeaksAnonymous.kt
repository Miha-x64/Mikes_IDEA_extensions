@file:Suppress("ktNoinlineFunc") // another inspection
package net.aquadc.mike.plugin.test


inline fun leaks(zzz: () -> Unit) {
    val a = { }
    val b = ::doesNotLeak
}

fun doesNotLeak(zzz: () -> Unit) {
    val a = { }
    val b = ::doesNotLeak
}
