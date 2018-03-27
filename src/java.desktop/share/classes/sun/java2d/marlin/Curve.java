/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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

final class Curve {

    float ax, ay, bx, by, cx, cy, dx, dy;
    float dax, day, dbx, dby;

    Curve() {
    }

    void set(final float[] points, final int type) {
        // if instead of switch (perf + most probable cases first)
        if (type == 8) {
            set(points[0], points[1],
                points[2], points[3],
                points[4], points[5],
                points[6], points[7]);
        } else if (type == 4) {
            set(points[0], points[1],
                points[2], points[3]);
        } else {
            set(points[0], points[1],
                points[2], points[3],
                points[4], points[5]);
        }
    }

    void set(final float x1, final float y1,
             final float x2, final float y2,
             final float x3, final float y3,
             final float x4, final float y4)
    {
        final float dx32 = 3.0f * (x3 - x2);
        final float dy32 = 3.0f * (y3 - y2);
        final float dx21 = 3.0f * (x2 - x1);
        final float dy21 = 3.0f * (y2 - y1);
        ax = (x4 - x1) - dx32;  // A = P3 - P0 - 3 (P2 - P1) = (P3 - P0) + 3 (P1 - P2)
        ay = (y4 - y1) - dy32;
        bx = (dx32 - dx21);     // B = 3 (P2 - P1) - 3(P1 - P0) = 3 (P2 + P0) - 6 P1
        by = (dy32 - dy21);
        cx = dx21;              // C = 3 (P1 - P0)
        cy = dy21;
        dx = x1;                // D = P0
        dy = y1;
        dax = 3.0f * ax;
        day = 3.0f * ay;
        dbx = 2.0f * bx;
        dby = 2.0f * by;
    }

    void set(final float x1, final float y1,
             final float x2, final float y2,
             final float x3, final float y3)
    {
        final float dx21 = (x2 - x1);
        final float dy21 = (y2 - y1);
        ax = 0.0f;              // A = 0
        ay = 0.0f;
        bx = (x3 - x2) - dx21;  // B = P3 - P0 - 2 P2
        by = (y3 - y2) - dy21;
        cx = 2.0f * dx21;       // C = 2 (P2 - P1)
        cy = 2.0f * dy21;
        dx = x1;                // D = P1
        dy = y1;
        dax = 0.0f;
        day = 0.0f;
        dbx = 2.0f * bx;
        dby = 2.0f * by;
    }

    void set(final float x1, final float y1,
             final float x2, final float y2)
    {
        final float dx21 = (x2 - x1);
        final float dy21 = (y2 - y1);
        ax = 0.0f;              // A = 0
        ay = 0.0f;
        bx = 0.0f;              // B = 0
        by = 0.0f;
        cx = dx21;              // C = (P2 - P1)
        cy = dy21;
        dx = x1;                // D = P1
        dy = y1;
        dax = 0.0f;
        day = 0.0f;
        dbx = 0.0f;
        dby = 0.0f;
    }

    int dxRoots(final float[] roots, final int off) {
        return Helpers.quadraticRoots(dax, dbx, cx, roots, off);
    }

    int dyRoots(final float[] roots, final int off) {
        return Helpers.quadraticRoots(day, dby, cy, roots, off);
    }

    int infPoints(final float[] pts, final int off) {
        // inflection point at t if -f'(t)x*f''(t)y + f'(t)y*f''(t)x == 0
        // Fortunately, this turns out to be quadratic, so there are at
        // most 2 inflection points.
        final float a = dax * dby - dbx * day;
        final float b = 2.0f * (cy * dax - day * cx);
        final float c = cy * dbx - cx * dby;

        return Helpers.quadraticRoots(a, b, c, pts, off);
    }

    int xPoints(final float[] ts, final int off, final float x)
    {
        return Helpers.cubicRootsInAB(ax, bx, cx, dx - x, ts, off, 0.0f, 1.0f);
    }

    int yPoints(final float[] ts, final int off, final float y)
    {
        return Helpers.cubicRootsInAB(ay, by, cy, dy - y, ts, off, 0.0f, 1.0f);
    }

    // finds points where the first and second derivative are
    // perpendicular. This happens when g(t) = f'(t)*f''(t) == 0 (where
    // * is a dot product). Unfortunately, we have to solve a cubic.
    private int perpendiculardfddf(final float[] pts, final int off) {
        assert pts.length >= off + 4;

        // these are the coefficients of some multiple of g(t) (not g(t),
        // because the roots of a polynomial are not changed after multiplication
        // by a constant, and this way we save a few multiplications).
        final float a = 2.0f * (dax * dax + day * day);
        final float b = 3.0f * (dax * dbx + day * dby);
        final float c = 2.0f * (dax * cx  + day * cy) + dbx * dbx + dby * dby;
        final float d = dbx * cx + dby * cy;

        return Helpers.cubicRootsInAB(a, b, c, d, pts, off, 0.0f, 1.0f);
    }

    // Tries to find the roots of the function ROC(t)-w in [0, 1). It uses
    // a variant of the false position algorithm to find the roots. False
    // position requires that 2 initial values x0,x1 be given, and that the
    // function must have opposite signs at those values. To find such
    // values, we need the local extrema of the ROC function, for which we
    // need the roots of its derivative; however, it's harder to find the
    // roots of the derivative in this case than it is to find the roots
    // of the original function. So, we find all points where this curve's
    // first and second derivative are perpendicular, and we pretend these
    // are our local extrema. There are at most 3 of these, so we will check
    // at most 4 sub-intervals of (0,1). ROC has asymptotes at inflection
    // points, so roc-w can have at least 6 roots. This shouldn't be a
    // problem for what we're trying to do (draw a nice looking curve).
    int rootsOfROCMinusW(final float[] roots, final int off, final float w2, final float err) {
        // no OOB exception, because by now off<=6, and roots.length >= 10
        assert off <= 6 && roots.length >= 10;

        int ret = off;
        final int end = off + perpendiculardfddf(roots, off);
        roots[end] = 1.0f; // always check interval end points

        float t0 = 0.0f, ft0 = ROCsq(t0) - w2;

        for (int i = off; i <= end; i++) {
            float t1 = roots[i], ft1 = ROCsq(t1) - w2;
            if (ft0 == 0.0f) {
                roots[ret++] = t0;
            } else if (ft1 * ft0 < 0.0f) { // have opposite signs
                // (ROC(t)^2 == w^2) == (ROC(t) == w) is true because
                // ROC(t) >= 0 for all t.
                roots[ret++] = falsePositionROCsqMinusX(t0, t1, w2, err);
            }
            t0 = t1;
            ft0 = ft1;
        }

        return ret - off;
    }

    private static float eliminateInf(final float x) {
        return (x == Float.POSITIVE_INFINITY ? Float.MAX_VALUE :
               (x == Float.NEGATIVE_INFINITY ? Float.MIN_VALUE : x));
    }

    // A slight modification of the false position algorithm on wikipedia.
    // This only works for the ROCsq-x functions. It might be nice to have
    // the function as an argument, but that would be awkward in java6.
    // TODO: It is something to consider for java8 (or whenever lambda
    // expressions make it into the language), depending on how closures
    // and turn out. Same goes for the newton's method
    // algorithm in Helpers.java
    private float falsePositionROCsqMinusX(final float t0, final float t1,
                                           final float w2, final float err)
    {
        final int iterLimit = 100;
        int side = 0;
        float t = t1, ft = eliminateInf(ROCsq(t) - w2);
        float s = t0, fs = eliminateInf(ROCsq(s) - w2);
        float r = s, fr;

        for (int i = 0; i < iterLimit && Math.abs(t - s) > err * Math.abs(t + s); i++) {
            r = (fs * t - ft * s) / (fs - ft);
            fr = ROCsq(r) - w2;
            if (sameSign(fr, ft)) {
                ft = fr; t = r;
                if (side < 0) {
                    fs /= (1 << (-side));
                    side--;
                } else {
                    side = -1;
                }
            } else if (fr * fs > 0.0f) {
                fs = fr; s = r;
                if (side > 0) {
                    ft /= (1 << side);
                    side++;
                } else {
                    side = 1;
                }
            } else {
                break;
            }
        }
        return r;
    }

    private static boolean sameSign(final float x, final float y) {
        // another way is to test if x*y > 0. This is bad for small x, y.
        return (x < 0.0f && y < 0.0f) || (x > 0.0f && y > 0.0f);
    }

    // returns the radius of curvature squared at t of this curve
    // see http://en.wikipedia.org/wiki/Radius_of_curvature_(applications)
    private float ROCsq(final float t) {
        final float dx = t * (t * dax + dbx) + cx;
        final float dy = t * (t * day + dby) + cy;
        final float ddx = 2.0f * dax * t + dbx;
        final float ddy = 2.0f * day * t + dby;
        final float dx2dy2 = dx * dx + dy * dy;
        final float ddx2ddy2 = ddx * ddx + ddy * ddy;
        final float ddxdxddydy = ddx * dx + ddy * dy;
        return dx2dy2 * ((dx2dy2 * dx2dy2) / (dx2dy2 * ddx2ddy2 - ddxdxddydy * ddxdxddydy));
    }
}
