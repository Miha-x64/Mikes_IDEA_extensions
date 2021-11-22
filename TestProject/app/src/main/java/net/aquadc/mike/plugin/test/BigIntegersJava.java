package net.aquadc.mike.plugin.test;

import java.math.BigDecimal;
import java.math.BigInteger;

@SuppressWarnings("unused")
public class BigIntegersJava {

    void whatever() {
        System.out.println(BigDecimal.valueOf(0));
        System.out.println(BigInteger.valueOf(0));
        System.out.println(new BigDecimal("1"));
        System.out.println(new BigInteger("1"));
        System.out.println(new BigDecimal("1").compareTo(BigDecimal.ZERO));
    }

}
