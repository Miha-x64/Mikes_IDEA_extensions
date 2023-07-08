package net.aquadc.mike.plugin.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class UncachedJava {

    private static final Gson GSON = new Gson(); // ok
    private static final Gson GSON2 = new GsonBuilder().create();

    private final Gson gson = new Gson();
    private final Gson gson2 = new GsonBuilder().create();

    private static final Gson GSON3;
    private static final Gson GSON4;
    private final Gson gson3;
    private final Gson gson4;
    static {
        GSON3 = new Gson();
        GSON4 = new GsonBuilder().create();

        new Gson().newBuilder().create(); // ok, don't report in static initializers
    }
    {
        gson3 = new Gson();
        gson4 = new GsonBuilder().create();
    }

}
