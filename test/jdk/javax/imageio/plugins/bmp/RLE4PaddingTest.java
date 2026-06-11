/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     8384512
 * @summary Test verifies that BMP images are encoded correctly with RLE4
 *          compression and odd number of distinct pixels at the end of
 *          scanline.
 */

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class RLE4PaddingTest {
    private static final int width = 5;
    private static final int height = 2;
    private static BufferedImage getTestImage() {
        // create BufferedImage with width 5 and all distinct pixels,
        // so that it uses absolute mode for RLE.
        // If we don't add appropriate padding at end of the scanline,
        // the encoded data of next scanline will be corrupt.
        int bpp = 4;
        int size = 16;
        byte[] r = new byte[16];
        byte[] g = new byte[16];
        byte[] b = new byte[16];

        for (int i = 0; i < 16; i++) {
            r[i] = g[i] = b[i] = (byte)(i * 16);
        }
        IndexColorModel icm = new IndexColorModel(bpp, size, r, g, b);
        BufferedImage src = new BufferedImage(width, height,
            BufferedImage.TYPE_BYTE_INDEXED, icm);

        int[][] rows = {
            {1, 2, 3, 4, 5},
            {6, 7, 8, 9, 10}
        };

        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                src.getRaster().setSample(x, y, 0, rows[y][x]);
            }
        }
        return src;
    }

    public static void main(String[] args) throws IOException {
        BufferedImage src = getTestImage();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("BMP").next();
        writer.setOutput(ios);
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionType("BI_RLE4");
        writer.write(null, new IIOImage(src, null, null), param);
        ios.close();
        baos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BufferedImage dst = ImageIO.read(bais);

        checkResult(src, dst);
    }

    private static void checkResult(BufferedImage src, BufferedImage dst) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int srcRgb = src.getRGB(x, y);
                int dstRgb = dst.getRGB(x, y);

                if (srcRgb != dstRgb) {
                    throw new RuntimeException("Test failed due to color" +
                        " difference: " + Integer.toHexString(dstRgb) +
                        " instead of " + Integer.toHexString(srcRgb) +
                        " at [" + x + ", " + y + "]");
                }
            }
        }
    }
}
