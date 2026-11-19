/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/*
 * @test
 * @bug  4954405
 * @summary Verify DataBuffer offsets are handled by ByteInterleavedRaster
 */

public class ByteInterleavedRasterOffsetsTest {

    public static void main(String[] args) {
        byte[] data = { 0, -1, 0, 0 }; // only set the R sample.
        int[] bandOffsets = { 0, 1, 2 };
        DataBuffer databuf = new DataBufferByte(data, 3, 1);
        WritableRaster raster =
            Raster.createInterleavedRaster(databuf, 1, 1, 3, 3, bandOffsets, null);
        int[] pixels = raster.getPixels(0, 0, 1, 1, (int[])null);
        byte[] elements = (byte[])raster.getDataElements(0, 0, null);
        ColorModel colorModel = new ComponentColorModel(
                ColorSpace.getInstance(ColorSpace.CS_sRGB), false, false,
                ColorModel.OPAQUE, DataBuffer.TYPE_BYTE);
        BufferedImage img = new BufferedImage(colorModel, raster, false, null);
        int pixel = img.getRGB(0, 0);

        System.out.println("PIXEL0=" + Integer.toHexString(pixels[0]));
        System.out.println("PIXEL1=" + Integer.toHexString(pixels[1]));
        System.out.println("PIXEL2=" + Integer.toHexString(pixels[2]));
        System.out.println("ELEMENT0=" + Integer.toHexString(elements[0] & 0xff));
        System.out.println("ELEMENT1=" + Integer.toHexString(elements[1] & 0xff));
        System.out.println("ELEMENT2=" + Integer.toHexString(elements[2] & 0xff));
        System.out.println("PIXEL=" + Integer.toHexString(pixel));

        if ((pixels[0] != 0xff) || (pixels[1] != 0) || (pixels[2] != 0)) {
            throw new RuntimeException("Unexpected pixels");
        }
        if (((elements[0] & 0xff) != 0xff) || (elements[1] != 0) || (elements[2] != 0)) {
            throw new RuntimeException("Unexpected elements");
        }
        if (pixel != 0xffff0000) {
            throw new RuntimeException("Unexpected pixel");
        }
    }
}
