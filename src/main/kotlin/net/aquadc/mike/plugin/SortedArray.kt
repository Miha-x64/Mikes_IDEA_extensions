package net.aquadc.mike.plugin


inline class SortedArray<E : Comparable<E>>
    @Deprecated("private") constructor(
    private val array: Array<*>
) {

    operator fun contains(value: E): Boolean =
        array.binarySearch(value) >= 0

    companion object {

        fun <E : Comparable<E>> of(vararg value: E): SortedArray<E> {
            value.sort()
            return SortedArray(value)
        }

        fun <E : Comparable<E>> from(value: Collection<E>): SortedArray<E> {
            return SortedArray(value.toTypedArray<Any?>().also { it.sort() })
        }

    }

}
