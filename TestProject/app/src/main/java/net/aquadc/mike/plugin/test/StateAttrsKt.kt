package net.aquadc.mike.plugin.test

import android.content.res.ColorStateList
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.animation.StateListAnimator

@Suppress("unused")
fun stateAttrsKt() {
    listOf(ColorStateList(
        arrayOf(intArrayOf(android.R.attr.checkable, android.R.attr.checked, android.R.attr.enabled, 0)),
        null,
    ))
    StateListDrawable().run {
        addState(
            intArrayOf(android.R.attr.checkable, android.R.attr.checked, android.R.attr.enabled, 1),
            null
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        StateListAnimator().addState(
            intArrayOf(android.R.attr.checkable, android.R.attr.checked, android.R.attr.enabled, 2),
            null
        )
}
