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
 * @summary Test configurable support for concatenated gzip streams
 * @run junit GZIPInputStreamConcat
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class GZIPInputStreamConcat {

    private int bufsize;
    private boolean allowConcatenation;
    private boolean ignoreExtraBytes;

    public static Stream<Object[]> testScenarios() throws IOException {

        // Test concat vs. non-concat, garbage vs. no-garbage, and various buffer sizes on random data
        Random random = new Random();
        final ArrayList<List<Object>> scenarios = new ArrayList<>();
        for (boolean concat : new boolean[] { false, true }) {
            for (boolean garbage : new boolean[] { false, true }) {
                for (int size = 1; size < 1024; size += random.nextInt(32) + 1) {
                    scenarios.add(List.of(randomData(0, 100), size, concat, garbage));
                }
            }
        }
        return scenarios.stream().map(List::toArray);
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    public void testScenario(byte[] uncompressed, int size, boolean concat, boolean garbage) throws IOException {
        this.bufsize = size;
        this.allowConcatenation = concat;
        this.ignoreExtraBytes = garbage;
        testScenario(uncompressed);
    }

    public void testScenario(byte[] uncompressed) throws IOException {

        // Compress the test data
        byte[] compressed = deflate(uncompressed);

        // Decompress a single stream with no extra garbage - should always work
        byte[] input = compressed;
        byte[] output = uncompressed;
        testDecomp(input, output, null);

        // Decompress a truncated GZIP header
        input = oneByteShort(gzipHeader());
        testDecomp(input, null, EOFException.class);

        // Decompress a single stream that is one byte short - should always fail
        input = oneByteShort(compressed);
        output = null;
        testDecomp(input, null, EOFException.class);

        // Decompress a single stream with one byte of extra garbage (trying all 256 possible values)
        for (int extra = 0; extra < 0x100; extra++) {
            input = oneByteLong(compressed, extra);
            output = uncompressed;
            testDecomp(input, output, !ignoreExtraBytes ? IOException.class : null);
        }

        // Decompress a single stream followed by a truncated GZIP header
        input = concat(compressed, oneByteShort(gzipHeader()));
        output = uncompressed;
        testDecomp(input, output, !ignoreExtraBytes ? IOException.class : null);

        // Decompress a single stream followed by another stream that is one byte short
        input = concat(compressed, oneByteShort(compressed));
        output = uncompressed;
        testDecomp(input, output, allowConcatenation || !ignoreExtraBytes ? IOException.class : null);

        // Decompress two streams concatenated
        input = concat(compressed, compressed);
        output = allowConcatenation ? concat(uncompressed, uncompressed) : uncompressed;
        testDecomp(input, output, !allowConcatenation && !ignoreExtraBytes ? ZipException.class : null);

        // Decompress three streams concatenated
        input = concat(compressed, compressed, compressed);
        output = allowConcatenation ? concat(uncompressed, uncompressed, uncompressed) : uncompressed;
        testDecomp(input, output, !allowConcatenation && !ignoreExtraBytes ? ZipException.class : null);

        // Decompress three streams concatenated followed by a truncated GZIP header
        input = concat(compressed, compressed, compressed, oneByteShort(gzipHeader()));
        output = allowConcatenation ? concat(uncompressed, uncompressed, uncompressed) : uncompressed;
        testDecomp(input, output, !ignoreExtraBytes ? IOException.class : null);
    }

    // Do decompression and check result
    public void testDecomp(byte[] compressed, byte[] uncompressed, Class<? extends IOException> exceptionType) {
        try {
            byte[] readback = inflate(new ByteArrayInputStream(compressed));
            if (exceptionType != null)
                throw new AssertionError("expected " + exceptionType.getSimpleName());
            assertArrayEquals(uncompressed, readback);
        } catch (IOException e) {
            if (exceptionType == null)
                throw new AssertionError("unexpected exception", e);
            if (!exceptionType.isAssignableFrom(e.getClass())) {
                throw new AssertionError(String.format(
                  "expected %s but got %s", exceptionType.getSimpleName(), e.getClass().getSimpleName()), e);
            }
        }
    }

    // Create a GZIP header
    public static byte[] gzipHeader() throws IOException {
        byte[] compressed = deflate(new byte[0]);
        return Arrays.copyOfRange(compressed, 0, 10);
    }

    // Add one extra byte to the given array
    public static byte[] oneByteLong(byte[] array, int value) {
        byte[] array2 = new byte[array.length + 1];
        System.arraycopy(array, 0, array2, 0, array.length);
        array2[array.length] = (byte)value;
        return array2;
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
    public static byte[] deflate(byte[] data) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(buf)) {
            out.write(data);
        }
        return buf.toByteArray();
    }

    // GZIP decompress data
    public byte[] inflate(InputStream in) throws IOException {
        return new GZIPInputStream(in, bufsize, allowConcatenation, ignoreExtraBytes).readAllBytes();
    }
}
