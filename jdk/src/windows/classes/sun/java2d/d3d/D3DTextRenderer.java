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
import sun.font.GlyphList;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.loops.GraphicsPrimitive;
import sun.java2d.pipe.GlyphListPipe;
import sun.java2d.pipe.Region;

public class D3DTextRenderer extends GlyphListPipe {

    protected native void doDrawGlyphList(long pData, long pCtx,
                                          Region clip, GlyphList gl);

    protected void drawGlyphList(SunGraphics2D sg2d, GlyphList gl) {
        AlphaComposite comp = (AlphaComposite)sg2d.composite;
        // We can only get here if the comp is Src or SrcOver (see
        // pipeline validation in D3DSurfaceData, so we force
        // it to be SrcOver.
        if (comp.getRule() != AlphaComposite.SRC_OVER) {
            comp = AlphaComposite.SrcOver;
        }

        synchronized (D3DContext.LOCK) {
            SurfaceData dstData = sg2d.surfaceData;
            long pCtx = D3DContext.getContext(dstData, dstData,
                                              sg2d.getCompClip(), comp,
                                              null, sg2d.eargb,
                                              D3DContext.NO_CONTEXT_FLAGS);

            doDrawGlyphList(dstData.getNativeOps(), pCtx,
                            sg2d.getCompClip(), gl);
        }
    }

    public D3DTextRenderer traceWrap() {
        return new Tracer();
    }

    public static class Tracer extends D3DTextRenderer {
        @Override
        protected void doDrawGlyphList(long pData, long pCtx,
                                       Region clip, GlyphList gl)
        {
            GraphicsPrimitive.tracePrimitive("D3DDrawGlyphs");
            super.doDrawGlyphList(pData, pCtx, clip, gl);
        }
    }
}
