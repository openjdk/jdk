/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4673490
 * @summary This test verifies that Toolkit images with a 1-bit
 * IndexColorModel (known as ByteBinary) and a transparent index are rendered properly.
 */

import java.awt.Color;
import java.awt.Graphics2D;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;

public class ByteBinaryBitmask {

    public static void main(String argv[]) throws Exception {

        /* Create the image */
        int w = 16, h = 16;
        byte[] bw = { (byte)255, (byte)0, };
        IndexColorModel icm = new IndexColorModel(1, 2, bw, bw, bw, 0);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY, icm);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.black);
        int xoff = 5;
        g2d.fillRect(xoff, 5, 1, 10); // 1 pixel wide

        int dw = 200, dh = 50;
        BufferedImage dest = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dest.createGraphics();
        g.setColor(Color.green);
        g.fillRect(0, 0, dw, dh);
        int x1 = 10;
        int x2 = 50;
        int x3 = 90;
        int x4 = 130;
        g.drawImage(img, x1, 10, null);
        g.drawImage(img, x2, 10, null);
        g.drawImage(img, x3, 10, null);
        g.drawImage(img, x4, 10, null);

        int blackPix = Color.black.getRGB();
        for (int y = 0; y < dh; y++) {
            boolean isBlack = false;
            for (int x = 0; x < dw; x++) {
               int rgb = dest.getRGB(x, y);
               if (rgb == blackPix) {
                   /* Src image has a one pixel wide vertical rect at off "xoff" and
                    * this is drawn at x1/x2/x3/x4) so the sum of those are the x locations
                    * to expect black.
                    */
                   if (x != (x1 + xoff) && x != (x2 + xoff) && x != (x3 + xoff) && x!= (x4 + xoff)) {
                       throw new RuntimeException("wrong x location: " +x);
                   }
                   if (isBlack) {
                       throw new RuntimeException("black after black");
                   }
               }
               isBlack = rgb == blackPix;
            }
        }
    }
}
