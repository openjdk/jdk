/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import static java.lang.Math.ulp;
import static java.lang.Math.sqrt;

import sun.awt.geom.PathConsumer2D;
import sun.java2d.marlin.Curve.BreakPtrIterator;


// TODO: some of the arithmetic here is too verbose and prone to hard to
// debug typos. We should consider making a small Point/Vector class that
// has methods like plus(Point), minus(Point), dot(Point), cross(Point)and such
final class Stroker implements PathConsumer2D, MarlinConst {

    private static final int MOVE_TO = 0;
    private static final int DRAWING_OP_TO = 1; // ie. curve, line, or quad
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

    // pisces used to use fixed point arithmetic with 16 decimal digits. I
    // didn't want to change the values of the constant below when I converted
    // it to floating point, so that's why the divisions by 2^16 are there.
    private static final float ROUND_JOIN_THRESHOLD = 1000/65536f;

    private static final float C = 0.5522847498307933f;

    private static final int MAX_N_CURVES = 11;

    private PathConsumer2D out;

    private int capStyle;
    private int joinStyle;

    private float lineWidth2;
    private float invHalfLineWidth2Sq;

    private final float[] offset0 = new float[2];
    private final float[] offset1 = new float[2];
    private final float[] offset2 = new float[2];
    private final float[] miter = new float[2];
    private float miterLimitSq;

    private int prev;

    // The starting point of the path, and the slope there.
    private float sx0, sy0, sdx, sdy;
    // the current point and the slope there.
    private float cx0, cy0, cdx, cdy; // c stands for current
    // vectors that when added to (sx0,sy0) and (cx0,cy0) respectively yield the
    // first and last points on the left parallel path. Since this path is
    // parallel, it's slope at any point is parallel to the slope of the
    // original path (thought they may have different directions), so these
    // could be computed from sdx,sdy and cdx,cdy (and vice versa), but that
    // would be error prone and hard to read, so we keep these anyway.
    private float smx, smy, cmx, cmy;

    private final PolyStack reverse;

    // This is where the curve to be processed is put. We give it
    // enough room to store 2 curves: one for the current subdivision, the
    // other for the rest of the curve.
    private final float[] middle = new float[2 * 8];
    private final float[] lp = new float[8];
    private final float[] rp = new float[8];
    private final float[] subdivTs = new float[MAX_N_CURVES - 1];

    // per-thread renderer context
    final RendererContext rdrCtx;

    // dirty curve
    final Curve curve;

    /**
     * Constructs a <code>Stroker</code>.
     * @param rdrCtx per-thread renderer context
     */
    Stroker(final RendererContext rdrCtx) {
        this.rdrCtx = rdrCtx;

        this.reverse = new PolyStack(rdrCtx);
        this.curve = rdrCtx.curve;
    }

    /**
     * Inits the <code>Stroker</code>.
     *
     * @param pc2d an output <code>PathConsumer2D</code>.
     * @param lineWidth the desired line width in pixels
     * @param capStyle the desired end cap style, one of
     * <code>CAP_BUTT</code>, <code>CAP_ROUND</code> or
     * <code>CAP_SQUARE</code>.
     * @param joinStyle the desired line join style, one of
     * <code>JOIN_MITER</code>, <code>JOIN_ROUND</code> or
     * <code>JOIN_BEVEL</code>.
     * @param miterLimit the desired miter limit
     * @return this instance
     */
    Stroker init(PathConsumer2D pc2d,
              float lineWidth,
              int capStyle,
              int joinStyle,
              float miterLimit)
    {
        this.out = pc2d;

        this.lineWidth2 = lineWidth / 2f;
        this.invHalfLineWidth2Sq = 1f / (2f * lineWidth2 * lineWidth2);
        this.capStyle = capStyle;
        this.joinStyle = joinStyle;

        float limit = miterLimit * lineWidth2;
        this.miterLimitSq = limit * limit;

        this.prev = CLOSE;

        rdrCtx.stroking = 1;

        return this; // fluent API
    }

    /**
     * Disposes this stroker:
     * clean up before reusing this instance
     */
    void dispose() {
        reverse.dispose();

        if (doCleanDirty) {
            // Force zero-fill dirty arrays:
            Arrays.fill(offset0, 0f);
            Arrays.fill(offset1, 0f);
            Arrays.fill(offset2, 0f);
            Arrays.fill(miter, 0f);
            Arrays.fill(middle, 0f);
            Arrays.fill(lp, 0f);
            Arrays.fill(rp, 0f);
            Arrays.fill(subdivTs, 0f);
        }
    }

    private static void computeOffset(final float lx, final float ly,
                                      final float w, final float[] m)
    {
        float len = lx*lx + ly*ly;
        if (len == 0f) {
            m[0] = 0f;
            m[1] = 0f;
        } else {
            len = (float) sqrt(len);
            m[0] =  (ly * w) / len;
            m[1] = -(lx * w) / len;
        }
    }

    // Returns true if the vectors (dx1, dy1) and (dx2, dy2) are
    // clockwise (if dx1,dy1 needs to be rotated clockwise to close
    // the smallest angle between it and dx2,dy2).
    // This is equivalent to detecting whether a point q is on the right side
    // of a line passing through points p1, p2 where p2 = p1+(dx1,dy1) and
    // q = p2+(dx2,dy2), which is the same as saying p1, p2, q are in a
    // clockwise order.
    // NOTE: "clockwise" here assumes coordinates with 0,0 at the bottom left.
    private static boolean isCW(final float dx1, final float dy1,
                                final float dx2, final float dy2)
    {
        return dx1 * dy2 <= dy1 * dx2;
    }

    private void drawRoundJoin(float x, float y,
                               float omx, float omy, float mx, float my,
                               boolean rev,
                               float threshold)
    {
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
            mx  = -mx;
            my  = -my;
        }
        drawRoundJoin(x, y, omx, omy, mx, my, rev);
    }

    private void drawRoundJoin(float cx, float cy,
                               float omx, float omy,
                               float mx, float my,
                               boolean rev)
    {
        // The sign of the dot product of mx,my and omx,omy is equal to the
        // the sign of the cosine of ext
        // (ext is the angle between omx,omy and mx,my).
        final float cosext = omx * mx + omy * my;
        // If it is >=0, we know that abs(ext) is <= 90 degrees, so we only
        // need 1 curve to approximate the circle section that joins omx,omy
        // and mx,my.
        final int numCurves = (cosext >= 0f) ? 1 : 2;

        switch (numCurves) {
        case 1:
            drawBezApproxForArc(cx, cy, omx, omy, mx, my, rev);
            break;
        case 2:
            // we need to split the arc into 2 arcs spanning the same angle.
            // The point we want will be one of the 2 intersections of the
            // perpendicular bisector of the chord (omx,omy)->(mx,my) and the
            // circle. We could find this by scaling the vector
            // (omx+mx, omy+my)/2 so that it has length=lineWidth2 (and thus lies
            // on the circle), but that can have numerical problems when the angle
            // between omx,omy and mx,my is close to 180 degrees. So we compute a
            // normal of (omx,omy)-(mx,my). This will be the direction of the
            // perpendicular bisector. To get one of the intersections, we just scale
            // this vector that its length is lineWidth2 (this works because the
            // perpendicular bisector goes through the origin). This scaling doesn't
            // have numerical problems because we know that lineWidth2 divided by
            // this normal's length is at least 0.5 and at most sqrt(2)/2 (because
            // we know the angle of the arc is > 90 degrees).
            float nx = my - omy, ny = omx - mx;
            float nlen = (float) sqrt(nx*nx + ny*ny);
            float scale = lineWidth2/nlen;
            float mmx = nx * scale, mmy = ny * scale;

            // if (isCW(omx, omy, mx, my) != isCW(mmx, mmy, mx, my)) then we've
            // computed the wrong intersection so we get the other one.
            // The test above is equivalent to if (rev).
            if (rev) {
                mmx = -mmx;
                mmy = -mmy;
            }
            drawBezApproxForArc(cx, cy, omx, omy, mmx, mmy, rev);
            drawBezApproxForArc(cx, cy, mmx, mmy, mx, my, rev);
            break;
        default:
        }
    }

    // the input arc defined by omx,omy and mx,my must span <= 90 degrees.
    private void drawBezApproxForArc(final float cx, final float cy,
                                     final float omx, final float omy,
                                     final float mx, final float my,
                                     boolean rev)
    {
        final float cosext2 = (omx * mx + omy * my) * invHalfLineWidth2Sq;

        // check round off errors producing cos(ext) > 1 and a NaN below
        // cos(ext) == 1 implies colinear segments and an empty join anyway
        if (cosext2 >= 0.5f) {
            // just return to avoid generating a flat curve:
            return;
        }

        // cv is the length of P1-P0 and P2-P3 divided by the radius of the arc
        // (so, cv assumes the arc has radius 1). P0, P1, P2, P3 are the points that
        // define the bezier curve we're computing.
        // It is computed using the constraints that P1-P0 and P3-P2 are parallel
        // to the arc tangents at the endpoints, and that |P1-P0|=|P3-P2|.
        float cv = (float) ((4.0 / 3.0) * sqrt(0.5 - cosext2) /
                            (1.0 + sqrt(cosext2 + 0.5)));
        // if clockwise, we need to negate cv.
        if (rev) { // rev is equivalent to isCW(omx, omy, mx, my)
            cv = -cv;
        }
        final float x1 = cx + omx;
        final float y1 = cy + omy;
        final float x2 = x1 - cv * omy;
        final float y2 = y1 + cv * omx;

        final float x4 = cx + mx;
        final float y4 = cy + my;
        final float x3 = x4 + cv * my;
        final float y3 = y4 - cv * mx;

        emitCurveTo(x1, y1, x2, y2, x3, y3, x4, y4, rev);
    }

    private void drawRoundCap(float cx, float cy, float mx, float my) {
        // the first and second arguments of the following two calls
        // are really will be ignored by emitCurveTo (because of the false),
        // but we put them in anyway, as opposed to just giving it 4 zeroes,
        // because it's just 4 additions and it's not good to rely on this
        // sort of assumption (right now it's true, but that may change).
        emitCurveTo(cx+mx-C*my, cy+my+C*mx,
                    cx-my+C*mx, cy+mx+C*my,
                    cx-my,      cy+mx);
        emitCurveTo(cx-my-C*mx, cy+mx-C*my,
                    cx-mx-C*my, cy-my+C*mx,
                    cx-mx,      cy-my);
    }

    // Put the intersection point of the lines (x0, y0) -> (x1, y1)
    // and (x0p, y0p) -> (x1p, y1p) in m[off] and m[off+1].
    // If the lines are parallel, it will put a non finite number in m.
    private static void computeIntersection(final float x0, final float y0,
                                            final float x1, final float y1,
                                            final float x0p, final float y0p,
                                            final float x1p, final float y1p,
                                            final float[] m, int off)
    {
        float x10 = x1 - x0;
        float y10 = y1 - y0;
        float x10p = x1p - x0p;
        float y10p = y1p - y0p;

        float den = x10*y10p - x10p*y10;
        float t = x10p*(y0-y0p) - y10p*(x0-x0p);
        t /= den;
        m[off++] = x0 + t*x10;
        m[off]   = y0 + t*y10;
    }

    private void drawMiter(final float pdx, final float pdy,
                           final float x0, final float y0,
                           final float dx, final float dy,
                           float omx, float omy, float mx, float my,
                           boolean rev)
    {
        if ((mx == omx && my == omy) ||
            (pdx == 0f && pdy == 0f) ||
            (dx == 0f && dy == 0f))
        {
            return;
        }

        if (rev) {
            omx = -omx;
            omy = -omy;
            mx  = -mx;
            my  = -my;
        }

        computeIntersection((x0 - pdx) + omx, (y0 - pdy) + omy, x0 + omx, y0 + omy,
                            (dx + x0) + mx, (dy + y0) + my, x0 + mx, y0 + my,
                            miter, 0);

        final float miterX = miter[0];
        final float miterY = miter[1];
        float lenSq = (miterX-x0)*(miterX-x0) + (miterY-y0)*(miterY-y0);

        // If the lines are parallel, lenSq will be either NaN or +inf
        // (actually, I'm not sure if the latter is possible. The important
        // thing is that -inf is not possible, because lenSq is a square).
        // For both of those values, the comparison below will fail and
        // no miter will be drawn, which is correct.
        if (lenSq < miterLimitSq) {
            emitLineTo(miterX, miterY, rev);
        }
    }

    @Override
    public void moveTo(float x0, float y0) {
        if (prev == DRAWING_OP_TO) {
            finish();
        }
        this.sx0 = this.cx0 = x0;
        this.sy0 = this.cy0 = y0;
        this.cdx = this.sdx = 1;
        this.cdy = this.sdy = 0;
        this.prev = MOVE_TO;
    }

    @Override
    public void lineTo(float x1, float y1) {
        float dx = x1 - cx0;
        float dy = y1 - cy0;
        if (dx == 0f && dy == 0f) {
            dx = 1f;
        }
        computeOffset(dx, dy, lineWidth2, offset0);
        final float mx = offset0[0];
        final float my = offset0[1];

        drawJoin(cdx, cdy, cx0, cy0, dx, dy, cmx, cmy, mx, my);

        emitLineTo(cx0 + mx, cy0 + my);
        emitLineTo( x1 + mx,  y1 + my);

        emitLineToRev(cx0 - mx, cy0 - my);
        emitLineToRev( x1 - mx,  y1 - my);

        this.cmx = mx;
        this.cmy = my;
        this.cdx = dx;
        this.cdy = dy;
        this.cx0 = x1;
        this.cy0 = y1;
        this.prev = DRAWING_OP_TO;
    }

    @Override
    public void closePath() {
        if (prev != DRAWING_OP_TO) {
            if (prev == CLOSE) {
                return;
            }
            emitMoveTo(cx0, cy0 - lineWidth2);
            this.cmx = this.smx = 0;
            this.cmy = this.smy = -lineWidth2;
            this.cdx = this.sdx = 1;
            this.cdy = this.sdy = 0;
            finish();
            return;
        }

        if (cx0 != sx0 || cy0 != sy0) {
            lineTo(sx0, sy0);
        }

        drawJoin(cdx, cdy, cx0, cy0, sdx, sdy, cmx, cmy, smx, smy);

        emitLineTo(sx0 + smx, sy0 + smy);

        emitMoveTo(sx0 - smx, sy0 - smy);
        emitReverse();

        this.prev = CLOSE;
        emitClose();
    }

    private void emitReverse() {
        reverse.popAll(out);
    }

    @Override
    public void pathDone() {
        if (prev == DRAWING_OP_TO) {
            finish();
        }

        out.pathDone();

        // this shouldn't matter since this object won't be used
        // after the call to this method.
        this.prev = CLOSE;

        // Dispose this instance:
        dispose();
    }

    private void finish() {
        if (capStyle == CAP_ROUND) {
            drawRoundCap(cx0, cy0, cmx, cmy);
        } else if (capStyle == CAP_SQUARE) {
            emitLineTo(cx0 - cmy + cmx, cy0 + cmx + cmy);
            emitLineTo(cx0 - cmy - cmx, cy0 + cmx - cmy);
        }

        emitReverse();

        if (capStyle == CAP_ROUND) {
            drawRoundCap(sx0, sy0, -smx, -smy);
        } else if (capStyle == CAP_SQUARE) {
            emitLineTo(sx0 + smy - smx, sy0 - smx - smy);
            emitLineTo(sx0 + smy + smx, sy0 - smx + smy);
        }

        emitClose();
    }

    private void emitMoveTo(final float x0, final float y0) {
        out.moveTo(x0, y0);
    }

    private void emitLineTo(final float x1, final float y1) {
        out.lineTo(x1, y1);
    }

    private void emitLineToRev(final float x1, final float y1) {
        reverse.pushLine(x1, y1);
    }

    private void emitLineTo(final float x1, final float y1,
                            final boolean rev)
    {
        if (rev) {
            emitLineToRev(x1, y1);
        } else {
            emitLineTo(x1, y1);
        }
    }

    private void emitQuadTo(final float x1, final float y1,
                            final float x2, final float y2)
    {
        out.quadTo(x1, y1, x2, y2);
    }

    private void emitQuadToRev(final float x0, final float y0,
                               final float x1, final float y1)
    {
        reverse.pushQuad(x0, y0, x1, y1);
    }

    private void emitCurveTo(final float x1, final float y1,
                             final float x2, final float y2,
                             final float x3, final float y3)
    {
        out.curveTo(x1, y1, x2, y2, x3, y3);
    }

    private void emitCurveToRev(final float x0, final float y0,
                                final float x1, final float y1,
                                final float x2, final float y2)
    {
        reverse.pushCubic(x0, y0, x1, y1, x2, y2);
    }

    private void emitCurveTo(final float x0, final float y0,
                             final float x1, final float y1,
                             final float x2, final float y2,
                             final float x3, final float y3, final boolean rev)
    {
        if (rev) {
            reverse.pushCubic(x0, y0, x1, y1, x2, y2);
        } else {
            out.curveTo(x1, y1, x2, y2, x3, y3);
        }
    }

    private void emitClose() {
        out.closePath();
    }

    private void drawJoin(float pdx, float pdy,
                          float x0, float y0,
                          float dx, float dy,
                          float omx, float omy,
                          float mx, float my)
    {
        if (prev != DRAWING_OP_TO) {
            emitMoveTo(x0 + mx, y0 + my);
            this.sdx = dx;
            this.sdy = dy;
            this.smx = mx;
            this.smy = my;
        } else {
            boolean cw = isCW(pdx, pdy, dx, dy);
            if (joinStyle == JOIN_MITER) {
                drawMiter(pdx, pdy, x0, y0, dx, dy, omx, omy, mx, my, cw);
            } else if (joinStyle == JOIN_ROUND) {
                drawRoundJoin(x0, y0,
                              omx, omy,
                              mx, my, cw,
                              ROUND_JOIN_THRESHOLD);
            }
            emitLineTo(x0, y0, !cw);
        }
        prev = DRAWING_OP_TO;
    }

    private static boolean within(final float x1, final float y1,
                                  final float x2, final float y2,
                                  final float ERR)
    {
        assert ERR > 0 : "";
        // compare taxicab distance. ERR will always be small, so using
        // true distance won't give much benefit
        return (Helpers.within(x1, x2, ERR) &&  // we want to avoid calling Math.abs
                Helpers.within(y1, y2, ERR)); // this is just as good.
    }

    private void getLineOffsets(float x1, float y1,
                                float x2, float y2,
                                float[] left, float[] right) {
        computeOffset(x2 - x1, y2 - y1, lineWidth2, offset0);
        final float mx = offset0[0];
        final float my = offset0[1];
        left[0] = x1 + mx;
        left[1] = y1 + my;
        left[2] = x2 + mx;
        left[3] = y2 + my;
        right[0] = x1 - mx;
        right[1] = y1 - my;
        right[2] = x2 - mx;
        right[3] = y2 - my;
    }

    private int computeOffsetCubic(float[] pts, final int off,
                                   float[] leftOff, float[] rightOff)
    {
        // if p1=p2 or p3=p4 it means that the derivative at the endpoint
        // vanishes, which creates problems with computeOffset. Usually
        // this happens when this stroker object is trying to winden
        // a curve with a cusp. What happens is that curveTo splits
        // the input curve at the cusp, and passes it to this function.
        // because of inaccuracies in the splitting, we consider points
        // equal if they're very close to each other.
        final float x1 = pts[off + 0], y1 = pts[off + 1];
        final float x2 = pts[off + 2], y2 = pts[off + 3];
        final float x3 = pts[off + 4], y3 = pts[off + 5];
        final float x4 = pts[off + 6], y4 = pts[off + 7];

        float dx4 = x4 - x3;
        float dy4 = y4 - y3;
        float dx1 = x2 - x1;
        float dy1 = y2 - y1;

        // if p1 == p2 && p3 == p4: draw line from p1->p4, unless p1 == p4,
        // in which case ignore if p1 == p2
        final boolean p1eqp2 = within(x1,y1,x2,y2, 6f * ulp(y2));
        final boolean p3eqp4 = within(x3,y3,x4,y4, 6f * ulp(y4));
        if (p1eqp2 && p3eqp4) {
            getLineOffsets(x1, y1, x4, y4, leftOff, rightOff);
            return 4;
        } else if (p1eqp2) {
            dx1 = x3 - x1;
            dy1 = y3 - y1;
        } else if (p3eqp4) {
            dx4 = x4 - x2;
            dy4 = y4 - y2;
        }

        // if p2-p1 and p4-p3 are parallel, that must mean this curve is a line
        float dotsq = (dx1 * dx4 + dy1 * dy4);
        dotsq *= dotsq;
        float l1sq = dx1 * dx1 + dy1 * dy1, l4sq = dx4 * dx4 + dy4 * dy4;
        if (Helpers.within(dotsq, l1sq * l4sq, 4f * ulp(dotsq))) {
            getLineOffsets(x1, y1, x4, y4, leftOff, rightOff);
            return 4;
        }

//      What we're trying to do in this function is to approximate an ideal
//      offset curve (call it I) of the input curve B using a bezier curve Bp.
//      The constraints I use to get the equations are:
//
//      1. The computed curve Bp should go through I(0) and I(1). These are
//      x1p, y1p, x4p, y4p, which are p1p and p4p. We still need to find
//      4 variables: the x and y components of p2p and p3p (i.e. x2p, y2p, x3p, y3p).
//
//      2. Bp should have slope equal in absolute value to I at the endpoints. So,
//      (by the way, the operator || in the comments below means "aligned with".
//      It is defined on vectors, so when we say I'(0) || Bp'(0) we mean that
//      vectors I'(0) and Bp'(0) are aligned, which is the same as saying
//      that the tangent lines of I and Bp at 0 are parallel. Mathematically
//      this means (I'(t) || Bp'(t)) <==> (I'(t) = c * Bp'(t)) where c is some
//      nonzero constant.)
//      I'(0) || Bp'(0) and I'(1) || Bp'(1). Obviously, I'(0) || B'(0) and
//      I'(1) || B'(1); therefore, Bp'(0) || B'(0) and Bp'(1) || B'(1).
//      We know that Bp'(0) || (p2p-p1p) and Bp'(1) || (p4p-p3p) and the same
//      is true for any bezier curve; therefore, we get the equations
//          (1) p2p = c1 * (p2-p1) + p1p
//          (2) p3p = c2 * (p4-p3) + p4p
//      We know p1p, p4p, p2, p1, p3, and p4; therefore, this reduces the number
//      of unknowns from 4 to 2 (i.e. just c1 and c2).
//      To eliminate these 2 unknowns we use the following constraint:
//
//      3. Bp(0.5) == I(0.5). Bp(0.5)=(x,y) and I(0.5)=(xi,yi), and I should note
//      that I(0.5) is *the only* reason for computing dxm,dym. This gives us
//          (3) Bp(0.5) = (p1p + 3 * (p2p + p3p) + p4p)/8, which is equivalent to
//          (4) p2p + p3p = (Bp(0.5)*8 - p1p - p4p) / 3
//      We can substitute (1) and (2) from above into (4) and we get:
//          (5) c1*(p2-p1) + c2*(p4-p3) = (Bp(0.5)*8 - p1p - p4p)/3 - p1p - p4p
//      which is equivalent to
//          (6) c1*(p2-p1) + c2*(p4-p3) = (4/3) * (Bp(0.5) * 2 - p1p - p4p)
//
//      The right side of this is a 2D vector, and we know I(0.5), which gives us
//      Bp(0.5), which gives us the value of the right side.
//      The left side is just a matrix vector multiplication in disguise. It is
//
//      [x2-x1, x4-x3][c1]
//      [y2-y1, y4-y3][c2]
//      which, is equal to
//      [dx1, dx4][c1]
//      [dy1, dy4][c2]
//      At this point we are left with a simple linear system and we solve it by
//      getting the inverse of the matrix above. Then we use [c1,c2] to compute
//      p2p and p3p.

        float x = (x1 + 3f * (x2 + x3) + x4) / 8f;
        float y = (y1 + 3f * (y2 + y3) + y4) / 8f;
        // (dxm,dym) is some tangent of B at t=0.5. This means it's equal to
        // c*B'(0.5) for some constant c.
        float dxm = x3 + x4 - x1 - x2, dym = y3 + y4 - y1 - y2;

        // this computes the offsets at t=0, 0.5, 1, using the property that
        // for any bezier curve the vectors p2-p1 and p4-p3 are parallel to
        // the (dx/dt, dy/dt) vectors at the endpoints.
        computeOffset(dx1, dy1, lineWidth2, offset0);
        computeOffset(dxm, dym, lineWidth2, offset1);
        computeOffset(dx4, dy4, lineWidth2, offset2);
        float x1p = x1 + offset0[0]; // start
        float y1p = y1 + offset0[1]; // point
        float xi  = x  + offset1[0]; // interpolation
        float yi  = y  + offset1[1]; // point
        float x4p = x4 + offset2[0]; // end
        float y4p = y4 + offset2[1]; // point

        float invdet43 = 4f / (3f * (dx1 * dy4 - dy1 * dx4));

        float two_pi_m_p1_m_p4x = 2f * xi - x1p - x4p;
        float two_pi_m_p1_m_p4y = 2f * yi - y1p - y4p;
        float c1 = invdet43 * (dy4 * two_pi_m_p1_m_p4x - dx4 * two_pi_m_p1_m_p4y);
        float c2 = invdet43 * (dx1 * two_pi_m_p1_m_p4y - dy1 * two_pi_m_p1_m_p4x);

        float x2p, y2p, x3p, y3p;
        x2p = x1p + c1*dx1;
        y2p = y1p + c1*dy1;
        x3p = x4p + c2*dx4;
        y3p = y4p + c2*dy4;

        leftOff[0] = x1p; leftOff[1] = y1p;
        leftOff[2] = x2p; leftOff[3] = y2p;
        leftOff[4] = x3p; leftOff[5] = y3p;
        leftOff[6] = x4p; leftOff[7] = y4p;

        x1p = x1 - offset0[0]; y1p = y1 - offset0[1];
        xi = xi - 2f * offset1[0]; yi = yi - 2f * offset1[1];
        x4p = x4 - offset2[0]; y4p = y4 - offset2[1];

        two_pi_m_p1_m_p4x = 2f * xi - x1p - x4p;
        two_pi_m_p1_m_p4y = 2f * yi - y1p - y4p;
        c1 = invdet43 * (dy4 * two_pi_m_p1_m_p4x - dx4 * two_pi_m_p1_m_p4y);
        c2 = invdet43 * (dx1 * two_pi_m_p1_m_p4y - dy1 * two_pi_m_p1_m_p4x);

        x2p = x1p + c1*dx1;
        y2p = y1p + c1*dy1;
        x3p = x4p + c2*dx4;
        y3p = y4p + c2*dy4;

        rightOff[0] = x1p; rightOff[1] = y1p;
        rightOff[2] = x2p; rightOff[3] = y2p;
        rightOff[4] = x3p; rightOff[5] = y3p;
        rightOff[6] = x4p; rightOff[7] = y4p;
        return 8;
    }

    // return the kind of curve in the right and left arrays.
    private int computeOffsetQuad(float[] pts, final int off,
                                  float[] leftOff, float[] rightOff)
    {
        final float x1 = pts[off + 0], y1 = pts[off + 1];
        final float x2 = pts[off + 2], y2 = pts[off + 3];
        final float x3 = pts[off + 4], y3 = pts[off + 5];

        final float dx3 = x3 - x2;
        final float dy3 = y3 - y2;
        final float dx1 = x2 - x1;
        final float dy1 = y2 - y1;

        // this computes the offsets at t = 0, 1
        computeOffset(dx1, dy1, lineWidth2, offset0);
        computeOffset(dx3, dy3, lineWidth2, offset1);

        leftOff[0]  = x1 + offset0[0]; leftOff[1]  = y1 + offset0[1];
        leftOff[4]  = x3 + offset1[0]; leftOff[5]  = y3 + offset1[1];
        rightOff[0] = x1 - offset0[0]; rightOff[1] = y1 - offset0[1];
        rightOff[4] = x3 - offset1[0]; rightOff[5] = y3 - offset1[1];

        float x1p = leftOff[0]; // start
        float y1p = leftOff[1]; // point
        float x3p = leftOff[4]; // end
        float y3p = leftOff[5]; // point

        // Corner cases:
        // 1. If the two control vectors are parallel, we'll end up with NaN's
        //    in leftOff (and rightOff in the body of the if below), so we'll
        //    do getLineOffsets, which is right.
        // 2. If the first or second two points are equal, then (dx1,dy1)==(0,0)
        //    or (dx3,dy3)==(0,0), so (x1p, y1p)==(x1p+dx1, y1p+dy1)
        //    or (x3p, y3p)==(x3p-dx3, y3p-dy3), which means that
        //    computeIntersection will put NaN's in leftOff and right off, and
        //    we will do getLineOffsets, which is right.
        computeIntersection(x1p, y1p, x1p+dx1, y1p+dy1, x3p, y3p, x3p-dx3, y3p-dy3, leftOff, 2);
        float cx = leftOff[2];
        float cy = leftOff[3];

        if (!(isFinite(cx) && isFinite(cy))) {
            // maybe the right path is not degenerate.
            x1p = rightOff[0];
            y1p = rightOff[1];
            x3p = rightOff[4];
            y3p = rightOff[5];
            computeIntersection(x1p, y1p, x1p+dx1, y1p+dy1, x3p, y3p, x3p-dx3, y3p-dy3, rightOff, 2);
            cx = rightOff[2];
            cy = rightOff[3];
            if (!(isFinite(cx) && isFinite(cy))) {
                // both are degenerate. This curve is a line.
                getLineOffsets(x1, y1, x3, y3, leftOff, rightOff);
                return 4;
            }
            // {left,right}Off[0,1,4,5] are already set to the correct values.
            leftOff[2] = 2f * x2 - cx;
            leftOff[3] = 2f * y2 - cy;
            return 6;
        }

        // rightOff[2,3] = (x2,y2) - ((left_x2, left_y2) - (x2, y2))
        // == 2*(x2, y2) - (left_x2, left_y2)
        rightOff[2] = 2f * x2 - cx;
        rightOff[3] = 2f * y2 - cy;
        return 6;
    }

    private static boolean isFinite(float x) {
        return (Float.NEGATIVE_INFINITY < x && x < Float.POSITIVE_INFINITY);
    }

    // If this class is compiled with ecj, then Hotspot crashes when OSR
    // compiling this function. See bugs 7004570 and 6675699
    // TODO: until those are fixed, we should work around that by
    // manually inlining this into curveTo and quadTo.
/******************************* WORKAROUND **********************************
    private void somethingTo(final int type) {
        // need these so we can update the state at the end of this method
        final float xf = middle[type-2], yf = middle[type-1];
        float dxs = middle[2] - middle[0];
        float dys = middle[3] - middle[1];
        float dxf = middle[type - 2] - middle[type - 4];
        float dyf = middle[type - 1] - middle[type - 3];
        switch(type) {
        case 6:
            if ((dxs == 0f && dys == 0f) ||
                (dxf == 0f && dyf == 0f)) {
               dxs = dxf = middle[4] - middle[0];
               dys = dyf = middle[5] - middle[1];
            }
            break;
        case 8:
            boolean p1eqp2 = (dxs == 0f && dys == 0f);
            boolean p3eqp4 = (dxf == 0f && dyf == 0f);
            if (p1eqp2) {
                dxs = middle[4] - middle[0];
                dys = middle[5] - middle[1];
                if (dxs == 0f && dys == 0f) {
                    dxs = middle[6] - middle[0];
                    dys = middle[7] - middle[1];
                }
            }
            if (p3eqp4) {
                dxf = middle[6] - middle[2];
                dyf = middle[7] - middle[3];
                if (dxf == 0f && dyf == 0f) {
                    dxf = middle[6] - middle[0];
                    dyf = middle[7] - middle[1];
                }
            }
        }
        if (dxs == 0f && dys == 0f) {
            // this happens iff the "curve" is just a point
            lineTo(middle[0], middle[1]);
            return;
        }
        // if these vectors are too small, normalize them, to avoid future
        // precision problems.
        if (Math.abs(dxs) < 0.1f && Math.abs(dys) < 0.1f) {
            float len = (float) sqrt(dxs*dxs + dys*dys);
            dxs /= len;
            dys /= len;
        }
        if (Math.abs(dxf) < 0.1f && Math.abs(dyf) < 0.1f) {
            float len = (float) sqrt(dxf*dxf + dyf*dyf);
            dxf /= len;
            dyf /= len;
        }

        computeOffset(dxs, dys, lineWidth2, offset0);
        final float mx = offset0[0];
        final float my = offset0[1];
        drawJoin(cdx, cdy, cx0, cy0, dxs, dys, cmx, cmy, mx, my);

        int nSplits = findSubdivPoints(curve, middle, subdivTs, type, lineWidth2);

        int kind = 0;
        BreakPtrIterator it = curve.breakPtsAtTs(middle, type, subdivTs, nSplits);
        while(it.hasNext()) {
            int curCurveOff = it.next();

            switch (type) {
            case 8:
                kind = computeOffsetCubic(middle, curCurveOff, lp, rp);
                break;
            case 6:
                kind = computeOffsetQuad(middle, curCurveOff, lp, rp);
                break;
            }
            emitLineTo(lp[0], lp[1]);
            switch(kind) {
            case 8:
                emitCurveTo(lp[2], lp[3], lp[4], lp[5], lp[6], lp[7]);
                emitCurveToRev(rp[0], rp[1], rp[2], rp[3], rp[4], rp[5]);
                break;
            case 6:
                emitQuadTo(lp[2], lp[3], lp[4], lp[5]);
                emitQuadToRev(rp[0], rp[1], rp[2], rp[3]);
                break;
            case 4:
                emitLineTo(lp[2], lp[3]);
                emitLineTo(rp[0], rp[1], true);
                break;
            }
            emitLineTo(rp[kind - 2], rp[kind - 1], true);
        }

        this.cmx = (lp[kind - 2] - rp[kind - 2]) / 2;
        this.cmy = (lp[kind - 1] - rp[kind - 1]) / 2;
        this.cdx = dxf;
        this.cdy = dyf;
        this.cx0 = xf;
        this.cy0 = yf;
        this.prev = DRAWING_OP_TO;
    }
****************************** END WORKAROUND *******************************/

    // finds values of t where the curve in pts should be subdivided in order
    // to get good offset curves a distance of w away from the middle curve.
    // Stores the points in ts, and returns how many of them there were.
    private static int findSubdivPoints(final Curve c, float[] pts, float[] ts,
                                        final int type, final float w)
    {
        final float x12 = pts[2] - pts[0];
        final float y12 = pts[3] - pts[1];
        // if the curve is already parallel to either axis we gain nothing
        // from rotating it.
        if (y12 != 0f && x12 != 0f) {
            // we rotate it so that the first vector in the control polygon is
            // parallel to the x-axis. This will ensure that rotated quarter
            // circles won't be subdivided.
            final float hypot = (float) sqrt(x12 * x12 + y12 * y12);
            final float cos = x12 / hypot;
            final float sin = y12 / hypot;
            final float x1 = cos * pts[0] + sin * pts[1];
            final float y1 = cos * pts[1] - sin * pts[0];
            final float x2 = cos * pts[2] + sin * pts[3];
            final float y2 = cos * pts[3] - sin * pts[2];
            final float x3 = cos * pts[4] + sin * pts[5];
            final float y3 = cos * pts[5] - sin * pts[4];

            switch(type) {
            case 8:
                final float x4 = cos * pts[6] + sin * pts[7];
                final float y4 = cos * pts[7] - sin * pts[6];
                c.set(x1, y1, x2, y2, x3, y3, x4, y4);
                break;
            case 6:
                c.set(x1, y1, x2, y2, x3, y3);
                break;
            default:
            }
        } else {
            c.set(pts, type);
        }

        int ret = 0;
        // we subdivide at values of t such that the remaining rotated
        // curves are monotonic in x and y.
        ret += c.dxRoots(ts, ret);
        ret += c.dyRoots(ts, ret);
        // subdivide at inflection points.
        if (type == 8) {
            // quadratic curves can't have inflection points
            ret += c.infPoints(ts, ret);
        }

        // now we must subdivide at points where one of the offset curves will have
        // a cusp. This happens at ts where the radius of curvature is equal to w.
        ret += c.rootsOfROCMinusW(ts, ret, w, 0.0001f);

        ret = Helpers.filterOutNotInAB(ts, 0, ret, 0.0001f, 0.9999f);
        Helpers.isort(ts, 0, ret);
        return ret;
    }

    @Override public void curveTo(float x1, float y1,
                                  float x2, float y2,
                                  float x3, float y3)
    {
        final float[] mid = middle;

        mid[0] = cx0; mid[1] = cy0;
        mid[2] = x1;  mid[3] = y1;
        mid[4] = x2;  mid[5] = y2;
        mid[6] = x3;  mid[7] = y3;

        // inlined version of somethingTo(8);
        // See the TODO on somethingTo

        // need these so we can update the state at the end of this method
        final float xf = mid[6], yf = mid[7];
        float dxs = mid[2] - mid[0];
        float dys = mid[3] - mid[1];
        float dxf = mid[6] - mid[4];
        float dyf = mid[7] - mid[5];

        boolean p1eqp2 = (dxs == 0f && dys == 0f);
        boolean p3eqp4 = (dxf == 0f && dyf == 0f);
        if (p1eqp2) {
            dxs = mid[4] - mid[0];
            dys = mid[5] - mid[1];
            if (dxs == 0f && dys == 0f) {
                dxs = mid[6] - mid[0];
                dys = mid[7] - mid[1];
            }
        }
        if (p3eqp4) {
            dxf = mid[6] - mid[2];
            dyf = mid[7] - mid[3];
            if (dxf == 0f && dyf == 0f) {
                dxf = mid[6] - mid[0];
                dyf = mid[7] - mid[1];
            }
        }
        if (dxs == 0f && dys == 0f) {
            // this happens if the "curve" is just a point
            lineTo(mid[0], mid[1]);
            return;
        }

        // if these vectors are too small, normalize them, to avoid future
        // precision problems.
        if (Math.abs(dxs) < 0.1f && Math.abs(dys) < 0.1f) {
            float len = (float) sqrt(dxs*dxs + dys*dys);
            dxs /= len;
            dys /= len;
        }
        if (Math.abs(dxf) < 0.1f && Math.abs(dyf) < 0.1f) {
            float len = (float) sqrt(dxf*dxf + dyf*dyf);
            dxf /= len;
            dyf /= len;
        }

        computeOffset(dxs, dys, lineWidth2, offset0);
        drawJoin(cdx, cdy, cx0, cy0, dxs, dys, cmx, cmy, offset0[0], offset0[1]);

        int nSplits = findSubdivPoints(curve, mid, subdivTs, 8, lineWidth2);

        final float[] l = lp;
        final float[] r = rp;

        int kind = 0;
        BreakPtrIterator it = curve.breakPtsAtTs(mid, 8, subdivTs, nSplits);
        while(it.hasNext()) {
            int curCurveOff = it.next();

            kind = computeOffsetCubic(mid, curCurveOff, l, r);
            emitLineTo(l[0], l[1]);

            switch(kind) {
            case 8:
                emitCurveTo(l[2], l[3], l[4], l[5], l[6], l[7]);
                emitCurveToRev(r[0], r[1], r[2], r[3], r[4], r[5]);
                break;
            case 4:
                emitLineTo(l[2], l[3]);
                emitLineToRev(r[0], r[1]);
                break;
            default:
            }
            emitLineToRev(r[kind - 2], r[kind - 1]);
        }

        this.cmx = (l[kind - 2] - r[kind - 2]) / 2f;
        this.cmy = (l[kind - 1] - r[kind - 1]) / 2f;
        this.cdx = dxf;
        this.cdy = dyf;
        this.cx0 = xf;
        this.cy0 = yf;
        this.prev = DRAWING_OP_TO;
    }

    @Override public void quadTo(float x1, float y1, float x2, float y2) {
        final float[] mid = middle;

        mid[0] = cx0; mid[1] = cy0;
        mid[2] = x1;  mid[3] = y1;
        mid[4] = x2;  mid[5] = y2;

        // inlined version of somethingTo(8);
        // See the TODO on somethingTo

        // need these so we can update the state at the end of this method
        final float xf = mid[4], yf = mid[5];
        float dxs = mid[2] - mid[0];
        float dys = mid[3] - mid[1];
        float dxf = mid[4] - mid[2];
        float dyf = mid[5] - mid[3];
        if ((dxs == 0f && dys == 0f) || (dxf == 0f && dyf == 0f)) {
            dxs = dxf = mid[4] - mid[0];
            dys = dyf = mid[5] - mid[1];
        }
        if (dxs == 0f && dys == 0f) {
            // this happens if the "curve" is just a point
            lineTo(mid[0], mid[1]);
            return;
        }
        // if these vectors are too small, normalize them, to avoid future
        // precision problems.
        if (Math.abs(dxs) < 0.1f && Math.abs(dys) < 0.1f) {
            float len = (float) sqrt(dxs*dxs + dys*dys);
            dxs /= len;
            dys /= len;
        }
        if (Math.abs(dxf) < 0.1f && Math.abs(dyf) < 0.1f) {
            float len = (float) sqrt(dxf*dxf + dyf*dyf);
            dxf /= len;
            dyf /= len;
        }

        computeOffset(dxs, dys, lineWidth2, offset0);
        drawJoin(cdx, cdy, cx0, cy0, dxs, dys, cmx, cmy, offset0[0], offset0[1]);

        int nSplits = findSubdivPoints(curve, mid, subdivTs, 6, lineWidth2);

        final float[] l = lp;
        final float[] r = rp;

        int kind = 0;
        BreakPtrIterator it = curve.breakPtsAtTs(mid, 6, subdivTs, nSplits);
        while(it.hasNext()) {
            int curCurveOff = it.next();

            kind = computeOffsetQuad(mid, curCurveOff, l, r);
            emitLineTo(l[0], l[1]);

            switch(kind) {
            case 6:
                emitQuadTo(l[2], l[3], l[4], l[5]);
                emitQuadToRev(r[0], r[1], r[2], r[3]);
                break;
            case 4:
                emitLineTo(l[2], l[3]);
                emitLineToRev(r[0], r[1]);
                break;
            default:
            }
            emitLineToRev(r[kind - 2], r[kind - 1]);
        }

        this.cmx = (l[kind - 2] - r[kind - 2]) / 2f;
        this.cmy = (l[kind - 1] - r[kind - 1]) / 2f;
        this.cdx = dxf;
        this.cdy = dyf;
        this.cx0 = xf;
        this.cy0 = yf;
        this.prev = DRAWING_OP_TO;
    }

    @Override public long getNativeConsumer() {
        throw new InternalError("Stroker doesn't use a native consumer");
    }

    // a stack of polynomial curves where each curve shares endpoints with
    // adjacent ones.
    static final class PolyStack {
        private static final byte TYPE_LINETO  = (byte) 0;
        private static final byte TYPE_QUADTO  = (byte) 1;
        private static final byte TYPE_CUBICTO = (byte) 2;

        float[] curves;
        int end;
        byte[] curveTypes;
        int numCurves;

        // per-thread renderer context
        final RendererContext rdrCtx;

        // per-thread initial arrays (large enough to satisfy most usages: 8192)
        // +1 to avoid recycling in Helpers.widenArray()
        private final float[] curves_initial = new float[INITIAL_LARGE_ARRAY + 1]; // 32K
        private final byte[] curveTypes_initial = new byte[INITIAL_LARGE_ARRAY + 1]; // 8K

        // used marks (stats only)
        int curveTypesUseMark;
        int curvesUseMark;

        /**
         * Constructor
         * @param rdrCtx per-thread renderer context
         */
        PolyStack(final RendererContext rdrCtx) {
            this.rdrCtx = rdrCtx;

            curves = curves_initial;
            curveTypes = curveTypes_initial;
            end = 0;
            numCurves = 0;

            if (doStats) {
                curveTypesUseMark = 0;
                curvesUseMark = 0;
            }
        }

        /**
         * Disposes this PolyStack:
         * clean up before reusing this instance
         */
        void dispose() {
            end = 0;
            numCurves = 0;

            if (doStats) {
                RendererContext.stats.stat_rdr_poly_stack_types
                    .add(curveTypesUseMark);
                RendererContext.stats.stat_rdr_poly_stack_curves
                    .add(curvesUseMark);
                // reset marks
                curveTypesUseMark = 0;
                curvesUseMark = 0;
            }

            // Return arrays:
            // curves and curveTypes are kept dirty
            if (curves != curves_initial) {
                rdrCtx.putDirtyFloatArray(curves);
                curves = curves_initial;
            }

            if (curveTypes != curveTypes_initial) {
                rdrCtx.putDirtyByteArray(curveTypes);
                curveTypes = curveTypes_initial;
            }
        }

        private void ensureSpace(final int n) {
            // use substraction to avoid integer overflow:
            if (curves.length - end < n) {
                if (doStats) {
                    RendererContext.stats.stat_array_stroker_polystack_curves
                        .add(end + n);
                }
                curves = rdrCtx.widenDirtyFloatArray(curves, end, end + n);
            }
            if (curveTypes.length <= numCurves) {
                if (doStats) {
                    RendererContext.stats.stat_array_stroker_polystack_curveTypes
                        .add(numCurves + 1);
                }
                curveTypes = rdrCtx.widenDirtyByteArray(curveTypes,
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

        void popAll(PathConsumer2D io) {
            if (doStats) {
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
            int nc = numCurves;
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
            int e  = end;
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
                e -= len;
                ret += Arrays.toString(Arrays.copyOfRange(curves, e, e+len))
                                       + "\n";
            }
            return ret;
        }
    }
}
