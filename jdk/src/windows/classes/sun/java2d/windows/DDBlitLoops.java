/*
 * Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.windows;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.awt.image.BufferedImage;
import sun.java2d.loops.GraphicsPrimitive;
import sun.java2d.loops.GraphicsPrimitiveMgr;
import sun.java2d.loops.CompositeType;
import sun.java2d.loops.SurfaceType;
import sun.java2d.loops.Blit;
import sun.java2d.loops.BlitBg;
import sun.java2d.loops.FillRect;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.pipe.Region;
import sun.awt.image.BufImgSurfaceData;

/**
 * DDBlitLoops
 *
 * This class accelerates Blits between two DD surfaces of the same depth,
 * using DirectDraw commands in native code to dispatch Blt calls to
 * the video accelerator.
 */
public class DDBlitLoops extends Blit {

    public static void register()
    {
        GraphicsPrimitive[] primitives = {
            // opaque to opaque loops

            // Note that we use the offscreen surfaceType for
            // this registry, but that both the onscreen and offscreen
            // DD types use the same DESC strings, so these loops will
            // be used whether the src/dest surfaces are onscreen or not
            new DDBlitLoops(Win32SurfaceData.IntRgbDD,
                            Win32SurfaceData.IntRgbDD, false),
            new DDBlitLoops(Win32SurfaceData.Ushort565RgbDD,
                            Win32SurfaceData.Ushort565RgbDD, false),
            new DDBlitLoops(Win32SurfaceData.IntRgbxDD,
                            Win32SurfaceData.IntRgbxDD, false),
            new DDBlitLoops(Win32SurfaceData.Ushort555RgbxDD,
                            Win32SurfaceData.Ushort555RgbxDD, false),
            new DDBlitLoops(Win32SurfaceData.Ushort555RgbDD,
                            Win32SurfaceData.Ushort555RgbDD, false),
            new DDBlitLoops(Win32SurfaceData.ByteIndexedOpaqueDD,
                            Win32SurfaceData.ByteIndexedOpaqueDD, false),
            new DDBlitLoops(Win32SurfaceData.ByteGrayDD,
                            Win32SurfaceData.ByteGrayDD, false),
            new DDBlitLoops(Win32SurfaceData.Index8GrayDD,
                            Win32SurfaceData.Index8GrayDD, false),
            new DDBlitLoops(Win32SurfaceData.ThreeByteBgrDD,
                            Win32SurfaceData.ThreeByteBgrDD, false),

            // 1-bit transparent to opaque loops
            new DDBlitLoops(Win32SurfaceData.IntRgbDD_BM,
                            Win32SurfaceData.IntRgbDD, true),
            new DDBlitLoops(Win32SurfaceData.Ushort565RgbDD_BM,
                            Win32SurfaceData.Ushort565RgbDD, true),
            new DDBlitLoops(Win32SurfaceData.IntRgbxDD_BM,
                            Win32SurfaceData.IntRgbxDD, true),
            new DDBlitLoops(Win32SurfaceData.Ushort555RgbxDD_BM,
                            Win32SurfaceData.Ushort555RgbxDD, true),
            new DDBlitLoops(Win32SurfaceData.Ushort555RgbDD_BM,
                            Win32SurfaceData.Ushort555RgbDD, true),
            new DDBlitLoops(Win32SurfaceData.ByteIndexedDD_BM,
                            Win32SurfaceData.ByteIndexedOpaqueDD, true),
            new DDBlitLoops(Win32SurfaceData.ByteGrayDD_BM,
                            Win32SurfaceData.ByteGrayDD, true),
            new DDBlitLoops(Win32SurfaceData.Index8GrayDD_BM,
                            Win32SurfaceData.Index8GrayDD, true),
            new DDBlitLoops(Win32SurfaceData.ThreeByteBgrDD_BM,
                            Win32SurfaceData.ThreeByteBgrDD, true),

            // any to 1-bit transparent bg loops
            new DelegateBlitBgLoop(Win32SurfaceData.IntRgbDD_BM,
                                   Win32SurfaceData.IntRgbDD),
            new DelegateBlitBgLoop(Win32SurfaceData.Ushort565RgbDD_BM,
                                   Win32SurfaceData.Ushort565RgbDD),
            new DelegateBlitBgLoop(Win32SurfaceData.IntRgbxDD_BM,
                                   Win32SurfaceData.IntRgbxDD),
            new DelegateBlitBgLoop(Win32SurfaceData.Ushort555RgbxDD_BM,
                                   Win32SurfaceData.Ushort555RgbxDD),
            new DelegateBlitBgLoop(Win32SurfaceData.Ushort555RgbDD_BM,
                                   Win32SurfaceData.Ushort555RgbDD),
            new DelegateBlitBgLoop(Win32SurfaceData.ByteIndexedDD_BM,
                                   Win32SurfaceData.ByteIndexedOpaqueDD),
            new DelegateBlitBgLoop(Win32SurfaceData.ByteGrayDD_BM,
                                   Win32SurfaceData.ByteGrayDD),
            new DelegateBlitBgLoop(Win32SurfaceData.Index8GrayDD_BM,
                                   Win32SurfaceData.Index8GrayDD),
            new DelegateBlitBgLoop(Win32SurfaceData.ThreeByteBgrDD_BM,
                                   Win32SurfaceData.ThreeByteBgrDD),

        };
        GraphicsPrimitiveMgr.register(primitives);
    }

    public DDBlitLoops(SurfaceType srcType, SurfaceType dstType, boolean over) {
        super(srcType,
              over ? CompositeType.SrcOverNoEa : CompositeType.SrcNoEa,
              dstType);
    }

    /**
     * Blit
     * This native method is where all of the work happens in the
     * accelerated Blit.
     */
    public native void Blit(SurfaceData src, SurfaceData dst,
                            Composite comp, Region clip,
                            int sx, int sy, int dx, int dy, int w, int h);


    /**
     * BlitBg
     * This loop is used to render from Sw surface data
     * to the Hw one in AOSI.copyBackupToAccelerated.
     */
    static class DelegateBlitBgLoop extends BlitBg {
        SurfaceType dstType;
        private static final Font defaultFont = new Font(Font.DIALOG, Font.PLAIN, 12);

        public DelegateBlitBgLoop(SurfaceType realDstType, SurfaceType delegateDstType) {
            super(SurfaceType.Any, CompositeType.SrcNoEa, realDstType);
            this.dstType = delegateDstType;
        }

        public void BlitBg(SurfaceData srcData, SurfaceData dstData,
                           Composite comp, Region clip, Color bgColor,
                           int srcx, int srcy, int dstx, int dsty, int width, int height)
        {
            ColorModel dstModel = dstData.getColorModel();
            WritableRaster wr =
                dstModel.createCompatibleWritableRaster(width, height);
            boolean isPremult = dstModel.isAlphaPremultiplied();
            BufferedImage bimg =
                new BufferedImage(dstModel, wr, isPremult, null);
            SurfaceData tmpData = BufImgSurfaceData.createData(bimg);
            SunGraphics2D sg2d = new SunGraphics2D(tmpData, bgColor, bgColor,
                                                   defaultFont);
            FillRect fillop = FillRect.locate(SurfaceType.AnyColor,
                                              CompositeType.SrcNoEa,
                                              tmpData.getSurfaceType());
            Blit combineop = Blit.getFromCache(srcData.getSurfaceType(),
                                               CompositeType.SrcOverNoEa,
                                               tmpData.getSurfaceType());
            Blit blitop = Blit.getFromCache(tmpData.getSurfaceType(),
                                            CompositeType.SrcNoEa, dstType);
            fillop.FillRect(sg2d, tmpData, 0, 0, width, height);
            combineop.Blit(srcData, tmpData, AlphaComposite.SrcOver, null,
                           srcx, srcy, 0, 0, width, height);
            blitop.Blit(tmpData, dstData, comp, clip,
                        0, 0, dstx, dsty, width, height);
        }
    }
}
