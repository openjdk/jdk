/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;

/**
 * @test
 * @author Martin Desruisseaux
 */
public final class SetData {
    private static final int WIDTH = 3, HEIGHT = 2;

    private static final int TRANSLATE_X = -2, TRANSLATE_Y = -3;

    public static void main(final String[] args) {
        testWithIntegers();
        testWithFloats();
        testTranslatedSource();
    }

    private static void testWithIntegers() {
        var data   = new int[] {4, 8, 2, 7, 9, 1};
        var empty  = new DataBufferInt(WIDTH*HEIGHT);
        var buffer = new DataBufferInt(data, WIDTH*HEIGHT);
        buffer = (DataBufferInt) writeAndRead(buffer, empty);
        if (!Arrays.equals(data, buffer.getData())) {
            throw new AssertionError("Pixel values are not equal.");
        }
    }

    private static void testWithFloats() {
        var data   = new float[] {0.4f, 0.8f, 0.2f, 0.7f, 0.9f, 0.1f};
        var empty  = new DataBufferFloat(WIDTH*HEIGHT);
        var buffer = new DataBufferFloat(data, WIDTH*HEIGHT);
        buffer = (DataBufferFloat) writeAndRead(buffer, empty);
        if (!Arrays.equals(data, buffer.getData())) {
            throw new AssertionError("Pixel values are not equal.");
        }
    }

    private static DataBuffer writeAndRead(DataBuffer buffer, DataBuffer empty) {
        /*
         * Prepare an image with all pixels initialized to zero.
         */
        var dt = buffer.getDataType();
        var cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        var cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE, dt);
        var sm = new BandedSampleModel(dt, WIDTH, HEIGHT, 1);
        var wr = Raster.createWritableRaster(sm, empty, null);
        var im = new BufferedImage(cm, wr, false, null);
        /*
         * Write data provided by the data buffer.
         */
        wr = Raster.createWritableRaster(sm, buffer, null);
        im.setData(wr);
        return im.getRaster().getDataBuffer();
    }

    private static void testTranslatedSource() {
        var source = WritableRaster.createBandedRaster(
                DataBuffer.TYPE_BYTE,
                WIDTH  - TRANSLATE_X + 2,
                HEIGHT - TRANSLATE_Y + 1, 1,
                new Point(TRANSLATE_X, TRANSLATE_Y));

        for (int y=0; y<HEIGHT; y++) {
            for (int x=0; x<WIDTH; x++) {
                source.setSample(x, y, 0, (y+1)*10 + (x+1));
            }
        }
        var expected = new byte[] {
            11, 12, 13,
            21, 22, 23
        };
        var im = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        im.setData(source);
        var actual = ((DataBufferByte) im.getRaster().getDataBuffer()).getData();
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError("Pixel values are not equal.");
        }
    }
}
