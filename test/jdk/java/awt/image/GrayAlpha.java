/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4243044
 * @summary This test should show two windows filled with checker
 *          board pattern. The transparency should runs from left to right from
 *          total transparent to total opaque.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual GrayAlpha
 */

import java.util.List;
import java.awt.Frame;
import java.awt.color.ColorSpace;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.awt.Point;
import java.awt.Panel;
import java.awt.Transparency;
import java.awt.Window;

public class GrayAlpha extends Panel {

    private static final String INSTRUCTIONS = """
        This test should show two windows filled with checker board
        vpattern. The transparency should runs from left to right from
        totally transparent to totally opaque. If either the pattern or
        the transparency is not shown correctly, click Fail, otherwise
        click Pass.""";

    BufferedImage bi;
    AffineTransform identityTransform = new AffineTransform();

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("GrayAlpha Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int)INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(GrayAlpha::createTestUI)
                .build()
                .awaitAndCheck();
    }


    public GrayAlpha(int width, int height,
                     boolean hasAlpha, boolean isAlphaPremultiplied,
                     boolean useRGB) {
        boolean isAlphaPremuliplied = true;
        int bands = useRGB ? 3 : 1;
        bands = hasAlpha ? bands + 1 : bands;

        ColorSpace cs = useRGB ?
            ColorSpace.getInstance(ColorSpace.CS_sRGB) :
            ColorSpace.getInstance(ColorSpace.CS_GRAY);
        int transparency = hasAlpha ?
            Transparency.TRANSLUCENT : Transparency.OPAQUE;
        int[] bits = new int[bands];
        for (int i = 0; i < bands; i++) {
            bits[i] = 8;
        }

        ColorModel cm = new ComponentColorModel(cs,
                                                bits,
                                                hasAlpha,
                                                isAlphaPremultiplied,
                                                transparency,
                                                DataBuffer.TYPE_BYTE);
        WritableRaster wr =
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                                           width, height, bands,
                                           new Point(0, 0));

        for (int b = 0; b < bands; b++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int s;

                    if (b != bands - 1 || !hasAlpha) {
                        // Gray band(s), fill with a checkerboard pattern
                        if (((x / 10) % 2) == ((y / 10) % 2)) {
                            s = 255;
                        } else {
                            s = 0;
                        }
                        if (isAlphaPremultiplied) {
                            int alpha = (x*255)/(width - 1);
                            s = (s*alpha)/255;
                        }
                    } else {
                        // Alpha band, increase opacity left to right
                        s = (x*255)/(width - 1);
                    }

                    wr.setSample(x, y, b, s);
                }
            }
        }

        this.bi = new BufferedImage(cm, wr, isAlphaPremultiplied, null);
    }

    public Dimension getPreferredSize() {
        return new Dimension(bi.getWidth(), bi.getHeight());
    }

    public void paint(Graphics g) {
        if (bi != null) {
            ((Graphics2D)g).drawImage(bi, 0, 0, null);
        }
    }

    public static Frame makeFrame(String title,
                                 int x, int y, int width, int height,
                                 boolean hasAlpha,
                                 boolean isAlphaPremultiplied,
                                 boolean useRGB) {
        Frame f = new Frame(title);
        f.add(new GrayAlpha(width, height,
                            hasAlpha, isAlphaPremultiplied, useRGB));
        f.pack();
        f.setLocation(x, y);
        return f;
    }

    private static List<Window> createTestUI() {
        int width = 200;
        int height = 200;

        int x = 100;
        int y = 100;

        Frame f1 = makeFrame("Gray (non-premultiplied)",
                  x, y, width, height,
                  true, false, false);
        x += width + 20;

        Frame f2 = makeFrame("Gray (premultiplied)",
                  x, y, width, height,
                  true, true, false);

        return List.of(f1, f2);
    }
}
