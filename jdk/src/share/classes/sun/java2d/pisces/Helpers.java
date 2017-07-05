/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

final class Helpers {
    private Helpers() {
        throw new Error("This is a non instantiable class");
    }

    static boolean within(final float x, final float y, final float err) {
        final float d = y - x;
        return (d <= err && d >= -err);
    }

    static boolean within(final double x, final double y, final double err) {
        final double d = y - x;
        return (d <= err && d >= -err);
    }

    static int quadraticRoots(final float a, final float b,
                              final float c, float[] zeroes, final int off)
    {
        int ret = off;
        float t;
        if (a != 0f) {
            final float dis = b*b - 4*a*c;
            if (dis > 0) {
                final float sqrtDis = (float)Math.sqrt(dis);
                // depending on the sign of b we use a slightly different
                // algorithm than the traditional one to find one of the roots
                // so we can avoid adding numbers of different signs (which
                // might result in loss of precision).
                if (b >= 0) {
                    zeroes[ret++] = (2 * c) / (-b - sqrtDis);
                    zeroes[ret++] = (-b - sqrtDis) / (2 * a);
                } else {
                    zeroes[ret++] = (-b + sqrtDis) / (2 * a);
                    zeroes[ret++] = (2 * c) / (-b + sqrtDis);
                }
            } else if (dis == 0f) {
                t = (-b) / (2 * a);
                zeroes[ret++] = t;
            }
        } else {
            if (b != 0f) {
                t = (-c) / b;
                zeroes[ret++] = t;
            }
        }
        return ret - off;
    }

    // find the roots of g(t) = a*t^3 + b*t^2 + c*t + d in [A,B)
    // We will not use Cardano's method, since it is complicated and
    // involves too many square and cubic roots. We will use Newton's method.
    // TODO: this should probably return ALL roots. Then the user can do
    // his own filtering of roots outside [A,B).
    static int cubicRootsInAB(final float a, final float b,
                              final float c, final float d,
                              float[] pts, final int off, final float E,
                              final float A, final float B)
    {
        if (a == 0) {
            return quadraticRoots(b, c, d, pts, off);
        }
        // the coefficients of g'(t). no dc variable because dc=c
        // we use these to get the critical points of g(t), which
        // we then use to chose starting points for Newton's method. These
        // should be very close to the actual roots.
        final float da = 3 * a;
        final float db = 2 * b;
        int numCritPts = quadraticRoots(da, db, c, pts, off+1);
        numCritPts = filterOutNotInAB(pts, off+1, numCritPts, A, B) - off - 1;
        // need them sorted.
        if (numCritPts == 2 && pts[off+1] > pts[off+2]) {
            float tmp = pts[off+1];
            pts[off+1] = pts[off+2];
            pts[off+2] = tmp;
        }

        int ret = off;

        // we don't actually care much about the extrema themselves. We
        // only use them to ensure that g(t) is monotonic in each
        // interval [pts[i],pts[i+1] (for i in off...off+numCritPts+1).
        // This will allow us to determine intervals containing exactly
        // one root.
        // The end points of the interval are always local extrema.
        pts[off] = A;
        pts[off + numCritPts + 1] = B;
        numCritPts += 2;

        float x0 = pts[off], fx0 = evalCubic(a, b, c, d, x0);
        for (int i = off; i < off + numCritPts - 1; i++) {
            float x1 = pts[i+1], fx1 = evalCubic(a, b, c, d, x1);
            if (fx0 == 0f) {
                pts[ret++] = x0;
            } else if (fx1 * fx0 < 0f) { // have opposite signs
                pts[ret++] = CubicNewton(a, b, c, d,
                        x0 + fx0 * (x1 - x0) / (fx0 - fx1), E);
            }
            x0 = x1;
            fx0 = fx1;
        }
        return ret - off;
    }

    // precondition: the polynomial to be evaluated must not be 0 at x0.
    static float CubicNewton(final float a, final float b,
                             final float c, final float d,
                             float x0, final float err)
    {
        // considering how this function is used, 10 should be more than enough
        final int itlimit = 10;
        float fx0 = evalCubic(a, b, c, d, x0);
        float x1;
        int count = 0;
        while(true) {
            x1 = x0 - (fx0 / evalCubic(0, 3 * a, 2 * b, c, x0));
            if (Math.abs(x1 - x0) < err * Math.abs(x1 + x0) || count == itlimit) {
                break;
            }
            x0 = x1;
            fx0 = evalCubic(a, b, c, d, x0);
            count++;
        }
        return x1;
    }

    // fills the input array with numbers 0, INC, 2*INC, ...
    static void fillWithIdxes(final float[] data, final int[] idxes) {
        if (idxes.length > 0) {
            idxes[0] = 0;
            for (int i = 1; i < idxes.length; i++) {
                idxes[i] = idxes[i-1] + (int)data[idxes[i-1]];
            }
        }
    }

    static void fillWithIdxes(final int[] idxes, final int inc) {
        if (idxes.length > 0) {
            idxes[0] = 0;
            for (int i = 1; i < idxes.length; i++) {
                idxes[i] = idxes[i-1] + inc;
            }
        }
    }

    // These use a hardcoded factor of 2 for increasing sizes. Perhaps this
    // should be provided as an argument.
    static float[] widenArray(float[] in, final int cursize, final int numToAdd) {
        if (in == null) {
            return new float[5 * numToAdd];
        }
        if (in.length >= cursize + numToAdd) {
            return in;
        }
        return Arrays.copyOf(in, 2 * (cursize + numToAdd));
    }
    static int[] widenArray(int[] in, final int cursize, final int numToAdd) {
        if (in.length >= cursize + numToAdd) {
            return in;
        }
        return Arrays.copyOf(in, 2 * (cursize + numToAdd));
    }

    static float evalCubic(final float a, final float b,
                           final float c, final float d,
                           final float t)
    {
        return t * (t * (t * a + b) + c) + d;
    }

    static float evalQuad(final float a, final float b,
                          final float c, final float t)
    {
        return t * (t * a + b) + c;
    }

    // returns the index 1 past the last valid element remaining after filtering
    static int filterOutNotInAB(float[] nums, final int off, final int len,
                                final float a, final float b)
    {
        int ret = off;
        for (int i = off; i < off + len; i++) {
            if (nums[i] > a && nums[i] < b) {
                nums[ret++] = nums[i];
            }
        }
        return ret;
    }

    static float polyLineLength(float[] poly, final int off, final int nCoords) {
        assert nCoords % 2 == 0 && poly.length >= off + nCoords : "";
        float acc = 0;
        for (int i = off + 2; i < off + nCoords; i += 2) {
            acc += linelen(poly[i], poly[i+1], poly[i-2], poly[i-1]);
        }
        return acc;
    }

    static float linelen(float x1, float y1, float x2, float y2) {
        return (float)Math.hypot(x2 - x1, y2 - y1);
    }

    static void subdivide(float[] src, int srcoff, float[] left, int leftoff,
                          float[] right, int rightoff, int type)
    {
        switch(type) {
        case 6:
            Helpers.subdivideQuad(src, srcoff, left, leftoff, right, rightoff);
            break;
        case 8:
            Helpers.subdivideCubic(src, srcoff, left, leftoff, right, rightoff);
            break;
        default:
            throw new InternalError("Unsupported curve type");
        }
    }

    static void isort(float[] a, int off, int len) {
        for (int i = off + 1; i < off + len; i++) {
            float ai = a[i];
            int j = i - 1;
            for (; j >= off && a[j] > ai; j--) {
                a[j+1] = a[j];
            }
            a[j+1] = ai;
        }
    }

    // Most of these are copied from classes in java.awt.geom because we need
    // float versions of these functions, and Line2D, CubicCurve2D,
    // QuadCurve2D don't provide them.
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
    static void subdivideCubic(float src[], int srcoff,
                               float left[], int leftoff,
                               float right[], int rightoff)
    {
        float x1 = src[srcoff + 0];
        float y1 = src[srcoff + 1];
        float ctrlx1 = src[srcoff + 2];
        float ctrly1 = src[srcoff + 3];
        float ctrlx2 = src[srcoff + 4];
        float ctrly2 = src[srcoff + 5];
        float x2 = src[srcoff + 6];
        float y2 = src[srcoff + 7];
        if (left != null) {
            left[leftoff + 0] = x1;
            left[leftoff + 1] = y1;
        }
        if (right != null) {
            right[rightoff + 6] = x2;
            right[rightoff + 7] = y2;
        }
        x1 = (x1 + ctrlx1) / 2.0f;
        y1 = (y1 + ctrly1) / 2.0f;
        x2 = (x2 + ctrlx2) / 2.0f;
        y2 = (y2 + ctrly2) / 2.0f;
        float centerx = (ctrlx1 + ctrlx2) / 2.0f;
        float centery = (ctrly1 + ctrly2) / 2.0f;
        ctrlx1 = (x1 + centerx) / 2.0f;
        ctrly1 = (y1 + centery) / 2.0f;
        ctrlx2 = (x2 + centerx) / 2.0f;
        ctrly2 = (y2 + centery) / 2.0f;
        centerx = (ctrlx1 + ctrlx2) / 2.0f;
        centery = (ctrly1 + ctrly2) / 2.0f;
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


    static void subdivideCubicAt(float t, float src[], int srcoff,
                                 float left[], int leftoff,
                                 float right[], int rightoff)
    {
        float x1 = src[srcoff + 0];
        float y1 = src[srcoff + 1];
        float ctrlx1 = src[srcoff + 2];
        float ctrly1 = src[srcoff + 3];
        float ctrlx2 = src[srcoff + 4];
        float ctrly2 = src[srcoff + 5];
        float x2 = src[srcoff + 6];
        float y2 = src[srcoff + 7];
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
        float centerx = ctrlx1 + t * (ctrlx2 - ctrlx1);
        float centery = ctrly1 + t * (ctrly2 - ctrly1);
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

    static void subdivideQuad(float src[], int srcoff,
                              float left[], int leftoff,
                              float right[], int rightoff)
    {
        float x1 = src[srcoff + 0];
        float y1 = src[srcoff + 1];
        float ctrlx = src[srcoff + 2];
        float ctrly = src[srcoff + 3];
        float x2 = src[srcoff + 4];
        float y2 = src[srcoff + 5];
        if (left != null) {
            left[leftoff + 0] = x1;
            left[leftoff + 1] = y1;
        }
        if (right != null) {
            right[rightoff + 4] = x2;
            right[rightoff + 5] = y2;
        }
        x1 = (x1 + ctrlx) / 2.0f;
        y1 = (y1 + ctrly) / 2.0f;
        x2 = (x2 + ctrlx) / 2.0f;
        y2 = (y2 + ctrly) / 2.0f;
        ctrlx = (x1 + x2) / 2.0f;
        ctrly = (y1 + y2) / 2.0f;
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

    static void subdivideQuadAt(float t, float src[], int srcoff,
                                float left[], int leftoff,
                                float right[], int rightoff)
    {
        float x1 = src[srcoff + 0];
        float y1 = src[srcoff + 1];
        float ctrlx = src[srcoff + 2];
        float ctrly = src[srcoff + 3];
        float x2 = src[srcoff + 4];
        float y2 = src[srcoff + 5];
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

    static void subdivideAt(float t, float src[], int srcoff,
                            float left[], int leftoff,
                            float right[], int rightoff, int size)
    {
        switch(size) {
        case 8:
            subdivideCubicAt(t, src, srcoff, left, leftoff, right, rightoff);
            break;
        case 6:
            subdivideQuadAt(t, src, srcoff, left, leftoff, right, rightoff);
            break;
        }
    }
}
