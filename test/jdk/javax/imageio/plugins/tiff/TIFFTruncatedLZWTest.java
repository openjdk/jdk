/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8394183
 * @summary Ensure that we throw IIOException while reading TIFF
            image using LZW compression and truncated strip data
 * @run main TIFFTruncatedLZWTest
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.Objects;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class TIFFTruncatedLZWTest {
    public static void main(String[] args) throws Exception {
        var b = ByteBuffer.allocate(75).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0x4949).putShort((short) 42).putInt(8)
            .putShort((short) 5);
        for (int[] t : new int[][] {
            {256,4,1},  // width
            {257,4,1},  // height
            {259,3,5},  // Use LZW compression
            {273,4,74}, // StripOffset
            {279,4,1}   // StripByteCount value 1
        }) {
            b.putShort((short) t[0]).putShort((short) t[1])
                .putInt(1).putInt(t[2]);
        }
        Iterator<ImageReader> readers =
            ImageIO.getImageReadersByFormatName("TIFF");
        if (!readers.hasNext()) {
            throw new IOException("No TIFF reader available");
        }

        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(
            new ByteArrayInputStream(b.array()))) {
            reader.setInput(input);
            reader.read(0); // first image in the TIFF
        } catch (IIOException e) {
            if (!Objects.equals(e.getMessage(), "Invalid LZW" +
                " compressed strip/tile data")) {
                throw e;
            }
        } finally {
            reader.dispose();
        }
    }
}
