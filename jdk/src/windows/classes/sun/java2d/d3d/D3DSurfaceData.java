/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import sun.awt.SunHints;
import sun.awt.Win32GraphicsConfig;
import sun.awt.Win32GraphicsDevice;
import sun.awt.image.SurfaceManager;
import sun.java2d.InvalidPipeException;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.SurfaceDataProxy;
import sun.java2d.loops.GraphicsPrimitive;
import sun.java2d.loops.SurfaceType;
import sun.java2d.pipe.PixelToShapeConverter;
import sun.java2d.pipe.TextPipe;
import sun.java2d.windows.Win32OffScreenSurfaceData;
import sun.java2d.windows.Win32SurfaceData;
import sun.java2d.windows.WinVolatileSurfaceManager;
import sun.java2d.windows.WindowsFlags;

import static sun.java2d.windows.Win32SurfaceData.*;

public class D3DSurfaceData extends Win32OffScreenSurfaceData {

    // properties of a surface
    /**
     * This property is used for a back-buffer surface
     */
    public static final int D3D_ATTACHED_SURFACE = (1 << 15);
    /**
     * A surface with this property can be used as a Direct3D rendering
     * destination.
     */
    public static final int D3D_RENDER_TARGET    = (1 << 16);

    public static final int
        D3D_INVALID_SURFACE    = 0;
    /**
     * Surface is a Direct3D plain surface (not a texture).
     * Plain surface can be used as render target.
     * VolatileImages typically use plain surfaces as their hardware
     * accelerated surfaces.
     */
    public static final int
        D3D_PLAIN_SURFACE      = (1 << 0) | D3D_RENDER_TARGET;
    /**
     * Direct3D texture. Mostly used for cached accelerated surfaces.
     * Surfaces of this type can be copied from using hardware acceleration
     * by using texture mapping.
     */
    public static final int
        D3D_TEXTURE_SURFACE    = (1 << 1);
    /**
     * Direct3D Backbuffer surface - an attached surface. Used for
     * multibuffered BufferStrategies.
     */
    public static final int
        D3D_BACKBUFFER_SURFACE = D3D_PLAIN_SURFACE | D3D_ATTACHED_SURFACE;
    /**
     * Render-to-texture. A texture which can also be a render target.
     * Combines the benefits of textures (fast copies-from) and
     * backbuffers or plain surfaces (hw-accelerated rendering to the surface)
     */
    public static final int
        D3D_RTT_SURFACE        = D3D_TEXTURE_SURFACE | D3D_RENDER_TARGET;

    // supported texture pixel formats
    public static final int PF_INVALID         =  0;
    public static final int PF_INT_ARGB        =  1;
    public static final int PF_INT_RGB         =  2;
    public static final int PF_INT_RGBX        =  3;
    public static final int PF_INT_BGR         =  4;
    public static final int PF_USHORT_565_RGB  =  5;
    public static final int PF_USHORT_555_RGB  =  6;
    public static final int PF_USHORT_555_RGBX =  7;
    public static final int PF_INT_ARGB_PRE    =  8;
    public static final int PF_USHORT_4444_ARGB=  9;

    public static final String
        DESC_INT_ARGB_D3D         = "Integer ARGB D3D with translucency";
    public static final String
        DESC_USHORT_4444_ARGB_D3D = "UShort 4444 ARGB D3D with translucency";

    /**
     * Surface type for texture destination.  We cannot render textures to
     * the screen because Direct3D is not clipped by the window's clip list,
     * so we only enable the texture blit loops for copies to offscreen
     * accelerated surfaces.
     */
    public static final String
        DESC_DEST_D3D           = "D3D render target";

    public static final SurfaceType D3DSurface =
        SurfaceType.Any.deriveSubType("Direct3D Surface");
    public static final SurfaceType D3DTexture =
        D3DSurface.deriveSubType("Direct3D Texture");

    /**
     * D3D destination surface types (derive from offscreen dd surfaces).
     * Note that all of these surfaces have the same surface description;
     * we do not care about the depth of the surface since texture ops
     * support multiple depths.
     */
    public static final SurfaceType IntRgbD3D =
        IntRgbDD.deriveSubType(DESC_DEST_D3D);

    public static final SurfaceType IntRgbxD3D =
        IntRgbxDD.deriveSubType(DESC_DEST_D3D);

    public static final SurfaceType Ushort565RgbD3D =
        Ushort565RgbDD.deriveSubType(DESC_DEST_D3D);

    public static final SurfaceType Ushort555RgbxD3D =
        Ushort555RgbxDD.deriveSubType(DESC_DEST_D3D);

    public static final SurfaceType Ushort555RgbD3D =
        Ushort555RgbDD.deriveSubType(DESC_DEST_D3D);

    // REMIND: Is it possible to have d3d accelerated on this type of surface?
    public static final SurfaceType ThreeByteBgrD3D =
        ThreeByteBgrDD.deriveSubType(DESC_DEST_D3D);

    public static final SurfaceType IntArgbD3D =
        SurfaceType.IntArgb.deriveSubType(DESC_INT_ARGB_D3D);

    public static final SurfaceType Ushort4444ArgbD3D =
        SurfaceType.Ushort4444Argb.deriveSubType(DESC_USHORT_4444_ARGB_D3D);

    // Textures we can render to using d3d
    public static final SurfaceType IntRgbD3D_RTT =
        IntRgbD3D.deriveSubType(DESC_DEST_D3D);

    public static final SurfaceType IntRgbxD3D_RTT =
        IntRgbxD3D.deriveSubType(DESC_DEST_D3D);

    public static final SurfaceType Ushort565RgbD3D_RTT =
        Ushort565RgbD3D.deriveSubType(DESC_DEST_D3D);

    public static final SurfaceType Ushort555RgbxD3D_RTT =
        Ushort555RgbxD3D.deriveSubType(DESC_DEST_D3D);

    public static final SurfaceType Ushort555RgbD3D_RTT =
        Ushort555RgbD3D.deriveSubType(DESC_DEST_D3D);

    public static final SurfaceType Ushort4444ArgbD3D_RTT =
        Ushort4444ArgbD3D.deriveSubType(DESC_DEST_D3D);

    public static final SurfaceType IntArgbD3D_RTT =
        IntArgbD3D.deriveSubType(DESC_DEST_D3D);

    public static final SurfaceType ThreeByteBgrD3D_RTT =
        ThreeByteBgrD3D.deriveSubType(DESC_DEST_D3D);

    // the type of this surface - texture, plain, back-buffer
    protected int type;
    protected int pixelFormat;

    private D3DContext d3dContext;

    protected static D3DRenderer d3dPipe;
    protected static PixelToShapeConverter d3dTxPipe;
    protected static D3DTextRenderer d3dTextPipe;
    protected static D3DDrawImage d3dDrawImagePipe;

    private native void initOps(int depth, int transparency);

    static {
        if (WindowsFlags.isD3DEnabled()) {
            D3DBlitLoops.register();
            D3DMaskFill.register();
        }

        d3dPipe = new D3DRenderer();
        d3dTxPipe = new PixelToShapeConverter(d3dPipe);
        d3dTextPipe = new D3DTextRenderer();
        d3dDrawImagePipe = new D3DDrawImage();

        if (GraphicsPrimitive.tracingEnabled()) {
            d3dPipe = d3dPipe.traceWrapD3D();
            d3dTextPipe = d3dTextPipe.traceWrap();
        }
    }

    @Override
    public SurfaceDataProxy makeProxyFor(SurfaceData srcData) {
        //D3D may be eliminated soon so no Proxy was created for it...
        //return D3DSurfaceDataProxy.createProxy(srcData, graphicsConfig);
        return SurfaceDataProxy.UNCACHED;
    }

    /**
     * Non-public constructor.  Use createData() to create an object.
     *
     * This constructor is used to house the common construction
     * code shared between the creation of D3DSurfaceData objects
     * and subclasses of D3DSurfaceData (such as D3DBackBufferSD).
     *
     * It calls the common constructor in the parent, and then
     * initializes other shared D3D data.
     */
    protected D3DSurfaceData(int width, int height,
                             int d3dSurfaceType,
                             SurfaceType sType, ColorModel cm,
                             GraphicsConfiguration gc,
                             Image image, int transparency)
    {
        super(width, height, sType, cm, gc, image, transparency);
        this.type = d3dSurfaceType;
    }

    /**
     * Private constructor.  Use createData() to create an object.
     *
     * This constructor calls the common constructor above and then
     * performs the specific initialization of the D3DSurface.
     */
    private D3DSurfaceData(int width, int height,
                           int d3dSurfaceType,
                           SurfaceType sType, ColorModel cm,
                           GraphicsConfiguration gc,
                           Image image, int transparency,
                           int screen)
    {
        this(width, height, d3dSurfaceType, sType, cm, gc, image, transparency);
        pixelFormat = initSurface(width, height, screen,
                                  null /*parent SurfaceData*/);
    }

    public static D3DSurfaceData createData(int width, int height,
                                            int d3dSurfaceType,
                                            ColorModel cm,
                                            GraphicsConfiguration gc,
                                            Image image)
    {
        Win32GraphicsDevice gd = (Win32GraphicsDevice)gc.getDevice();
        // After a display change ddInstance may not be
        // recreated yet, and in this case isD3DEnabledOnDevice will
        // return false, until someone attempted to recreate the
        // primary.
        if (!gd.isD3DEnabledOnDevice()) {
            return null;
        }

        return new D3DSurfaceData(width, height,
                                  d3dSurfaceType,
                                  getSurfaceType(gc, cm, d3dSurfaceType),
                                  cm, gc, image,
                                  cm.getTransparency(), gd.getScreen());
    }

    int getPixelFormat() {
        return pixelFormat;
    }

    static SurfaceType getSurfaceType(GraphicsConfiguration gc,
                                      ColorModel cm,
                                      int d3dSurfaceType)
    {
        if (d3dSurfaceType == D3D_TEXTURE_SURFACE) {
            // for non-rtt textures we have only one surface type
            return D3DTexture;
        } else {
            int pixelSize = cm.getPixelSize();
            Win32GraphicsDevice gd = (Win32GraphicsDevice)gc.getDevice();
            int transparency = cm.getTransparency();

            // We'll attempt to use render-to-texture if render target is
            // requested, but it's not a back-buffer and we support RTT
            // for this configuration.
            boolean useRTT =
                ((d3dSurfaceType & D3D_RENDER_TARGET) != 0) &&
                ((d3dSurfaceType & D3D_BACKBUFFER_SURFACE) == 0) &&
                gd.getD3DContext().isRTTSupported();

            // if there's no RTT available, we can't accelerate non-opaque
            // surfaces, so we return null.
            if (transparency == Transparency.TRANSLUCENT ||
                transparency == Transparency.BITMASK)
            {
                if (pixelSize == 16) {
                    return useRTT ? Ushort4444ArgbD3D_RTT :
                        null/*Ushort4444ArgbD3D*/;
                } else {
                    return useRTT ? IntArgbD3D_RTT : null/*IntArgbD3D*/;
                }
            } else {
                // it's an opaque surface, either a VI or a back-buffer
                switch (pixelSize) {
                case 32:
                case 24:
                    if (cm instanceof DirectColorModel) {
                        if (((DirectColorModel)cm).getRedMask() == 0xff0000) {
                            return useRTT ? IntRgbD3D_RTT : IntRgbD3D;
                        } else {
                            return useRTT ? IntRgbxD3D_RTT : IntRgbxD3D;
                        }
                    } else {
                        return useRTT ? ThreeByteBgrD3D_RTT : ThreeByteBgrD3D;
                    }
                case 15:
                    return useRTT ? Ushort555RgbD3D_RTT : Ushort555RgbD3D;
                case 16:
                    if ((cm instanceof DirectColorModel) &&
                        (((DirectColorModel)cm).getBlueMask() == 0x3e))
                    {
                        return useRTT ? Ushort555RgbxD3D_RTT : Ushort555RgbxD3D;
                    } else {
                        return useRTT ? Ushort565RgbD3D_RTT : Ushort565RgbD3D;
                    }
                case 8: // not supported
                default:
                    throw new sun.java2d.InvalidPipeException("Unsupported bit " +
                                                              "depth: " +
                                                              cm.getPixelSize());
                }
            }
        }
    }

    private native int initOffScreenSurface(long pCtx,
                                            long pData, long parentPdata,
                                            int width, int height,
                                            int type, int screen);

    protected int initSurface(int width, int height, int screen,
                              Win32SurfaceData parentData)
    {
        int pFormat = PF_INVALID;

        synchronized (D3DContext.LOCK) {
            long pData = getNativeOps();
            long pDataParent = 0L;
            if (parentData != null) {
                pDataParent = parentData.getNativeOps();
            }
            D3DContext d3dContext = getContext();
            long pCtx = d3dContext.getNativeContext();
            // native context could be 0 if the context is currently invalid,
            // so attempt to revalidate
            if (pCtx == 0) {
                d3dContext.reinitNativeContext();
                pCtx = d3dContext.getNativeContext();
            }
            if (pData != 0 && pCtx != 0) {
                pFormat = initOffScreenSurface(pCtx,
                                               pData, pDataParent,
                                               width, height, type, screen);
            } else {
                // if the context can't be restored, give up for now.
                throw new InvalidPipeException("D3DSD.initSurface: pData " +
                                               "or pCtx is null");
            }
        }
        return pFormat;
    }

    @Override
    public void validatePipe(SunGraphics2D sg2d) {
        // we don't support COMP_XOR yet..
        if (sg2d.compositeState < sg2d.COMP_XOR) {
            TextPipe textpipe;
            boolean validated = false;

            if (((sg2d.compositeState <= sg2d.COMP_ISCOPY &&
                  sg2d.paintState <= sg2d.PAINT_ALPHACOLOR) ||
                 (sg2d.compositeState == sg2d.COMP_ALPHA &&
                  sg2d.paintState <= sg2d.PAINT_ALPHACOLOR &&
                  (((AlphaComposite)sg2d.composite).getRule() ==
                   AlphaComposite.SRC_OVER))) &&
                sg2d.textAntialiasHint <= SunHints.INTVAL_TEXT_ANTIALIAS_GASP)
            {
                // D3DTextRenderer handles both AA and non-AA text, but
                // only works if composite is SrcNoEa or SrcOver
                textpipe = d3dTextPipe;
            } else {
                // do this to initialize textpipe correctly; we will attempt
                // to override the non-text pipes below
                super.validatePipe(sg2d);
                textpipe = sg2d.textpipe;
                validated = true;
            }

            if (sg2d.antialiasHint != SunHints.INTVAL_ANTIALIAS_ON &&
                sg2d.paintState <= sg2d.PAINT_ALPHACOLOR)
            {
                sg2d.drawpipe =
                    sg2d.strokeState == sg2d.STROKE_THIN ? d3dPipe : d3dTxPipe;
                sg2d.fillpipe = d3dPipe;
                sg2d.shapepipe = d3dPipe;
            } else if (!validated) {
                super.validatePipe(sg2d);
            }
            // install the text pipe based on our earlier decision
            sg2d.textpipe = textpipe;
        } else {
            super.validatePipe(sg2d);
        }

        // always override the image pipe with the specialized D3D pipe
        sg2d.imagepipe = d3dDrawImagePipe;
    }

    /**
     * Disables D3D acceleration on the surface manager of this surfaceData
     * object. This can happen when we encounter a hard error in rendering a D3D
     * primitive (for example, if we were unable to set a surface as D3D target
     * surface).
     * Upon next validation the SurfaceManager will create a non-D3D surface.
     */
    public void disableD3D() {
        markSurfaceLost();
        SurfaceManager sMgr = SurfaceManager.getManager(image);
        if (sMgr instanceof WinVolatileSurfaceManager) {
            ((WinVolatileSurfaceManager)sMgr).setD3DAccelerationEnabled(false);
        }
    }

    @Override
    public boolean surfacePunted() {
        // Punting is disabled for D3D surfaces
        return false;
    }

    D3DContext getContext() {
        return ((Win32GraphicsDevice)graphicsConfig.getDevice()).getD3DContext();
    }
}
