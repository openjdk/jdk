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

public class Stroker implements LineSink {

    private static final int MOVE_TO = 0;
    private static final int LINE_TO = 1;
    private static final int CLOSE = 2;

    /**
     * Constant value for join style.
     */
    public static final int JOIN_MITER = 0;

    /**
     * Constant value for join style.
     */
    public static final int JOIN_ROUND = 1;

    /**
     * Constant value for join style.
     */
    public static final int JOIN_BEVEL = 2;

    /**
     * Constant value for end cap style.
     */
    public static final int CAP_BUTT = 0;

    /**
     * Constant value for end cap style.
     */
    public static final int CAP_ROUND = 1;

    /**
     * Constant value for end cap style.
     */
    public static final int CAP_SQUARE = 2;

    private final LineSink output;

    private final int capStyle;
    private final int joinStyle;

    private final float m00, m01, m10, m11, det;

    private final float lineWidth2;
    private final float scaledLineWidth2;

    // For any pen offset (pen_dx, pen_dy) that does not depend on
    // the line orientation, the pen should be transformed so that:
    //
    // pen_dx' = m00*pen_dx + m01*pen_dy
    // pen_dy' = m10*pen_dx + m11*pen_dy
    //
    // For a round pen, this means:
    //
    // pen_dx(r, theta) = r*cos(theta)
    // pen_dy(r, theta) = r*sin(theta)
    //
    // pen_dx'(r, theta) = r*(m00*cos(theta) + m01*sin(theta))
    // pen_dy'(r, theta) = r*(m10*cos(theta) + m11*sin(theta))
    private int numPenSegments;
    private final float[] pen_dx;
    private final float[] pen_dy;
    private boolean[] penIncluded;
    private final float[] join;

    private final float[] offset = new float[2];
    private float[] reverse = new float[100];
    private final float[] miter = new float[2];
    private final float miterLimitSq;

    private int prev;
    private int rindex;
    private boolean started;
    private boolean lineToOrigin;
    private boolean joinToOrigin;

    private float sx0, sy0, sx1, sy1, x0, y0, px0, py0;
    private float mx0, my0, omx, omy;

    private float m00_2_m01_2;
    private float m10_2_m11_2;
    private float m00_m10_m01_m11;

    /**
     * Constructs a <code>Stroker</code>.
     *
     * @param output an output <code>LineSink</code>.
     * @param lineWidth the desired line width in pixels
     * @param capStyle the desired end cap style, one of
     * <code>CAP_BUTT</code>, <code>CAP_ROUND</code> or
     * <code>CAP_SQUARE</code>.
     * @param joinStyle the desired line join style, one of
     * <code>JOIN_MITER</code>, <code>JOIN_ROUND</code> or
     * <code>JOIN_BEVEL</code>.
     * @param miterLimit the desired miter limit
     * @param transform a <code>Transform4</code> object indicating
     * the transform that has been previously applied to all incoming
     * coordinates.  This is required in order to produce consistently
     * shaped end caps and joins.
     */
    public Stroker(LineSink output,
                   float lineWidth,
                   int capStyle,
                   int joinStyle,
                   float miterLimit,
                   float m00, float m01, float m10, float m11) {
        this.output = output;

        this.lineWidth2 = lineWidth / 2;
        this.scaledLineWidth2 = m00 * lineWidth2;
        this.capStyle = capStyle;
        this.joinStyle = joinStyle;

        m00_2_m01_2 = m00*m00 + m01*m01;
        m10_2_m11_2 = m10*m10 + m11*m11;
        m00_m10_m01_m11 = m00*m10 + m01*m11;

        this.m00 = m00;
        this.m01 = m01;
        this.m10 = m10;
        this.m11 = m11;
        det = m00*m11 - m01*m10;

        float limit = miterLimit * lineWidth2 * det;
        this.miterLimitSq = limit*limit;

        this.numPenSegments = (int)(3.14159f * lineWidth);
        this.pen_dx = new float[numPenSegments];
        this.pen_dy = new float[numPenSegments];
        this.penIncluded = new boolean[numPenSegments];
        this.join = new float[2*numPenSegments];

        for (int i = 0; i < numPenSegments; i++) {
            double theta = (i * 2.0 * Math.PI)/numPenSegments;

            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            pen_dx[i] = (float)(lineWidth2 * (m00*cos + m01*sin));
            pen_dy[i] = (float)(lineWidth2 * (m10*cos + m11*sin));
        }

        prev = CLOSE;
        rindex = 0;
        started = false;
        lineToOrigin = false;
    }

    private void computeOffset(float x0, float y0,
                               float x1, float y1, float[] m) {
        float lx = x1 - x0;
        float ly = y1 - y0;

        float dx, dy;
        if (m00 > 0 && m00 == m11 && m01 == 0 & m10 == 0) {
            float ilen = (float)Math.hypot(lx, ly);
            if (ilen == 0) {
                dx = dy = 0;
            } else {
                dx = (ly * scaledLineWidth2)/ilen;
                dy = -(lx * scaledLineWidth2)/ilen;
            }
        } else {
            int sdet = (det > 0) ? 1 : -1;
            float a = ly * m00 - lx * m10;
            float b = ly * m01 - lx * m11;
            float dh = (float)Math.hypot(a, b);
            float div = sdet * lineWidth2/dh;

            float ddx = ly * m00_2_m01_2 - lx * m00_m10_m01_m11;
            float ddy = ly * m00_m10_m01_m11 - lx * m10_2_m11_2;
            dx = ddx*div;
            dy = ddy*div;
        }

        m[0] = dx;
        m[1] = dy;
    }

    private void ensureCapacity(int newrindex) {
        if (reverse.length < newrindex) {
            reverse = java.util.Arrays.copyOf(reverse, 6*reverse.length/5);
        }
    }

    private boolean isCCW(float x0, float y0,
                          float x1, float y1,
                          float x2, float y2) {
        return (x1 - x0) * (y2 - y1) < (y1 - y0) * (x2 - x1);
    }

    private boolean side(float x,  float y,
                         float x0, float y0,
                         float x1, float y1) {
        return (y0 - y1)*x + (x1 - x0)*y + (x0*y1 - x1*y0) > 0;
    }

    private int computeRoundJoin(float cx, float cy,
                                 float xa, float ya,
                                 float xb, float yb,
                                 int side,
                                 boolean flip,
                                 float[] join) {
        float px, py;
        int ncoords = 0;

        boolean centerSide;
        if (side == 0) {
            centerSide = side(cx, cy, xa, ya, xb, yb);
        } else {
            centerSide = (side == 1);
        }
        for (int i = 0; i < numPenSegments; i++) {
            px = cx + pen_dx[i];
            py = cy + pen_dy[i];

            boolean penSide = side(px, py, xa, ya, xb, yb);
            penIncluded[i] = (penSide != centerSide);
        }

        int start = -1, end = -1;
        for (int i = 0; i < numPenSegments; i++) {
            if (penIncluded[i] &&
                !penIncluded[(i + numPenSegments - 1) % numPenSegments]) {
                start = i;
            }
            if (penIncluded[i] &&
                !penIncluded[(i + 1) % numPenSegments]) {
                end = i;
            }
        }

        if (end < start) {
            end += numPenSegments;
        }

        if (start != -1 && end != -1) {
            float dxa = cx + pen_dx[start] - xa;
            float dya = cy + pen_dy[start] - ya;
            float dxb = cx + pen_dx[start] - xb;
            float dyb = cy + pen_dy[start] - yb;

            boolean rev = (dxa*dxa + dya*dya > dxb*dxb + dyb*dyb);
            int i = rev ? end : start;
            int incr = rev ? -1 : 1;
            while (true) {
                int idx = i % numPenSegments;
                px = cx + pen_dx[idx];
                py = cy + pen_dy[idx];
                join[ncoords++] = px;
                join[ncoords++] = py;
                if (i == (rev ? start : end)) {
                    break;
                }
                i += incr;
            }
        }

        return ncoords/2;
    }

    // pisces used to use fixed point arithmetic with 16 decimal digits. I
    // didn't want to change the values of the constants below when I converted
    // it to floating point, so that's why the divisions by 2^16 are there.
    private static final float ROUND_JOIN_THRESHOLD = 1000/65536f;
    private static final float ROUND_JOIN_INTERNAL_THRESHOLD = 1000000000/65536f;

    private void drawRoundJoin(float x, float y,
                               float omx, float omy, float mx, float my,
                               int side,
                               boolean flip,
                               boolean rev,
                               float threshold) {
        if ((omx == 0 && omy == 0) || (mx == 0 && my == 0)) {
            return;
        }

        float domx = omx - mx;
        float domy = omy - my;
        float len = domx*domx + domy*domy;
        if (len < threshold) {
            return;
        }

        if (rev) {
            omx = -omx;
            omy = -omy;
            mx = -mx;
            my = -my;
        }

        float bx0 = x + omx;
        float by0 = y + omy;
        float bx1 = x + mx;
        float by1 = y + my;

        int npoints = computeRoundJoin(x, y,
                                       bx0, by0, bx1, by1, side, flip,
                                       join);
        for (int i = 0; i < npoints; i++) {
            emitLineTo(join[2*i], join[2*i + 1], rev);
        }
    }

    // Return the intersection point of the lines (ix0, iy0) -> (ix1, iy1)
    // and (ix0p, iy0p) -> (ix1p, iy1p) in m[0] and m[1]
    private void computeMiter(float x0, float y0, float x1, float y1,
                              float x0p, float y0p, float x1p, float y1p,
                              float[] m) {
        float x10 = x1 - x0;
        float y10 = y1 - y0;
        float x10p = x1p - x0p;
        float y10p = y1p - y0p;

        float den = x10*y10p - x10p*y10;
        if (den == 0) {
            m[0] = x0;
            m[1] = y0;
            return;
        }

        float t = x1p*(y0 - y0p) - x0*y10p + x0p*(y1p - y0);
        m[0] = x0 + (t*x10)/den;
        m[1] = y0 + (t*y10)/den;
    }

    private void drawMiter(float px0, float py0,
                           float x0, float y0,
                           float x1, float y1,
                           float omx, float omy, float mx, float my,
                           boolean rev) {
        if (mx == omx && my == omy) {
            return;
        }
        if (px0 == x0 && py0 == y0) {
            return;
        }
        if (x0 == x1 && y0 == y1) {
            return;
        }

        if (rev) {
            omx = -omx;
            omy = -omy;
            mx = -mx;
            my = -my;
        }

        computeMiter(px0 + omx, py0 + omy, x0 + omx, y0 + omy,
                     x0 + mx, y0 + my, x1 + mx, y1 + my,
                     miter);

        // Compute miter length in untransformed coordinates
        float dx = miter[0] - x0;
        float dy = miter[1] - y0;
        float a = dy*m00 - dx*m10;
        float b = dy*m01 - dx*m11;
        float lenSq = a*a + b*b;

        if (lenSq < miterLimitSq) {
            emitLineTo(miter[0], miter[1], rev);
        }
    }


    public void moveTo(float x0, float y0) {
        // System.out.println("Stroker.moveTo(" + x0/65536.0 + ", " + y0/65536.0 + ")");

        if (lineToOrigin) {
            // not closing the path, do the previous lineTo
            lineToImpl(sx0, sy0, joinToOrigin);
            lineToOrigin = false;
        }

        if (prev == LINE_TO) {
            finish();
        }

        this.sx0 = this.x0 = x0;
        this.sy0 = this.y0 = y0;
        this.rindex = 0;
        this.started = false;
        this.joinSegment = false;
        this.prev = MOVE_TO;
    }

    boolean joinSegment = false;

    public void lineJoin() {
        // System.out.println("Stroker.lineJoin()");
        this.joinSegment = true;
    }

    public void lineTo(float x1, float y1) {
        // System.out.println("Stroker.lineTo(" + x1/65536.0 + ", " + y1/65536.0 + ")");

        if (lineToOrigin) {
            if (x1 == sx0 && y1 == sy0) {
                // staying in the starting point
                return;
            }

            // not closing the path, do the previous lineTo
            lineToImpl(sx0, sy0, joinToOrigin);
            lineToOrigin = false;
        } else if (x1 == x0 && y1 == y0) {
            return;
        } else if (x1 == sx0 && y1 == sy0) {
            lineToOrigin = true;
            joinToOrigin = joinSegment;
            joinSegment = false;
            return;
        }

        lineToImpl(x1, y1, joinSegment);
        joinSegment = false;
    }

    private void lineToImpl(float x1, float y1, boolean joinSegment) {
        computeOffset(x0, y0, x1, y1, offset);
        float mx = offset[0];
        float my = offset[1];

        if (!started) {
            emitMoveTo(x0 + mx, y0 + my);
            this.sx1 = x1;
            this.sy1 = y1;
            this.mx0 = mx;
            this.my0 = my;
            started = true;
        } else {
            boolean ccw = isCCW(px0, py0, x0, y0, x1, y1);
            if (joinSegment) {
                if (joinStyle == JOIN_MITER) {
                    drawMiter(px0, py0, x0, y0, x1, y1, omx, omy, mx, my,
                              ccw);
                } else if (joinStyle == JOIN_ROUND) {
                    drawRoundJoin(x0, y0,
                                  omx, omy,
                                  mx, my, 0, false, ccw,
                                  ROUND_JOIN_THRESHOLD);
                }
            } else {
                // Draw internal joins as round
                drawRoundJoin(x0, y0,
                              omx, omy,
                              mx, my, 0, false, ccw,
                              ROUND_JOIN_INTERNAL_THRESHOLD);
            }

            emitLineTo(x0, y0, !ccw);
        }

        emitLineTo(x0 + mx, y0 + my, false);
        emitLineTo(x1 + mx, y1 + my, false);

        emitLineTo(x0 - mx, y0 - my, true);
        emitLineTo(x1 - mx, y1 - my, true);

        this.omx = mx;
        this.omy = my;
        this.px0 = x0;
        this.py0 = y0;
        this.x0 = x1;
        this.y0 = y1;
        this.prev = LINE_TO;
    }

    public void close() {
        // System.out.println("Stroker.close()");

        if (lineToOrigin) {
            // ignore the previous lineTo
            lineToOrigin = false;
        }

        if (!started) {
            finish();
            return;
        }

        computeOffset(x0, y0, sx0, sy0, offset);
        float mx = offset[0];
        float my = offset[1];

        // Draw penultimate join
        boolean ccw = isCCW(px0, py0, x0, y0, sx0, sy0);
        if (joinSegment) {
            if (joinStyle == JOIN_MITER) {
                drawMiter(px0, py0, x0, y0, sx0, sy0, omx, omy, mx, my, ccw);
            } else if (joinStyle == JOIN_ROUND) {
                drawRoundJoin(x0, y0, omx, omy, mx, my, 0, false, ccw,
                              ROUND_JOIN_THRESHOLD);
            }
        } else {
            // Draw internal joins as round
            drawRoundJoin(x0, y0,
                          omx, omy,
                          mx, my, 0, false, ccw,
                          ROUND_JOIN_INTERNAL_THRESHOLD);
        }

        emitLineTo(x0 + mx, y0 + my);
        emitLineTo(sx0 + mx, sy0 + my);

        ccw = isCCW(x0, y0, sx0, sy0, sx1, sy1);

        // Draw final join on the outside
        if (!ccw) {
            if (joinStyle == JOIN_MITER) {
                drawMiter(x0, y0, sx0, sy0, sx1, sy1,
                          mx, my, mx0, my0, false);
            } else if (joinStyle == JOIN_ROUND) {
                drawRoundJoin(sx0, sy0, mx, my, mx0, my0, 0, false, false,
                              ROUND_JOIN_THRESHOLD);
            }
        }

        emitLineTo(sx0 + mx0, sy0 + my0);
        emitLineTo(sx0 - mx0, sy0 - my0);  // same as reverse[0], reverse[1]

        // Draw final join on the inside
        if (ccw) {
            if (joinStyle == JOIN_MITER) {
                drawMiter(x0, y0, sx0, sy0, sx1, sy1,
                          -mx, -my, -mx0, -my0, false);
            } else if (joinStyle == JOIN_ROUND) {
                drawRoundJoin(sx0, sy0, -mx, -my, -mx0, -my0, 0,
                              true, false,
                              ROUND_JOIN_THRESHOLD);
            }
        }

        emitLineTo(sx0 - mx, sy0 - my);
        emitLineTo(x0 - mx, y0 - my);
        for (int i = rindex - 2; i >= 0; i -= 2) {
            emitLineTo(reverse[i], reverse[i + 1]);
        }

        this.x0 = this.sx0;
        this.y0 = this.sy0;
        this.rindex = 0;
        this.started = false;
        this.joinSegment = false;
        this.prev = CLOSE;
        emitClose();
    }

    public void end() {
        // System.out.println("Stroker.end()");

        if (lineToOrigin) {
            // not closing the path, do the previous lineTo
            lineToImpl(sx0, sy0, joinToOrigin);
            lineToOrigin = false;
        }

        if (prev == LINE_TO) {
            finish();
        }

        output.end();
        this.joinSegment = false;
        this.prev = MOVE_TO;
    }

    double userSpaceLineLength(double dx, double dy) {
        double a = (dy*m00 - dx*m10)/det;
        double b = (dy*m01 - dx*m11)/det;
        return Math.hypot(a, b);
    }

    private void finish() {
        if (capStyle == CAP_ROUND) {
            drawRoundJoin(x0, y0,
                          omx, omy, -omx, -omy, 1, false, false,
                          ROUND_JOIN_THRESHOLD);
        } else if (capStyle == CAP_SQUARE) {
            float dx = px0 - x0;
            float dy = py0 - y0;
            float len = (float)userSpaceLineLength(dx, dy);
            float s = lineWidth2/len;

            float capx = x0 - dx*s;
            float capy = y0 - dy*s;

            emitLineTo(capx + omx, capy + omy);
            emitLineTo(capx - omx, capy - omy);
        }

        for (int i = rindex - 2; i >= 0; i -= 2) {
            emitLineTo(reverse[i], reverse[i + 1]);
        }
        this.rindex = 0;

        if (capStyle == CAP_ROUND) {
            drawRoundJoin(sx0, sy0,
                          -mx0, -my0, mx0, my0, 1, false, false,
                          ROUND_JOIN_THRESHOLD);
        } else if (capStyle == CAP_SQUARE) {
            float dx = sx1 - sx0;
            float dy = sy1 - sy0;
            float len = (float)userSpaceLineLength(dx, dy);
            float s = lineWidth2/len;

            float capx = sx0 - dx*s;
            float capy = sy0 - dy*s;

            emitLineTo(capx - mx0, capy - my0);
            emitLineTo(capx + mx0, capy + my0);
        }

        emitClose();
        this.joinSegment = false;
    }

    private void emitMoveTo(float x0, float y0) {
        // System.out.println("Stroker.emitMoveTo(" + x0/65536.0 + ", " + y0/65536.0 + ")");
        output.moveTo(x0, y0);
    }

    private void emitLineTo(float x1, float y1) {
        // System.out.println("Stroker.emitLineTo(" + x0/65536.0 + ", " + y0/65536.0 + ")");
        output.lineTo(x1, y1);
    }

    private void emitLineTo(float x1, float y1, boolean rev) {
        if (rev) {
            ensureCapacity(rindex + 2);
            reverse[rindex++] = x1;
            reverse[rindex++] = y1;
        } else {
            emitLineTo(x1, y1);
        }
    }

    private void emitClose() {
        // System.out.println("Stroker.emitClose()");
        output.close();
    }
}

