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
import java.util.Arrays;
import sun.awt.geom.PathConsumer2D;
import sun.java2d.marlin.stats.Histogram;
import sun.java2d.marlin.stats.StatLong;

final class Helpers implements MarlinConst {

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
        if (a != 0.0f) {
            final float dis = b*b - 4*a*c;
            if (dis > 0.0f) {
                final float sqrtDis = (float) Math.sqrt(dis);
                // depending on the sign of b we use a slightly different
                // algorithm than the traditional one to find one of the roots
                // so we can avoid adding numbers of different signs (which
                // might result in loss of precision).
                if (b >= 0.0f) {
                    zeroes[ret++] = (2.0f * c) / (-b - sqrtDis);
                    zeroes[ret++] = (-b - sqrtDis) / (2.0f * a);
                } else {
                    zeroes[ret++] = (-b + sqrtDis) / (2.0f * a);
                    zeroes[ret++] = (2.0f * c) / (-b + sqrtDis);
                }
            } else if (dis == 0.0f) {
                t = (-b) / (2.0f * a);
                zeroes[ret++] = t;
            }
        } else {
            if (b != 0.0f) {
                t = (-c) / b;
                zeroes[ret++] = t;
            }
        }
        return ret - off;
    }

    // find the roots of g(t) = d*t^3 + a*t^2 + b*t + c in [A,B)
    static int cubicRootsInAB(float d, float a, float b, float c,
                              float[] pts, final int off,
                              final float A, final float B)
    {
        if (d == 0.0f) {
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
            final double phi = (1.0d/3.0d) * Math.acos(-q / Math.sqrt(-cb_p));
            final double t = 2.0d * Math.sqrt(-p);

            pts[ off+0 ] = (float) ( t * Math.cos(phi));
            pts[ off+1 ] = (float) (-t * Math.cos(phi + (PI / 3.0d)));
            pts[ off+2 ] = (float) (-t * Math.cos(phi - (PI / 3.0d)));
            num = 3;
        } else {
            final double sqrt_D = Math.sqrt(D);
            final double u =   Math.cbrt(sqrt_D - q);
            final double v = - Math.cbrt(sqrt_D + q);

            pts[ off ] = (float) (u + v);
            num = 1;

            if (within(D, 0.0d, 1e-8d)) {
                pts[off+1] = -(pts[off] / 2.0f);
                num = 2;
            }
        }

        final float sub = (1.0f/3.0f) * a;

        for (int i = 0; i < num; ++i) {
            pts[ off+i ] -= sub;
        }

        return filterOutNotInAB(pts, off, num, A, B) - off;
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
        for (int i = off, end = off + len; i < end; i++) {
            if (nums[i] >= a && nums[i] < b) {
                nums[ret++] = nums[i];
            }
        }
        return ret;
    }

    static float linelen(float x1, float y1, float x2, float y2) {
        final float dx = x2 - x1;
        final float dy = y2 - y1;
        return (float) Math.sqrt(dx*dx + dy*dy);
    }

    static void subdivide(float[] src, int srcoff, float[] left, int leftoff,
                          float[] right, int rightoff, int type)
    {
        switch(type) {
        case 6:
            Helpers.subdivideQuad(src, srcoff, left, leftoff, right, rightoff);
            return;
        case 8:
            Helpers.subdivideCubic(src, srcoff, left, leftoff, right, rightoff);
            return;
        default:
            throw new InternalError("Unsupported curve type");
        }
    }

    static void isort(float[] a, int off, int len) {
        for (int i = off + 1, end = off + len; i < end; i++) {
            float ai = a[i];
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
    static void subdivideCubic(float[] src, int srcoff,
                               float[] left, int leftoff,
                               float[] right, int rightoff)
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


    static void subdivideCubicAt(float t, float[] src, int srcoff,
                                 float[] left, int leftoff,
                                 float[] right, int rightoff)
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

    static void subdivideQuad(float[] src, int srcoff,
                              float[] left, int leftoff,
                              float[] right, int rightoff)
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

    static void subdivideQuadAt(float t, float[] src, int srcoff,
                                float[] left, int leftoff,
                                float[] right, int rightoff)
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

    static void subdivideAt(float t, float[] src, int srcoff,
                            float[] left, int leftoff,
                            float[] right, int rightoff, int size)
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

    // From sun.java2d.loops.GeneralRenderer:

    static int outcode(final float x, final float y,
                       final float[] clipRect)
    {
        int code;
        if (y < clipRect[0]) {
            code = OUTCODE_TOP;
        } else if (y >= clipRect[1]) {
            code = OUTCODE_BOTTOM;
        } else {
            code = 0;
        }
        if (x < clipRect[2]) {
            code |= OUTCODE_LEFT;
        } else if (x >= clipRect[3]) {
            code |= OUTCODE_RIGHT;
        }
        return code;
    }

    // a stack of polynomial curves where each curve shares endpoints with
    // adjacent ones.
    static final class PolyStack {
        private static final byte TYPE_LINETO  = (byte) 0;
        private static final byte TYPE_QUADTO  = (byte) 1;
        private static final byte TYPE_CUBICTO = (byte) 2;

        // curves capacity = edges count (8192) = edges x 2 (coords)
        private static final int INITIAL_CURVES_COUNT = INITIAL_EDGES_COUNT << 1;

        // types capacity = edges count (4096)
        private static final int INITIAL_TYPES_COUNT = INITIAL_EDGES_COUNT;

        float[] curves;
        int end;
        byte[] curveTypes;
        int numCurves;

        // curves ref (dirty)
        final FloatArrayCache.Reference curves_ref;
        // curveTypes ref (dirty)
        final ByteArrayCache.Reference curveTypes_ref;

        // used marks (stats only)
        int curveTypesUseMark;
        int curvesUseMark;

        private final StatLong stat_polystack_types;
        private final StatLong stat_polystack_curves;
        private final Histogram hist_polystack_curves;
        private final StatLong stat_array_polystack_curves;
        private final StatLong stat_array_polystack_curveTypes;

        PolyStack(final RendererContext rdrCtx) {
            this(rdrCtx, null, null, null, null, null);
        }

        PolyStack(final RendererContext rdrCtx,
                  final StatLong stat_polystack_types,
                  final StatLong stat_polystack_curves,
                  final Histogram hist_polystack_curves,
                  final StatLong stat_array_polystack_curves,
                  final StatLong stat_array_polystack_curveTypes)
        {
            curves_ref = rdrCtx.newDirtyFloatArrayRef(INITIAL_CURVES_COUNT); // 32K
            curves     = curves_ref.initial;

            curveTypes_ref = rdrCtx.newDirtyByteArrayRef(INITIAL_TYPES_COUNT); // 4K
            curveTypes     = curveTypes_ref.initial;
            numCurves = 0;
            end = 0;

            if (DO_STATS) {
                curveTypesUseMark = 0;
                curvesUseMark = 0;
            }
            this.stat_polystack_types = stat_polystack_types;
            this.stat_polystack_curves = stat_polystack_curves;
            this.hist_polystack_curves = hist_polystack_curves;
            this.stat_array_polystack_curves = stat_array_polystack_curves;
            this.stat_array_polystack_curveTypes = stat_array_polystack_curveTypes;
        }

        /**
         * Disposes this PolyStack:
         * clean up before reusing this instance
         */
        void dispose() {
            end = 0;
            numCurves = 0;

            if (DO_STATS) {
                stat_polystack_types.add(curveTypesUseMark);
                stat_polystack_curves.add(curvesUseMark);
                hist_polystack_curves.add(curvesUseMark);

                // reset marks
                curveTypesUseMark = 0;
                curvesUseMark = 0;
            }

            // Return arrays:
            // curves and curveTypes are kept dirty
            curves     = curves_ref.putArray(curves);
            curveTypes = curveTypes_ref.putArray(curveTypes);
        }

        private void ensureSpace(final int n) {
            // use substraction to avoid integer overflow:
            if (curves.length - end < n) {
                if (DO_STATS) {
                    stat_array_polystack_curves.add(end + n);
                }
                curves = curves_ref.widenArray(curves, end, end + n);
            }
            if (curveTypes.length <= numCurves) {
                if (DO_STATS) {
                    stat_array_polystack_curveTypes.add(numCurves + 1);
                }
                curveTypes = curveTypes_ref.widenArray(curveTypes,
                                                       numCurves,
                                                       numCurves + 1);
            }
        }

        void pushCubic(float x0, float y0,
                       float x1, float y1,
                       float x2, float y2)
        {
            ensureSpace(6);
            curveTypes[numCurves++] = TYPE_CUBICTO;
            // we reverse the coordinate order to make popping easier
            final float[] _curves = curves;
            int e = end;
            _curves[e++] = x2;    _curves[e++] = y2;
            _curves[e++] = x1;    _curves[e++] = y1;
            _curves[e++] = x0;    _curves[e++] = y0;
            end = e;
        }

        void pushQuad(float x0, float y0,
                      float x1, float y1)
        {
            ensureSpace(4);
            curveTypes[numCurves++] = TYPE_QUADTO;
            final float[] _curves = curves;
            int e = end;
            _curves[e++] = x1;    _curves[e++] = y1;
            _curves[e++] = x0;    _curves[e++] = y0;
            end = e;
        }

        void pushLine(float x, float y) {
            ensureSpace(2);
            curveTypes[numCurves++] = TYPE_LINETO;
            curves[end++] = x;    curves[end++] = y;
        }

        void pullAll(final PathConsumer2D io) {
            final int nc = numCurves;
            if (nc == 0) {
                return;
            }
            if (DO_STATS) {
                // update used marks:
                if (numCurves > curveTypesUseMark) {
                    curveTypesUseMark = numCurves;
                }
                if (end > curvesUseMark) {
                    curvesUseMark = end;
                }
            }
            final byte[]  _curveTypes = curveTypes;
            final float[] _curves = curves;
            int e = 0;

            for (int i = 0; i < nc; i++) {
                switch(_curveTypes[i]) {
                case TYPE_LINETO:
                    io.lineTo(_curves[e], _curves[e+1]);
                    e += 2;
                    continue;
                case TYPE_QUADTO:
                    io.quadTo(_curves[e+0], _curves[e+1],
                              _curves[e+2], _curves[e+3]);
                    e += 4;
                    continue;
                case TYPE_CUBICTO:
                    io.curveTo(_curves[e+0], _curves[e+1],
                               _curves[e+2], _curves[e+3],
                               _curves[e+4], _curves[e+5]);
                    e += 6;
                    continue;
                default:
                }
            }
            numCurves = 0;
            end = 0;
        }

        void popAll(final PathConsumer2D io) {
            int nc = numCurves;
            if (nc == 0) {
                return;
            }
            if (DO_STATS) {
                // update used marks:
                if (numCurves > curveTypesUseMark) {
                    curveTypesUseMark = numCurves;
                }
                if (end > curvesUseMark) {
                    curvesUseMark = end;
                }
            }
            final byte[]  _curveTypes = curveTypes;
            final float[] _curves = curves;
            int e  = end;

            while (nc != 0) {
                switch(_curveTypes[--nc]) {
                case TYPE_LINETO:
                    e -= 2;
                    io.lineTo(_curves[e], _curves[e+1]);
                    continue;
                case TYPE_QUADTO:
                    e -= 4;
                    io.quadTo(_curves[e+0], _curves[e+1],
                              _curves[e+2], _curves[e+3]);
                    continue;
                case TYPE_CUBICTO:
                    e -= 6;
                    io.curveTo(_curves[e+0], _curves[e+1],
                               _curves[e+2], _curves[e+3],
                               _curves[e+4], _curves[e+5]);
                    continue;
                default:
                }
            }
            numCurves = 0;
            end = 0;
        }

        @Override
        public String toString() {
            String ret = "";
            int nc = numCurves;
            int last = end;
            int len;
            while (nc != 0) {
                switch(curveTypes[--nc]) {
                case TYPE_LINETO:
                    len = 2;
                    ret += "line: ";
                    break;
                case TYPE_QUADTO:
                    len = 4;
                    ret += "quad: ";
                    break;
                case TYPE_CUBICTO:
                    len = 6;
                    ret += "cubic: ";
                    break;
                default:
                    len = 0;
                }
                last -= len;
                ret += Arrays.toString(Arrays.copyOfRange(curves, last, last+len))
                                       + "\n";
            }
            return ret;
        }
    }

    // a stack of integer indices
    static final class IndexStack {

        // integer capacity = edges count / 4 ~ 1024
        private static final int INITIAL_COUNT = INITIAL_EDGES_COUNT >> 2;

        private int end;
        private int[] indices;

        // indices ref (dirty)
        private final IntArrayCache.Reference indices_ref;

        // used marks (stats only)
        private int indicesUseMark;

        private final StatLong stat_idxstack_indices;
        private final Histogram hist_idxstack_indices;
        private final StatLong stat_array_idxstack_indices;

        IndexStack(final RendererContext rdrCtx) {
            this(rdrCtx, null, null, null);
        }

        IndexStack(final RendererContext rdrCtx,
                   final StatLong stat_idxstack_indices,
                   final Histogram hist_idxstack_indices,
                   final StatLong stat_array_idxstack_indices)
        {
            indices_ref = rdrCtx.newDirtyIntArrayRef(INITIAL_COUNT); // 4K
            indices     = indices_ref.initial;
            end = 0;

            if (DO_STATS) {
                indicesUseMark = 0;
            }
            this.stat_idxstack_indices = stat_idxstack_indices;
            this.hist_idxstack_indices = hist_idxstack_indices;
            this.stat_array_idxstack_indices = stat_array_idxstack_indices;
        }

        /**
         * Disposes this PolyStack:
         * clean up before reusing this instance
         */
        void dispose() {
            end = 0;

            if (DO_STATS) {
                stat_idxstack_indices.add(indicesUseMark);
                hist_idxstack_indices.add(indicesUseMark);

                // reset marks
                indicesUseMark = 0;
            }

            // Return arrays:
            // values is kept dirty
            indices = indices_ref.putArray(indices);
        }

        boolean isEmpty() {
            return (end == 0);
        }

        void reset() {
            end = 0;
        }

        void push(final int v) {
            // remove redundant values (reverse order):
            int[] _values = indices;
            final int nc = end;
            if (nc != 0) {
                if (_values[nc - 1] == v) {
                    // remove both duplicated values:
                    end--;
                    return;
                }
            }
            if (_values.length <= nc) {
                if (DO_STATS) {
                    stat_array_idxstack_indices.add(nc + 1);
                }
                indices = _values = indices_ref.widenArray(_values, nc, nc + 1);
            }
            _values[end++] = v;

            if (DO_STATS) {
                // update used marks:
                if (end > indicesUseMark) {
                    indicesUseMark = end;
                }
            }
        }

        void pullAll(final float[] points, final PathConsumer2D io) {
            final int nc = end;
            if (nc == 0) {
                return;
            }
            final int[] _values = indices;

            for (int i = 0, j; i < nc; i++) {
                j = _values[i] << 1;
                io.lineTo(points[j], points[j + 1]);
            }
            end = 0;
        }
    }
}
