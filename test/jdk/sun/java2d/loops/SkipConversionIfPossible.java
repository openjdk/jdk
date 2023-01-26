/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_BGR;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;

/**
 * @test
 * @bug 8297681
 * @summary The blit TYPE_4BYTE_ABGR_PRE to TYPE_INT_ARGB_PRE should be "direct"
 */
public final class SkipConversionIfPossible {

    private static final int SIZE = 256;

    public static void main(String[] args) {
        // Initial bug was in the TYPE_4BYTE_ABGR_PRE to TYPE_INT_ARGB_PRE blit.
        // But I checked other blits just in case.
        test(new int[]{TYPE_INT_ARGB_PRE, TYPE_4BYTE_ABGR_PRE});
        test(new int[]{TYPE_INT_RGB, TYPE_INT_BGR, TYPE_3BYTE_BGR});
        test(new int[]{TYPE_INT_ARGB, TYPE_4BYTE_ABGR});
    }

    private static void test(int[] types) {
        for (int src : types) {
            for (int dst : types) {
                render(src, dst);
            }
        }
    }

    private static void render(int src, int dst) {
        BufferedImage from = new BufferedImage(SIZE, SIZE, src);
        for (int a = 0; a < SIZE; ++a) {
            for (int c = 0; c < SIZE; ++c) {
                // The data is intentionally broken for the argb_pre format, but
                // it should be stored as is in dst if no conversion was done.
                from.getRaster().setPixel(c, a, new int[]{c, c << 24, -c, a});
            }
        }
        BufferedImage to = new BufferedImage(SIZE, SIZE, dst);
        Graphics2D g = to.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(from, 0, 0, null);
        g.dispose();

        for (int a = 0; a < SIZE; ++a) {
            for (int c = 0; c < SIZE; ++c) {
                int[] pixel1 = from.getRaster().getPixel(c, a, (int[]) null);
                int[] pixel2 = to.getRaster().getPixel(c, a, (int[]) null);
                if (!Arrays.equals(pixel1, pixel2)) {
                    System.err.println(Arrays.toString(pixel1));
                    System.err.println(Arrays.toString(pixel2));
                    throw new RuntimeException();
                }
            }
        }
    }
}
