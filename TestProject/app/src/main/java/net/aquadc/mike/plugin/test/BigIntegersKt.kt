package net.aquadc.mike.plugin.test

import java.math.BigDecimal
import java.math.BigInteger

fun whatever() {
    println(BigDecimal.valueOf(0))
    println(BigInteger.valueOf(0))
    println(BigDecimal("1"))
    println(BigInteger("1"))
    println(BigDecimal.ONE.compareTo(BigDecimal.ZERO))
    println(BigInteger.ONE.compareTo(BigInteger.ZERO))
    println(BigInteger.ONE.let { it.compareTo(BigInteger.ZERO) })
    println(BigInteger.ONE.run {
        println()
        compareTo(BigInteger.ZERO)
    })
}