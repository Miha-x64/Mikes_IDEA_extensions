package net.aquadc.mike.plugin.test;

import android.graphics.Color;

import static android.graphics.Color.parseColor;

@SuppressWarnings("unused")
public class ParseColorJava {
    private final int black = Color.parseColor("black");
    private final int white = parseColor("#FFFFFF");
    private final int blue = parseColor("#FF0000FF");
    private final int orange = parseColor("#FFFF8855");
    private final int transparentOrange = parseColor("#88FF8855");
    private final int fuchsia = parseColor("fuchsia");
    private final int bad = parseColor("nope");
    private final int nonConst = parseColor(String.valueOf(bad));
}
