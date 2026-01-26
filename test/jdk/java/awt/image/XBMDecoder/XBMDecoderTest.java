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
 * @bug 8361748
 * @summary Tests XBM image size limits and if XBMImageDecoder.produceImage()
 *          throws appropriate error when parsing invalid XBM image data.
 * @run main XBMDecoderTest
 */

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.util.Arrays;
import javax.swing.ImageIcon;

public class XBMDecoderTest {

    public static void main(String[] args) throws Exception {
        String dir = System.getProperty("test.src");
        PrintStream originalErr = System.err;
        boolean validCase;

        File currentDir = new File(dir);
        File[] files = currentDir.listFiles((File d, String s)
                                            -> s.endsWith(".xbm"));

        for (File file : files) {
            String fileName = file.getName();
            validCase = fileName.startsWith("valid");

            System.out.println("--- Testing " + fileName + " ---");
            try (FileInputStream fis = new FileInputStream(file);
                 ByteArrayOutputStream errContent = new ByteArrayOutputStream()) {
                System.setErr(new PrintStream(errContent));

                ImageIcon icon = new ImageIcon(fis.readAllBytes());
                boolean isErrEmpty = errContent.toString().isEmpty();

                if (!isErrEmpty) {
                    System.out.println("Expected ImageFormatException occurred.");
                    System.out.print(errContent);
                }
                if (validCase && !isErrEmpty) {
                    throw new RuntimeException("Test failed: Error stream not empty");
                } else if (!validCase && isErrEmpty && hasPixelData(icon.getImage())) {
                    throw new RuntimeException("Test failed: ImageFormatException"
                            + " expected but not thrown");
                }
                if (validCase && !hasPixelData(icon.getImage())) {
                    throw new RuntimeException("Test failed: the parsed image " +
                            "does not contain any pixel data");
                }
                System.out.println("PASSED\n");
            } finally {
                System.setErr(originalErr);
            }
        }
    }

    private static boolean hasPixelData(Image img) {
        int w = img.getWidth(null);
        int h = img.getHeight(null);
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        int[] pixels = bi.getRGB(0, 0, w, h, null, 0, w);
        if (Arrays.stream(pixels).allMatch(i -> i == 0)) {
            return false;
        }
        return true;
    }
}
