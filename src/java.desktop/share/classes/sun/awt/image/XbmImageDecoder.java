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
import java.io.InputStream;
import java.io.IOException;

import static java.lang.Math.multiplyExact;

/**
 * Parse files of the form:
 *
 * {@snippet lang=c:
 * #define foo_width w
 * #define foo_height h
 * static char foo_bits[] = {
 * 0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,
 * 0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,0xnn,
 * 0xnn,0xnn,0xnn,0xnn};
 * }
 *
 * @author James Gosling
 */
public class XbmImageDecoder extends ImageDecoder {
    private static final byte[] XbmColormap = {(byte) 255, (byte) 255, (byte) 255,
                                               0, 0, 0};
    private static final int XbmHints = (ImageConsumer.TOPDOWNLEFTRIGHT |
                                         ImageConsumer.COMPLETESCANLINES |
                                         ImageConsumer.SINGLEPASS |
                                         ImageConsumer.SINGLEFRAME);

    private static final int MAX_CHAR_LIMIT = 128000;
    private static final int MAX_XBM_SIZE = 16384;
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
        char[] nm = new char[80];
        int c;
        int i = 0;
        int state = 0;
        int H = 0;
        int W = 0;
        int x = 0;
        int y = 0;
        boolean consumeWidthValue = false;
        boolean consumeHeightValue = false;
        boolean validWidthConsumed = false;
        boolean validHeightConsumed = false;
        // number of tokens seen as part of this define statement
        int defineTokenCount = 0;
        byte[] raster = null;
        IndexColorModel model = null;
        int charCount = 0;

        //read header info
        while (!aborted && (c = input.read()) != -1) {
            charCount++;
            if (charCount > MAX_CHAR_LIMIT) {
                error("Incomplete image after reading "
                    + "the maximum allowed number of characters: "
                    + MAX_CHAR_LIMIT);
            }
            if ('a' <= c && c <= 'z' ||
                'A' <= c && c <= 'Z' ||
                '0' <= c && c <= '9' || c == '#' || c == '_') {
                if (i < nm.length) {
                    nm[i++] = (char) c;
                } else {
                    error("XBM header contains literal greater than size 80");
                }
            } else if (i > 0) {
                int nc = i;
                i = 0;
                if (defineTokenCount >= 1) {
                    // we are inside a #define line
                    defineTokenCount++;
                }
                if (defineTokenCount == 0) {
                    if (nc == 7 &&
                        nm[0] == '#' &&
                        nm[1] == 'd' &&
                        nm[2] == 'e' &&
                        nm[3] == 'f' &&
                        nm[4] == 'i' &&
                        nm[5] == 'n' &&
                        nm[6] == 'e')
                    {
                        defineTokenCount++;
                        continue;
                    }
                } else if (defineTokenCount == 2) {
                    // consume second token in #define line
                    if (state < 2) {
                        if (nm[nc - 1] == 'h' &&
                            !validWidthConsumed) {
                            consumeWidthValue = true;
                        } else if ((nm[nc - 1] == 't' && nc > 1 &&
                                    nm[nc - 2] == 'h') &&
                                   !validHeightConsumed) {
                            consumeHeightValue = true;
                        }
                    }
                } else if (defineTokenCount == 3) {
                    defineTokenCount = 0;
                    // consume third token in #define line
                    int n = 0;
                    for (int p = 0; p < nc; p++) {
                        if ('0' <= (c = nm[p]) && c <= '9') {
                            n = n * 10 + c - '0';
                            if (n > MAX_XBM_SIZE) {
                                error("Width/Height cannot be more than: "
                                    + MAX_XBM_SIZE);
                            }
                        } else {
                            error("Invalid width/height value");
                        }
                    }

                    if (n > 0 && (consumeWidthValue || consumeHeightValue)) {
                        if (consumeWidthValue) {
                            if (!validWidthConsumed) {
                                W = n;
                                validWidthConsumed = true;
                                state++;
                            }
                            consumeWidthValue = false;
                        } else if (consumeHeightValue) {
                            if (!validHeightConsumed) {
                                H = n;
                                validHeightConsumed = true;
                                state++;
                            }
                            consumeHeightValue = false;
                        }
                    }
                    // verify the consumed width & height value and initialize
                    // required constructs
                    if (state == 2) {
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
            }
        }

        if (state != 3) {
            error("Width or Height of XBM file not defined");
        }

        // skip until we find '{'
        boolean imageDataStarted = false;
        while (!aborted && (c = input.read()) != -1) {
            charCount++;
            if (charCount > MAX_CHAR_LIMIT) {
                error("Incomplete image after reading "
                    + "the maximum allowed number of characters: "
                    + MAX_CHAR_LIMIT);
            }
            if (c == '{') {
                imageDataStarted = true;
                break;
            }
        }

        if (!imageDataStarted) {
            error("Missing '{' at the start of image data");
        }

        // used to make sure that we have the final delimiter '};',
        // while parsing the image data
        int previousChar = '{';
        // parse image data
        boolean imageDataTerminated = false;
        while (!aborted && (c = input.read()) != -1) {
            charCount++;
            if (charCount > MAX_CHAR_LIMIT) {
                error("Incomplete image after reading "
                    + "the maximum allowed number of characters: "
                    + MAX_CHAR_LIMIT);
            }

            if (c == ';') {
                if (previousChar != '}') {
                    error("Abrupt end of image data without '};' delimiter");
                }
                imageDataTerminated = true;
                break;
            }
            if (!Character.isWhitespace(c)) {
                previousChar = c;
            }

            if (',' != c && '}' != c &&
                !Character.isWhitespace(c)) {
                nm[i++] = (char) c;
                if (i > 4) {
                    error("Image hex data should be 3 or 4 characters long");
                }
            } else if (i == 3 || i == 4) {
                // consume valid hex image data
                int n = 0;
                int nc = i;
                i = 0;
                if (nm[0] == '0' &&
                    (nm[1] == 'x' || nm[1] == 'X')) {
                    for (int p = 2; p < nc; p++) {
                        c = nm[p];
                        if ('0' <= c && c <= '9')
                            c = c - '0';
                        else if ('A' <= c && c <= 'F')
                            c = c - 'A' + 10;
                        else if ('a' <= c && c <= 'f')
                            c = c - 'a' + 10;
                        else
                            error("Corrupt hex image data");
                        n = n * 16 + c;
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
                        if ((y + 1) > H) {
                            error("Scan size of XBM file exceeds"
                                + " the defined width x height");
                        }
                        if (setPixels(0, y, W, 1, model, raster, 0, W) == 0) {
                            error("Unexpected error occurred during setPixel()");
                        }
                        x = 0;
                        y++;
                    }
                } else {
                    error("Corrupt hex image data");
                }
            } else if (i == 1 || i == 2) {
                error("Image hex data should be 3 or 4 characters long");
            }
        }
        if (!imageDataTerminated) {
            error("Missing terminator ';'");
        }
        input.close();
        imageComplete(ImageConsumer.STATICIMAGEDONE, true);
    }
}
