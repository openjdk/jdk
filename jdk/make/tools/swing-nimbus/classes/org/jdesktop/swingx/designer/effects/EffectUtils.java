/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package org.jdesktop.swingx.designer.effects;


/**
 * EffectUtils
 *
 * @author Created by Jasper Potts (Jun 18, 2007)
 */
public class EffectUtils {

    /**
     * <p>Blurs the source pixels into the destination pixels. The force of the blur is specified by the radius which
     * must be greater than 0.</p> <p>The source and destination pixels arrays are expected to be in the BYTE_GREY
     * format.</p> <p>After this method is executed, dstPixels contains a transposed and filtered copy of
     * srcPixels.</p>
     *
     * @param srcPixels the source pixels
     * @param dstPixels the destination pixels
     * @param width     the width of the source picture
     * @param height    the height of the source picture
     * @param kernel    the kernel of the blur effect
     * @param radius    the radius of the blur effect
     */
    public static void blur(byte[] srcPixels, byte[] dstPixels,
                            int width, int height,
                            float[] kernel, int radius) {
        float p;
        int cp;
        for (int y = 0; y < height; y++) {
            int index = y;
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                p = 0.0f;
                for (int i = -radius; i <= radius; i++) {
                    int subOffset = x + i;
                    if (subOffset < 0 || subOffset >= width) {
                        subOffset = (x + width) % width;
                    }
                    int pixel = srcPixels[offset + subOffset] & 0xFF;
                    float blurFactor = kernel[radius + i];
                    p += blurFactor * pixel;
                }
                cp = (int) (p + 0.5f);
                dstPixels[index] = (byte) (cp > 255 ? 255 : cp);
                index += height;
            }
        }
    }

    public static float[] createGaussianKernel(int radius) {
        if (radius < 1) {
            throw new IllegalArgumentException("Radius must be >= 1");
        }

        float[] data = new float[radius * 2 + 1];

        float sigma = radius / 3.0f;
        float twoSigmaSquare = 2.0f * sigma * sigma;
        float sigmaRoot = (float) Math.sqrt(twoSigmaSquare * Math.PI);
        float total = 0.0f;

        for (int i = -radius; i <= radius; i++) {
            float distance = i * i;
            int index = i + radius;
            data[index] = (float) Math.exp(-distance / twoSigmaSquare) / sigmaRoot;
            total += data[index];
        }

        for (int i = 0; i < data.length; i++) {
            data[i] /= total;
        }

        return data;
    }
}
