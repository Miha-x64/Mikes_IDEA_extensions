package net.aquadc.mike.plugin.test

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import androidx.annotation.RequiresApi


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun main() {
    GradientDrawable().apply {
        color = null
        cornerRadius = 4f
    }

    val d = GradientDrawable()
    d.setColor(Color.RED)
    d.cornerRadius = 4f
}
