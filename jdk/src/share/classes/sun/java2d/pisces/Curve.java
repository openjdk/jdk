/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.pisces;

import java.util.Iterator;

class Curve {

    float ax, ay, bx, by, cx, cy, dx, dy;
    float dax, day, dbx, dby;

    Curve() {
    }

    void set(float[] points, int type) {
        switch(type) {
        case 8:
            set(points[0], points[1],
                points[2], points[3],
                points[4], points[5],
                points[6], points[7]);
            break;
        case 6:
            set(points[0], points[1],
                points[2], points[3],
                points[4], points[5]);
            break;
        default:
            throw new InternalError("Curves can only be cubic or quadratic");
        }
    }

    void set(float x1, float y1,
             float x2, float y2,
             float x3, float y3,
             float x4, float y4)
    {
        ax = 3 * (x2 - x3) + x4 - x1;
        ay = 3 * (y2 - y3) + y4 - y1;
        bx = 3 * (x1 - 2 * x2 + x3);
        by = 3 * (y1 - 2 * y2 + y3);
        cx = 3 * (x2 - x1);
        cy = 3 * (y2 - y1);
        dx = x1;
        dy = y1;
        dax = 3 * ax; day = 3 * ay;
        dbx = 2 * bx; dby = 2 * by;
    }

    void set(float x1, float y1,
             float x2, float y2,
             float x3, float y3)
    {
        ax = ay = 0f;

        bx = x1 - 2 * x2 + x3;
        by = y1 - 2 * y2 + y3;
        cx = 2 * (x2 - x1);
        cy = 2 * (y2 - y1);
        dx = x1;
        dy = y1;
        dax = 0; day = 0;
        dbx = 2 * bx; dby = 2 * by;
    }

    float xat(float t) {
        return t * (t * (t * ax + bx) + cx) + dx;
    }
    float yat(float t) {
        return t * (t * (t * ay + by) + cy) + dy;
    }

    float dxat(float t) {
        return t * (t * dax + dbx) + cx;
    }

    float dyat(float t) {
        return t * (t * day + dby) + cy;
    }

    private float ddxat(float t) {
        return 2 * dax * t + dbx;
    }

    private float ddyat(float t) {
        return 2 * day * t + dby;
    }

    int dxRoots(float[] roots, int off) {
        return Helpers.quadraticRoots(dax, dbx, cx, roots, off);
    }

    int dyRoots(float[] roots, int off) {
        return Helpers.quadraticRoots(day, dby, cy, roots, off);
    }

    int infPoints(float[] pts, int off) {
        // inflection point at t if -f'(t)x*f''(t)y + f'(t)y*f''(t)x == 0
        // Fortunately, this turns out to be quadratic, so there are at
        // most 2 inflection points.
        final float a = dax * dby - dbx * day;
        final float b = 2 * (cy * dax - day * cx);
        final float c = cy * dbx - cx * dby;

        return Helpers.quadraticRoots(a, b, c, pts, off);
    }

    // finds points where the first and second derivative are
    // perpendicular. This happens when g(t) = f'(t)*f''(t) == 0 (where
    // * is a dot product). Unfortunately, we have to solve a cubic.
    private int perpendiculardfddf(float[] pts, int off, final float err) {
        assert pts.length >= off + 4;

        // these are the coefficients of g(t):
        final float a = 2*(dax*dax + day*day);
        final float b = 3*(dax*dbx + day*dby);
        final float c = 2*(dax*cx + day*cy) + dbx*dbx + dby*dby;
        final float d = dbx*cx + dby*cy;
        // TODO: We might want to divide the polynomial by a to make the
        // coefficients smaller. This won't change the roots.
        return Helpers.cubicRootsInAB(a, b, c, d, pts, off, err, 0f, 1f);
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
    int rootsOfROCMinusW(float[] roots, int off, final float w, final float err) {
        // no OOB exception, because by now off<=6, and roots.length >= 10
        assert off <= 6 && roots.length >= 10;
        int ret = off;
        int numPerpdfddf = perpendiculardfddf(roots, off, err);
        float t0 = 0, ft0 = ROCsq(t0) - w*w;
        roots[off + numPerpdfddf] = 1f; // always check interval end points
        numPerpdfddf++;
        for (int i = off; i < off + numPerpdfddf; i++) {
            float t1 = roots[i], ft1 = ROCsq(t1) - w*w;
            if (ft0 == 0f) {
                roots[ret++] = t0;
            } else if (ft1 * ft0 < 0f) { // have opposite signs
                // (ROC(t)^2 == w^2) == (ROC(t) == w) is true because
                // ROC(t) >= 0 for all t.
                roots[ret++] = falsePositionROCsqMinusX(t0, t1, w*w, err);
            }
            t0 = t1;
            ft0 = ft1;
        }

        return ret - off;
    }

    private static float eliminateInf(float x) {
        return (x == Float.POSITIVE_INFINITY ? Float.MAX_VALUE :
            (x == Float.NEGATIVE_INFINITY ? Float.MIN_VALUE : x));
    }

    // A slight modification of the false position algorithm on wikipedia.
    // This only works for the ROCsq-x functions. It might be nice to have
    // the function as an argument, but that would be awkward in java6.
    // It is something to consider for java7, depending on how closures
    // and function objects turn out. Same goes for the newton's method
    // algorithm in Helpers.java
    private float falsePositionROCsqMinusX(float x0, float x1,
                                           final float x, final float err)
    {
        final int iterLimit = 100;
        int side = 0;
        float t = x1, ft = eliminateInf(ROCsq(t) - x);
        float s = x0, fs = eliminateInf(ROCsq(s) - x);
        float r = s, fr;
        for (int i = 0; i < iterLimit && Math.abs(t - s) > err * Math.abs(t + s); i++) {
            r = (fs * t - ft * s) / (fs - ft);
            fr = ROCsq(r) - x;
            if (fr * ft > 0) {// have the same sign
                ft = fr; t = r;
                if (side < 0) {
                    fs /= (1 << (-side));
                    side--;
                } else {
                    side = -1;
                }
            } else if (fr * fs > 0) {
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

    // returns the radius of curvature squared at t of this curve
    // see http://en.wikipedia.org/wiki/Radius_of_curvature_(applications)
    private float ROCsq(final float t) {
        final float dx = dxat(t);
        final float dy = dyat(t);
        final float ddx = ddxat(t);
        final float ddy = ddyat(t);
        final float dx2dy2 = dx*dx + dy*dy;
        final float ddx2ddy2 = ddx*ddx + ddy*ddy;
        final float ddxdxddydy = ddx*dx + ddy*dy;
        float ret = ((dx2dy2*dx2dy2) / (dx2dy2 * ddx2ddy2 - ddxdxddydy*ddxdxddydy))*dx2dy2;
        return ret;
    }

    // curve to be broken should be in pts[0]
    // this will change the contents of both pts and Ts
    // TODO: There's no reason for Ts to be an array. All we need is a sequence
    // of t values at which to subdivide. An array statisfies this condition,
    // but is unnecessarily restrictive. Ts should be an Iterator<Float> instead.
    // Doing this will also make dashing easier, since we could easily make
    // LengthIterator an Iterator<Float> and feed it to this function to simplify
    // the loop in Dasher.somethingTo.
    static Iterator<float[]> breakPtsAtTs(final float[][] pts, final int type,
                                          final float[] Ts, final int numTs)
    {
        assert pts.length >= 2 && pts[0].length >= 8 && numTs <= Ts.length;
        return new Iterator<float[]>() {
            int nextIdx = 0;
            int nextCurveIdx = 0;
            float prevT = 0;

            @Override public boolean hasNext() {
                return nextCurveIdx < numTs + 1;
            }

            @Override public float[] next() {
                float[] ret;
                if (nextCurveIdx < numTs) {
                    float curT = Ts[nextCurveIdx];
                    float splitT = (curT - prevT) / (1 - prevT);
                    Helpers.subdivideAt(splitT,
                                        pts[nextIdx], 0,
                                        pts[nextIdx], 0,
                                        pts[1-nextIdx], 0, type);
                    updateTs(Ts, Ts[nextCurveIdx], nextCurveIdx + 1, numTs - nextCurveIdx - 1);
                    ret = pts[nextIdx];
                    nextIdx = 1 - nextIdx;
                } else {
                    ret = pts[nextIdx];
                }
                nextCurveIdx++;
                return ret;
            }

            @Override public void remove() {}
        };
    }

    // precondition: ts[off]...ts[off+len-1] must all be greater than t.
    private static void updateTs(float[] ts, final float t, final int off, final int len) {
        for (int i = off; i < off + len; i++) {
            ts[i] = (ts[i] - t) / (1 - t);
        }
    }
}

