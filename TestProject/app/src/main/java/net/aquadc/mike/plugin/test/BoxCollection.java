package net.aquadc.mike.plugin.test;

import java.util.*;

@SuppressWarnings({"unused", "ResultOfMethodCallIgnored"})
public class BoxCollection {
    public static void main(String[] args) {
        // skip inapplicable type
        var arrN = new Object[4];
        var iArrN = new int[0];


        var bools = new Boolean[16];
        new ArrayList<Boolean>(10);
        new HashSet<Integer>(10);
        new TreeSet<Long>();
        var iArrNI = new Integer[] { 1, 2, 3, null };
        Arrays.asList(1, 2, 3, null);
        var bIArrN = new Integer[4];
        var bIArr = new Integer[] { 1, 2, 3 };
        Arrays.asList(1, 2, 3);
        List.of(1, 2, 3);
        Set.of(4L, 5L, 6L);
    }
}
