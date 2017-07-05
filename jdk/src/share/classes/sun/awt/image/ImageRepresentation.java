/*
 * Copyright 1995-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt.image;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Transparency;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageObserver;
import sun.awt.image.ByteComponentRaster;
import sun.awt.image.IntegerComponentRaster;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import sun.awt.image.ImageWatched;
import java.util.Hashtable;

public class ImageRepresentation extends ImageWatched implements ImageConsumer
{
    InputStreamImageSource src;
    ToolkitImage image;
    int tag;

    long pData; // used by windows native code only -- internal state REMIND ATTN @@

    int width = -1;
    int height = -1;
    int hints;

    int availinfo;

    Rectangle newbits;

    BufferedImage bimage;
    WritableRaster biRaster;
    protected ColorModel cmodel;
    ColorModel srcModel = null;
    int[] srcLUT = null;
    int srcLUTtransIndex = -1;
    int numSrcLUT = 0;
    boolean forceCMhint;
    int sstride;
    boolean isDefaultBI = false;
    boolean isSameCM = false;

    private native static void initIDs();

    static {
        /* ensure that the necessary native libraries are loaded */
        NativeLibLoader.loadLibraries();
        initIDs();
    }

    /**
     * Create an ImageRepresentation for the given Image.  The
     * width and height are unknown at this point.  The color
     * model is a hint as to the color model to use when creating
     * the buffered image.  If null, the src color model will
     * be used.
     */
    public ImageRepresentation(ToolkitImage im, ColorModel cmodel, boolean
                               forceCMhint) {
        image = im;

        if (image.getSource() instanceof InputStreamImageSource) {
            src = (InputStreamImageSource) image.getSource();
        }

        setColorModel(cmodel);

        this.forceCMhint = forceCMhint;
    }

    /* REMIND: Only used for Frame.setIcon - should use ImageWatcher instead */
    public synchronized void reconstruct(int flags) {
        if (src != null) {
            src.checkSecurity(null, false);
        }
        int missinginfo = flags & ~availinfo;
        if ((availinfo & ImageObserver.ERROR) == 0 && missinginfo != 0) {
            numWaiters++;
            try {
                startProduction();
                missinginfo = flags & ~availinfo;
                while ((availinfo & ImageObserver.ERROR) == 0 &&
                       missinginfo != 0)
                {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    missinginfo = flags & ~availinfo;
                }
            } finally {
                decrementWaiters();
            }
        }
    }

    public void setDimensions(int w, int h) {
        if (src != null) {
            src.checkSecurity(null, false);
        }

        image.setDimensions(w, h);

        newInfo(image, (ImageObserver.WIDTH | ImageObserver.HEIGHT),
                0, 0, w, h);

        if (w <= 0 || h <= 0) {
            imageComplete(ImageConsumer.IMAGEERROR);
            return;
        }

        if (width != w || height != h) {
            // dimension mismatch => trigger recreation of the buffer
            bimage = null;
        }

        width = w;
        height = h;

        availinfo |= ImageObserver.WIDTH | ImageObserver.HEIGHT;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    ColorModel getColorModel() {
        return cmodel;
    }

    BufferedImage getBufferedImage() {
        return bimage;
    }

    /**
     * Returns the BufferedImage that will be used as the representation of
     * the pixel data.  Subclasses can override this method to return
     * platform specific subclasses of BufferedImage that may or may not be
     * accelerated.
     *
     * It is subclass' responsibility to propagate acceleration priority
     * to the newly created image.
     */
    protected BufferedImage createImage(ColorModel cm,
                                        WritableRaster raster,
                                        boolean isRasterPremultiplied,
                                        Hashtable properties)
    {
        BufferedImage bi =
            new BufferedImage(cm, raster, isRasterPremultiplied, null);
        bi.setAccelerationPriority(image.getAccelerationPriority());
        return bi;
    }

    public void setProperties(Hashtable<?,?> props) {
        if (src != null) {
            src.checkSecurity(null, false);
        }
        image.setProperties(props);
        newInfo(image, ImageObserver.PROPERTIES, 0, 0, 0, 0);
    }

    public void setColorModel(ColorModel model) {
        if (src != null) {
            src.checkSecurity(null, false);
        }
        srcModel = model;

        // Check to see if model is INT_RGB
        if (model instanceof IndexColorModel) {
            if (model.getTransparency() == model.TRANSLUCENT) {
                // REMIND:
                // Probably need to composite anyway so force ARGB
                cmodel = ColorModel.getRGBdefault();
                srcLUT = null;
            }
            else {
                IndexColorModel icm = (IndexColorModel) model;
                numSrcLUT = icm.getMapSize();
                srcLUT = new int[Math.max(numSrcLUT, 256)];
                icm.getRGBs(srcLUT);
                srcLUTtransIndex = icm.getTransparentPixel();
                cmodel = model;
            }
        }
        else {
            if (cmodel == null) {
                cmodel = model;
                srcLUT   = null;
            }
            else if (model instanceof DirectColorModel) {
                // If it is INT_RGB or INT_ARGB, use the model
                DirectColorModel dcm = (DirectColorModel) model;
                if ((dcm.getRedMask() == 0xff0000) &&
                    (dcm.getGreenMask() == 0xff00) &&
                    (dcm.getBlueMask()  == 0x00ff)) {
                    cmodel   = model;
                    srcLUT   = null;
                }
            }
        }

        isSameCM = (cmodel == model);
    }

    void createBufferedImage() {
        // REMIND:  Be careful!  Is this called everytime there is a
        // startProduction?  We only want to call it if it is new or
        // there is an error
        isDefaultBI = false;
        try {
            biRaster = cmodel.createCompatibleWritableRaster(width, height);
            bimage = createImage(cmodel, biRaster,
                                 cmodel.isAlphaPremultiplied(), null);
        } catch (Exception e) {
            // Create a default image
            cmodel = ColorModel.getRGBdefault();
            biRaster = cmodel.createCompatibleWritableRaster(width, height);
            bimage = createImage(cmodel, biRaster, false, null);
        }
        int type = bimage.getType();

        if ((cmodel == ColorModel.getRGBdefault()) ||
               (type == BufferedImage.TYPE_INT_RGB) ||
               (type == BufferedImage.TYPE_INT_ARGB_PRE)) {
            isDefaultBI = true;
        }
        else if (cmodel instanceof DirectColorModel) {
            DirectColorModel dcm = (DirectColorModel) cmodel;
            if (dcm.getRedMask() == 0xff0000 &&
                dcm.getGreenMask() == 0xff00 &&
                dcm.getBlueMask()  == 0xff) {
                isDefaultBI = true;
            }
        }
    }

    private void convertToRGB() {
        int w = bimage.getWidth();
        int h = bimage.getHeight();
        int size = w*h;

        DataBufferInt dbi = new DataBufferInt(size);
        // Note that stealData() requires a markDirty() afterwards
        // since we modify the data in it.
        int newpixels[] = SunWritableRaster.stealData(dbi, 0);
        if (cmodel instanceof IndexColorModel &&
            biRaster instanceof ByteComponentRaster &&
            biRaster.getNumDataElements() == 1)
        {
            ByteComponentRaster bct = (ByteComponentRaster) biRaster;
            byte[] data = bct.getDataStorage();
            int coff = bct.getDataOffset(0);
            for (int i=0; i < size; i++) {
                newpixels[i] = srcLUT[data[coff+i]&0xff];
            }
        }
        else {
            Object srcpixels = null;
            int off=0;
            for (int y=0; y < h; y++) {
                for (int x=0; x < w; x++) {
                    srcpixels=biRaster.getDataElements(x, y, srcpixels);
                    newpixels[off++] = cmodel.getRGB(srcpixels);
                }
            }
        }
        // We modified the data array directly above so mark it as dirty now...
        SunWritableRaster.markDirty(dbi);

        isSameCM = false;
        cmodel = ColorModel.getRGBdefault();

        int bandMasks[] = {0x00ff0000,
                           0x0000ff00,
                           0x000000ff,
                           0xff000000};

        biRaster = Raster.createPackedRaster(dbi,w,h,w,
                                             bandMasks,null);

        bimage = createImage(cmodel, biRaster,
                             cmodel.isAlphaPremultiplied(), null);
        srcLUT = null;
        isDefaultBI = true;
    }

    public void setHints(int h) {
        if (src != null) {
            src.checkSecurity(null, false);
        }
        hints = h;
    }

    public native void setICMpixels(int x, int y, int w, int h, int[] lut,
                                    byte[] pix, int off, int scansize,
                                    IntegerComponentRaster ict);
    public native int setDiffICM(int x, int y, int w, int h, int[] lut,
                                 int transPix, int numLut, IndexColorModel icm,
                                 byte[] pix, int off, int scansize,
                                 ByteComponentRaster bct, int chanOff);
    static boolean s_useNative = true;

    public void setPixels(int x, int y, int w, int h,
                          ColorModel model,
                          byte pix[], int off, int scansize) {
        int lineOff=off;
        int poff;
        int[] newLUT=null;

        if (src != null) {
            src.checkSecurity(null, false);
        }

        // REMIND: What if the model doesn't fit in default color model?
        synchronized (this) {
            if (bimage == null) {
                if (cmodel == null) {
                    cmodel = model;
                }
                createBufferedImage();
            }
            if (isSameCM && (cmodel != model) && (srcLUT != null) &&
                (model instanceof IndexColorModel) &&
                (biRaster instanceof ByteComponentRaster))
            {
                IndexColorModel icm = (IndexColorModel) model;
                ByteComponentRaster bct = (ByteComponentRaster) biRaster;
                int numlut = numSrcLUT;
                if (setDiffICM(x, y, w, h, srcLUT, srcLUTtransIndex,
                               numSrcLUT, icm,
                               pix, off, scansize, bct,
                               bct.getDataOffset(0)) == 0) {
                    convertToRGB();
                }
                else {
                    // Note that setDiffICM modified the raster directly
                    // so we must mark it as changed
                    bct.markDirty();
                    if (numlut != numSrcLUT) {
                        boolean hasAlpha = icm.hasAlpha();
                        if (srcLUTtransIndex != -1) {
                            hasAlpha = true;
                        }
                        int nbits = icm.getPixelSize();
                        icm = new IndexColorModel(nbits,
                                                  numSrcLUT, srcLUT,
                                                  0, hasAlpha,
                                                  srcLUTtransIndex,
                                                  (nbits > 8
                                                   ? DataBuffer.TYPE_USHORT
                                                   : DataBuffer.TYPE_BYTE));
                        cmodel = icm;
                        bimage = createImage(icm, bct, false, null);
                    }
                    return;
                }
            }

            if (isDefaultBI) {
                int pixel;
                IntegerComponentRaster iraster =
                                          (IntegerComponentRaster) biRaster;
                if (srcLUT != null && model instanceof IndexColorModel) {
                    if (model != srcModel) {
                        // Fill in the new lut
                        ((IndexColorModel)model).getRGBs(srcLUT);
                        srcModel = model;
                    }

                    if (s_useNative) {
                        // Note that setICMpixels modifies the raster directly
                        // so we must mark it as changed afterwards
                        setICMpixels(x, y, w, h, srcLUT, pix, off, scansize,
                                     iraster);
                        iraster.markDirty();
                    }
                    else {
                        int[] storage = new int[w*h];
                        int soff = 0;
                        // It is an IndexColorModel
                        for (int yoff=0; yoff < h; yoff++,
                                 lineOff += scansize) {
                            poff = lineOff;
                            for (int i=0; i < w; i++) {
                                storage[soff++] = srcLUT[pix[poff++]&0xff];
                            }
                        }
                        iraster.setDataElements(x, y, w, h, storage);
                    }
                }
                else {
                    int[] storage = new int[w];
                    for (int yoff=y; yoff < y+h; yoff++, lineOff += scansize) {
                        poff = lineOff;
                        for (int i=0; i < w; i++) {
                            storage[i] = model.getRGB(pix[poff++]&0xff);
                        }
                        iraster.setDataElements(x, yoff, w, 1, storage);
                    }
                    availinfo |= ImageObserver.SOMEBITS;
                }
            }
            else if ((cmodel == model) &&
                     (biRaster instanceof ByteComponentRaster) &&
                     (biRaster.getNumDataElements() == 1)){
                ByteComponentRaster bt = (ByteComponentRaster) biRaster;
                if (off == 0 && scansize == w) {
                    bt.putByteData(x, y, w, h, pix);
                }
                else {
                    byte[] bpix = new byte[w];
                    poff = off;
                    for (int yoff=y; yoff < y+h; yoff++) {
                        System.arraycopy(pix, poff, bpix, 0, w);
                        bt.putByteData(x, yoff, w, 1, bpix);
                        poff += scansize;
                    }
                }
            }
            else {
                for (int yoff=y; yoff < y+h; yoff++, lineOff += scansize) {
                    poff = lineOff;
                    for (int xoff=x; xoff < x+w; xoff++) {
                        bimage.setRGB(xoff, yoff,
                                      model.getRGB(pix[poff++]&0xff));
                    }
                }
                availinfo |= ImageObserver.SOMEBITS;
            }
        }

        if ((availinfo & ImageObserver.FRAMEBITS) == 0) {
            newInfo(image, ImageObserver.SOMEBITS, x, y, w, h);
        }
    }


    public void setPixels(int x, int y, int w, int h, ColorModel model,
                          int pix[], int off, int scansize)
    {
        int lineOff=off;
        int poff;

        if (src != null) {
            src.checkSecurity(null, false);
        }

        // REMIND: What if the model doesn't fit in default color model?
        synchronized (this) {
            if (bimage == null) {
                if (cmodel == null) {
                    cmodel = model;
                }
                createBufferedImage();
            }

            int[] storage = new int[w];
            int yoff;
            int pixel;

            if (cmodel instanceof IndexColorModel) {
                // REMIND: Right now we don't support writing back into ICM
                // images.
                convertToRGB();
            }

            if ((model == cmodel) &&
                (biRaster instanceof IntegerComponentRaster)) {
                IntegerComponentRaster iraster =
                                         (IntegerComponentRaster) biRaster;

                if (off == 0 && scansize == w) {
                    iraster.setDataElements(x, y, w, h, pix);
                }
                else {
                    // Need to pack the data
                    for (yoff=y; yoff < y+h; yoff++, lineOff+=scansize) {
                        System.arraycopy(pix, lineOff, storage, 0, w);
                        iraster.setDataElements(x, yoff, w, 1, storage);
                    }
                }
            }
            else {
                if (model.getTransparency() != model.OPAQUE &&
                    cmodel.getTransparency() == cmodel.OPAQUE) {
                    convertToRGB();
                }

                if (isDefaultBI) {
                    IntegerComponentRaster iraster =
                                        (IntegerComponentRaster) biRaster;
                    int[] data = iraster.getDataStorage();
                    if (cmodel.equals(model)) {
                        int sstride = iraster.getScanlineStride();
                        int doff = y*sstride + x;
                        for (yoff=0; yoff < h; yoff++, lineOff += scansize) {
                            System.arraycopy(pix, lineOff, data, doff, w);
                            doff += sstride;
                        }
                        // Note: manual modification of pixels, mark the
                        // raster as changed
                        iraster.markDirty();
                    }
                    else {
                        for (yoff=y; yoff < y+h; yoff++, lineOff += scansize) {
                            poff = lineOff;
                            for (int i=0; i < w; i++) {
                                storage[i]=model.getRGB(pix[poff++]);
                            }
                            iraster.setDataElements(x, yoff, w, 1, storage);
                        }
                    }

                    availinfo |= ImageObserver.SOMEBITS;
                }
                else {
                    Object tmp = null;

                    for (yoff=y; yoff < y+h; yoff++, lineOff += scansize) {
                        poff = lineOff;
                        for (int xoff=x; xoff < x+w; xoff++) {
                            pixel = model.getRGB(pix[poff++]);
                            tmp = cmodel.getDataElements(pixel,tmp);
                            biRaster.setDataElements(xoff, yoff,tmp);
                        }
                    }
                    availinfo |= ImageObserver.SOMEBITS;
                }
            }
        }

        // Can't do this here since we might need to transform/clip
        // the region
        if (((availinfo & ImageObserver.FRAMEBITS) == 0)) {
            newInfo(image, ImageObserver.SOMEBITS, x, y, w, h);
        }
    }

    public BufferedImage getOpaqueRGBImage() {
        if (bimage.getType() == BufferedImage.TYPE_INT_ARGB) {
            int w = bimage.getWidth();
            int h = bimage.getHeight();
            int size = w * h;

            // Note that we steal the data array here, but only for reading...
            DataBufferInt db = (DataBufferInt)biRaster.getDataBuffer();
            int[] pixels = SunWritableRaster.stealData(db, 0);

            for (int i = 0; i < size; i++) {
                if ((pixels[i] >>> 24) != 0xff) {
                    return bimage;
                }
            }

            ColorModel opModel = new DirectColorModel(24,
                                                      0x00ff0000,
                                                      0x0000ff00,
                                                      0x000000ff);

            int bandmasks[] = {0x00ff0000, 0x0000ff00, 0x000000ff};
            WritableRaster opRaster = Raster.createPackedRaster(db, w, h, w,
                                                                bandmasks,
                                                                null);

            try {
                BufferedImage opImage = createImage(opModel, opRaster,
                                                    false, null);
                return opImage;
            } catch (Exception e) {
                return bimage;
            }
        }
        return bimage;
    }

    private boolean consuming = false;

    public void imageComplete(int status) {
        if (src != null) {
            src.checkSecurity(null, false);
        }
        boolean done;
        int info;
        switch (status) {
        default:
        case ImageConsumer.IMAGEABORTED:
            done = true;
            info = ImageObserver.ABORT;
            break;
        case ImageConsumer.IMAGEERROR:
            image.addInfo(ImageObserver.ERROR);
            done = true;
            info = ImageObserver.ERROR;
            dispose();
            break;
        case ImageConsumer.STATICIMAGEDONE:
            done = true;
            info = ImageObserver.ALLBITS;
            break;
        case ImageConsumer.SINGLEFRAMEDONE:
            done = false;
            info = ImageObserver.FRAMEBITS;
            break;
        }
        synchronized (this) {
            if (done) {
                image.getSource().removeConsumer(this);
                consuming = false;
                newbits = null;

                if (bimage != null) {
                    bimage = getOpaqueRGBImage();
                }
            }
            availinfo |= info;
            notifyAll();
        }

        newInfo(image, info, 0, 0, width, height);

        image.infoDone(status);
    }

    /*synchronized*/ void startProduction() {
        if (!consuming) {
            consuming = true;
            image.getSource().startProduction(this);
        }
    }

    private int numWaiters;

    private synchronized void checkConsumption() {
        if (isWatcherListEmpty() && numWaiters == 0 &&
            ((availinfo & ImageObserver.ALLBITS) == 0))
        {
            dispose();
        }
    }

    public synchronized void notifyWatcherListEmpty() {
        checkConsumption();
    }

    private synchronized void decrementWaiters() {
        --numWaiters;
        checkConsumption();
    }

    public boolean prepare(ImageObserver iw) {
        if (src != null) {
            src.checkSecurity(null, false);
        }
        if ((availinfo & ImageObserver.ERROR) != 0) {
            if (iw != null) {
                iw.imageUpdate(image, ImageObserver.ERROR|ImageObserver.ABORT,
                               -1, -1, -1, -1);
            }
            return false;
        }
        boolean done = ((availinfo & ImageObserver.ALLBITS) != 0);
        if (!done) {
            addWatcher(iw);
            startProduction();
            // Some producers deliver image data synchronously
            done = ((availinfo & ImageObserver.ALLBITS) != 0);
        }
        return done;
    }

    public int check(ImageObserver iw) {

        if (src != null) {
            src.checkSecurity(null, false);
        }
        if ((availinfo & (ImageObserver.ERROR | ImageObserver.ALLBITS)) == 0) {
            addWatcher(iw);
        }

        return availinfo;
    }

    public boolean drawToBufImage(Graphics g, ToolkitImage img,
                                  int x, int y, Color bg,
                                  ImageObserver iw) {

        if (src != null) {
            src.checkSecurity(null, false);
        }
        if ((availinfo & ImageObserver.ERROR) != 0) {
            if (iw != null) {
                iw.imageUpdate(image, ImageObserver.ERROR|ImageObserver.ABORT,
                               -1, -1, -1, -1);
            }
            return false;
        }
        boolean done  = ((availinfo & ImageObserver.ALLBITS) != 0);
        boolean abort = ((availinfo & ImageObserver.ABORT) != 0);

        if (!done && !abort) {
            addWatcher(iw);
            startProduction();
            // Some producers deliver image data synchronously
            done = ((availinfo & ImageObserver.ALLBITS) != 0);
        }

        if (done || (0 != (availinfo & ImageObserver.FRAMEBITS))) {
            g.drawImage (bimage, x, y, bg, null);
        }

        return done;
    }

    public boolean drawToBufImage(Graphics g, ToolkitImage img,
                                  int x, int y, int w, int h,
                                  Color bg, ImageObserver iw) {

        if (src != null) {
            src.checkSecurity(null, false);
        }
        if ((availinfo & ImageObserver.ERROR) != 0) {
            if (iw != null) {
                iw.imageUpdate(image, ImageObserver.ERROR|ImageObserver.ABORT,
                               -1, -1, -1, -1);
            }
            return false;
        }

        boolean done  = ((availinfo & ImageObserver.ALLBITS) != 0);
        boolean abort = ((availinfo & ImageObserver.ABORT) != 0);

        if (!done && !abort) {
            addWatcher(iw);
            startProduction();
            // Some producers deliver image data synchronously
            done = ((availinfo & ImageObserver.ALLBITS) != 0);
        }

        if (done || (0 != (availinfo & ImageObserver.FRAMEBITS))) {
            g.drawImage (bimage, x, y, w, h, bg, null);
        }

        return done;
    }

    public boolean drawToBufImage(Graphics g, ToolkitImage img,
                                  int dx1, int dy1, int dx2, int dy2,
                                  int sx1, int sy1, int sx2, int sy2,
                                  Color bg, ImageObserver iw) {

        if (src != null) {
            src.checkSecurity(null, false);
        }
        if ((availinfo & ImageObserver.ERROR) != 0) {
            if (iw != null) {
                iw.imageUpdate(image, ImageObserver.ERROR|ImageObserver.ABORT,
                               -1, -1, -1, -1);
            }
            return false;
        }
        boolean done  = ((availinfo & ImageObserver.ALLBITS) != 0);
        boolean abort = ((availinfo & ImageObserver.ABORT) != 0);

        if (!done && !abort) {
            addWatcher(iw);
            startProduction();
            // Some producers deliver image data synchronously
            done = ((availinfo & ImageObserver.ALLBITS) != 0);
        }

        if (done || (0 != (availinfo & ImageObserver.FRAMEBITS))) {
            g.drawImage (bimage,
                         dx1, dy1, dx2, dy2,
                         sx1, sy1, sx2, sy2,
                         bg, null);
        }

        return done;
    }

    public boolean drawToBufImage(Graphics g, ToolkitImage img,
                                  AffineTransform xform,
                                  ImageObserver iw)
    {
        Graphics2D g2 = (Graphics2D) g;

        if (src != null) {
            src.checkSecurity(null, false);
        }
        if ((availinfo & ImageObserver.ERROR) != 0) {
            if (iw != null) {
                iw.imageUpdate(image, ImageObserver.ERROR|ImageObserver.ABORT,
                               -1, -1, -1, -1);
            }
            return false;
        }
        boolean done  = ((availinfo & ImageObserver.ALLBITS) != 0);
        boolean abort = ((availinfo & ImageObserver.ABORT) != 0);

        if (!done && !abort) {
            addWatcher(iw);
            startProduction();
            // Some producers deliver image data synchronously
            done = ((availinfo & ImageObserver.ALLBITS) != 0);
        }

        if (done || (0 != (availinfo & ImageObserver.FRAMEBITS))) {
            g2.drawImage (bimage, xform, null);
        }

        return done;
    }

    synchronized void abort() {
        image.getSource().removeConsumer(this);
        consuming = false;
        newbits = null;
        bimage = null;
        biRaster = null;
        cmodel = null;
        srcLUT = null;
        isDefaultBI = false;
        isSameCM = false;

        newInfo(image, ImageObserver.ABORT, -1, -1, -1, -1);
        availinfo &= ~(ImageObserver.SOMEBITS
                       | ImageObserver.FRAMEBITS
                       | ImageObserver.ALLBITS
                       | ImageObserver.ERROR);
    }

    synchronized void dispose() {
        image.getSource().removeConsumer(this);
        consuming = false;
        newbits = null;
        availinfo &= ~(ImageObserver.SOMEBITS
                       | ImageObserver.FRAMEBITS
                       | ImageObserver.ALLBITS);
    }

    public void setAccelerationPriority(float priority) {
        if (bimage != null) {
            bimage.setAccelerationPriority(priority);
        }
    }
}
