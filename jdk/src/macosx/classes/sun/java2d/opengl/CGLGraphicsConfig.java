/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.opengl;

import java.awt.AWTException;
import java.awt.BufferCapabilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.ImageCapabilities;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.VolatileImage;
import java.awt.image.WritableRaster;

import sun.awt.CGraphicsConfig;
import sun.awt.CGraphicsDevice;
import sun.awt.TextureSizeConstraining;
import sun.awt.image.OffScreenImage;
import sun.awt.image.SunVolatileImage;
import sun.awt.image.SurfaceManager;
import sun.java2d.Disposer;
import sun.java2d.DisposerRecord;
import sun.java2d.SunGraphics2D;
import sun.java2d.Surface;
import sun.java2d.SurfaceData;
import sun.java2d.opengl.OGLContext.OGLContextCaps;
import sun.java2d.pipe.hw.AccelSurface;
import sun.java2d.pipe.hw.AccelTypedVolatileImage;
import sun.java2d.pipe.hw.ContextCapabilities;
import static sun.java2d.opengl.OGLSurfaceData.*;
import static sun.java2d.opengl.OGLContext.OGLContextCaps.*;
import sun.java2d.opengl.CGLSurfaceData.CGLVSyncOffScreenSurfaceData;
import sun.java2d.pipe.hw.AccelDeviceEventListener;
import sun.java2d.pipe.hw.AccelDeviceEventNotifier;

import sun.lwawt.macosx.CPlatformView;

public class CGLGraphicsConfig extends CGraphicsConfig
    implements OGLGraphicsConfig, TextureSizeConstraining
{
    //private static final int kOpenGLSwapInterval = RuntimeOptions.getCurrentOptions().OpenGLSwapInterval;
    private static final int kOpenGLSwapInterval = 0; // TODO
    protected static boolean cglAvailable;
    private static ImageCapabilities imageCaps = new CGLImageCaps();

    private int pixfmt;
    private BufferCapabilities bufferCaps;
    private long pConfigInfo;
    private ContextCapabilities oglCaps;
    private OGLContext context;
    private Object disposerReferent = new Object();

    public static native int getDefaultPixFmt(int screennum);
    private static native boolean initCGL();
    private static native long getCGLConfigInfo(int screennum, int visualnum,
                                                int swapInterval);
    private static native int getOGLCapabilities(long configInfo);

    static {
        cglAvailable = initCGL();
    }

    protected CGLGraphicsConfig(CGraphicsDevice device, int pixfmt,
                                long configInfo, ContextCapabilities oglCaps)
    {
        super(device);

        this.pixfmt = pixfmt;
        this.pConfigInfo = configInfo;
        this.oglCaps = oglCaps;
        context = new OGLContext(OGLRenderQueue.getInstance(), this);

        // add a record to the Disposer so that we destroy the native
        // CGLGraphicsConfigInfo data when this object goes away
        Disposer.addRecord(disposerReferent,
                           new CGLGCDisposerRecord(pConfigInfo));
    }

    @Override
    public Object getProxyKey() {
        return this;
    }

    @Override
    public SurfaceData createManagedSurface(int w, int h, int transparency) {
        return CGLSurfaceData.createData(this, w, h,
                                         getColorModel(transparency),
                                         null,
                                         OGLSurfaceData.TEXTURE);
    }

    public static CGLGraphicsConfig getConfig(CGraphicsDevice device,
                                              int pixfmt)
    {
        if (!cglAvailable) {
            return null;
        }

        long cfginfo = 0;
        final String ids[] = new String[1];
        OGLRenderQueue rq = OGLRenderQueue.getInstance();
        rq.lock();
        try {
            // getCGLConfigInfo() creates and destroys temporary
            // surfaces/contexts, so we should first invalidate the current
            // Java-level context and flush the queue...
            OGLContext.invalidateCurrentContext();

            cfginfo = getCGLConfigInfo(device.getCoreGraphicsScreen(), pixfmt,
                                       kOpenGLSwapInterval);

            OGLContext.setScratchSurface(cfginfo);
            rq.flushAndInvokeNow(new Runnable() {
                public void run() {
                    ids[0] = OGLContext.getOGLIdString();
                }
            });
        } finally {
            rq.unlock();
        }
        if (cfginfo == 0) {
            return null;
        }

        int oglCaps = getOGLCapabilities(cfginfo);
        ContextCapabilities caps = new OGLContextCaps(oglCaps, ids[0]);

        return new CGLGraphicsConfig(device, pixfmt, cfginfo, caps);
    }

    public static boolean isCGLAvailable() {
        return cglAvailable;
    }

    /**
     * Returns true if the provided capability bit is present for this config.
     * See OGLContext.java for a list of supported capabilities.
     */
    public final boolean isCapPresent(int cap) {
        return ((oglCaps.getCaps() & cap) != 0);
    }

    public final long getNativeConfigInfo() {
        return pConfigInfo;
    }

    /**
     * {@inheritDoc}
     *
     * @see sun.java2d.pipe.hw.BufferedContextProvider#getContext
     */
    public final OGLContext getContext() {
        return context;
    }

    @Override
    public BufferedImage createCompatibleImage(int width, int height) {
        ColorModel model = new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
        WritableRaster
            raster = model.createCompatibleWritableRaster(width, height);
        return new BufferedImage(model, raster, model.isAlphaPremultiplied(),
                                 null);
    }

    @Override
    public ColorModel getColorModel(int transparency) {
        switch (transparency) {
        case Transparency.OPAQUE:
            // REMIND: once the ColorModel spec is changed, this should be
            //         an opaque premultiplied DCM...
            return new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
        case Transparency.BITMASK:
            return new DirectColorModel(25, 0xff0000, 0xff00, 0xff, 0x1000000);
        case Transparency.TRANSLUCENT:
            ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
            return new DirectColorModel(cs, 32,
                                        0xff0000, 0xff00, 0xff, 0xff000000,
                                        true, DataBuffer.TYPE_INT);
        default:
            return null;
        }
    }

    public boolean isDoubleBuffered() {
        return isCapPresent(CAPS_DOUBLEBUFFERED);
    }

    private static class CGLGCDisposerRecord implements DisposerRecord {
        private long pCfgInfo;
        public CGLGCDisposerRecord(long pCfgInfo) {
            this.pCfgInfo = pCfgInfo;
        }
        public void dispose() {
            if (pCfgInfo != 0) {
                OGLRenderQueue.disposeGraphicsConfig(pCfgInfo);
                pCfgInfo = 0;
            }
        }
    }

    // TODO: CGraphicsConfig doesn't implement displayChanged() yet
    //@Override
    public synchronized void displayChanged() {
        //super.displayChanged();

        // the context could hold a reference to a CGLSurfaceData, which in
        // turn has a reference back to this CGLGraphicsConfig, so in order
        // for this instance to be disposed we need to break the connection
        OGLRenderQueue rq = OGLRenderQueue.getInstance();
        rq.lock();
        try {
            OGLContext.invalidateCurrentContext();
        } finally {
            rq.unlock();
        }

        updateTotalDisplayBounds();
    }

    @Override
    public String toString() {
        int screen = getDevice().getCoreGraphicsScreen();
        return ("CGLGraphicsConfig[dev="+screen+",pixfmt="+pixfmt+"]");
    }


    /**
     * The following methods are invoked from ComponentModel.java rather
     * than having the Mac OS X-dependent implementations hardcoded in that
     * class.  This way the appropriate actions are taken based on the peer's
     * GraphicsConfig, whether it is a CGraphicsConfig or a
     * CGLGraphicsConfig.
     */

    /**
     * Creates a new SurfaceData that will be associated with the given
     * LWWindowPeer.
     */
    @Override
    public SurfaceData createSurfaceData(CPlatformView pView) {
        return CGLSurfaceData.createData(pView);
    }

    /**
     * Creates a new SurfaceData that will be associated with the given
     * CGLLayer.
     */
    @Override
    public SurfaceData createSurfaceData(CGLLayer layer) {
        return CGLSurfaceData.createData(layer);
    }

    /**
     * Creates a new hidden-acceleration image of the given width and height
     * that is associated with the target Component.
     */
    @Override
    public Image createAcceleratedImage(Component target,
                                        int width, int height)
    {
        ColorModel model = getColorModel(Transparency.OPAQUE);
        WritableRaster wr =
            model.createCompatibleWritableRaster(width, height);
        return new OffScreenImage(target, model, wr,
                                  model.isAlphaPremultiplied());
    }

    /**
     * The following methods correspond to the multibuffering methods in
     * CWindowPeer.java...
     */

    /**
     * Attempts to create a OGL-based backbuffer for the given peer.  If
     * the requested configuration is not natively supported, an AWTException
     * is thrown.  Otherwise, if the backbuffer creation is successful, a
     * value of 1 is returned.
     */
    @Override
    public long createBackBuffer(CPlatformView pView,
                                 int numBuffers, BufferCapabilities caps)
        throws AWTException
    {
        if (numBuffers > 2) {
            throw new AWTException(
                "Only double or single buffering is supported");
        }
        BufferCapabilities configCaps = getBufferCapabilities();
        if (!configCaps.isPageFlipping()) {
            throw new AWTException("Page flipping is not supported");
        }
        if (caps.getFlipContents() == BufferCapabilities.FlipContents.PRIOR) {
            throw new AWTException("FlipContents.PRIOR is not supported");
        }

        // non-zero return value means backbuffer creation was successful
        // (checked in CPlatformWindow.flip(), etc.)
        return 1;
    }

    /**
     * Destroys the backbuffer object represented by the given handle value.
     */
    @Override
    public void destroyBackBuffer(long backBuffer) {
    }

    /**
     * Creates a VolatileImage that essentially wraps the target Component's
     * backbuffer (the provided backbuffer handle is essentially ignored).
     */
    @Override
    public VolatileImage createBackBufferImage(Component target,
                                               long backBuffer)
    {
        return new SunVolatileImage(target,
                                    target.getWidth(), target.getHeight(),
                                    Boolean.TRUE);
    }

    /**
     * Performs the native OGL flip operation for the given target Component.
     */
    @Override
    public void flip(CPlatformView pView,
                     Component target, VolatileImage xBackBuffer,
                     int x1, int y1, int x2, int y2,
                     BufferCapabilities.FlipContents flipAction)
    {
        if (flipAction == BufferCapabilities.FlipContents.COPIED) {
            SurfaceManager vsm = SurfaceManager.getManager(xBackBuffer);
            SurfaceData sd = vsm.getPrimarySurfaceData();

            if (sd instanceof CGLVSyncOffScreenSurfaceData) {
                CGLVSyncOffScreenSurfaceData vsd =
                    (CGLVSyncOffScreenSurfaceData)sd;
                SurfaceData bbsd = vsd.getFlipSurface();
                Graphics2D bbg =
                    new SunGraphics2D(bbsd, Color.black, Color.white, null);
                try {
                    bbg.drawImage(xBackBuffer, 0, 0, null);
                } finally {
                    bbg.dispose();
                }
            } else {
                pView.drawImageOnPeer(xBackBuffer, x1, y1, x2, y2);
                return;
            }
        } else if (flipAction == BufferCapabilities.FlipContents.PRIOR) {
            // not supported by CGL...
            return;
        }

        OGLSurfaceData.swapBuffers(pView.getAWTView());

        if (flipAction == BufferCapabilities.FlipContents.BACKGROUND) {
            Graphics g = xBackBuffer.getGraphics();
            try {
                g.setColor(target.getBackground());
                g.fillRect(0, 0,
                           xBackBuffer.getWidth(),
                           xBackBuffer.getHeight());
            } finally {
                g.dispose();
            }
        }
    }

    private static class CGLBufferCaps extends BufferCapabilities {
        public CGLBufferCaps(boolean dblBuf) {
            super(imageCaps, imageCaps,
                  dblBuf ? FlipContents.UNDEFINED : null);
        }
    }

    @Override
    public BufferCapabilities getBufferCapabilities() {
        if (bufferCaps == null) {
            bufferCaps = new CGLBufferCaps(isDoubleBuffered());
        }
        return bufferCaps;
    }

    private static class CGLImageCaps extends ImageCapabilities {
        private CGLImageCaps() {
            super(true);
        }
        public boolean isTrueVolatile() {
            return true;
        }
    }

    @Override
    public ImageCapabilities getImageCapabilities() {
        return imageCaps;
    }

    /**
     * {@inheritDoc}
     *
     * @see sun.java2d.pipe.hw.AccelGraphicsConfig#createCompatibleVolatileImage
     */
    public VolatileImage
        createCompatibleVolatileImage(int width, int height,
                                      int transparency, int type)
    {
        if (type == FLIP_BACKBUFFER || type == WINDOW || type == UNDEFINED ||
            transparency == Transparency.BITMASK)
        {
            return null;
        }

        if (type == FBOBJECT) {
            if (!isCapPresent(CAPS_EXT_FBOBJECT)) {
                return null;
            }
        } else if (type == PBUFFER) {
            boolean isOpaque = transparency == Transparency.OPAQUE;
            if (!isOpaque && !isCapPresent(CAPS_STORED_ALPHA)) {
                return null;
            }
        }

        SunVolatileImage vi = new AccelTypedVolatileImage(this, width, height,
                                                          transparency, type);
        Surface sd = vi.getDestSurface();
        if (!(sd instanceof AccelSurface) ||
            ((AccelSurface)sd).getType() != type)
        {
            vi.flush();
            vi = null;
        }

        return vi;
    }

    /**
     * {@inheritDoc}
     *
     * @see sun.java2d.pipe.hw.AccelGraphicsConfig#getContextCapabilities
     */
    public ContextCapabilities getContextCapabilities() {
        return oglCaps;
    }

    public void addDeviceEventListener(AccelDeviceEventListener l) {
        int screen = getDevice().getCoreGraphicsScreen();
        AccelDeviceEventNotifier.addListener(l, screen);
    }

    public void removeDeviceEventListener(AccelDeviceEventListener l) {
        AccelDeviceEventNotifier.removeListener(l);
    }

    private static final Rectangle totalDisplayBounds = new Rectangle();

    private static void updateTotalDisplayBounds() {
        synchronized (totalDisplayBounds) {
            Rectangle virtualBounds = new Rectangle();
            for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                for (GraphicsConfiguration gc : gd.getConfigurations()) {
                    virtualBounds = virtualBounds.union(gc.getBounds());
                }
            }
            totalDisplayBounds.setBounds(virtualBounds);
        }
    }

    // 7160609: GL still fails to create a square texture of this size,
    //          so we use this value to cap the total display bounds.
    native private static int getMaxTextureSize();

    @Override
    public int getMaxTextureWidth() {
        int width;

        synchronized (totalDisplayBounds) {
            if (totalDisplayBounds.width == 0) {
                updateTotalDisplayBounds();
            }
            width = totalDisplayBounds.width;
        }

        return Math.min(width, getMaxTextureSize());
    }

    @Override
    public int getMaxTextureHeight() {
        int height;

        synchronized (totalDisplayBounds) {
            if (totalDisplayBounds.height == 0) {
                updateTotalDisplayBounds();
            }
            height = totalDisplayBounds.height;
        }

        return Math.min(height, getMaxTextureSize());
    }
}
