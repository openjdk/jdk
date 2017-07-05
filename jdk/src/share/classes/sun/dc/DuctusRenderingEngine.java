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

package sun.dc;

import java.awt.Shape;
import java.awt.BasicStroke;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.AffineTransform;

import sun.awt.geom.PathConsumer2D;
import sun.java2d.pipe.Region;
import sun.java2d.pipe.AATileGenerator;
import sun.java2d.pipe.RenderingEngine;

import sun.dc.pr.Rasterizer;
import sun.dc.pr.PathStroker;
import sun.dc.pr.PathDasher;
import sun.dc.pr.PRException;
import sun.dc.path.PathConsumer;
import sun.dc.path.PathException;
import sun.dc.path.FastPathProducer;

public class DuctusRenderingEngine extends RenderingEngine {
    static final float PenUnits = 0.01f;
    static final int MinPenUnits = 100;
    static final int MinPenUnitsAA = 20;
    static final float MinPenSizeAA = PenUnits * MinPenUnitsAA;

    static final float UPPER_BND = Float.MAX_VALUE / 2.0f;
    static final float LOWER_BND = -UPPER_BND;

    private static final int RasterizerCaps[] = {
        Rasterizer.BUTT, Rasterizer.ROUND, Rasterizer.SQUARE
    };

    private static final int RasterizerCorners[] = {
        Rasterizer.MITER, Rasterizer.ROUND, Rasterizer.BEVEL
    };

    static float[] getTransformMatrix(AffineTransform transform) {
        float matrix[] = new float[4];
        double dmatrix[] = new double[6];
        transform.getMatrix(dmatrix);
        for (int i = 0; i < 4; i++) {
            matrix[i] = (float) dmatrix[i];
        }
        return matrix;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Shape createStrokedShape(Shape src,
                                    float width,
                                    int caps,
                                    int join,
                                    float miterlimit,
                                    float dashes[],
                                    float dashphase)
    {
        FillAdapter filler = new FillAdapter();
        PathStroker stroker = new PathStroker(filler);
        PathDasher dasher = null;

        try {
            PathConsumer consumer;

            stroker.setPenDiameter(width);
            stroker.setPenT4(null);
            stroker.setCaps(RasterizerCaps[caps]);
            stroker.setCorners(RasterizerCorners[join], miterlimit);
            if (dashes != null) {
                dasher = new PathDasher(stroker);
                dasher.setDash(dashes, dashphase);
                dasher.setDashT4(null);
                consumer = dasher;
            } else {
                consumer = stroker;
            }

            feedConsumer(consumer, src.getPathIterator(null));
        } finally {
            stroker.dispose();
            if (dasher != null) {
                dasher.dispose();
            }
        }

        return filler.getShape();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void strokeTo(Shape src,
                         AffineTransform transform,
                         BasicStroke bs,
                         boolean thin,
                         boolean normalize,
                         boolean antialias,
                         PathConsumer2D sr)
    {
        PathStroker stroker = new PathStroker(sr);
        PathConsumer consumer = stroker;

        float matrix[] = null;
        if (!thin) {
            stroker.setPenDiameter(bs.getLineWidth());
            if (transform != null) {
                matrix = getTransformMatrix(transform);
            }
            stroker.setPenT4(matrix);
            stroker.setPenFitting(PenUnits, MinPenUnits);
        }
        stroker.setCaps(RasterizerCaps[bs.getEndCap()]);
        stroker.setCorners(RasterizerCorners[bs.getLineJoin()],
                           bs.getMiterLimit());
        float[] dashes = bs.getDashArray();
        if (dashes != null) {
            PathDasher dasher = new PathDasher(stroker);
            dasher.setDash(dashes, bs.getDashPhase());
            if (transform != null && matrix == null) {
                matrix = getTransformMatrix(transform);
            }
            dasher.setDashT4(matrix);
            consumer = dasher;
        }

        try {
            PathIterator pi = src.getPathIterator(transform);

            feedConsumer(pi, consumer, normalize, 0.25f);
        } catch (PathException e) {
            throw new InternalError("Unable to Stroke shape ("+
                                    e.getMessage()+")");
        } finally {
            while (consumer != null && consumer != sr) {
                PathConsumer next = consumer.getConsumer();
                consumer.dispose();
                consumer = next;
            }
        }
    }

    /*
     * Feed a path from a PathIterator to a Ductus PathConsumer.
     */
    public static void feedConsumer(PathIterator pi, PathConsumer consumer,
                                    boolean normalize, float norm)
        throws PathException
    {
        consumer.beginPath();
        boolean pathClosed = false;
        boolean skip = false;
        boolean subpathStarted = false;
        float mx = 0.0f;
        float my = 0.0f;
        float point[]  = new float[6];
        float rnd = (0.5f - norm);
        float ax = 0.0f;
        float ay = 0.0f;

        while (!pi.isDone()) {
            int type = pi.currentSegment(point);
            if (pathClosed == true) {
                pathClosed = false;
                if (type != PathIterator.SEG_MOVETO) {
                    // Force current point back to last moveto point
                    consumer.beginSubpath(mx, my);
                    subpathStarted = true;
                }
            }
            if (normalize) {
                int index;
                switch (type) {
                case PathIterator.SEG_CUBICTO:
                    index = 4;
                    break;
                case PathIterator.SEG_QUADTO:
                    index = 2;
                    break;
                case PathIterator.SEG_MOVETO:
                case PathIterator.SEG_LINETO:
                    index = 0;
                    break;
                case PathIterator.SEG_CLOSE:
                default:
                    index = -1;
                    break;
                }
                if (index >= 0) {
                    float ox = point[index];
                    float oy = point[index+1];
                    float newax = (float) Math.floor(ox + rnd) + norm;
                    float neway = (float) Math.floor(oy + rnd) + norm;
                    point[index] = newax;
                    point[index+1] = neway;
                    newax -= ox;
                    neway -= oy;
                    switch (type) {
                    case PathIterator.SEG_CUBICTO:
                        point[0] += ax;
                        point[1] += ay;
                        point[2] += newax;
                        point[3] += neway;
                        break;
                    case PathIterator.SEG_QUADTO:
                        point[0] += (newax + ax) / 2;
                        point[1] += (neway + ay) / 2;
                        break;
                    case PathIterator.SEG_MOVETO:
                    case PathIterator.SEG_LINETO:
                    case PathIterator.SEG_CLOSE:
                        break;
                    }
                    ax = newax;
                    ay = neway;
                }
            }
            switch (type) {
            case PathIterator.SEG_MOVETO:

                /* Checking SEG_MOVETO coordinates if they are out of the
                 * [LOWER_BND, UPPER_BND] range. This check also handles NaN
                 * and Infinity values. Skipping next path segment in case of
                 * invalid data.
                 */
                if (point[0] < UPPER_BND && point[0] > LOWER_BND &&
                    point[1] < UPPER_BND && point[1] > LOWER_BND)
                {
                    mx = point[0];
                    my = point[1];
                    consumer.beginSubpath(mx, my);
                    subpathStarted = true;
                    skip = false;
                } else {
                    skip = true;
                }
                break;
            case PathIterator.SEG_LINETO:
                /* Checking SEG_LINETO coordinates if they are out of the
                 * [LOWER_BND, UPPER_BND] range. This check also handles NaN
                 * and Infinity values. Ignoring current path segment in case
                 * of invalid data. If segment is skipped its endpoint
                 * (if valid) is used to begin new subpath.
                 */
                if (point[0] < UPPER_BND && point[0] > LOWER_BND &&
                    point[1] < UPPER_BND && point[1] > LOWER_BND)
                {
                    if (skip) {
                        consumer.beginSubpath(point[0], point[1]);
                        subpathStarted = true;
                        skip = false;
                    } else {
                        consumer.appendLine(point[0], point[1]);
                    }
                }
                break;
            case PathIterator.SEG_QUADTO:
                // Quadratic curves take two points

                /* Checking SEG_QUADTO coordinates if they are out of the
                 * [LOWER_BND, UPPER_BND] range. This check also handles NaN
                 * and Infinity values. Ignoring current path segment in case
                 * of invalid endpoints's data. Equivalent to the SEG_LINETO
                 * if endpoint coordinates are valid but there are invalid data
                 * amoung other coordinates
                 */
                if (point[2] < UPPER_BND && point[2] > LOWER_BND &&
                    point[3] < UPPER_BND && point[3] > LOWER_BND)
                {
                    if (skip) {
                        consumer.beginSubpath(point[2], point[3]);
                        subpathStarted = true;
                        skip = false;
                    } else {
                        if (point[0] < UPPER_BND && point[0] > LOWER_BND &&
                            point[1] < UPPER_BND && point[1] > LOWER_BND)
                        {
                            consumer.appendQuadratic(point[0], point[1],
                                                     point[2], point[3]);
                        } else {
                            consumer.appendLine(point[2], point[3]);
                        }
                    }
                }
                break;
            case PathIterator.SEG_CUBICTO:
                // Cubic curves take three points

                /* Checking SEG_CUBICTO coordinates if they are out of the
                 * [LOWER_BND, UPPER_BND] range. This check also handles NaN
                 * and Infinity values. Ignoring current path segment in case
                 * of invalid endpoints's data. Equivalent to the SEG_LINETO
                 * if endpoint coordinates are valid but there are invalid data
                 * amoung other coordinates
                 */
                if (point[4] < UPPER_BND && point[4] > LOWER_BND &&
                    point[5] < UPPER_BND && point[5] > LOWER_BND)
                {
                    if (skip) {
                        consumer.beginSubpath(point[4], point[5]);
                        subpathStarted = true;
                        skip = false;
                    } else {
                        if (point[0] < UPPER_BND && point[0] > LOWER_BND &&
                            point[1] < UPPER_BND && point[1] > LOWER_BND &&
                            point[2] < UPPER_BND && point[2] > LOWER_BND &&
                            point[3] < UPPER_BND && point[3] > LOWER_BND)
                        {
                            consumer.appendCubic(point[0], point[1],
                                                 point[2], point[3],
                                                 point[4], point[5]);
                        } else {
                            consumer.appendLine(point[4], point[5]);
                        }
                    }
                }
                break;
            case PathIterator.SEG_CLOSE:
                if (subpathStarted) {
                    consumer.closedSubpath();
                    subpathStarted = false;
                    pathClosed = true;
                }
                break;
            }
            pi.next();
        }

        consumer.endPath();
    }

    private static Rasterizer theRasterizer;

    public synchronized static Rasterizer getRasterizer() {
        Rasterizer r = theRasterizer;
        if (r == null) {
            r = new Rasterizer();
        } else {
            theRasterizer = null;
        }
        return r;
    }

    public synchronized static void dropRasterizer(Rasterizer r) {
        r.reset();
        theRasterizer = r;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getMinimumAAPenSize() {
        return MinPenSizeAA;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AATileGenerator getAATileGenerator(Shape s,
                                              AffineTransform at,
                                              Region clip,
                                              BasicStroke bs,
                                              boolean thin,
                                              boolean normalize,
                                              int bbox[])
    {
        Rasterizer r = getRasterizer();
        PathIterator pi = s.getPathIterator(at);

        if (bs != null) {
            float matrix[] = null;
            r.setUsage(Rasterizer.STROKE);
            if (thin) {
                r.setPenDiameter(MinPenSizeAA);
            } else {
                r.setPenDiameter(bs.getLineWidth());
                if (at != null) {
                    matrix = getTransformMatrix(at);
                    r.setPenT4(matrix);
                }
                r.setPenFitting(PenUnits, MinPenUnitsAA);
            }
            r.setCaps(RasterizerCaps[bs.getEndCap()]);
            r.setCorners(RasterizerCorners[bs.getLineJoin()],
                         bs.getMiterLimit());
            float[] dashes = bs.getDashArray();
            if (dashes != null) {
                r.setDash(dashes, bs.getDashPhase());
                if (at != null && matrix == null) {
                    matrix = getTransformMatrix(at);
                }
                r.setDashT4(matrix);
            }
        } else {
            r.setUsage(pi.getWindingRule() == PathIterator.WIND_EVEN_ODD
                       ? Rasterizer.EOFILL
                       : Rasterizer.NZFILL);
        }

        r.beginPath();
        {
            boolean pathClosed = false;
            boolean skip = false;
            boolean subpathStarted = false;
            float mx = 0.0f;
            float my = 0.0f;
            float point[]  = new float[6];
            float ax = 0.0f;
            float ay = 0.0f;

            while (!pi.isDone()) {
                int type = pi.currentSegment(point);
                if (pathClosed == true) {
                    pathClosed = false;
                    if (type != PathIterator.SEG_MOVETO) {
                        // Force current point back to last moveto point
                        r.beginSubpath(mx, my);
                        subpathStarted = true;
                    }
                }
                if (normalize) {
                    int index;
                    switch (type) {
                    case PathIterator.SEG_CUBICTO:
                        index = 4;
                        break;
                    case PathIterator.SEG_QUADTO:
                        index = 2;
                        break;
                    case PathIterator.SEG_MOVETO:
                    case PathIterator.SEG_LINETO:
                        index = 0;
                        break;
                    case PathIterator.SEG_CLOSE:
                    default:
                        index = -1;
                        break;
                    }
                    if (index >= 0) {
                        float ox = point[index];
                        float oy = point[index+1];
                        float newax = (float) Math.floor(ox) + 0.5f;
                        float neway = (float) Math.floor(oy) + 0.5f;
                        point[index] = newax;
                        point[index+1] = neway;
                        newax -= ox;
                        neway -= oy;
                        switch (type) {
                        case PathIterator.SEG_CUBICTO:
                            point[0] += ax;
                            point[1] += ay;
                            point[2] += newax;
                            point[3] += neway;
                            break;
                        case PathIterator.SEG_QUADTO:
                            point[0] += (newax + ax) / 2;
                            point[1] += (neway + ay) / 2;
                            break;
                        case PathIterator.SEG_MOVETO:
                        case PathIterator.SEG_LINETO:
                        case PathIterator.SEG_CLOSE:
                            break;
                        }
                        ax = newax;
                        ay = neway;
                    }
                }
                switch (type) {
                case PathIterator.SEG_MOVETO:

                   /* Checking SEG_MOVETO coordinates if they are out of the
                    * [LOWER_BND, UPPER_BND] range. This check also handles NaN
                    * and Infinity values. Skipping next path segment in case
                    * of invalid data.
                    */

                    if (point[0] < UPPER_BND &&  point[0] > LOWER_BND &&
                        point[1] < UPPER_BND &&  point[1] > LOWER_BND)
                    {
                        mx = point[0];
                        my = point[1];
                        r.beginSubpath(mx, my);
                        subpathStarted = true;
                        skip = false;
                    } else {
                        skip = true;
                    }
                    break;

                case PathIterator.SEG_LINETO:
                    /* Checking SEG_LINETO coordinates if they are out of the
                     * [LOWER_BND, UPPER_BND] range. This check also handles
                     * NaN and Infinity values. Ignoring current path segment
                     * in case of invalid data. If segment is skipped its
                     * endpoint (if valid) is used to begin new subpath.
                     */
                    if (point[0] < UPPER_BND && point[0] > LOWER_BND &&
                        point[1] < UPPER_BND && point[1] > LOWER_BND)
                    {
                        if (skip) {
                            r.beginSubpath(point[0], point[1]);
                            subpathStarted = true;
                            skip = false;
                        } else {
                            r.appendLine(point[0], point[1]);
                        }
                    }
                    break;

                case PathIterator.SEG_QUADTO:
                    // Quadratic curves take two points

                    /* Checking SEG_QUADTO coordinates if they are out of the
                     * [LOWER_BND, UPPER_BND] range. This check also handles
                     * NaN and Infinity values. Ignoring current path segment
                     * in case of invalid endpoints's data. Equivalent to the
                     * SEG_LINETO if endpoint coordinates are valid but there
                     * are invalid data amoung other coordinates
                     */
                    if (point[2] < UPPER_BND && point[2] > LOWER_BND &&
                        point[3] < UPPER_BND && point[3] > LOWER_BND)
                    {
                        if (skip) {
                            r.beginSubpath(point[2], point[3]);
                            subpathStarted = true;
                            skip = false;
                        } else {
                            if (point[0] < UPPER_BND && point[0] > LOWER_BND &&
                                point[1] < UPPER_BND && point[1] > LOWER_BND)
                            {
                                r.appendQuadratic(point[0], point[1],
                                                  point[2], point[3]);
                            } else {
                                r.appendLine(point[2], point[3]);
                            }
                        }
                    }
                    break;
                case PathIterator.SEG_CUBICTO:
                    // Cubic curves take three points

                    /* Checking SEG_CUBICTO coordinates if they are out of the
                     * [LOWER_BND, UPPER_BND] range. This check also handles
                     * NaN and Infinity values. Ignoring  current path segment
                     * in case of invalid endpoints's data. Equivalent to the
                     * SEG_LINETO if endpoint coordinates are valid but there
                     * are invalid data amoung other coordinates
                     */

                    if (point[4] < UPPER_BND && point[4] > LOWER_BND &&
                        point[5] < UPPER_BND && point[5] > LOWER_BND)
                    {
                        if (skip) {
                            r.beginSubpath(point[4], point[5]);
                            subpathStarted = true;
                            skip = false;
                        } else {
                            if (point[0] < UPPER_BND && point[0] > LOWER_BND &&
                                point[1] < UPPER_BND && point[1] > LOWER_BND &&
                                point[2] < UPPER_BND && point[2] > LOWER_BND &&
                                point[3] < UPPER_BND && point[3] > LOWER_BND)
                            {
                                r.appendCubic(point[0], point[1],
                                              point[2], point[3],
                                              point[4], point[5]);
                            } else {
                                r.appendLine(point[4], point[5]);
                            }
                        }
                    }
                    break;
                case PathIterator.SEG_CLOSE:
                    if (subpathStarted) {
                        r.closedSubpath();
                        subpathStarted = false;
                        pathClosed = true;
                    }
                    break;
                }
                pi.next();
            }
        }

        try {
            r.endPath();
            r.getAlphaBox(bbox);
            clip.clipBoxToBounds(bbox);
            if (bbox[0] >= bbox[2] || bbox[1] >= bbox[3]) {
                dropRasterizer(r);
                return null;
            }
            r.setOutputArea(bbox[0], bbox[1],
                            bbox[2] - bbox[0],
                            bbox[3] - bbox[1]);
        } catch (PRException e) {
            /*
             * This exeption is thrown from the native part of the Ductus
             * (only in case of a debug build) to indicate that some
             * segments of the path have very large coordinates.
             * See 4485298 for more info.
             */
            System.err.println("DuctusRenderingEngine.getAATileGenerator: "+e);
        }

        return r;
    }

    private void feedConsumer(PathConsumer consumer, PathIterator pi) {
        try {
            consumer.beginPath();
            boolean pathClosed = false;
            float mx = 0.0f;
            float my = 0.0f;
            float point[]  = new float[6];

            while (!pi.isDone()) {
                int type = pi.currentSegment(point);
                if (pathClosed == true) {
                    pathClosed = false;
                    if (type != PathIterator.SEG_MOVETO) {
                        // Force current point back to last moveto point
                        consumer.beginSubpath(mx, my);
                    }
                }
                switch (type) {
                case PathIterator.SEG_MOVETO:
                    mx = point[0];
                    my = point[1];
                    consumer.beginSubpath(point[0], point[1]);
                    break;
                case PathIterator.SEG_LINETO:
                    consumer.appendLine(point[0], point[1]);
                    break;
                case PathIterator.SEG_QUADTO:
                    consumer.appendQuadratic(point[0], point[1],
                                             point[2], point[3]);
                    break;
                case PathIterator.SEG_CUBICTO:
                    consumer.appendCubic(point[0], point[1],
                                         point[2], point[3],
                                         point[4], point[5]);
                    break;
                case PathIterator.SEG_CLOSE:
                    consumer.closedSubpath();
                    pathClosed = true;
                    break;
                }
                pi.next();
            }

            consumer.endPath();
        } catch (PathException e) {
            throw new InternalError("Unable to Stroke shape ("+
                                    e.getMessage()+")");
        }
    }

    private class FillAdapter implements PathConsumer {
        boolean closed;
        Path2D.Float path;

        public FillAdapter() {
            // Ductus only supplies float coordinates so
            // Path2D.Double is not necessary here.
            path = new Path2D.Float(Path2D.WIND_NON_ZERO);
        }

        public Shape getShape() {
            return path;
        }

        public void dispose() {
        }

        public PathConsumer getConsumer() {
            return null;
        }

        public void beginPath() {}

        public void beginSubpath(float x0, float y0) {
            if (closed) {
                path.closePath();
                closed = false;
            }
            path.moveTo(x0, y0);
        }

        public void appendLine(float x1, float y1) {
            path.lineTo(x1, y1);
        }

        public void appendQuadratic(float xm, float ym, float x1, float y1) {
            path.quadTo(xm, ym, x1, y1);
        }

        public void appendCubic(float xm, float ym,
                                float xn, float yn,
                                float x1, float y1) {
            path.curveTo(xm, ym, xn, yn, x1, y1);
        }

        public void closedSubpath() {
            closed = true;
        }

        public void endPath() {
            if (closed) {
                path.closePath();
                closed = false;
            }
        }

        public void useProxy(FastPathProducer proxy)
            throws PathException
        {
            proxy.sendTo(this);
        }

        public long getCPathConsumer() {
            return 0;
        }
    }
}
