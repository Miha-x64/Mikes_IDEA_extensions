package net.aquadc.mike.plugin.test

@Suppress("unused")
fun boxArrays() {
    // nothing to do here
    arrayOfNulls<Any>(4)
    IntArray(1)


    arrayOf(1, 2, 3, null)
    arrayOf(1u, 2u, null)
    Array(5) { it.toString().toFloatOrNull() }
    Array(init = { it.toString().toFloatOrNull() }, size = 5)
    listOf(1L)
    listOf(1, 2L, null)
    ArrayList<Boolean>(16)
    arrayListOf(true, false)
    ArrayList<Boolean>()
    setOf(1, 2, null)
    arrayOf(1, 2, 3)
    arrayOf(1, 2, 3L)
    arrayOfNulls<Int>(4)
    arrayOf(1u, 2u)
    Array(5) { it.toFloat() }
    listOf(1, 2L)
    setOf(1u, 2u)
    mutableSetOf(1)
    hashSetOf(1, 1)
    sortedSetOf(3, 2, 1)
    sortedSetOf(3, 2, 1, null)
    linkedSetOf(1, 9, 2)

//    1 in setOf(1) // TODO contains -> binarySearch
//    setOf(1) intersect linkedSetOf(1) // TODO intersect -> filter { binarySearch() }
//    mutableSetOf(1).retainAll(linkedSetOf(1)) // TODO retainAll(Set) -> retainAll { binarySearch() }
}
