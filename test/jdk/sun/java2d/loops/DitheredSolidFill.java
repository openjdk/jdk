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
 * @bug 4181172
 * @summary Confirm that solid white fill is not dithered on an 8-bit indexed surface.
 *          The test draws two areas filled with white solid color.
 *          The upper left square is filled in aliasing mode and
 *          the lower right square is filled in anti-aliasing mode.
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class DitheredSolidFill {

    public static void main(String args[]) {
       BufferedImage bi = new BufferedImage(120, 120, BufferedImage.TYPE_BYTE_INDEXED);
       Graphics2D g2D = bi.createGraphics();

        g2D.setColor(Color.black);
        g2D.fillRect(0, 0, 100, 100);

        g2D.setColor(Color.white);
        g2D.fillRect(5, 5, 40, 40);
        checkPixels(bi, 5, 5, 40, 40);

        g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2D.fillRect(55, 55, 40, 40);
        checkPixels(bi, 55, 55, 40, 40);
    }

    static void checkPixels(BufferedImage bi, int x, int y, int w, int h) {
       // pixel can be off white, but must be the same in all cases.
       int expectedPix = bi.getRGB(x, y);
       for (int x0 = x; x0 < x + w; x0++) {
           for (int y0 = y; y0 < y + h; y0++) {
              if (bi.getRGB(x0, y0) != expectedPix) {
                  try {
                      javax.imageio.ImageIO.write(bi, "png", new java.io.File("failed.png"));
                  } catch (Exception e) {
                  }
                  throw new RuntimeException("Not expected pix : " +
                                             Integer.toHexString(bi.getRGB(x0, y0)) +
                                             " at " + x0 + "," + y0);
              }
           }
       }
   }
}
