/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.d3d;

import java.awt.AlphaComposite;
import java.awt.Composite;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.loops.GraphicsPrimitive;
import sun.java2d.loops.GraphicsPrimitiveMgr;
import sun.java2d.loops.CompositeType;
import sun.java2d.loops.SurfaceType;
import sun.java2d.loops.MaskFill;
import static sun.java2d.d3d.D3DSurfaceData.*;

/**
 * The MaskFill operation is expressed as:
 *   dst = ((src <MODE> dst) * pathA) + (dst * (1 - pathA))
 *
 * The D3D implementation of the MaskFill operation differs from the above
 * equation because it is not possible to perform such a complex operation in
 * D3d (without the use of advanced techniques like fragment shaders and
 * multitexturing).  Therefore, the D3DMaskFill operation is expressed as:
 *   dst = (src * pathA) <SrcOver> dst
 *
 * This simplified formula is only equivalent to the "true" MaskFill equation
 * in the following situations:
 *   - <MODE> is SrcOver
 *   - <MODE> is Src, extra alpha == 1.0, and the source color is opaque
 *
 * Therefore, we register D3DMaskFill primitives for only the SurfaceType and
 * CompositeType restrictions mentioned above.  In addition for the Src
 * case, we must override the composite with a SrcOver (no extra alpha)
 * instance, so that we set up the D3d blending mode to match the
 * D3DMaskFill equation.
 */
public class D3DMaskFill extends MaskFill {

    public static void register() {
        GraphicsPrimitive[] primitives = {
            new D3DMaskFill(SurfaceType.AnyColor,
                            CompositeType.SrcOver,
                            IntRgbD3D),
            new D3DMaskFill(SurfaceType.OpaqueColor,
                            CompositeType.SrcNoEa,
                            IntRgbD3D),

            new D3DMaskFill(SurfaceType.AnyColor,
                            CompositeType.SrcOver,
                            Ushort565RgbD3D),
            new D3DMaskFill(SurfaceType.OpaqueColor,
                            CompositeType.SrcNoEa,
                            Ushort565RgbD3D),

            new D3DMaskFill(SurfaceType.AnyColor,
                            CompositeType.SrcOver,
                            IntRgbxD3D),
            new D3DMaskFill(SurfaceType.OpaqueColor,
                            CompositeType.SrcNoEa,
                            IntRgbxD3D),

            new D3DMaskFill(SurfaceType.AnyColor,
                            CompositeType.SrcOver,
                            Ushort555RgbD3D),
            new D3DMaskFill(SurfaceType.OpaqueColor,
                            CompositeType.SrcNoEa,
                            Ushort555RgbD3D),

            new D3DMaskFill(SurfaceType.AnyColor,
                            CompositeType.SrcOver,
                            Ushort555RgbxD3D),
            new D3DMaskFill(SurfaceType.OpaqueColor,
                            CompositeType.SrcNoEa,
                            Ushort555RgbxD3D),

            new D3DMaskFill(SurfaceType.AnyColor,
                            CompositeType.SrcOver,
                            ThreeByteBgrD3D),
            new D3DMaskFill(SurfaceType.OpaqueColor,
                            CompositeType.SrcNoEa,
                            ThreeByteBgrD3D),
        };
        GraphicsPrimitiveMgr.register(primitives);
    }

    D3DMaskFill(SurfaceType srcType, CompositeType compType,
                SurfaceType dstType) {
        super(srcType, compType, dstType);
    }

    private native void MaskFill(long pData, long pCtx,
                                 int x, int y, int w, int h,
                                 byte[] mask, int maskoff, int maskscan);

    @Override
    public void MaskFill(SunGraphics2D sg2d, SurfaceData sData,
                         Composite comp,
                         int x, int y, int w, int h,
                         byte[] mask, int maskoff, int maskscan)
    {
        AlphaComposite acomp = (AlphaComposite)comp;
        if (acomp.getRule() != AlphaComposite.SRC_OVER) {
            comp = AlphaComposite.SrcOver;
        }

        synchronized (D3DContext.LOCK) {
            long pCtx = D3DContext.getContext(sData, sData,
                                              sg2d.getCompClip(), comp,
                                              null, sg2d.eargb,
                                              D3DContext.NO_CONTEXT_FLAGS);

            MaskFill(sData.getNativeOps(), pCtx, x, y, w, h,
                     mask, maskoff, maskscan);
        }
    }
}
