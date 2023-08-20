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
import java.awt.image.SampleModel;

/**
 * @test
 * @bug 8279216
 * @summary behavior checked by this test is not specified, this test just
 *          defends against accidental changes
 */
public final class SkipSampleModel {

    private static int[] TYPES = {
            BufferedImage.TYPE_INT_RGB,
            BufferedImage.TYPE_INT_ARGB,
            BufferedImage.TYPE_INT_ARGB_PRE,
            BufferedImage.TYPE_INT_BGR,
            BufferedImage.TYPE_3BYTE_BGR,
            BufferedImage.TYPE_4BYTE_ABGR,
            BufferedImage.TYPE_4BYTE_ABGR_PRE,
            BufferedImage.TYPE_USHORT_565_RGB,
            BufferedImage.TYPE_USHORT_555_RGB,
            BufferedImage.TYPE_BYTE_GRAY,
            BufferedImage.TYPE_USHORT_GRAY,
            BufferedImage.TYPE_BYTE_BINARY,
            BufferedImage.TYPE_BYTE_INDEXED
    };

    private static final ColorSpace[] CSs = {
            ColorSpace.getInstance(ColorSpace.CS_CIEXYZ),
            ColorSpace.getInstance(ColorSpace.CS_GRAY),
            ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB),
            ColorSpace.getInstance(ColorSpace.CS_PYCC),
            ColorSpace.getInstance(ColorSpace.CS_sRGB)
    };

    public static void main(String[] args) {
        for (ColorSpace cs : CSs) {
            ColorConvertOp op = new ColorConvertOp(cs, null);
            for (int srcType : TYPES) {
                for (int dstType : TYPES) {
                    BufferedImage src = new BufferedImage(1, 1, srcType) {
                        @Override
                        public SampleModel getSampleModel() {
                            throw new AssertionError();
                        }
                    };
                    BufferedImage dst = new BufferedImage(1, 1, dstType) {
                        @Override
                        public SampleModel getSampleModel() {
                            throw new AssertionError();
                        }
                    };
                    op.filter(src, dst);
                }
            }
        }
    }
}
