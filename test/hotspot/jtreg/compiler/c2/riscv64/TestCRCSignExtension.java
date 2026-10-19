/*
 * Copyright (c) 2026, Institute of Software, Chinese Academy of Sciences.
 * All rights reserved.
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
 * @test id=c1
 * @summary Verify sign extension of RISC-V CRC32 and CRC32C intrinsic results
 * @requires os.arch == "riscv64" & vm.compiler1.enabled
 * @library /test/lib
 * @modules java.base/java.util.zip:+open java.base/sun.nio.ch
 * @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+UseCRC32Intrinsics -XX:+UseCRC32CIntrinsics
 *      -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -XX:CompileThreshold=1000
 *      -XX:CompileCommand=compileonly,compiler.c2.riscv64.TestCRCSignExtension::test*
 *      compiler.c2.riscv64.TestCRCSignExtension
 */

/*
 * @test id=c2
 * @summary Verify sign extension of RISC-V CRC32 and CRC32C intrinsic results
 * @requires os.arch == "riscv64" & vm.compiler2.enabled
 * @library /test/lib
 * @modules java.base/java.util.zip:+open java.base/sun.nio.ch
 * @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+UseCRC32Intrinsics -XX:+UseCRC32CIntrinsics
 *      -XX:-TieredCompilation -XX:CompileThreshold=1000
 *      -XX:CompileCommand=compileonly,compiler.c2.riscv64.TestCRCSignExtension::test*
 *      compiler.c2.riscv64.TestCRCSignExtension
 */

package compiler.c2.riscv64;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.CRC32C;

import jdk.test.lib.Asserts;
import sun.nio.ch.DirectBuffer;

public class TestCRCSignExtension {
    private static final MethodHandle CRC32_BYTE;
    private static final MethodHandle CRC32_ARRAY;
    private static final MethodHandle CRC32_BUFFER;
    private static final MethodHandle CRC32C_ARRAY;
    private static final MethodHandle CRC32C_BUFFER;
    private static final byte[] DATA = new byte[1025];
    private static final ByteBuffer DIRECT = ByteBuffer.allocateDirect(DATA.length);
    private static final long ADDRESS = ((DirectBuffer) DIRECT).address();
    private static final Case[] CASES = new Case[12];

    private record Case(int crc, int offset, int length, int crc32, int crc32c) {}

    static {
        try {
            var lookup32 = MethodHandles.privateLookupIn(CRC32.class, MethodHandles.lookup());
            var lookup32c = MethodHandles.privateLookupIn(CRC32C.class, MethodHandles.lookup());
            var byteType = MethodType.methodType(int.class, int.class, int.class);
            var arrayType = MethodType.methodType(int.class, int.class, byte[].class, int.class, int.class);
            var bufferType = MethodType.methodType(int.class, int.class, long.class, int.class, int.class);
            CRC32_BYTE = lookup32.findStatic(CRC32.class, "update", byteType);
            CRC32_ARRAY = lookup32.findStatic(CRC32.class, "updateBytes0", arrayType);
            CRC32_BUFFER = lookup32.findStatic(CRC32.class, "updateByteBuffer0", bufferType);
            CRC32C_ARRAY = lookup32c.findStatic(CRC32C.class, "updateBytes", arrayType);
            CRC32C_BUFFER = lookup32c.findStatic(CRC32C.class, "updateDirectByteBuffer", bufferType);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        for (int i = 0; i < DATA.length; i++) {
            DATA[i] = (byte) (i * 31);
        }
        DIRECT.put(DATA);
        int index = 0;
        for (int crc : new int[] {0, -1}) {
            for (int offset = 0; offset < 2; offset++) {
                for (int length : new int[] {0, 1, 1024}) {
                    CASES[index++] = new Case(crc, offset, length,
                            ~reference(~crc, offset, length, 0xedb88320),
                            reference(crc, offset, length, 0x82f63b78));
                }
            }
        }
    }

    private static int referenceByte(int crc, int value, int polynomial) {
        crc ^= value & 0xff;
        for (int bit = 0; bit < 8; bit++) {
            crc = (crc >>> 1) ^ ((crc & 1) == 0 ? 0 : polynomial);
        }
        return crc;
    }

    private static int reference(int crc, int offset, int length, int polynomial) {
        for (int i = offset; i < offset + length; i++) {
            crc = referenceByte(crc, DATA[i], polynomial);
        }
        return crc;
    }

    // Compare before a field store or an interpreted return can normalize the result.
    private static boolean testCRC32Byte(int value, int expected) throws Throwable {
        return (int) CRC32_BYTE.invokeExact(0, value) == expected;
    }

    private static boolean testCRC32Array(int crc, int offset, int length, int expected) throws Throwable {
        return (int) CRC32_ARRAY.invokeExact(crc, DATA, offset, length) == expected;
    }

    private static boolean testCRC32Buffer(int crc, int offset, int length, int expected) throws Throwable {
        return (int) CRC32_BUFFER.invokeExact(crc, ADDRESS, offset, length) == expected;
    }

    private static boolean testCRC32CArray(int crc, int offset, int end, int expected) throws Throwable {
        return (int) CRC32C_ARRAY.invokeExact(crc, DATA, offset, end) == expected;
    }

    private static boolean testCRC32CBuffer(int crc, int offset, int end, int expected) throws Throwable {
        return (int) CRC32C_BUFFER.invokeExact(crc, ADDRESS, offset, end) == expected;
    }

    public static void main(String[] args) throws Throwable {
        for (int i = 0; i < 20_000; i++) {
            int value = (i & 1) == 0 ? 0 : 128;
            int expected = ~referenceByte(-1, value, 0xedb88320);
            Asserts.assertTrue(testCRC32Byte(value, expected), "CRC32 byte");
            Asserts.assertFalse(testCRC32Byte(value, expected ^ 1), "CRC32 byte mismatch");

            Case c = CASES[i % CASES.length];
            Asserts.assertTrue(testCRC32Array(c.crc(), c.offset(), c.length(), c.crc32()), "CRC32 array");
            Asserts.assertFalse(testCRC32Array(c.crc(), c.offset(), c.length(), c.crc32() ^ 1), "CRC32 array mismatch");
            Asserts.assertTrue(testCRC32Buffer(c.crc(), c.offset(), c.length(), c.crc32()), "CRC32 buffer");
            Asserts.assertFalse(testCRC32Buffer(c.crc(), c.offset(), c.length(), c.crc32() ^ 1), "CRC32 buffer mismatch");

            int end = c.offset() + c.length();
            Asserts.assertTrue(testCRC32CArray(c.crc(), c.offset(), end, c.crc32c()), "CRC32C array");
            Asserts.assertFalse(testCRC32CArray(c.crc(), c.offset(), end, c.crc32c() ^ 1), "CRC32C array mismatch");
            Asserts.assertTrue(testCRC32CBuffer(c.crc(), c.offset(), end, c.crc32c()), "CRC32C buffer");
            Asserts.assertFalse(testCRC32CBuffer(c.crc(), c.offset(), end, c.crc32c() ^ 1), "CRC32C buffer mismatch");
        }
    }
}
