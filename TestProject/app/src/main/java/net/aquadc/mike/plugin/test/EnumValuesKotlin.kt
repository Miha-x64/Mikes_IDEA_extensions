package net.aquadc.mike.plugin.test

enum class EnumValuesKotlin {
    A, B, C;

    init {
        values()
    }

    internal fun zzz() {
        EnumValuesKotlin.values()
    }

    companion object {
        private val VALS = values() // ok
        private val VALS2: Array<EnumValuesKotlin>
        init {
            VALS2 = values() // ok
        }
    }

}

class SomeClassKt {
    constructor() {
        EnumValuesKotlin.values()
    }

    init {
        EnumValuesKotlin.values()
    }
}
