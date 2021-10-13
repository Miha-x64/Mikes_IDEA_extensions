@file:Suppress("unused")
package net.aquadc.mike.plugin.test

import android.graphics.Color
import android.graphics.Color.parseColor


private val black = Color.parseColor("black")
private val white = parseColor("#FFFFFF")
private val blue = parseColor("#FF0000FF")
private val orange = parseColor("#FFFF8855")
private val bad = parseColor("")
private val nonConst = parseColor(bad.toString())
