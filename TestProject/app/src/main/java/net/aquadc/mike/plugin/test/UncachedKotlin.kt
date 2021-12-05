@file:Suppress(
    "ConvertSecondaryConstructorToPrimary", "JoinDeclarationAndAssignment", "RemoveRedundantQualifierName", "unused"
)
package net.aquadc.mike.plugin.test

import com.google.gson.Gson
import com.google.gson.GsonBuilder

private val VALS = UncachedKotlin.values() // ok
private val GSON = Gson()
private val GSON2 = GsonBuilder().create()

enum class UncachedKotlin {
    A, B, C;

    private val vals = values()
    private val gson = Gson()
    private val gson2 = GsonBuilder().create()
    private val vals2: Array<UncachedKotlin>
    private val gson3: Gson
    private val gson4: Gson
    init {
        vals2 = values()
        gson3 = Gson()
        gson4 = GsonBuilder().create()
    }

    companion object {
        private val VALS2: Array<UncachedKotlin>
        private val GSON3: Gson
        private val GSON4: Gson

        init {
            VALS2 = values() // ok
            GSON3 = Gson()
            GSON4 = GsonBuilder().create()
            values() // ok, don't report in static initializers
            Gson().newBuilder().create()
        }
    }

    fun zzz() {
        values()
        Gson().newBuilder().create()
    }
}
