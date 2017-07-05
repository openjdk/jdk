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

import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * EffectUtilsTemp - effect utils methods that are not being used for now but we might want later
 *
 * @author Created by Jasper Potts (Jun 18, 2007)
 */
public class EffectUtilsTemp {

    /**
     * Extract the alpha channel of a image into new greyscale buffered image
     *
     * @param src Must but INT_ARGB buffered image
     * @return new TYPE_BYTE_GRAY image of just the alpha channel
     */
    public static BufferedImage extractAlpha(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        // extract image alpha channel as greyscale image
        final BufferedImage greyImg = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2 = greyImg.createGraphics();
        g2.setComposite(new Composite() {
            public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel,
                                                  RenderingHints hints) {
                return new CompositeContext() {
                    public void dispose() {}

                    public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
                        int width = Math.min(src.getWidth(), dstIn.getWidth());
                        int height = Math.min(src.getHeight(), dstIn.getHeight());
                        int[] srcPixels = new int[width];
                        byte[] dstPixels = new byte[width];
                        for (int y = 0; y < height; y++) {
                            src.getDataElements(0, y, width, 1, srcPixels);
                            for (int x = 0; x < width; x++) {
                                dstPixels[x] = (byte) ((srcPixels[x] & 0xFF000000) >>> 24);
                            }
                            dstOut.setDataElements(0, y, width, 1, dstPixels);
                        }
                    }
                };
            }
        });
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return greyImg;
    }

}
