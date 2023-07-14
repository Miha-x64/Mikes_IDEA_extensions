package net.aquadc.mike.plugin.test;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class DrwblJ extends ColorDrawable {
    private final Drawable anon = new Drawable() {
        @Override
        public void draw(@NonNull Canvas canvas) {

        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.UNKNOWN;
        }
    };

    <T extends Drawable> void pleaseDontTrigger() {}
}
