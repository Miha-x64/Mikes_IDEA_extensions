package net.aquadc.mike.plugin

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Suppress("NOTHING_TO_INLINE") @JvmInline value class IntPair(val both: Long) {
    constructor(first: Int, second: Int) : this(first.toLong().shl(32) or (second.toLong() and 0xFFFFFFFFL))

    inline operator fun component1(): Int = (both ushr 32).toInt()
    inline operator fun component2(): Int = (both and 0xFFFFFFFFL).toInt()
}

fun <T> Array<T>.lastIndicesOfExclusive(first: T, second: T): IntPair {
    var ioff = -1
    var iofs = -1
    var i = size
    while (--i >= 0 && ((ioff or iofs) < 0)) {
        if (ioff < 0 && this[i] == first) ioff = i
        else if (iofs < 0 && this[i] == second) iofs = i
    }
    return IntPair(ioff, iofs)
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> Array<out T>.copy(): Array<T> =
    arrayOfNulls<T>(size).also { System.arraycopy(this, 0, it, 0, size) } as Array<T>

@OptIn(ExperimentalContracts::class)
inline fun <T> Array<T>.tryUpdate(atIndex: Int, update: (T) -> T) {
    contract { callsInPlace(update, InvocationKind.AT_MOST_ONCE) }
    trySet(atIndex) { update(this[atIndex]) }
}

@OptIn(ExperimentalContracts::class)
inline fun <T> Array<T>.trySet(index: Int, value: () -> T) {
    contract { callsInPlace(value, InvocationKind.AT_MOST_ONCE) }
    if (index in indices) this[index] = value()
}
