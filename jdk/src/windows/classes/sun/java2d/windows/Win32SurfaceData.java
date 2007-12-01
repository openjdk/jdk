/*
 * Copyright 1999-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.Rectangle;
import java.awt.GraphicsConfiguration;
import java.awt.color.ColorSpace;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;

import sun.awt.SunHints;
import sun.awt.Win32GraphicsConfig;
import sun.awt.Win32GraphicsDevice;
import sun.awt.image.PixelConverter;
import sun.awt.windows.WComponentPeer;
import sun.awt.windows.WFileDialogPeer;
import sun.awt.windows.WPrintDialogPeer;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.SurfaceDataProxy;
import sun.java2d.pipe.Region;
import sun.java2d.pipe.PixelToShapeConverter;
import sun.java2d.loops.GraphicsPrimitive;
import sun.java2d.loops.SurfaceType;
import sun.java2d.loops.CompositeType;
import sun.java2d.loops.RenderLoops;
import sun.java2d.loops.XORComposite;

public class Win32SurfaceData extends SurfaceData {
    WComponentPeer peer;
    private Win32GraphicsConfig graphicsConfig;
    private RenderLoops solidloops;

    // GDI onscreen surface type
    public static final String
        DESC_GDI                = "GDI";

    // DDraw offscreen surface type names
    public static final String
        DESC_INT_RGB_DD         = "Integer RGB DirectDraw";

    public static final String
        DESC_INT_RGBx_DD        = "Integer RGBx DirectDraw";

    public static final String
        DESC_USHORT_565_RGB_DD  = "Short 565 RGB DirectDraw";

    public static final String
        DESC_USHORT_555_RGBx_DD = "Short 555 RGBx DirectDraw";

    public static final String
        DESC_USHORT_555_RGB_DD  = "Short 555 RGB DirectDraw";

    public static final String
        DESC_BYTE_INDEXED_OPAQUE_DD
                                = "8-bit Indexed (Opaque) DirectDraw";

    public static final String
        DESC_BYTE_GRAY_DD       = "Byte Gray DirectDraw";

    public static final String
        DESC_INDEX8_GRAY_DD     = "Index8 Gray DirectDraw";

    public static final String
        DESC_3BYTE_BGR_DD       = "3 Byte BGR DirectDraw";

    // Surface types with 1-bit transparency
    public static final String
        DESC_INT_RGB_DD_BM      = "Integer RGB DirectDraw with 1 bit transp";

    public static final String
        DESC_INT_RGBx_DD_BM     = "Integer RGBx DirectDraw with 1 bit transp";

    public static final String
        DESC_USHORT_565_RGB_DD_BM
                                = "Short 565 RGB DirectDraw with 1 bit transp";

    public static final String
        DESC_USHORT_555_RGBx_DD_BM
                                = "Short 555 RGBx DirectDraw with 1 bit transp";

    public static final String
        DESC_USHORT_555_RGB_DD_BM
                                = "Short 555 RGB DirectDraw with 1 bit transp";

    public static final String
        DESC_3BYTE_BGR_DD_BM    = "3 Byte BGR DirectDraw with 1 bit transp";

    public static final String
        DESC_BYTE_INDEXED_DD_BM = "8-bit Indexed DirectDraw with 1 bit transp";

    public static final String
        DESC_BYTE_GRAY_DD_BM    = "Byte Gray DirectDraw with 1 bit transp";

    public static final String
        DESC_INDEX8_GRAY_DD_BM  = "Index8 Gray DirectDraw with 1 bit transp";


    // Gdi (screen) surface types

    // Generic GDI surface type - used for registering all loops
    public static final SurfaceType AnyGdi =
        SurfaceType.IntRgb.deriveSubType(DESC_GDI);

    public static final SurfaceType IntRgbGdi =
        SurfaceType.IntRgb.deriveSubType(DESC_GDI);

    public static final SurfaceType Ushort565RgbGdi =
        SurfaceType.Ushort565Rgb.deriveSubType(DESC_GDI);

    public static final SurfaceType Ushort555RgbGdi =
        SurfaceType.Ushort555Rgb.deriveSubType(DESC_GDI);

    public static final SurfaceType ThreeByteBgrGdi =
        SurfaceType.ThreeByteBgr.deriveSubType(DESC_GDI);

    // DDraw offscreen surface types
    public static final SurfaceType IntRgbDD =
        SurfaceType.IntRgb.deriveSubType(DESC_INT_RGB_DD);

    public static final SurfaceType IntRgbxDD =
        SurfaceType.IntRgbx.deriveSubType(DESC_INT_RGBx_DD);

    public static final SurfaceType Ushort565RgbDD =
        SurfaceType.Ushort565Rgb.deriveSubType(DESC_USHORT_565_RGB_DD);

    public static final SurfaceType Ushort555RgbxDD =
        SurfaceType.Ushort555Rgbx.deriveSubType(DESC_USHORT_555_RGBx_DD);

    public static final SurfaceType Ushort555RgbDD =
        SurfaceType.Ushort555Rgb.deriveSubType(DESC_USHORT_555_RGB_DD);

    public static final SurfaceType ByteIndexedOpaqueDD =
        SurfaceType.ByteIndexedOpaque.deriveSubType(DESC_BYTE_INDEXED_OPAQUE_DD);

    public static final SurfaceType ByteGrayDD =
        SurfaceType.ByteGray.deriveSubType(DESC_BYTE_GRAY_DD);

    public static final SurfaceType Index8GrayDD =
        SurfaceType.Index8Gray.deriveSubType(DESC_INDEX8_GRAY_DD);

    public static final SurfaceType ThreeByteBgrDD =
        SurfaceType.ThreeByteBgr.deriveSubType(DESC_3BYTE_BGR_DD);

    // DDraw onscreen surface types (derive from Gdi surfaces)
    public static final SurfaceType IntRgbDDscreen =
        IntRgbGdi.deriveSubType(DESC_INT_RGB_DD);

    public static final SurfaceType Ushort565RgbDDscreen =
        Ushort565RgbGdi.deriveSubType(DESC_USHORT_565_RGB_DD);

    public static final SurfaceType Ushort555RgbDDscreen =
        Ushort555RgbGdi.deriveSubType(DESC_USHORT_555_RGB_DD);

    public static final SurfaceType ThreeByteBgrDDscreen =
        ThreeByteBgrGdi.deriveSubType(DESC_3BYTE_BGR_DD);

    // These screen types will not be handled as GDI surfaces
    // (we can do dithering to 8-bit surfaces faster than
    // GDI, so do not use GDI Blits to indexed surfaces.
    // And Rgbx surfaces are documented to not work with
    // GDI, so do not use GDI for that surface type either)
    public static final SurfaceType IntRgbxDDscreen = IntRgbxDD;

    public static final SurfaceType Ushort555RgbxDDscreen = Ushort555RgbxDD;

    public static final SurfaceType ByteIndexedOpaqueDDscreen =
        ByteIndexedOpaqueDD;

    public static final SurfaceType ByteGrayDDscreen = ByteGrayDD;

    public static final SurfaceType Index8GrayDDscreen = Index8GrayDD;

    // Surface types with 1-bit transparency
    public static final SurfaceType IntRgbDD_BM =
        SurfaceType.Custom.deriveSubType(DESC_INT_RGB_DD_BM,
                                         PixelConverter.Xrgb.instance);

    public static final SurfaceType IntRgbxDD_BM =
        SurfaceType.Custom.deriveSubType(DESC_INT_RGBx_DD_BM,
                                         PixelConverter.Rgbx.instance);

    public static final SurfaceType Ushort565RgbDD_BM =
        SurfaceType.Custom.deriveSubType(DESC_USHORT_565_RGB_DD_BM,
                                         PixelConverter.Ushort565Rgb.instance);

    public static final SurfaceType Ushort555RgbxDD_BM =
        SurfaceType.Custom.deriveSubType(DESC_USHORT_555_RGBx_DD_BM,
                                         PixelConverter.Ushort555Rgbx.instance);

    public static final SurfaceType Ushort555RgbDD_BM =
        SurfaceType.Custom.deriveSubType(DESC_USHORT_555_RGB_DD_BM,
                                         PixelConverter.Ushort555Rgb.instance);

    public static final SurfaceType ByteIndexedDD_BM =
        SurfaceType.Custom.deriveSubType(DESC_BYTE_INDEXED_DD_BM);

    public static final SurfaceType ByteGrayDD_BM =
        SurfaceType.Custom.deriveSubType(DESC_BYTE_GRAY_DD_BM);

    public static final SurfaceType Index8GrayDD_BM =
        SurfaceType.Custom.deriveSubType(DESC_INDEX8_GRAY_DD_BM);

    public static final SurfaceType ThreeByteBgrDD_BM =
        SurfaceType.Custom.deriveSubType(DESC_3BYTE_BGR_DD_BM,
                                         PixelConverter.Xrgb.instance);

    private static native void initIDs(Class xorComp);

    static {
        initIDs(XORComposite.class);
        if (WindowsFlags.isGdiBlitEnabled()) {
            // Register our gdi Blit loops
            GDIBlitLoops.register();
        }
    }

    public static SurfaceType getSurfaceType(ColorModel cm) {
        // REMIND: If ddraw not available, set sType to non-ddraw surface type
        switch (cm.getPixelSize()) {
        case 32:
        case 24:
            if (cm instanceof DirectColorModel) {
                if (((DirectColorModel)cm).getRedMask() == 0xff0000) {
                    return IntRgbDDscreen;
                } else {
                    return IntRgbxDDscreen;
                }
            } else {
                return ThreeByteBgrDDscreen;
            }
        case 15:
            return Ushort555RgbDDscreen;
        case 16:
            if ((cm instanceof DirectColorModel) &&
                (((DirectColorModel)cm).getBlueMask() == 0x3e))
            {
                return Ushort555RgbxDDscreen;
            } else {
                return Ushort565RgbDDscreen;
            }
        case 8:
            if (cm.getColorSpace().getType() == ColorSpace.TYPE_GRAY &&
                cm instanceof ComponentColorModel) {
                return ByteGrayDDscreen;
            } else if (cm instanceof IndexColorModel &&
                       isOpaqueGray((IndexColorModel)cm)) {
                return Index8GrayDDscreen;
            } else {
                return ByteIndexedOpaqueDDscreen;
            }
        default:
            throw new sun.java2d.InvalidPipeException("Unsupported bit " +
                                                      "depth: " +
                                                      cm.getPixelSize());
        }
    }

    @Override
    public SurfaceDataProxy makeProxyFor(SurfaceData srcData) {
        // If D3D is enabled then we might have D3D capabilities, but
        // that pipeline is going away soon so we will not bother
        // creating the D3DProxy needed to manage those.  For now we
        // will just use DDraw cached surfaces in all cases.
        return Win32SurfaceDataProxy.createProxy(srcData, graphicsConfig);
    }

    public static Win32SurfaceData createData(WComponentPeer peer,
                                            int numBuffers)
    {
        SurfaceType sType = getSurfaceType(peer.getDeviceColorModel());
        return new Win32SurfaceData(peer, sType, numBuffers);
    }


    public Raster getRaster(int x, int y, int w, int h) {
        throw new InternalError("not implemented yet");
    }

    protected static GDIRenderer gdiPipe;
    protected static PixelToShapeConverter gdiTxPipe;

    static {
        gdiPipe = new GDIRenderer();
        if (GraphicsPrimitive.tracingEnabled()) {
            gdiPipe = gdiPipe.traceWrap();
        }
        gdiTxPipe = new PixelToShapeConverter(gdiPipe);
    }

    public void validatePipe(SunGraphics2D sg2d) {
        if (sg2d.antialiasHint != SunHints.INTVAL_ANTIALIAS_ON &&
            sg2d.paintState <= sg2d.PAINT_ALPHACOLOR &&
            (sg2d.compositeState <= sg2d.COMP_ISCOPY ||
             sg2d.compositeState == sg2d.COMP_XOR))
        {
            if (sg2d.clipState == sg2d.CLIP_SHAPE) {
                // Do this to init textpipe correctly; we will override the
                // other non-text pipes below
                // REMIND: we should clean this up eventually instead of
                // having this work duplicated.
                super.validatePipe(sg2d);
            } else {
                switch (sg2d.textAntialiasHint) {

                case SunHints.INTVAL_TEXT_ANTIALIAS_DEFAULT:
                    /* equate DEFAULT to OFF which it is for us */
                case SunHints.INTVAL_TEXT_ANTIALIAS_OFF:
                    sg2d.textpipe = solidTextRenderer;
                    break;

                case SunHints.INTVAL_TEXT_ANTIALIAS_ON:
                    sg2d.textpipe = aaTextRenderer;
                    break;

                default:
                    switch (sg2d.getFontInfo().aaHint) {

                    case SunHints.INTVAL_TEXT_ANTIALIAS_LCD_HRGB:
                    case SunHints.INTVAL_TEXT_ANTIALIAS_LCD_VRGB:
                        sg2d.textpipe = lcdTextRenderer;
                        break;

                    case SunHints.INTVAL_TEXT_ANTIALIAS_ON:
                        sg2d.textpipe = aaTextRenderer;
                        break;

                    default:
                        sg2d.textpipe = solidTextRenderer;
                    }
                }
            }
            sg2d.imagepipe = imagepipe;
            if (sg2d.transformState >= sg2d.TRANSFORM_TRANSLATESCALE) {
                sg2d.drawpipe = gdiTxPipe;
                sg2d.fillpipe = gdiTxPipe;
            } else if (sg2d.strokeState != sg2d.STROKE_THIN){
                sg2d.drawpipe = gdiTxPipe;
                sg2d.fillpipe = gdiPipe;
            } else {
                sg2d.drawpipe = gdiPipe;
                sg2d.fillpipe = gdiPipe;
            }
            sg2d.shapepipe = gdiPipe;
            // This is needed for AA text.
            // Note that even a SolidTextRenderer can dispatch AA text
            // if a GlyphVector overrides the AA setting.
            // We use getRenderLoops() rather than setting solidloops
            // directly so that we get the appropriate loops in XOR mode.
            sg2d.loops = getRenderLoops(sg2d);
        } else {
            super.validatePipe(sg2d);
        }
    }

    public RenderLoops getRenderLoops(SunGraphics2D sg2d) {
        if (sg2d.paintState <= sg2d.PAINT_ALPHACOLOR &&
            sg2d.compositeState <= sg2d.COMP_ISCOPY)
        {
            return solidloops;
        }
        return super.getRenderLoops(sg2d);
    }

    public GraphicsConfiguration getDeviceConfiguration() {
        return graphicsConfig;
    }

    /**
     * Initializes the native Ops pointer.
     */
    private native void initOps(WComponentPeer peer, int depth, int redMask,
                                int greenMask, int blueMask, int numBuffers,
                                int screen);

    public Win32SurfaceData(WComponentPeer peer, SurfaceType sType,
                            int numBuffers)
    {
        super(sType, peer.getDeviceColorModel());
        ColorModel cm = peer.getDeviceColorModel();
        this.peer = peer;
        int rMask = 0, gMask = 0, bMask = 0;
        int depth;
        switch (cm.getPixelSize()) {
        case 32:
        case 24:
            if (cm instanceof DirectColorModel) {
                depth = 32;
            } else {
                depth = 24;
            }
            break;
        default:
            depth = cm.getPixelSize();
        }
        if (cm instanceof DirectColorModel) {
            DirectColorModel dcm = (DirectColorModel)cm;
            rMask = dcm.getRedMask();
            gMask = dcm.getGreenMask();
            bMask = dcm.getBlueMask();
        }
        this.graphicsConfig =
            (Win32GraphicsConfig) peer.getGraphicsConfiguration();
        this.solidloops = graphicsConfig.getSolidLoops(sType);
        if (peer instanceof WFileDialogPeer ||
            peer instanceof WPrintDialogPeer )
        {
            // REMIND: Awful hack.  The right fix for this problem
            // would be for these type of Peers to not even use a
            // Win32SurfaceData object since they never do any
            // rendering.  Or they could actually implement the
            // functionality needed in initOps.  But this seems
            // to work for now.  See bug 4391928 for more info.
            return;
        }
        Win32GraphicsDevice gd =
            (Win32GraphicsDevice)graphicsConfig.getDevice();
        initOps(peer, depth, rMask, gMask, bMask, numBuffers, gd.getScreen());
        setBlitProxyKey(graphicsConfig.getProxyKey());
    }

    public SurfaceData getReplacement() {
        return peer.getSurfaceData();
    }

    public Rectangle getBounds() {
        Rectangle r = peer.getBounds();
        r.x = r.y = 0;
        return r;
    }

    public boolean copyArea(SunGraphics2D sg2d,
                            int x, int y, int w, int h, int dx, int dy)
    {
        CompositeType comptype = sg2d.imageComp;
        if (sg2d.transformState < sg2d.TRANSFORM_TRANSLATESCALE &&
            sg2d.clipState != sg2d.CLIP_SHAPE &&
            (CompositeType.SrcOverNoEa.equals(comptype) ||
             CompositeType.SrcNoEa.equals(comptype)))
        {
            x += sg2d.transX;
            y += sg2d.transY;
            int dstx1 = x + dx;
            int dsty1 = y + dy;
            int dstx2 = dstx1 + w;
            int dsty2 = dsty1 + h;
            Region clip = sg2d.getCompClip();
            if (dstx1 < clip.getLoX()) dstx1 = clip.getLoX();
            if (dsty1 < clip.getLoY()) dsty1 = clip.getLoY();
            if (dstx2 > clip.getHiX()) dstx2 = clip.getHiX();
            if (dsty2 > clip.getHiY()) dsty2 = clip.getHiY();
            if (dstx1 < dstx2 && dsty1 < dsty2) {
                gdiPipe.devCopyArea(this, dstx1 - dx, dsty1 - dy,
                                    dx, dy,
                                    dstx2 - dstx1, dsty2 - dsty1);
            }
            return true;
        }
        return false;
    }

    private native void invalidateSD();
    public void invalidate() {
        if (isValid()) {
            invalidateSD();
            super.invalidate();
            //peer.invalidateBackBuffer();
        }
    }

    // This gets called when restoring the back buffer
    public native void restoreSurface();
    public native void flip(SurfaceData data);

    /**
     * Returns destination Component associated with this SurfaceData.
     */
    public Object getDestination() {
        return peer.getTarget();
    }
}
