package net.aquadc.mike.plugin.test;

import java.math.BigDecimal;

public class BigIntegers {

    void whatever() {
        System.out.println(BigDecimal.valueOf(0));
        System.out.println(new BigDecimal("1"));
        System.out.println(new BigDecimal("1").compareTo(BigDecimal.ZERO));
    }

}
