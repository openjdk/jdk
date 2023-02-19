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
import java.util.concurrent.atomic.AtomicInteger;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.awt.image.BufferedImage.TYPE_BYTE_BINARY;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_BYTE_INDEXED;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_BGR;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_555_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_565_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_GRAY;

/**
 * @test
 * @bug 8264666
 * @summary No exception or errors should occur in ColorConvertOp.filter().
 * @run main/othervm/timeout=600 UnexpectedSourceImageSize
 */
public final class UnexpectedSourceImageSize {

    private static final int SIZE = 100;

    private static final int[] TYPES = {
            TYPE_INT_RGB, TYPE_INT_ARGB, TYPE_INT_ARGB_PRE, TYPE_INT_BGR,
            TYPE_3BYTE_BGR, TYPE_4BYTE_ABGR, TYPE_4BYTE_ABGR_PRE,
            TYPE_USHORT_565_RGB, TYPE_USHORT_555_RGB, TYPE_BYTE_GRAY,
            TYPE_USHORT_GRAY, TYPE_BYTE_BINARY, TYPE_BYTE_INDEXED
    };
    private static final int[] INTERESTING_POINTS = new int[]{
            Integer.MIN_VALUE / SIZE - 1,
            -SIZE, -3, -1, 0, 1, 3,
            Integer.MAX_VALUE / SIZE + 1,
    };
    private static final int[] CSs = new int[]{
            ColorSpace.CS_sRGB, ColorSpace.CS_LINEAR_RGB, ColorSpace.CS_CIEXYZ,
            ColorSpace.CS_PYCC, ColorSpace.CS_GRAY
    };

    public static void main(String[] args) throws Exception {
        Thread[] threads = new Thread[CSs.length];
        for (int i = 0; i < threads.length; i++) {
            ColorSpace cs = ColorSpace.getInstance(CSs[i]);
            threads[i] = new Thread(() -> {
                for (final int type : TYPES) {
                    test(cs, type);
                }
            });
            threads[i].start();
        }
        for (int i = 0; i < CSs.length; i++) {
            threads[i].join();
        }
    }

    /**
     * 1. Checks how many times the width/height are requested during filter()
     * 2. Repeats step1, but returns some random data for each request
     */
    private static void test(ColorSpace cs, int type) {
        AtomicInteger srcCountW = new AtomicInteger();
        AtomicInteger srcCountH = new AtomicInteger();
        AtomicInteger dstCountW = new AtomicInteger();
        AtomicInteger dstCountH = new AtomicInteger();

        BufferedImage dstBI = new BufferedImage(SIZE, SIZE, type) {
            public int getWidth() {
                dstCountW.incrementAndGet();
                return super.getWidth();
            }
            public int getHeight() {
                dstCountH.incrementAndGet();
                return super.getHeight();
            }
        };
        BufferedImage srcBI = new BufferedImage(SIZE, SIZE, type) {
            public int getWidth() {
                srcCountW.incrementAndGet();
                return super.getWidth();
            }
            public int getHeight() {
                srcCountH.incrementAndGet();
                return super.getHeight();
            }
        };

        filter(srcBI, cs, dstBI);
        if (dstCountW.get() == 0 && dstCountH.get() == 0
                && srcCountW.get() == 0 && srcCountH.get() == 0) {
            // getWidth/getHeight are never called
            return;
        }
        for (int brokenH : INTERESTING_POINTS) {
            for (int brokenW : INTERESTING_POINTS) {
                for (int srcW = 0; srcW <= srcCountW.get(); ++srcW) {
                    for (int srcH = 0; srcH <= srcCountH.get(); ++srcH) {
                        srcBI = makeBI(type, brokenH, brokenW, srcW, srcH);
                        for (int dstW = 0; dstW <= dstCountW.get(); ++dstW) {
                            for (int dstH = 0; dstH <= dstCountH.get(); ++dstH) {
                                try {
                                    dstBI = makeBI(type, brokenH, brokenW, dstW, dstH);
                                    filter(srcBI, cs, dstBI);
                                } catch (Exception | OutOfMemoryError ignore) {
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static BufferedImage makeBI(int biType, int brokenH, int brokenW,
                                        int breakStepW, int breakStepH) {
        return new BufferedImage(SIZE, SIZE, biType) {
            private int stepW = 0;
            private int stepH = 0;
            public int getWidth() {
                if (stepW == breakStepW) {
                    return brokenW;
                }
                stepW++;
                return super.getWidth();
            }
            public int getHeight() {
                if (stepH == breakStepH) {
                    return brokenH;
                }
                stepH++;
                return super.getHeight();
            }
        };
    }

    private static void filter(BufferedImage src, ColorSpace to,
                               BufferedImage dest) {
        ColorConvertOp op = new ColorConvertOp(to, null);
        op.filter(src, dest);
    }
}
