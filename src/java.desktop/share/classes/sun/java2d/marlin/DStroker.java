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

import java.util.Arrays;
import sun.java2d.marlin.DHelpers.PolyStack;

// TODO: some of the arithmetic here is too verbose and prone to hard to
// debug typos. We should consider making a small Point/Vector class that
// has methods like plus(Point), minus(Point), dot(Point), cross(Point)and such
final class DStroker implements DPathConsumer2D, MarlinConst {

    private static final int MOVE_TO = 0;
    private static final int DRAWING_OP_TO = 1; // ie. curve, line, or quad
    private static final int CLOSE = 2;

    // pisces used to use fixed point arithmetic with 16 decimal digits. I
    // didn't want to change the values of the constant below when I converted
    // it to floating point, so that's why the divisions by 2^16 are there.
    private static final double ROUND_JOIN_THRESHOLD = 1000.0d/65536.0d;

    // kappa = (4/3) * (SQRT(2) - 1)
    private static final double C = (4.0d * (Math.sqrt(2.0d) - 1.0d) / 3.0d);

    // SQRT(2)
    private static final double SQRT_2 = Math.sqrt(2.0d);

    private static final int MAX_N_CURVES = 11;

    private DPathConsumer2D out;

    private int capStyle;
    private int joinStyle;

    private double lineWidth2;
    private double invHalfLineWidth2Sq;

    private final double[] offset0 = new double[2];
    private final double[] offset1 = new double[2];
    private final double[] offset2 = new double[2];
    private final double[] miter = new double[2];
    private double miterLimitSq;

    private int prev;

    // The starting point of the path, and the slope there.
    private double sx0, sy0, sdx, sdy;
    // the current point and the slope there.
    private double cx0, cy0, cdx, cdy; // c stands for current
    // vectors that when added to (sx0,sy0) and (cx0,cy0) respectively yield the
    // first and last points on the left parallel path. Since this path is
    // parallel, it's slope at any point is parallel to the slope of the
    // original path (thought they may have different directions), so these
    // could be computed from sdx,sdy and cdx,cdy (and vice versa), but that
    // would be error prone and hard to read, so we keep these anyway.
    private double smx, smy, cmx, cmy;

    private final PolyStack reverse;

    // This is where the curve to be processed is put. We give it
    // enough room to store all curves.
    private final double[] middle = new double[MAX_N_CURVES * 6 + 2];
    private final double[] lp = new double[8];
    private final double[] rp = new double[8];
    private final double[] subdivTs = new double[MAX_N_CURVES - 1];

    // per-thread renderer context
    final DRendererContext rdrCtx;

    // dirty curve
    final DCurve curve;

    // Bounds of the drawing region, at pixel precision.
    private double[] clipRect;

    // the outcode of the current point
    private int cOutCode = 0;

    // the outcode of the starting point
    private int sOutCode = 0;

    // flag indicating if the path is opened (clipped)
    private boolean opened = false;
    // flag indicating if the starting point's cap is done
    private boolean capStart = false;

    /**
     * Constructs a <code>DStroker</code>.
     * @param rdrCtx per-thread renderer context
     */
    DStroker(final DRendererContext rdrCtx) {
        this.rdrCtx = rdrCtx;

        this.reverse = (rdrCtx.stats != null) ?
            new PolyStack(rdrCtx,
                    rdrCtx.stats.stat_str_polystack_types,
                    rdrCtx.stats.stat_str_polystack_curves,
                    rdrCtx.stats.hist_str_polystack_curves,
                    rdrCtx.stats.stat_array_str_polystack_curves,
                    rdrCtx.stats.stat_array_str_polystack_types)
            : new PolyStack(rdrCtx);

        this.curve = rdrCtx.curve;
    }

    /**
     * Inits the <code>DStroker</code>.
     *
     * @param pc2d an output <code>DPathConsumer2D</code>.
     * @param lineWidth the desired line width in pixels
     * @param capStyle the desired end cap style, one of
     * <code>CAP_BUTT</code>, <code>CAP_ROUND</code> or
     * <code>CAP_SQUARE</code>.
     * @param joinStyle the desired line join style, one of
     * <code>JOIN_MITER</code>, <code>JOIN_ROUND</code> or
     * <code>JOIN_BEVEL</code>.
     * @param miterLimit the desired miter limit
     * @param scale scaling factor applied to clip boundaries
     * @return this instance
     */
    DStroker init(final DPathConsumer2D pc2d,
                  final double lineWidth,
                  final int capStyle,
                  final int joinStyle,
                  final double miterLimit,
                  final double scale)
    {
        this.out = pc2d;

        this.lineWidth2 = lineWidth / 2.0d;
        this.invHalfLineWidth2Sq = 1.0d / (2.0d * lineWidth2 * lineWidth2);
        this.capStyle = capStyle;
        this.joinStyle = joinStyle;

        final double limit = miterLimit * lineWidth2;
        this.miterLimitSq = limit * limit;

        this.prev = CLOSE;

        rdrCtx.stroking = 1;

        if (rdrCtx.doClip) {
            // Adjust the clipping rectangle with the stroker margin (miter limit, width)
            double rdrOffX = 0.0d, rdrOffY = 0.0d;
            double margin = lineWidth2;

            if (capStyle == CAP_SQUARE) {
                margin *= SQRT_2;
            }
            if ((joinStyle == JOIN_MITER) && (margin < limit)) {
                margin = limit;
            }
            if (scale != 1.0d) {
                margin *= scale;
                rdrOffX = scale * DRenderer.RDR_OFFSET_X;
                rdrOffY = scale * DRenderer.RDR_OFFSET_Y;
            }
            // add a small rounding error:
            margin += 1e-3d;

            // bounds as half-open intervals: minX <= x < maxX and minY <= y < maxY
            // adjust clip rectangle (ymin, ymax, xmin, xmax):
            final double[] _clipRect = rdrCtx.clipRect;
            _clipRect[0] -= margin - rdrOffY;
            _clipRect[1] += margin + rdrOffY;
            _clipRect[2] -= margin - rdrOffX;
            _clipRect[3] += margin + rdrOffX;
            this.clipRect = _clipRect;
        } else {
            this.clipRect = null;
            this.cOutCode = 0;
            this.sOutCode = 0;
        }
        return this; // fluent API
    }

    /**
     * Disposes this stroker:
     * clean up before reusing this instance
     */
    void dispose() {
        reverse.dispose();

        opened   = false;
        capStart = false;

        if (DO_CLEAN_DIRTY) {
            // Force zero-fill dirty arrays:
            Arrays.fill(offset0, 0.0d);
            Arrays.fill(offset1, 0.0d);
            Arrays.fill(offset2, 0.0d);
            Arrays.fill(miter, 0.0d);
            Arrays.fill(middle, 0.0d);
            Arrays.fill(lp, 0.0d);
            Arrays.fill(rp, 0.0d);
            Arrays.fill(subdivTs, 0.0d);
        }
    }

    private static void computeOffset(final double lx, final double ly,
                                      final double w, final double[] m)
    {
        double len = lx*lx + ly*ly;
        if (len == 0.0d) {
            m[0] = 0.0d;
            m[1] = 0.0d;
        } else {
            len = Math.sqrt(len);
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
    private static boolean isCW(final double dx1, final double dy1,
                                final double dx2, final double dy2)
    {
        return dx1 * dy2 <= dy1 * dx2;
    }

    private void drawRoundJoin(double x, double y,
                               double omx, double omy, double mx, double my,
                               boolean rev,
                               double threshold)
    {
        if ((omx == 0.0d && omy == 0.0d) || (mx == 0.0d && my == 0.0d)) {
            return;
        }

        double domx = omx - mx;
        double domy = omy - my;
        double len = domx*domx + domy*domy;
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

    private void drawRoundJoin(double cx, double cy,
                               double omx, double omy,
                               double mx, double my,
                               boolean rev)
    {
        // The sign of the dot product of mx,my and omx,omy is equal to the
        // the sign of the cosine of ext
        // (ext is the angle between omx,omy and mx,my).
        final double cosext = omx * mx + omy * my;
        // If it is >=0, we know that abs(ext) is <= 90 degrees, so we only
        // need 1 curve to approximate the circle section that joins omx,omy
        // and mx,my.
        final int numCurves = (cosext >= 0.0d) ? 1 : 2;

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
            double nx = my - omy, ny = omx - mx;
            double nlen = Math.sqrt(nx*nx + ny*ny);
            double scale = lineWidth2/nlen;
            double mmx = nx * scale, mmy = ny * scale;

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
    private void drawBezApproxForArc(final double cx, final double cy,
                                     final double omx, final double omy,
                                     final double mx, final double my,
                                     boolean rev)
    {
        final double cosext2 = (omx * mx + omy * my) * invHalfLineWidth2Sq;

        // check round off errors producing cos(ext) > 1 and a NaN below
        // cos(ext) == 1 implies colinear segments and an empty join anyway
        if (cosext2 >= 0.5d) {
            // just return to avoid generating a flat curve:
            return;
        }

        // cv is the length of P1-P0 and P2-P3 divided by the radius of the arc
        // (so, cv assumes the arc has radius 1). P0, P1, P2, P3 are the points that
        // define the bezier curve we're computing.
        // It is computed using the constraints that P1-P0 and P3-P2 are parallel
        // to the arc tangents at the endpoints, and that |P1-P0|=|P3-P2|.
        double cv = ((4.0d / 3.0d) * Math.sqrt(0.5d - cosext2) /
                            (1.0d + Math.sqrt(cosext2 + 0.5d)));
        // if clockwise, we need to negate cv.
        if (rev) { // rev is equivalent to isCW(omx, omy, mx, my)
            cv = -cv;
        }
        final double x1 = cx + omx;
        final double y1 = cy + omy;
        final double x2 = x1 - cv * omy;
        final double y2 = y1 + cv * omx;

        final double x4 = cx + mx;
        final double y4 = cy + my;
        final double x3 = x4 + cv * my;
        final double y3 = y4 - cv * mx;

        emitCurveTo(x1, y1, x2, y2, x3, y3, x4, y4, rev);
    }

    private void drawRoundCap(double cx, double cy, double mx, double my) {
        final double Cmx = C * mx;
        final double Cmy = C * my;
        emitCurveTo(cx + mx - Cmy, cy + my + Cmx,
                    cx - my + Cmx, cy + mx + Cmy,
                    cx - my,       cy + mx);
        emitCurveTo(cx - my - Cmx, cy + mx - Cmy,
                    cx - mx - Cmy, cy - my + Cmx,
                    cx - mx,       cy - my);
    }

    // Return the intersection point of the lines (x0, y0) -> (x1, y1)
    // and (x0p, y0p) -> (x1p, y1p) in m[off] and m[off+1]
    private static void computeMiter(final double x0, final double y0,
                                     final double x1, final double y1,
                                     final double x0p, final double y0p,
                                     final double x1p, final double y1p,
                                     final double[] m, int off)
    {
        double x10 = x1 - x0;
        double y10 = y1 - y0;
        double x10p = x1p - x0p;
        double y10p = y1p - y0p;

        // if this is 0, the lines are parallel. If they go in the
        // same direction, there is no intersection so m[off] and
        // m[off+1] will contain infinity, so no miter will be drawn.
        // If they go in the same direction that means that the start of the
        // current segment and the end of the previous segment have the same
        // tangent, in which case this method won't even be involved in
        // miter drawing because it won't be called by drawMiter (because
        // (mx == omx && my == omy) will be true, and drawMiter will return
        // immediately).
        double den = x10*y10p - x10p*y10;
        double t = x10p*(y0-y0p) - y10p*(x0-x0p);
        t /= den;
        m[off++] = x0 + t*x10;
        m[off]   = y0 + t*y10;
    }

    // Return the intersection point of the lines (x0, y0) -> (x1, y1)
    // and (x0p, y0p) -> (x1p, y1p) in m[off] and m[off+1]
    private static void safeComputeMiter(final double x0, final double y0,
                                         final double x1, final double y1,
                                         final double x0p, final double y0p,
                                         final double x1p, final double y1p,
                                         final double[] m, int off)
    {
        double x10 = x1 - x0;
        double y10 = y1 - y0;
        double x10p = x1p - x0p;
        double y10p = y1p - y0p;

        // if this is 0, the lines are parallel. If they go in the
        // same direction, there is no intersection so m[off] and
        // m[off+1] will contain infinity, so no miter will be drawn.
        // If they go in the same direction that means that the start of the
        // current segment and the end of the previous segment have the same
        // tangent, in which case this method won't even be involved in
        // miter drawing because it won't be called by drawMiter (because
        // (mx == omx && my == omy) will be true, and drawMiter will return
        // immediately).
        double den = x10*y10p - x10p*y10;
        if (den == 0.0d) {
            m[off++] = (x0 + x0p) / 2.0d;
            m[off]   = (y0 + y0p) / 2.0d;
            return;
        }
        double t = x10p*(y0-y0p) - y10p*(x0-x0p);
        t /= den;
        m[off++] = x0 + t*x10;
        m[off] = y0 + t*y10;
    }

    private void drawMiter(final double pdx, final double pdy,
                           final double x0, final double y0,
                           final double dx, final double dy,
                           double omx, double omy, double mx, double my,
                           boolean rev)
    {
        if ((mx == omx && my == omy) ||
            (pdx == 0.0d && pdy == 0.0d) ||
            (dx == 0.0d && dy == 0.0d))
        {
            return;
        }

        if (rev) {
            omx = -omx;
            omy = -omy;
            mx  = -mx;
            my  = -my;
        }

        computeMiter((x0 - pdx) + omx, (y0 - pdy) + omy, x0 + omx, y0 + omy,
                     (dx + x0) + mx, (dy + y0) + my, x0 + mx, y0 + my,
                     miter, 0);

        final double miterX = miter[0];
        final double miterY = miter[1];
        double lenSq = (miterX-x0)*(miterX-x0) + (miterY-y0)*(miterY-y0);

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
    public void moveTo(final double x0, final double y0) {
        moveTo(x0, y0, cOutCode);
        // update starting point:
        this.sx0 = x0;
        this.sy0 = y0;
        this.sdx = 1.0d;
        this.sdy = 0.0d;
        this.opened   = false;
        this.capStart = false;

        if (clipRect != null) {
            final int outcode = DHelpers.outcode(x0, y0, clipRect);
            this.cOutCode = outcode;
            this.sOutCode = outcode;
        }
    }

    private void moveTo(final double x0, final double y0,
                        final int outcode)
    {
        if (prev == MOVE_TO) {
            this.cx0 = x0;
            this.cy0 = y0;
        } else {
            if (prev == DRAWING_OP_TO) {
                finish(outcode);
            }
            this.prev = MOVE_TO;
            this.cx0 = x0;
            this.cy0 = y0;
            this.cdx = 1.0d;
            this.cdy = 0.0d;
        }
    }

    @Override
    public void lineTo(final double x1, final double y1) {
        lineTo(x1, y1, false);
    }

    private void lineTo(final double x1, final double y1,
                        final boolean force)
    {
        final int outcode0 = this.cOutCode;
        if (!force && clipRect != null) {
            final int outcode1 = DHelpers.outcode(x1, y1, clipRect);
            this.cOutCode = outcode1;

            // basic rejection criteria
            if ((outcode0 & outcode1) != 0) {
                moveTo(x1, y1, outcode0);
                opened = true;
                return;
            }
        }

        double dx = x1 - cx0;
        double dy = y1 - cy0;
        if (dx == 0.0d && dy == 0.0d) {
            dx = 1.0d;
        }
        computeOffset(dx, dy, lineWidth2, offset0);
        final double mx = offset0[0];
        final double my = offset0[1];

        drawJoin(cdx, cdy, cx0, cy0, dx, dy, cmx, cmy, mx, my, outcode0);

        emitLineTo(cx0 + mx, cy0 + my);
        emitLineTo( x1 + mx,  y1 + my);

        emitLineToRev(cx0 - mx, cy0 - my);
        emitLineToRev( x1 - mx,  y1 - my);

        this.prev = DRAWING_OP_TO;
        this.cx0 = x1;
        this.cy0 = y1;
        this.cdx = dx;
        this.cdy = dy;
        this.cmx = mx;
        this.cmy = my;
    }

    @Override
    public void closePath() {
        // distinguish empty path at all vs opened path ?
        if (prev != DRAWING_OP_TO && !opened) {
            if (prev == CLOSE) {
                return;
            }
            emitMoveTo(cx0, cy0 - lineWidth2);

            this.sdx = 1.0d;
            this.sdy = 0.0d;
            this.cdx = 1.0d;
            this.cdy = 0.0d;

            this.smx = 0.0d;
            this.smy = -lineWidth2;
            this.cmx = 0.0d;
            this.cmy = -lineWidth2;

            finish(cOutCode);
            return;
        }

        // basic acceptance criteria
        if ((sOutCode & cOutCode) == 0) {
            if (cx0 != sx0 || cy0 != sy0) {
                lineTo(sx0, sy0, true);
            }

            drawJoin(cdx, cdy, cx0, cy0, sdx, sdy, cmx, cmy, smx, smy, sOutCode);

            emitLineTo(sx0 + smx, sy0 + smy);

            if (opened) {
                emitLineTo(sx0 - smx, sy0 - smy);
            } else {
                emitMoveTo(sx0 - smx, sy0 - smy);
            }
        }
        // Ignore caps like finish(false)
        emitReverse();

        this.prev = CLOSE;

        if (opened) {
            // do not emit close
            opened = false;
        } else {
            emitClose();
        }
    }

    private void emitReverse() {
        reverse.popAll(out);
    }

    @Override
    public void pathDone() {
        if (prev == DRAWING_OP_TO) {
            finish(cOutCode);
        }

        out.pathDone();

        // this shouldn't matter since this object won't be used
        // after the call to this method.
        this.prev = CLOSE;

        // Dispose this instance:
        dispose();
    }

    private void finish(final int outcode) {
        // Problem: impossible to guess if the path will be closed in advance
        //          i.e. if caps must be drawn or not ?
        // Solution: use the ClosedPathDetector before Stroker to determine
        // if the path is a closed path or not
        if (!rdrCtx.closedPath) {
            if (outcode == 0) {
                // current point = end's cap:
                if (capStyle == CAP_ROUND) {
                    drawRoundCap(cx0, cy0, cmx, cmy);
                } else if (capStyle == CAP_SQUARE) {
                    emitLineTo(cx0 - cmy + cmx, cy0 + cmx + cmy);
                    emitLineTo(cx0 - cmy - cmx, cy0 + cmx - cmy);
                }
            }
            emitReverse();

            if (!capStart) {
                capStart = true;

                if (sOutCode == 0) {
                    // starting point = initial cap:
                    if (capStyle == CAP_ROUND) {
                        drawRoundCap(sx0, sy0, -smx, -smy);
                    } else if (capStyle == CAP_SQUARE) {
                        emitLineTo(sx0 + smy - smx, sy0 - smx - smy);
                        emitLineTo(sx0 + smy + smx, sy0 - smx + smy);
                    }
                }
            }
        } else {
            emitReverse();
        }
        emitClose();
    }

    private void emitMoveTo(final double x0, final double y0) {
        out.moveTo(x0, y0);
    }

    private void emitLineTo(final double x1, final double y1) {
        out.lineTo(x1, y1);
    }

    private void emitLineToRev(final double x1, final double y1) {
        reverse.pushLine(x1, y1);
    }

    private void emitLineTo(final double x1, final double y1,
                            final boolean rev)
    {
        if (rev) {
            emitLineToRev(x1, y1);
        } else {
            emitLineTo(x1, y1);
        }
    }

    private void emitQuadTo(final double x1, final double y1,
                            final double x2, final double y2)
    {
        out.quadTo(x1, y1, x2, y2);
    }

    private void emitQuadToRev(final double x0, final double y0,
                               final double x1, final double y1)
    {
        reverse.pushQuad(x0, y0, x1, y1);
    }

    private void emitCurveTo(final double x1, final double y1,
                             final double x2, final double y2,
                             final double x3, final double y3)
    {
        out.curveTo(x1, y1, x2, y2, x3, y3);
    }

    private void emitCurveToRev(final double x0, final double y0,
                                final double x1, final double y1,
                                final double x2, final double y2)
    {
        reverse.pushCubic(x0, y0, x1, y1, x2, y2);
    }

    private void emitCurveTo(final double x0, final double y0,
                             final double x1, final double y1,
                             final double x2, final double y2,
                             final double x3, final double y3, final boolean rev)
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

    private void drawJoin(double pdx, double pdy,
                          double x0, double y0,
                          double dx, double dy,
                          double omx, double omy,
                          double mx, double my,
                          final int outcode)
    {
        if (prev != DRAWING_OP_TO) {
            emitMoveTo(x0 + mx, y0 + my);
            if (!opened) {
                this.sdx = dx;
                this.sdy = dy;
                this.smx = mx;
                this.smy = my;
            }
        } else {
            final boolean cw = isCW(pdx, pdy, dx, dy);
            if (outcode == 0) {
                if (joinStyle == JOIN_MITER) {
                    drawMiter(pdx, pdy, x0, y0, dx, dy, omx, omy, mx, my, cw);
                } else if (joinStyle == JOIN_ROUND) {
                    drawRoundJoin(x0, y0,
                                  omx, omy,
                                  mx, my, cw,
                                  ROUND_JOIN_THRESHOLD);
                }
            }
            emitLineTo(x0, y0, !cw);
        }
        prev = DRAWING_OP_TO;
    }

    private static boolean within(final double x1, final double y1,
                                  final double x2, final double y2,
                                  final double ERR)
    {
        assert ERR > 0 : "";
        // compare taxicab distance. ERR will always be small, so using
        // true distance won't give much benefit
        return (DHelpers.within(x1, x2, ERR) &&  // we want to avoid calling Math.abs
                DHelpers.within(y1, y2, ERR)); // this is just as good.
    }

    private void getLineOffsets(double x1, double y1,
                                double x2, double y2,
                                double[] left, double[] right) {
        computeOffset(x2 - x1, y2 - y1, lineWidth2, offset0);
        final double mx = offset0[0];
        final double my = offset0[1];
        left[0] = x1 + mx;
        left[1] = y1 + my;
        left[2] = x2 + mx;
        left[3] = y2 + my;
        right[0] = x1 - mx;
        right[1] = y1 - my;
        right[2] = x2 - mx;
        right[3] = y2 - my;
    }

    private int computeOffsetCubic(double[] pts, final int off,
                                   double[] leftOff, double[] rightOff)
    {
        // if p1=p2 or p3=p4 it means that the derivative at the endpoint
        // vanishes, which creates problems with computeOffset. Usually
        // this happens when this stroker object is trying to widen
        // a curve with a cusp. What happens is that curveTo splits
        // the input curve at the cusp, and passes it to this function.
        // because of inaccuracies in the splitting, we consider points
        // equal if they're very close to each other.
        final double x1 = pts[off + 0], y1 = pts[off + 1];
        final double x2 = pts[off + 2], y2 = pts[off + 3];
        final double x3 = pts[off + 4], y3 = pts[off + 5];
        final double x4 = pts[off + 6], y4 = pts[off + 7];

        double dx4 = x4 - x3;
        double dy4 = y4 - y3;
        double dx1 = x2 - x1;
        double dy1 = y2 - y1;

        // if p1 == p2 && p3 == p4: draw line from p1->p4, unless p1 == p4,
        // in which case ignore if p1 == p2
        final boolean p1eqp2 = within(x1, y1, x2, y2, 6.0d * Math.ulp(y2));
        final boolean p3eqp4 = within(x3, y3, x4, y4, 6.0d * Math.ulp(y4));
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
        double dotsq = (dx1 * dx4 + dy1 * dy4);
        dotsq *= dotsq;
        double l1sq = dx1 * dx1 + dy1 * dy1, l4sq = dx4 * dx4 + dy4 * dy4;
        if (DHelpers.within(dotsq, l1sq * l4sq, 4.0d * Math.ulp(dotsq))) {
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

        double x = (x1 + 3.0d * (x2 + x3) + x4) / 8.0d;
        double y = (y1 + 3.0d * (y2 + y3) + y4) / 8.0d;
        // (dxm,dym) is some tangent of B at t=0.5. This means it's equal to
        // c*B'(0.5) for some constant c.
        double dxm = x3 + x4 - x1 - x2, dym = y3 + y4 - y1 - y2;

        // this computes the offsets at t=0, 0.5, 1, using the property that
        // for any bezier curve the vectors p2-p1 and p4-p3 are parallel to
        // the (dx/dt, dy/dt) vectors at the endpoints.
        computeOffset(dx1, dy1, lineWidth2, offset0);
        computeOffset(dxm, dym, lineWidth2, offset1);
        computeOffset(dx4, dy4, lineWidth2, offset2);
        double x1p = x1 + offset0[0]; // start
        double y1p = y1 + offset0[1]; // point
        double xi  = x  + offset1[0]; // interpolation
        double yi  = y  + offset1[1]; // point
        double x4p = x4 + offset2[0]; // end
        double y4p = y4 + offset2[1]; // point

        double invdet43 = 4.0d / (3.0d * (dx1 * dy4 - dy1 * dx4));

        double two_pi_m_p1_m_p4x = 2.0d * xi - x1p - x4p;
        double two_pi_m_p1_m_p4y = 2.0d * yi - y1p - y4p;
        double c1 = invdet43 * (dy4 * two_pi_m_p1_m_p4x - dx4 * two_pi_m_p1_m_p4y);
        double c2 = invdet43 * (dx1 * two_pi_m_p1_m_p4y - dy1 * two_pi_m_p1_m_p4x);

        double x2p, y2p, x3p, y3p;
        x2p = x1p + c1*dx1;
        y2p = y1p + c1*dy1;
        x3p = x4p + c2*dx4;
        y3p = y4p + c2*dy4;

        leftOff[0] = x1p; leftOff[1] = y1p;
        leftOff[2] = x2p; leftOff[3] = y2p;
        leftOff[4] = x3p; leftOff[5] = y3p;
        leftOff[6] = x4p; leftOff[7] = y4p;

        x1p = x1 - offset0[0]; y1p = y1 - offset0[1];
        xi = xi - 2.0d * offset1[0]; yi = yi - 2.0d * offset1[1];
        x4p = x4 - offset2[0]; y4p = y4 - offset2[1];

        two_pi_m_p1_m_p4x = 2.0d * xi - x1p - x4p;
        two_pi_m_p1_m_p4y = 2.0d * yi - y1p - y4p;
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

    // compute offset curves using bezier spline through t=0.5 (i.e.
    // ComputedCurve(0.5) == IdealParallelCurve(0.5))
    // return the kind of curve in the right and left arrays.
    private int computeOffsetQuad(double[] pts, final int off,
                                  double[] leftOff, double[] rightOff)
    {
        final double x1 = pts[off + 0], y1 = pts[off + 1];
        final double x2 = pts[off + 2], y2 = pts[off + 3];
        final double x3 = pts[off + 4], y3 = pts[off + 5];

        final double dx3 = x3 - x2;
        final double dy3 = y3 - y2;
        final double dx1 = x2 - x1;
        final double dy1 = y2 - y1;

        // if p1=p2 or p3=p4 it means that the derivative at the endpoint
        // vanishes, which creates problems with computeOffset. Usually
        // this happens when this stroker object is trying to widen
        // a curve with a cusp. What happens is that curveTo splits
        // the input curve at the cusp, and passes it to this function.
        // because of inaccuracies in the splitting, we consider points
        // equal if they're very close to each other.

        // if p1 == p2 && p3 == p4: draw line from p1->p4, unless p1 == p4,
        // in which case ignore.
        final boolean p1eqp2 = within(x1, y1, x2, y2, 6.0d * Math.ulp(y2));
        final boolean p2eqp3 = within(x2, y2, x3, y3, 6.0d * Math.ulp(y3));
        if (p1eqp2 || p2eqp3) {
            getLineOffsets(x1, y1, x3, y3, leftOff, rightOff);
            return 4;
        }

        // if p2-p1 and p4-p3 are parallel, that must mean this curve is a line
        double dotsq = (dx1 * dx3 + dy1 * dy3);
        dotsq *= dotsq;
        double l1sq = dx1 * dx1 + dy1 * dy1, l3sq = dx3 * dx3 + dy3 * dy3;
        if (DHelpers.within(dotsq, l1sq * l3sq, 4.0d * Math.ulp(dotsq))) {
            getLineOffsets(x1, y1, x3, y3, leftOff, rightOff);
            return 4;
        }

        // this computes the offsets at t=0, 0.5, 1, using the property that
        // for any bezier curve the vectors p2-p1 and p4-p3 are parallel to
        // the (dx/dt, dy/dt) vectors at the endpoints.
        computeOffset(dx1, dy1, lineWidth2, offset0);
        computeOffset(dx3, dy3, lineWidth2, offset1);

        double x1p = x1 + offset0[0]; // start
        double y1p = y1 + offset0[1]; // point
        double x3p = x3 + offset1[0]; // end
        double y3p = y3 + offset1[1]; // point
        safeComputeMiter(x1p, y1p, x1p+dx1, y1p+dy1, x3p, y3p, x3p-dx3, y3p-dy3, leftOff, 2);
        leftOff[0] = x1p; leftOff[1] = y1p;
        leftOff[4] = x3p; leftOff[5] = y3p;

        x1p = x1 - offset0[0]; y1p = y1 - offset0[1];
        x3p = x3 - offset1[0]; y3p = y3 - offset1[1];
        safeComputeMiter(x1p, y1p, x1p+dx1, y1p+dy1, x3p, y3p, x3p-dx3, y3p-dy3, rightOff, 2);
        rightOff[0] = x1p; rightOff[1] = y1p;
        rightOff[4] = x3p; rightOff[5] = y3p;
        return 6;
    }

    // finds values of t where the curve in pts should be subdivided in order
    // to get good offset curves a distance of w away from the middle curve.
    // Stores the points in ts, and returns how many of them there were.
    private static int findSubdivPoints(final DCurve c, double[] pts, double[] ts,
                                        final int type, final double w)
    {
        final double x12 = pts[2] - pts[0];
        final double y12 = pts[3] - pts[1];
        // if the curve is already parallel to either axis we gain nothing
        // from rotating it.
        if (y12 != 0.0d && x12 != 0.0d) {
            // we rotate it so that the first vector in the control polygon is
            // parallel to the x-axis. This will ensure that rotated quarter
            // circles won't be subdivided.
            final double hypot = Math.sqrt(x12 * x12 + y12 * y12);
            final double cos = x12 / hypot;
            final double sin = y12 / hypot;
            final double x1 = cos * pts[0] + sin * pts[1];
            final double y1 = cos * pts[1] - sin * pts[0];
            final double x2 = cos * pts[2] + sin * pts[3];
            final double y2 = cos * pts[3] - sin * pts[2];
            final double x3 = cos * pts[4] + sin * pts[5];
            final double y3 = cos * pts[5] - sin * pts[4];

            switch(type) {
            case 8:
                final double x4 = cos * pts[6] + sin * pts[7];
                final double y4 = cos * pts[7] - sin * pts[6];
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
        ret += c.rootsOfROCMinusW(ts, ret, w, 0.0001d);

        ret = DHelpers.filterOutNotInAB(ts, 0, ret, 0.0001d, 0.9999d);
        DHelpers.isort(ts, 0, ret);
        return ret;
    }

    @Override
    public void curveTo(final double x1, final double y1,
                        final double x2, final double y2,
                        final double x3, final double y3)
    {
        final int outcode0 = this.cOutCode;
        if (clipRect != null) {
            final int outcode3 = DHelpers.outcode(x3, y3, clipRect);
            this.cOutCode = outcode3;

            if ((outcode0 & outcode3) != 0) {
                final int outcode1 = DHelpers.outcode(x1, y1, clipRect);
                final int outcode2 = DHelpers.outcode(x2, y2, clipRect);

                // basic rejection criteria
                if ((outcode0 & outcode1 & outcode2 & outcode3) != 0) {
                    moveTo(x3, y3, outcode0);
                    opened = true;
                    return;
                }
            }
        }

        final double[] mid = middle;

        mid[0] = cx0; mid[1] = cy0;
        mid[2] = x1;  mid[3] = y1;
        mid[4] = x2;  mid[5] = y2;
        mid[6] = x3;  mid[7] = y3;

        // need these so we can update the state at the end of this method
        final double xf = x3, yf = y3;
        double dxs = mid[2] - mid[0];
        double dys = mid[3] - mid[1];
        double dxf = mid[6] - mid[4];
        double dyf = mid[7] - mid[5];

        boolean p1eqp2 = (dxs == 0.0d && dys == 0.0d);
        boolean p3eqp4 = (dxf == 0.0d && dyf == 0.0d);
        if (p1eqp2) {
            dxs = mid[4] - mid[0];
            dys = mid[5] - mid[1];
            if (dxs == 0.0d && dys == 0.0d) {
                dxs = mid[6] - mid[0];
                dys = mid[7] - mid[1];
            }
        }
        if (p3eqp4) {
            dxf = mid[6] - mid[2];
            dyf = mid[7] - mid[3];
            if (dxf == 0.0d && dyf == 0.0d) {
                dxf = mid[6] - mid[0];
                dyf = mid[7] - mid[1];
            }
        }
        if (dxs == 0.0d && dys == 0.0d) {
            // this happens if the "curve" is just a point
            // fix outcode0 for lineTo() call:
            if (clipRect != null) {
                this.cOutCode = outcode0;
            }
            lineTo(mid[0], mid[1]);
            return;
        }

        // if these vectors are too small, normalize them, to avoid future
        // precision problems.
        if (Math.abs(dxs) < 0.1d && Math.abs(dys) < 0.1d) {
            double len = Math.sqrt(dxs*dxs + dys*dys);
            dxs /= len;
            dys /= len;
        }
        if (Math.abs(dxf) < 0.1d && Math.abs(dyf) < 0.1d) {
            double len = Math.sqrt(dxf*dxf + dyf*dyf);
            dxf /= len;
            dyf /= len;
        }

        computeOffset(dxs, dys, lineWidth2, offset0);
        drawJoin(cdx, cdy, cx0, cy0, dxs, dys, cmx, cmy, offset0[0], offset0[1], outcode0);

        final int nSplits = findSubdivPoints(curve, mid, subdivTs, 8, lineWidth2);

        double prevT = 0.0d;
        for (int i = 0, off = 0; i < nSplits; i++, off += 6) {
            final double t = subdivTs[i];
            DHelpers.subdivideCubicAt((t - prevT) / (1.0d - prevT),
                                     mid, off, mid, off, mid, off + 6);
            prevT = t;
        }

        final double[] l = lp;
        final double[] r = rp;

        int kind = 0;
        for (int i = 0, off = 0; i <= nSplits; i++, off += 6) {
            kind = computeOffsetCubic(mid, off, l, r);

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

        this.prev = DRAWING_OP_TO;
        this.cx0 = xf;
        this.cy0 = yf;
        this.cdx = dxf;
        this.cdy = dyf;
        this.cmx = (l[kind - 2] - r[kind - 2]) / 2.0d;
        this.cmy = (l[kind - 1] - r[kind - 1]) / 2.0d;
    }

    @Override
    public void quadTo(final double x1, final double y1,
                       final double x2, final double y2)
    {
        final int outcode0 = this.cOutCode;
        if (clipRect != null) {
            final int outcode2 = DHelpers.outcode(x2, y2, clipRect);
            this.cOutCode = outcode2;

            if ((outcode0 & outcode2) != 0) {
                final int outcode1 = DHelpers.outcode(x1, y1, clipRect);

                // basic rejection criteria
                if ((outcode0 & outcode1 & outcode2) != 0) {
                    moveTo(x2, y2, outcode0);
                    opened = true;
                    return;
                }
            }
        }

        final double[] mid = middle;

        mid[0] = cx0; mid[1] = cy0;
        mid[2] = x1;  mid[3] = y1;
        mid[4] = x2;  mid[5] = y2;

        // need these so we can update the state at the end of this method
        final double xf = x2, yf = y2;
        double dxs = mid[2] - mid[0];
        double dys = mid[3] - mid[1];
        double dxf = mid[4] - mid[2];
        double dyf = mid[5] - mid[3];
        if ((dxs == 0.0d && dys == 0.0d) || (dxf == 0.0d && dyf == 0.0d)) {
            dxs = dxf = mid[4] - mid[0];
            dys = dyf = mid[5] - mid[1];
        }
        if (dxs == 0.0d && dys == 0.0d) {
            // this happens if the "curve" is just a point
            // fix outcode0 for lineTo() call:
            if (clipRect != null) {
                this.cOutCode = outcode0;
            }
            lineTo(mid[0], mid[1]);
            return;
        }
        // if these vectors are too small, normalize them, to avoid future
        // precision problems.
        if (Math.abs(dxs) < 0.1d && Math.abs(dys) < 0.1d) {
            double len = Math.sqrt(dxs*dxs + dys*dys);
            dxs /= len;
            dys /= len;
        }
        if (Math.abs(dxf) < 0.1d && Math.abs(dyf) < 0.1d) {
            double len = Math.sqrt(dxf*dxf + dyf*dyf);
            dxf /= len;
            dyf /= len;
        }

        computeOffset(dxs, dys, lineWidth2, offset0);
        drawJoin(cdx, cdy, cx0, cy0, dxs, dys, cmx, cmy, offset0[0], offset0[1], outcode0);

        int nSplits = findSubdivPoints(curve, mid, subdivTs, 6, lineWidth2);

        double prevt = 0.0d;
        for (int i = 0, off = 0; i < nSplits; i++, off += 4) {
            final double t = subdivTs[i];
            DHelpers.subdivideQuadAt((t - prevt) / (1.0d - prevt),
                                    mid, off, mid, off, mid, off + 4);
            prevt = t;
        }

        final double[] l = lp;
        final double[] r = rp;

        int kind = 0;
        for (int i = 0, off = 0; i <= nSplits; i++, off += 4) {
            kind = computeOffsetQuad(mid, off, l, r);

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

        this.prev = DRAWING_OP_TO;
        this.cx0 = xf;
        this.cy0 = yf;
        this.cdx = dxf;
        this.cdy = dyf;
        this.cmx = (l[kind - 2] - r[kind - 2]) / 2.0d;
        this.cmy = (l[kind - 1] - r[kind - 1]) / 2.0d;
    }

    @Override public long getNativeConsumer() {
        throw new InternalError("Stroker doesn't use a native consumer");
    }
}
