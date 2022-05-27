package net.aquadc.mike.plugin.test

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import androidx.annotation.RequiresApi


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun main() {
    GradientDrawable().apply {
        cornerRadius = 4f
        color = null
    }

    val d = GradientDrawable()
    d.cornerRadius = 4f
    d.setColor(Color.RED)
}
