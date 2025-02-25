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
 * @bug 4203598
 * @summary This test verifies that an image with transparent background can be displayed
 *          correctly with the red background color given.
 *           The correct display should be the sleeping Duke on a red background.
 *
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ImageRendering {

    public static void main(String[] args) throws Exception {

        String imgName = "snooze.gif";
        File file = new File(System.getProperty("test.src", "."), imgName);
        BufferedImage image = ImageIO.read(file);
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = dest.createGraphics();
        g2d.drawImage(image, 0, 0, Color.red, null);
        int redPixel = Color.red.getRGB();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int srcPixel = image.getRGB(x, y);
                if ((srcPixel & 0x0ff000000) == 0) {
                    int destPix = dest.getRGB(x, y);
                    if (destPix != redPixel) {
                        throw new RuntimeException("Not red at x=" + x +
                               " y=" + y +
                               "pix = " + Integer.toHexString(destPix));
                    }
                }
            }
       }
    }
}
