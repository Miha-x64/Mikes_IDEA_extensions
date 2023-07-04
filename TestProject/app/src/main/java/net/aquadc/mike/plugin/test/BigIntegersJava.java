package net.aquadc.mike.plugin.test;

import java.math.BigDecimal;
import java.math.BigInteger;

@SuppressWarnings("unused")
public class BigIntegersJava {

    static void whatever() {
        System.out.println(BigDecimal.valueOf(0));
        System.out.println(BigDecimal.valueOf(2));
        System.out.println(BigInteger.valueOf(0));
        System.out.println(BigInteger.valueOf(0));
        System.out.println(new BigDecimal("1"));
        System.out.println(new BigInteger("01"));
        System.out.println(new BigInteger("1.0"));
        System.out.println(new BigInteger("1.00"));
        System.out.println(new BigInteger("0.1e2"));
        System.out.println(new BigInteger("2"));
        System.out.println(BigDecimal.ONE.compareTo(BigDecimal.ZERO));
        System.out.println(BigInteger.ONE.compareTo(BigInteger.ZERO));
    }

}
