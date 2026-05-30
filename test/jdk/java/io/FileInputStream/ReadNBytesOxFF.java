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

/*
 * @test
 * @bug     8XXXXXXX
 * @summary readNBytes should not treat byte value 0xFF as EOF
 * @run main ReadNBytesOxFF
 */

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.Arrays;

/**
 * Tests that FileInputStream.readNBytes() and ChannelInputStream.readNBytes()
 * correctly handle the byte value 0xFF (255). Previously, the single-byte
 * retry path cast read() to byte before comparing with -1:
 *
 *     byte b = (byte)read();  // (byte)255 == -1
 *     if (b == -1) break;     // incorrectly treats 0xFF as EOF
 *
 * This caused readNBytes() to return truncated data when encountering 0xFF
 * bytes in the single-byte fallback path (entered when read(byte[],int,int)
 * returns 0).
 */
public class ReadNBytesOxFF {

    public static void main(String[] args) throws Exception {
        // Create a file filled with 0xFF bytes
        byte[] data = new byte[256];
        Arrays.fill(data, (byte) 0xFF);
        // Also include some 0xFF bytes interspersed with other values
        for (int i = 0; i < data.length; i += 2) {
            data[i] = (byte) 0xFF;
            if (i + 1 < data.length) {
                data[i + 1] = (byte) i;
            }
        }

        Path tmpFile = Files.createTempFile("readnbytes-0xff-", ".bin");
        try {
            Files.write(tmpFile, data);

            // Test FileInputStream.readNBytes()
            testFileInputStream(tmpFile, data);

            // Test ChannelInputStream.readNBytes() via Channels.newInputStream()
            testChannelInputStream(tmpFile, data);

            System.out.println("Test passed.");
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    static void testFileInputStream(Path path, byte[] expected) throws Exception {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            byte[] result = fis.readNBytes(expected.length);
            if (result.length != expected.length) {
                throw new RuntimeException(
                    "FileInputStream.readNBytes: expected " + expected.length +
                    " bytes but got " + result.length +
                    " (0xFF byte likely misinterpreted as EOF)");
            }
            if (!Arrays.equals(result, expected)) {
                throw new RuntimeException(
                    "FileInputStream.readNBytes: content mismatch");
            }
        }
    }

    static void testChannelInputStream(Path path, byte[] expected) throws Exception {
        try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ);
             InputStream cis = Channels.newInputStream(fc)) {
            byte[] result = cis.readNBytes(expected.length);
            if (result.length != expected.length) {
                throw new RuntimeException(
                    "ChannelInputStream.readNBytes: expected " + expected.length +
                    " bytes but got " + result.length +
                    " (0xFF byte likely misinterpreted as EOF)");
            }
            if (!Arrays.equals(result, expected)) {
                throw new RuntimeException(
                    "ChannelInputStream.readNBytes: content mismatch");
            }
        }
    }
}
