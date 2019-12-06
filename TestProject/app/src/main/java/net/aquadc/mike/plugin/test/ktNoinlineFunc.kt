@file:Suppress("unused", "UNUSED_VARIABLE")
package net.aquadc.mike.plugin.test


fun noinlines(): () -> Unit {
    { }

    fun func0() {}

    val func1 = fun() {}

    val func2 = ::noinlines

    val func3 = { }

    consumeNoInline1(fun() {})
    consumeNoInline1(::inlines)
    consumeNoInline1 {  }

    consumeNoInline2(fun() {})
    consumeNoInline2(::inlines)
    consumeNoInline2 {  }
    consumeNoInline2(f = {  })

    ({ }) - { }
    { } noinlineInfix { }

    return { }
}

fun inlines() {
    consumeInline(fun() {})
    consumeInline(::inlines)
    consumeInline {  }

    Unit + { }
    Unit inlineInfix { }
}

inline fun consumeInline(f: () -> Unit) {}
fun consumeNoInline1(f: () -> Unit) {}
inline fun consumeNoInline2(noinline f: () -> Unit) {}

operator fun (() -> Unit).minus(f: () -> Unit) {}
operator fun (() -> Unit).unaryMinus() {}
infix fun (() -> Unit).noinlineInfix(f: () -> Unit) {}

inline operator fun Unit.plus(f: () -> Unit) {}
inline infix fun Unit.inlineInfix(f: () -> Unit) {}

inline fun (() -> Unit).zzz() {
    this()
}

inline operator fun (() -> Unit).unaryPlus() {
    this()
}
