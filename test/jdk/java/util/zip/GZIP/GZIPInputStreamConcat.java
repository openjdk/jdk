/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8322256
 * @summary Test support for concatenated GZIP streams
 * @run junit GZIPInputStreamConcat
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GZIPInputStreamConcat {

    // The buffer size passed to GZIPInputStream
    private int bufSize;

    public static Stream<Arguments> testScenarios() throws IOException {
        // Test concat vs. non-concat, garbage vs. no-garbage, and various buffer sizes on random data
        Random random = new Random();
        List<Arguments> scenarios = new ArrayList<>();
        for (int bufSize = 1; bufSize < 1024; bufSize += random.nextInt(32) + 1) {
            scenarios.add(Arguments.of(randomData(0, 100), bufSize));
        }
        return scenarios.stream();
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    public void testScenario(byte[] uncompressed, int bufSize) throws IOException {
        this.bufSize = bufSize;
        runTests(uncompressed);
    }

    public void runTests(byte[] uncompressed) throws IOException {

        // Compress the test data
        byte[] compressed = gzip(uncompressed);

        // Decompress a single stream with no extra garbage - should always work
        decomp(compressed, uncompressed);

        // Decompress a truncated GZIP header
        decompFail(oneByteShort(gzipHeader()), EOFException.class);

        // Decompress a single stream that is one byte short - should always fail
        decompFail(oneByteShort(compressed), EOFException.class);

        // Decompress a single stream with one byte of extra garbage (trying all 256 possible values)
        for (int extra = Byte.MIN_VALUE; extra <= Byte.MAX_VALUE ; extra++) {
            decomp(oneByteLong(compressed, (byte) extra), uncompressed);
        }

        // Decompress a single stream followed by a truncated GZIP header
        decomp(concat(compressed, oneByteShort(gzipHeader())), uncompressed);

        // Decompress a single stream followed by another stream that is one byte short
        decompFail(concat(compressed, oneByteShort(compressed)), IOException.class);

        // Decompress two streams concatenated
        decomp(concat(compressed, compressed), concat(uncompressed, uncompressed));

        // Decompress three streams concatenated
        decomp(concat(compressed, compressed, compressed),
                concat(uncompressed, uncompressed, uncompressed));

        // Decompress three streams concatenated followed by a truncated GZIP header
        decomp(concat(compressed, compressed, compressed, oneByteShort(gzipHeader())),
                concat(uncompressed, uncompressed, uncompressed));
    }

    // Do decompression and check result
    public void decomp(byte[] compressed, byte[] uncompressed) throws IOException {
        byte[] readback = gunzip(new ByteArrayInputStream(compressed));
        assertArrayEquals(uncompressed, readback);
    }

    // Do decompression, asserting an execption
    public void decompFail(byte[] compressed, Class<? extends IOException> exceptionType) {
        assertThrows(exceptionType, () -> {
            byte[] readback = gunzip(new ByteArrayInputStream(compressed));
        });
    }

    // Create a GZIP header
    public static byte[] gzipHeader() throws IOException {
        byte[] compressed = gzip(new byte[0]);
        return Arrays.copyOfRange(compressed, 0, 10);
    }

    // Add one extra byte to the given array
    public static byte[] oneByteLong(byte[] array, byte value) {
        return concat(array, new byte[] {value});
    }

    // Chop off the last byte of the given array
    public static byte[] oneByteShort(byte[] array) {
        return Arrays.copyOfRange(array, 0, array.length - 1);
    }

    // Create some random data
    public static byte[] randomData(int min, int max) {
        Random random = new Random();
        byte[] data = new byte[min + random.nextInt(max - min)];
        random.nextBytes(data);
        return data;
    }

    // Concatenate byte arrays
    public static byte[] concat(byte[]... arrays) {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (byte[] array : arrays)
            buf.writeBytes(array);
        return buf.toByteArray();
    }

    // GZIP compress data
    public static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(buf)) {
            out.write(data);
        }
        return buf.toByteArray();
    }

    // GZIP decompress data
    public byte[] gunzip(InputStream in) throws IOException {
        return new GZIPInputStream(in, bufSize).readAllBytes();
    }
}
