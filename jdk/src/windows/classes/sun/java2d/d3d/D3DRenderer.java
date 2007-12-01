/*
 * Copyright 2002-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.java2d.d3d;

import java.awt.Composite;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.IllegalPathStateException;
import java.awt.geom.PathIterator;
import java.awt.geom.RoundRectangle2D;
import sun.java2d.SunGraphics2D;
import sun.java2d.pipe.Region;
import sun.java2d.SurfaceData;
import sun.java2d.loops.GraphicsPrimitive;
import sun.java2d.pipe.LoopPipe;
import sun.java2d.pipe.ShapeSpanIterator;
import sun.java2d.pipe.SpanIterator;

import static sun.java2d.d3d.D3DContext.*;
import sun.java2d.windows.DDRenderer;

public class D3DRenderer extends DDRenderer {

    native boolean doDrawLineD3D(long pData, long pCtx,
                                 int x1, int y1, int x2, int y2);
    native boolean doDrawRectD3D(long pData, long pCtx,
                                 int x, int y, int w, int h);
    native boolean doFillRectD3D(long pData, long pCtx, int x, int y,
                                 int width, int height);
    native void doDrawPoly(long pData, long pCtx, int transx, int transy,
                           int[] xpoints, int[] ypoints,
                           int npoints, boolean isclosed);
    native void devFillSpans(long pData, long pCtx, SpanIterator si,
                             long iterator, int transx, int transy);


    private long getContext(SunGraphics2D sg2d) {
        AffineTransform at =
            sg2d.transformState < sg2d.TRANSFORM_TRANSLATESCALE ?
            null : sg2d.transform;
        int ctxflags = (sg2d.eargb >>> 24) == 0xff ?
            SRC_IS_OPAQUE : NO_CONTEXT_FLAGS;
        return D3DContext.getContext(null, sg2d.surfaceData,
                                     sg2d.getCompClip(),
                                     sg2d.getComposite(),
                                     at,
                                     sg2d.eargb,
                                     ctxflags);
    }

    @Override
    public void drawLine(SunGraphics2D sg2d,
                         int x1, int y1, int x2, int y2)
    {
        synchronized (D3DContext.LOCK) {
            doDrawLineD3D(sg2d.surfaceData.getNativeOps(),
                          getContext(sg2d),
                          x1 + sg2d.transX, y1 + sg2d.transY,
                          x2 + sg2d.transX, y2 + sg2d.transY);
        }
    }

    @Override
    public void fillRect(SunGraphics2D sg2d,
                         int x, int y, int width, int height)
    {
        synchronized (D3DContext.LOCK) {
            doFillRectD3D(sg2d.surfaceData.getNativeOps(),
                          getContext(sg2d),
                          sg2d.transX + x, sg2d.transY + y, width, height);
        }
    }

    @Override
    public void drawRect(SunGraphics2D sg2d,
                         int x, int y, int width, int height)
    {
        synchronized (D3DContext.LOCK) {
            doDrawRectD3D(sg2d.surfaceData.getNativeOps(),
                          getContext(sg2d),
                          x + sg2d.transX, sg2d.transY + y, width, height);
        }
    }

    @Override
    public void drawPolyline(SunGraphics2D sg2d,
                             int xpoints[], int ypoints[], int npoints)
    {
        synchronized (D3DContext.LOCK) {
            doDrawPoly(sg2d.surfaceData.getNativeOps(),
                       getContext(sg2d),
                       sg2d.transX, sg2d.transY,
                       xpoints, ypoints, npoints, false);
        }
    }

    @Override
    public void drawPolygon(SunGraphics2D sg2d,
                            int xpoints[], int ypoints[], int npoints)
    {
        synchronized (D3DContext.LOCK) {
            doDrawPoly(sg2d.surfaceData.getNativeOps(),
                       getContext(sg2d),
                       sg2d.transX, sg2d.transY,
                       xpoints, ypoints, npoints, true);
        }
    }

    @Override
    public void drawRoundRect(SunGraphics2D sg2d,
                              int x, int y, int width, int height,
                              int arcWidth, int arcHeight)
    {
        draw(sg2d, new RoundRectangle2D.Float(x, y, width, height,
                                              arcWidth, arcHeight));
    }

    @Override
    public void drawOval(SunGraphics2D sg2d,
                         int x, int y, int width, int height)
    {
        draw(sg2d, new Ellipse2D.Float(x, y, width, height));
    }

    @Override
    public void drawArc(SunGraphics2D sg2d,
                        int x, int y, int width, int height,
                        int startAngle, int arcAngle)
    {
        draw(sg2d, new Arc2D.Float(x, y, width, height,
                                   startAngle, arcAngle,
                                   Arc2D.OPEN));
    }

    @Override
    public void fillRoundRect(SunGraphics2D sg2d,
                              int x, int y, int width, int height,
                              int arcWidth, int arcHeight)
    {
        fill(sg2d, new RoundRectangle2D.Float(x, y, width, height,
             arcWidth, arcHeight));
    }

    @Override
    public void fillOval(SunGraphics2D sg2d,
                         int x, int y, int width, int height)
    {
        fill(sg2d, new Ellipse2D.Float(x, y, width, height));
    }

    @Override
    public void fillArc(SunGraphics2D sg2d,
                        int x, int y, int width, int height,
                        int startAngle, int arcAngle)
    {
        fill(sg2d, new Arc2D.Float(x, y, width, height,
             startAngle, arcAngle, Arc2D.PIE));
    }

    @Override
    public void fillPolygon(SunGraphics2D sg2d,
                            int xpoints[], int ypoints[],
                            int npoints)
    {
        fill(sg2d, new Polygon(xpoints, ypoints, npoints));
    }

    @Override
    public void draw(SunGraphics2D sg2d, Shape s)
    {
        if (sg2d.strokeState == sg2d.STROKE_THIN) {
            Polygon p;
            if (s instanceof Polygon) {
                p = (Polygon) s;
                drawPolygon(sg2d, p.xpoints, p.ypoints, p.npoints);
                return;
            }
            // we're letting d3d handle the transforms
            PathIterator pi = s.getPathIterator(null, 0.5f);
            p = new Polygon();
            float coords[] = new float[2];
            while (!pi.isDone()) {
                switch (pi.currentSegment(coords)) {
                    case PathIterator.SEG_MOVETO:
                        if (p.npoints > 1) {
                            drawPolyline(sg2d, p.xpoints, p.ypoints, p.npoints);
                        }
                        p.reset();
                        p.addPoint((int) Math.floor(coords[0]),
                            (int) Math.floor(coords[1]));
                        break;
                    case PathIterator.SEG_LINETO:
                        if (p.npoints == 0) {
                            throw new IllegalPathStateException
                                ("missing initial moveto in path definition");
                        }
                        p.addPoint((int) Math.floor(coords[0]),
                            (int) Math.floor(coords[1]));
                        break;
                    case PathIterator.SEG_CLOSE:
                        if (p.npoints > 0) {
                            p.addPoint(p.xpoints[0], p.ypoints[0]);
                        }
                        break;
                    default:
                        throw new
                            IllegalPathStateException("path not flattened");
                }
                pi.next();
            }
            if (p.npoints > 1) {
                drawPolyline(sg2d, p.xpoints, p.ypoints, p.npoints);
            }
        } else if (sg2d.strokeState < sg2d.STROKE_CUSTOM) {
            ShapeSpanIterator si = LoopPipe.getStrokeSpans(sg2d, s);
            try {
                synchronized (D3DContext.LOCK) {
                    int ctxflags = (sg2d.eargb >>> 24) == 0xff ?
                        SRC_IS_OPAQUE : NO_CONTEXT_FLAGS;
                    // in this case the spans will be pre-transformed, so we
                    // pass null transform to getContext
                    long pCtx = D3DContext.getContext(null, sg2d.surfaceData,
                                                      sg2d.getCompClip(),
                                                      sg2d.getComposite(),
                                                      null /*transform*/,
                                                      sg2d.eargb/*pixel*/,
                                                      ctxflags);
                    devFillSpans(sg2d.surfaceData.getNativeOps(), pCtx, si,
                                 si.getNativeIterator(), 0, 0);
                }
            } finally {
                si.dispose();
            }
        } else {
            fill(sg2d, sg2d.stroke.createStrokedShape(s));
        }
    }

    @Override
    public void fill(SunGraphics2D sg2d, Shape s) {
        AffineTransform at;
        int transx, transy;

        if ( sg2d.transformState < sg2d.TRANSFORM_TRANSLATESCALE) {
            // Transform (translation) will be done by devFillSpans
            at = null;
            transx = sg2d.transX;
            transy = sg2d.transY;
        } else {
            // Transform will be done by the PathIterator
            at = sg2d.transform;
            transx = transy = 0;
        }

        ShapeSpanIterator ssi = LoopPipe.getFillSSI(sg2d);
        try {
            // Subtract transx/y from the SSI clip to match the
            // (potentially untranslated) geometry fed to it
            Region clip = sg2d.getCompClip();
            ssi.setOutputAreaXYXY(clip.getLoX() - transx,
                                  clip.getLoY() - transy,
                                  clip.getHiX() - transx,
                                  clip.getHiY() - transy);
            ssi.appendPath(s.getPathIterator(at));
            synchronized (D3DContext.LOCK) {
                int ctxflags = (sg2d.eargb >>> 24) == 0xff ?
                    SRC_IS_OPAQUE : NO_CONTEXT_FLAGS;
                long pCtx = D3DContext.getContext(null, sg2d.surfaceData,
                                                  sg2d.getCompClip(),
                                                  sg2d.getComposite(),
                                                  null/*transform*/,
                                                  sg2d.eargb/*pixel*/,
                                                  ctxflags);
                devFillSpans(sg2d.surfaceData.getNativeOps(), pCtx, ssi,
                             ssi.getNativeIterator(),
                             transx, transy);
            }
        } finally {
            ssi.dispose();
        }
    }

    D3DRenderer traceWrapD3D() {
        return new Tracer();
    }

    private class Tracer extends D3DRenderer {
        @Override
        public void drawLine(SunGraphics2D sg2d,
                             int x1, int y1, int x2, int y2)
        {
            GraphicsPrimitive.tracePrimitive("D3DDrawLine");
            super.drawLine(sg2d, x1, y1, x2, y2);
        }
        @Override
        public void drawRect(SunGraphics2D sg2d, int x, int y, int w, int h) {
            GraphicsPrimitive.tracePrimitive("D3DDrawRect");
            super.drawRect(sg2d, x, y, w, h);
        }
        @Override
        public void drawPolyline(SunGraphics2D sg2d,
                                 int[] xPoints, int[] yPoints,
                                 int nPoints)
        {
            GraphicsPrimitive.tracePrimitive("D3DDrawPolyline");
            super.drawPolyline(sg2d, xPoints, yPoints, nPoints);
        }
        @Override
        public void drawPolygon(SunGraphics2D sg2d,
                                int[] xPoints, int[] yPoints,
                                int nPoints)
        {
            GraphicsPrimitive.tracePrimitive("D3DDrawPolygon");
            super.drawPolygon(sg2d, xPoints, yPoints, nPoints);
        }
        @Override
        public void fillRect(SunGraphics2D sg2d, int x, int y, int w, int h) {
            GraphicsPrimitive.tracePrimitive("D3DFillRect");
            super.fillRect(sg2d, x, y, w, h);
        }
        @Override
        void devFillSpans(long pData, long pCtx, SpanIterator si, long iterator,
                          int transx, int transy)
        {
            GraphicsPrimitive.tracePrimitive("D3DFillSpans");
            super.devFillSpans(pData, pCtx, si, iterator, transx, transy);
        }
        @Override
        public void devCopyArea(SurfaceData sData,
                                int srcx, int srcy,
                                int dx, int dy,
                                int w, int h)
        {
            GraphicsPrimitive.tracePrimitive("DXCopyArea");
            super.devCopyArea(sData, srcx, srcy, dx, dy, w, h);
        }

    }
}
