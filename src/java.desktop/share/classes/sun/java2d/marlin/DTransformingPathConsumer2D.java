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

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import sun.java2d.marlin.DHelpers.IndexStack;
import sun.java2d.marlin.DHelpers.PolyStack;

final class DTransformingPathConsumer2D {

    private final DRendererContext rdrCtx;

    // recycled ClosedPathDetector instance from detectClosedPath()
    private final ClosedPathDetector   cpDetector;

    // recycled PathClipFilter instance from pathClipper()
    private final PathClipFilter       pathClipper;

    // recycled DPathConsumer2D instance from wrapPath2D()
    private final Path2DWrapper        wp_Path2DWrapper        = new Path2DWrapper();

    // recycled DPathConsumer2D instances from deltaTransformConsumer()
    private final DeltaScaleFilter     dt_DeltaScaleFilter     = new DeltaScaleFilter();
    private final DeltaTransformFilter dt_DeltaTransformFilter = new DeltaTransformFilter();

    // recycled DPathConsumer2D instances from inverseDeltaTransformConsumer()
    private final DeltaScaleFilter     iv_DeltaScaleFilter     = new DeltaScaleFilter();
    private final DeltaTransformFilter iv_DeltaTransformFilter = new DeltaTransformFilter();

    // recycled PathTracer instances from tracer...() methods
    private final PathTracer tracerInput      = new PathTracer("[Input]");
    private final PathTracer tracerCPDetector = new PathTracer("ClosedPathDetector");
    private final PathTracer tracerFiller     = new PathTracer("Filler");
    private final PathTracer tracerStroker    = new PathTracer("Stroker");

    DTransformingPathConsumer2D(final DRendererContext rdrCtx) {
        // used by RendererContext
        this.rdrCtx = rdrCtx;
        this.cpDetector = new ClosedPathDetector(rdrCtx);
        this.pathClipper = new PathClipFilter(rdrCtx);
    }

    DPathConsumer2D wrapPath2D(Path2D.Double p2d) {
        return wp_Path2DWrapper.init(p2d);
    }

    DPathConsumer2D traceInput(DPathConsumer2D out) {
        return tracerInput.init(out);
    }

    DPathConsumer2D traceClosedPathDetector(DPathConsumer2D out) {
        return tracerCPDetector.init(out);
    }

    DPathConsumer2D traceFiller(DPathConsumer2D out) {
        return tracerFiller.init(out);
    }

    DPathConsumer2D traceStroker(DPathConsumer2D out) {
        return tracerStroker.init(out);
    }

    DPathConsumer2D detectClosedPath(DPathConsumer2D out) {
        return cpDetector.init(out);
    }

    DPathConsumer2D pathClipper(DPathConsumer2D out) {
        return pathClipper.init(out);
    }

    DPathConsumer2D deltaTransformConsumer(DPathConsumer2D out,
                                          AffineTransform at)
    {
        if (at == null) {
            return out;
        }
        final double mxx = at.getScaleX();
        final double mxy = at.getShearX();
        final double myx = at.getShearY();
        final double myy = at.getScaleY();

        if (mxy == 0.0d && myx == 0.0d) {
            if (mxx == 1.0d && myy == 1.0d) {
                return out;
            } else {
                // Scale only
                if (rdrCtx.doClip) {
                    // adjust clip rectangle (ymin, ymax, xmin, xmax):
                    adjustClipScale(rdrCtx.clipRect, mxx, myy);
                }
                return dt_DeltaScaleFilter.init(out, mxx, myy);
            }
        } else {
            if (rdrCtx.doClip) {
                // adjust clip rectangle (ymin, ymax, xmin, xmax):
                adjustClipInverseDelta(rdrCtx.clipRect, mxx, mxy, myx, myy);
            }
            return dt_DeltaTransformFilter.init(out, mxx, mxy, myx, myy);
        }
    }

    private static void adjustClipOffset(final double[] clipRect) {
        clipRect[0] += Renderer.RDR_OFFSET_Y;
        clipRect[1] += Renderer.RDR_OFFSET_Y;
        clipRect[2] += Renderer.RDR_OFFSET_X;
        clipRect[3] += Renderer.RDR_OFFSET_X;
    }

    private static void adjustClipScale(final double[] clipRect,
                                        final double mxx, final double myy)
    {
        adjustClipOffset(clipRect);

        // Adjust the clipping rectangle (iv_DeltaScaleFilter):
        clipRect[0] /= myy;
        clipRect[1] /= myy;
        clipRect[2] /= mxx;
        clipRect[3] /= mxx;
    }

    private static void adjustClipInverseDelta(final double[] clipRect,
                                               final double mxx, final double mxy,
                                               final double myx, final double myy)
    {
        adjustClipOffset(clipRect);

        // Adjust the clipping rectangle (iv_DeltaTransformFilter):
        final double det = mxx * myy - mxy * myx;
        final double imxx =  myy / det;
        final double imxy = -mxy / det;
        final double imyx = -myx / det;
        final double imyy =  mxx / det;

        double xmin, xmax, ymin, ymax;
        double x, y;
        // xmin, ymin:
        x = clipRect[2] * imxx + clipRect[0] * imxy;
        y = clipRect[2] * imyx + clipRect[0] * imyy;

        xmin = xmax = x;
        ymin = ymax = y;

        // xmax, ymin:
        x = clipRect[3] * imxx + clipRect[0] * imxy;
        y = clipRect[3] * imyx + clipRect[0] * imyy;

        if (x < xmin) { xmin = x; } else if (x > xmax) { xmax = x; }
        if (y < ymin) { ymin = y; } else if (y > ymax) { ymax = y; }

        // xmin, ymax:
        x = clipRect[2] * imxx + clipRect[1] * imxy;
        y = clipRect[2] * imyx + clipRect[1] * imyy;

        if (x < xmin) { xmin = x; } else if (x > xmax) { xmax = x; }
        if (y < ymin) { ymin = y; } else if (y > ymax) { ymax = y; }

        // xmax, ymax:
        x = clipRect[3] * imxx + clipRect[1] * imxy;
        y = clipRect[3] * imyx + clipRect[1] * imyy;

        if (x < xmin) { xmin = x; } else if (x > xmax) { xmax = x; }
        if (y < ymin) { ymin = y; } else if (y > ymax) { ymax = y; }

        clipRect[0] = ymin;
        clipRect[1] = ymax;
        clipRect[2] = xmin;
        clipRect[3] = xmax;
    }

    DPathConsumer2D inverseDeltaTransformConsumer(DPathConsumer2D out,
                                                 AffineTransform at)
    {
        if (at == null) {
            return out;
        }
        double mxx = at.getScaleX();
        double mxy = at.getShearX();
        double myx = at.getShearY();
        double myy = at.getScaleY();

        if (mxy == 0.0d && myx == 0.0d) {
            if (mxx == 1.0d && myy == 1.0d) {
                return out;
            } else {
                return iv_DeltaScaleFilter.init(out, 1.0d/mxx, 1.0d/myy);
            }
        } else {
            final double det = mxx * myy - mxy * myx;
            return iv_DeltaTransformFilter.init(out,
                                                myy / det,
                                               -mxy / det,
                                               -myx / det,
                                                mxx / det);
        }
    }

    static final class DeltaScaleFilter implements DPathConsumer2D {
        private DPathConsumer2D out;
        private double sx, sy;

        DeltaScaleFilter() {}

        DeltaScaleFilter init(DPathConsumer2D out,
                              double mxx, double myy)
        {
            this.out = out;
            sx = mxx;
            sy = myy;
            return this; // fluent API
        }

        @Override
        public void moveTo(double x0, double y0) {
            out.moveTo(x0 * sx, y0 * sy);
        }

        @Override
        public void lineTo(double x1, double y1) {
            out.lineTo(x1 * sx, y1 * sy);
        }

        @Override
        public void quadTo(double x1, double y1,
                           double x2, double y2)
        {
            out.quadTo(x1 * sx, y1 * sy,
                       x2 * sx, y2 * sy);
        }

        @Override
        public void curveTo(double x1, double y1,
                            double x2, double y2,
                            double x3, double y3)
        {
            out.curveTo(x1 * sx, y1 * sy,
                        x2 * sx, y2 * sy,
                        x3 * sx, y3 * sy);
        }

        @Override
        public void closePath() {
            out.closePath();
        }

        @Override
        public void pathDone() {
            out.pathDone();
        }

        @Override
        public long getNativeConsumer() {
            return 0;
        }
    }

    static final class DeltaTransformFilter implements DPathConsumer2D {
        private DPathConsumer2D out;
        private double mxx, mxy, myx, myy;

        DeltaTransformFilter() {}

        DeltaTransformFilter init(DPathConsumer2D out,
                                  double mxx, double mxy,
                                  double myx, double myy)
        {
            this.out = out;
            this.mxx = mxx;
            this.mxy = mxy;
            this.myx = myx;
            this.myy = myy;
            return this; // fluent API
        }

        @Override
        public void moveTo(double x0, double y0) {
            out.moveTo(x0 * mxx + y0 * mxy,
                       x0 * myx + y0 * myy);
        }

        @Override
        public void lineTo(double x1, double y1) {
            out.lineTo(x1 * mxx + y1 * mxy,
                       x1 * myx + y1 * myy);
        }

        @Override
        public void quadTo(double x1, double y1,
                           double x2, double y2)
        {
            out.quadTo(x1 * mxx + y1 * mxy,
                       x1 * myx + y1 * myy,
                       x2 * mxx + y2 * mxy,
                       x2 * myx + y2 * myy);
        }

        @Override
        public void curveTo(double x1, double y1,
                            double x2, double y2,
                            double x3, double y3)
        {
            out.curveTo(x1 * mxx + y1 * mxy,
                        x1 * myx + y1 * myy,
                        x2 * mxx + y2 * mxy,
                        x2 * myx + y2 * myy,
                        x3 * mxx + y3 * mxy,
                        x3 * myx + y3 * myy);
        }

        @Override
        public void closePath() {
            out.closePath();
        }

        @Override
        public void pathDone() {
            out.pathDone();
        }

        @Override
        public long getNativeConsumer() {
            return 0;
        }
    }

    static final class Path2DWrapper implements DPathConsumer2D {
        private Path2D.Double p2d;

        Path2DWrapper() {}

        Path2DWrapper init(Path2D.Double p2d) {
            this.p2d = p2d;
            return this;
        }

        @Override
        public void moveTo(double x0, double y0) {
            p2d.moveTo(x0, y0);
        }

        @Override
        public void lineTo(double x1, double y1) {
            p2d.lineTo(x1, y1);
        }

        @Override
        public void closePath() {
            p2d.closePath();
        }

        @Override
        public void pathDone() {}

        @Override
        public void curveTo(double x1, double y1,
                            double x2, double y2,
                            double x3, double y3)
        {
            p2d.curveTo(x1, y1, x2, y2, x3, y3);
        }

        @Override
        public void quadTo(double x1, double y1, double x2, double y2) {
            p2d.quadTo(x1, y1, x2, y2);
        }

        @Override
        public long getNativeConsumer() {
            throw new InternalError("Not using a native peer");
        }
    }

    static final class ClosedPathDetector implements DPathConsumer2D {

        private final DRendererContext rdrCtx;
        private final PolyStack stack;

        private DPathConsumer2D out;

        ClosedPathDetector(final DRendererContext rdrCtx) {
            this.rdrCtx = rdrCtx;
            this.stack = (rdrCtx.stats != null) ?
                new PolyStack(rdrCtx,
                        rdrCtx.stats.stat_cpd_polystack_types,
                        rdrCtx.stats.stat_cpd_polystack_curves,
                        rdrCtx.stats.hist_cpd_polystack_curves,
                        rdrCtx.stats.stat_array_cpd_polystack_curves,
                        rdrCtx.stats.stat_array_cpd_polystack_types)
                : new PolyStack(rdrCtx);
        }

        ClosedPathDetector init(DPathConsumer2D out) {
            this.out = out;
            return this; // fluent API
        }

        /**
         * Disposes this instance:
         * clean up before reusing this instance
         */
        void dispose() {
            stack.dispose();
        }

        @Override
        public void pathDone() {
            // previous path is not closed:
            finish(false);
            out.pathDone();

            // TODO: fix possible leak if exception happened
            // Dispose this instance:
            dispose();
        }

        @Override
        public void closePath() {
            // path is closed
            finish(true);
            out.closePath();
        }

        @Override
        public void moveTo(double x0, double y0) {
            // previous path is not closed:
            finish(false);
            out.moveTo(x0, y0);
        }

        private void finish(final boolean closed) {
            rdrCtx.closedPath = closed;
            stack.pullAll(out);
        }

        @Override
        public void lineTo(double x1, double y1) {
            stack.pushLine(x1, y1);
        }

        @Override
        public void curveTo(double x3, double y3,
                            double x2, double y2,
                            double x1, double y1)
        {
            stack.pushCubic(x1, y1, x2, y2, x3, y3);
        }

        @Override
        public void quadTo(double x2, double y2, double x1, double y1) {
            stack.pushQuad(x1, y1, x2, y2);
        }

        @Override
        public long getNativeConsumer() {
            throw new InternalError("Not using a native peer");
        }
    }

    static final class PathClipFilter implements DPathConsumer2D {

        private DPathConsumer2D out;

        // Bounds of the drawing region, at pixel precision.
        private final double[] clipRect;

        private final double[] corners = new double[8];
        private boolean init_corners = false;

        private final IndexStack stack;

        // the current outcode of the current sub path
        private int cOutCode = 0;

        // the cumulated (and) outcode of the complete path
        private int gOutCode = MarlinConst.OUTCODE_MASK_T_B_L_R;

        private boolean outside = false;

        // The current point OUTSIDE
        private double cx0, cy0;

        PathClipFilter(final DRendererContext rdrCtx) {
            this.clipRect = rdrCtx.clipRect;
            this.stack = (rdrCtx.stats != null) ?
                new IndexStack(rdrCtx,
                        rdrCtx.stats.stat_pcf_idxstack_indices,
                        rdrCtx.stats.hist_pcf_idxstack_indices,
                        rdrCtx.stats.stat_array_pcf_idxstack_indices)
                : new IndexStack(rdrCtx);
        }

        PathClipFilter init(final DPathConsumer2D out) {
            this.out = out;

            // Adjust the clipping rectangle with the renderer offsets
            final double rdrOffX = DRenderer.RDR_OFFSET_X;
            final double rdrOffY = DRenderer.RDR_OFFSET_Y;

            // add a small rounding error:
            final double margin = 1e-3d;

            final double[] _clipRect = this.clipRect;
            _clipRect[0] -= margin - rdrOffY;
            _clipRect[1] += margin + rdrOffY;
            _clipRect[2] -= margin - rdrOffX;
            _clipRect[3] += margin + rdrOffX;

            this.init_corners = true;
            this.gOutCode = MarlinConst.OUTCODE_MASK_T_B_L_R;

            return this; // fluent API
        }

        /**
         * Disposes this instance:
         * clean up before reusing this instance
         */
        void dispose() {
            stack.dispose();
        }

        private void finishPath() {
            if (outside) {
                // criteria: inside or totally outside ?
                if (gOutCode == 0) {
                    finish();
                } else {
                    this.outside = false;
                    stack.reset();
                }
            }
        }

        private void finish() {
            this.outside = false;

            if (!stack.isEmpty()) {
                if (init_corners) {
                    init_corners = false;

                    final double[] _corners = corners;
                    final double[] _clipRect = clipRect;
                    // Top Left (0):
                    _corners[0] = _clipRect[2];
                    _corners[1] = _clipRect[0];
                    // Bottom Left (1):
                    _corners[2] = _clipRect[2];
                    _corners[3] = _clipRect[1];
                    // Top right (2):
                    _corners[4] = _clipRect[3];
                    _corners[5] = _clipRect[0];
                    // Bottom Right (3):
                    _corners[6] = _clipRect[3];
                    _corners[7] = _clipRect[1];
                }
                stack.pullAll(corners, out);
            }
            out.lineTo(cx0, cy0);
        }

        @Override
        public void pathDone() {
            finishPath();

            out.pathDone();

            // TODO: fix possible leak if exception happened
            // Dispose this instance:
            dispose();
        }

        @Override
        public void closePath() {
            finishPath();

            out.closePath();
        }

        @Override
        public void moveTo(final double x0, final double y0) {
            finishPath();

            final int outcode = DHelpers.outcode(x0, y0, clipRect);
            this.cOutCode = outcode;
            this.outside = false;
            out.moveTo(x0, y0);
        }

        @Override
        public void lineTo(final double xe, final double ye) {
            final int outcode0 = this.cOutCode;
            final int outcode1 = DHelpers.outcode(xe, ye, clipRect);
            this.cOutCode = outcode1;

            final int sideCode = (outcode0 & outcode1);

            // basic rejection criteria:
            if (sideCode == 0) {
                this.gOutCode = 0;
            } else {
                this.gOutCode &= sideCode;
                // keep last point coordinate before entering the clip again:
                this.outside = true;
                this.cx0 = xe;
                this.cy0 = ye;

                clip(sideCode, outcode0, outcode1);
                return;
            }
            if (outside) {
                finish();
            }
            // clipping disabled:
            out.lineTo(xe, ye);
        }

        private void clip(final int sideCode,
                          final int outcode0,
                          final int outcode1)
        {
            // corner or cross-boundary on left or right side:
            if ((outcode0 != outcode1)
                    && ((sideCode & MarlinConst.OUTCODE_MASK_L_R) != 0))
            {
                // combine outcodes:
                final int mergeCode = (outcode0 | outcode1);
                final int tbCode = mergeCode & MarlinConst.OUTCODE_MASK_T_B;
                final int lrCode = mergeCode & MarlinConst.OUTCODE_MASK_L_R;
                final int off = (lrCode == MarlinConst.OUTCODE_LEFT) ? 0 : 2;

                // add corners to outside stack:
                switch (tbCode) {
                    case MarlinConst.OUTCODE_TOP:
// System.out.println("TOP "+ ((off == 0) ? "LEFT" : "RIGHT"));
                        stack.push(off); // top
                        return;
                    case MarlinConst.OUTCODE_BOTTOM:
// System.out.println("BOTTOM "+ ((off == 0) ? "LEFT" : "RIGHT"));
                        stack.push(off + 1); // bottom
                        return;
                    default:
                        // both TOP / BOTTOM:
                        if ((outcode0 & MarlinConst.OUTCODE_TOP) != 0) {
// System.out.println("TOP + BOTTOM "+ ((off == 0) ? "LEFT" : "RIGHT"));
                            // top to bottom
                            stack.push(off); // top
                            stack.push(off + 1); // bottom
                        } else {
// System.out.println("BOTTOM + TOP "+ ((off == 0) ? "LEFT" : "RIGHT"));
                            // bottom to top
                            stack.push(off + 1); // bottom
                            stack.push(off); // top
                        }
                }
            }
        }

        @Override
        public void curveTo(final double x1, final double y1,
                            final double x2, final double y2,
                            final double xe, final double ye)
        {
            final int outcode0 = this.cOutCode;
            final int outcode3 = DHelpers.outcode(xe, ye, clipRect);
            this.cOutCode = outcode3;

            int sideCode = outcode0 & outcode3;

            if (sideCode == 0) {
                this.gOutCode = 0;
            } else {
                sideCode &= DHelpers.outcode(x1, y1, clipRect);
                sideCode &= DHelpers.outcode(x2, y2, clipRect);
                this.gOutCode &= sideCode;

                // basic rejection criteria:
                if (sideCode != 0) {
                    // keep last point coordinate before entering the clip again:
                    this.outside = true;
                    this.cx0 = xe;
                    this.cy0 = ye;

                    clip(sideCode, outcode0, outcode3);
                    return;
                }
            }
            if (outside) {
                finish();
            }
            // clipping disabled:
            out.curveTo(x1, y1, x2, y2, xe, ye);
        }

        @Override
        public void quadTo(final double x1, final double y1,
                           final double xe, final double ye)
        {
            final int outcode0 = this.cOutCode;
            final int outcode2 = DHelpers.outcode(xe, ye, clipRect);
            this.cOutCode = outcode2;

            int sideCode = outcode0 & outcode2;

            if (sideCode == 0) {
                this.gOutCode = 0;
            } else {
                sideCode &= DHelpers.outcode(x1, y1, clipRect);
                this.gOutCode &= sideCode;

                // basic rejection criteria:
                if (sideCode != 0) {
                    // keep last point coordinate before entering the clip again:
                    this.outside = true;
                    this.cx0 = xe;
                    this.cy0 = ye;

                    clip(sideCode, outcode0, outcode2);
                    return;
                }
            }
            if (outside) {
                finish();
            }
            // clipping disabled:
            out.quadTo(x1, y1, xe, ye);
        }

        @Override
        public long getNativeConsumer() {
            throw new InternalError("Not using a native peer");
        }
    }

    static final class PathTracer implements DPathConsumer2D {
        private final String prefix;
        private DPathConsumer2D out;

        PathTracer(String name) {
            this.prefix = name + ": ";
        }

        PathTracer init(DPathConsumer2D out) {
            this.out = out;
            return this; // fluent API
        }

        @Override
        public void moveTo(double x0, double y0) {
            log("moveTo (" + x0 + ", " + y0 + ')');
            out.moveTo(x0, y0);
        }

        @Override
        public void lineTo(double x1, double y1) {
            log("lineTo (" + x1 + ", " + y1 + ')');
            out.lineTo(x1, y1);
        }

        @Override
        public void curveTo(double x1, double y1,
                            double x2, double y2,
                            double x3, double y3)
        {
            log("curveTo P1(" + x1 + ", " + y1 + ") P2(" + x2 + ", " + y2  + ") P3(" + x3 + ", " + y3 + ')');
            out.curveTo(x1, y1, x2, y2, x3, y3);
        }

        @Override
        public void quadTo(double x1, double y1, double x2, double y2) {
            log("quadTo P1(" + x1 + ", " + y1 + ") P2(" + x2 + ", " + y2  + ')');
            out.quadTo(x1, y1, x2, y2);
        }

        @Override
        public void closePath() {
            log("closePath");
            out.closePath();
        }

        @Override
        public void pathDone() {
            log("pathDone");
            out.pathDone();
        }

        private void log(final String message) {
            System.out.println(prefix + message);
        }

        @Override
        public long getNativeConsumer() {
            throw new InternalError("Not using a native peer");
        }
    }
}
