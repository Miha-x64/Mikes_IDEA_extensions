package android.graphics;

public class PixelFormat {

    public static final int UNKNOWN = 0;

    /**
     * System chooses a format that supports translucency (many alpha bits)
     */
    public static final int TRANSLUCENT = -3;

    /**
     * System chooses a format that supports transparency
     * (at least 1 alpha bit)
     */
    public static final int TRANSPARENT = -2;

    /**
     * System chooses an opaque format (no alpha bits required)
     */
    public static final int OPAQUE = -1;
}
