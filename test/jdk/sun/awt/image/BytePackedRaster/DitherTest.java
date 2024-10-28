/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 4184283
 * @summary Checks rendering of dithered byte packed image does not crash.
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;
import java.awt.image.WritableRaster;

public class DitherTest extends Component {

    final static int NOOP = 0;
    final static int RED = 1;
    final static int GREEN = 2;
    final static int BLUE = 3;
    final static int ALPHA = 4;
    final static int SATURATION = 5;

    final static byte red[] = {(byte)0, (byte)132, (byte)0, (byte)132, (byte)0, (byte)132,
                         (byte)0, (byte)198, (byte)198, (byte)165, (byte)255, (byte)165, (byte)132,
                         (byte)255, (byte)0, (byte)255};

    final static byte green[] = {(byte)0, (byte)0, (byte)130, (byte)130, (byte)0,
                           (byte)0, (byte)130, (byte)195, (byte)223, (byte)203, (byte)251, (byte)162,
                           (byte)132, (byte)0, (byte)255, (byte)255};

    final static byte blue[] = {(byte)0, (byte)0, (byte)0, (byte)0, (byte)132, (byte)132,
                          (byte)132, (byte)198, (byte)198, (byte)247, (byte)247, (byte)165, (byte)132,
                          (byte)0, (byte)0, (byte)0};

    static IndexColorModel cm16 = new IndexColorModel( 4, 16, red, green, blue);


    public static void main(String args[]) {

        int imageWidth = 256;
        int imageHeight = 256;
        WritableRaster raster = cm16.createCompatibleWritableRaster(imageWidth, imageHeight);
        BufferedImage intermediateImage = new BufferedImage(cm16, raster, false, null);
        Image calculatedImage = calculateImage();

        Graphics2D ig = intermediateImage.createGraphics();
        // Clear background and fill a red rectangle just to prove that we can draw on intermediateImage
        ig.setColor(Color.white);
        ig.fillRect(0,0,imageWidth,imageHeight);
        ig.drawImage(calculatedImage, 0, 0, imageWidth, imageHeight, null);
        ig.setColor(Color.red);
        ig.fillRect(0,0,5,5);

        BufferedImage destImage = new BufferedImage(imageWidth, imageWidth, BufferedImage.TYPE_INT_RGB);
        Graphics2D dg = destImage.createGraphics();
        dg.drawImage(intermediateImage, 0, 0, imageWidth, imageHeight, null);
    }

    private static void applymethod(int c[], int method, int step, int total, int vals[]) {
        if (method == NOOP)
            return;
        int val = ((total < 2)
                   ? vals[0]
                   : vals[0] + ((vals[1] - vals[0]) * step / (total - 1)));
        switch (method) {
        case RED:
            c[0] = val;
            break;
        case GREEN:
            c[1] = val;
            break;
        case BLUE:
            c[2] = val;
            break;
        case ALPHA:
            c[3] = val;
            break;
        case SATURATION:
            int max = Math.max(Math.max(c[0], c[1]), c[2]);
            int min = max * (255 - val) / 255;
            if (c[0] == 0) c[0] = min;
            if (c[1] == 0) c[1] = min;
            if (c[2] == 0) c[2] = min;
            break;
        }
    }

    private static Image calculateImage() {

        int xvals[] = { 0, 255 };
        int yvals[] = { 0, 255 };
        int xmethod = RED;
        int ymethod = BLUE;
        int width = 256;
        int height = 256;
        int pixels[] = new int[width * height];
        int c[] = new int[4];
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                c[0] = c[1] = c[2] = 0;
                c[3] = 255;
                if (xmethod < ymethod) {
                    applymethod(c, xmethod, i, width, xvals);
                    applymethod(c, ymethod, j, height, yvals);
                } else {
                    applymethod(c, ymethod, j, height, yvals);
                    applymethod(c, xmethod, i, width, xvals);
                }
                pixels[index++] = ((c[3] << 24) |
                                   (c[0] << 16) |
                                   (c[1] << 8) |
                                   (c[2] << 0));
            }
        }

        DitherTest dt = new DitherTest();
        return dt.createImage(new MemoryImageSource(width, height, ColorModel.getRGBdefault(), pixels, 0, width));
    }
}

