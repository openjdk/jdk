/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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

final class TransformingPathConsumer2D {

    TransformingPathConsumer2D() {
        // used by RendererContext
    }

    // recycled PathConsumer2D instance from transformConsumer()
    private final Path2DWrapper        wp_Path2DWrapper        = new Path2DWrapper();

    PathConsumer2D wrapPath2d(Path2D.Float p2d)
    {
        return wp_Path2DWrapper.init(p2d);
    }

    // recycled PathConsumer2D instances from transformConsumer()
    private final TranslateFilter      tx_TranslateFilter      = new TranslateFilter();
    private final DeltaScaleFilter     tx_DeltaScaleFilter     = new DeltaScaleFilter();
    private final ScaleFilter          tx_ScaleFilter          = new ScaleFilter();
    private final DeltaTransformFilter tx_DeltaTransformFilter = new DeltaTransformFilter();
    private final TransformFilter      tx_TransformFilter      = new TransformFilter();

    PathConsumer2D transformConsumer(PathConsumer2D out,
                                     AffineTransform at)
    {
        if (at == null) {
            return out;
        }
        float mxx = (float) at.getScaleX();
        float mxy = (float) at.getShearX();
        float mxt = (float) at.getTranslateX();
        float myx = (float) at.getShearY();
        float myy = (float) at.getScaleY();
        float myt = (float) at.getTranslateY();
        if (mxy == 0f && myx == 0f) {
            if (mxx == 1f && myy == 1f) {
                if (mxt == 0f && myt == 0f) {
                    return out;
                } else {
                    return tx_TranslateFilter.init(out, mxt, myt);
                }
            } else {
                if (mxt == 0f && myt == 0f) {
                    return tx_DeltaScaleFilter.init(out, mxx, myy);
                } else {
                    return tx_ScaleFilter.init(out, mxx, myy, mxt, myt);
                }
            }
        } else if (mxt == 0f && myt == 0f) {
            return tx_DeltaTransformFilter.init(out, mxx, mxy, myx, myy);
        } else {
            return tx_TransformFilter.init(out, mxx, mxy, mxt, myx, myy, myt);
        }
    }

    // recycled PathConsumer2D instances from deltaTransformConsumer()
    private final DeltaScaleFilter     dt_DeltaScaleFilter     = new DeltaScaleFilter();
    private final DeltaTransformFilter dt_DeltaTransformFilter = new DeltaTransformFilter();

    PathConsumer2D deltaTransformConsumer(PathConsumer2D out,
                                          AffineTransform at)
    {
        if (at == null) {
            return out;
        }
        float mxx = (float) at.getScaleX();
        float mxy = (float) at.getShearX();
        float myx = (float) at.getShearY();
        float myy = (float) at.getScaleY();
        if (mxy == 0f && myx == 0f) {
            if (mxx == 1f && myy == 1f) {
                return out;
            } else {
                return dt_DeltaScaleFilter.init(out, mxx, myy);
            }
        } else {
            return dt_DeltaTransformFilter.init(out, mxx, mxy, myx, myy);
        }
    }

    // recycled PathConsumer2D instances from inverseDeltaTransformConsumer()
    private final DeltaScaleFilter     iv_DeltaScaleFilter     = new DeltaScaleFilter();
    private final DeltaTransformFilter iv_DeltaTransformFilter = new DeltaTransformFilter();

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
        if (mxy == 0f && myx == 0f) {
            if (mxx == 1f && myy == 1f) {
                return out;
            } else {
                return iv_DeltaScaleFilter.init(out, 1.0f/mxx, 1.0f/myy);
            }
        } else {
            float det = mxx * myy - mxy * myx;
            return iv_DeltaTransformFilter.init(out,
                                                myy / det,
                                               -mxy / det,
                                               -myx / det,
                                                mxx / det);
        }
    }

    static final class TranslateFilter implements PathConsumer2D {
        private PathConsumer2D out;
        private float tx, ty;

        TranslateFilter() {}

        TranslateFilter init(PathConsumer2D out,
                             float tx, float ty)
        {
            this.out = out;
            this.tx = tx;
            this.ty = ty;
            return this; // fluent API
        }

        @Override
        public void moveTo(float x0, float y0) {
            out.moveTo(x0 + tx, y0 + ty);
        }

        @Override
        public void lineTo(float x1, float y1) {
            out.lineTo(x1 + tx, y1 + ty);
        }

        @Override
        public void quadTo(float x1, float y1,
                           float x2, float y2)
        {
            out.quadTo(x1 + tx, y1 + ty,
                       x2 + tx, y2 + ty);
        }

        @Override
        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
        {
            out.curveTo(x1 + tx, y1 + ty,
                        x2 + tx, y2 + ty,
                        x3 + tx, y3 + ty);
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

    static final class ScaleFilter implements PathConsumer2D {
        private PathConsumer2D out;
        private float sx, sy, tx, ty;

        ScaleFilter() {}

        ScaleFilter init(PathConsumer2D out,
                         float sx, float sy,
                         float tx, float ty)
        {
            this.out = out;
            this.sx = sx;
            this.sy = sy;
            this.tx = tx;
            this.ty = ty;
            return this; // fluent API
        }

        @Override
        public void moveTo(float x0, float y0) {
            out.moveTo(x0 * sx + tx, y0 * sy + ty);
        }

        @Override
        public void lineTo(float x1, float y1) {
            out.lineTo(x1 * sx + tx, y1 * sy + ty);
        }

        @Override
        public void quadTo(float x1, float y1,
                           float x2, float y2)
        {
            out.quadTo(x1 * sx + tx, y1 * sy + ty,
                       x2 * sx + tx, y2 * sy + ty);
        }

        @Override
        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
        {
            out.curveTo(x1 * sx + tx, y1 * sy + ty,
                        x2 * sx + tx, y2 * sy + ty,
                        x3 * sx + tx, y3 * sy + ty);
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

    static final class TransformFilter implements PathConsumer2D {
        private PathConsumer2D out;
        private float mxx, mxy, mxt, myx, myy, myt;

        TransformFilter() {}

        TransformFilter init(PathConsumer2D out,
                             float mxx, float mxy, float mxt,
                             float myx, float myy, float myt)
        {
            this.out = out;
            this.mxx = mxx;
            this.mxy = mxy;
            this.mxt = mxt;
            this.myx = myx;
            this.myy = myy;
            this.myt = myt;
            return this; // fluent API
        }

        @Override
        public void moveTo(float x0, float y0) {
            out.moveTo(x0 * mxx + y0 * mxy + mxt,
                       x0 * myx + y0 * myy + myt);
        }

        @Override
        public void lineTo(float x1, float y1) {
            out.lineTo(x1 * mxx + y1 * mxy + mxt,
                       x1 * myx + y1 * myy + myt);
        }

        @Override
        public void quadTo(float x1, float y1,
                           float x2, float y2)
        {
            out.quadTo(x1 * mxx + y1 * mxy + mxt,
                       x1 * myx + y1 * myy + myt,
                       x2 * mxx + y2 * mxy + mxt,
                       x2 * myx + y2 * myy + myt);
        }

        @Override
        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
        {
            out.curveTo(x1 * mxx + y1 * mxy + mxt,
                        x1 * myx + y1 * myy + myt,
                        x2 * mxx + y2 * mxy + mxt,
                        x2 * myx + y2 * myy + myt,
                        x3 * mxx + y3 * mxy + mxt,
                        x3 * myx + y3 * myy + myt);
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
}
