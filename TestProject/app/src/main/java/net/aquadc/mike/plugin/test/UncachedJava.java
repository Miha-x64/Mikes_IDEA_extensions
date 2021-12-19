package net.aquadc.mike.plugin.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@SuppressWarnings({"FieldCanBeLocal", "unused", "ResultOfMethodCallIgnored"})
public enum UncachedJava {
    A, B, C;

    private static final UncachedJava[] VALS = values(); // ok
    private static final Gson GSON = new Gson();
    private static final Gson GSON2 = new GsonBuilder().create();

    private final UncachedJava[] vals = values();
    private final Gson gson = new Gson();
    private final Gson gson2 = new GsonBuilder().create();

    private static final UncachedJava[] VALS2;
    private static final Gson GSON3;
    private static final Gson GSON4;
    private final UncachedJava[] vals2;
    private final Gson gson3;
    private final Gson gson4;
    static {
        VALS2 = values(); // ok
        GSON3 = new Gson();
        GSON4 = new GsonBuilder().create();

        values(); // ok, don't report in static initializers
        new Gson().newBuilder().create();
    }
    {
        vals2 = values();
        gson3 = new Gson();
        gson4 = new GsonBuilder().create();
    }

    void zzz() {
        UncachedJava.values();
        new Gson().newBuilder().create();
    }

}
