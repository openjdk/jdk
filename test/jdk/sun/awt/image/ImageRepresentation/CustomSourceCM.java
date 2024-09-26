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

/**
 * @test
 * @bug 4192756
 * @summary Tests that using a non-default colormodel generates correct images under 16/24 bit mode
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.MemoryImageSource;
import java.util.Arrays;

/*
 * NOTE: This bug only appears under specific conditions.  If the background of
 * the surface is red, then you are not running under the conditions necessary
 * to test for the regression so the results of this test will be inconclusive.
 *
 * The test should be run under any of the following screen depths/surfaces:
 *
 * 15-bit, otherwise known as 555 RGB or 32768 (thousands) colors
 * 16-bit, otherwise known as 565 RGB or 65536 (thousands) colors
 * 24-bit, otherwise known as 16777216 (millions) colors
 *
 * The test draws 2 rectangles.  Both rectangles should be half black (left)
 * and half blue (right).  If the top rectangle is all black, the test fails.
 * If the background is red, the results are inconclusive (see above).
*/

public class CustomSourceCM extends Component {

    public static int IMG_W = 80;
    public static int IMG_H = 30;

    static void test(int imageType) {

        int w = IMG_W + 20;
        int h = IMG_H * 2 + 40;
        BufferedImage bi = new BufferedImage(w, h, imageType);

        DirectColorModel dcm;

        /* the next dozen lines or so are intended to help
         * ascertain if the destination surface is of the type
         * that exhibited the original bug, making the background
         * white in those cases. It is not strictly necessary.
         * It is only for a manual tester to be able to tell by looking.
         * The real test is the check for black and blue later on.
         */
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.red);
        g.fillRect(0, 0, w, h);

        ColorModel cm = bi.getColorModel();
        if (cm instanceof ComponentColorModel) {
            g.setColor(Color.white);
            g.fillRect(0, 0, w, h);
        } else if (cm instanceof DirectColorModel) {
            dcm = (DirectColorModel) cm;
            if (dcm.getPixelSize() < 24) {
                g.setColor(Color.white);
                g.fillRect(0, 0, w, h);
            }
        }

        // Construct a ColorModel and data for a 16-bit 565 image...
        dcm = new DirectColorModel(16, 0x1f, 0x7e0, 0xf800);

        // Create an image which is black on the left, blue on the right.
        int[] pixels = new int[IMG_W * IMG_H];
        int blue = dcm.getBlueMask();
        int off = 0;
        for (int y = 0; y < IMG_H; y++) {
            Arrays.fill(pixels, off, off+IMG_W/2, 0);
            Arrays.fill(pixels, off+IMG_W/2, off+IMG_W, blue);
            off += IMG_W;
        }
        MemoryImageSource mis = new MemoryImageSource(IMG_W, IMG_H, dcm,
                                                      pixels, 0, IMG_W);
        CustomSourceCM comp = new CustomSourceCM();
        Image img = comp.createImage(mis);

        // Draw the image on to the surface.
        g.drawImage(img, 10, 10, null);

        // Create a similar effect with 2 fillrects, below the image.
        g.setColor(Color.black);
        g.fillRect(10, 60, IMG_W/2, IMG_H);
        g.setColor(Color.blue);
        g.fillRect(10+IMG_W/2, 60, IMG_W/2, IMG_H);

        // Now sample points in the image to confirm they are the expected color.
        int bluePix = Color.blue.getRGB();
        int blackPix = Color.black.getRGB();
        int black_topLeft = bi.getRGB(10+IMG_W/4, 10+IMG_H/2);
        int blue_topRight = bi.getRGB(10+IMG_W*3/4, 10+IMG_H/2);
        int black_bottomLeft = bi.getRGB(10+IMG_W/4, 60+IMG_H/2);
        int blue_bottomRight = bi.getRGB(10+IMG_W*3/4, 60+IMG_H/2);
        if ((black_topLeft != blackPix) || (black_bottomLeft != blackPix) ||
            (blue_topRight != bluePix) ||  (blue_bottomRight != bluePix)) {

            String fileName = "failed " + imageType + ".png";
            try {
                javax.imageio.ImageIO.write(bi, "png", new java.io.File(fileName));
            } catch (Exception e) { };
            throw new RuntimeException("unexpected colors");
        }
    }

    public static void main(String argv[]) {
        test(BufferedImage.TYPE_USHORT_555_RGB);
        test(BufferedImage.TYPE_USHORT_565_RGB);
        test(BufferedImage.TYPE_3BYTE_BGR);
    }
}
