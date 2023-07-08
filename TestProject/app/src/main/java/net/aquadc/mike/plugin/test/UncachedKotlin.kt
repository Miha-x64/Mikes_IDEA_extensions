@file:Suppress(
    "ConvertSecondaryConstructorToPrimary", "JoinDeclarationAndAssignment", "RemoveRedundantQualifierName", "unused"
)
package net.aquadc.mike.plugin.test

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient

private val GSON = Gson()
private val GSON2 = GsonBuilder().create()
private val OK_HTTP = OkHttpClient()
private val OK_HTTP2 = OkHttpClient.Builder().build()

class UncachedKotlin {

    private val gson = Gson()
    private val okHttp = OkHttpClient()
    private val gson2 = GsonBuilder().create()
    private val okHttp2 = OkHttpClient.Builder().build()
    private val gsonOk = GsonBuilder().disableHtmlEscaping().create()
    private val okHttpOk = OkHttpClient.Builder().cache(null).build()
    private val gson3: Gson
    private val okHttp3: OkHttpClient
    private val gson4: Gson
    private val okHttp4: OkHttpClient
    init {
        gson3 = Gson()
        gson4 = GsonBuilder().create()
        okHttp3 = OkHttpClient()
        okHttp4 = OkHttpClient.Builder().build()
    }

    companion object {
        private val GSON2 = Gson()
        private val OK_HTTP2 = OkHttpClient()
        private val GSON3: Gson
        private val OK_HTTP3: OkHttpClient
        private val GSON4: Gson
        private val OK_HTTP4: OkHttpClient

        init {
            GSON3 = Gson()
            OK_HTTP3 = OkHttpClient()
            GSON4 = GsonBuilder().create()
            OK_HTTP4 = OkHttpClient.Builder().build()
            Gson().newBuilder().create()
            OkHttpClient.Builder().build()
        }
    }
}
