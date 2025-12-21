@file:Suppress("unused", "MayBeConstant", "RemoveRedundantQualifierName")
package net.aquadc.mike.plugin.test

import android.graphics.Color
import android.graphics.Color.parseColor
import androidx.core.graphics.toColorInt


private val black = Color.parseColor("black")
private val red = Color.RED
private val white = Color.parseColor("#FFFFFF")
private val blue = parseColor("#FF0000FF")
private val orange = 0xFFFF8855.toInt()
private val transparentOrange = 0x88FF8855.toInt()
private val fuchsia = "fuchsia".toColorInt()
private val bad = parseColor("nope")
private val nonConst = parseColor(bad.toString())
