package net.aquadc.mike.plugin.test;

import android.graphics.Color;

import static android.graphics.Color.parseColor;

@SuppressWarnings("unused")
public class ParseColorJava {
    private static final int black = Color.parseColor("black");
    private static final int red = Color.RED;
    private static final int white = parseColor("#FFFFFF");
    private static final int blue = parseColor("#FF0000FF");
    private static final int orange = 0xFFFF8855;
    private static final int transparentOrange = 0x88FF8855;
    private static final int fuchsia = parseColor("fuchsia");
    private static final int bad = parseColor("nope");
    private static final int nonConst = parseColor(String.valueOf(bad));
}
