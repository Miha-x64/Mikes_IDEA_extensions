package net.aquadc.mike.plugin.test;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public enum EnumValuesJava {
    A, B, C;

    private static final EnumValuesJava[] VALS = values(); // ok
    private final EnumValuesJava[] vals = values();
    private static final EnumValuesJava[] VALS2;
    private final EnumValuesJava[] vals2;
    static {
        VALS2 = values(); // ok
        values();
    }
    {
        vals2 = values();
    }

    void zzz() {
        EnumValuesJava.values();
    }

}

class SomeClassJ {
    SomeClassJ() {
        EnumValuesJava.values();
    }
}
