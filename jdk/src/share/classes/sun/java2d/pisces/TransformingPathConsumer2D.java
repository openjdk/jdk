/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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

import sun.awt.geom.PathConsumer2D;
import java.awt.geom.AffineTransform;

public class TransformingPathConsumer2D {
    public static PathConsumer2D
        transformConsumer(PathConsumer2D out,
                          AffineTransform at)
    {
        if (at == null) {
            return out;
        }
        float Mxx = (float) at.getScaleX();
        float Mxy = (float) at.getShearX();
        float Mxt = (float) at.getTranslateX();
        float Myx = (float) at.getShearY();
        float Myy = (float) at.getScaleY();
        float Myt = (float) at.getTranslateY();
        if (Mxy == 0f && Myx == 0f) {
            if (Mxx == 1f && Myy == 1f) {
                if (Mxt == 0f && Myt == 0f) {
                    return out;
                } else {
                    return new TranslateFilter(out, Mxt, Myt);
                }
            } else {
                return new ScaleFilter(out, Mxx, Myy, Mxt, Myt);
            }
        } else {
            return new TransformFilter(out, Mxx, Mxy, Mxt, Myx, Myy, Myt);
        }
    }

    static class TranslateFilter implements PathConsumer2D {
        PathConsumer2D out;
        float tx;
        float ty;

        TranslateFilter(PathConsumer2D out,
                        float tx, float ty)
        {
            this.out = out;
            this.tx = tx;
            this.ty = ty;
        }

        public void moveTo(float x0, float y0) {
            out.moveTo(x0 + tx, y0 + ty);
        }

        public void lineTo(float x1, float y1) {
            out.lineTo(x1 + tx, y1 + ty);
        }

        public void quadTo(float x1, float y1,
                           float x2, float y2)
        {
            out.quadTo(x1 + tx, y1 + ty,
                       x2 + tx, y2 + ty);
        }

        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
        {
            out.curveTo(x1 + tx, y1 + ty,
                        x2 + tx, y2 + ty,
                        x3 + tx, y3 + ty);
        }

        public void closePath() {
            out.closePath();
        }

        public void pathDone() {
            out.pathDone();
        }

        public long getNativeConsumer() {
            return 0;
        }
    }

    static class ScaleFilter implements PathConsumer2D {
        PathConsumer2D out;
        float sx;
        float sy;
        float tx;
        float ty;

        ScaleFilter(PathConsumer2D out,
                    float sx, float sy, float tx, float ty)
        {
            this.out = out;
            this.sx = sx;
            this.sy = sy;
            this.tx = tx;
            this.ty = ty;
        }

        public void moveTo(float x0, float y0) {
            out.moveTo(x0 * sx + tx, y0 * sy + ty);
        }

        public void lineTo(float x1, float y1) {
            out.lineTo(x1 * sx + tx, y1 * sy + ty);
        }

        public void quadTo(float x1, float y1,
                           float x2, float y2)
        {
            out.quadTo(x1 * sx + tx, y1 * sy + ty,
                       x2 * sx + tx, y2 * sy + ty);
        }

        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
        {
            out.curveTo(x1 * sx + tx, y1 * sy + ty,
                        x2 * sx + tx, y2 * sy + ty,
                        x3 * sx + tx, y3 * sy + ty);
        }

        public void closePath() {
            out.closePath();
        }

        public void pathDone() {
            out.pathDone();
        }

        public long getNativeConsumer() {
            return 0;
        }
    }

    static class TransformFilter implements PathConsumer2D {
        PathConsumer2D out;
        float Mxx;
        float Mxy;
        float Mxt;
        float Myx;
        float Myy;
        float Myt;

        TransformFilter(PathConsumer2D out,
                        float Mxx, float Mxy, float Mxt,
                        float Myx, float Myy, float Myt)
        {
            this.out = out;
            this.Mxx = Mxx;
            this.Mxy = Mxy;
            this.Mxt = Mxt;
            this.Myx = Myx;
            this.Myy = Myy;
            this.Myt = Myt;
        }

        public void moveTo(float x0, float y0) {
            out.moveTo(x0 * Mxx + y0 * Mxy + Mxt,
                       x0 * Myx + y0 * Myy + Myt);
        }

        public void lineTo(float x1, float y1) {
            out.lineTo(x1 * Mxx + y1 * Mxy + Mxt,
                       x1 * Myx + y1 * Myy + Myt);
        }

        public void quadTo(float x1, float y1,
                           float x2, float y2)
        {
            out.quadTo(x1 * Mxx + y1 * Mxy + Mxt,
                       x1 * Myx + y1 * Myy + Myt,
                       x2 * Mxx + y2 * Mxy + Mxt,
                       x2 * Myx + y2 * Myy + Myt);
        }

        public void curveTo(float x1, float y1,
                            float x2, float y2,
                            float x3, float y3)
        {
            out.curveTo(x1 * Mxx + y1 * Mxy + Mxt,
                        x1 * Myx + y1 * Myy + Myt,
                        x2 * Mxx + y2 * Mxy + Mxt,
                        x2 * Myx + y2 * Myy + Myt,
                        x3 * Mxx + y3 * Mxy + Mxt,
                        x3 * Myx + y3 * Myy + Myt);
        }

        public void closePath() {
            out.closePath();
        }

        public void pathDone() {
            out.pathDone();
        }

        public long getNativeConsumer() {
            return 0;
        }
    }
}
