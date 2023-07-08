@file:Suppress(
    "ConvertSecondaryConstructorToPrimary", "JoinDeclarationAndAssignment", "RemoveRedundantQualifierName", "unused"
)
package net.aquadc.mike.plugin.test

import com.google.gson.Gson
import com.google.gson.GsonBuilder

private val GSON = Gson()
private val GSON2 = GsonBuilder().create()

class UncachedKotlin {

    private val gson = Gson()
    private val gson2 = GsonBuilder().create()
    private val vals2: Array<UncachedKotlin>
    private val gson3: Gson
    private val gson4: Gson
    init {
        gson3 = Gson()
        gson4 = GsonBuilder().create()
    }

    companion object {
        private val GSON3: Gson
        private val GSON4: Gson

        init {
            GSON3 = Gson()
            GSON4 = GsonBuilder().create()
            Gson().newBuilder().create()
        }
    }

    fun zzz() {
        Gson().newBuilder().create()
    }
}
