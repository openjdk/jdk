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
 * @bug 8395707
 * @summary Test verifies that interlaced GIFs are decoded accurately
 */

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.util.Random;

public class InterlaceTest {

    public static void main(String[] args) throws Exception {
        // The complaint in JDK-8395707 is that interlaced GIFs are not
        // decoded correctly when the height is 4 pixels or smaller. This test
        // iterates through a few thousand width/height combinations to be safe

        int samples = 0;
        int failures = 0;
        for (int w = 1; w < 100; w++) {
            for (int h = 1; h < 100; h++) {
                samples++;
                BufferedImage bi = getSampleImage(w, h);
                byte[] fileData = writeInterlacedGIF(bi);
                BufferedImage readImage = ImageIO.read(
                        new ByteArrayInputStream(fileData));

                if (!isImageEquals(bi, readImage)) {
                    failures++;
                    System.err.println("Failure #" + failures + "; " +
                            w + "x" + h);
                    System.err.println("Original image:");
                    System.err.println(toString(bi));
                    System.err.println("Decoded image:");
                    System.err.println(toString(readImage));
                }
            }
        }
        if (failures > 0) {
            throw new RuntimeException("See System.err for details");
        }
        System.out.println("No errors; " + samples + " images tested");
    }

    /**
     * Print an image as a matrix of hex RGB values for human readability.
     */
    private static String toString(BufferedImage indexedImage) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < indexedImage.getHeight(); y++) {
            sb.append("[");
            for (int x = 0; x < indexedImage.getWidth(); x++) {
                int rgb = indexedImage.getRGB(x, y);
                sb.append(Integer.toUnsignedString(rgb, 16));
                if (x < indexedImage.getWidth() - 1) {
                    sb.append(", ");
                }
            }
            sb.append("]\n");
        }
        return sb.toString().trim();
    }

    private static boolean isImageEquals(BufferedImage imageA,
                                         BufferedImage imageB) {
        for (int y = 0; y < imageA.getHeight(); y++) {
            for (int x = 0; x < imageA.getWidth(); x++) {
                int rgb1 = imageA.getRGB(x, y);
                int rgb2 = imageB.getRGB(x, y);
                if (rgb1 != rgb2) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Create a random BufferedImage using the default IndexColorModel.
     */
    public static BufferedImage getSampleImage(int width, int height) {
        BufferedImage returnValue = new BufferedImage(width, height,
                BufferedImage.TYPE_BYTE_INDEXED);
        IndexColorModel icm = (IndexColorModel) returnValue.getColorModel();
        Random r = new Random(0);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = icm.getRGB(r.nextInt(icm.getMapSize()));
                returnValue.setRGB(x, y, rgb);
            }
        }

        return returnValue;
    }

    /**
     * Convert a BufferedImage to an interlaced GIF image file.
     */
    public static byte[] writeInterlacedGIF(BufferedImage bi) throws Exception {
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream()) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(byteOut)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(bi, null, null), param);
            }
            writer.dispose();
            return byteOut.toByteArray();
        }
    }
}
