/*
 * Copyright (c) 1995, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 *      Reads xbitmap format images into a DIBitmap structure.
 */
package sun.awt.image;

import java.awt.image.ImageConsumer;
import java.awt.image.IndexColorModel;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.multiplyExact;

/**
 * Parse files of the form:
 *
 * #define foo_width w
 * #define foo_height h
 * static char foo_bits[] = {
 * 0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,
 * 0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,
 * 0xnn,0xnn,0xnn,0xnn};
 *
 * @author James Gosling
 */
public class XbmImageDecoder extends ImageDecoder {
    private static byte[] XbmColormap = {(byte) 255, (byte) 255, (byte) 255,
                                         0, 0, 0};
    private static int XbmHints = (ImageConsumer.TOPDOWNLEFTRIGHT |
                                   ImageConsumer.COMPLETESCANLINES |
                                   ImageConsumer.SINGLEPASS |
                                   ImageConsumer.SINGLEFRAME);
    private static final int MAX_XBM_SIZE = 16384;
    private static final int HEADER_SCAN_LIMIT = 100;

    public XbmImageDecoder(InputStreamImageSource src, InputStream is) {
        super(src, is);
        if (!(input instanceof BufferedInputStream)) {
            // If the topmost stream is a metered stream,
            // we take forever to decode the image...
            input = new BufferedInputStream(input, 80);
        }
    }


    /**
     * An error has occurred. Throw an exception.
     */
    private static void error(String s1) throws ImageFormatException {
        throw new ImageFormatException(s1);
    }

    /**
     * produce an image from the stream.
     */
    public void produceImage() throws IOException, ImageFormatException {
        int H = 0;
        int W = 0;
        int x = 0;
        int y = 0;
        int n = 0;
        int state = 0;
        byte[] raster = null;
        IndexColorModel model = null;

        String matchRegex = "\\s*(0[xX])?((?:(?!,|\\};).)+)(,|\\};)";
        String replaceRegex = "0[xX]|,|\\s+|\\};";

        String line;
        int lineNum = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {
            // loop to process XBM header - width, height and create raster
            while (!aborted && (line = br.readLine()) != null
                    && lineNum <= HEADER_SCAN_LIMIT) {
                lineNum++;
                // process #define stmts
                if (line.trim().startsWith("#define")) {
                    String[] token = line.split("\\s+");
                    if (token.length != 3) {
                        error("Error while parsing define statement");
                    }
                    try {
                        if (!token[2].isBlank() && state == 0) {
                            if (token[1].endsWith("th")) {
                                W = Integer.parseInt(token[2]);
                            } else if (token[1].endsWith("t")) {
                                H = Integer.parseInt(token[2]);
                            }
                            state = 1; // after first dimension is set
                        } else if (!token[2].isBlank() && state == 1) {
                            if (token[1].endsWith("th")) {
                                W = Integer.parseInt(token[2]);
                            } else if (token[1].endsWith("t")) {
                                H = Integer.parseInt(token[2]);
                            }
                            state = 2; // after second dimension is set
                        }
                    } catch (NumberFormatException nfe) {
                        // parseInt() can throw NFE
                        error("Error while parsing width or height.");
                    }
                }

                if (state == 2) {
                    if (W <= 0 || H <= 0) {
                        error("Invalid values for width or height.");
                    }
                    if (multiplyExact(W, H) > MAX_XBM_SIZE) {
                        error("Large XBM file size."
                                + " Maximum allowed size: " + MAX_XBM_SIZE);
                    }
                    model = new IndexColorModel(8, 2, XbmColormap,
                            0, false, 0);
                    setDimensions(W, H);
                    setColorModel(model);
                    setHints(XbmHints);
                    headerComplete();
                    raster = new byte[W];
                    state = 3;
                    break;
                }
            }

            if (state != 3) {
                error("Width or Height of XBM file not defined");
            }

            boolean contFlag = false;
            StringBuilder sb = new StringBuilder();

            // loop to process image data
            while (!aborted && (line = br.readLine()) != null) {
                lineNum++;

                if (!contFlag) {
                    if (line.contains("[]")) {
                        contFlag = true;
                    } else {
                        continue;
                    }
                }

                int end = line.indexOf(';');
                if (end >= 0) {
                    sb.append(line, 0, end + 1);
                    break;
                } else {
                    sb.append(line).append(System.lineSeparator());
                }
            }

            String resultLine = sb.toString();
            int cutOffIndex = resultLine.indexOf('{');
            resultLine = resultLine.substring(cutOffIndex + 1);

            Matcher matcher = Pattern.compile(matchRegex).matcher(resultLine);
            while (matcher.find()) {
                if (y >= H) {
                    error("Scan size of XBM file exceeds"
                            + " the defined width x height");
                }

                int startIndex = matcher.start();
                int endIndex = matcher.end();
                String hexByte = resultLine.substring(startIndex, endIndex);
                hexByte = hexByte.replaceAll("^\\s+", "");

                if (!(hexByte.startsWith("0x")
                        || hexByte.startsWith("0X"))) {
                    error("Invalid hexadecimal number at Ln#:" + lineNum
                            + " Col#:" + (startIndex + 1));
                }
                hexByte = hexByte.replaceAll(replaceRegex, "");
                if (hexByte.length() != 2) {
                    error("Invalid hexadecimal number at Ln#:" + lineNum
                            + " Col#:" + (startIndex + 1));
                }

                try {
                    n = Integer.parseInt(hexByte, 16);
                } catch (NumberFormatException nfe) {
                    error("Error parsing hexadecimal at Ln#:" + lineNum
                            + " Col#:" + (startIndex + 1));
                }
                for (int mask = 1; mask <= 0x80; mask <<= 1) {
                    if (x < W) {
                        if ((n & mask) != 0)
                            raster[x] = 1;
                        else
                            raster[x] = 0;
                    }
                    x++;
                }

                if (x >= W) {
                    int result = setPixels(0, y, W, 1, model, raster, 0, W);
                    if (result <= 0) {
                        error("Unexpected error occurred during setPixel()");
                    }
                    x = 0;
                    y++;
                }
            }
            imageComplete(ImageConsumer.STATICIMAGEDONE, true);
        }
    }
}
