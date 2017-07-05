/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.IllegalPathStateException;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;

/**
 * @test
 * @bug 8076419
 * @summary Check Path2D copy constructor (trims arrays)
 *          and constructor with zero capacity
 * @run main Path2DCopyConstructor
 */
public class Path2DCopyConstructor {

    private final static float EPSILON = 5e-6f;
    private final static float FLATNESS = 1e-2f;

    private final static AffineTransform at
        = AffineTransform.getScaleInstance(1.3, 2.4);

    private final static Rectangle2D.Double rect2d
        = new Rectangle2D.Double(3.2, 4.1, 5.0, 10.0);

    private final static Point2D.Double pt2d
        = new Point2D.Double(2.0, 2.5);

    public static boolean verbose;

    static void log(String msg) {
        if (verbose) {
            System.out.println(msg);
        }
    }

    public static void main(String argv[]) {
        verbose = (argv.length != 0);

        testEmptyDoublePaths();
        testDoublePaths();

        testEmptyFloatPaths();
        testFloatPaths();

        testEmptyGeneralPath();
        testGeneralPath();
    }

    static void testEmptyDoublePaths() {
        log("\n - Test(Path2D.Double[0]) ---");
        test(() -> new Path2D.Double(Path2D.WIND_NON_ZERO, 0));
    }

    static void testDoublePaths() {
        log("\n - Test(Path2D.Double) ---");
        test(() -> new Path2D.Double());
    }

    static void testEmptyFloatPaths() {
        log("\n - Test(Path2D.Float[0]) ---");
        test(() -> new Path2D.Float(Path2D.WIND_NON_ZERO, 0));
    }

    static void testFloatPaths() {
        log("\n - Test(Path2D.Float) ---");
        test(() -> new Path2D.Float());
    }

    static void testEmptyGeneralPath() {
        log("\n - Test(GeneralPath[0]) ---");
        test(() -> new GeneralPath(Path2D.WIND_NON_ZERO, 0));
    }

    static void testGeneralPath() {
        log("\n - Test(GeneralPath) ---");
        test(() -> new GeneralPath());
    }

    interface PathFactory {
        Path2D makePath();
    }

    static void test(PathFactory pf) {
        log("\n --- test: path(empty) ---");
        test(pf.makePath(), true);
        log("\n\n --- test: path(addMove) ---");
        test(addMove(pf.makePath()), false);
        log("\n\n --- test: path(addMoveAndLines) ---");
        test(addMoveAndLines(pf.makePath()), false);
        log("\n\n --- test: path(addMoveAndQuads) ---");
        test(addMoveAndQuads(pf.makePath()), false);
        log("\n\n --- test: path(addMoveAndCubics) ---");
        test(addMoveAndCubics(pf.makePath()), false);
        log("\n\n --- test: path(addMoveAndClose) ---");
        test(addMoveAndClose(pf.makePath()), false);
    }

    static Path2D addMove(Path2D p2d) {
        p2d.moveTo(1.0, 0.5);
        return p2d;
    }

    static Path2D addMoveAndLines(Path2D p2d) {
        addMove(p2d);
        addLines(p2d);
        return p2d;
    }

    static Path2D addLines(Path2D p2d) {
        for (int i = 0; i < 10; i++) {
            p2d.lineTo(1.1 * i, 2.3 * i);
        }
        return p2d;
    }

    static Path2D addMoveAndCubics(Path2D p2d) {
        addMove(p2d);
        addCubics(p2d);
        return p2d;
    }

    static Path2D addCubics(Path2D p2d) {
        for (int i = 0; i < 10; i++) {
            p2d.curveTo(1.1 * i, 1.2 * i, 1.3 * i, 1.4 * i, 1.5 * i, 1.6 * i);
        }
        return p2d;
    }

    static Path2D addMoveAndQuads(Path2D p2d) {
        addMove(p2d);
        addQuads(p2d);
        return p2d;
    }

    static Path2D addQuads(Path2D p2d) {
        for (int i = 0; i < 10; i++) {
            p2d.quadTo(1.1 * i, 1.2 * i, 1.3 * i, 1.4 * i);
        }
        return p2d;
    }

    static Path2D addMoveAndClose(Path2D p2d) {
        addMove(p2d);
        addClose(p2d);
        return p2d;
    }

    static Path2D addClose(Path2D p2d) {
        p2d.closePath();
        return p2d;
    }

    static void test(Path2D p2d, boolean isEmpty) {
        testEqual(new Path2D.Float(p2d), p2d);
        testEqual(new Path2D.Double(p2d), p2d);
        testEqual(new GeneralPath(p2d), p2d);

        testIterator(new Path2D.Float(p2d), p2d);
        testIterator(new Path2D.Double(p2d), p2d);
        testIterator((Path2D) p2d.clone(), p2d);

        testFlattening(new Path2D.Float(p2d), p2d);
        testFlattening(new Path2D.Double(p2d), p2d);
        testFlattening((Path2D) p2d.clone(), p2d);

        testAddMove(new Path2D.Float(p2d));
        testAddMove(new Path2D.Double(p2d));
        testAddMove((Path2D) p2d.clone());

        // These should expect exception if empty
        testAddLine(new Path2D.Float(p2d), isEmpty);
        testAddLine(new Path2D.Double(p2d), isEmpty);
        testAddLine((Path2D) p2d.clone(), isEmpty);

        testAddQuad(new Path2D.Float(p2d), isEmpty);
        testAddQuad(new Path2D.Double(p2d), isEmpty);
        testAddQuad((Path2D) p2d.clone(), isEmpty);

        testAddCubic(new Path2D.Float(p2d), isEmpty);
        testAddCubic(new Path2D.Double(p2d), isEmpty);
        testAddCubic((Path2D) p2d.clone(), isEmpty);

        testAddClose(new Path2D.Float(p2d), isEmpty);
        testAddClose(new Path2D.Double(p2d), isEmpty);
        testAddClose((Path2D) p2d.clone(), isEmpty);

        testGetBounds(new Path2D.Float(p2d), p2d);
        testGetBounds(new Path2D.Double(p2d), p2d);
        testGetBounds((Path2D) p2d.clone(), p2d);

        testTransform(new Path2D.Float(p2d));
        testTransform(new Path2D.Double(p2d));
        testTransform((Path2D) p2d.clone());

        testIntersect(new Path2D.Float(p2d), p2d);
        testIntersect(new Path2D.Double(p2d), p2d);
        testIntersect((Path2D) p2d.clone(), p2d);

        testContains(new Path2D.Float(p2d), p2d);
        testContains(new Path2D.Double(p2d), p2d);
        testContains((Path2D) p2d.clone(), p2d);

        testGetCurrentPoint(new Path2D.Float(p2d), p2d);
        testGetCurrentPoint(new Path2D.Double(p2d), p2d);
        testGetCurrentPoint((Path2D) p2d.clone(), p2d);
    }

    static void testEqual(Path2D pathA, Path2D pathB) {
        final PathIterator itA = pathA.getPathIterator(null);
        final PathIterator itB = pathB.getPathIterator(null);

        float[] coordsA = new float[6];
        float[] coordsB = new float[6];

        int n = 0;
        for (; !itA.isDone() && !itB.isDone(); itA.next(), itB.next(), n++) {
            int typeA = itA.currentSegment(coordsA);
            int typeB = itB.currentSegment(coordsB);

            if (typeA != typeB) {
                throw new IllegalStateException("Path-segment[" + n + "] "
                    + " type are not equals [" + typeA + "|" + typeB + "] !");
            }
            if (!equalsArray(coordsA, coordsB, getLength(typeA))) {
                throw new IllegalStateException("Path-segment[" + n + "] coords"
                    + " are not equals [" + Arrays.toString(coordsA) + "|"
                    + Arrays.toString(coordsB) + "] !");
            }
        }
        if (!itA.isDone() || !itB.isDone()) {
            throw new IllegalStateException("Paths do not have same lengths !");
        }
        log("testEqual: " + n + " segments.");
    }

    static void testIterator(Path2D pathA, Path2D pathB) {
        final PathIterator itA = pathA.getPathIterator(at);
        final PathIterator itB = pathB.getPathIterator(at);

        float[] coordsA = new float[6];
        float[] coordsB = new float[6];

        int n = 0;
        for (; !itA.isDone() && !itB.isDone(); itA.next(), itB.next(), n++) {
            int typeA = itA.currentSegment(coordsA);
            int typeB = itB.currentSegment(coordsB);

            if (typeA != typeB) {
                throw new IllegalStateException("Path-segment[" + n + "] "
                    + "type are not equals [" + typeA + "|" + typeB + "] !");
            }
            // Take care of floating-point precision:
            if (!equalsArrayEps(coordsA, coordsB, getLength(typeA))) {
                throw new IllegalStateException("Path-segment[" + n + "] coords"
                    + " are not equals [" + Arrays.toString(coordsA) + "|"
                    + Arrays.toString(coordsB) + "] !");
            }
        }
        if (!itA.isDone() || !itB.isDone()) {
            throw new IllegalStateException("Paths do not have same lengths !");
        }
        log("testIterator: " + n + " segments.");
    }

    static void testFlattening(Path2D pathA, Path2D pathB) {
        final PathIterator itA = pathA.getPathIterator(at, FLATNESS);
        final PathIterator itB = pathB.getPathIterator(at, FLATNESS);

        float[] coordsA = new float[6];
        float[] coordsB = new float[6];

        int n = 0;
        for (; !itA.isDone() && !itB.isDone(); itA.next(), itB.next(), n++) {
            int typeA = itA.currentSegment(coordsA);
            int typeB = itB.currentSegment(coordsB);

            if (typeA != typeB) {
                throw new IllegalStateException("Path-segment[" + n + "] "
                    + "type are not equals [" + typeA + "|" + typeB + "] !");
            }
            // Take care of floating-point precision:
            if (!equalsArrayEps(coordsA, coordsB, getLength(typeA))) {
                throw new IllegalStateException("Path-segment[" + n + "] coords"
                    + " are not equals [" + Arrays.toString(coordsA) + "|"
                    + Arrays.toString(coordsB) + "] !");
            }
        }
        if (!itA.isDone() || !itB.isDone()) {
            throw new IllegalStateException("Paths do not have same lengths !");
        }
        log("testFlattening: " + n + " segments.");
    }

    static void testAddMove(Path2D pathA) {
        addMove(pathA);
        log("testAddMove: passed.");
    }

    static void testAddLine(Path2D pathA, boolean isEmpty) {
        try {
            addLines(pathA);
        }
        catch (IllegalPathStateException ipse) {
            if (isEmpty) {
                log("testAddLine: passed "
                    + "(expected IllegalPathStateException catched).");
                return;
            } else {
                throw ipse;
            }
        }
        if (isEmpty) {
            throw new IllegalStateException("IllegalPathStateException not thrown !");
        }
        log("testAddLine: passed.");
    }

    static void testAddQuad(Path2D pathA, boolean isEmpty) {
        try {
            addQuads(pathA);
        }
        catch (IllegalPathStateException ipse) {
            if (isEmpty) {
                log("testAddQuad: passed "
                    + "(expected IllegalPathStateException catched).");
                return;
            } else {
                throw ipse;
            }
        }
        if (isEmpty) {
            throw new IllegalStateException("IllegalPathStateException not thrown !");
        }
        log("testAddQuad: passed.");
    }

    static void testAddCubic(Path2D pathA, boolean isEmpty) {
        try {
            addCubics(pathA);
        }
        catch (IllegalPathStateException ipse) {
            if (isEmpty) {
                log("testAddCubic: passed "
                    + "(expected IllegalPathStateException catched).");
                return;
            } else {
                throw ipse;
            }
        }
        if (isEmpty) {
            throw new IllegalStateException("IllegalPathStateException not thrown !");
        }
        log("testAddCubic: passed.");
    }

    static void testAddClose(Path2D pathA, boolean isEmpty) {
        try {
            addClose(pathA);
        }
        catch (IllegalPathStateException ipse) {
            if (isEmpty) {
                log("testAddClose: passed "
                    + "(expected IllegalPathStateException catched).");
                return;
            } else {
                throw ipse;
            }
        }
        if (isEmpty) {
            throw new IllegalStateException("IllegalPathStateException not thrown !");
        }
        log("testAddClose: passed.");
    }

    static void testGetBounds(Path2D pathA, Path2D pathB) {
        final Rectangle rA = pathA.getBounds();
        final Rectangle rB = pathB.getBounds();

        if (!rA.equals(rB)) {
            throw new IllegalStateException("Bounds are not equals [" + rA
                + "|" + rB + "] !");
        }
        final Rectangle2D r2dA = pathA.getBounds2D();
        final Rectangle2D r2dB = pathB.getBounds2D();

        if (!equalsRectangle2D(r2dA, r2dB)) {
            throw new IllegalStateException("Bounds2D are not equals ["
                + r2dA + "|" + r2dB + "] !");
        }
        log("testGetBounds: passed.");
    }

    static void testTransform(Path2D pathA) {
        pathA.transform(at);
        log("testTransform: passed.");
    }

    static void testIntersect(Path2D pathA, Path2D pathB) {
        boolean resA = pathA.intersects(rect2d);
        boolean resB = pathB.intersects(rect2d);
        if (resA != resB) {
            throw new IllegalStateException("Intersects(rect2d) are not equals ["
                + resA + "|" + resB + "] !");
        }
        resA = pathA.intersects(1.0, 2.0, 13.0, 17.0);
        resB = pathB.intersects(1.0, 2.0, 13.0, 17.0);
        if (resA != resB) {
            throw new IllegalStateException("Intersects(doubles) are not equals ["
                + resA + "|" + resB + "] !");
        }
        log("testIntersect: passed.");
    }

    static void testContains(Path2D pathA, Path2D pathB) {
        boolean resA = pathA.contains(pt2d);
        boolean resB = pathB.contains(pt2d);
        if (resA != resB) {
            throw new IllegalStateException("Contains(pt) are not equals ["
                + resA + "|" + resB + "] !");
        }
        resA = pathA.contains(pt2d.getX(), pt2d.getY());
        resB = pathB.contains(pt2d.getX(), pt2d.getY());
        if (resA != resB) {
            throw new IllegalStateException("Contains(x,y) are not equals ["
                + resA + "|" + resB + "] !");
        }
        resA = pathA.contains(rect2d);
        resB = pathB.contains(rect2d);
        if (resA != resB) {
            throw new IllegalStateException("Contains(rect2d) are not equals ["
                + resA + "|" + resB + "] !");
        }
        resA = pathA.contains(1.0, 2.0, 13.0, 17.0);
        resB = pathB.contains(1.0, 2.0, 13.0, 17.0);
        if (resA != resB) {
            throw new IllegalStateException("Contains(doubles) are not equals ["
                + resA + "|" + resB + "] !");
        }
        log("testContains: passed.");
    }

    static void testGetCurrentPoint(Path2D pathA, Path2D pathB) {
        final Point2D ptA = pathA.getCurrentPoint();
        final Point2D ptB = pathA.getCurrentPoint();
        if (((ptA == null) && (ptB != null))
            || ((ptA != null) && !ptA.equals(ptB)))
        {
            throw new IllegalStateException("getCurrentPoint() are not equals ["
                + ptA + "|" + ptB + "] !");
        }
        log("testGetCurrentPoint: passed.");
    }

    static int getLength(int type) {
        switch(type) {
            case PathIterator.SEG_CUBICTO:
                return 6;
            case PathIterator.SEG_QUADTO:
                return 4;
            case PathIterator.SEG_LINETO:
            case PathIterator.SEG_MOVETO:
                return 2;
            case PathIterator.SEG_CLOSE:
                return 0;
            default:
                throw new IllegalStateException("Invalid type: " + type);
        }
    }


    // Custom equals methods ---

    public static boolean equalsArray(float[] a, float[] a2, final int len) {
        for (int i = 0; i < len; i++) {
            if (Float.floatToIntBits(a[i]) != Float.floatToIntBits(a2[i])) {
                return false;
            }
        }
        return true;
    }

    static boolean equalsArrayEps(float[] a, float[] a2, final int len) {
        for (int i = 0; i < len; i++) {
            if (!equalsEps(a[i], a2[i])) {
                return false;
            }
        }

        return true;
    }

    static boolean equalsRectangle2D(Rectangle2D a, Rectangle2D b) {
        if (a == b) {
            return true;
        }
        return equalsEps(a.getX(), b.getX())
            && equalsEps(a.getY(), b.getY())
            && equalsEps(a.getWidth(), b.getWidth())
            && equalsEps(a.getHeight(), b.getHeight());
    }

    static boolean equalsEps(float a, float b) {
        return (Math.abs(a - b) <= EPSILON);
    }

    static boolean equalsEps(double a, double b) {
        return (Math.abs(a - b) <= EPSILON);
    }
}
