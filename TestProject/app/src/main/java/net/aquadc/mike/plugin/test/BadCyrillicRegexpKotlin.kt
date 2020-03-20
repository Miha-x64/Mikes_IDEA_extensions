@file:Suppress("unused")
package net.aquadc.mike.plugin.test


private val A_YA_UPPER = Regex("[А-Я]")
private val A_YA_LOWER = Regex("[а-я]")
private val A_YA_LOWER_UPPER = Regex("[а-яА-ЯA-Z]")
private val A_YA_UPPER_LOWER = Regex("[a-zА-Яа-я\\d]")
private val A_YA_u = Regex("[\\u0410-\\u042F]")

private val A_YA_u_AND_ESCAPED = Regex("[\\u0410-\\u042F]\\Q[а-я]\\E")

private val A_YA_ESCAPED = Regex("\\Q[а-я]\\E")
private val A_H = Regex("[а-х]")
private val H_YA = Regex("[х-я]")
