package net.aquadc.mike.plugin.android;

import gnu.trove.TIntArrayList;
import libcore.util.EmptyArray;

import java.awt.geom.Path2D;
import java.util.logging.Level;
import java.util.logging.Logger;

// I can see Path_Delegate and PathParser_Delegate, but they are not stable,
// so I've borrowed these to avoid NoClassDefFoundError.

public final class PathDelegate {
    private final Path2D.Float mPath;
    private float mLastX;
    private float mLastY;

    private PathDelegate() {
        this.mPath = new Path2D.Float();
        this.mLastX = 0.0F;
        this.mLastY = 0.0F;
    }

    public Path2D getJavaShape() {
        return this.mPath;
    }

    public boolean isEmpty() {
        return this.mPath.getPathIterator(null).isDone();
    }

    public void moveTo(float x, float y) {
        this.mPath.moveTo(this.mLastX = x, this.mLastY = y);
    }

    public void rMoveTo(float dx, float dy) {
        dx += this.mLastX;
        dy += this.mLastY;
        this.mPath.moveTo(this.mLastX = dx, this.mLastY = dy);
    }

    public void lineTo(float x, float y) {
        if (this.isEmpty()) {
            this.mPath.moveTo(this.mLastX = 0.0F, this.mLastY = 0.0F);
        }

        this.mPath.lineTo(this.mLastX = x, this.mLastY = y);
    }

    public void rLineTo(float dx, float dy) {
        if (this.isEmpty()) {
            this.mPath.moveTo(this.mLastX = 0.0F, this.mLastY = 0.0F);
        }

        if (!(Math.abs(dx) < 1.0E-4F) || !(Math.abs(dy) < 1.0E-4F)) {
            dx += this.mLastX;
            dy += this.mLastY;
            this.mPath.lineTo(this.mLastX = dx, this.mLastY = dy);
        }
    }

    public void quadTo(float x1, float y1, float x2, float y2) {
        this.mPath.quadTo(x1, y1, this.mLastX = x2, this.mLastY = y2);
    }

    public void rQuadTo(float dx1, float dy1, float dx2, float dy2) {
        if (this.isEmpty()) {
            this.mPath.moveTo(this.mLastX = 0.0F, this.mLastY = 0.0F);
        }

        dx1 += this.mLastX;
        dy1 += this.mLastY;
        dx2 += this.mLastX;
        dy2 += this.mLastY;
        this.mPath.quadTo(dx1, dy1, this.mLastX = dx2, this.mLastY = dy2);
    }

    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        if (this.isEmpty()) {
            this.mPath.moveTo(0f, 0f);
        }

        this.mPath.curveTo(x1, y1, x2, y2, this.mLastX = x3, this.mLastY = y3);
    }

    public void rCubicTo(float dx1, float dy1, float dx2, float dy2, float dx3, float dy3) {
        if (this.isEmpty()) {
            this.mPath.moveTo(this.mLastX = 0.0F, this.mLastY = 0.0F);
        }

        dx1 += this.mLastX;
        dy1 += this.mLastY;
        dx2 += this.mLastX;
        dy2 += this.mLastY;
        dx3 += this.mLastX;
        dy3 += this.mLastY;
        this.mPath.curveTo(dx1, dy1, dx2, dy2, this.mLastX = dx3, this.mLastY = dy3);
    }

    public void close() {
        this.mPath.closePath();
    }

    ///////////////////////////////////////////////////////////////////////////
    /////////////////////////////// PathParser ////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private static final Logger LOGGER = Logger.getLogger("PathParser");

    public static Path2D parse(String pathData, TIntArrayList floatRanges, int usefulPrecision) {
        int start = 0;
        int end = 1;

        PathDelegate path = new PathDelegate();
        float[] current = new float[6];
        char previousCommand = 'm';

        int[] tmp = new int[1];
        float[] buf = EmptyArray.FLOAT;
        for (; end < pathData.length(); start = end++) {
            end = nextStart(pathData, end);
            while (pathData.charAt(start) <= ' ') start++;
            int endTrimmed = end;
            while (pathData.charAt(endTrimmed - 1) <= ' ') endTrimmed--;
            if (endTrimmed - start > 0) {
                int len = endTrimmed - start;
                float[] results = buf.length < len ? (buf = new float[len]) : buf;
                int count = getFloats(pathData, start, endTrimmed, results, tmp, floatRanges, usefulPrecision);
                // TODO report unused or verbose commands
                PathDataNode.addCommand(path, current, previousCommand, previousCommand = pathData.charAt(start), results, count);
            }
        }

        if (end - start == 1 && start < pathData.length())
            PathDataNode.addCommand(path, current, previousCommand, pathData.charAt(start), EmptyArray.FLOAT, 0);

        return path.getJavaShape();
    }

    private static int nextStart(String s, int end) {
        while (end < s.length()) {
            char c = s.charAt(end);
            if (((c - 'A') * (c - 'Z') <= 0 || (c - 'a') * (c - 'z') <= 0) && c != 'e' && c != 'E') {
                return end;
            }

            ++end;
        }

        return end;
    }

    private static boolean extract(
            String input, int start, int end, int[] outEndPosition,
            TIntArrayList floatRanges, int usefulPrecision
    ) {
        int currentIndex = start;
        boolean endWithNegOrDot = false;
        int dotAt = -1;

        loop:
        for (boolean isExponential = false; currentIndex < end; ++currentIndex) {
            boolean isPrevExponential = isExponential;
            isExponential = false;
            switch (input.charAt(currentIndex)) {
                case '\t':
                case '\n':
                case ' ':
                case ',':
                    break loop;
                case '-':
                    if (currentIndex != start && !isPrevExponential) {
                        endWithNegOrDot = true;
                        break loop;
                    }
                    break;
                case '.':
                    if (dotAt < 0) {
                        dotAt = currentIndex;
                    } else {
                        endWithNegOrDot = true;
                        break loop;
                    }
                    break;
                case 'E':
                case 'e':
                    isExponential = true;
            }
        }

        if (usefulPrecision >= 0 && dotAt >= 0 && currentIndex - dotAt - 1 > usefulPrecision) {
            floatRanges.add(start);
            floatRanges.add(currentIndex);
        }

        outEndPosition[0] = currentIndex;
        return endWithNegOrDot;
    }

    private static final byte POS = 0;
    private static final byte FLAG = 1;
    private static final byte DEG = 2;
    private static final byte[] ARC = {
            POS, POS, DEG, FLAG, FLAG, POS, POS
        // (rx ry x-axis-rotation large-arc-flag sweep-flag x y)
    };  // https://www.w3.org/TR/SVG/paths.html#PathDataEllipticalArcCommands
    private static int getFloats(
            String input, int start, int end, float[] results, int[] tmp,
            TIntArrayList floatRanges, int usefulPrecision) {
        char first = input.charAt(start++);
        if (first != 'z' && first != 'Z') {
            try {
                int count = 0;
                boolean arc = first == 'a' || first == 'A';
                while (start < end) {
                    boolean endWithNegOrDot = extract(
                            input, start, end, tmp,
                            floatRanges, arc && ARC[count%ARC.length] != POS ? -1 : usefulPrecision
                    ); // arcs contain degrees and flags, ignore them
                    int endPosition = tmp[0];
                    if (start < endPosition) {
                        results[count++] = Float.parseFloat(input.substring(start, endPosition));
                    }

                    start = endPosition + (endWithNegOrDot ? 0 : 1);
                }

                return count;
            } catch (NumberFormatException e) {
                assert false : "error in parsing \"" + input.substring(start, end) + "\"" + e;

                return 0;
            }
        } else {
            return 0;
        }
    }

    static final class PathDataNode {
        final char mType;
        final float[] mParams;
        final int start, end;

        PathDataNode(char type, float[] params, int start, int end) {
            this.mType = type;
            this.mParams = params;
            this.start = start;
            this.end = end;
        }

        private static void addCommand(PathDelegate path, float[] current, char previousCmd, char cmd, float[] val, int count) {
            int incr = 2;
            float currentX = current[0];
            float currentY = current[1];
            float ctrlPointX = current[2];
            float ctrlPointY = current[3];
            float currentSegmentStartX = current[4];
            float currentSegmentStartY = current[5];
            switch(cmd) {
                case 'A':
                case 'a':
                    incr = 7;
                    break;
                case 'C':
                case 'c':
                    incr = 6;
                    break;
                case 'H':
                case 'V':
                case 'h':
                case 'v':
                    incr = 1;
                    break;
             // case 'L', 'M', 'T', 'l', 'm', 't': incr = 2; break;
                case 'Q':
                case 'S':
                case 'q':
                case 's':
                    incr = 4;
                    break;
                case 'Z':
                case 'z':
                    path.close();
                    currentX = currentSegmentStartX;
                    currentY = currentSegmentStartY;
                    ctrlPointX = currentSegmentStartX;
                    ctrlPointY = currentSegmentStartY;
                    path.moveTo(currentSegmentStartX, currentSegmentStartY);
            }

            for(int k = 0; k < count; k += incr) {
                float reflectiveCtrlPointX;
                float reflectiveCtrlPointY;
                switch(cmd) {
                    case 'A':
                        drawArc(path, currentX, currentY, val[k + 5], val[k + 6], val[k], val[k + 1], val[k + 2], val[k + 3] != 0.0F, val[k + 4] != 0.0F);
                        currentX = val[k + 5];
                        currentY = val[k + 6];
                        ctrlPointX = currentX;
                        ctrlPointY = currentY;
                        break;
                    case 'C':
                        path.cubicTo(val[k], val[k + 1], val[k + 2], val[k + 3], val[k + 4], val[k + 5]);
                        currentX = val[k + 4];
                        currentY = val[k + 5];
                        ctrlPointX = val[k + 2];
                        ctrlPointY = val[k + 3];
                        break;
                    case 'H':
                        path.lineTo(val[k], currentY);
                        currentX = val[k];
                        break;
                    case 'L':
                        path.lineTo(val[k], val[k + 1]);
                        currentX = val[k];
                        currentY = val[k + 1];
                        break;
                    case 'M':
                        currentX = val[k];
                        currentY = val[k + 1];
                        if (k > 0) {
                            path.lineTo(val[k], val[k + 1]);
                        } else {
                            path.moveTo(val[k], val[k + 1]);
                            currentSegmentStartX = currentX;
                            currentSegmentStartY = currentY;
                        }
                        break;
                    case 'Q':
                        path.quadTo(val[k], val[k + 1], val[k + 2], val[k + 3]);
                        ctrlPointX = val[k];
                        ctrlPointY = val[k + 1];
                        currentX = val[k + 2];
                        currentY = val[k + 3];
                        break;
                    case 'S':
                        reflectiveCtrlPointX = currentX;
                        reflectiveCtrlPointY = currentY;
                        if (previousCmd == 'c' || previousCmd == 's' || previousCmd == 'C' || previousCmd == 'S') {
                            reflectiveCtrlPointX = 2.0F * currentX - ctrlPointX;
                            reflectiveCtrlPointY = 2.0F * currentY - ctrlPointY;
                        }

                        path.cubicTo(reflectiveCtrlPointX, reflectiveCtrlPointY, val[k], val[k + 1], val[k + 2], val[k + 3]);
                        ctrlPointX = val[k];
                        ctrlPointY = val[k + 1];
                        currentX = val[k + 2];
                        currentY = val[k + 3];
                        break;
                    case 'T':
                        reflectiveCtrlPointX = currentX;
                        reflectiveCtrlPointY = currentY;
                        if (previousCmd == 'q' || previousCmd == 't' || previousCmd == 'Q' || previousCmd == 'T') {
                            reflectiveCtrlPointX = 2.0F * currentX - ctrlPointX;
                            reflectiveCtrlPointY = 2.0F * currentY - ctrlPointY;
                        }

                        path.quadTo(reflectiveCtrlPointX, reflectiveCtrlPointY, val[k], val[k + 1]);
                        ctrlPointX = reflectiveCtrlPointX;
                        ctrlPointY = reflectiveCtrlPointY;
                        currentX = val[k];
                        currentY = val[k + 1];
                        break;
                    case 'V':
                        path.lineTo(currentX, val[k]);
                        currentY = val[k];
                        break;
                    case 'a':
                        drawArc(path, currentX, currentY, val[k + 5] + currentX, val[k + 6] + currentY, val[k], val[k + 1], val[k + 2], val[k + 3] != 0.0F, val[k + 4] != 0.0F);
                        currentX += val[k + 5];
                        currentY += val[k + 6];
                        ctrlPointX = currentX;
                        ctrlPointY = currentY;
                        break;
                    case 'c':
                        path.rCubicTo(val[k], val[k + 1], val[k + 2], val[k + 3], val[k + 4], val[k + 5]);
                        ctrlPointX = currentX + val[k + 2];
                        ctrlPointY = currentY + val[k + 3];
                        currentX += val[k + 4];
                        currentY += val[k + 5];
                        break;
                    case 'h':
                        path.rLineTo(val[k], 0.0F);
                        currentX += val[k];
                        break;
                    case 'l':
                        path.rLineTo(val[k], val[k + 1]);
                        currentX += val[k];
                        currentY += val[k + 1];
                        break;
                    case 'm':
                        currentX += val[k];
                        currentY += val[k + 1];
                        if (k > 0) {
                            path.rLineTo(val[k], val[k + 1]);
                        } else {
                            path.rMoveTo(val[k], val[k + 1]);
                            currentSegmentStartX = currentX;
                            currentSegmentStartY = currentY;
                        }
                        break;
                    case 'q':
                        path.rQuadTo(val[k], val[k + 1], val[k + 2], val[k + 3]);
                        ctrlPointX = currentX + val[k];
                        ctrlPointY = currentY + val[k + 1];
                        currentX += val[k + 2];
                        currentY += val[k + 3];
                        break;
                    case 's':
                        reflectiveCtrlPointX = 0.0F;
                        reflectiveCtrlPointY = 0.0F;
                        if (previousCmd == 'c' || previousCmd == 's' || previousCmd == 'C' || previousCmd == 'S') {
                            reflectiveCtrlPointX = currentX - ctrlPointX;
                            reflectiveCtrlPointY = currentY - ctrlPointY;
                        }

                        path.rCubicTo(reflectiveCtrlPointX, reflectiveCtrlPointY, val[k], val[k + 1], val[k + 2], val[k + 3]);
                        ctrlPointX = currentX + val[k];
                        ctrlPointY = currentY + val[k + 1];
                        currentX += val[k + 2];
                        currentY += val[k + 3];
                        break;
                    case 't':
                        reflectiveCtrlPointX = 0.0F;
                        reflectiveCtrlPointY = 0.0F;
                        if (previousCmd == 'q' || previousCmd == 't' || previousCmd == 'Q' || previousCmd == 'T') {
                            reflectiveCtrlPointX = currentX - ctrlPointX;
                            reflectiveCtrlPointY = currentY - ctrlPointY;
                        }

                        path.rQuadTo(reflectiveCtrlPointX, reflectiveCtrlPointY, val[k], val[k + 1]);
                        ctrlPointX = currentX + reflectiveCtrlPointX;
                        ctrlPointY = currentY + reflectiveCtrlPointY;
                        currentX += val[k];
                        currentY += val[k + 1];
                        break;
                    case 'v':
                        path.rLineTo(0.0F, val[k]);
                        currentY += val[k];
                }

                previousCmd = cmd;
            }

            current[0] = currentX;
            current[1] = currentY;
            current[2] = ctrlPointX;
            current[3] = ctrlPointY;
            current[4] = currentSegmentStartX;
            current[5] = currentSegmentStartY;
        }

        private static void drawArc(PathDelegate p, float x0, float y0, float x1, float y1, float a, float b, float theta, boolean isMoreThanHalf, boolean isPositiveArc) {
            if (LOGGER.isLoggable(Level.FINE))
                LOGGER.log(Level.FINE, "(" + x0 + "," + y0 + ")-(" + x1 + "," + y1 + ") {" + a + " " + b + "}");
            double thetaD = Math.toRadians(theta);
            double cosTheta = Math.cos(thetaD);
            double sinTheta = Math.sin(thetaD);
            double x0p = ((double)x0 * cosTheta + (double)y0 * sinTheta) / (double)a;
            double y0p = ((double)(-x0) * sinTheta + (double)y0 * cosTheta) / (double)b;
            double x1p = ((double)x1 * cosTheta + (double)y1 * sinTheta) / (double)a;
            double y1p = ((double)(-x1) * sinTheta + (double)y1 * cosTheta) / (double)b;
            if (LOGGER.isLoggable(Level.FINE))
                LOGGER.log(Level.FINE, "unit space (" + x0p + "," + y0p + ")-(" + x1p + "," + y1p + ")");
            double dx = x0p - x1p;
            double dy = y0p - y1p;
            double xm = (x0p + x1p) / 2.0D;
            double ym = (y0p + y1p) / 2.0D;
            double dsq = dx * dx + dy * dy;
            if (dsq == 0.0D) {
                LOGGER.log(Level.FINE, " Points are coincident");
            } else {
                double disc = 1.0D / dsq - 0.25D;
                if (disc < 0.0D) {
                    if (LOGGER.isLoggable(Level.FINE))
                        LOGGER.log(Level.FINE, "Points are too far apart " + dsq);
                    float adjust = (float)(Math.sqrt(dsq) / 1.99999D);
                    drawArc(p, x0, y0, x1, y1, a * adjust, b * adjust, theta, isMoreThanHalf, isPositiveArc);
                } else {
                    double s = Math.sqrt(disc);
                    double sdx = s * dx;
                    double sdy = s * dy;
                    double cx;
                    double cy;
                    if (isMoreThanHalf == isPositiveArc) {
                        cx = xm - sdy;
                        cy = ym + sdx;
                    } else {
                        cx = xm + sdy;
                        cy = ym - sdx;
                    }

                    double eta0 = Math.atan2(y0p - cy, x0p - cx);
                    if (LOGGER.isLoggable(Level.FINE))
                        LOGGER.log(Level.FINE, "eta0 = Math.atan2( " + (y0p - cy) + " , " + (x0p - cx) + ") = " + Math.toDegrees(eta0));
                    double eta1 = Math.atan2(y1p - cy, x1p - cx);
                    if (LOGGER.isLoggable(Level.FINE))
                        LOGGER.log(Level.FINE, "eta1 = Math.atan2( " + (y1p - cy) + " , " + (x1p - cx) + ") = " + Math.toDegrees(eta1));
                    double sweep = eta1 - eta0;
                    if (isPositiveArc != sweep >= 0.0D) {
                        if (sweep > 0.0D) {
                            sweep -= 6.283185307179586D;
                        } else {
                            sweep += 6.283185307179586D;
                        }
                    }

                    cx *= a;
                    cy *= b;
                    double tcx = cx;
                    cx = cx * cosTheta - cy * sinTheta;
                    cy = tcx * sinTheta + cy * cosTheta;
                    if (LOGGER.isLoggable(Level.FINE))
                        LOGGER.log(Level.FINE, "cx, cy, a, b, x0, y0, thetaD, eta0, sweep = " + cx + " , " + cy + " , " + a + " , " + b + " , " + x0 + " , " + y0 + " , " + Math.toDegrees(thetaD) + " , " + Math.toDegrees(eta0) + " , " + Math.toDegrees(sweep));
                    arcToBezier(p, cx, cy, a, b, x0, y0, thetaD, eta0, sweep);
                }
            }
        }

        private static void arcToBezier(PathDelegate p, double cx, double cy, double a, double b, double e1x, double e1y, double theta, double start, double sweep) {
            int numSegments = (int)Math.ceil(Math.abs(sweep * 4.0D / 3.141592653589793D));
            double eta1 = start;
            double cosTheta = Math.cos(theta);
            double sinTheta = Math.sin(theta);
            double cosEta1 = Math.cos(start);
            double sinEta1 = Math.sin(start);
            double ep1x = -a * cosTheta * sinEta1 - b * sinTheta * cosEta1;
            double ep1y = -a * sinTheta * sinEta1 + b * cosTheta * cosEta1;
            double anglePerSegment = sweep / (double)numSegments;

            for(int i = 0; i < numSegments; ++i) {
                double eta2 = eta1 + anglePerSegment;
                double sinEta2 = Math.sin(eta2);
                double cosEta2 = Math.cos(eta2);
                double e2x = cx + a * cosTheta * cosEta2 - b * sinTheta * sinEta2;
                double e2y = cy + a * sinTheta * cosEta2 + b * cosTheta * sinEta2;
                double ep2x = -a * cosTheta * sinEta2 - b * sinTheta * cosEta2;
                double ep2y = -a * sinTheta * sinEta2 + b * cosTheta * cosEta2;
                double tanDiff2 = Math.tan((eta2 - eta1) / 2.0D);
                double alpha = Math.sin(eta2 - eta1) * (Math.sqrt(4.0D + 3.0D * tanDiff2 * tanDiff2) - 1.0D) / 3.0D;
                double q1x = e1x + alpha * ep1x;
                double q1y = e1y + alpha * ep1y;
                double q2x = e2x - alpha * ep2x;
                double q2y = e2y - alpha * ep2y;
                p.cubicTo((float)q1x, (float)q1y, (float)q2x, (float)q2y, (float)e2x, (float)e2y);
                eta1 = eta2;
                e1x = e2x;
                e1y = e2y;
                ep1x = ep2x;
                ep1y = ep2y;
            }

        }
    }
}
