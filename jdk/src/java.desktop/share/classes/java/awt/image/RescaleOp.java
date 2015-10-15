/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.image;

import java.awt.color.ColorSpace;
import java.awt.geom.Rectangle2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.RenderingHints;
import sun.awt.image.ImagingLib;

/**
 * This class performs a pixel-by-pixel rescaling of the data in the
 * source image by multiplying the sample values for each pixel by a scale
 * factor and then adding an offset. The scaled sample values are clipped
 * to the minimum/maximum representable in the destination image.
 * <p>
 * The pseudo code for the rescaling operation is as follows:
 * <pre>
 *for each pixel from Source object {
 *    for each band/component of the pixel {
 *        dstElement = (srcElement*scaleFactor) + offset
 *    }
 *}
 * </pre>
 * <p>
 * For Rasters, rescaling operates on bands.  The number of
 * sets of scaling constants may be one, in which case the same constants
 * are applied to all bands, or it must equal the number of Source
 * Raster bands.
 * <p>
 * For BufferedImages, rescaling operates on color and alpha components.
 * The number of sets of scaling constants may be one, in which case the
 * same constants are applied to all color (but not alpha) components.
 * Otherwise, the  number of sets of scaling constants may
 * equal the number of Source color components, in which case no
 * rescaling of the alpha component (if present) is performed.
 * If neither of these cases apply, the number of sets of scaling constants
 * must equal the number of Source color components plus alpha components,
 * in which case all color and alpha components are rescaled.
 * <p>
 * BufferedImage sources with premultiplied alpha data are treated in the same
 * manner as non-premultiplied images for purposes of rescaling.  That is,
 * the rescaling is done per band on the raw data of the BufferedImage source
 * without regard to whether the data is premultiplied.  If a color conversion
 * is required to the destination ColorModel, the premultiplied state of
 * both source and destination will be taken into account for this step.
 * <p>
 * Images with an IndexColorModel cannot be rescaled.
 * <p>
 * If a RenderingHints object is specified in the constructor, the
 * color rendering hint and the dithering hint may be used when color
 * conversion is required.
 * <p>
 * Note that in-place operation is allowed (i.e. the source and destination can
 * be the same object).
 * @see java.awt.RenderingHints#KEY_COLOR_RENDERING
 * @see java.awt.RenderingHints#KEY_DITHERING
 */
public class RescaleOp implements BufferedImageOp, RasterOp {
    float[] scaleFactors;
    float[] offsets;
    int length = 0;
    RenderingHints hints;

    private int srcNbits;
    private int dstNbits;


    /**
     * Constructs a new RescaleOp with the desired scale factors
     * and offsets.  The length of the scaleFactor and offset arrays
     * must meet the restrictions stated in the class comments above.
     * The RenderingHints argument may be null.
     * @param scaleFactors the specified scale factors
     * @param offsets the specified offsets
     * @param hints the specified <code>RenderingHints</code>, or
     *        <code>null</code>
     */
    public RescaleOp (float[] scaleFactors, float[] offsets,
                      RenderingHints hints) {
        length = scaleFactors.length;
        if (length > offsets.length) length = offsets.length;

        this.scaleFactors = new float[length];
        this.offsets      = new float[length];
        for (int i=0; i < length; i++) {
            this.scaleFactors[i] = scaleFactors[i];
            this.offsets[i]      = offsets[i];
        }
        this.hints = hints;
    }

    /**
     * Constructs a new RescaleOp with the desired scale factor
     * and offset.  The scaleFactor and offset will be applied to
     * all bands in a source Raster and to all color (but not alpha)
     * components in a BufferedImage.
     * The RenderingHints argument may be null.
     * @param scaleFactor the specified scale factor
     * @param offset the specified offset
     * @param hints the specified <code>RenderingHints</code>, or
     *        <code>null</code>
     */
    public RescaleOp (float scaleFactor, float offset, RenderingHints hints) {
        length = 1;
        this.scaleFactors = new float[1];
        this.offsets      = new float[1];
        this.scaleFactors[0] = scaleFactor;
        this.offsets[0]       = offset;
        this.hints = hints;
    }

    /**
     * Returns the scale factors in the given array. The array is also
     * returned for convenience.  If scaleFactors is null, a new array
     * will be allocated.
     * @param scaleFactors the array to contain the scale factors of
     *        this <code>RescaleOp</code>
     * @return the scale factors of this <code>RescaleOp</code>.
     */
    public final float[] getScaleFactors (float scaleFactors[]) {
        if (scaleFactors == null) {
            return this.scaleFactors.clone();
        }
        System.arraycopy (this.scaleFactors, 0, scaleFactors, 0,
                          Math.min(this.scaleFactors.length,
                                   scaleFactors.length));
        return scaleFactors;
    }

    /**
     * Returns the offsets in the given array. The array is also returned
     * for convenience.  If offsets is null, a new array
     * will be allocated.
     * @param offsets the array to contain the offsets of
     *        this <code>RescaleOp</code>
     * @return the offsets of this <code>RescaleOp</code>.
     */
    public final float[] getOffsets(float offsets[]) {
        if (offsets == null) {
            return this.offsets.clone();
        }

        System.arraycopy (this.offsets, 0, offsets, 0,
                          Math.min(this.offsets.length, offsets.length));
        return offsets;
    }

    /**
     * Returns the number of scaling factors and offsets used in this
     * RescaleOp.
     * @return the number of scaling factors and offsets of this
     *         <code>RescaleOp</code>.
     */
    public final int getNumFactors() {
        return length;
    }


    /**
     * Creates a ByteLookupTable to implement the rescale.
     * The table may have either a SHORT or BYTE input.
     * @param nElems    Number of elements the table is to have.
     *                  This will generally be 256 for byte and
     *                  65536 for short.
     */
    private ByteLookupTable createByteLut(float scale[],
                                          float off[],
                                          int   nBands,
                                          int   nElems) {

        byte[][]        lutData = new byte[scale.length][nElems];

        for (int band=0; band<scale.length; band++) {
            float  bandScale   = scale[band];
            float  bandOff     = off[band];
            byte[] bandLutData = lutData[band];
            for (int i=0; i<nElems; i++) {
                int val = (int)(i*bandScale + bandOff);
                if ((val & 0xffffff00) != 0) {
                    if (val < 0) {
                        val = 0;
                    } else {
                        val = 255;
                    }
                }
                bandLutData[i] = (byte)val;
            }

        }

        return new ByteLookupTable(0, lutData);
    }

    /**
     * Creates a ShortLookupTable to implement the rescale.
     * The table may have either a SHORT or BYTE input.
     * @param nElems    Number of elements the table is to have.
     *                  This will generally be 256 for byte and
     *                  65536 for short.
     */
    private ShortLookupTable createShortLut(float scale[],
                                            float off[],
                                            int   nBands,
                                            int   nElems) {

        short[][]        lutData = new short[scale.length][nElems];

        for (int band=0; band<scale.length; band++) {
            float   bandScale   = scale[band];
            float   bandOff     = off[band];
            short[] bandLutData = lutData[band];
            for (int i=0; i<nElems; i++) {
                int val = (int)(i*bandScale + bandOff);
                if ((val & 0xffff0000) != 0) {
                    if (val < 0) {
                        val = 0;
                    } else {
                        val = 65535;
                    }
                }
                bandLutData[i] = (short)val;
            }
        }

        return new ShortLookupTable(0, lutData);
    }


    /**
     * Determines if the rescale can be performed as a lookup.
     * The dst must be a byte or short type.
     * The src must be less than 16 bits.
     * All source band sizes must be the same and all dst band sizes
     * must be the same.
     */
    private boolean canUseLookup(Raster src, Raster dst) {

        //
        // Check that the src datatype is either a BYTE or SHORT
        //
        int datatype = src.getDataBuffer().getDataType();
        if(datatype != DataBuffer.TYPE_BYTE &&
           datatype != DataBuffer.TYPE_USHORT) {
            return false;
        }

        //
        // Check dst sample sizes. All must be 8 or 16 bits.
        //
        SampleModel dstSM = dst.getSampleModel();
        dstNbits = dstSM.getSampleSize(0);

        if (!(dstNbits == 8 || dstNbits == 16)) {
            return false;
        }
        for (int i=1; i<src.getNumBands(); i++) {
            int bandSize = dstSM.getSampleSize(i);
            if (bandSize != dstNbits) {
                return false;
            }
        }

        //
        // Check src sample sizes. All must be the same size
        //
        SampleModel srcSM = src.getSampleModel();
        srcNbits = srcSM.getSampleSize(0);
        if (srcNbits > 16) {
            return false;
        }
        for (int i=1; i<src.getNumBands(); i++) {
            int bandSize = srcSM.getSampleSize(i);
            if (bandSize != srcNbits) {
                return false;
            }
        }

        return true;
    }

    /**
     * Rescales the source BufferedImage.
     * If the color model in the source image is not the same as that
     * in the destination image, the pixels will be converted
     * in the destination.  If the destination image is null,
     * a BufferedImage will be created with the source ColorModel.
     * An IllegalArgumentException may be thrown if the number of
     * scaling factors/offsets in this object does not meet the
     * restrictions stated in the class comments above, or if the
     * source image has an IndexColorModel.
     * @param src the <code>BufferedImage</code> to be filtered
     * @param dst the destination for the filtering operation
     *            or <code>null</code>
     * @return the filtered <code>BufferedImage</code>.
     * @throws IllegalArgumentException if the <code>ColorModel</code>
     *         of <code>src</code> is an <code>IndexColorModel</code>,
     *         or if the number of scaling factors and offsets in this
     *         <code>RescaleOp</code> do not meet the requirements
     *         stated in the class comments.
     */
    public final BufferedImage filter (BufferedImage src, BufferedImage dst) {
        ColorModel srcCM = src.getColorModel();
        ColorModel dstCM;
        int numSrcColorComp = srcCM.getNumColorComponents();
        int scaleConst = length;

        if (srcCM instanceof IndexColorModel) {
            throw new
                IllegalArgumentException("Rescaling cannot be "+
                                         "performed on an indexed image");
        }
        if (scaleConst != 1 && scaleConst != numSrcColorComp &&
            scaleConst != srcCM.getNumComponents())
        {
            throw new IllegalArgumentException("Number of scaling constants "+
                                               "does not equal the number of"+
                                               " of color or color/alpha "+
                                               " components");
        }

        boolean needToConvert = false;

        // Include alpha
        if (scaleConst > numSrcColorComp && srcCM.hasAlpha()) {
            scaleConst = numSrcColorComp+1;
        }

        int width = src.getWidth();
        int height = src.getHeight();

        BufferedImage origDst = dst;
        if (dst == null) {
            dst = createCompatibleDestImage(src, null);
            dstCM = srcCM;
        }
        else {
            if (width != dst.getWidth()) {
                throw new
                    IllegalArgumentException("Src width ("+width+
                                             ") not equal to dst width ("+
                                             dst.getWidth()+")");
            }
            if (height != dst.getHeight()) {
                throw new
                    IllegalArgumentException("Src height ("+height+
                                             ") not equal to dst height ("+
                                             dst.getHeight()+")");
            }

            dstCM = dst.getColorModel();
            if(srcCM.getColorSpace().getType() !=
               dstCM.getColorSpace().getType()) {
                needToConvert = true;
                dst = createCompatibleDestImage(src, null);
            }

        }

        boolean scaleAlpha = true;

        //
        // The number of sets of scaling constants may be one,
        // in which case the same constants are applied to all color
        // (but NOT alpha) components. Otherwise, the number of sets
        // of scaling constants may equal the number of Source color
        // components, in which case NO rescaling of the alpha component
        // (if present) is performed.
        //
        if (numSrcColorComp == scaleConst || scaleConst == 1) {
            scaleAlpha = false;
        }

        //
        // Try to use a native BI rescale operation first
        //
        if (ImagingLib.filter(this, src, dst) == null) {
            //
            // Native BI rescale failed - convert to rasters
            //
            WritableRaster srcRaster = src.getRaster();
            WritableRaster dstRaster = dst.getRaster();

            if (!scaleAlpha) {
                if (srcCM.hasAlpha()) {
                    // Do not rescale Alpha component
                    int minx = srcRaster.getMinX();
                    int miny = srcRaster.getMinY();
                    int[] bands = new int[numSrcColorComp];
                    for (int i=0; i < numSrcColorComp; i++) {
                        bands[i] = i;
                    }
                    srcRaster =
                        srcRaster.createWritableChild(minx, miny,
                                                      srcRaster.getWidth(),
                                                      srcRaster.getHeight(),
                                                      minx, miny,
                                                      bands);
                }
                if (dstCM.hasAlpha()) {
                    int minx = dstRaster.getMinX();
                    int miny = dstRaster.getMinY();
                    int[] bands = new int[numSrcColorComp];
                    for (int i=0; i < numSrcColorComp; i++) {
                        bands[i] = i;
                    }
                    dstRaster =
                        dstRaster.createWritableChild(minx, miny,
                                                      dstRaster.getWidth(),
                                                      dstRaster.getHeight(),
                                                      minx, miny,
                                                      bands);
                }
            }

            //
            // Call the raster filter method
            //
            filterRasterImpl(srcRaster, dstRaster, scaleConst);

            //
            // here copy the unscaled src alpha to destination alpha channel
            //
            if (!scaleAlpha) {
                Raster srcAlphaRaster = null;
                WritableRaster dstAlphaRaster = null;

                if (srcCM.hasAlpha()) {
                    srcAlphaRaster = src.getAlphaRaster();
                }
                if (dstCM.hasAlpha()) {
                    dstAlphaRaster = dst.getAlphaRaster();
                    if (srcAlphaRaster != null) {
                        dstAlphaRaster.setRect(srcAlphaRaster);
                    } else {
                        int alpha = 0xff << 24;
                        for (int cy=0; cy < dst.getHeight(); cy++) {
                            for (int cx=0; cx < dst.getWidth(); cx++) {
                                int color = dst.getRGB(cx, cy);

                                dst.setRGB(cx, cy, color | alpha);
                            }
                        }
                    }
                }
            }
        }

        if (needToConvert) {
            // ColorModels are not the same
            ColorConvertOp ccop = new ColorConvertOp(hints);
            dst = ccop.filter(dst, origDst);
        }
        return dst;
    }

    /**
     * Rescales the pixel data in the source Raster.
     * If the destination Raster is null, a new Raster will be created.
     * The source and destination must have the same number of bands.
     * Otherwise, an IllegalArgumentException is thrown.
     * Note that the number of scaling factors/offsets in this object must
     * meet the restrictions stated in the class comments above.
     * Otherwise, an IllegalArgumentException is thrown.
     * @param src the <code>Raster</code> to be filtered
     * @param dst the destination for the filtering operation
     *            or <code>null</code>
     * @return the filtered <code>WritableRaster</code>.
     * @throws IllegalArgumentException if <code>src</code> and
     *         <code>dst</code> do not have the same number of bands,
     *         or if the number of scaling factors and offsets in this
     *         <code>RescaleOp</code> do not meet the requirements
     *         stated in the class comments.
     */
    public final WritableRaster filter (Raster src, WritableRaster dst)  {
        return filterRasterImpl(src, dst, length);
    }

    private WritableRaster filterRasterImpl(Raster src, WritableRaster dst, int scaleConst) {
        int numBands = src.getNumBands();
        int width  = src.getWidth();
        int height = src.getHeight();
        int[] srcPix = null;
        int step = 0;
        int tidx = 0;

        // Create a new destination Raster, if needed
        if (dst == null) {
            dst = createCompatibleDestRaster(src);
        }
        else if (height != dst.getHeight() || width != dst.getWidth()) {
            throw new
               IllegalArgumentException("Width or height of Rasters do not "+
                                        "match");
        }
        else if (numBands != dst.getNumBands()) {
            // Make sure that the number of bands are equal
            throw new IllegalArgumentException("Number of bands in src "
                            + numBands
                            + " does not equal number of bands in dest "
                            + dst.getNumBands());
        }

        // Make sure that the arrays match
        // Make sure that the low/high/constant arrays match
        if (scaleConst != 1 && scaleConst != src.getNumBands()) {
            throw new IllegalArgumentException("Number of scaling constants "+
                                               "does not equal the number of"+
                                               " of bands in the src raster");
        }

        //
        // Try for a native raster rescale first
        //
        if (ImagingLib.filter(this, src, dst) != null) {
            return dst;
        }

        //
        // Native raster rescale failed.
        // Try to see if a lookup operation can be used
        //
        if (canUseLookup(src, dst)) {
            int srcNgray = (1 << srcNbits);
            int dstNgray = (1 << dstNbits);

            if (dstNgray == 256) {
                ByteLookupTable lut = createByteLut(scaleFactors, offsets,
                                                    numBands, srcNgray);
                LookupOp op = new LookupOp(lut, hints);
                op.filter(src, dst);
            } else {
                ShortLookupTable lut = createShortLut(scaleFactors, offsets,
                                                      numBands, srcNgray);
                LookupOp op = new LookupOp(lut, hints);
                op.filter(src, dst);
            }
        } else {
            //
            // Fall back to the slow code
            //
            if (scaleConst > 1) {
                step = 1;
            }

            int sminX = src.getMinX();
            int sY = src.getMinY();
            int dminX = dst.getMinX();
            int dY = dst.getMinY();
            int sX;
            int dX;

            //
            //  Determine bits per band to determine maxval for clamps.
            //  The min is assumed to be zero.
            //  REMIND: This must change if we ever support signed data types.
            //
            int nbits;
            int dstMax[] = new int[numBands];
            int dstMask[] = new int[numBands];
            SampleModel dstSM = dst.getSampleModel();
            for (int z=0; z<numBands; z++) {
                nbits = dstSM.getSampleSize(z);
                dstMax[z] = (1 << nbits) - 1;
                dstMask[z] = ~(dstMax[z]);
            }

            int val;
            for (int y=0; y < height; y++, sY++, dY++) {
                dX = dminX;
                sX = sminX;
                for (int x = 0; x < width; x++, sX++, dX++) {
                    // Get data for all bands at this x,y position
                    srcPix = src.getPixel(sX, sY, srcPix);
                    tidx = 0;
                    for (int z=0; z<numBands; z++, tidx += step) {
                        val = (int)(srcPix[z]*scaleFactors[tidx]
                                          + offsets[tidx]);
                        // Clamp
                        if ((val & dstMask[z]) != 0) {
                            if (val < 0) {
                                val = 0;
                            } else {
                                val = dstMax[z];
                            }
                        }
                        srcPix[z] = val;

                    }

                    // Put it back for all bands
                    dst.setPixel(dX, dY, srcPix);
                }
            }
        }
        return dst;
    }

    /**
     * Returns the bounding box of the rescaled destination image.  Since
     * this is not a geometric operation, the bounding box does not
     * change.
     */
    public final Rectangle2D getBounds2D (BufferedImage src) {
         return getBounds2D(src.getRaster());
    }

    /**
     * Returns the bounding box of the rescaled destination Raster.  Since
     * this is not a geometric operation, the bounding box does not
     * change.
     * @param src the rescaled destination <code>Raster</code>
     * @return the bounds of the specified <code>Raster</code>.
     */
    public final Rectangle2D getBounds2D (Raster src) {
        return src.getBounds();
    }

    /**
     * Creates a zeroed destination image with the correct size and number of
     * bands.
     * @param src       Source image for the filter operation.
     * @param destCM    ColorModel of the destination.  If null, the
     *                  ColorModel of the source will be used.
     * @return the zeroed-destination image.
     */
    public BufferedImage createCompatibleDestImage (BufferedImage src,
                                                    ColorModel destCM) {
        BufferedImage image;
        if (destCM == null) {
            ColorModel cm = src.getColorModel();
            image = new BufferedImage(cm,
                                      src.getRaster().createCompatibleWritableRaster(),
                                      cm.isAlphaPremultiplied(),
                                      null);
        }
        else {
            int w = src.getWidth();
            int h = src.getHeight();
            image = new BufferedImage (destCM,
                                   destCM.createCompatibleWritableRaster(w, h),
                                   destCM.isAlphaPremultiplied(), null);
        }

        return image;
    }

    /**
     * Creates a zeroed-destination <code>Raster</code> with the correct
     * size and number of bands, given this source.
     * @param src       the source <code>Raster</code>
     * @return the zeroed-destination <code>Raster</code>.
     */
    public WritableRaster createCompatibleDestRaster (Raster src) {
        return src.createCompatibleWritableRaster(src.getWidth(), src.getHeight());
    }

    /**
     * Returns the location of the destination point given a
     * point in the source.  If dstPt is non-null, it will
     * be used to hold the return value.  Since this is not a geometric
     * operation, the srcPt will equal the dstPt.
     * @param srcPt a point in the source image
     * @param dstPt the destination point or <code>null</code>
     * @return the location of the destination point.
     */
    public final Point2D getPoint2D (Point2D srcPt, Point2D dstPt) {
        if (dstPt == null) {
            dstPt = new Point2D.Float();
        }
        dstPt.setLocation(srcPt.getX(), srcPt.getY());
        return dstPt;
    }

    /**
     * Returns the rendering hints for this op.
     * @return the rendering hints of this <code>RescaleOp</code>.
     */
    public final RenderingHints getRenderingHints() {
        return hints;
    }
}
