package net.aquadc.mike.plugin.test;

import com.google.gson.Gson;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public enum UncachedJava {
    A, B, C;

    private static final UncachedJava[] VALS = values(); // ok
    private static final Gson GSON = new Gson();

    private final UncachedJava[] vals = values();
    private final Gson gson = new Gson();

    private static final UncachedJava[] VALS2;
    private static final Gson GSON2;
    private final UncachedJava[] vals2;
    private final Gson gson2;
    static {
        VALS2 = values(); // ok
        GSON2 = new Gson();

        values();
        new Gson();
    }
    {
        vals2 = values();
        gson2 = new Gson();
    }

    void zzz() {
        UncachedJava.values();
        new Gson();
    }

}
