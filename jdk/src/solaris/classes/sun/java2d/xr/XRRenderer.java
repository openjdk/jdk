/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.xr;

import java.awt.*;
import java.awt.geom.*;

import sun.awt.SunToolkit;
import sun.java2d.SunGraphics2D;
import sun.java2d.loops.*;
import sun.java2d.pipe.Region;
import sun.java2d.pipe.PixelDrawPipe;
import sun.java2d.pipe.PixelFillPipe;
import sun.java2d.pipe.ShapeDrawPipe;
import sun.java2d.pipe.SpanIterator;
import sun.java2d.pipe.ShapeSpanIterator;
import sun.java2d.pipe.LoopPipe;

/**
 * XRender provides only accalerated rectangles. To emulate higher "order"
 *  geometry we have to pass everything else to DoPath/FillSpans.
 *
 * TODO: DrawRect could be instrified
 *
 * @author Clemens Eisserer
 */

public class XRRenderer implements PixelDrawPipe, PixelFillPipe, ShapeDrawPipe {
    XRDrawHandler drawHandler;
    MaskTileManager tileManager;

    public XRRenderer(MaskTileManager tileManager) {
        this.tileManager = tileManager;
        this.drawHandler = new XRDrawHandler();
    }

    /**
     * Common validate method, used by all XRRender functions to validate the
     * destination context.
     */
    private final void validateSurface(SunGraphics2D sg2d) {
        XRSurfaceData xrsd = (XRSurfaceData) sg2d.surfaceData;
        xrsd.validateAsDestination(sg2d, sg2d.getCompClip());
        xrsd.maskBuffer.validateCompositeState(sg2d.composite, sg2d.transform,
                                               sg2d.paint, sg2d);
    }

    public void drawLine(SunGraphics2D sg2d, int x1, int y1, int x2, int y2) {
        try {
            SunToolkit.awtLock();

            validateSurface(sg2d);
            int transx = sg2d.transX;
            int transy = sg2d.transY;

            XRSurfaceData xrsd = (XRSurfaceData) sg2d.surfaceData;

            tileManager.addLine(x1 + transx, y1 + transy,
                                x2 + transx, y2 + transy);
            tileManager.fillMask(xrsd);
        } finally {
            SunToolkit.awtUnlock();
        }
    }

    public void drawRect(SunGraphics2D sg2d,
                         int x, int y, int width, int height) {
        draw(sg2d, new Rectangle2D.Float(x, y, width, height));
    }

    public void drawPolyline(SunGraphics2D sg2d,
                             int xpoints[], int ypoints[], int npoints) {
        Path2D.Float p2d = new Path2D.Float();
        if (npoints > 1) {
            p2d.moveTo(xpoints[0], ypoints[0]);
            for (int i = 1; i < npoints; i++) {
                p2d.lineTo(xpoints[i], ypoints[i]);
            }
        }

        draw(sg2d, p2d);
    }

    public void drawPolygon(SunGraphics2D sg2d,
                            int xpoints[], int ypoints[], int npoints) {
        draw(sg2d, new Polygon(xpoints, ypoints, npoints));
    }

    public synchronized void fillRect(SunGraphics2D sg2d,
                                      int x, int y, int width, int height) {
        SunToolkit.awtLock();
        try {
            validateSurface(sg2d);

            XRSurfaceData xrsd = (XRSurfaceData) sg2d.surfaceData;

            x += sg2d.transform.getTranslateX();
            y += sg2d.transform.getTranslateY();

            tileManager.addRect(x, y, width, height);
            tileManager.fillMask(xrsd);

        } finally {
            SunToolkit.awtUnlock();
        }
    }

    public void fillPolygon(SunGraphics2D sg2d,
                            int xpoints[], int ypoints[], int npoints) {
        fill(sg2d, new Polygon(xpoints, ypoints, npoints));
    }

    public void drawRoundRect(SunGraphics2D sg2d,
                              int x, int y, int width, int height,
                              int arcWidth, int arcHeight) {
        draw(sg2d, new RoundRectangle2D.Float(x, y, width, height,
                                              arcWidth, arcHeight));
    }

    public void fillRoundRect(SunGraphics2D sg2d, int x, int y,
                              int width, int height,
                              int arcWidth, int arcHeight) {
        fill(sg2d, new RoundRectangle2D.Float(x, y, width, height,
                                              arcWidth, arcHeight));
    }

    public void drawOval(SunGraphics2D sg2d,
                         int x, int y, int width, int height) {
        draw(sg2d, new Ellipse2D.Float(x, y, width, height));
    }

    public void fillOval(SunGraphics2D sg2d,
                         int x, int y, int width, int height) {
        fill(sg2d, new Ellipse2D.Float(x, y, width, height));
    }

    public void drawArc(SunGraphics2D sg2d,
                       int x, int y, int width, int height,
                        int startAngle, int arcAngle) {
        draw(sg2d, new Arc2D.Float(x, y, width, height,
                                   startAngle, arcAngle, Arc2D.OPEN));
    }

    public void fillArc(SunGraphics2D sg2d,
                         int x, int y, int width, int height,
                         int startAngle, int arcAngle) {
        fill(sg2d, new Arc2D.Float(x, y, width, height,
             startAngle, arcAngle, Arc2D.PIE));
    }

    private class XRDrawHandler extends ProcessPath.DrawHandler {

        XRDrawHandler() {
            // these are bogus values; the caller will use validate()
            // to ensure that they are set properly prior to each usage
            super(0, 0, 0, 0);
        }

        /**
         * This method needs to be called prior to each draw/fillPath()
         * operation to ensure the clip bounds are up to date.
         */
        void validate(SunGraphics2D sg2d) {
            Region clip = sg2d.getCompClip();
            setBounds(clip.getLoX(), clip.getLoY(),
                      clip.getHiX(), clip.getHiY(), sg2d.strokeHint);
            validateSurface(sg2d);
        }

        public void drawLine(int x1, int y1, int x2, int y2) {
            tileManager.addLine(x1, y1, x2, y2);
        }

        public void drawPixel(int x, int y) {
            tileManager.addRect(x, y, 1, 1);
        }

        public void drawScanline(int x1, int x2, int y) {
            tileManager.addRect(x1, y, x2 - x1 + 1, 1);
        }
    }

    protected void drawPath(SunGraphics2D sg2d, Path2D.Float p2df,
                            int transx, int transy) {
        SunToolkit.awtLock();
        try {
            validateSurface(sg2d);
            drawHandler.validate(sg2d);
            ProcessPath.drawPath(drawHandler, p2df, transx, transy);
            tileManager.fillMask(((XRSurfaceData) sg2d.surfaceData));
        } finally {
            SunToolkit.awtUnlock();
        }
    }

    protected void fillPath(SunGraphics2D sg2d, Path2D.Float p2df,
                            int transx, int transy) {
        SunToolkit.awtLock();
        try {
            validateSurface(sg2d);
            drawHandler.validate(sg2d);
            ProcessPath.fillPath(drawHandler, p2df, transx, transy);
            tileManager.fillMask(((XRSurfaceData) sg2d.surfaceData));
        } finally {
            SunToolkit.awtUnlock();
        }
    }

    protected void fillSpans(SunGraphics2D sg2d, SpanIterator si,
                             int transx, int transy) {
        SunToolkit.awtLock();
        try {
            validateSurface(sg2d);
            int[] spanBox = new int[4];
            while (si.nextSpan(spanBox)) {
                tileManager.addRect(spanBox[0] + transx,
                                    spanBox[1] + transy,
                                    spanBox[2] - spanBox[0],
                                    spanBox[3] - spanBox[1]);
            }
            tileManager.fillMask(((XRSurfaceData) sg2d.surfaceData));
        } finally {
            SunToolkit.awtUnlock();
        }
    }

    public void draw(SunGraphics2D sg2d, Shape s) {
        if (sg2d.strokeState == SunGraphics2D.STROKE_THIN) {
            Path2D.Float p2df;
            int transx, transy;
            if (sg2d.transformState <= SunGraphics2D.TRANSFORM_INT_TRANSLATE) {
                if (s instanceof Path2D.Float) {
                    p2df = (Path2D.Float) s;
                } else {
                    p2df = new Path2D.Float(s);
                }
                transx = sg2d.transX;
                transy = sg2d.transY;
            } else {
                p2df = new Path2D.Float(s, sg2d.transform);
                transx = 0;
                transy = 0;
            }
            drawPath(sg2d, p2df, transx, transy);
        } else if (sg2d.strokeState < SunGraphics2D.STROKE_CUSTOM) {
            ShapeSpanIterator si = LoopPipe.getStrokeSpans(sg2d, s);
            try {
                fillSpans(sg2d, si, 0, 0);
            } finally {
                si.dispose();
            }
        } else {
            fill(sg2d, sg2d.stroke.createStrokedShape(s));
        }
    }

    public void fill(SunGraphics2D sg2d, Shape s) {
        int transx, transy;

        if (sg2d.strokeState == SunGraphics2D.STROKE_THIN) {
            // Here we are able to use fillPath() for
            // high-quality fills.
            Path2D.Float p2df;
            if (sg2d.transformState <= SunGraphics2D.TRANSFORM_INT_TRANSLATE) {
                if (s instanceof Path2D.Float) {
                    p2df = (Path2D.Float) s;
                } else {
                    p2df = new Path2D.Float(s);
                }
                transx = sg2d.transX;
                transy = sg2d.transY;
            } else {
                p2df = new Path2D.Float(s, sg2d.transform);
                transx = 0;
                transy = 0;
            }
            fillPath(sg2d, p2df, transx, transy);
            return;
        }

        AffineTransform at;
        if (sg2d.transformState <= SunGraphics2D.TRANSFORM_INT_TRANSLATE) {
            // Transform (translation) will be done by FillSpans
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
            fillSpans(sg2d, ssi, transx, transy);
        } finally {
            ssi.dispose();
        }
    }
}
