package net.aquadc.mike.plugin.test

import android.graphics.Color
import android.graphics.drawable.GradientDrawable


fun main(args: Array<String>) {
    GradientDrawable().apply {
        cornerRadius = 4f
        setColor(Color.RED)
    }

    val d = GradientDrawable()
    d.cornerRadius = 4f
    d.setColor(Color.RED)
}
