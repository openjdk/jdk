/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.xr;

import java.awt.*;
import java.awt.MultipleGradientPaint.*;
import java.awt.geom.*;
import java.awt.image.*;

import sun.java2d.*;
import sun.java2d.loops.*;
import sun.java2d.pipe.*;

abstract class XRPaints {
    static XRCompositeManager xrCompMan;

    static final XRGradient xrGradient = new XRGradient();
    static final XRLinearGradient xrLinearGradient = new XRLinearGradient();
    static final XRRadialGradient xrRadialGradient = new XRRadialGradient();
    static final XRTexture xrTexture = new XRTexture();

    public static void register(XRCompositeManager xrComp) {
        xrCompMan = xrComp;
    }

    private static XRPaints getXRPaint(SunGraphics2D sg2d) {
        switch (sg2d.paintState) {
        case SunGraphics2D.PAINT_GRADIENT:
            return xrGradient;

        case SunGraphics2D.PAINT_LIN_GRADIENT:
            return xrLinearGradient;

        case SunGraphics2D.PAINT_RAD_GRADIENT:
            return xrRadialGradient;

        case SunGraphics2D.PAINT_TEXTURE:
            return xrTexture;

        default:
            return null;
        }
    }

    /**
     * Attempts to locate an implementation corresponding to the paint state of
     * the provided SunGraphics2D object. If no implementation can be found, or
     * if the paint cannot be accelerated under the conditions of the
     * SunGraphics2D, this method returns false; otherwise, returns true.
     */
    static boolean isValid(SunGraphics2D sg2d) {
        XRPaints impl = getXRPaint(sg2d);
        return (impl != null && impl.isPaintValid(sg2d));
    }

    static void setPaint(SunGraphics2D sg2d, Paint paint) {
        XRPaints impl = getXRPaint(sg2d);
        if (impl != null) {
            impl.setXRPaint(sg2d, paint);
        }
    }

    /**
     * Returns true if this implementation is able to accelerate the Paint
     * object associated with, and under the conditions of, the provided
     * SunGraphics2D instance; otherwise returns false.
     */
    abstract boolean isPaintValid(SunGraphics2D sg2d);

    abstract void setXRPaint(SunGraphics2D sg2d, Paint paint);

    private static class XRGradient extends XRPaints {
        private XRGradient() {
        }

        /**
         * There are no restrictions for accelerating GradientPaint, so this
         * method always returns true.
         */
        @Override
        boolean isPaintValid(SunGraphics2D sg2d) {
            return true;
        }

        void setXRPaint(SunGraphics2D sg2d, Paint pt) {
            GradientPaint paint = (GradientPaint) pt;

            int[] pixels = convertToIntArgbPixels(new Color[] { paint.getColor1(), paint.getColor2() }, false);

            float fractions[] = new float[2];
            fractions[0] = 0;
            fractions[1] = 1;

            Point2D pt1 = paint.getPoint1();
            Point2D pt2 = paint.getPoint2();

            AffineTransform at = (AffineTransform) sg2d.transform.clone();
            try {
                at.invert();
            } catch (NoninvertibleTransformException ex) {
                at.setToIdentity();
            }

            int repeat = paint.isCyclic() ? XRUtils.RepeatReflect : XRUtils.RepeatPad;

            XRBackend con = xrCompMan.getBackend();
            int gradient = con.createLinearGradient(pt1, pt2, fractions, pixels, repeat, at);
            xrCompMan.setGradientPaint(new XRSurfaceData.XRInternalSurfaceData(con, gradient, at));
        }
    }

    public int getGradientLength(Point2D pt1, Point2D pt2) {
           double xDiff = Math.max(pt1.getX(), pt2.getX()) - Math.min(pt1.getX(), pt2.getX());
           double yDiff = Math.max(pt1.getY(), pt2.getY()) - Math.min(pt1.getY(), pt2.getY());
           return (int) Math.ceil(Math.sqrt(xDiff*xDiff + yDiff*yDiff));
    }

    private static class XRLinearGradient extends XRPaints {

        @Override
        boolean isPaintValid(SunGraphics2D sg2d) {
            return true;
        }

        @Override
        void setXRPaint(SunGraphics2D sg2d, Paint pt) {
            LinearGradientPaint paint = (LinearGradientPaint) pt;
            boolean linear = (paint.getColorSpace() == ColorSpaceType.LINEAR_RGB);

            Color[] colors = paint.getColors();
            Point2D pt1 = paint.getStartPoint();
            Point2D pt2 = paint.getEndPoint();


            AffineTransform at = paint.getTransform();
            at.preConcatenate(sg2d.transform);

            int repeat = XRUtils.getRepeatForCycleMethod(paint.getCycleMethod());
            float[] fractions = paint.getFractions();
            int[] pixels = convertToIntArgbPixels(colors, linear);

            try {
                at.invert();
            } catch (NoninvertibleTransformException ex) {
                ex.printStackTrace();
            }

            XRBackend con = xrCompMan.getBackend();
            int gradient = con.createLinearGradient(pt1, pt2, fractions, pixels, repeat, at);
            xrCompMan.setGradientPaint(new XRSurfaceData.XRInternalSurfaceData(con, gradient, at));
        }
    }

    private static class XRRadialGradient extends XRPaints {

        @Override
        boolean isPaintValid(SunGraphics2D sg2d) {
            RadialGradientPaint grad = (RadialGradientPaint) sg2d.paint;
            return grad.getFocusPoint().equals(grad.getCenterPoint());
        }

        @Override
        void setXRPaint(SunGraphics2D sg2d, Paint pt) {
            RadialGradientPaint paint = (RadialGradientPaint) pt;
            boolean linear = (paint.getColorSpace() == ColorSpaceType.LINEAR_RGB);
            Color[] colors = paint.getColors();
            Point2D center = paint.getCenterPoint();
            Point2D focus = paint.getFocusPoint();

            int repeat = XRUtils.getRepeatForCycleMethod(paint.getCycleMethod());
            float[] fractions = paint.getFractions();
            int[] pixels = convertToIntArgbPixels(colors, linear);
            float radius = paint.getRadius();

            // save original (untransformed) center and focus points
            double cx = center.getX();
            double cy = center.getY();
            double fx = focus.getX();
            double fy = focus.getY();

            AffineTransform at = paint.getTransform();
            at.preConcatenate(sg2d.transform);
            focus = at.transform(focus, focus);

            // transform unit circle to gradient coords; we start with the
            // unit circle (center=(0,0), focus on positive x-axis, radius=1)
            // and then transform into gradient space
            at.translate(cx, cy);
            at.rotate(fx - cx, fy - cy);
            // at.scale(radius, radius);

            // invert to get mapping from device coords to unit circle
            try {
                at.invert();
            } catch (Exception e) {
                at.setToScale(0.0, 0.0);
            }
            focus = at.transform(focus, focus);

            // clamp the focus point so that it does not rest on, or outside
            // of, the circumference of the gradient circle
            fx = Math.min(focus.getX(), 0.99);

            XRBackend con = xrCompMan.getBackend();
            int gradient = con.createRadialGradient(new Point2D.Float(0, 0), new Point2D.Float(0, 0), 0, radius, fractions, pixels, repeat, at);
            xrCompMan.setGradientPaint(new XRSurfaceData.XRInternalSurfaceData(con, gradient, at));
        }
    }

    private static class XRTexture extends XRPaints {

        @Override
        boolean isPaintValid(SunGraphics2D sg2d) {
            TexturePaint paint = (TexturePaint) sg2d.paint;
            BufferedImage bi = paint.getImage();
            XRSurfaceData dstData = (XRSurfaceData) sg2d.getDestSurface();

            SurfaceData srcData = dstData.getSourceSurfaceData(bi, SunGraphics2D.TRANSFORM_ISIDENT, CompositeType.SrcOver, null);
            if (!(srcData instanceof XRSurfaceData)) {
                // REMIND: this is a hack that attempts to cache the system
                // memory image from the TexturePaint instance into an
                // OpenGL texture...
                srcData = dstData.getSourceSurfaceData(bi, SunGraphics2D.TRANSFORM_ISIDENT, CompositeType.SrcOver, null);
                if (!(srcData instanceof XRSurfaceData)) {
                    return false;
                }
            }

            return true;
        }

        @Override
        void setXRPaint(SunGraphics2D sg2d, Paint pt) {
            TexturePaint paint = (TexturePaint) pt;

            BufferedImage bi = paint.getImage();
            SurfaceData dstData = sg2d.surfaceData;
            SurfaceData srcData = dstData.getSourceSurfaceData(bi, SunGraphics2D.TRANSFORM_ISIDENT, CompositeType.SrcOver, null);

            // REMIND: this hack tries to ensure that we have a cached texture
            if (!(srcData instanceof XRSurfaceData)) {
                srcData = dstData.getSourceSurfaceData(paint.getImage(), SunGraphics2D.TRANSFORM_ISIDENT, CompositeType.SrcOver, null);
                if (!(srcData instanceof XRSurfaceData)) {
                    throw new InternalError("Surface not cachable");
                }
            }

            XRSurfaceData x11SrcData = (XRSurfaceData) srcData;

            AffineTransform at = (AffineTransform) sg2d.transform.clone();
            Rectangle2D anchor = paint.getAnchorRect();
            at.translate(anchor.getX(), anchor.getY());
            at.scale(anchor.getWidth() / ((double) bi.getWidth()), anchor.getHeight() / ((double) bi.getHeight()));

            try {
                at.invert();
            } catch (NoninvertibleTransformException ex) {
                at.setToIdentity(); /* TODO: Right thing to do in this case? */
            }

            x11SrcData.validateAsSource(at, XRUtils.RepeatNormal, XRUtils.ATransOpToXRQuality(sg2d.interpolationType));
            xrCompMan.setTexturePaint(((XRSurfaceData) srcData));
        }
    }

    public int[] convertToIntArgbPixels(Color[] colors, boolean linear) {
        int[] pixels = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            pixels[i] = colorToIntArgbPixel(colors[i], linear);
        }
        return pixels;
    }

    public int colorToIntArgbPixel(Color c, boolean linear) {
        int rgb = c.getRGB();

        int a = rgb >>> 24;
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = (rgb) & 0xff;
        if (linear) {
            r = BufferedPaints.convertSRGBtoLinearRGB(r);
            g = BufferedPaints.convertSRGBtoLinearRGB(g);
            b = BufferedPaints.convertSRGBtoLinearRGB(b);
        }

        a *= xrCompMan.getExtraAlpha();

        return ((a << 24) | (r << 16) | (g << 8) | (b));
    }
}
