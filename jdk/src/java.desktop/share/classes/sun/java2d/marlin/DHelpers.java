/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.java2d.marlin;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sqrt;
import static java.lang.Math.cbrt;
import static java.lang.Math.acos;

final class DHelpers implements MarlinConst {

    private DHelpers() {
        throw new Error("This is a non instantiable class");
    }

    static boolean within(final double x, final double y, final double err) {
        final double d = y - x;
        return (d <= err && d >= -err);
    }

    static int quadraticRoots(final double a, final double b,
                              final double c, double[] zeroes, final int off)
    {
        int ret = off;
        double t;
        if (a != 0.0d) {
            final double dis = b*b - 4*a*c;
            if (dis > 0.0d) {
                final double sqrtDis = Math.sqrt(dis);
                // depending on the sign of b we use a slightly different
                // algorithm than the traditional one to find one of the roots
                // so we can avoid adding numbers of different signs (which
                // might result in loss of precision).
                if (b >= 0.0d) {
                    zeroes[ret++] = (2.0d * c) / (-b - sqrtDis);
                    zeroes[ret++] = (-b - sqrtDis) / (2.0d * a);
                } else {
                    zeroes[ret++] = (-b + sqrtDis) / (2.0d * a);
                    zeroes[ret++] = (2.0d * c) / (-b + sqrtDis);
                }
            } else if (dis == 0.0d) {
                t = (-b) / (2.0d * a);
                zeroes[ret++] = t;
            }
        } else {
            if (b != 0.0d) {
                t = (-c) / b;
                zeroes[ret++] = t;
            }
        }
        return ret - off;
    }

    // find the roots of g(t) = d*t^3 + a*t^2 + b*t + c in [A,B)
    static int cubicRootsInAB(double d, double a, double b, double c,
                              double[] pts, final int off,
                              final double A, final double B)
    {
        if (d == 0.0d) {
            int num = quadraticRoots(a, b, c, pts, off);
            return filterOutNotInAB(pts, off, num, A, B) - off;
        }
        // From Graphics Gems:
        // http://tog.acm.org/resources/GraphicsGems/gems/Roots3And4.c
        // (also from awt.geom.CubicCurve2D. But here we don't need as
        // much accuracy and we don't want to create arrays so we use
        // our own customized version).

        // normal form: x^3 + ax^2 + bx + c = 0
        a /= d;
        b /= d;
        c /= d;

        //  substitute x = y - A/3 to eliminate quadratic term:
        //     x^3 +Px + Q = 0
        //
        // Since we actually need P/3 and Q/2 for all of the
        // calculations that follow, we will calculate
        // p = P/3
        // q = Q/2
        // instead and use those values for simplicity of the code.
        double sq_A = a * a;
        double p = (1.0d/3.0d) * ((-1.0d/3.0d) * sq_A + b);
        double q = (1.0d/2.0d) * ((2.0d/27.0d) * a * sq_A - (1.0d/3.0d) * a * b + c);

        // use Cardano's formula

        double cb_p = p * p * p;
        double D = q * q + cb_p;

        int num;
        if (D < 0.0d) {
            // see: http://en.wikipedia.org/wiki/Cubic_function#Trigonometric_.28and_hyperbolic.29_method
            final double phi = (1.0d/3.0d) * acos(-q / sqrt(-cb_p));
            final double t = 2.0d * sqrt(-p);

            pts[ off+0 ] = ( t * cos(phi));
            pts[ off+1 ] = (-t * cos(phi + (PI / 3.0d)));
            pts[ off+2 ] = (-t * cos(phi - (PI / 3.0d)));
            num = 3;
        } else {
            final double sqrt_D = sqrt(D);
            final double u = cbrt(sqrt_D - q);
            final double v = - cbrt(sqrt_D + q);

            pts[ off ] = (u + v);
            num = 1;

            if (within(D, 0.0d, 1e-8d)) {
                pts[off+1] = -(pts[off] / 2.0d);
                num = 2;
            }
        }

        final double sub = (1.0d/3.0d) * a;

        for (int i = 0; i < num; ++i) {
            pts[ off+i ] -= sub;
        }

        return filterOutNotInAB(pts, off, num, A, B) - off;
    }

    static double evalCubic(final double a, final double b,
                           final double c, final double d,
                           final double t)
    {
        return t * (t * (t * a + b) + c) + d;
    }

    static double evalQuad(final double a, final double b,
                          final double c, final double t)
    {
        return t * (t * a + b) + c;
    }

    // returns the index 1 past the last valid element remaining after filtering
    static int filterOutNotInAB(double[] nums, final int off, final int len,
                                final double a, final double b)
    {
        int ret = off;
        for (int i = off, end = off + len; i < end; i++) {
            if (nums[i] >= a && nums[i] < b) {
                nums[ret++] = nums[i];
            }
        }
        return ret;
    }

    static double polyLineLength(double[] poly, final int off, final int nCoords) {
        assert nCoords % 2 == 0 && poly.length >= off + nCoords : "";
        double acc = 0.0d;
        for (int i = off + 2; i < off + nCoords; i += 2) {
            acc += linelen(poly[i], poly[i+1], poly[i-2], poly[i-1]);
        }
        return acc;
    }

    static double linelen(double x1, double y1, double x2, double y2) {
        final double dx = x2 - x1;
        final double dy = y2 - y1;
        return Math.sqrt(dx*dx + dy*dy);
    }

    static void subdivide(double[] src, int srcoff, double[] left, int leftoff,
                          double[] right, int rightoff, int type)
    {
        switch(type) {
        case 6:
            DHelpers.subdivideQuad(src, srcoff, left, leftoff, right, rightoff);
            return;
        case 8:
            DHelpers.subdivideCubic(src, srcoff, left, leftoff, right, rightoff);
            return;
        default:
            throw new InternalError("Unsupported curve type");
        }
    }

    static void isort(double[] a, int off, int len) {
        for (int i = off + 1, end = off + len; i < end; i++) {
            double ai = a[i];
            int j = i - 1;
            for (; j >= off && a[j] > ai; j--) {
                a[j+1] = a[j];
            }
            a[j+1] = ai;
        }
    }

    // Most of these are copied from classes in java.awt.geom because we need
    // both single and double precision variants of these functions, and Line2D,
    // CubicCurve2D, QuadCurve2D don't provide them.
    /**
     * Subdivides the cubic curve specified by the coordinates
     * stored in the <code>src</code> array at indices <code>srcoff</code>
     * through (<code>srcoff</code>&nbsp;+&nbsp;7) and stores the
     * resulting two subdivided curves into the two result arrays at the
     * corresponding indices.
     * Either or both of the <code>left</code> and <code>right</code>
     * arrays may be <code>null</code> or a reference to the same array
     * as the <code>src</code> array.
     * Note that the last point in the first subdivided curve is the
     * same as the first point in the second subdivided curve. Thus,
     * it is possible to pass the same array for <code>left</code>
     * and <code>right</code> and to use offsets, such as <code>rightoff</code>
     * equals (<code>leftoff</code> + 6), in order
     * to avoid allocating extra storage for this common point.
     * @param src the array holding the coordinates for the source curve
     * @param srcoff the offset into the array of the beginning of the
     * the 6 source coordinates
     * @param left the array for storing the coordinates for the first
     * half of the subdivided curve
     * @param leftoff the offset into the array of the beginning of the
     * the 6 left coordinates
     * @param right the array for storing the coordinates for the second
     * half of the subdivided curve
     * @param rightoff the offset into the array of the beginning of the
     * the 6 right coordinates
     * @since 1.7
     */
    static void subdivideCubic(double[] src, int srcoff,
                               double[] left, int leftoff,
                               double[] right, int rightoff)
    {
        double x1 = src[srcoff + 0];
        double y1 = src[srcoff + 1];
        double ctrlx1 = src[srcoff + 2];
        double ctrly1 = src[srcoff + 3];
        double ctrlx2 = src[srcoff + 4];
        double ctrly2 = src[srcoff + 5];
        double x2 = src[srcoff + 6];
        double y2 = src[srcoff + 7];
        if (left != null) {
            left[leftoff + 0] = x1;
            left[leftoff + 1] = y1;
        }
        if (right != null) {
            right[rightoff + 6] = x2;
            right[rightoff + 7] = y2;
        }
        x1 = (x1 + ctrlx1) / 2.0d;
        y1 = (y1 + ctrly1) / 2.0d;
        x2 = (x2 + ctrlx2) / 2.0d;
        y2 = (y2 + ctrly2) / 2.0d;
        double centerx = (ctrlx1 + ctrlx2) / 2.0d;
        double centery = (ctrly1 + ctrly2) / 2.0d;
        ctrlx1 = (x1 + centerx) / 2.0d;
        ctrly1 = (y1 + centery) / 2.0d;
        ctrlx2 = (x2 + centerx) / 2.0d;
        ctrly2 = (y2 + centery) / 2.0d;
        centerx = (ctrlx1 + ctrlx2) / 2.0d;
        centery = (ctrly1 + ctrly2) / 2.0d;
        if (left != null) {
            left[leftoff + 2] = x1;
            left[leftoff + 3] = y1;
            left[leftoff + 4] = ctrlx1;
            left[leftoff + 5] = ctrly1;
            left[leftoff + 6] = centerx;
            left[leftoff + 7] = centery;
        }
        if (right != null) {
            right[rightoff + 0] = centerx;
            right[rightoff + 1] = centery;
            right[rightoff + 2] = ctrlx2;
            right[rightoff + 3] = ctrly2;
            right[rightoff + 4] = x2;
            right[rightoff + 5] = y2;
        }
    }


    static void subdivideCubicAt(double t, double[] src, int srcoff,
                                 double[] left, int leftoff,
                                 double[] right, int rightoff)
    {
        double x1 = src[srcoff + 0];
        double y1 = src[srcoff + 1];
        double ctrlx1 = src[srcoff + 2];
        double ctrly1 = src[srcoff + 3];
        double ctrlx2 = src[srcoff + 4];
        double ctrly2 = src[srcoff + 5];
        double x2 = src[srcoff + 6];
        double y2 = src[srcoff + 7];
        if (left != null) {
            left[leftoff + 0] = x1;
            left[leftoff + 1] = y1;
        }
        if (right != null) {
            right[rightoff + 6] = x2;
            right[rightoff + 7] = y2;
        }
        x1 = x1 + t * (ctrlx1 - x1);
        y1 = y1 + t * (ctrly1 - y1);
        x2 = ctrlx2 + t * (x2 - ctrlx2);
        y2 = ctrly2 + t * (y2 - ctrly2);
        double centerx = ctrlx1 + t * (ctrlx2 - ctrlx1);
        double centery = ctrly1 + t * (ctrly2 - ctrly1);
        ctrlx1 = x1 + t * (centerx - x1);
        ctrly1 = y1 + t * (centery - y1);
        ctrlx2 = centerx + t * (x2 - centerx);
        ctrly2 = centery + t * (y2 - centery);
        centerx = ctrlx1 + t * (ctrlx2 - ctrlx1);
        centery = ctrly1 + t * (ctrly2 - ctrly1);
        if (left != null) {
            left[leftoff + 2] = x1;
            left[leftoff + 3] = y1;
            left[leftoff + 4] = ctrlx1;
            left[leftoff + 5] = ctrly1;
            left[leftoff + 6] = centerx;
            left[leftoff + 7] = centery;
        }
        if (right != null) {
            right[rightoff + 0] = centerx;
            right[rightoff + 1] = centery;
            right[rightoff + 2] = ctrlx2;
            right[rightoff + 3] = ctrly2;
            right[rightoff + 4] = x2;
            right[rightoff + 5] = y2;
        }
    }

    static void subdivideQuad(double[] src, int srcoff,
                              double[] left, int leftoff,
                              double[] right, int rightoff)
    {
        double x1 = src[srcoff + 0];
        double y1 = src[srcoff + 1];
        double ctrlx = src[srcoff + 2];
        double ctrly = src[srcoff + 3];
        double x2 = src[srcoff + 4];
        double y2 = src[srcoff + 5];
        if (left != null) {
            left[leftoff + 0] = x1;
            left[leftoff + 1] = y1;
        }
        if (right != null) {
            right[rightoff + 4] = x2;
            right[rightoff + 5] = y2;
        }
        x1 = (x1 + ctrlx) / 2.0d;
        y1 = (y1 + ctrly) / 2.0d;
        x2 = (x2 + ctrlx) / 2.0d;
        y2 = (y2 + ctrly) / 2.0d;
        ctrlx = (x1 + x2) / 2.0d;
        ctrly = (y1 + y2) / 2.0d;
        if (left != null) {
            left[leftoff + 2] = x1;
            left[leftoff + 3] = y1;
            left[leftoff + 4] = ctrlx;
            left[leftoff + 5] = ctrly;
        }
        if (right != null) {
            right[rightoff + 0] = ctrlx;
            right[rightoff + 1] = ctrly;
            right[rightoff + 2] = x2;
            right[rightoff + 3] = y2;
        }
    }

    static void subdivideQuadAt(double t, double[] src, int srcoff,
                                double[] left, int leftoff,
                                double[] right, int rightoff)
    {
        double x1 = src[srcoff + 0];
        double y1 = src[srcoff + 1];
        double ctrlx = src[srcoff + 2];
        double ctrly = src[srcoff + 3];
        double x2 = src[srcoff + 4];
        double y2 = src[srcoff + 5];
        if (left != null) {
            left[leftoff + 0] = x1;
            left[leftoff + 1] = y1;
        }
        if (right != null) {
            right[rightoff + 4] = x2;
            right[rightoff + 5] = y2;
        }
        x1 = x1 + t * (ctrlx - x1);
        y1 = y1 + t * (ctrly - y1);
        x2 = ctrlx + t * (x2 - ctrlx);
        y2 = ctrly + t * (y2 - ctrly);
        ctrlx = x1 + t * (x2 - x1);
        ctrly = y1 + t * (y2 - y1);
        if (left != null) {
            left[leftoff + 2] = x1;
            left[leftoff + 3] = y1;
            left[leftoff + 4] = ctrlx;
            left[leftoff + 5] = ctrly;
        }
        if (right != null) {
            right[rightoff + 0] = ctrlx;
            right[rightoff + 1] = ctrly;
            right[rightoff + 2] = x2;
            right[rightoff + 3] = y2;
        }
    }

    static void subdivideAt(double t, double[] src, int srcoff,
                            double[] left, int leftoff,
                            double[] right, int rightoff, int size)
    {
        switch(size) {
        case 8:
            subdivideCubicAt(t, src, srcoff, left, leftoff, right, rightoff);
            return;
        case 6:
            subdivideQuadAt(t, src, srcoff, left, leftoff, right, rightoff);
            return;
        }
    }
}
