@file:Suppress("ktNoinlineFunc", "unused") // another inspection
package net.aquadc.mike.plugin.test


inline fun leak1(zzz: () -> Unit) {
    val a = { }
    val b = ::whatever
}

inline fun leak2(crossinline zzz: () -> Unit) {
    whatever { } // TODO
}


inline fun noLeak1(crossinline zzz: () -> Unit) {
    whatever { zzz() }
}

inline fun noLeak2(crossinline zzz: () -> Unit) =
    Runnable { zzz() }

inline fun noLeak3(crossinline zzz: () -> Unit) =
    object : Runnable {
        override fun run() {
            zzz()
        }
    }

fun noLeak() {
    val a = { }
    val b = ::whatever
}

fun whatever(zzz: () -> Unit) = Unit
