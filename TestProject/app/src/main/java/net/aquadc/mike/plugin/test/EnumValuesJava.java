package net.aquadc.mike.plugin.test;

public enum EnumValuesJava {
    A, B, C;

    private static final EnumValuesJava[] VALS = values();
    {
        values();
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
