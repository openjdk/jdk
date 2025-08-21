/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**********************************************************************
 **********************************************************************
 **********************************************************************
 *** COPYRIGHT (c) Eastman Kodak Company, 1997                      ***
 *** As  an unpublished  work pursuant to Title 17 of the United    ***
 *** States Code.  All rights reserved.                             ***
 **********************************************************************
 **********************************************************************
 **********************************************************************/

package sun.java2d.cmm.lcms;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.lang.ref.Reference;

import sun.awt.AWTAccessor;
import sun.java2d.cmm.ColorTransform;

final class LCMSTransform implements ColorTransform {

    private static final class NativeTransform {
        private long ID;
        private int inFormatter;
        private int outFormatter;

        private boolean match(LCMSImageLayout in, LCMSImageLayout out) {
            return inFormatter == in.pixelType && outFormatter == out.pixelType;
        }
    }

    private volatile NativeTransform transform;
    private final LCMSProfile[] lcmsProfiles;
    private final int renderingIntent;

    private final int numInComponents;
    private final int numOutComponents;

    /**
     * Creates a transform based on the list of profiles and rendering intent.
     * The first profile will be considered input, and the last profile will be
     * considered output.
     *
     * @param  renderingIntent the desired rendering intent, could be ignored if
     *         unsupported. If the {@link ColorTransform#Any} is passed then we
     *         will try to use {@code ICC_Profile.icPerceptual} intent.
     * @param  profiles the list of color profiles
     */
    LCMSTransform(int renderingIntent, ICC_Profile... profiles) {
        var acc = AWTAccessor.getICC_ProfileAccessor();
        lcmsProfiles = new LCMSProfile[profiles.length];
        for (int i = 0; i < profiles.length; i++) {
            lcmsProfiles[i] = LCMS.getLcmsProfile(acc.cmmProfile(profiles[i]));
            profiles[i].getMajorVersion(); // force header initialization
        }
        this.renderingIntent = (renderingIntent == ColorTransform.Any) ?
                ICC_Profile.icPerceptual : renderingIntent;
        // Note that ICC_Profile.getNumComponents() is quite expensive (it may
        // result in a reading of the profile header). So, here we cache the
        // number of components of input and output profiles for further usage.
        numInComponents = profiles[0].getNumComponents();
        numOutComponents = profiles[profiles.length - 1].getNumComponents();
    }

    public int getNumInComponents() {
        return numInComponents;
    }

    public int getNumOutComponents() {
        return numOutComponents;
    }

    private void doTransform(LCMSImageLayout in, LCMSImageLayout out) {
        NativeTransform tfm = transform;
        // update native transform if needed
        if (tfm == null || !tfm.match(in, out)) {
            synchronized (this) {
                tfm = transform;
                if (tfm == null || !tfm.match(in, out)) {
                    tfm = new NativeTransform();
                    tfm.inFormatter = in.pixelType;
                    tfm.outFormatter = out.pixelType;
                    tfm.ID = LCMS.createTransform(lcmsProfiles, renderingIntent,
                                                  tfm.inFormatter,
                                                  tfm.outFormatter, tfm);
                    // Disposer will destroy forgotten transform
                    transform = tfm;
                }
            }
        }
        LCMS.colorConvert(tfm.ID, in.width, in.height, in.offset,
                          in.nextRowOffset, out.offset, out.nextRowOffset,
                          in.dataArray, out.dataArray,
                          in.dataType, out.dataType);
        Reference.reachabilityFence(tfm); // prevent deallocation of "tfm.ID"
    }

    /**
     * Returns {@code true} if lcms may support this format directly.
     */
    private static boolean isLCMSSupport(BufferedImage src, BufferedImage dst) {
        boolean dstAlpha = dst.getColorModel().hasAlpha();
        boolean srcAlpha = src.getColorModel().hasAlpha();
        boolean srcPre = srcAlpha && src.getColorModel().isAlphaPremultiplied();
        // lcms does not convert pre-alpha for transparent src if dst is opaque
        // lcms does not set correct alpha for transparent dst if src is opaque
        return !dstAlpha && !srcPre || dstAlpha == srcAlpha;
    }

    public void colorConvert(BufferedImage src, BufferedImage dst) {
        LCMSImageLayout srcIL, dstIL;
        if (isLCMSSupport(src, dst)) {
            dstIL = LCMSImageLayout.createImageLayout(dst);

            if (dstIL != null) {
                srcIL = LCMSImageLayout.createImageLayout(src);
                if (srcIL != null) {
                    doTransform(srcIL, dstIL);
                    return;
                }
            }
        }
        Raster srcRas = src.getRaster();
        WritableRaster dstRas = dst.getRaster();
        ColorModel srcCM = src.getColorModel();
        ColorModel dstCM = dst.getColorModel();
        int w = src.getWidth();
        int h = src.getHeight();
        int srcNumComp = srcCM.getNumColorComponents();
        int dstNumComp = dstCM.getNumColorComponents();
        int precision = 8;
        float maxNum = 255.0f;
        for (int i = 0; i < srcNumComp; i++) {
            if (srcCM.getComponentSize(i) > 8) {
                 precision = 16;
                 maxNum = 65535.0f;
             }
        }
        for (int i = 0; i < dstNumComp; i++) {
            if (dstCM.getComponentSize(i) > 8) {
                 precision = 16;
                 maxNum = 65535.0f;
             }
        }
        float[] srcMinVal = new float[srcNumComp];
        float[] srcInvDiffMinMax = new float[srcNumComp];
        ColorSpace cs = srcCM.getColorSpace();
        for (int i = 0; i < srcNumComp; i++) {
            srcMinVal[i] = cs.getMinValue(i);
            srcInvDiffMinMax[i] = maxNum / (cs.getMaxValue(i) - srcMinVal[i]);
        }
        cs = dstCM.getColorSpace();
        float[] dstMinVal = new float[dstNumComp];
        float[] dstDiffMinMax = new float[dstNumComp];
        for (int i = 0; i < dstNumComp; i++) {
            dstMinVal[i] = cs.getMinValue(i);
            dstDiffMinMax[i] = (cs.getMaxValue(i) - dstMinVal[i]) / maxNum;
        }
        boolean dstHasAlpha = dstCM.hasAlpha();
        boolean needSrcAlpha = srcCM.hasAlpha() && dstHasAlpha;
        float[] dstColor;
        if (dstHasAlpha) {
            dstColor = new float[dstNumComp + 1];
        } else {
            dstColor = new float[dstNumComp];
        }
        if (precision == 8) {
            byte[] srcLine = new byte[w * srcNumComp];
            byte[] dstLine = new byte[w * dstNumComp];
            Object pixel;
            float[] color;
            float[] alpha = null;
            if (needSrcAlpha) {
                alpha = new float[w];
            }
            int idx;
            // TODO check for src npixels = dst npixels
            srcIL = new LCMSImageLayout(srcLine, getNumInComponents());
            dstIL = new LCMSImageLayout(dstLine, getNumOutComponents());
            // process each scanline
            for (int y = 0; y < h; y++) {
                // convert src scanline
                pixel = null;
                color = null;
                idx = 0;
                for (int x = 0; x < w; x++) {
                    pixel = srcRas.getDataElements(x, y, pixel);
                    color = srcCM.getNormalizedComponents(pixel, color, 0);
                    for (int i = 0; i < srcNumComp; i++) {
                        srcLine[idx++] = (byte)
                            ((color[i] - srcMinVal[i]) * srcInvDiffMinMax[i] +
                             0.5f);
                    }
                    if (needSrcAlpha) {
                        alpha[x] = color[srcNumComp];
                    }
                }
                // color convert srcLine to dstLine
                doTransform(srcIL, dstIL);

                // convert dst scanline
                pixel = null;
                idx = 0;
                for (int x = 0; x < w; x++) {
                    for (int i = 0; i < dstNumComp; i++) {
                        dstColor[i] = ((float) (dstLine[idx++] & 0xff)) *
                                      dstDiffMinMax[i] + dstMinVal[i];
                    }
                    if (needSrcAlpha) {
                        dstColor[dstNumComp] = alpha[x];
                    } else if (dstHasAlpha) {
                        dstColor[dstNumComp] = 1.0f;
                    }
                    pixel = dstCM.getDataElements(dstColor, 0, pixel);
                    dstRas.setDataElements(x, y, pixel);
                }
            }
        } else {
            short[] srcLine = new short[w * srcNumComp];
            short[] dstLine = new short[w * dstNumComp];
            Object pixel;
            float[] color;
            float[] alpha = null;
            if (needSrcAlpha) {
                alpha = new float[w];
            }
            int idx;
            srcIL = new LCMSImageLayout(srcLine, getNumInComponents());
            dstIL = new LCMSImageLayout(dstLine, getNumOutComponents());
            // process each scanline
            for (int y = 0; y < h; y++) {
                // convert src scanline
                pixel = null;
                color = null;
                idx = 0;
                for (int x = 0; x < w; x++) {
                    pixel = srcRas.getDataElements(x, y, pixel);
                    color = srcCM.getNormalizedComponents(pixel, color, 0);
                    for (int i = 0; i < srcNumComp; i++) {
                        srcLine[idx++] = (short)
                            ((color[i] - srcMinVal[i]) * srcInvDiffMinMax[i] +
                             0.5f);
                    }
                    if (needSrcAlpha) {
                        alpha[x] = color[srcNumComp];
                    }
                }
                // color convert srcLine to dstLine
                doTransform(srcIL, dstIL);

                // convert dst scanline
                pixel = null;
                idx = 0;
                for (int x = 0; x < w; x++) {
                    for (int i = 0; i < dstNumComp; i++) {
                        dstColor[i] = ((float) (dstLine[idx++] & 0xffff)) *
                                      dstDiffMinMax[i] + dstMinVal[i];
                    }
                    if (needSrcAlpha) {
                        dstColor[dstNumComp] = alpha[x];
                    } else if (dstHasAlpha) {
                        dstColor[dstNumComp] = 1.0f;
                    }
                    pixel = dstCM.getDataElements(dstColor, 0, pixel);
                    dstRas.setDataElements(x, y, pixel);
                }
            }
        }
    }

    public void colorConvert(Raster src, WritableRaster dst,
                             float[] srcMinVal, float[]srcMaxVal,
                             float[] dstMinVal, float[]dstMaxVal) {
        LCMSImageLayout srcIL, dstIL;

        // Can't pass src and dst directly to CMM, so process per scanline
        SampleModel srcSM = src.getSampleModel();
        SampleModel dstSM = dst.getSampleModel();
        int srcTransferType = src.getTransferType();
        int dstTransferType = dst.getTransferType();
        boolean srcIsFloat, dstIsFloat;
        if ((srcTransferType == DataBuffer.TYPE_FLOAT) ||
            (srcTransferType == DataBuffer.TYPE_DOUBLE)) {
            srcIsFloat = true;
        } else {
            srcIsFloat = false;
        }
        if ((dstTransferType == DataBuffer.TYPE_FLOAT) ||
            (dstTransferType == DataBuffer.TYPE_DOUBLE)) {
            dstIsFloat = true;
        } else {
            dstIsFloat = false;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        int srcNumBands = src.getNumBands();
        int dstNumBands = dst.getNumBands();
        float[] srcScaleFactor = new float[srcNumBands];
        float[] dstScaleFactor = new float[dstNumBands];
        float[] srcUseMinVal = new float[srcNumBands];
        float[] dstUseMinVal = new float[dstNumBands];
        for (int i = 0; i < srcNumBands; i++) {
            if (srcIsFloat) {
                srcScaleFactor[i] =  65535.0f / (srcMaxVal[i] - srcMinVal[i]);
                srcUseMinVal[i] = srcMinVal[i];
            } else {
                if (srcTransferType == DataBuffer.TYPE_SHORT) {
                    srcScaleFactor[i] = 65535.0f / 32767.0f;
                } else {
                    srcScaleFactor[i] = 65535.0f /
                        ((float) ((1 << srcSM.getSampleSize(i)) - 1));
                }
                srcUseMinVal[i] = 0.0f;
            }
        }
        for (int i = 0; i < dstNumBands; i++) {
            if (dstIsFloat) {
                dstScaleFactor[i] = (dstMaxVal[i] - dstMinVal[i]) / 65535.0f;
                dstUseMinVal[i] = dstMinVal[i];
            } else {
                if (dstTransferType == DataBuffer.TYPE_SHORT) {
                    dstScaleFactor[i] = 32767.0f / 65535.0f;
                } else {
                    dstScaleFactor[i] =
                        ((float) ((1 << dstSM.getSampleSize(i)) - 1)) /
                        65535.0f;
                }
                dstUseMinVal[i] = 0.0f;
            }
        }
        int ys = src.getMinY();
        int yd = dst.getMinY();
        int xs, xd;
        float sample;
        short[] srcLine = new short[w * srcNumBands];
        short[] dstLine = new short[w * dstNumBands];
        int idx;
        srcIL = new LCMSImageLayout(srcLine, getNumInComponents());
        dstIL = new LCMSImageLayout(dstLine, getNumOutComponents());
        // process each scanline
        for (int y = 0; y < h; y++, ys++, yd++) {
            // get src scanline
            xs = src.getMinX();
            idx = 0;
            for (int x = 0; x < w; x++, xs++) {
                for (int i = 0; i < srcNumBands; i++) {
                    sample = src.getSampleFloat(xs, ys, i);
                    srcLine[idx++] = (short)
                        ((sample - srcUseMinVal[i]) * srcScaleFactor[i] + 0.5f);
                }
            }

            // color convert srcLine to dstLine
            doTransform(srcIL, dstIL);

            // store dst scanline
            xd = dst.getMinX();
            idx = 0;
            for (int x = 0; x < w; x++, xd++) {
                for (int i = 0; i < dstNumBands; i++) {
                    sample = ((dstLine[idx++] & 0xffff) * dstScaleFactor[i]) +
                             dstUseMinVal[i];
                    dst.setSample(xd, yd, i, sample);
                }
            }
        }
    }

    public void colorConvert(Raster src, WritableRaster dst) {

        LCMSImageLayout srcIL, dstIL;
        dstIL = LCMSImageLayout.createImageLayout(dst, null);
        if (dstIL != null) {
            srcIL = LCMSImageLayout.createImageLayout(src, null);
            if (srcIL != null) {
                doTransform(srcIL, dstIL);
                return;
            }
        }
        // Can't pass src and dst directly to CMM, so process per scanline
        SampleModel srcSM = src.getSampleModel();
        SampleModel dstSM = dst.getSampleModel();
        int srcTransferType = src.getTransferType();
        int dstTransferType = dst.getTransferType();
        int w = src.getWidth();
        int h = src.getHeight();
        int srcNumBands = src.getNumBands();
        int dstNumBands = dst.getNumBands();
        int precision = 8;
        float maxNum = 255.0f;
        for (int i = 0; i < srcNumBands; i++) {
            if (srcSM.getSampleSize(i) > 8) {
                 precision = 16;
                 maxNum = 65535.0f;
             }
        }
        for (int i = 0; i < dstNumBands; i++) {
            if (dstSM.getSampleSize(i) > 8) {
                 precision = 16;
                 maxNum = 65535.0f;
             }
        }
        float[] srcScaleFactor = new float[srcNumBands];
        float[] dstScaleFactor = new float[dstNumBands];
        for (int i = 0; i < srcNumBands; i++) {
            if (srcTransferType == DataBuffer.TYPE_SHORT) {
                srcScaleFactor[i] = maxNum / 32767.0f;
            } else {
                srcScaleFactor[i] = maxNum /
                    ((float) ((1 << srcSM.getSampleSize(i)) - 1));
            }
        }
        for (int i = 0; i < dstNumBands; i++) {
            if (dstTransferType == DataBuffer.TYPE_SHORT) {
                dstScaleFactor[i] = 32767.0f / maxNum;
            } else {
                dstScaleFactor[i] =
                    ((float) ((1 << dstSM.getSampleSize(i)) - 1)) / maxNum;
            }
        }
        int ys = src.getMinY();
        int yd = dst.getMinY();
        int xs, xd;
        int sample;
        if (precision == 8) {
            byte[] srcLine = new byte[w * srcNumBands];
            byte[] dstLine = new byte[w * dstNumBands];
            int idx;
            // TODO check for src npixels = dst npixels
            srcIL = new LCMSImageLayout(srcLine, getNumInComponents());
            dstIL = new LCMSImageLayout(dstLine, getNumOutComponents());
            // process each scanline
            for (int y = 0; y < h; y++, ys++, yd++) {
                // get src scanline
                xs = src.getMinX();
                idx = 0;
                for (int x = 0; x < w; x++, xs++) {
                    for (int i = 0; i < srcNumBands; i++) {
                        sample = src.getSample(xs, ys, i);
                        srcLine[idx++] = (byte)
                            ((sample * srcScaleFactor[i]) + 0.5f);
                    }
                }

                // color convert srcLine to dstLine
                doTransform(srcIL, dstIL);

                // store dst scanline
                xd = dst.getMinX();
                idx = 0;
                for (int x = 0; x < w; x++, xd++) {
                    for (int i = 0; i < dstNumBands; i++) {
                        sample = (int) (((dstLine[idx++] & 0xff) *
                                         dstScaleFactor[i]) + 0.5f);
                        dst.setSample(xd, yd, i, sample);
                    }
                }
            }
        } else {
            short[] srcLine = new short[w * srcNumBands];
            short[] dstLine = new short[w * dstNumBands];
            int idx;
            srcIL = new LCMSImageLayout(srcLine, getNumInComponents());
            dstIL = new LCMSImageLayout(dstLine, getNumOutComponents());
            // process each scanline
            for (int y = 0; y < h; y++, ys++, yd++) {
                // get src scanline
                xs = src.getMinX();
                idx = 0;
                for (int x = 0; x < w; x++, xs++) {
                    for (int i = 0; i < srcNumBands; i++) {
                        sample = src.getSample(xs, ys, i);
                        srcLine[idx++] = (short)
                            ((sample * srcScaleFactor[i]) + 0.5f);
                    }
                }

                // color convert srcLine to dstLine
                doTransform(srcIL, dstIL);

                // store dst scanline
                xd = dst.getMinX();
                idx = 0;
                for (int x = 0; x < w; x++, xd++) {
                    for (int i = 0; i < dstNumBands; i++) {
                        sample = (int) (((dstLine[idx++] & 0xffff) *
                                         dstScaleFactor[i]) + 0.5f);
                        dst.setSample(xd, yd, i, sample);
                    }
                }
            }
        }
    }

    /* convert an array of colors in short format */
    /* each color is a contiguous set of array elements */
    /* the number of colors is (size of the array) / (number of input/output
       components */
    public short[] colorConvert(short[] src, short[] dst) {
        if (dst == null) {
            dst = new short[(src.length / numInComponents) * numOutComponents];
        }
        doTransform(new LCMSImageLayout(src, numInComponents),
                    new LCMSImageLayout(dst, numOutComponents));
        return dst;
    }

    public byte[] colorConvert(byte[] src, byte[] dst) {
        if (dst == null) {
            dst = new byte[(src.length / numInComponents) * numOutComponents];
        }
        doTransform(new LCMSImageLayout(src, numInComponents),
                    new LCMSImageLayout(dst, numOutComponents));

        return dst;
    }
}
