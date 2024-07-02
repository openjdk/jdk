/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 7036144
 * @summary Test concatenated gz streams when available() returns zero
 * @run junit GZIPInputStreamAvailable
 */

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class GZIPInputStreamAvailable {

    public static final int NUM_COPIES = 100;

    @Test
    public void testZeroAvailable() throws IOException {

        // Create some uncompressed data and then repeat it NUM_COPIES times
        byte[] uncompressed1 = "this is a test".getBytes("ASCII");
        byte[] uncompressedN = repeat(uncompressed1, NUM_COPIES);

        // Compress the original data and then repeat that NUM_COPIES times
        byte[] compressed1 = deflate(uncompressed1);
        byte[] compressedN = repeat(compressed1, NUM_COPIES);

        // (a) Read back inflated data from a stream where available() is accurate and verify
        byte[] readback1 = inflate(new ByteArrayInputStream(compressedN));
        assertArrayEquals(uncompressedN, readback1);

        // (b) Read back inflated data from a stream where available() always returns zero and verify
        byte[] readback2 = inflate(new ZeroAvailableStream(new ByteArrayInputStream(compressedN)));
        assertArrayEquals(uncompressedN, readback2);
    }

    public static byte[] repeat(byte[] data, int count) {
        byte[] repeat = new byte[data.length * count];
        int off = 0;
        for (int i = 0; i < count; i++) {
            System.arraycopy(data, 0, repeat, off, data.length);
            off += data.length;
        }
        return repeat;
    }

    public static byte[] deflate(byte[] data) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(buf)) {
            out.write(data);
        }
        return buf.toByteArray();
    }

    public static byte[] inflate(InputStream in) throws IOException {
        return new GZIPInputStream(in).readAllBytes();
    }

    public static class ZeroAvailableStream extends FilterInputStream {
        public ZeroAvailableStream(InputStream in) {
            super(in);
        }
        @Override
        public int available() {
            return 0;
        }
    }
}
