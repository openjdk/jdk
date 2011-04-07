/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
    private static enum NormMode {OFF, ON_NO_AA, ON_WITH_AA}

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
                 NormMode.OFF,
                 caps,
                 join,
                 miterlimit,
                 dashes,
                 dashphase,
                 new PathConsumer2D() {
                     public void moveTo(float x0, float y0) {
                         p2d.moveTo(x0, y0);
                     }
                     public void lineTo(float x1, float y1) {
                         p2d.lineTo(x1, y1);
                     }
                     public void closePath() {
                         p2d.closePath();
                     }
                     public void pathDone() {}
                     public void curveTo(float x1, float y1,
                                         float x2, float y2,
                                         float x3, float y3) {
                         p2d.curveTo(x1, y1, x2, y2, x3, y3);
                     }
                     public void quadTo(float x1, float y1, float x2, float y2) {
                         p2d.quadTo(x1, y1, x2, y2);
                     }
                     public long getNativeConsumer() {
                         throw new InternalError("Not using a native peer");
                     }
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
        NormMode norm = (normalize) ?
                ((antialias) ? NormMode.ON_WITH_AA : NormMode.ON_NO_AA)
                : NormMode.OFF;
        strokeTo(src, at, bs, thin, norm, antialias, consumer);
    }

    void strokeTo(Shape src,
                  AffineTransform at,
                  BasicStroke bs,
                  boolean thin,
                  NormMode normalize,
                  boolean antialias,
                  PathConsumer2D pc2d)
    {
        float lw;
        if (thin) {
            if (antialias) {
                lw = userSpaceLineWidth(at, 0.5f);
            } else {
                lw = userSpaceLineWidth(at, 1.0f);
            }
        } else {
            lw = bs.getLineWidth();
        }
        strokeTo(src,
                 at,
                 lw,
                 normalize,
                 bs.getEndCap(),
                 bs.getLineJoin(),
                 bs.getMiterLimit(),
                 bs.getDashArray(),
                 bs.getDashPhase(),
                 pc2d);
    }

    private float userSpaceLineWidth(AffineTransform at, float lw) {

        double widthScale;

        if ((at.getType() & (AffineTransform.TYPE_GENERAL_TRANSFORM |
                            AffineTransform.TYPE_GENERAL_SCALE)) != 0) {
            widthScale = Math.sqrt(at.getDeterminant());
        } else {
            /* First calculate the "maximum scale" of this transform. */
            double A = at.getScaleX();       // m00
            double C = at.getShearX();       // m01
            double B = at.getShearY();       // m10
            double D = at.getScaleY();       // m11

            /*
             * Given a 2 x 2 affine matrix [ A B ] such that
             *                             [ C D ]
             * v' = [x' y'] = [Ax + Cy, Bx + Dy], we want to
             * find the maximum magnitude (norm) of the vector v'
             * with the constraint (x^2 + y^2 = 1).
             * The equation to maximize is
             *     |v'| = sqrt((Ax+Cy)^2+(Bx+Dy)^2)
             * or  |v'| = sqrt((AA+BB)x^2 + 2(AC+BD)xy + (CC+DD)y^2).
             * Since sqrt is monotonic we can maximize |v'|^2
             * instead and plug in the substitution y = sqrt(1 - x^2).
             * Trigonometric equalities can then be used to get
             * rid of most of the sqrt terms.
             */

            double EA = A*A + B*B;          // x^2 coefficient
            double EB = 2*(A*C + B*D);      // xy coefficient
            double EC = C*C + D*D;          // y^2 coefficient

            /*
             * There is a lot of calculus omitted here.
             *
             * Conceptually, in the interests of understanding the
             * terms that the calculus produced we can consider
             * that EA and EC end up providing the lengths along
             * the major axes and the hypot term ends up being an
             * adjustment for the additional length along the off-axis
             * angle of rotated or sheared ellipses as well as an
             * adjustment for the fact that the equation below
             * averages the two major axis lengths.  (Notice that
             * the hypot term contains a part which resolves to the
             * difference of these two axis lengths in the absence
             * of rotation.)
             *
             * In the calculus, the ratio of the EB and (EA-EC) terms
             * ends up being the tangent of 2*theta where theta is
             * the angle that the long axis of the ellipse makes
             * with the horizontal axis.  Thus, this equation is
             * calculating the length of the hypotenuse of a triangle
             * along that axis.
             */

            double hypot = Math.sqrt(EB*EB + (EA-EC)*(EA-EC));
            /* sqrt omitted, compare to squared limits below. */
            double widthsquared = ((EA + EC + hypot)/2.0);

            widthScale = Math.sqrt(widthsquared);
        }

        return (float) (lw / widthScale);
    }

    void strokeTo(Shape src,
                  AffineTransform at,
                  float width,
                  NormMode normalize,
                  int caps,
                  int join,
                  float miterlimit,
                  float dashes[],
                  float dashphase,
                  PathConsumer2D pc2d)
    {
        // We use strokerat and outat so that in Stroker and Dasher we can work only
        // with the pre-transformation coordinates. This will repeat a lot of
        // computations done in the path iterator, but the alternative is to
        // work with transformed paths and compute untransformed coordinates
        // as needed. This would be faster but I do not think the complexity
        // of working with both untransformed and transformed coordinates in
        // the same code is worth it.
        // However, if a path's width is constant after a transformation,
        // we can skip all this untransforming.

        // If normalization is off we save some transformations by not
        // transforming the input to pisces. Instead, we apply the
        // transformation after the path processing has been done.
        // We can't do this if normalization is on, because it isn't a good
        // idea to normalize before the transformation is applied.
        AffineTransform strokerat = null;
        AffineTransform outat = null;

        PathIterator pi = null;

        if (at != null && !at.isIdentity()) {
            final double a = at.getScaleX();
            final double b = at.getShearX();
            final double c = at.getShearY();
            final double d = at.getScaleY();
            final double det = a * d - c * b;
            if (Math.abs(det) <= 2 * Float.MIN_VALUE) {
                // this rendering engine takes one dimensional curves and turns
                // them into 2D shapes by giving them width.
                // However, if everything is to be passed through a singular
                // transformation, these 2D shapes will be squashed down to 1D
                // again so, nothing can be drawn.

                // Every path needs an initial moveTo and a pathDone. If these
                // are not there this causes a SIGSEGV in libawt.so (at the time
                // of writing of this comment (September 16, 2010)). Actually,
                // I am not sure if the moveTo is necessary to avoid the SIGSEGV
                // but the pathDone is definitely needed.
                pc2d.moveTo(0, 0);
                pc2d.pathDone();
                return;
            }

            // If the transform is a constant multiple of an orthogonal transformation
            // then every length is just multiplied by a constant, so we just
            // need to transform input paths to stroker and tell stroker
            // the scaled width. This condition is satisfied if
            // a*b == -c*d && a*a+c*c == b*b+d*d. In the actual check below, we
            // leave a bit of room for error.
            if (nearZero(a*b + c*d, 2) && nearZero(a*a+c*c - (b*b+d*d), 2)) {
                double scale = Math.sqrt(a*a + c*c);
                if (dashes != null) {
                    dashes = java.util.Arrays.copyOf(dashes, dashes.length);
                    for (int i = 0; i < dashes.length; i++) {
                        dashes[i] = (float)(scale * dashes[i]);
                    }
                    dashphase = (float)(scale * dashphase);
                }
                width = (float)(scale * width);
                pi = src.getPathIterator(at);
                if (normalize != NormMode.OFF) {
                    pi = new NormalizingPathIterator(pi, normalize);
                }
                // by now strokerat == null && outat == null. Input paths to
                // stroker (and maybe dasher) will have the full transform at
                // applied to them and nothing will happen to the output paths.
            } else {
                if (normalize != NormMode.OFF) {
                    strokerat = at;
                    pi = src.getPathIterator(at);
                    pi = new NormalizingPathIterator(pi, normalize);
                    // by now strokerat == at && outat == null. Input paths to
                    // stroker (and maybe dasher) will have the full transform at
                    // applied to them, then they will be normalized, and then
                    // the inverse of *only the non translation part of at* will
                    // be applied to the normalized paths. This won't cause problems
                    // in stroker, because, suppose at = T*A, where T is just the
                    // translation part of at, and A is the rest. T*A has already
                    // been applied to Stroker/Dasher's input. Then Ainv will be
                    // applied. Ainv*T*A is not equal to T, but it is a translation,
                    // which means that none of stroker's assumptions about its
                    // input will be violated. After all this, A will be applied
                    // to stroker's output.
                } else {
                    outat = at;
                    pi = src.getPathIterator(null);
                    // outat == at && strokerat == null. This is because if no
                    // normalization is done, we can just apply all our
                    // transformations to stroker's output.
                }
            }
        } else {
            // either at is null or it's the identity. In either case
            // we don't transform the path.
            pi = src.getPathIterator(null);
            if (normalize != NormMode.OFF) {
                pi = new NormalizingPathIterator(pi, normalize);
            }
        }

        // by now, at least one of outat and strokerat will be null. Unless at is not
        // a constant multiple of an orthogonal transformation, they will both be
        // null. In other cases, outat == at if normalization is off, and if
        // normalization is on, strokerat == at.
        pc2d = TransformingPathConsumer2D.transformConsumer(pc2d, outat);
        pc2d = TransformingPathConsumer2D.deltaTransformConsumer(pc2d, strokerat);
        pc2d = new Stroker(pc2d, width, caps, join, miterlimit);
        if (dashes != null) {
            pc2d = new Dasher(pc2d, dashes, dashphase);
        }
        pc2d = TransformingPathConsumer2D.inverseDeltaTransformConsumer(pc2d, strokerat);
        pathTo(pi, pc2d);
    }

    private static boolean nearZero(double num, int nulps) {
        return Math.abs(num) < nulps * Math.ulp(num);
    }

    private static class NormalizingPathIterator implements PathIterator {

        private final PathIterator src;

        // the adjustment applied to the current position.
        private float curx_adjust, cury_adjust;
        // the adjustment applied to the last moveTo position.
        private float movx_adjust, movy_adjust;

        // constants used in normalization computations
        private final float lval, rval;

        NormalizingPathIterator(PathIterator src, NormMode mode) {
            this.src = src;
            switch (mode) {
            case ON_NO_AA:
                // round to nearest (0.25, 0.25) pixel
                lval = rval = 0.25f;
                break;
            case ON_WITH_AA:
                // round to nearest pixel center
                lval = 0f;
                rval = 0.5f;
                break;
            case OFF:
                throw new InternalError("A NormalizingPathIterator should " +
                         "not be created if no normalization is being done");
            default:
                throw new InternalError("Unrecognized normalization mode");
            }
        }

        public int currentSegment(float[] coords) {
            int type = src.currentSegment(coords);

            int lastCoord;
            switch(type) {
            case PathIterator.SEG_CUBICTO:
                lastCoord = 4;
                break;
            case PathIterator.SEG_QUADTO:
                lastCoord = 2;
                break;
            case PathIterator.SEG_LINETO:
            case PathIterator.SEG_MOVETO:
                lastCoord = 0;
                break;
            case PathIterator.SEG_CLOSE:
                // we don't want to deal with this case later. We just exit now
                curx_adjust = movx_adjust;
                cury_adjust = movy_adjust;
                return type;
            default:
                throw new InternalError("Unrecognized curve type");
            }

            // normalize endpoint
            float x_adjust = (float)Math.floor(coords[lastCoord] + lval) +
                         rval - coords[lastCoord];
            float y_adjust = (float)Math.floor(coords[lastCoord+1] + lval) +
                         rval - coords[lastCoord + 1];

            coords[lastCoord    ] += x_adjust;
            coords[lastCoord + 1] += y_adjust;

            // now that the end points are done, normalize the control points
            switch(type) {
            case PathIterator.SEG_CUBICTO:
                coords[0] += curx_adjust;
                coords[1] += cury_adjust;
                coords[2] += x_adjust;
                coords[3] += y_adjust;
                break;
            case PathIterator.SEG_QUADTO:
                coords[0] += (curx_adjust + x_adjust) / 2;
                coords[1] += (cury_adjust + y_adjust) / 2;
                break;
            case PathIterator.SEG_LINETO:
                break;
            case PathIterator.SEG_MOVETO:
                movx_adjust = x_adjust;
                movy_adjust = y_adjust;
                break;
            case PathIterator.SEG_CLOSE:
                throw new InternalError("This should be handled earlier.");
            }
            curx_adjust = x_adjust;
            cury_adjust = y_adjust;
            return type;
        }

        public int currentSegment(double[] coords) {
            float[] tmp = new float[6];
            int type = this.currentSegment(tmp);
            for (int i = 0; i < 6; i++) {
                coords[i] = (float) tmp[i];
            }
            return type;
        }

        public int getWindingRule() {
            return src.getWindingRule();
        }

        public boolean isDone() {
            return src.isDone();
        }

        public void next() {
            src.next();
        }
    }

    static void pathTo(PathIterator pi, PathConsumer2D pc2d) {
        RenderingEngine.feedConsumer(pi, pc2d);
        pc2d.pathDone();
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
        Renderer r;
        NormMode norm = (normalize) ? NormMode.ON_WITH_AA : NormMode.OFF;
        if (bs == null) {
            PathIterator pi;
            if (normalize) {
                pi = new NormalizingPathIterator(s.getPathIterator(at), norm);
            } else {
                pi = s.getPathIterator(at);
            }
            r = new Renderer(3, 3,
                             clip.getLoX(), clip.getLoY(),
                             clip.getWidth(), clip.getHeight(),
                             pi.getWindingRule());
            pathTo(pi, r);
        } else {
            r = new Renderer(3, 3,
                             clip.getLoX(), clip.getLoY(),
                             clip.getWidth(), clip.getHeight(),
                             PathIterator.WIND_NON_ZERO);
            strokeTo(s, at, bs, thin, norm, true, r);
        }
        r.endRendering();
        PiscesTileGenerator ptg = new PiscesTileGenerator(r, r.MAX_AA_ALPHA);
        ptg.getBbox(bbox);
        return ptg;
    }

    public AATileGenerator getAATileGenerator(double x, double y,
                                              double dx1, double dy1,
                                              double dx2, double dy2,
                                              double lw1, double lw2,
                                              Region clip,
                                              int bbox[])
    {
        // REMIND: Deal with large coordinates!
        double ldx1, ldy1, ldx2, ldy2;
        boolean innerpgram = (lw1 > 0 && lw2 > 0);

        if (innerpgram) {
            ldx1 = dx1 * lw1;
            ldy1 = dy1 * lw1;
            ldx2 = dx2 * lw2;
            ldy2 = dy2 * lw2;
            x -= (ldx1 + ldx2) / 2.0;
            y -= (ldy1 + ldy2) / 2.0;
            dx1 += ldx1;
            dy1 += ldy1;
            dx2 += ldx2;
            dy2 += ldy2;
            if (lw1 > 1 && lw2 > 1) {
                // Inner parallelogram was entirely consumed by stroke...
                innerpgram = false;
            }
        } else {
            ldx1 = ldy1 = ldx2 = ldy2 = 0;
        }

        Renderer r = new Renderer(3, 3,
                clip.getLoX(), clip.getLoY(),
                clip.getWidth(), clip.getHeight(),
                PathIterator.WIND_EVEN_ODD);

        r.moveTo((float) x, (float) y);
        r.lineTo((float) (x+dx1), (float) (y+dy1));
        r.lineTo((float) (x+dx1+dx2), (float) (y+dy1+dy2));
        r.lineTo((float) (x+dx2), (float) (y+dy2));
        r.closePath();

        if (innerpgram) {
            x += ldx1 + ldx2;
            y += ldy1 + ldy2;
            dx1 -= 2.0 * ldx1;
            dy1 -= 2.0 * ldy1;
            dx2 -= 2.0 * ldx2;
            dy2 -= 2.0 * ldy2;
            r.moveTo((float) x, (float) y);
            r.lineTo((float) (x+dx1), (float) (y+dy1));
            r.lineTo((float) (x+dx1+dx2), (float) (y+dy1+dy2));
            r.lineTo((float) (x+dx2), (float) (y+dy2));
            r.closePath();
        }

        r.pathDone();

        r.endRendering();
        PiscesTileGenerator ptg = new PiscesTileGenerator(r, r.MAX_AA_ALPHA);
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

