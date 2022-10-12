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

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.util.concurrent.CountDownLatch;

/**
 * @test
 * @bug 8273972
 * @summary Verifies that ColorConvertOp works fine if shared between threads
 * @run main/othervm/timeout=600 MTTransformValidation
 */
public final class MTTransformValidation {

    public static final int SIZE = 255;
    private static volatile boolean failed = false;

    private static final int[] spaces = {
            ColorSpace.CS_CIEXYZ, ColorSpace.CS_GRAY, ColorSpace.CS_LINEAR_RGB,
            ColorSpace.CS_PYCC, ColorSpace.CS_sRGB
    };

    private static final int[] types = new int[]{
            BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB,
            BufferedImage.TYPE_INT_ARGB_PRE, BufferedImage.TYPE_INT_BGR,
            BufferedImage.TYPE_3BYTE_BGR, BufferedImage.TYPE_4BYTE_ABGR,
            BufferedImage.TYPE_4BYTE_ABGR_PRE,
            BufferedImage.TYPE_USHORT_565_RGB,
            BufferedImage.TYPE_USHORT_555_RGB, BufferedImage.TYPE_BYTE_GRAY,
            BufferedImage.TYPE_USHORT_GRAY, BufferedImage.TYPE_BYTE_BINARY,
            BufferedImage.TYPE_BYTE_INDEXED
    };

    /**
     * For all possible combinations of color spaces and image types, convert
     * the source image using one shared ColorConvertOp. The result is validated
     * against images converted on one thread only.
     */
    public static void main(String[] args) throws Exception {
        for (int srcCS : spaces) {
            for (int dstCS : spaces) {
                if(srcCS != dstCS) {
                    for (int type : types) {
                        checkTypes(ColorSpace.getInstance(srcCS),
                                   ColorSpace.getInstance(dstCS), type);
                    }
                }
            }
        }
    }

    private static void checkTypes(ColorSpace srcCS, ColorSpace dstCS, int type)
            throws Exception {
        ColorConvertOp goldOp = new ColorConvertOp(srcCS, dstCS, null);
        BufferedImage gold = goldOp.filter(createSrc(type), null);
        // we do not share the goldOp since it is already initialized, but
        // instead we will trigger initialization/usage of the new sharedOp on
        // different threads at once
        ColorConvertOp sharedOp = new ColorConvertOp(srcCS, dstCS, null);
        test(gold, sharedOp, type);

        if (failed) {
            throw new RuntimeException("Unexpected exception");
        }
    }

    private static void test(BufferedImage gold, ColorConvertOp sharedOp,
                             int type) throws Exception {
        Thread[] ts = new Thread[7];
        CountDownLatch latch = new CountDownLatch(ts.length);
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                BufferedImage local = createSrc(type);
                latch.countDown();
                try {
                    latch.await();
                    BufferedImage image = sharedOp.filter(local, null);
                    validate(image, gold);
                } catch (Throwable t) {
                    t.printStackTrace();
                    failed = true;
                }
            });
        }
        for (Thread t : ts) {
            t.start();
        }
        for (Thread t : ts) {
            t.join();
        }
    }

    private static BufferedImage createSrc(int type) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, type);
        fill(img);
        return img;
    }

    private static void fill(BufferedImage image) {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                image.setRGB(i, j,
                             (i << 24) | (i << 16) | (j << 8) | ((i + j) >> 1));
            }
        }
    }

    private static void validate(BufferedImage img1, BufferedImage img2) {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int rgb1 = img1.getRGB(i, j);
                int rgb2 = img2.getRGB(i, j);
                if (rgb1 != rgb2) {
                    System.err.println("rgb1 = " + Integer.toHexString(rgb1));
                    System.err.println("rgb2 = " + Integer.toHexString(rgb2));
                    throw new RuntimeException();
                }
            }
        }
    }
}
