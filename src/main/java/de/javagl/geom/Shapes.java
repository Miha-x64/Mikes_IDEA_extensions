/*
 * www.javagl.de - Geom - Geometry utilities
 *
 * Copyright (c) 2013-2016 Marco Hutter - http://www.javagl.de
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package de.javagl.geom;

import java.awt.Shape;
//import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility methods related to Shapes
 */
public class Shapes
{
    // Mike-REMOVED public static List<Line2D> computeLineSegments(Shape shape, double flatness)
    // Mike-REMOVED public static List<Point2D> computePoints(Shape shape, double flatness, boolean storeOnClose)
    // Mike-REMOVED public static Shape interpolate(Shape shape0, Shape shape1, double alpha)
    // Mike-REMOVED private static void interpolate(double c0[], double c1[], double c[], double alpha, int n)

    /**
     * Compute all closed regions that occur in the given shape, as
     * lists of points, each describing one polygon
     *
     * @param shape The shape
     * @param flatness The flatness for the shape path iterator
     * @return The regions
     */
    static List<List<Point2D>> computeRegions(Shape shape, double flatness) {
        List<List<Point2D>> regions = new ArrayList<>();
        PathIterator pi = shape.getPathIterator(null, flatness);
        float[] coords = new float[6];
        List<Point2D> region = Collections.emptyList();
        while (!pi.isDone()) {
            switch (pi.currentSegment(coords)) {
                case PathIterator.SEG_MOVETO:
                    region = new ArrayList<>();
                    region.add(new Point2D.Float(coords[0], coords[1]));
                    break;

                case PathIterator.SEG_LINETO:
                    region.add(new Point2D.Float(coords[0], coords[1]));
                    break;

                case PathIterator.SEG_CLOSE:
                    regions.add(region);
                    break;

                case PathIterator.SEG_CUBICTO:
                case PathIterator.SEG_QUADTO:
                default:
                    throw new AssertionError(
                            "Invalid segment in flattened path");
            }
            pi.next();
        }
        return regions;
    }

    /**
     * Computes the (signed) area enclosed by the given point list.
     * The area will be positive if the points are ordered 
     * counterclockwise, and negative if the points are ordered
     * clockwise.
     *
     * @param points The points
     * @return The signed area
     */
    static double computeSignedArea(List<? extends Point2D> points)
    {
        double sum0 = 0;
        double sum1 = 0;
        for (int i=0; i<points.size()-1; i++)
        {
            int i0 = i;
            int i1 = i + 1;
            Point2D p0 = points.get(i0);
            Point2D p1 = points.get(i1);
            double x0 = p0.getX();
            double y0 = p0.getY();
            double x1 = p1.getX();
            double y1 = p1.getY();
            sum0 += x0 * y1;
            sum1 += x1 * y0;
        }
        Point2D p0 = points.get(0);
        Point2D pn = points.get(points.size()-1);
        double x0 = p0.getX();
        double y0 = p0.getY();
        double xn = pn.getX();
        double yn = pn.getY();
        sum0 += xn * y0;
        sum1 += x0 * yn;
        return 0.5 * (sum0 - sum1);
    }

    /**
     * Compute the (signed) area that is covered by the given shape.<br>
     * <br>
     * The area will be positive for regions where the points are 
     * ordered counterclockwise, and and negative for regions where 
     * the points are ordered clockwise.
     *
     * @param shape The shape
     * @param flatness The flatness for the path iterator
     * @return The signed area
     */
    public static double computeSignedArea(Shape shape, double flatness)
    {
        double area = 0;
        List<List<Point2D>> regions = computeRegions(shape, flatness);
        for (List<Point2D> region : regions)
        {
            double signedArea = computeSignedArea(region);
            //System.out.println("got "+signedArea+" for "+region);
            area += signedArea;
        }
        return area;
    }

    // Mike-REMOVED public static double computeLength(Shape shape, double flatness)

    /**
     * Computes the list of sub-shapes of the given shape. These are the
     * shapes that are separated in the given shape via SEG_MOVETO or 
     * SEG_CLOSE operations
     *
     * @param shape The input shape
     * @return The sub-shapes
     */
    public static List<Shape> computeSubShapes(Shape shape) {
        List<Shape> result = new ArrayList<>();
        PathIterator pi = shape.getPathIterator(null);
        float[] coords = new float[6];
        float[] previous = new float[2];
        float[] first = new float[2];
        Path2D currentShape = null;
        while (!pi.isDone()) {
            int segment = pi.currentSegment(coords);
            switch (segment) {
                case PathIterator.SEG_MOVETO:
                    if (currentShape != null) {
                        result.add(currentShape);
                        currentShape = null;
                    }
                    previous[0] = coords[0];
                    previous[1] = coords[1];
                    first[0] = coords[0];
                    first[1] = coords[1];
                    break;

                case PathIterator.SEG_CLOSE:
                    if (currentShape != null) {
                        currentShape.closePath();
                        result.add(currentShape);
                        currentShape = null;
                    }
                    previous[0] = first[0];
                    previous[1] = first[1];
                    break;

                case PathIterator.SEG_LINETO:
                    if (currentShape == null) {
                        currentShape = new Path2D.Float();
                        currentShape.moveTo(previous[0], previous[1]);
                    }
                    currentShape.lineTo(coords[0], coords[1]);
                    previous[0] = coords[0];
                    previous[1] = coords[1];
                    break;

                case PathIterator.SEG_QUADTO:
                    if (currentShape == null) {
                        currentShape = new Path2D.Float();
                        currentShape.moveTo(previous[0], previous[1]);
                    }
                    currentShape.quadTo(coords[0], coords[1], coords[2], coords[3]);
                    previous[0] = coords[2];
                    previous[1] = coords[3];
                    break;

                case PathIterator.SEG_CUBICTO:
                    if (currentShape == null) {
                        currentShape = new Path2D.Float();
                        currentShape.moveTo(previous[0], previous[1]);
                    }
                    currentShape.curveTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
                    previous[0] = coords[4];
                    previous[1] = coords[5];
                    break;

                default:
                    // Should never occur
                    throw new AssertionError(
                            "Invalid segment in path!");
            }
            pi.next();
        }
        if (currentShape != null) {
            result.add(currentShape);
            currentShape = null;
        }
        return result;
    }


    /**
     * Private constructor to prevent instantiation
     */
    private Shapes() {
        // Private constructor to prevent instantiation
    }

}
