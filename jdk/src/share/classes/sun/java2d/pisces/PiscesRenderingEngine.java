/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.pisces;

import java.awt.Shape;
import java.awt.BasicStroke;
import java.awt.geom.Path2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;

import sun.awt.geom.PathConsumer2D;
import sun.java2d.pipe.Region;
import sun.java2d.pipe.RenderingEngine;
import sun.java2d.pipe.AATileGenerator;

public class PiscesRenderingEngine extends RenderingEngine {
    public static Transform4 IdentT4 = new Transform4();
    public static double defaultFlat = 0.1;

    static int FloatToS15_16(float flt) {
        flt = flt * 65536f + 0.5f;
        if (flt <= -(65536f * 65536f)) {
            return Integer.MIN_VALUE;
        } else if (flt >= (65536f * 65536f)) {
            return Integer.MAX_VALUE;
        } else {
            return (int) Math.floor(flt);
        }
    }

    static float S15_16ToFloat(int fix) {
        return (fix / 65536f);
    }

    /**
     * Create a widened path as specified by the parameters.
     * <p>
     * The specified {@code src} {@link Shape} is widened according
     * to the specified attribute parameters as per the
     * {@link BasicStroke} specification.
     *
     * @param src the source path to be widened
     * @param width the width of the widened path as per {@code BasicStroke}
     * @param caps the end cap decorations as per {@code BasicStroke}
     * @param join the segment join decorations as per {@code BasicStroke}
     * @param miterlimit the miter limit as per {@code BasicStroke}
     * @param dashes the dash length array as per {@code BasicStroke}
     * @param dashphase the initial dash phase as per {@code BasicStroke}
     * @return the widened path stored in a new {@code Shape} object
     * @since 1.7
     */
    public Shape createStrokedShape(Shape src,
                                    float width,
                                    int caps,
                                    int join,
                                    float miterlimit,
                                    float dashes[],
                                    float dashphase)
    {
        final Path2D p2d = new Path2D.Float();

        strokeTo(src,
                 null,
                 width,
                 caps,
                 join,
                 miterlimit,
                 dashes,
                 dashphase,
                 new LineSink() {
                     public void moveTo(int x0, int y0) {
                         p2d.moveTo(S15_16ToFloat(x0), S15_16ToFloat(y0));
                     }
                     public void lineJoin() {}
                     public void lineTo(int x1, int y1) {
                         p2d.lineTo(S15_16ToFloat(x1), S15_16ToFloat(y1));
                     }
                     public void close() {
                         p2d.closePath();
                     }
                     public void end() {}
                 });

        return p2d;
    }

    /**
     * Sends the geometry for a widened path as specified by the parameters
     * to the specified consumer.
     * <p>
     * The specified {@code src} {@link Shape} is widened according
     * to the parameters specified by the {@link BasicStroke} object.
     * Adjustments are made to the path as appropriate for the
     * {@link VALUE_STROKE_NORMALIZE} hint if the {@code normalize}
     * boolean parameter is true.
     * Adjustments are made to the path as appropriate for the
     * {@link VALUE_ANTIALIAS_ON} hint if the {@code antialias}
     * boolean parameter is true.
     * <p>
     * The geometry of the widened path is forwarded to the indicated
     * {@link PathConsumer2D} object as it is calculated.
     *
     * @param src the source path to be widened
     * @param bs the {@code BasicSroke} object specifying the
     *           decorations to be applied to the widened path
     * @param normalize indicates whether stroke normalization should
     *                  be applied
     * @param antialias indicates whether or not adjustments appropriate
     *                  to antialiased rendering should be applied
     * @param consumer the {@code PathConsumer2D} instance to forward
     *                 the widened geometry to
     * @since 1.7
     */
    public void strokeTo(Shape src,
                         AffineTransform at,
                         BasicStroke bs,
                         boolean thin,
                         boolean normalize,
                         boolean antialias,
                         final PathConsumer2D consumer)
    {
        strokeTo(src, at, bs, thin, normalize, antialias,
                 new LineSink() {
                     public void moveTo(int x0, int y0) {
                         consumer.moveTo(S15_16ToFloat(x0), S15_16ToFloat(y0));
                     }
                     public void lineJoin() {}
                     public void lineTo(int x1, int y1) {
                         consumer.lineTo(S15_16ToFloat(x1), S15_16ToFloat(y1));
                     }
                     public void close() {
                         consumer.closePath();
                     }
                     public void end() {
                         consumer.pathDone();
                     }
                 });
    }

    void strokeTo(Shape src,
                  AffineTransform at,
                  BasicStroke bs,
                  boolean thin,
                  boolean normalize,
                  boolean antialias,
                  LineSink lsink)
    {
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
        strokeTo(src,
                 at,
                 lw,
                 bs.getEndCap(),
                 bs.getLineJoin(),
                 bs.getMiterLimit(),
                 bs.getDashArray(),
                 bs.getDashPhase(),
                 lsink);
    }

    void strokeTo(Shape src,
                  AffineTransform at,
                  float width,
                  int caps,
                  int join,
                  float miterlimit,
                  float dashes[],
                  float dashphase,
                  LineSink lsink)
    {
        Transform4 t4;

        if (at == null || at.isIdentity()) {
            t4 = IdentT4;
        } else {
            t4 = new Transform4(FloatToS15_16((float) at.getScaleX()),
                                FloatToS15_16((float) at.getShearX()),
                                FloatToS15_16((float) at.getShearY()),
                                FloatToS15_16((float) at.getScaleY()));
        }

        lsink = new Stroker(lsink,
                            FloatToS15_16(width),
                            caps,
                            join,
                            FloatToS15_16(miterlimit),
                            t4);
        if (dashes != null) {
            int fdashes[] = new int[dashes.length];
            for (int i = 0; i < dashes.length; i++) {
                fdashes[i] = FloatToS15_16(dashes[i]);
            }
            lsink = new Dasher(lsink,
                               fdashes,
                               FloatToS15_16(dashphase),
                               t4);
        }

        PathIterator pi = src.getPathIterator(at, defaultFlat);
        pathTo(pi, lsink);
    }

    void pathTo(PathIterator pi, LineSink lsink) {
        float coords[] = new float[2];
        while (!pi.isDone()) {
            switch (pi.currentSegment(coords)) {
            case PathIterator.SEG_MOVETO:
                lsink.moveTo(FloatToS15_16(coords[0]),
                             FloatToS15_16(coords[1]));
                break;
            case PathIterator.SEG_LINETO:
                lsink.lineJoin();
                lsink.lineTo(FloatToS15_16(coords[0]),
                             FloatToS15_16(coords[1]));
                break;
            case PathIterator.SEG_CLOSE:
                lsink.lineJoin();
                lsink.close();
                break;
            default:
                throw new InternalError("unknown flattened segment type");
            }
            pi.next();
        }
        lsink.end();
    }

    /**
     * Construct an antialiased tile generator for the given shape with
     * the given rendering attributes and store the bounds of the tile
     * iteration in the bbox parameter.
     * The {@code at} parameter specifies a transform that should affect
     * both the shape and the {@code BasicStroke} attributes.
     * The {@code clip} parameter specifies the current clip in effect
     * in device coordinates and can be used to prune the data for the
     * operation, but the renderer is not required to perform any
     * clipping.
     * If the {@code BasicStroke} parameter is null then the shape
     * should be filled as is, otherwise the attributes of the
     * {@code BasicStroke} should be used to specify a draw operation.
     * The {@code thin} parameter indicates whether or not the
     * transformed {@code BasicStroke} represents coordinates smaller
     * than the minimum resolution of the antialiasing rasterizer as
     * specified by the {@code getMinimumAAPenWidth()} method.
     * <p>
     * Upon returning, this method will fill the {@code bbox} parameter
     * with 4 values indicating the bounds of the iteration of the
     * tile generator.
     * The iteration order of the tiles will be as specified by the
     * pseudo-code:
     * <pre>
     *     for (y = bbox[1]; y < bbox[3]; y += tileheight) {
     *         for (x = bbox[0]; x < bbox[2]; x += tilewidth) {
     *         }
     *     }
     * </pre>
     * If there is no output to be rendered, this method may return
     * null.
     *
     * @param s the shape to be rendered (fill or draw)
     * @param at the transform to be applied to the shape and the
     *           stroke attributes
     * @param clip the current clip in effect in device coordinates
     * @param bs if non-null, a {@code BasicStroke} whose attributes
     *           should be applied to this operation
     * @param thin true if the transformed stroke attributes are smaller
     *             than the minimum dropout pen width
     * @param normalize true if the {@code VALUE_STROKE_NORMALIZE}
     *                  {@code RenderingHint} is in effect
     * @param bbox returns the bounds of the iteration
     * @return the {@code AATileGenerator} instance to be consulted
     *         for tile coverages, or null if there is no output to render
     * @since 1.7
     */
    public AATileGenerator getAATileGenerator(Shape s,
                                              AffineTransform at,
                                              Region clip,
                                              BasicStroke bs,
                                              boolean thin,
                                              boolean normalize,
                                              int bbox[])
    {
        PiscesCache pc = PiscesCache.createInstance();
        Renderer r = new Renderer();
        r.setCache(pc);
        r.setAntialiasing(3, 3);
        r.beginRendering(clip.getLoX(), clip.getLoY(),
                         clip.getWidth(), clip.getHeight());
        if (bs == null) {
            PathIterator pi = s.getPathIterator(at, defaultFlat);
            r.setWindingRule(pi.getWindingRule());
            pathTo(pi, r);
        } else {
            r.setWindingRule(PathIterator.WIND_NON_ZERO);
            strokeTo(s, at, bs, thin, normalize, true, r);
        }
        r.endRendering();
        PiscesTileGenerator ptg = new PiscesTileGenerator(pc, r.MAX_AA_ALPHA);
        ptg.getBbox(bbox);
        return ptg;
    }

    /**
     * Returns the minimum pen width that the antialiasing rasterizer
     * can represent without dropouts occuring.
     * @since 1.7
     */
    public float getMinimumAAPenSize() {
        return 0.5f;
    }

    static {
        if (PathIterator.WIND_NON_ZERO != Renderer.WIND_NON_ZERO ||
            PathIterator.WIND_EVEN_ODD != Renderer.WIND_EVEN_ODD ||
            BasicStroke.JOIN_MITER != Stroker.JOIN_MITER ||
            BasicStroke.JOIN_ROUND != Stroker.JOIN_ROUND ||
            BasicStroke.JOIN_BEVEL != Stroker.JOIN_BEVEL ||
            BasicStroke.CAP_BUTT != Stroker.CAP_BUTT ||
            BasicStroke.CAP_ROUND != Stroker.CAP_ROUND ||
            BasicStroke.CAP_SQUARE != Stroker.CAP_SQUARE)
        {
            throw new InternalError("mismatched renderer constants");
        }
    }
}
