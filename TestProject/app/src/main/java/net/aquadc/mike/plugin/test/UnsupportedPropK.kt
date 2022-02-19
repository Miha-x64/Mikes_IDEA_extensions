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
}

private class SuperVideoView(context: Context?) : VideoView(context)
