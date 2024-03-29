/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.graphics;

// Mike-REMOVED import android.annotation.IntDef, java.lang.annotation.Retention, java.lang.annotation.RetentionPolicy;

public class PixelFormat {
    // Mike-REMOVED public @interface Opacity {}, public @interface Format { }

    // NOTE: these constants must match the values from graphics/common/x.x/types.hal

    public static final int UNKNOWN      = 0;

    /** System chooses a format that supports translucency (many alpha bits) */
    public static final int TRANSLUCENT  = -3;

    /**
     * System chooses a format that supports transparency
     * (at least 1 alpha bit)
     */
    public static final int TRANSPARENT  = -2;

    /** System chooses an opaque format (no alpha bits required) */
    public static final int OPAQUE       = -1;

    // Mike-REMOVED format constants RGBA_8888, RGBX_8888, RGB_888, RGB_565, RGBA_5551, RGBA_4444, A_8, L_8, LA_88, RGB_332      = 0xB;

    // Mike-REMOVED YCbCr_422_SP, YCbCr_420_SP, YCbCr_422_I, RGBA_F16, RGBA_1010102, HSV_888 = 0x37, JPEG

    // Mike-REMOVED public int bytesPerPixel, bitsPerPixel;

    // Mike-REMOVED public static void getPixelFormatInfo(@Format int format, PixelFormat info)

    // Mike-REMOVED public static boolean formatHasAlpha(@Format int format)

    // Mike-REMOVED public static boolean isPublicFormat(@Format int format)

    // Mike-REMOVED public static String formatToString(@Format int format)
}
