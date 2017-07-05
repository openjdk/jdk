/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.Color;
import java.awt.Transparency;
import java.awt.Rectangle;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;

import sun.awt.Win32GraphicsConfig;
import sun.awt.Win32GraphicsDevice;
import sun.awt.image.BufImgSurfaceData;
import sun.awt.image.SunWritableRaster;
import sun.java2d.SurfaceData;
import sun.java2d.SurfaceDataProxy;
import sun.java2d.SunGraphics2D;
import sun.java2d.StateTracker;
import sun.java2d.InvalidPipeException;
import sun.java2d.loops.CompositeType;

/**
 * The proxy class contains the logic for when to replace a
 * SurfaceData with a cached X11 Pixmap and the code to create
 * the accelerated surfaces.
 */
public abstract class Win32SurfaceDataProxy extends SurfaceDataProxy {
    /**
     * Represents the maximum size (width * height) of an image that we should
     * scan for an unused color.  Any image larger than this would probably
     * require too much computation time.
     */
    private static final int MAX_SIZE = 65536;

    public static SurfaceDataProxy createProxy(SurfaceData srcData,
                                               Win32GraphicsConfig dstConfig)
    {
        Win32GraphicsDevice wgd =
            (Win32GraphicsDevice) dstConfig.getDevice();
        if (!wgd.isDDEnabledOnDevice() ||
            srcData instanceof Win32SurfaceData ||
            srcData instanceof Win32OffScreenSurfaceData)
        {
            // If they are not on the same screen then we could cache the
            // blit by returning an instance of Opaque below, but this
            // only happens for VolatileImage blits to the wrong screen
            // which we make no promises on so we just punt to UNCACHED...
            return UNCACHED;
        }

        ColorModel srcCM = srcData.getColorModel();
        int srcTransparency = srcCM.getTransparency();

        if (srcTransparency == Transparency.OPAQUE) {
            return new Opaque(dstConfig);
        } else if (srcTransparency == Transparency.BITMASK) {
            if (Bitmask.isCompatible(srcCM, srcData)) {
                return new Bitmask(dstConfig);
            }
        }

        return UNCACHED;
    }

    int srcTransparency;
    Win32GraphicsConfig wgc;

    public Win32SurfaceDataProxy(Win32GraphicsConfig wgc,
                                 int srcTransparency)
    {
        this.wgc = wgc;
        this.srcTransparency = srcTransparency;
        activateDisplayListener();
    }

    @Override
    public SurfaceData validateSurfaceData(SurfaceData srcData,
                                           SurfaceData cachedData,
                                           int w, int h)
    {
        if (cachedData == null ||
            !cachedData.isValid() ||
            cachedData.isSurfaceLost())
        {
            // use the device's color model for ddraw surfaces
            ColorModel dstScreenCM = wgc.getDeviceColorModel();
            try {
                cachedData =
                    Win32OffScreenSurfaceData.createData(w, h,
                                                         dstScreenCM,
                                                         wgc, null,
                                                         srcTransparency);
            } catch (InvalidPipeException e) {
                Win32GraphicsDevice wgd = (Win32GraphicsDevice) wgc.getDevice();
                if (!wgd.isDDEnabledOnDevice()) {
                    invalidate();
                    flush();
                    return null;
                }
            }
        }
        return cachedData;
    }

    /**
     * Proxy for opaque source images.
     */
    public static class Opaque extends Win32SurfaceDataProxy {
        static int TXMAX =
            (WindowsFlags.isDDScaleEnabled()
             ? SunGraphics2D.TRANSFORM_TRANSLATESCALE
             : SunGraphics2D.TRANSFORM_ANY_TRANSLATE);

        public Opaque(Win32GraphicsConfig wgc) {
            super(wgc, Transparency.OPAQUE);
        }

        @Override
        public boolean isSupportedOperation(SurfaceData srcData,
                                            int txtype,
                                            CompositeType comp,
                                            Color bgColor)
        {
            // we save a read from video memory for compositing
            // operations by copying from the buffered image sd
            return (txtype <= TXMAX &&
                    (CompositeType.SrcOverNoEa.equals(comp) ||
                     CompositeType.SrcNoEa.equals(comp)));
        }
    }

    /**
     * Proxy for bitmask transparent source images.
     * This proxy can accelerate unscaled SrcOver copies with no bgColor.
     *
     * Note that this proxy plays some games with returning the srcData
     * from the validate method.  It needs to do this since the conditions
     * for caching an accelerated copy depend on many factors that can
     * change over time, including:
     *
     * - the depth of the display
     * - the availability of a transparent pixel
     */
    public static class Bitmask extends Win32SurfaceDataProxy {
        /**
         * Tests a source image ColorModel and SurfaceData to
         * see if they are of an appropriate size and type to
         * perform our transparent pixel searches.
         *
         * Note that some dynamic factors may occur which prevent
         * us from finding or using a transparent pixel.  These
         * are detailed above in the class comments.  We do not
         * test those conditions here, but rely on the Bitmask
         * proxy to verify those conditions on the fly.
         */
        public static boolean isCompatible(ColorModel srcCM,
                                           SurfaceData srcData)
        {
            if (srcCM instanceof IndexColorModel) {
                return true;
            } else if (srcCM instanceof DirectColorModel) {
                return isCompatibleDCM((DirectColorModel) srcCM, srcData);
            }

            return false;
        }

        /**
         * Tests a given DirectColorModel to make sure it is
         * compatible with the assumptions we make when scanning
         * a DCM image for a transparent pixel.
         */
        public static boolean isCompatibleDCM(DirectColorModel dcm,
                                              SurfaceData srcData)
        {
            // The BISD restriction is because we need to
            // examine the pixels to find a tranparent color
            if (!(srcData instanceof BufImgSurfaceData)) {
                return false;
            }

            // The size restriction prevents us from wasting too
            // much time scanning large images for unused pixel values.
            Rectangle bounds = srcData.getBounds();
            // Using division instead of multiplication avoids overflow
            if (bounds.width <= 0 ||
                MAX_SIZE / bounds.width < bounds.height)
            {
                return false;
            }

            // Below we use the pixels from the data buffer to map
            // directly to pixel values using the dstData.pixelFor()
            // method so the pixel format must be compatible with
            // ARGB or we could end up with bad results.  We assume
            // here that the destination is opaque and so only the
            // red, green, and blue masks matter.
            // These new checks for RGB masks are more correct,
            // but potentially reject the acceleration of some images
            // that we used to allow just because we cannot prove
            // that they will work OK.  If we ever had an INT_BGR
            // image for instance, would that have really failed here?
            // 565 and 555 screens will both keep equal numbers of
            // bits of red and blue, but will differ in the amount of
            // green they keep so INT_BGR might be safe, but if anyone
            // ever created an INT_RBG image then 555 and 565 might
            // differ in whether they thought a transparent pixel
            // was available.  Also, are there any other strange
            // screen formats where bizarre orderings of the RGB
            // would cause the tests below to make mistakes?
            return ((dcm.getPixelSize() == 25) &&
                    (dcm.getTransferType() == DataBuffer.TYPE_INT) &&
                    (dcm.getRedMask()   == 0x00ff0000) &&
                    (dcm.getGreenMask() == 0x0000ff00) &&
                    (dcm.getBlueMask()  == 0x000000ff));
        }

        int transPixel;
        Color transColor;

        // The real accelerated surface - only used when we can find
        // a transparent color.
        SurfaceData accelData;

        public Bitmask(Win32GraphicsConfig wgc) {
            super(wgc, Transparency.BITMASK);
        }

        @Override
        public boolean isSupportedOperation(SurfaceData srcData,
                                            int txtype,
                                            CompositeType comp,
                                            Color bgColor)
        {
            // We have accelerated loops only for blits with SrcOverNoEa
            // (no blit bg loops or blit loops with SrcNoEa)
            return (CompositeType.SrcOverNoEa.equals(comp) &&
                    bgColor == null &&
                    txtype < SunGraphics2D.TRANSFORM_TRANSLATESCALE);
        }

        /**
         * Note that every time we update the surface we may or may
         * not find a transparent pixel depending on what was modified
         * in the source image since the last time we looked.
         * Our validation method saves the accelerated surface aside
         * in a different field so we can switch back and forth between
         * the accelerated version and null depending on whether we
         * find a transparent pixel.
         * Note that we also override getRetryTracker() and return a
         * tracker that tracks the source pixels so that we do not
         * try to revalidate until there are new pixels to be scanned.
         */
        @Override
        public SurfaceData validateSurfaceData(SurfaceData srcData,
                                               SurfaceData cachedData,
                                               int w, int h)
        {
            // Evaluate the dest screen pixel size every time
            ColorModel dstScreenCM = wgc.getDeviceColorModel();
            if (dstScreenCM.getPixelSize() <= 8) {
                return null;
            }
            accelData = super.validateSurfaceData(srcData, accelData, w, h);
            return (accelData != null &&
                    findTransparentPixel(srcData, accelData))
                ? accelData
                : null;
        }

        @Override
        public StateTracker getRetryTracker(SurfaceData srcData) {
            // If we failed to validate, it is permanent until the
            // next change to srcData...
            return srcData.getStateTracker();
        }

        @Override
        public void updateSurfaceData(SurfaceData srcData,
                                      SurfaceData dstData,
                                      int w, int h)
        {
            updateSurfaceDataBg(srcData, dstData, w, h, transColor);
        }

        /**
         * Invoked when the cached surface should be dropped.
         * Overrides the base class implementation so we can invalidate
         * the accelData field instead of the cachedSD field.
         */
        @Override
        public synchronized void flush() {
            SurfaceData accelData = this.accelData;
            if (accelData != null) {
                this.accelData = null;
                accelData.flush();
            }
            super.flush();
        }

        /**
         * The following constants determine the size of the histograms
         * used when searching for an unused color
         */
        private static final int ICM_HISTOGRAM_SIZE = 256;
        private static final int ICM_HISTOGRAM_MASK = ICM_HISTOGRAM_SIZE - 1;
        private static final int DCM_HISTOGRAM_SIZE = 1024;
        private static final int DCM_HISTOGRAM_MASK = DCM_HISTOGRAM_SIZE - 1;

        /**
         * Attempts to find an unused pixel value in the image and if
         * successful, sets up the DirectDraw surface so that it uses
         * this value as its color key.
         */
        public boolean findTransparentPixel(SurfaceData srcData,
                                            SurfaceData accelData)
        {
            ColorModel srcCM = srcData.getColorModel();
            boolean success = false;

            if (srcCM instanceof IndexColorModel) {
                success = findUnusedPixelICM((IndexColorModel) srcCM,
                                             accelData);
            } else if (srcCM instanceof DirectColorModel) {
                success = findUnusedPixelDCM((BufImgSurfaceData) srcData,
                                             accelData);
            }

            if (success) {
                int rgb = accelData.rgbFor(transPixel);
                transColor = new Color(rgb);
                Win32OffScreenSurfaceData wossd =
                    (Win32OffScreenSurfaceData) accelData;
                wossd.setTransparentPixel(transPixel);
            } else {
                transColor = null;
            }
            return success;
        }

        /**
         * Attempts to find an unused pixel value in the color map of an
         * IndexColorModel.  If successful, it returns that value (in the
         * ColorModel of the destination surface) or null otherwise.
         */
        private boolean findUnusedPixelICM(IndexColorModel icm,
                                           SurfaceData accelData) {
            int mapsize = icm.getMapSize();
            int[] histogram = new int[ICM_HISTOGRAM_SIZE];
            int[] cmap = new int[mapsize];
            icm.getRGBs(cmap);

            // load up the histogram
            for (int i = 0; i < mapsize; i++) {
                int pixel = accelData.pixelFor(cmap[i]);
                histogram[pixel & ICM_HISTOGRAM_MASK]++;
            }

            // find an empty histo-bucket
            for (int j = 0; j < histogram.length; j++) {
                if (histogram[j] == 0) {
                    transPixel = j;
                    return true;
                }
            }

            return false;
        }

        /**
         * Attempts to find an unused pixel value in an image with a
         * 25-bit DirectColorModel and a DataBuffer of TYPE_INT.
         * If successful, it returns that value (in the ColorModel
         * of the destination surface) or null otherwise.
         */
        private boolean findUnusedPixelDCM(BufImgSurfaceData bisd,
                                           SurfaceData accelData)
        {
            BufferedImage bimg = (BufferedImage) bisd.getDestination();
            DataBufferInt db =
                (DataBufferInt) bimg.getRaster().getDataBuffer();
            int[] pixels = SunWritableRaster.stealData(db, 0);
            int[] histogram = new int[DCM_HISTOGRAM_SIZE];

            // load up the histogram
            // REMIND: we could possibly make this faster by keeping track
            // of the unique colors found, and only doing a pixelFor()
            // when we come across a new unique color
            // REMIND: We are assuming pixels are in ARGB format.  Is that
            // a safe assumption here?
            for (int i = 0; i < pixels.length; i++) {
                int pixel = accelData.pixelFor(pixels[i]);
                histogram[pixel & DCM_HISTOGRAM_MASK]++;
            }

            // find an empty histo-bucket
            for (int j = 0; j < histogram.length; j++) {
                if (histogram[j] == 0) {
                    transPixel = j;
                    return true;
                }
            }

            return false;
        }
    }
}
