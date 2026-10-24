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
 * @bug 8392737
 * @summary Check interpreted CRC32 updates with different input forms, alignments and lengths
 * @run main/othervm -Xint compiler.intrinsics.zip.TestCRC32Interpreter
 */

package compiler.intrinsics.zip;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

public class TestCRC32Interpreter {
    // Include both sides of the scalar and vector loop boundaries.
    private static final int[] LENGTHS = {
        0, 1, 2, 3, 4, 5, 7, 8, 9, 15, 16, 17, 31, 32, 33,
        63, 64, 65, 127, 128, 129, 255, 256, 257, 383, 384, 385,
        511, 512, 513, 639, 640, 641, 767, 768, 769, 1023, 1024, 1025,
        2047, 2048, 2049, 4095, 4096, 4097, 8191, 8192, 8193
    };

    // CRC32.update(int) must only consume the low eight bits.
    private static final int[] PREFIX = {
        0, 1, 127, 128, 255, 256, -1, -128, Integer.MIN_VALUE, Integer.MAX_VALUE
    };

    private static int referenceByte(int crc, int value) {
        crc = ~crc ^ (value & 0xff);
        for (int bit = 0; bit < 8; bit++) {
            crc = (crc >>> 1) ^ ((crc & 1) == 0 ? 0 : 0xedb88320);
        }
        return ~crc;
    }

    private static void check(CRC32 crc, int expected, String kind, int offset, int length) {
        if (crc.getValue() != Integer.toUnsignedLong(expected)) {
            throw new AssertionError(kind + ": offset=" + offset + ", length=" + length
                    + ", expected=" + Integer.toUnsignedLong(expected)
                    + ", actual=" + crc.getValue());
        }
    }

    private static CRC32 newCRC(boolean prefix) {
        CRC32 crc = new CRC32();
        if (prefix) {
            for (int value : PREFIX) {
                crc.update(value);
            }
        }
        return crc;
    }

    public static void main(String[] args) {
        byte[] data = new byte[8193 + 16];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31 + (i >>> 3));
        }
        ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
        direct.put(data);
        ByteBuffer[] buffers = {
            ByteBuffer.wrap(data), ByteBuffer.wrap(data).asReadOnlyBuffer(),
            direct, direct.asReadOnlyBuffer()
        };

        int initial = 0;
        CRC32 single = new CRC32();
        for (int value : PREFIX) {
            initial = referenceByte(initial, value);
            single.update(value);
            check(single, initial, "single byte", 0, 1);
        }

        int cases = 0;
        for (boolean prefix : new boolean[] {false, true}) {
            for (int offset = 0; offset < 16; offset++) {
                for (int length : LENGTHS) {
                    int expected = prefix ? initial : 0;
                    CRC32 bytes = newCRC(prefix);
                    for (int i = offset; i < offset + length; i++) {
                        expected = referenceByte(expected, data[i]);
                        bytes.update(data[i]);
                    }
                    check(bytes, expected, "byte loop", offset, length);

                    CRC32 array = newCRC(prefix);
                    array.update(data, offset, length);
                    check(array, expected, "array", offset, length);

                    CRC32 split = newCRC(prefix);
                    split.update(data, offset, length / 2);
                    split.update(data, offset + length / 2, length - length / 2);
                    check(split, expected, "split array", offset, length);

                    for (ByteBuffer buffer : buffers) {
                        buffer.clear().position(offset).limit(offset + length);
                        CRC32 crc = newCRC(prefix);
                        crc.update(buffer);
                        check(crc, expected, buffer.isDirect() ? "direct buffer" : "heap buffer",
                                offset, length);
                        if (buffer.position() != offset + length || buffer.limit() != offset + length) {
                            throw new AssertionError("Unexpected buffer position or limit");
                        }
                    }
                    cases++;
                }
            }
        }
        System.out.println("Passed " + cases + " CRC32 cases");
    }
}
