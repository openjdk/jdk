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

import sun.awt.geom.PathConsumer2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.Arrays;
import sun.java2d.marlin.Helpers.IndexStack;
import sun.java2d.marlin.Helpers.PolyStack;

final class TransformingPathConsumer2D {

    // higher uncertainty in float variant for huge shapes > 10^7
    static final float CLIP_RECT_PADDING = 1.0f;

    private final RendererContext rdrCtx;

    // recycled ClosedPathDetector instance from detectClosedPath()
    private final ClosedPathDetector   cpDetector;

    // recycled PathClipFilter instance from pathClipper()
    private final PathClipFilter       pathClipper;

    // recycled PathConsumer2D instance from wrapPath2D()
    private final Path2DWrapper        wp_Path2DWrapper        = new Path2DWrapper();

    // recycled PathConsumer2D instances from deltaTransformConsumer()
    private final DeltaScaleFilter     dt_DeltaScaleFilter     = new DeltaScaleFilter();
    private final DeltaTransformFilter dt_DeltaTransformFilter = new DeltaTransformFilter();

    // recycled PathConsumer2D instances from inverseDeltaTransformConsumer()
    private final DeltaScaleFilter     iv_DeltaScaleFilter     = new DeltaScaleFilter();
    private final DeltaTransformFilter iv_DeltaTransformFilter = new DeltaTransformFilter();

    // recycled PathTracer instances from tracer...() methods
    private final PathTracer tracerInput      = new PathTracer("[Input]");
    private final PathTracer tracerCPDetector = new PathTracer("ClosedPathDetector");
    private final PathTracer tracerFiller     = new PathTracer("Filler");
    private final PathTracer tracerStroker    = new PathTracer("Stroker");
    private final PathTracer tracerDasher     = new PathTracer("Dasher");

    TransformingPathConsumer2D(final RendererContext rdrCtx) {
        // used by RendererContext
        this.rdrCtx = rdrCtx;
        this.cpDetector = new ClosedPathDetector(rdrCtx);
        this.pathClipper = new PathClipFilter(rdrCtx);
    }

    PathConsumer2D wrapPath2D(Path2D.Float p2d) {
        return wp_Path2DWrapper.init(p2d);
    }

    PathConsumer2D traceInput(PathConsumer2D out) {
        return tracerInput.init(out);
    }

    PathConsumer2D traceClosedPathDetector(PathConsumer2D out) {
        return tracerCPDetector.init(out);
    }

    PathConsumer2D traceFiller(PathConsumer2D out) {
        return tracerFiller.init(out);
    }

    PathConsumer2D traceStroker(PathConsumer2D out) {
        return tracerStroker.init(out);
    }

    PathConsumer2D traceDasher(PathConsumer2D out) {
        return tracerDasher.init(out);
    }

    PathConsumer2D detectClosedPath(PathConsumer2D out) {
        return cpDetector.init(out);
    }

    PathConsumer2D pathClipper(PathConsumer2D out) {
        return pathClipper.init(out);
    }

    PathConsumer2D deltaTransformConsumer(PathConsumer2D out,
                                          AffineTransform at)
    {
        if (at == null) {
            return out;
        }
        final float mxx = (float) at.getScaleX();
        final float mxy = (float) at.getShearX();
        final float myx = (float) at.getShearY();
        final float myy = (float) at.getScaleY();

        if (mxy == 0.0f && myx == 0.0f) {
            if (mxx == 1.0f && myy == 1.0f) {
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

    private static void adjustClipOffset(final float[] clipRect) {
        clipRect[0] += Renderer.RDR_OFFSET_Y;
        clipRect[1] += Renderer.RDR_OFFSET_Y;
        clipRect[2] += Renderer.RDR_OFFSET_X;
        clipRect[3] += Renderer.RDR_OFFSET_X;
    }

    private static void adjustClipScale(final float[] clipRect,
                                        final float mxx, final float myy)
    {
        adjustClipOffset(clipRect);

        // Adjust the clipping rectangle (iv_DeltaScaleFilter):
        clipRect[0] /= myy;
        clipRect[1] /= myy;
        clipRect[2] /= mxx;
        clipRect[3] /= mxx;
    }

    private static void adjustClipInverseDelta(final float[] clipRect,
                                               final float mxx, final float mxy,
                                               final float myx, final float myy)
    {
        adjustClipOffset(clipRect);

        // Adjust the clipping rectangle (iv_DeltaTransformFilter):
        final float det = mxx * myy - mxy * myx;
        final float imxx =  myy / det;
        final float imxy = -mxy / det;
        final float imyx = -myx / det;
        final float imyy =  mxx / det;

        float xmin, xmax, ymin, ymax;
        float x, y;
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

    PathConsumer2D inverseDeltaTransformConsumer(PathConsumer2D out,
                                                 AffineTransform at)
    {
        if (at == null) {
            return out;
        }
        float mxx = (float) at.getScaleX();
        float mxy = (float) at.getShearX();
        float myx = (float) at.getShearY();
        float myy = (float) at.getScaleY();

        if (mxy == 0.0f && myx == 0.0f) {
            if (mxx == 1.0f && myy == 1.0f) {
                return out;
            } else {
                return iv_DeltaScaleFilter.init(out, 1.0f/mxx, 1.0f/myy);
            }
        } else {
            final float det = mxx * myy - mxy * myx;
            return iv_DeltaTransformFilter.init(out,
                                                myy / det,
                                               -mxy / det,
                                               -myx / det,
                                                mxx / det);
        }
    }

    static final class DeltaScaleFilter implements PathConsumer2D {
        private PathConsumer2D out;
        private float sx, sy;

        DeltaScaleFilter() {}

        DeltaScaleFilter init(PathConsumer2D out,
                              float mxx, float myy)
        {
            this.out = out;
            sx = mxx;
            sy = myy;
            return this; // fluent API
        }

        @Override
        public void moveTo(float x0, float y0) {
            out.moveTo(x0 * sx, y0 * sy);
        }

        @Override
        public void lineTo(float x1, float y1) {
            out.lineTo(x1 * sx, y1 * sy);
        }

        @Override
        public void quadTo(float x1, float y1,
                           float x2, float y2)
        {
            out.quadTo(x1 * sx, y1 * sy,
                       x2 * sx, y2 * sy);
        }

        @Override
        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
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

    static final class DeltaTransformFilter implements PathConsumer2D {
        private PathConsumer2D out;
        private float mxx, mxy, myx, myy;

        DeltaTransformFilter() {}

        DeltaTransformFilter init(PathConsumer2D out,
                                  float mxx, float mxy,
                                  float myx, float myy)
        {
            this.out = out;
            this.mxx = mxx;
            this.mxy = mxy;
            this.myx = myx;
            this.myy = myy;
            return this; // fluent API
        }

        @Override
        public void moveTo(float x0, float y0) {
            out.moveTo(x0 * mxx + y0 * mxy,
                       x0 * myx + y0 * myy);
        }

        @Override
        public void lineTo(float x1, float y1) {
            out.lineTo(x1 * mxx + y1 * mxy,
                       x1 * myx + y1 * myy);
        }

        @Override
        public void quadTo(float x1, float y1,
                           float x2, float y2)
        {
            out.quadTo(x1 * mxx + y1 * mxy,
                       x1 * myx + y1 * myy,
                       x2 * mxx + y2 * mxy,
                       x2 * myx + y2 * myy);
        }

        @Override
        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
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

    static final class Path2DWrapper implements PathConsumer2D {
        private Path2D.Float p2d;

        Path2DWrapper() {}

        Path2DWrapper init(Path2D.Float p2d) {
            this.p2d = p2d;
            return this;
        }

        @Override
        public void moveTo(float x0, float y0) {
            p2d.moveTo(x0, y0);
        }

        @Override
        public void lineTo(float x1, float y1) {
            p2d.lineTo(x1, y1);
        }

        @Override
        public void closePath() {
            p2d.closePath();
        }

        @Override
        public void pathDone() {}

        @Override
        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
        {
            p2d.curveTo(x1, y1, x2, y2, x3, y3);
        }

        @Override
        public void quadTo(float x1, float y1, float x2, float y2) {
            p2d.quadTo(x1, y1, x2, y2);
        }

        @Override
        public long getNativeConsumer() {
            throw new InternalError("Not using a native peer");
        }
    }

    static final class ClosedPathDetector implements PathConsumer2D {

        private final RendererContext rdrCtx;
        private final PolyStack stack;

        private PathConsumer2D out;

        ClosedPathDetector(final RendererContext rdrCtx) {
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

        ClosedPathDetector init(PathConsumer2D out) {
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
        public void moveTo(float x0, float y0) {
            // previous path is not closed:
            finish(false);
            out.moveTo(x0, y0);
        }

        private void finish(final boolean closed) {
            rdrCtx.closedPath = closed;
            stack.pullAll(out);
        }

        @Override
        public void lineTo(float x1, float y1) {
            stack.pushLine(x1, y1);
        }

        @Override
        public void curveTo(float x3, float y3,
                            float x2, float y2,
                            float x1, float y1)
        {
            stack.pushCubic(x1, y1, x2, y2, x3, y3);
        }

        @Override
        public void quadTo(float x2, float y2, float x1, float y1) {
            stack.pushQuad(x1, y1, x2, y2);
        }

        @Override
        public long getNativeConsumer() {
            throw new InternalError("Not using a native peer");
        }
    }

    static final class PathClipFilter implements PathConsumer2D {

        private PathConsumer2D out;

        // Bounds of the drawing region, at pixel precision.
        private final float[] clipRect;

        private final float[] corners = new float[8];
        private boolean init_corners = false;

        private final IndexStack stack;

        // the current outcode of the current sub path
        private int cOutCode = 0;

        // the cumulated (and) outcode of the complete path
        private int gOutCode = MarlinConst.OUTCODE_MASK_T_B_L_R;

        private boolean outside = false;

        // The current point (TODO stupid repeated info)
        private float cx0, cy0;

        // The current point OUTSIDE
        private float cox0, coy0;

        private boolean subdivide = MarlinConst.DO_CLIP_SUBDIVIDER;
        private final CurveClipSplitter curveSplitter;

        PathClipFilter(final RendererContext rdrCtx) {
            this.clipRect = rdrCtx.clipRect;
            this.curveSplitter = rdrCtx.curveClipSplitter;

            this.stack = (rdrCtx.stats != null) ?
                new IndexStack(rdrCtx,
                        rdrCtx.stats.stat_pcf_idxstack_indices,
                        rdrCtx.stats.hist_pcf_idxstack_indices,
                        rdrCtx.stats.stat_array_pcf_idxstack_indices)
                : new IndexStack(rdrCtx);
        }

        PathClipFilter init(final PathConsumer2D out) {
            this.out = out;

            // Adjust the clipping rectangle with the renderer offsets
            final float rdrOffX = Renderer.RDR_OFFSET_X;
            final float rdrOffY = Renderer.RDR_OFFSET_Y;

            // add a small rounding error:
            final float margin = 1e-3f;

            final float[] _clipRect = this.clipRect;
            _clipRect[0] -= margin - rdrOffY;
            _clipRect[1] += margin + rdrOffY;
            _clipRect[2] -= margin - rdrOffX;
            _clipRect[3] += margin + rdrOffX;

            if (MarlinConst.DO_CLIP_SUBDIVIDER) {
                // adjust padded clip rectangle:
                curveSplitter.init();
            }

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

                    final float[] _corners = corners;
                    final float[] _clipRect = clipRect;
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
            out.lineTo(cox0, coy0);
            this.cx0 = cox0;
            this.cy0 = coy0;
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
        public void moveTo(final float x0, final float y0) {
            finishPath();

            this.cOutCode = Helpers.outcode(x0, y0, clipRect);
            this.outside = false;
            out.moveTo(x0, y0);
            this.cx0 = x0;
            this.cy0 = y0;
        }

        @Override
        public void lineTo(final float xe, final float ye) {
            final int outcode0 = this.cOutCode;
            final int outcode1 = Helpers.outcode(xe, ye, clipRect);

            // Should clip
            final int orCode = (outcode0 | outcode1);
            if (orCode != 0) {
                final int sideCode = (outcode0 & outcode1);

                // basic rejection criteria:
                if (sideCode == 0) {
                    // ovelap clip:
                    if (subdivide) {
                        // avoid reentrance
                        subdivide = false;
                        boolean ret;
                        // subdivide curve => callback with subdivided parts:
                        if (outside) {
                            ret = curveSplitter.splitLine(cox0, coy0, xe, ye,
                                                          orCode, this);
                        } else {
                            ret = curveSplitter.splitLine(cx0, cy0, xe, ye,
                                                          orCode, this);
                        }
                        // reentrance is done:
                        subdivide = true;
                        if (ret) {
                            return;
                        }
                    }
                    // already subdivided so render it
                } else {
                    this.cOutCode = outcode1;
                    this.gOutCode &= sideCode;
                    // keep last point coordinate before entering the clip again:
                    this.outside = true;
                    this.cox0 = xe;
                    this.coy0 = ye;

                    clip(sideCode, outcode0, outcode1);
                    return;
                }
            }

            this.cOutCode = outcode1;
            this.gOutCode = 0;

            if (outside) {
                finish();
            }
            // clipping disabled:
            out.lineTo(xe, ye);
            this.cx0 = xe;
            this.cy0 = ye;
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
                        stack.push(off); // top
                        return;
                    case MarlinConst.OUTCODE_BOTTOM:
                        stack.push(off + 1); // bottom
                        return;
                    default:
                        // both TOP / BOTTOM:
                        if ((outcode0 & MarlinConst.OUTCODE_TOP) != 0) {
                            // top to bottom
                            stack.push(off); // top
                            stack.push(off + 1); // bottom
                        } else {
                            // bottom to top
                            stack.push(off + 1); // bottom
                            stack.push(off); // top
                        }
                }
            }
        }

        @Override
        public void curveTo(final float x1, final float y1,
                            final float x2, final float y2,
                            final float xe, final float ye)
        {
            final int outcode0 = this.cOutCode;
            final int outcode1 = Helpers.outcode(x1, y1, clipRect);
            final int outcode2 = Helpers.outcode(x2, y2, clipRect);
            final int outcode3 = Helpers.outcode(xe, ye, clipRect);

            // Should clip
            final int orCode = (outcode0 | outcode1 | outcode2 | outcode3);
            if (orCode != 0) {
                final int sideCode = outcode0 & outcode1 & outcode2 & outcode3;

                // basic rejection criteria:
                if (sideCode == 0) {
                    // ovelap clip:
                    if (subdivide) {
                        // avoid reentrance
                        subdivide = false;
                        // subdivide curve => callback with subdivided parts:
                        boolean ret;
                        if (outside) {
                            ret = curveSplitter.splitCurve(cox0, coy0, x1, y1,
                                                           x2, y2, xe, ye,
                                                           orCode, this);
                        } else {
                            ret = curveSplitter.splitCurve(cx0, cy0, x1, y1,
                                                           x2, y2, xe, ye,
                                                           orCode, this);
                        }
                        // reentrance is done:
                        subdivide = true;
                        if (ret) {
                            return;
                        }
                    }
                    // already subdivided so render it
                } else {
                    this.cOutCode = outcode3;
                    this.gOutCode &= sideCode;
                    // keep last point coordinate before entering the clip again:
                    this.outside = true;
                    this.cox0 = xe;
                    this.coy0 = ye;

                    clip(sideCode, outcode0, outcode3);
                    return;
                }
            }

            this.cOutCode = outcode3;
            this.gOutCode = 0;

            if (outside) {
                finish();
            }
            // clipping disabled:
            out.curveTo(x1, y1, x2, y2, xe, ye);
            this.cx0 = xe;
            this.cy0 = ye;
        }

        @Override
        public void quadTo(final float x1, final float y1,
                           final float xe, final float ye)
        {
            final int outcode0 = this.cOutCode;
            final int outcode1 = Helpers.outcode(x1, y1, clipRect);
            final int outcode2 = Helpers.outcode(xe, ye, clipRect);

            // Should clip
            final int orCode = (outcode0 | outcode1 | outcode2);
            if (orCode != 0) {
                final int sideCode = outcode0 & outcode1 & outcode2;

                // basic rejection criteria:
                if (sideCode == 0) {
                    // ovelap clip:
                    if (subdivide) {
                        // avoid reentrance
                        subdivide = false;
                        // subdivide curve => callback with subdivided parts:
                        boolean ret;
                        if (outside) {
                            ret = curveSplitter.splitQuad(cox0, coy0, x1, y1,
                                                          xe, ye, orCode, this);
                        } else {
                            ret = curveSplitter.splitQuad(cx0, cy0, x1, y1,
                                                          xe, ye, orCode, this);
                        }
                        // reentrance is done:
                        subdivide = true;
                        if (ret) {
                            return;
                        }
                    }
                    // already subdivided so render it
                } else {
                    this.cOutCode = outcode2;
                    this.gOutCode &= sideCode;
                    // keep last point coordinate before entering the clip again:
                    this.outside = true;
                    this.cox0 = xe;
                    this.coy0 = ye;

                    clip(sideCode, outcode0, outcode2);
                    return;
                }
            }

            this.cOutCode = outcode2;
            this.gOutCode = 0;

            if (outside) {
                finish();
            }
            // clipping disabled:
            out.quadTo(x1, y1, xe, ye);
            this.cx0 = xe;
            this.cy0 = ye;
        }

        @Override
        public long getNativeConsumer() {
            throw new InternalError("Not using a native peer");
        }
    }

    static final class CurveClipSplitter {

        static final float LEN_TH = MarlinProperties.getSubdividerMinLength();
        static final boolean DO_CHECK_LENGTH = (LEN_TH > 0.0f);

        private static final boolean TRACE = false;

        private static final int MAX_N_CURVES = 3 * 4;

        // clip rectangle (ymin, ymax, xmin, xmax):
        final float[] clipRect;

        // clip rectangle (ymin, ymax, xmin, xmax) including padding:
        final float[] clipRectPad = new float[4];
        private boolean init_clipRectPad = false;

        // This is where the curve to be processed is put. We give it
        // enough room to store all curves.
        final float[] middle = new float[MAX_N_CURVES * 8 + 2];
        // t values at subdivision points
        private final float[] subdivTs = new float[MAX_N_CURVES];

        // dirty curve
        private final Curve curve;

        CurveClipSplitter(final RendererContext rdrCtx) {
            this.clipRect = rdrCtx.clipRect;
            this.curve = rdrCtx.curve;
        }

        void init() {
            this.init_clipRectPad = true;
        }

        private void initPaddedClip() {
            // bounds as half-open intervals: minX <= x < maxX and minY <= y < maxY
            // adjust padded clip rectangle (ymin, ymax, xmin, xmax):
            // add a rounding error (curve subdivision ~ 0.1px):
            final float[] _clipRect = clipRect;
            final float[] _clipRectPad = clipRectPad;

            _clipRectPad[0] = _clipRect[0] - CLIP_RECT_PADDING;
            _clipRectPad[1] = _clipRect[1] + CLIP_RECT_PADDING;
            _clipRectPad[2] = _clipRect[2] - CLIP_RECT_PADDING;
            _clipRectPad[3] = _clipRect[3] + CLIP_RECT_PADDING;

            if (TRACE) {
                MarlinUtils.logInfo("clip: X [" + _clipRectPad[2] + " .. " + _clipRectPad[3] +"] "
                                        + "Y ["+ _clipRectPad[0] + " .. " + _clipRectPad[1] +"]");
            }
        }

        boolean splitLine(final float x0, final float y0,
                          final float x1, final float y1,
                          final int outCodeOR,
                          final PathConsumer2D out)
        {
            if (TRACE) {
                MarlinUtils.logInfo("divLine P0(" + x0 + ", " + y0 + ") P1(" + x1 + ", " + y1 + ")");
            }

            if (DO_CHECK_LENGTH && Helpers.fastLineLen(x0, y0, x1, y1) <= LEN_TH) {
                return false;
            }

            final float[] mid = middle;
            mid[0] = x0;  mid[1] = y0;
            mid[2] = x1;  mid[3] = y1;

            return subdivideAtIntersections(4, outCodeOR, out);
        }

        boolean splitQuad(final float x0, final float y0,
                          final float x1, final float y1,
                          final float x2, final float y2,
                          final int outCodeOR,
                          final PathConsumer2D out)
        {
            if (TRACE) {
                MarlinUtils.logInfo("divQuad P0(" + x0 + ", " + y0 + ") P1(" + x1 + ", " + y1 + ") P2(" + x2 + ", " + y2 + ")");
            }

            if (DO_CHECK_LENGTH && Helpers.fastQuadLen(x0, y0, x1, y1, x2, y2) <= LEN_TH) {
                return false;
            }

            final float[] mid = middle;
            mid[0] = x0;  mid[1] = y0;
            mid[2] = x1;  mid[3] = y1;
            mid[4] = x2;  mid[5] = y2;

            return subdivideAtIntersections(6, outCodeOR, out);
        }

        boolean splitCurve(final float x0, final float y0,
                           final float x1, final float y1,
                           final float x2, final float y2,
                           final float x3, final float y3,
                           final int outCodeOR,
                           final PathConsumer2D out)
        {
            if (TRACE) {
                MarlinUtils.logInfo("divCurve P0(" + x0 + ", " + y0 + ") P1(" + x1 + ", " + y1 + ") P2(" + x2 + ", " + y2 + ") P3(" + x3 + ", " + y3 + ")");
            }

            if (DO_CHECK_LENGTH && Helpers.fastCurvelen(x0, y0, x1, y1, x2, y2, x3, y3) <= LEN_TH) {
                return false;
            }

            final float[] mid = middle;
            mid[0] = x0;  mid[1] = y0;
            mid[2] = x1;  mid[3] = y1;
            mid[4] = x2;  mid[5] = y2;
            mid[6] = x3;  mid[7] = y3;

            return subdivideAtIntersections(8, outCodeOR, out);
        }

        private boolean subdivideAtIntersections(final int type, final int outCodeOR,
                                                 final PathConsumer2D out)
        {
            final float[] mid = middle;
            final float[] subTs = subdivTs;

            if (init_clipRectPad) {
                init_clipRectPad = false;
                initPaddedClip();
            }

            final int nSplits = Helpers.findClipPoints(curve, mid, subTs, type,
                                                        outCodeOR, clipRectPad);

            if (TRACE) {
                MarlinUtils.logInfo("nSplits: "+ nSplits);
                MarlinUtils.logInfo("subTs: "+Arrays.toString(Arrays.copyOfRange(subTs, 0, nSplits)));
            }
            if (nSplits == 0) {
                // only curve support shortcut
                return false;
            }
            float prevT = 0.0f;

            for (int i = 0, off = 0; i < nSplits; i++, off += type) {
                final float t = subTs[i];

                Helpers.subdivideAt((t - prevT) / (1.0f - prevT),
                                     mid, off, mid, off, type);
                prevT = t;
            }

            for (int i = 0, off = 0; i <= nSplits; i++, off += type) {
                if (TRACE) {
                    MarlinUtils.logInfo("Part Curve "+Arrays.toString(Arrays.copyOfRange(mid, off, off + type)));
                }
                emitCurrent(type, mid, off, out);
            }
            return true;
        }

        static void emitCurrent(final int type, final float[] pts,
                                final int off, final PathConsumer2D out)
        {
            // if instead of switch (perf + most probable cases first)
            if (type == 8) {
                out.curveTo(pts[off + 2], pts[off + 3],
                            pts[off + 4], pts[off + 5],
                            pts[off + 6], pts[off + 7]);
            } else if (type == 4) {
                out.lineTo(pts[off + 2], pts[off + 3]);
            } else {
                out.quadTo(pts[off + 2], pts[off + 3],
                           pts[off + 4], pts[off + 5]);
            }
        }
    }

    static final class CurveBasicMonotonizer {

        private static final int MAX_N_CURVES = 11;

        // squared half line width (for stroker)
        private float lw2;

        // number of splitted curves
        int nbSplits;

        // This is where the curve to be processed is put. We give it
        // enough room to store all curves.
        final float[] middle = new float[MAX_N_CURVES * 6 + 2];
        // t values at subdivision points
        private final float[] subdivTs = new float[MAX_N_CURVES - 1];

        // dirty curve
        private final Curve curve;

        CurveBasicMonotonizer(final RendererContext rdrCtx) {
            this.curve = rdrCtx.curve;
        }

        void init(final float lineWidth) {
            this.lw2 = (lineWidth * lineWidth) / 4.0f;
        }

        CurveBasicMonotonizer curve(final float x0, final float y0,
                                    final float x1, final float y1,
                                    final float x2, final float y2,
                                    final float x3, final float y3)
        {
            final float[] mid = middle;
            mid[0] = x0;  mid[1] = y0;
            mid[2] = x1;  mid[3] = y1;
            mid[4] = x2;  mid[5] = y2;
            mid[6] = x3;  mid[7] = y3;

            final float[] subTs = subdivTs;
            final int nSplits = Helpers.findSubdivPoints(curve, mid, subTs, 8, lw2);

            float prevT = 0.0f;
            for (int i = 0, off = 0; i < nSplits; i++, off += 6) {
                final float t = subTs[i];

                Helpers.subdivideCubicAt((t - prevT) / (1.0f - prevT),
                                          mid, off, mid, off, off + 6);
                prevT = t;
            }

            this.nbSplits = nSplits;
            return this;
        }

        CurveBasicMonotonizer quad(final float x0, final float y0,
                                   final float x1, final float y1,
                                   final float x2, final float y2)
        {
            final float[] mid = middle;
            mid[0] = x0;  mid[1] = y0;
            mid[2] = x1;  mid[3] = y1;
            mid[4] = x2;  mid[5] = y2;

            final float[] subTs = subdivTs;
            final int nSplits = Helpers.findSubdivPoints(curve, mid, subTs, 6, lw2);

            float prevt = 0.0f;
            for (int i = 0, off = 0; i < nSplits; i++, off += 4) {
                final float t = subTs[i];
                Helpers.subdivideQuadAt((t - prevt) / (1.0f - prevt),
                                         mid, off, mid, off, off + 4);
                prevt = t;
            }

            this.nbSplits = nSplits;
            return this;
        }
    }

    static final class PathTracer implements PathConsumer2D {
        private final String prefix;
        private PathConsumer2D out;

        PathTracer(String name) {
            this.prefix = name + ": ";
        }

        PathTracer init(PathConsumer2D out) {
            this.out = out;
            return this; // fluent API
        }

        @Override
        public void moveTo(float x0, float y0) {
            log("moveTo (" + x0 + ", " + y0 + ')');
            out.moveTo(x0, y0);
        }

        @Override
        public void lineTo(float x1, float y1) {
            log("lineTo (" + x1 + ", " + y1 + ')');
            out.lineTo(x1, y1);
        }

        @Override
        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
        {
            log("curveTo P1(" + x1 + ", " + y1 + ") P2(" + x2 + ", " + y2  + ") P3(" + x3 + ", " + y3 + ')');
            out.curveTo(x1, y1, x2, y2, x3, y3);
        }

        @Override
        public void quadTo(float x1, float y1, float x2, float y2) {
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
            MarlinUtils.logInfo(prefix + message);
        }

        @Override
        public long getNativeConsumer() {
            throw new InternalError("Not using a native peer");
        }
    }
}
