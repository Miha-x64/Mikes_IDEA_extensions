@file:Suppress("UnusedImport")
package net.aquadc.mike.plugin.test

import android.content.Context
import android.view.View
import android.widget.VideoView
import kotlinx.android.synthetic.*

fun test(context: Context?) {
    View(context).setOnClickListener(null)
    VideoView(context).setOnClickListener(null)
    object : VideoView(context) {}.setOnClickListener(null)
    SuperVideoView(context).setOnClickListener(null)


    context!!.obtainStyledAttributes(intArrayOf(android.R.attr.content)).recycle()
    val var1 = intArrayOf(android.R.attr.content)
    context.obtainStyledAttributes(var1).recycle()
    val var2 = intArrayOf(android.R.attr.content, 0)
    context.obtainStyledAttributes(var2).recycle()
    context.obtainStyledAttributes(UnsupportedPropJ.ATTRS).recycle()
    context.obtainStyledAttributes(ATTRS).recycle()
    context.obtainStyledAttributes(R.styleable.ActionBar).recycle()
}
val ATTRS = intArrayOf(android.R.attr.content, 0)
@JvmField val ATTRS2 = intArrayOf(android.R.attr.content, 0)

private class SuperVideoView(context: Context?) : VideoView(context)
