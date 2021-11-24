/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8262297
 * @summary Test that writing invalid bit per pixel image throws IOException
 */

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class BMPBitsPerPixelTest {

    public static void main(String[] args) {
        test(1, false);
        test(2, true);
        test(3, true);
        test(4, false);
        test(5, true);
        test(6, true);
        test(7, true);
        test(8, false);
    }

    public static void test(int bpp, boolean shouldThrowException) {
        int palettes = (int)Math.pow(2, bpp);
        byte[] r = new byte[palettes];
        byte[] g = new byte[palettes];
        byte[] b = new byte[palettes];
        boolean exceptionThrown = false;
        try {
            IndexColorModel cm = new IndexColorModel(bpp, palettes, r, g, b);
            int imageType = BufferedImage.TYPE_BYTE_BINARY;
            if (bpp > 4) {
                imageType = BufferedImage.TYPE_BYTE_INDEXED;
            }
            BufferedImage img = new BufferedImage(10, 10, imageType, (IndexColorModel)cm);
            File file = File.createTempFile("test", ".bmp", new File("."));
            file.deleteOnExit();
            ImageIO.write(img, "BMP", file);
        } catch (IOException e) {
            exceptionThrown = true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Unexpected exception: " + e);
        }

        if (shouldThrowException && !exceptionThrown) {
            throw new RuntimeException("IOException was not caught.");
        } else if (!shouldThrowException && exceptionThrown) {
            throw new RuntimeException("IOException should not be thrown.");
        } else {
            System.out.println("Test PASSED.");
        }
    }
}
