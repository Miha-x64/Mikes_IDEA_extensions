package net.aquadc.mike.plugin.test

import com.google.gson.Gson

enum class EnumValuesKotlin {
    A, B, C;

    init {
        values()
        Gson()
    }

    internal fun zzz() {
        EnumValuesKotlin.values()
        Gson()
    }

    companion object {
        private val VALS = values() // ok
        private val GSON = Gson()

        private val VALS2: Array<EnumValuesKotlin>
        private val GSON2: Gson
        init {
            VALS2 = values() // ok
            GSON2 = Gson()
        }
    }

}

class SomeClassKt {
    constructor() {
        EnumValuesKotlin.values()

        Gson().fromJson("1", Int::class.java)
    }

    init {
        EnumValuesKotlin.values()
        Gson()
    }
}
