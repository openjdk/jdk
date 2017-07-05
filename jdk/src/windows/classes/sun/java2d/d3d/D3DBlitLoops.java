/*
 * Copyright 2002-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.Composite;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import sun.java2d.loops.GraphicsPrimitive;
import sun.java2d.loops.GraphicsPrimitiveMgr;
import sun.java2d.loops.CompositeType;
import sun.java2d.loops.SurfaceType;
import sun.java2d.loops.Blit;
import sun.java2d.loops.ScaledBlit;
import sun.java2d.loops.TransformBlit;
import sun.java2d.pipe.Region;
import sun.java2d.SurfaceData;

import static sun.java2d.d3d.D3DSurfaceData.*;

/**
 * This class contains accelerated blits/scales/transforms
 * between textures and DD surfaces.
 */
public class D3DBlitLoops {

    static void register()
    {
        GraphicsPrimitive[] primitives = {
            new D3DTextureToSurfaceBlit(IntRgbD3D),
            new D3DTextureToSurfaceBlit(Ushort565RgbD3D),
            new D3DTextureToSurfaceBlit(IntRgbxD3D),
            new D3DTextureToSurfaceBlit(Ushort555RgbD3D),
            new D3DTextureToSurfaceBlit(ThreeByteBgrD3D),

            new D3DTextureToSurfaceScale(IntRgbD3D),
            new D3DTextureToSurfaceScale(Ushort565RgbD3D),
            new D3DTextureToSurfaceScale(IntRgbxD3D),
            new D3DTextureToSurfaceScale(Ushort555RgbD3D),
            new D3DTextureToSurfaceScale(ThreeByteBgrD3D),

            new D3DTextureToSurfaceTransform(D3DTexture, IntRgbD3D),
            new D3DTextureToSurfaceTransform(D3DTexture, Ushort565RgbD3D),
            new D3DTextureToSurfaceTransform(D3DTexture, IntRgbxD3D),
            new D3DTextureToSurfaceTransform(D3DTexture, Ushort555RgbD3D),
            new D3DTextureToSurfaceTransform(D3DTexture, ThreeByteBgrD3D),

            new DelegateSwToTextureLoop(),

        };
        GraphicsPrimitiveMgr.register(primitives);
    }
    static native void doTransform(long pSrc, long pDst, long pCtx,
                                   int hint,
                                   int sx1, int sy1, int sx2, int sy2,
                                   float dx1, float dy1,
                                   float dx2, float dy2);

    static long getContext(SurfaceData src, SurfaceData dst,
                           Region clip, Composite comp, AffineTransform at)
    {
        int ctxFlags;
        if (src.getTransparency() == Transparency.OPAQUE) {
            ctxFlags = D3DContext.SRC_IS_OPAQUE;
        } else {
            ctxFlags = D3DContext.NO_CONTEXT_FLAGS;
        }

        return D3DContext.getContext(src, dst, clip, comp, at,
                                     0xffffffff /* rgb */, ctxFlags);
    }
}

class D3DTextureToSurfaceBlit extends Blit {
    D3DTextureToSurfaceBlit(SurfaceType dstType) {
        super(D3DTexture, CompositeType.AnyAlpha , dstType);
    }

    /**
     * Blit
     * This native method is where all of the work happens in the
     * accelerated Blit.
     */
    @Override
    public void Blit(SurfaceData src, SurfaceData dst,
                     Composite comp, Region clip,
                     int sx, int sy, int dx, int dy, int w, int h)
    {
        synchronized (D3DContext.LOCK) {
            long pCtx = D3DBlitLoops.getContext(src, dst, clip, comp, null);
            D3DBlitLoops.doTransform(src.getNativeOps(), dst.getNativeOps(),
                                     pCtx,
                                     AffineTransformOp.TYPE_NEAREST_NEIGHBOR,
                                     sx, sy, sx+w, sy+h,
                                     (float)dx, (float)dy,
                                     (float)(dx+w), (float)(dy+h));
        }
    }
}

class D3DTextureToSurfaceTransform extends TransformBlit {

    D3DTextureToSurfaceTransform(SurfaceType srcType,
                                 SurfaceType dstType)
    {
        super(srcType, CompositeType.AnyAlpha, dstType);
    }

    @Override
    public void Transform(SurfaceData src, SurfaceData dst,
                          Composite comp, Region clip,
                          AffineTransform at, int hint,
                          int sx, int sy, int dx, int dy,
                          int w, int h)
    {
        synchronized (D3DContext.LOCK) {
            long pCtx = D3DBlitLoops.getContext(src, dst, clip, comp, at);
            D3DBlitLoops.doTransform(src.getNativeOps(), dst.getNativeOps(),
                                     pCtx, hint,
                                     sx, sy, sx+w, sy+h,
                                     (float)dx, (float)dy,
                                     (float)(dx+w), (float)(dy+h));
        }
    }
}

class D3DTextureToSurfaceScale extends ScaledBlit {

    D3DTextureToSurfaceScale(SurfaceType dstType) {
        super(D3DTexture, CompositeType.AnyAlpha, dstType);
    }

    @Override
    public void Scale(SurfaceData src, SurfaceData dst,
                      Composite comp, Region clip,
                      int sx1, int sy1,
                      int sx2, int sy2,
                      double dx1, double dy1,
                      double dx2, double dy2)
    {
        synchronized (D3DContext.LOCK) {
            long pCtx = D3DBlitLoops.getContext(src, dst, clip, comp, null);
            D3DBlitLoops.doTransform(src.getNativeOps(), dst.getNativeOps(),
                                     pCtx,
                                     AffineTransformOp.TYPE_NEAREST_NEIGHBOR,
                                     sx1, sy1, sx2, sy2,
                                     (float)dx1, (float)dy1,
                                     (float)dx2, (float)dy2);
        }
    }
}

class DelegateSwToTextureLoop extends Blit {

    DelegateSwToTextureLoop() {
        super(SurfaceType.Any, CompositeType.SrcNoEa, D3DTexture);
    }

    @Override
    public void Blit(SurfaceData src, SurfaceData dst,
                     Composite comp, Region clip,
                     int sx, int sy, int dx, int dy, int w, int h)
    {
        Blit realBlit = null;
        int pf = ((D3DSurfaceData)dst).getPixelFormat();
        switch (pf) {
        case PF_INT_ARGB:
            realBlit = Blit.getFromCache(src.getSurfaceType(),
                                         CompositeType.SrcNoEa,
                                         SurfaceType.IntArgbPre);
            break;
        case PF_INT_RGB:
            realBlit = Blit.getFromCache(src.getSurfaceType(),
                                         CompositeType.SrcNoEa,
                                         SurfaceType.IntRgb);
            break;
        case PF_USHORT_565_RGB:
            realBlit = Blit.getFromCache(src.getSurfaceType(),
                                         CompositeType.SrcNoEa,
                                         SurfaceType.Ushort565Rgb);
            break;
        case PF_USHORT_555_RGB:
            realBlit = Blit.getFromCache(src.getSurfaceType(),
                                         CompositeType.SrcNoEa,
                                         SurfaceType.Ushort555Rgb);
            break;
        case PF_USHORT_4444_ARGB:
            // REMIND: this should really be premultiplied!
            realBlit = Blit.getFromCache(src.getSurfaceType(),
                                         CompositeType.SrcNoEa,
                                         SurfaceType.Ushort4444Argb);
            break;
        default:
             throw
                 new InternalError("Can't yet handle dest pixel format: "+pf);
        }

        if (realBlit != null) {
            realBlit.Blit(src, dst, comp, clip, sx, sy, dx, dy, w, h);
        }
    }
}
