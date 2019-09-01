package net.aquadc.mike.plugin.test

import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.util.Property
import android.view.View

@SuppressWarnings("androidTargetApiShouldBeRequires")
@TargetApi(21)
fun anims() {
    ObjectAnimator().setPropertyName("alpha")
    ObjectAnimator().setProperty(View.ALPHA)
    ObjectAnimator().propertyName
    ObjectAnimator.ofInt(null, "translationX")
    ObjectAnimator.ofInt(null, "translationX", "translationY", null)
    ObjectAnimator.ofInt(null, null as Property<Nothing?, Int>?)
    ObjectAnimator.ofInt(null, null, null as Property<Nothing?, Int>?, null)
}
