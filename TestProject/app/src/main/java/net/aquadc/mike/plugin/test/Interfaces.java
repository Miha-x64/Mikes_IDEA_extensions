package net.aquadc.mike.plugin.test;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class Interfaces {

    private void a() {
        a(1, 1, Collections.<Number>emptyList());
    }

    static void a(Serializable a, Comparable b, List<? extends Number> c) {

    }

}
