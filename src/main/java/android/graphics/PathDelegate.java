package android.graphics;

import com.intellij.openapi.util.TextRange;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.aquadc.mike.plugin.android.res.Cmd;
import org.jetbrains.annotations.Nullable;

import java.awt.geom.Path2D;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static java.math.BigDecimal.ZERO;
import static net.aquadc.mike.plugin.android.res.PathParseUtilKt.*;

// I can see Path_Delegate and PathParser_Delegate, but they are not stable,
// so I've borrowed these to avoid NoClassDefFoundError,
// and seriously reworked it to satisfy my needs.

// Spec: https://www.w3.org/TR/SVG/paths.html

public final class PathDelegate {
    private static final BigDecimal[] EMPTY = new BigDecimal[0];
    private static final BigDecimal TWO = new BigDecimal(2);
    private final List<? super Path2D.Float> paths;
    private final int windingRule;
    private Path2D.Float currentPath;
    private float mLastX;
    private float mLastY;

    private PathDelegate(List<? super Path2D.Float> paths, int windingRule) {
        this.paths = paths;
        this.windingRule = windingRule;
    }
    private static boolean isEmpty(Path2D.Float path) {
        return path.getPathIterator(null).isDone();
    }
    private Path2D.Float currentPath() {
        return currentPath == null ? newPath() : currentPath;
    }
    private Path2D.Float newPath() {
        /*if (paths == null) {
            return currentPath == null ? (currentPath = new Path2D.Float()) : currentPath;
        } else {*/
            paths.add(currentPath = new Path2D.Float(windingRule));
            return currentPath;
//        }
    }

    public void moveTo(float x, float y) {
        newPath().moveTo(this.mLastX = x, this.mLastY = y);
    }

    public void rMoveTo(float dx, float dy) {
        newPath().moveTo(this.mLastX += dx, this.mLastY += dy);
    }

    public void lineTo(float x, float y) {
        Path2D.Float path = currentPath();
        if (isEmpty(path)) {
            path.moveTo(this.mLastX = 0.0F, this.mLastY = 0.0F);
        }

        path.lineTo(this.mLastX = x, this.mLastY = y);
    }

    public void rLineTo(float dx, float dy) {
        Path2D.Float path = currentPath();
        if (isEmpty(path)) {
            path.moveTo(this.mLastX = 0.0F, this.mLastY = 0.0F);
        }

        if (!(Math.abs(dx) < 1.0E-4F) || !(Math.abs(dy) < 1.0E-4F)) {
            path.lineTo(this.mLastX += dx, this.mLastY += dy);
        }
    }

    public void quadTo(float x1, float y1, float x2, float y2) {
        currentPath().quadTo(x1, y1, this.mLastX = x2, this.mLastY = y2);
    }

    public void rQuadTo(float dx1, float dy1, float dx2, float dy2) {
        Path2D.Float path = currentPath();
        if (isEmpty(path)) {
            path.moveTo(this.mLastX = 0.0F, this.mLastY = 0.0F);
        }

        quadTo(mLastX + dx1, mLastY + dy1, mLastX + dx2, mLastY + dy2);
    }

    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        Path2D.Float path = currentPath();
        if (isEmpty(path)) {
            path.moveTo(0f, 0f);
        }

        path.curveTo(x1, y1, x2, y2, this.mLastX = x3, this.mLastY = y3);
    }

    public void rCubicTo(float dx1, float dy1, float dx2, float dy2, float dx3, float dy3) {
        Path2D.Float path = currentPath();
        if (isEmpty(path)) {
            path.moveTo(this.mLastX = 0.0F, this.mLastY = 0.0F);
        }

        cubicTo(mLastX + dx1, mLastY + dy1, mLastX + dx2, mLastY + dy2, mLastX + dx3, mLastY + dy3);
    }

    public void close() {
        currentPath().closePath();
        /*if (paths != null)*/ currentPath = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    /////////////////////////////// PathParser ////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Parses {@param pathData} SVG path data sub-paths into {@param paths}
     */
    public static void parse(
            String pathData,
            List<? super Path2D.Float> paths,
            List<Cmd> cmds,
            @Nullable IntArrayList pathStarts, IntArrayList floatRanges, @Nullable FloatArrayList endPositions,
            boolean evenOdd
    ) throws PathError {
        PathDelegate delegate = new PathDelegate(paths, evenOdd ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
        BigDecimal[] state = new BigDecimal[6];
        Arrays.fill(state, ZERO);
        char prevCmd = 'm';
        Path2D.Float prevPath = null;

        int[] tmp = new int[1];
        BigDecimal[] results = EMPTY;
        int start, end, pdLen;
        for (start = 0, end = 1, pdLen = pathData.length(); end < pdLen; start = end++) {
            end = nextCmd(pathData, end);
            while (start < pdLen && pathData.charAt(start) <= ' ') start++;
            int endTrimmed = end;
            while (endTrimmed >= start && pathData.charAt(endTrimmed - 1) <= ' ') endTrimmed--;

            int len = endTrimmed - start;
            if (len > 0) {
                if (results.length < len) results = new BigDecimal[len / 2 + 1/*bad estimation but it sort of works*/];
                int rangesOffset = floatRanges.size();
                int count = getFloats(pathData, start, endTrimmed, results, tmp, floatRanges);
                char next = pathData.charAt(start);
                float lastX = delegate.mLastX, lastY = delegate.mLastY;
                if ((prevCmd == 'z' || prevCmd == 'Z') && (next != 'm' && next != 'M'))
                    delegate.moveTo(state[0].floatValue(), state[1].floatValue());
                addCommand(delegate, state, prevCmd, start, results, count, pathData, cmds, floatRanges, rangesOffset);
                prevCmd = next;
                if (delegate.currentPath != null && prevPath != delegate.currentPath) {
                    if (pathStarts != null) pathStarts.add(start);
                    if (endPositions != null && delegate.paths.size() > 1) add(endPositions, lastX, lastY);
                }
                prevPath = delegate.currentPath;
            }
        }

        if (end - start == 1 && start < pdLen) { // 'z' case, one last command
            addCommand(delegate, state, prevCmd, start, EMPTY, 0, pathData, cmds, floatRanges, -1);
        }
        if (pathStarts != null) pathStarts.add(pdLen);
        if (endPositions != null) add(endPositions, delegate.mLastX, delegate.mLastY);
    }
    private static void add(FloatArrayList into, float x, float y) {
        into.add(x);
        into.add(y);
    }

    private static int nextCmd(String s, int end) {
        for (int len = s.length(); end < len; end++) {
            char c = s.charAt(end);
            if (isCommand(c)) {
                return end;
            }
        }

        return end;
    }

    private static boolean extract(String input, int start, int end, int[] outEndPosition) {
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

        outEndPosition[0] = currentIndex;
        return endWithNegOrDot;
    }

    private static int getFloats(
            String input, int start, int end, BigDecimal[] results, int[] tmp,
            IntArrayList floatRanges
    ) throws PathError {
        start++; // skip cmd
        try {
            int count = 0;
            while (start < end) {
                boolean endWithNegOrDot = extract(input, start, end, tmp);
                int endPosition = tmp[0];
                if (start < endPosition) {
                    results[count++] = new BigDecimal(input.substring(start, endPosition));
                    floatRanges.add(start);
                    floatRanges.add(endPosition);
                }
                start = endPosition + (endWithNegOrDot ? 0 : 1);
            }
            return count;
        } catch (NumberFormatException e) {
            throw new PathError("Can't parseFloat", new TextRange(start, end));
        }
    }

    private static void addCommand(
            PathDelegate path, BigDecimal[] current, char previousCmd, int position, BigDecimal[] val, int count,
            String pathData, List<Cmd> cmds, IntArrayList floatRanges, int rangesOffset
    ) throws PathError {
        char cmd = pathData.charAt(position);
        Cmd.Param[] params = Cmd.paramsOf(cmd);
        if (params == null) {
            throw new PathError("Unsupported command '" + cmd + "'", TextRange.from(position, 1));
        }
        BigDecimal currentX = current[0];
        BigDecimal currentY = current[1];
        BigDecimal ctrlPointX = current[2];
        BigDecimal ctrlPointY = current[3];
        BigDecimal currentSegmentStartX = current[4];
        BigDecimal currentSegmentStartY = current[5];
        int incr = params.length;
        if (incr == 0) { // Zz
            path.close();
            currentX = currentSegmentStartX;
            currentY = currentSegmentStartY;
            ctrlPointX = currentSegmentStartX;
            ctrlPointY = currentSegmentStartY;
            path.mLastX = currentSegmentStartX.floatValue();
            path.mLastY = currentSegmentStartY.floatValue();
            cmds.add(new Cmd(currentX, currentY, cmd, EMPTY, pathData, floatRanges, -1));
            count = 0; // guard against passing arguments to Zz: skip the following loop
        }

        for (int k = 0; k < count; k += incr) {
            if (k + incr >= val.length) {
                throw new PathError(
                    String.format(
                        "Missing arguments for '%s': provided %d, required %d %s",
                        cmd, count % params.length, params.length, Arrays.toString(params)
                    ),
                    new TextRange(rangesOffset + 2 * k, floatRanges.getInt(rangesOffset + 2 * count - 1))
                );
            }
            cmds.add(new Cmd(currentX, currentY, cmd, Arrays.copyOfRange(val, k, k + incr),pathData, floatRanges, rangesOffset + 2 * k));
            BigDecimal reflectiveCtrlPointX;
            BigDecimal reflectiveCtrlPointY;
            switch(cmd) {
                case 'A':
                    drawArc(path, currentX.floatValue(), currentY.floatValue(), val[k + 5].floatValue(), val[k + 6].floatValue(), val[k].floatValue(), val[k + 1].floatValue(), val[k + 2].floatValue(), val[k + 3].signum() != 0, val[k + 4].signum() != 0);
                    currentX = val[k + 5];
                    currentY = val[k + 6];
                    ctrlPointX = currentX;
                    ctrlPointY = currentY;
                    break;
                case 'C':
                    path.cubicTo(val[k].floatValue(), val[k + 1].floatValue(), val[k + 2].floatValue(), val[k + 3].floatValue(), val[k + 4].floatValue(), val[k + 5].floatValue());
                    currentX = val[k + 4];
                    currentY = val[k + 5];
                    ctrlPointX = val[k + 2];
                    ctrlPointY = val[k + 3];
                    break;
                case 'H':
                    path.lineTo(val[k].floatValue(), currentY.floatValue());
                    //                               ^^^^^^^^- This should be 0 according to the spec but works differently EVERYWHERE
                    currentX = val[k];
                    break;
                case 'L':
                    path.lineTo(val[k].floatValue(), val[k + 1].floatValue());
                    currentX = val[k];
                    currentY = val[k + 1];
                    break;
                case 'M':
                    currentX = val[k];
                    currentY = val[k + 1];
                    path.moveTo(val[k].floatValue(), val[k + 1].floatValue());
                    currentSegmentStartX = currentX;
                    currentSegmentStartY = currentY;
                    cmd = 'L';
                    break;
                case 'Q':
                    path.quadTo(val[k].floatValue(), val[k + 1].floatValue(), val[k + 2].floatValue(), val[k + 3].floatValue());
                    ctrlPointX = val[k];
                    ctrlPointY = val[k + 1];
                    currentX = val[k + 2];
                    currentY = val[k + 3];
                    break;
                case 'S':
                    reflectiveCtrlPointX = currentX;
                    reflectiveCtrlPointY = currentY;
                    if (previousCmd == 'c' || previousCmd == 's' || previousCmd == 'C' || previousCmd == 'S') {
                        reflectiveCtrlPointX = TWO.multiply(currentX).subtract(ctrlPointX);
                        reflectiveCtrlPointY = TWO.multiply(currentY).subtract(ctrlPointY);
                    }

                    path.cubicTo(reflectiveCtrlPointX.floatValue(), reflectiveCtrlPointY.floatValue(), val[k].floatValue(), val[k + 1].floatValue(), val[k + 2].floatValue(), val[k + 3].floatValue());
                    ctrlPointX = val[k];
                    ctrlPointY = val[k + 1];
                    currentX = val[k + 2];
                    currentY = val[k + 3];
                    break;
                case 'T':
                    reflectiveCtrlPointX = currentX;
                    reflectiveCtrlPointY = currentY;
                    if (previousCmd == 'q' || previousCmd == 't' || previousCmd == 'Q' || previousCmd == 'T') {
                        reflectiveCtrlPointX = TWO.multiply(currentX).subtract(ctrlPointX);
                        reflectiveCtrlPointY = TWO.multiply(currentY).subtract(ctrlPointY);
                    }

                    path.quadTo(reflectiveCtrlPointX.floatValue(), reflectiveCtrlPointY.floatValue(), val[k].floatValue(), val[k + 1].floatValue());
                    ctrlPointX = reflectiveCtrlPointX;
                    ctrlPointY = reflectiveCtrlPointY;
                    currentX = val[k];
                    currentY = val[k + 1];
                    break;
                case 'V':
                    path.lineTo(currentX.floatValue(), val[k].floatValue());
                    //          ^^^^^^^^- This should be 0 according to the spec but works differently EVERYWHERE
                    currentY = val[k];
                    break;
                case 'a':
                    drawArc(path, currentX.floatValue(), currentY.floatValue(), val[k + 5].add(currentX).floatValue(), val[k + 6].add(currentY).floatValue(), val[k].floatValue(), val[k + 1].floatValue(), val[k + 2].floatValue(), val[k + 3].signum() != 0, val[k + 4].signum() != 0);
                    currentX = currentX.add(val[k + 5]);
                    currentY = currentY.add(val[k + 6]);
                    ctrlPointX = currentX;
                    ctrlPointY = currentY;
                    break;
                case 'c':
                    path.rCubicTo(val[k].floatValue(), val[k + 1].floatValue(), val[k + 2].floatValue(), val[k + 3].floatValue(), val[k + 4].floatValue(), val[k + 5].floatValue());
                    ctrlPointX = currentX.add(val[k + 2]);
                    ctrlPointY = currentY.add(val[k + 3]);
                    currentX = currentX.add(val[k + 4]);
                    currentY = currentY.add(val[k + 5]);
                    break;
                case 'h':
                    path.rLineTo(val[k].floatValue(), 0f);
                    currentX = currentX.add(val[k]);
                    break;
                case 'l':
                    path.rLineTo(val[k].floatValue(), val[k + 1].floatValue());
                    currentX = currentX.add(val[k]);
                    currentY = currentY.add(val[k + 1]);
                    break;
                case 'm':
                    currentX = currentX.add(val[k]);
                    currentY = currentY.add(val[k + 1]);
                    path.rMoveTo(val[k].floatValue(), val[k + 1].floatValue());
                    currentSegmentStartX = currentX;
                    currentSegmentStartY = currentY;
                    cmd = 'l';
                    break;
                case 'q':
                    path.rQuadTo(val[k].floatValue(), val[k + 1].floatValue(), val[k + 2].floatValue(), val[k + 3].floatValue());
                    ctrlPointX = currentX.add(val[k]);
                    ctrlPointY = currentY.add(val[k + 1]);
                    currentX = currentX.add(val[k + 2]);
                    currentY = currentY.add(val[k + 3]);
                    break;
                case 's':
                    reflectiveCtrlPointX = ZERO;
                    reflectiveCtrlPointY = ZERO;
                    if (previousCmd == 'c' || previousCmd == 's' || previousCmd == 'C' || previousCmd == 'S') {
                        reflectiveCtrlPointX = currentX.subtract(ctrlPointX);
                        reflectiveCtrlPointY = currentY.subtract(ctrlPointY);
                    }

                    path.rCubicTo(reflectiveCtrlPointX.floatValue(), reflectiveCtrlPointY.floatValue(), val[k].floatValue(), val[k + 1].floatValue(), val[k + 2].floatValue(), val[k + 3].floatValue());
                    ctrlPointX = currentX.add(val[k]);
                    ctrlPointY = currentY.add(val[k + 1]);
                    currentX = currentX.add(val[k + 2]);
                    currentY = currentY.add(val[k + 3]);
                    break;
                case 't':
                    reflectiveCtrlPointX = ZERO;
                    reflectiveCtrlPointY = ZERO;
                    if (previousCmd == 'q' || previousCmd == 't' || previousCmd == 'Q' || previousCmd == 'T') {
                        reflectiveCtrlPointX = currentX.subtract(ctrlPointX);
                        reflectiveCtrlPointY = currentY.subtract(ctrlPointY);
                    }

                    path.rQuadTo(reflectiveCtrlPointX.floatValue(), reflectiveCtrlPointY.floatValue(), val[k].floatValue(), val[k + 1].floatValue());
                    ctrlPointX = currentX.add(reflectiveCtrlPointX);
                    ctrlPointY = currentY.add(reflectiveCtrlPointY);
                    currentX = currentX.add(val[k]);
                    currentY = currentY.add(val[k + 1]);
                    break;
                case 'v':
                    path.rLineTo(0.0F, val[k].floatValue());
                    currentY = currentY.add(val[k]);
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

    private static void drawArc(
            PathDelegate p,
            float x0, float y0, float x1, float y1, float a, float b, float theta,
            boolean isMoreThanHalf, boolean isPositiveArc) {
        double thetaD = Math.toRadians(theta);
        double cosTheta = Math.cos(thetaD);
        double sinTheta = Math.sin(thetaD);
        double x0p = ((double)x0 * cosTheta + (double)y0 * sinTheta) / (double)a;
        double y0p = ((double)(-x0) * sinTheta + (double)y0 * cosTheta) / (double)b;
        double x1p = ((double)x1 * cosTheta + (double)y1 * sinTheta) / (double)a;
        double y1p = ((double)(-x1) * sinTheta + (double)y1 * cosTheta) / (double)b;
        double dx = x0p - x1p;
        double dy = y0p - y1p;
        double xm = (x0p + x1p) / 2.0D;
        double ym = (y0p + y1p) / 2.0D;
        double dsq = dx * dx + dy * dy;
        if (dsq == 0.0D) // Points are coincident
            return;

        double disc = 1.0D / dsq - 0.25D;
        if (disc < 0.0D) {
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
            double eta1 = Math.atan2(y1p - cy, x1p - cx);
            double sweep = eta1 - eta0;
            if (isPositiveArc != sweep >= 0.0D) {
                if (sweep > 0.0D) sweep -= 2 * Math.PI;
                else sweep += 2 * Math.PI;
            }

            cx *= a;
            cy *= b;
            double tcx = cx;
            cx = cx * cosTheta - cy * sinTheta;
            cy = tcx * sinTheta + cy * cosTheta;
            arcToBezier(p, cx, cy, a, b, x0, y0, thetaD, eta0, sweep);
        }
    }

    private static void arcToBezier(
            PathDelegate p,
            double cx, double cy, double a, double b, double e1x, double e1y, double theta, double start, double sweep
    ) {
        int numSegments = (int)Math.ceil(Math.abs(sweep * 4.0D / Math.PI));
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

    public static final class PathError extends Exception {
        public final TextRange at;
        PathError(String message, TextRange at) {
            super(message);
            this.at = at;
        }
    }
}
