package net.aquadc.mike.plugin.test;

import android.annotation.SuppressLint;

import java.util.Arrays;
import java.util.List;

public class VarargsJava {

    VarargsJava(int i, int... j) {}

    @SuppressWarnings({"InstantiationOfUtilityClass", "Since15", "ResultOfMethodCallIgnored"})
    @SuppressLint("DefaultLocale")
    public static void main(String[] args) {
        List.of(1, 2); // no
        List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        List.of(new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 });
        String.format("%d", 1);
        new VarargsJava(1, 2, 3);
        new VarargsJava(1, new int[] { 2, 3 });
        new VarargsKotlin(1, new int[] { 2, 3 });
    }
}
