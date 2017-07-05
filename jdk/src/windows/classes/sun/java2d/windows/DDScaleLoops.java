/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import sun.java2d.loops.GraphicsPrimitive;
import sun.java2d.loops.GraphicsPrimitiveMgr;
import sun.java2d.loops.GraphicsPrimitiveProxy;
import sun.java2d.loops.CompositeType;
import sun.java2d.loops.SurfaceType;
import sun.java2d.loops.ScaledBlit;
import sun.java2d.pipe.Region;
import sun.java2d.SurfaceData;
import java.awt.Composite;

/**
 * DDScaleLoops
 *
 * This class accelerates ScaledBlits between two DirectDraw surfaces.  Since
 * the onscreen surface is of that type and some of the offscreen surfaces
 * may be of that type (if they were created in a SurfaceDataProxy), then
 * this type of ScaledBlit will accelerated double-buffer copies between those
 * two surfaces.
*/
public class DDScaleLoops extends ScaledBlit {
    private ScaledBlit swblit;

    public static void register()
    {
        GraphicsPrimitive[] primitives = {
            new DDScaleLoops(Win32SurfaceData.IntRgbDD),
            new DDScaleLoops(Win32SurfaceData.Ushort565RgbDD),
            new DDScaleLoops(Win32SurfaceData.IntRgbxDD),
            new DDScaleLoops(Win32SurfaceData.Ushort555RgbxDD),
            new DDScaleLoops(Win32SurfaceData.Ushort555RgbDD),
            new DDScaleLoops(Win32SurfaceData.ByteIndexedOpaqueDD),
            new DDScaleLoops(Win32SurfaceData.ThreeByteBgrDD)
        };
        GraphicsPrimitiveMgr.register(primitives);
    }

    public DDScaleLoops(SurfaceType surfType) {
        super(surfType, CompositeType.SrcNoEa, surfType);
    }

    /**
     * Scale
     * This native method is where all of the work happens in the
     * accelerated ScaledBlit for the scaling case.
     */
    public native void Scale(SurfaceData src, SurfaceData dst,
                             Composite comp, int sx, int sy,
                             int dx, int dy, int sw, int sh,
                             int dw, int dh);

    public void Scale(SurfaceData src, SurfaceData dst,
                      Composite comp, Region clip,
                      int sx1, int sy1,
                      int sx2, int sy2,
                      double dx1, double dy1,
                      double dx2, double dy2)
    {
        // REMIND: We can still do it if the clip equals the device
        // bounds for a destination window, but this logic rejects
        // that case...
        int dx = (int) Math.round(dx1);
        int dy = (int) Math.round(dy1);
        int dw = (int) Math.round(dx2) - dx;
        int dh = (int) Math.round(dy2) - dy;
        if (clip.encompassesXYWH(dx, dy, dw, dh)) {
            // Note that this rounding creates inaccuracies, but these
            // loops are disabled by default until a user specifically
            // enables them so this rounding behavior can be one of
            // the drawbacks that the user accepts when enabling this
            // non-standard feature.
            // If we should ever want to turn them on by default then
            // we will need to decide what better handling to put here.
            Scale(src, dst, comp, sx1, sy1, dx, dy, sx2-sx1, sy2-sy1, dw, dh);
        } else {
            if (swblit == null) {
                // REMIND: This assumes that the DD surface types are
                // directly derived from a non-DD type that has a loop.
                swblit = ScaledBlit.getFromCache(getSourceType().getSuperType(),
                                                 getCompositeType(),
                                                 getDestType().getSuperType());
            }
            swblit.Scale(src, dst, comp, clip,
                         sx1, sy1, sx2, sy2,
                         dx1, dy1, dx2, dy2);
        }
    }
}
