/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.opengl;

import java.awt.Composite;
import sun.java2d.SunGraphics2D;
import sun.java2d.loops.GraphicsPrimitive;
import sun.java2d.loops.GraphicsPrimitiveMgr;
import sun.java2d.loops.CompositeType;
import sun.java2d.loops.SurfaceType;
import sun.java2d.pipe.BufferedMaskFill;
import static sun.java2d.loops.CompositeType.*;
import static sun.java2d.loops.SurfaceType.*;

class OGLMaskFill extends BufferedMaskFill {

    static void register() {
        GraphicsPrimitive[] primitives = {
            new OGLMaskFill(AnyColor,                  SrcOver),
            new OGLMaskFill(OpaqueColor,               SrcNoEa),
            new OGLMaskFill(GradientPaint,             SrcOver),
            new OGLMaskFill(OpaqueGradientPaint,       SrcNoEa),
            new OGLMaskFill(LinearGradientPaint,       SrcOver),
            new OGLMaskFill(OpaqueLinearGradientPaint, SrcNoEa),
            new OGLMaskFill(RadialGradientPaint,       SrcOver),
            new OGLMaskFill(OpaqueRadialGradientPaint, SrcNoEa),
            new OGLMaskFill(TexturePaint,              SrcOver),
            new OGLMaskFill(OpaqueTexturePaint,        SrcNoEa),
        };
        GraphicsPrimitiveMgr.register(primitives);
    }

    protected OGLMaskFill(SurfaceType srcType, CompositeType compType) {
        super(OGLRenderQueue.getInstance(),
              srcType, compType, OGLSurfaceData.OpenGLSurface);
    }

    @Override
    protected native void maskFill(int x, int y, int w, int h,
                                   int maskoff, int maskscan, int masklen,
                                   byte[] mask);

    @Override
    protected void validateContext(SunGraphics2D sg2d,
                                   Composite comp, int ctxflags)
    {
        OGLSurfaceData dstData = (OGLSurfaceData)sg2d.surfaceData;
        OGLContext.validateContext(dstData, dstData,
                                   sg2d.getCompClip(), comp,
                                   null, sg2d.paint, sg2d, ctxflags);
    }
}
