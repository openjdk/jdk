/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Tests valid and invalid (unsupported) combinations of compression and colour spaces.
 * Follow up on an email thread:
 * https://mail.openjdk.java.net/pipermail/client-libs-dev/2022-May/004882.html
 *
 * @test
 * @run main TIFFCompressionsTest
 * @summary Verify valid color space x compression combinations
 */
public class TIFFCompressionsTest {
    public static void main(String[] args) throws IOException {
        // @formatter:off
        final String[][] testData = new String[][] {
               // Filename                       OK/NOK  Regexp to match toString (OK) or exception (NOK)
               // Expected pixel values for [0,0], [1,0], [2,0]
  new String[] { "test_4x4_cmyk_jpeg.tiff",     "OK", ".*Bits = 32 numComponents = 4 .*SimpleCMYK.* transparency = 1 .*alpha = false.*",
                 "[255, 254, 254, 0]", "[255, 3, 2, 255]", "[255, 0, 0, 3]"},
  new String[] { "test_4x4_cmyk_lzw.tiff",      "OK", ".*Bits = 32 numComponents = 4 .*SimpleCMYK.* transparency = 1 .*alpha = false.*",
                 "[0, 1, 0, 255]",     "[1, 254, 252, 1]", "[0, 255, 255, 249]"},
  new String[] { "test_4x4_gray_jpeg.tiff",     "OK", ".*Bits = 8 numComponents = 1 .*ICC_.* transparency = 1 .*alpha = false.*",
                 "[0]",                "[58]",             "[0]"},
  new String[] { "test_4x4_grayalpha_lzw.tiff", "OK", ".*Bits = 16 numComponents = 2 .*ICC_.* transparency = 3 .*alpha = true.*",
                 "[0, 254]",           "[54, 255]",        "[1, 127]"},
  new String[] { "test_4x4_rgba_deflate.tiff",  "OK", ".*Bits = 32 numComponents = 4 .*ICC_.* transparency = 3 .*alpha = true.*",
                 "[0, 0, 0, 254]",     "[254, 0, 0, 255]", "[2, 0, 0, 127]"},
  new String[] { "test_4x4_rgba_lzw.tiff",      "OK", ".*Bits = 32 numComponents = 4 .*ICC_.* transparency = 3 .*alpha = true.*",
                 "[0, 0, 0, 254]",     "[254, 0, 0, 255]", "[2, 0, 0, 127]"},
  new String[] { "test_4x4_rgba_none.tiff",     "OK", ".*Bits = 32 numComponents = 4 .*ICC_.* transparency = 3 .*alpha = true.*",
                 "[0, 0, 0, 254]",     "[254, 0, 0, 255]", "[2, 0, 0, 127]"},
  new String[] { "test_4x4_rgba_packbits.tiff", "OK", ".*Bits = 32 numComponents = 4 .*ICC_.* transparency = 3 .*alpha = true.*",
                 "[0, 0, 0, 254]",     "[254, 0, 0, 255]", "[2, 0, 0, 127]"},
  new String[] { "test_4x4_rgba_jpeg.tiff",     "NOK","JPEG compressed tiles with alpha channel are not supported." },
  new String[] { "test_4x4_grayalpha_jpeg.tiff","NOK","JPEG compressed tiles with alpha channel are not supported." },
        };
        // @formatter:on
        final List<String> errors = new ArrayList<>();
        for (String[] data : testData) {
            final String filename = data[0].trim();
            System.out.printf("Processing image %s.\n", filename);
            final boolean ok = "OK".equals(data[1].trim());
            final Pattern pattern = Pattern.compile(data[2].trim());
            BufferedImage image = null;
            String exceptionMsg = null;
            try {
                image = ImageIO.read(Objects.requireNonNull(TIFFCompressionsTest.class.getResource(filename)).openStream());
            } catch (IIOException e) {
                exceptionMsg = e.getMessage();
                e.printStackTrace();
            }
            if (ok) {
                if (image != null) {
                    if (pattern.matcher(image.toString()).matches()) {
                        int[] actual = new int[image.getColorModel().getNumComponents()];
                        for (int x = 0; x < 3; x++) {
                            int[] expected = decodeArray(data[3 + x].trim());
                            image.getData().getPixel(x, 0, actual);
                            if (!Arrays.equals(actual, expected)) {
                                errors.add(String.format("Pixel [%d, 0] of image %s was supposed to be %s but was %s.",
                                        x, filename, Arrays.toString(expected), Arrays.toString(actual)));
                            }
                        }
                    } else {
                        errors.add(String.format("Image %s's toString() was supposed to match regexp %s.", filename, pattern));
                    }
                } else {
                    errors.add(String.format("Image %s was supposed to be correctly decoded.", filename));
                }
            } else {
                if (image != null) {
                    errors.add(String.format("Image %s was not supposed to be decoded.", filename));
                } else {
                    if (exceptionMsg == null || !pattern.matcher(exceptionMsg).matches()) {
                        errors.add(
                                String.format("IIOException for image %s was supposed to match regexp %s.", filename, pattern));
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            System.out.println("Failed tests: \n" + String.join("\n", errors));
            throw new RuntimeException("There were errors, see the log.");
        }
    }

    public static int[] decodeArray(final String array) {
        final String[] strings = array.substring(1, array.length() - 1).split(",");
        final int[] ints = new int[strings.length];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = Integer.parseInt(strings[i].trim());
        }
        return ints;
    }
}
