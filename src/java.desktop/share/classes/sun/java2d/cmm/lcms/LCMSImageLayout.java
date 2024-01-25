/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.cmm.lcms;

import java.awt.color.CMMException;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.Raster;
import java.lang.annotation.Native;
import java.nio.ByteOrder;

import sun.awt.image.ByteComponentRaster;
import sun.awt.image.IntegerComponentRaster;
import sun.awt.image.ShortComponentRaster;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

final class LCMSImageLayout {

    private static int BYTES_SH(int x) {
        return x;
    }

    private static int EXTRA_SH(int x) {
        return x << 7;
    }

    private static int CHANNELS_SH(int x) {
        return x << 3;
    }

    private static final int PREMUL     = 1 << 23;
    private static final int SWAPFIRST  = 1 << 14;
    private static final int DOSWAP     = 1 << 10;
    private static final int PT_GRAY_8        = CHANNELS_SH(1) | BYTES_SH(1);
    private static final int PT_GRAY_16       = CHANNELS_SH(1) | BYTES_SH(2);
    private static final int PT_RGB_8         = CHANNELS_SH(3) | BYTES_SH(1);
    private static final int PT_RGBA_8        = PT_RGB_8  | EXTRA_SH(1);
    private static final int PT_ARGB_8        = PT_RGBA_8 | SWAPFIRST;
    private static final int PT_ARGB_8_PREMUL = PT_ARGB_8 | PREMUL;
    private static final int PT_BGR_8         = PT_RGB_8  | DOSWAP;
    private static final int PT_ABGR_8        = PT_BGR_8  | EXTRA_SH(1);
    private static final int PT_ABGR_8_PREMUL = PT_ABGR_8 | PREMUL;
//  private static final int PT_BGRA_8        = PT_ABGR_8 | SWAPFIRST;
    private static final int SWAP_ENDIAN =
            ByteOrder.nativeOrder() == LITTLE_ENDIAN ? DOSWAP : 0;
    @Native
    private static final int DT_BYTE = 0;
    @Native
    private static final int DT_SHORT = 1;
    @Native
    private static final int DT_INT = 2;
    int pixelType;
    int dataType;
    int width;
    int height;
    int nextRowOffset;
    private int nextPixelOffset;
    int offset;

    Object dataArray;

    private int dataArrayLength; /* in bytes */

    /**
     * Creates a layout object for given parameters.
     *
     * @param  data the storage of pixels: {@code byte[], short[] or int[]}
     * @param  length the length of the data array
     * @param  nc the number of color components
     * @param  dt the type of data array: DT_BYTE, DT_SHORT or DT_INT
     * @param  size the size of one color component in bytes
     */
    private LCMSImageLayout(Object data, int length, int nc, int dt, int size) {
        dataArray = data;
        dataType = dt;
        dataArrayLength = length * size;
        pixelType = CHANNELS_SH(nc) | BYTES_SH(size);
        width = length / nc;
        height = 1;
        nextPixelOffset = nc * size;
        nextRowOffset = dataArrayLength; // outside of data since height is 1
        verify();
    }

    LCMSImageLayout(byte[] data, int nc) {
        this(data, data.length, nc, DT_BYTE, Byte.BYTES);
    }

    LCMSImageLayout(short[] data, int nc) {
        this(data, data.length, nc, DT_SHORT, Short.BYTES);
    }

    private LCMSImageLayout() {
    }

    /* This method creates a layout object for given image.
     * Returns null if the image is not supported by current implementation.
     */
    static LCMSImageLayout createImageLayout(BufferedImage image) {
        LCMSImageLayout l = new LCMSImageLayout();

        switch (image.getType()) {
            case BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB:
                l.pixelType = PT_ARGB_8 ^ SWAP_ENDIAN;
                break;
            case BufferedImage.TYPE_INT_ARGB_PRE:
                l.pixelType = PT_ARGB_8_PREMUL ^ SWAP_ENDIAN;
                break;
            case BufferedImage.TYPE_INT_BGR:
                l.pixelType = PT_ABGR_8 ^ SWAP_ENDIAN;
                break;
            case BufferedImage.TYPE_3BYTE_BGR:
                l.pixelType = PT_BGR_8;
                break;
            case BufferedImage.TYPE_4BYTE_ABGR:
                l.pixelType = PT_ABGR_8;
                break;
            case BufferedImage.TYPE_4BYTE_ABGR_PRE:
                l.pixelType = PT_ABGR_8_PREMUL;
                break;
            case BufferedImage.TYPE_BYTE_GRAY:
                l.pixelType = PT_GRAY_8;
                break;
            case BufferedImage.TYPE_USHORT_GRAY:
                l.pixelType = PT_GRAY_16;
                break;
            default:
                /* ColorConvertOp creates component images as
                 * default destination, so this kind of images
                 * has to be supported.
                 */
                ColorModel cm = image.getColorModel();
                if (cm instanceof ComponentColorModel ccm) {
                    // verify whether the component size is fine
                    int[] cs = ccm.getComponentSize();
                    for (int s : cs) {
                        if (s != 8) {
                            return null;
                        }
                    }
                    return createImageLayout(image.getRaster(), cm);
                }
                return null;
        }

        l.width = image.getWidth();
        l.height = image.getHeight();

        switch (image.getType()) {
            case BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB,
                 BufferedImage.TYPE_INT_ARGB_PRE, BufferedImage.TYPE_INT_BGR ->
            {
                var intRaster = (IntegerComponentRaster) image.getRaster();
                l.nextRowOffset = safeMult(4, intRaster.getScanlineStride());
                l.nextPixelOffset = safeMult(4, intRaster.getPixelStride());
                l.offset = safeMult(4, intRaster.getDataOffset(0));
                l.dataArray = intRaster.getDataStorage();
                l.dataArrayLength = 4 * intRaster.getDataStorage().length;
                l.dataType = DT_INT;
            }
            case BufferedImage.TYPE_BYTE_GRAY, BufferedImage.TYPE_3BYTE_BGR,
                 BufferedImage.TYPE_4BYTE_ABGR,
                 BufferedImage.TYPE_4BYTE_ABGR_PRE ->
            {
                var byteRaster = (ByteComponentRaster) image.getRaster();
                l.nextRowOffset = byteRaster.getScanlineStride();
                l.nextPixelOffset = byteRaster.getPixelStride();
                int firstBand = byteRaster.getSampleModel().getNumBands() - 1;
                l.offset = byteRaster.getDataOffset(firstBand);
                l.dataArray = byteRaster.getDataStorage();
                l.dataArrayLength = byteRaster.getDataStorage().length;
                l.dataType = DT_BYTE;
            }
            case BufferedImage.TYPE_USHORT_GRAY -> {
                var shortRaster = (ShortComponentRaster) image.getRaster();
                l.nextRowOffset = safeMult(2, shortRaster.getScanlineStride());
                l.nextPixelOffset = safeMult(2, shortRaster.getPixelStride());
                l.offset = safeMult(2, shortRaster.getDataOffset(0));
                l.dataArray = shortRaster.getDataStorage();
                l.dataArrayLength = 2 * shortRaster.getDataStorage().length;
                l.dataType = DT_SHORT;
            }
            default -> {
                return null;
            }
        }
        l.verify();
        return l;
    }

    private enum BandOrder {
        DIRECT,
        INVERTED,
        ARBITRARY,
        UNKNOWN;

        static BandOrder getBandOrder(int[] bandOffsets) {
            BandOrder order = UNKNOWN;

            int numBands = bandOffsets.length;

            for (int i = 0; (order != ARBITRARY) && (i < bandOffsets.length); i++) {
                switch (order) {
                    case UNKNOWN:
                        if (bandOffsets[i] == i) {
                            order = DIRECT;
                        } else if (bandOffsets[i] == (numBands - 1 - i)) {
                            order = INVERTED;
                        } else {
                            order = ARBITRARY;
                        }
                        break;
                    case DIRECT:
                        if (bandOffsets[i] != i) {
                            order = ARBITRARY;
                        }
                        break;
                    case INVERTED:
                        if (bandOffsets[i] != (numBands - 1 - i)) {
                            order = ARBITRARY;
                        }
                        break;
                }
            }
            return order;
        }
    }

    private void verify() {
        checkIndex(offset, dataArrayLength);
        if (nextPixelOffset != getBytesPerPixel(pixelType)) {
            throw new CMMException("Invalid image layout");
        }

        int lastScanOffset = safeMult(nextRowOffset, (height - 1));
        int lastPixelOffset = safeMult(nextPixelOffset, (width -1 ));
        long off = (long) offset + lastPixelOffset + lastScanOffset;

        checkIndex(off, dataArrayLength);
    }

    private static int checkIndex(long index, int length) {
        if (index < 0 || index >= length) {
            throw new CMMException("Invalid image layout");
        }
        return (int) index;
    }

    private static int safeMult(int a, int b) {
        long res = (long) a * b;
        return checkIndex(res, Integer.MAX_VALUE);
    }

    static LCMSImageLayout createImageLayout(Raster r, ColorModel cm) {
        LCMSImageLayout l = new LCMSImageLayout();
        if (r instanceof ByteComponentRaster br &&
                r.getSampleModel() instanceof ComponentSampleModel csm)
        {
            int numBands = br.getNumBands();
            boolean hasAlpha = cm != null && cm.hasAlpha();
            l.pixelType = (hasAlpha ? CHANNELS_SH(numBands - 1) | EXTRA_SH(1)
                                    : CHANNELS_SH(numBands)) | BYTES_SH(1);
            if (hasAlpha && cm.isAlphaPremultiplied()) {
                l.pixelType |= PREMUL;
            }

            int[] bandOffsets = csm.getBandOffsets();
            BandOrder order = BandOrder.getBandOrder(bandOffsets);

            int firstBand = 0;
            switch (order) {
                case INVERTED:
                    l.pixelType |= DOSWAP;
                    firstBand  = numBands - 1;
                    break;
                case DIRECT:
                    // do nothing
                    break;
                default:
                    // unable to create the image layout;
                    return null;
            }

            l.nextRowOffset = br.getScanlineStride();
            l.nextPixelOffset = br.getPixelStride();

            l.offset = br.getDataOffset(firstBand);
            l.dataType = DT_BYTE;
            byte[] data = br.getDataStorage();
            l.dataArray = data;
            l.dataArrayLength = data.length;

            l.width = br.getWidth();
            l.height = br.getHeight();
            l.verify();
            return l;
        }
        return null;
    }

    /**
     * Derives number of bytes per pixel from the pixel format.
     * Following bit fields are used here:
     *  [0..2] - bytes per sample
     *  [3..6] - number of color samples per pixel
     *  [7..9] - number of non-color samples per pixel
     *
     * A complete description of the pixel format can be found
     * here: lcms2.h, lines 651 - 667.
     *
     * @param pixelType pixel format in lcms2 notation.
     * @return number of bytes per pixel for given pixel format.
     */
    private static int getBytesPerPixel(int pixelType) {
        int bytesPerSample = (0x7 & pixelType);
        int colorSamplesPerPixel = 0xF & (pixelType >> 3);
        int extraSamplesPerPixel = 0x7 & (pixelType >> 7);

        return bytesPerSample * (colorSamplesPerPixel + extraSamplesPerPixel);
    }
}
