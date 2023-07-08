package net.aquadc.mike.plugin.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.OkHttpClient;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class UncachedJava {

    private static final Gson GSON = new Gson(); // ok
    private static final OkHttpClient OK_HTTP = new OkHttpClient();
    private static final Gson GSON2 = new GsonBuilder().create();
    private static final OkHttpClient OK_HTTP2 = new OkHttpClient.Builder().build();

    private final Gson gson = new Gson();
    private final OkHttpClient okHttp = new OkHttpClient();
    private final Gson gson2 = new GsonBuilder().create();
    private final OkHttpClient okHttp2 = new OkHttpClient.Builder().build();

    private static final Gson GSON3;
    private static final OkHttpClient OK_HTTP3;
    private static final Gson GSON4;
    private static final OkHttpClient OK_HTTP4;
    private final Gson gson3;
    private final OkHttpClient okHttp3;
    private final Gson gson4;
    private final OkHttpClient okHttp4;
    static {
        GSON3 = new Gson();
        OK_HTTP3 = new OkHttpClient();
        GSON4 = new GsonBuilder().create();
        OK_HTTP4 = new OkHttpClient.Builder().build();

        new Gson().newBuilder().create(); // ok, don't report in static initializers
    }
    {
        gson3 = new Gson();
        okHttp3 = new OkHttpClient();
        gson4 = new GsonBuilder().create();
        okHttp4 = new OkHttpClient.Builder().build();
    }

}
