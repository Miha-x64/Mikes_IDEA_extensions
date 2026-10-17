package net.aquadc.mike.plugin

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@JvmInline
internal value class SortedArray<E : Comparable<E>> internal constructor(private val array: Array<*>) {

    @OptIn(ExperimentalContracts::class)
    operator fun contains(value: E?): Boolean {
        contract { returns(true) implies (value != null) }
        return array.binarySearch(value) >= 0
    }

    companion object {

        fun <E : Comparable<E>> of(vararg value: E): SortedArray<E> {
            value.sort()
            return SortedArray(value)
        }

        inline fun <reified E : Comparable<E>> of(value: E): SortedArray<E> {
            return SortedArray(arrayOf(value))
        }

        fun <E : Comparable<E>> from(value: Collection<E>): SortedArray<E> {
            return SortedArray(value.toTypedArray<Any?>().also { it.sort() })
        }

    }

}
