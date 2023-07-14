@file:Suppress("unused", "OVERRIDE_DEPRECATION")
package net.aquadc.mike.plugin.test

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable

class DrwblK : Drawable() {

    override fun draw(canvas: Canvas) {
    }

    override fun setAlpha(alpha: Int) {
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }

    override fun getOpacity(): Int =
        PixelFormat.UNKNOWN

    private val anon = object : ColorDrawable() {
        
    }

    fun <T : Drawable> neverGetsTriggered() {}
}
