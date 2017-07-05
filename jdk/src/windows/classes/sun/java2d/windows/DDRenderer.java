/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.windows;

import java.awt.Composite;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.pipe.Region;
import sun.java2d.pipe.PixelDrawPipe;
import sun.java2d.pipe.PixelFillPipe;
import sun.java2d.pipe.ShapeDrawPipe;
import sun.java2d.pipe.SpanIterator;
import sun.java2d.loops.GraphicsPrimitive;

/**
 * DDRenderer
 *
 * This class accelerates rendering to a surface of type
 * WinOffScreenSurfaceData.  The renderers in here are simply java wrappers
 * around native methods that do the real work.
 */
public class DDRenderer extends GDIRenderer {

    //
    // Native implementations
    //
    native void doDrawLineDD(SurfaceData sData,
                             int color,
                             int x1, int y1, int x2, int y2);
    native void doFillRectDD(SurfaceData sData,
                             int color,
                             int left, int top,
                             int right, int bottom);
    native void doDrawRectDD(SurfaceData sData,
                             int color,
                             int x, int y, int w, int h);

    //
    // Internal Java methods for rendering
    //

    /**
     * Clip the given line to the clip bounds in sg2d.
     * Assume that the line passed in is given in the order of
     * x1 <= x2, y1 <= y2
     */
    private void clipAndDrawLine(SunGraphics2D sg2d,
                                 int x1, int y1, int x2, int y2)
    {
        // If any of these are true, the line lies outside of the
        // clip bounds
        Region clip = sg2d.getCompClip();
        int cx1 = clip.getLoX();
        int cy1 = clip.getLoY();
        int cx2 = clip.getHiX();
        int cy2 = clip.getHiY();
        // For each edge, clip the appropriate coordinate against
        // that edge.  We are only dealing with horizontal or vertical lines
        // for now, so there is no interpolation between points to
        // the proper clip coordinate.
        if (x1 <  cx1) x1 = cx1;
        if (y1 <  cy1) y1 = cy1;
        if (x2 >= cx2) x2 = cx2 - 1;
        if (y2 >= cy2) y2 = cy2 - 1;
        // If the start moved past the end (or vice versa),
        // then we are outside the clip.
        if (x1 <= x2 && y1 <= y2) {
            doDrawLineDD(sg2d.surfaceData, sg2d.pixel, x1, y1, x2, y2);
        }
    }

    // REMIND: This is just a hack to get WIDE lines to honor the
    // necessary hinted pixelization rules.  This should be replaced
    // by a native FillSpans method or a getHintedStrokeGeneralPath()
    // method that could be filled by the doShape method more quickly.
    public void doFillSpans(SunGraphics2D sg2d, SpanIterator si) {
        int box[] = new int[4];
        SurfaceData sd = sg2d.surfaceData;
        while (si.nextSpan(box)) {
            doFillRectDD(sd, sg2d.pixel, box[0], box[1], box[2], box[3]);
        }
    }


    //
    // Java wrappers for the primitive renderers
    //

    /**
     * drawLine draws a line between the pixel at x1, y1 and the
     * pixel at x2, y2 (including the last pixel).
     */
    public void drawLine(SunGraphics2D sg2d,
                         int x1, int y1, int x2, int y2)
    {
        // Note that we only handle horizontal or vertical lines through
        // this renderer.  This is because the implementation uses a fill
        // Blt through DirectDraw, which only works for rectangle shapes.
        if (x1 == x2 || y1 == y2) {

            int transx1 = x1 + sg2d.transX;
            int transy1 = y1 + sg2d.transY;
            int transx2 = x2 + sg2d.transX;
            int transy2 = y2 + sg2d.transY;
            int t;
            // First, set the ordering of the line coordinates;
            // clipAndDrawLine() expects x1 < x2 and y1 < y2
            if (transx1 > transx2) {
                t = transx1;
                transx1 = transx2;
                transx2 = t;
            }
            if (transy1 > transy2) {
                t = transy1;
                transy1 = transy2;
                transy2 = t;
            }
            clipAndDrawLine(sg2d, transx1, transy1, transx2, transy2);
        }
        else {
            // Punt to our superclass renderer to render diagonal lines
            super.drawLine(sg2d, x1, y1, x2, y2);
        }
    }


    /**
     * fillRect filles a rect from the pixel at x, y to (but not including)
     * the pixel at (x + width), (y + height)
     */
    public void fillRect(SunGraphics2D sg2d,
                         int x, int y, int width, int height)
    {
        int clipLeft, clipTop, clipRight, clipBottom;

        // REMIND: This check should probably go in SunGraphics2D instead.
        if (width <= 0 || height <= 0) {
            return;
        }

        // Here we clip the fill rect to the size of the clip bounds in sg2d.
        // The native code can then assume that it receives a non-empty, post-
        // clipped primitive.
        clipLeft = x + sg2d.transX;
        clipTop = y + sg2d.transY;
        clipRight = clipLeft + width;
        clipBottom = clipTop + height;

        Region clip = sg2d.getCompClip();

        // Clip each edge of the rect to the appropriate edge of the clip
        // bounds.
        if (clipLeft   < clip.getLoX()) clipLeft   = clip.getLoX();
        if (clipTop    < clip.getLoY()) clipTop    = clip.getLoY();
        if (clipRight  > clip.getHiX()) clipRight  = clip.getHiX();
        if (clipBottom > clip.getHiY()) clipBottom = clip.getHiY();

        if (clipRight > clipLeft && clipBottom > clipTop) {
            doFillRectDD(sg2d.surfaceData, sg2d.pixel, clipLeft, clipTop,
                         clipRight, clipBottom);
        }
    }


    /**
     * draw a rectangle outline starting at x, y and going to the pixel
     * at (x + width), (y + width) (including the lower right pixel)
     */
    public void drawRect(SunGraphics2D sg2d,
                         int x, int y, int width, int height)
    {
        if (width < 2 || height < 2) {
            fillRect(sg2d, x, y, width+1, height+1);
            return;
        }
        int transx = x + sg2d.transX;
        int transy = y + sg2d.transY;
        Region clip = sg2d.getCompClip();
        if (!clip.encompassesXYWH(transx, transy, width+1, height+1)) {
            // Rect needs clipping - draw each edge separately, clipping
            // as we go.
            // Prefer longer horizontal lines if possible.
            clipAndDrawLine(sg2d, transx, transy,
                            transx + width, transy);
            clipAndDrawLine(sg2d, transx, transy + 1,
                            transx, transy + height - 1);
            clipAndDrawLine(sg2d, transx + width, transy + 1,
                            transx + width, transy + height - 1);
            clipAndDrawLine(sg2d, transx, transy + height,
                            transx + width, transy + height);
        } else {
            // No clipping needed - just call native method which draws
            // all edges in one method
            doDrawRectDD(sg2d.surfaceData, sg2d.pixel, transx, transy,
                         width, height);
        }
    }

    @Override
    public native void devCopyArea(SurfaceData sData,
                                   int srcx, int srcy, int dx, int dy,
                                   int w, int h);

    public DDRenderer traceWrapDD() {
        return new Tracer();
    }

    public static class Tracer extends DDRenderer {
        void doDrawLine(SurfaceData sData,
                        Region clip, Composite comp, int color,
                        int x1, int y1, int x2, int y2)
        {
            GraphicsPrimitive.tracePrimitive("GDIDrawLine");
            super.doDrawLine(sData, clip, comp, color, x1, y1, x2, y2);
        }
        void doDrawRect(SurfaceData sData,
                        Region clip, Composite comp, int color,
                        int x, int y, int w, int h)
        {
            GraphicsPrimitive.tracePrimitive("GDIDrawRect");
            super.doDrawRect(sData, clip, comp, color, x, y, w, h);
        }
        void doDrawRoundRect(SurfaceData sData,
                             Region clip, Composite comp, int color,
                             int x, int y, int w, int h,
                             int arcW, int arcH)
        {
            GraphicsPrimitive.tracePrimitive("GDIDrawRoundRect");
            super.doDrawRoundRect(sData, clip, comp, color,
                                  x, y, w, h, arcW, arcH);
        }
        void doDrawOval(SurfaceData sData,
                        Region clip, Composite comp, int color,
                        int x, int y, int w, int h)
        {
            GraphicsPrimitive.tracePrimitive("GDIDrawOval");
            super.doDrawOval(sData, clip, comp, color, x, y, w, h);
        }
        void doDrawArc(SurfaceData sData,
                       Region clip, Composite comp, int color,
                       int x, int y, int w, int h,
                       int angleStart, int angleExtent)
        {
            GraphicsPrimitive.tracePrimitive("GDIDrawArc");
            super.doDrawArc(sData, clip, comp, color, x, y, w, h,
                            angleStart, angleExtent);
        }
        void doDrawPoly(SurfaceData sData,
                        Region clip, Composite comp, int color,
                        int transx, int transy,
                        int[] xpoints, int[] ypoints,
                        int npoints, boolean isclosed)
        {
            GraphicsPrimitive.tracePrimitive("GDIDrawPoly");
            super.doDrawPoly(sData, clip, comp, color, transx, transy,
                             xpoints, ypoints, npoints, isclosed);
        }
        void doFillRect(SurfaceData sData,
                        Region clip, Composite comp, int color,
                        int x, int y, int w, int h)
        {
            GraphicsPrimitive.tracePrimitive("GDIFillRect");
            super.doFillRect(sData, clip, comp, color, x, y, w, h);
        }
        void doFillRoundRect(SurfaceData sData,
                             Region clip, Composite comp, int color,
                             int x, int y, int w, int h,
                             int arcW, int arcH)
        {
            GraphicsPrimitive.tracePrimitive("GDIFillRoundRect");
            super.doFillRoundRect(sData, clip, comp, color,
                                  x, y, w, h, arcW, arcH);
        }
        void doFillOval(SurfaceData sData,
                        Region clip, Composite comp, int color,
                        int x, int y, int w, int h)
        {
            GraphicsPrimitive.tracePrimitive("GDIFillOval");
            super.doFillOval(sData, clip, comp, color, x, y, w, h);
        }
        void doFillArc(SurfaceData sData,
                       Region clip, Composite comp, int color,
                       int x, int y, int w, int h,
                       int angleStart, int angleExtent)
        {
            GraphicsPrimitive.tracePrimitive("GDIFillArc");
            super.doFillArc(sData, clip, comp, color, x, y, w, h,
                            angleStart, angleExtent);
        }
        void doFillPoly(SurfaceData sData,
                        Region clip, Composite comp, int color,
                        int transx, int transy,
                        int[] xpoints, int[] ypoints,
                        int npoints)
        {
            GraphicsPrimitive.tracePrimitive("GDIFillPoly");
            super.doFillPoly(sData, clip, comp, color, transx, transy,
                             xpoints, ypoints, npoints);
        }
        void doShape(SurfaceData sData,
                     Region clip, Composite comp, int color,
                     int transX, int transY,
                     Path2D.Float p2df, boolean isfill)
        {
            GraphicsPrimitive.tracePrimitive(isfill
                                             ? "GDIFillShape"
                                             : "GDIDrawShape");
            super.doShape(sData, clip, comp, color,
                          transX, transY, p2df, isfill);
        }
        public void devCopyArea(SurfaceData sData,
                                int srcx, int srcy,
                                int dx, int dy,
                                int w, int h)
        {
            GraphicsPrimitive.tracePrimitive("DXCopyArea");
            super.devCopyArea(sData, srcx, srcy, dx, dy, w, h);
        }
        void doDrawLineDD(SurfaceData sData,
                          int color,
                          int x1, int y1, int x2, int y2)
        {
            GraphicsPrimitive.tracePrimitive("DXDrawLine");
            super.doDrawLineDD(sData, color, x1, y1, x2, y2);
        }
        void doFillRectDD(SurfaceData sData,
                          int color,
                          int left, int top,
                          int right, int bottom)
        {
            GraphicsPrimitive.tracePrimitive("DXFillRect");
            super.doFillRectDD(sData, color, left, top, right, bottom);
        }
        void doDrawRectDD(SurfaceData sData,
                          int color,
                          int x, int y, int w, int h)
        {
            GraphicsPrimitive.tracePrimitive("DXDrawRect");
            super.doDrawRectDD(sData, color, x, y, w, h);
        }
    }
}
