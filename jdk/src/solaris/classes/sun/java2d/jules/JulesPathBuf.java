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

package sun.java2d.jules;

import java.awt.*;
import java.awt.geom.*;
import sun.awt.X11GraphicsEnvironment;
import sun.java2d.pipe.*;
import sun.java2d.xr.*;

public class JulesPathBuf {
    static final double[] emptyDash = new double[0];

    private static final byte CAIRO_PATH_OP_MOVE_TO = 0;
    private static final byte CAIRO_PATH_OP_LINE_TO = 1;
    private static final byte CAIRO_PATH_OP_CURVE_TO = 2;
    private static final byte CAIRO_PATH_OP_CLOSE_PATH = 3;

    private static final int  CAIRO_FILL_RULE_WINDING = 0;
    private static final int CAIRO_FILL_RULE_EVEN_ODD = 1;

    GrowablePointArray points = new GrowablePointArray(128);
    GrowableByteArray ops = new GrowableByteArray(1, 128);
    int[] xTrapArray = new int[512];

    private static final boolean isCairoAvailable;

    static {
        isCairoAvailable =
           java.security.AccessController.doPrivileged(
                          new java.security.PrivilegedAction<Boolean>() {
            public Boolean run() {
                boolean loadSuccess = false;
                if (X11GraphicsEnvironment.isXRenderAvailable()) {
                    try {
                        System.loadLibrary("jules");
                        loadSuccess = true;
                        if (X11GraphicsEnvironment.isXRenderVerbose()) {
                            System.out.println(
                                       "Xrender: INFO: Jules library loaded");
                        }
                    } catch (UnsatisfiedLinkError ex) {
                        loadSuccess = false;
                        if (X11GraphicsEnvironment.isXRenderVerbose()) {
                            System.out.println(
                                "Xrender: INFO: Jules library not installed.");
                        }
                    }
                }
                return Boolean.valueOf(loadSuccess);
            }
        });
    }

    public static boolean isCairoAvailable() {
        return isCairoAvailable;
    }

    public TrapezoidList tesselateFill(Shape s, AffineTransform at, Region clip) {
        int windingRule = convertPathData(s, at);
        xTrapArray[0] = 0;

        xTrapArray = tesselateFillNative(points.getArray(), ops.getArray(),
                                         points.getSize(), ops.getSize(),
                                         xTrapArray, xTrapArray.length,
                                         getCairoWindingRule(windingRule),
                                         clip.getLoX(), clip.getLoY(),
                                         clip.getHiX(), clip.getHiY());

        return new TrapezoidList(xTrapArray);
    }

    public TrapezoidList tesselateStroke(Shape s, BasicStroke bs, boolean thin,
                                         boolean adjust, boolean antialias,
                                         AffineTransform at, Region clip) {

        float lw;
        if (thin) {
            if (antialias) {
                lw = 0.5f;
            } else {
                lw = 1.0f;
            }
        } else {
            lw = bs.getLineWidth();
        }

        convertPathData(s, at);

        double[] dashArray = floatToDoubleArray(bs.getDashArray());
        xTrapArray[0] = 0;

        xTrapArray =
             tesselateStrokeNative(points.getArray(), ops.getArray(),
                                   points.getSize(), ops.getSize(),
                                   xTrapArray, xTrapArray.length, lw,
                                   bs.getEndCap(), bs.getLineJoin(),
                                   bs.getMiterLimit(), dashArray,
                                   dashArray.length, bs.getDashPhase(),
                                   1, 0, 0, 0, 1, 0,
                                   clip.getLoX(), clip.getLoY(),
                                   clip.getHiX(), clip.getHiY());

        return new TrapezoidList(xTrapArray);
    }

    protected double[] floatToDoubleArray(float[] dashArrayFloat) {
        double[] dashArrayDouble = emptyDash;
        if (dashArrayFloat != null) {
            dashArrayDouble = new double[dashArrayFloat.length];

            for (int i = 0; i < dashArrayFloat.length; i++) {
                dashArrayDouble[i] = dashArrayFloat[i];
            }
        }

        return dashArrayDouble;
    }

    protected int convertPathData(Shape s, AffineTransform at) {
        PathIterator pi = s.getPathIterator(at);

        double[] coords = new double[6];
        double currX = 0;
        double currY = 0;

        while (!pi.isDone()) {
            int curOp = pi.currentSegment(coords);

            int pointIndex;
            switch (curOp) {

            case PathIterator.SEG_MOVETO:
                ops.addByte(CAIRO_PATH_OP_MOVE_TO);
                pointIndex = points.getNextIndex();
                points.setX(pointIndex, DoubleToCairoFixed(coords[0]));
                points.setY(pointIndex, DoubleToCairoFixed(coords[1]));
                currX = coords[0];
                currY = coords[1];
                break;

            case PathIterator.SEG_LINETO:
                ops.addByte(CAIRO_PATH_OP_LINE_TO);
                pointIndex = points.getNextIndex();
                points.setX(pointIndex, DoubleToCairoFixed(coords[0]));
                points.setY(pointIndex, DoubleToCairoFixed(coords[1]));
                currX = coords[0];
                currY = coords[1];
                break;

                /**
                 *    q0 = p0
                 *    q1 = (p0+2*p1)/3
                 *    q2 = (p2+2*p1)/3
                 *    q3 = p2
                 */
            case PathIterator.SEG_QUADTO:
                double x1 = coords[0];
                double y1 = coords[1];
                double x2, y2;
                double x3 = coords[2];
                double y3 = coords[3];

                x2 = x1 + (x3 - x1) / 3;
                y2 = y1 + (y3 - y1) / 3;
                x1 = currX + 2 * (x1 - currX) / 3;
                y1 =currY + 2 * (y1 - currY) / 3;

                ops.addByte(CAIRO_PATH_OP_CURVE_TO);
                pointIndex = points.getNextIndex();
                points.setX(pointIndex, DoubleToCairoFixed(x1));
                points.setY(pointIndex, DoubleToCairoFixed(y1));
                pointIndex = points.getNextIndex();
                points.setX(pointIndex, DoubleToCairoFixed(x2));
                points.setY(pointIndex, DoubleToCairoFixed(y2));
                pointIndex = points.getNextIndex();
                points.setX(pointIndex, DoubleToCairoFixed(x3));
                points.setY(pointIndex, DoubleToCairoFixed(y3));
                currX = x3;
                currY = y3;
                break;

            case PathIterator.SEG_CUBICTO:
                ops.addByte(CAIRO_PATH_OP_CURVE_TO);
                pointIndex = points.getNextIndex();
                points.setX(pointIndex, DoubleToCairoFixed(coords[0]));
                points.setY(pointIndex, DoubleToCairoFixed(coords[1]));
                pointIndex = points.getNextIndex();
                points.setX(pointIndex, DoubleToCairoFixed(coords[2]));
                points.setY(pointIndex, DoubleToCairoFixed(coords[3]));
                pointIndex = points.getNextIndex();
                points.setX(pointIndex, DoubleToCairoFixed(coords[4]));
                points.setY(pointIndex, DoubleToCairoFixed(coords[5]));
                currX = coords[4];
                currY = coords[5];
                break;

            case PathIterator.SEG_CLOSE:
                ops.addByte(CAIRO_PATH_OP_CLOSE_PATH);
                break;
            }

            pi.next();
        }

        return pi.getWindingRule();
    }

    private static native int[]
         tesselateStrokeNative(int[] pointArray, byte[] ops,
                               int pointCnt, int opCnt,
                               int[] xTrapArray, int xTrapArrayLength,
                               double lineWidth, int lineCap, int lineJoin,
                               double miterLimit, double[] dashArray,
                               int dashCnt, double offset,
                               double m00, double m01, double m02,
                               double m10, double m11, double m12,
                               int clipLowX, int clipLowY,
                               int clipWidth, int clipHeight);

    private static native int[]
        tesselateFillNative(int[] pointArray, byte[] ops, int pointCnt,
                            int opCnt, int[] xTrapArray, int xTrapArrayLength,
                            int windingRule, int clipLowX, int clipLowY,                                    int clipWidth, int clipHeight);

    public void clear() {
        points.clear();
        ops.clear();
        xTrapArray[0] = 0;
    }

    private static int DoubleToCairoFixed(double dbl) {
        return (int) (dbl * 256);
    }

    private static int getCairoWindingRule(int j2dWindingRule) {
        switch(j2dWindingRule) {
        case PathIterator.WIND_EVEN_ODD:
            return CAIRO_FILL_RULE_EVEN_ODD;

        case PathIterator.WIND_NON_ZERO:
            return CAIRO_FILL_RULE_WINDING;

            default:
                throw new IllegalArgumentException("Illegal Java2D winding rule specified");
        }
    }
}
