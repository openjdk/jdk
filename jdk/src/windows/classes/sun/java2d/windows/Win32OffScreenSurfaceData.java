/*
 * Copyright 2000-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;

import sun.awt.SunHints;
import sun.awt.Win32GraphicsConfig;
import sun.awt.Win32GraphicsDevice;
import sun.awt.image.SurfaceManager;
import sun.awt.image.SunVolatileImage;
import sun.awt.image.WritableRasterNative;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.SurfaceDataProxy;
import sun.java2d.loops.CompositeType;
import sun.java2d.pipe.PixelToShapeConverter;
import sun.java2d.loops.GraphicsPrimitive;
import sun.java2d.loops.SurfaceType;
import sun.java2d.loops.RenderLoops;
import sun.java2d.pipe.Region;

/**
 * Win32OffScreenSurfaceData
 *
 * This class implements a hardware-accelerated video memory surface.  It uses
 * a custom renderer (DDRenderer) to render via DirectDraw into the
 * surface and uses a custom Blit loop (DDBlitLoops) to copy between
 * two hardware-accelerated surfaces (including the screen).
 */
public class Win32OffScreenSurfaceData extends SurfaceData {

    protected int width;
    protected int height;
    protected int transparency;

    protected GraphicsConfiguration graphicsConfig;
    protected Image image;
    protected RenderLoops solidloops;
    private boolean ddSurfacePunted = false;

    private static native void initIDs();

    static {
        initIDs();
        // REMIND: This isn't really thought-out; if the user doesn't have or
        // doesn't want ddraw then we should not even have this surface type
        // in the loop
        if (WindowsFlags.isDDEnabled() && WindowsFlags.isDDOffscreenEnabled()) {
            if (WindowsFlags.isDDBlitEnabled()) {
                // Register out hardware-accelerated Blit loops
                DDBlitLoops.register();
            }
            if (WindowsFlags.isDDScaleEnabled()) {
                DDScaleLoops.register();
            }
        }
    }

    public static SurfaceType getSurfaceType(ColorModel cm, int transparency) {
        boolean transparent = (transparency == Transparency.BITMASK);
        switch (cm.getPixelSize()) {
        case 32:
        case 24:
            if (cm instanceof DirectColorModel) {
                if (((DirectColorModel)cm).getRedMask() == 0xff0000) {
                    return transparent ? Win32SurfaceData.IntRgbDD_BM :
                                         Win32SurfaceData.IntRgbDD;
                } else {
                    return transparent ? Win32SurfaceData.IntRgbxDD_BM :
                                         Win32SurfaceData.IntRgbxDD;
                }
            } else {
                return transparent ? Win32SurfaceData.ThreeByteBgrDD_BM :
                                     Win32SurfaceData.ThreeByteBgrDD;
            }
        case 15:
            return transparent ? Win32SurfaceData.Ushort555RgbDD_BM :
                                 Win32SurfaceData.Ushort555RgbDD;
        case 16:
            if ((cm instanceof DirectColorModel) &&
                (((DirectColorModel)cm).getBlueMask() == 0x3e))
            {
                return transparent ? Win32SurfaceData.Ushort555RgbxDD_BM :
                                     Win32SurfaceData.Ushort555RgbxDD;
            } else {
                return transparent ? Win32SurfaceData.Ushort565RgbDD_BM :
                                     Win32SurfaceData.Ushort565RgbDD;
            }
        case 8:
            if (cm.getColorSpace().getType() == ColorSpace.TYPE_GRAY &&
                cm instanceof ComponentColorModel) {
                return transparent ? Win32SurfaceData.ByteGrayDD_BM :
                                     Win32SurfaceData.ByteGrayDD;
            } else if (cm instanceof IndexColorModel &&
                       isOpaqueGray((IndexColorModel)cm)) {
                return transparent ? Win32SurfaceData.Index8GrayDD_BM :
                                     Win32SurfaceData.Index8GrayDD;
            } else {
                return transparent ? Win32SurfaceData.ByteIndexedDD_BM :
                                     Win32SurfaceData.ByteIndexedOpaqueDD;
            }
        default:
            throw new sun.java2d.InvalidPipeException("Unsupported bit " +
                                                      "depth: " +
                                                      cm.getPixelSize());
        }
    }

    @Override
    public SurfaceDataProxy makeProxyFor(SurfaceData srcData) {
        Win32GraphicsConfig wgc = (Win32GraphicsConfig) graphicsConfig;
        return Win32SurfaceDataProxy.createProxy(srcData, wgc);
    }

    public static Win32OffScreenSurfaceData
        createData(int width, int height,
                   ColorModel cm, Win32GraphicsConfig gc,
                   Image image, int transparency)
    {
        // Win32OSD doesn't support acceleration of translucent images
        if (transparency == Transparency.TRANSLUCENT) {
            return null;
        }


        Win32GraphicsDevice gd = (Win32GraphicsDevice)gc.getDevice();
        if (!gd.isOffscreenAccelerationEnabled())
        {
            // If acceleration for this type of image is disabled on this
            // device, do not create an accelerated surface type
            return null;
        }

        return new Win32OffScreenSurfaceData(width, height,
                                             getSurfaceType(cm, transparency),
                                             cm, gc, image, transparency,
                                             gd.getScreen());
    }

    protected static DDRenderer ddPipe;
    protected static PixelToShapeConverter ddTxPipe;

    static {
        ddPipe = new DDRenderer();
        if (GraphicsPrimitive.tracingEnabled()) {
            ddPipe = ddPipe.traceWrapDD();
        }
        ddTxPipe = new PixelToShapeConverter(ddPipe);
    }

    public void validatePipe(SunGraphics2D sg2d) {
        if (sg2d.antialiasHint != SunHints.INTVAL_ANTIALIAS_ON &&
            sg2d.paintState <= sg2d.PAINT_ALPHACOLOR &&
            sg2d.compositeState <= sg2d.COMP_ISCOPY &&
            sg2d.clipState != sg2d.CLIP_SHAPE &&
            transparency != Transparency.TRANSLUCENT)
        {
            PixelToShapeConverter txPipe;
            DDRenderer nontxPipe;
            txPipe    = ddTxPipe;
            nontxPipe = ddPipe;
            sg2d.imagepipe = imagepipe;
            if (sg2d.transformState >= sg2d.TRANSFORM_TRANSLATESCALE) {
                sg2d.drawpipe = txPipe;
                sg2d.fillpipe = txPipe;
            } else if (sg2d.strokeState != sg2d.STROKE_THIN){
                sg2d.drawpipe = txPipe;
                sg2d.fillpipe = nontxPipe;
            } else {
                sg2d.drawpipe = nontxPipe;
                sg2d.fillpipe = nontxPipe;
            }
            sg2d.shapepipe = nontxPipe;
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
            // This is needed for AA text.
            // Note that even a SolidTextRenderer can dispatch AA text
            // if a GlyphVector overrides the AA setting.
            sg2d.loops = solidloops;
        } else {
            super.validatePipe(sg2d);
        }
    }

    public static boolean isDDScaleEnabled() {
        return WindowsFlags.isDDScaleEnabled();
    }

    private WritableRasterNative wrn = null;
    public synchronized Raster getRaster(int x, int y, int w, int h) {
        if (wrn == null) {
            wrn = WritableRasterNative.createNativeRaster(getColorModel(),
                                                          this,
                                                          width, height);
            if (wrn == null) {
                throw new InternalError("Unable to create native raster");
            }
        }

        return wrn;
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
    private native void initOps(int depth, int transparency);

    /**
     * This native method creates the offscreen surface in video memory and
     * (if necessary) initializes DirectDraw
     */
    private native void initSurface(int depth, int width, int height,
                                    int screen,
                                    boolean isVolatile,
                                    int transparency);

    public native void restoreSurface();

    /**
     * Non-public constructor.  Use createData() to create an object.
     *
     * This constructor is used to house the common construction
     * code shared between the creation of Win32OSSD objects
     * and subclasses of Win32OSSD (such as D3DSurfaceData
     * and WinBackBufferSurfaceData).
     *
     * It calls the common constructor in the parent, and then
     * initializes other shared Win32 data.
     */
    protected Win32OffScreenSurfaceData(int width, int height,
                                        SurfaceType sType, ColorModel cm,
                                        GraphicsConfiguration gc,
                                        Image image, int transparency)
    {
        super(sType, cm);
        this.width = width;
        this.height = height;
        this.graphicsConfig = gc;
        this.image = image;
        this.transparency = transparency;
        this.solidloops =
            ((Win32GraphicsConfig)graphicsConfig).getSolidLoops(sType);
        initOps(cm.getPixelSize(), transparency);
    }

    /**
     * Private constructor.  Use createData() to create an object.
     *
     * This constructor calls the common constructor above and then
     * performs the specific initialization of the Win32Surface.
     */
    private Win32OffScreenSurfaceData(int width, int height,
                                      SurfaceType sType, ColorModel cm,
                                      Win32GraphicsConfig gc,
                                      Image image, int transparency,
                                      int screen)
    {
        this(width, height, sType, cm, gc, image, transparency);
        initSurface(cm.getPixelSize(), width, height, screen,
                    (image instanceof SunVolatileImage), transparency);
        setBlitProxyKey(gc.getProxyKey());
    }

    /**
     * Need this since the surface data is created with
     * the color model of the target GC, which is always
     * opaque. But in SunGraphics2D.blitSD we choose loops
     * based on the transparency on the source SD, so
     * we could choose wrong loop (blit instead of blitbg,
     * for example, which will cause problems in transparent
     * case).
     */
    public int getTransparency() {
        return transparency;
    }

    /**
     * When someone asks for a new surface data, we punt to our
     * container image which will attempt to restore the contents
     * of this surface or, failing that, will return null.
     */
    public SurfaceData getReplacement() {
        return restoreContents(image);
    }

    public Rectangle getBounds() {
        return new Rectangle(width, height);
    }

    protected native void nativeInvalidate();

    public void invalidate() {
        if (isValid()) {
            synchronized (this) {
                wrn = null;
            }
            nativeInvalidate();
            super.invalidate();
        }
    }

    public native void setTransparentPixel(int pixel);

    public native void flush();

    /**
     * Returns true if the native representation of this image has been
     * moved into ddraw system memory.  This happens when many reads
     * or read-modify-write operations are requested of that surface.
     * If we have moved that surface into system memory, we should note that
     * here so that someone wanting to copy something to this surface will
     * take that into account during that copy.
     */
    public boolean surfacePunted() {
        return ddSurfacePunted;
    }

    protected void markSurfaceLost() {
        synchronized (this) {
            wrn = null;
        }
        setSurfaceLost(true);
        if (image != null) {
            // Inform the Volatile that it lost its accelerated surface
            SurfaceManager sMgr = SurfaceManager.getManager(image);
            sMgr.acceleratedSurfaceLost();
        }
    }

    /**
     * This method is called from the native code if an unrecoverable
     * error has been detected.
     *
     * Marks the surface lost, and notifies the surface manager
     * that the DirectDraw acceleration for the corresponding image
     * should be disabled.
     */
    protected void disableDD() {
        markSurfaceLost();
        if (image != null) {
            SurfaceManager sMgr = SurfaceManager.getManager(image);
            // REMIND: yes, this is not pretty; the accelerationEnabled property
            // should be pulled up to SurfaceManager some day.
            if (sMgr instanceof WinVolatileSurfaceManager) {
                ((WinVolatileSurfaceManager)sMgr).setAccelerationEnabled(false);
            }
        }
        setBlitProxyKey(null);
    }

    /**
     * Returns destination Image associated with this SurfaceData.
     */
    public Object getDestination() {
        return image;
    }

    @Override
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
                ddPipe.devCopyArea(this, dstx1 - dx, dsty1 - dy,
                                   dx, dy,
                                   dstx2 - dstx1, dsty2 - dsty1);
            }
            return true;
        }
        return false;
    }
}
