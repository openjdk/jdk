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
 * @bug 8390546
 * @summary Verify that the memory effect of the encodeISOArray intrinsic is correctly wired in.
 * @library /test/lib
 * @requires vm.compiler2.enabled
 * @modules java.base/java.lang:+open java.base/sun.nio.cs:+open
 * @run main compiler.intrinsics.string.TestEncodeISOArray
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:CompileThreshold=100
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:UseAVX=0 -XX:-UseSSE42Intrinsics
 *                   compiler.intrinsics.string.TestEncodeISOArray
 */

package compiler.intrinsics.string;

import jdk.test.lib.Asserts;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TestEncodeISOArray {
    private static final int ITERATIONS = 20_000;
    private static final int FIRST_BYTE = 'C';
    private static final char[] SOURCE = "C2 go brrr".toCharArray();
    private static final byte[] UTF16_SOURCE = toUTF16Bytes(SOURCE);
    private static final byte[] EXPECTED = "C2 go brrr".getBytes(StandardCharsets.UTF_8);
    private static final MethodType ENCODE_TYPE = MethodType.methodType(int.class, char[].class, int.class, byte[].class, int.class, int.class);
    private static final MethodType ENCODE_BYTE_TYPE = MethodType.methodType(int.class, byte[].class, int.class, byte[].class, int.class, int.class);
    private static final MethodHandle ENCODE_ASCII_ARRAY = findEncoder("java.lang.StringCoding", "encodeAsciiArray0", ENCODE_TYPE);
    private static final MethodHandle ENCODE_ISO_ARRAY = findEncoder("sun.nio.cs.ISO_8859_1$Encoder", "encodeISOArray0", ENCODE_TYPE);
    private static final MethodHandle ENCODE_BYTE_ISO_ARRAY = findEncoder("java.lang.StringCoding", "encodeISOArray0", ENCODE_BYTE_TYPE);

    private static byte[] toUTF16Bytes(char[] chars) {
        ByteBuffer buffer = ByteBuffer.allocate(chars.length * Character.BYTES).order(ByteOrder.nativeOrder());
        for (char c : chars) {
            buffer.putChar(c);
        }
        return buffer.array();
    }

    private static MethodHandle findEncoder(String className, String methodName, MethodType type) {
        try {
            Class<?> holder = Class.forName(className);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(holder, MethodHandles.lookup());
            return lookup.findStatic(holder, methodName, type);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Original reproducer from JDK-8390546
    private static byte[] toUtf8Bytes(char[] chars) {
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        return Arrays.copyOfRange(byteBuffer.array(), 0, byteBuffer.limit());
    }

    // Targeted check that does not depend on the UTF-8 encoder and arraycopy both being inlined.
    private static int encodeASCIIAndLoadFirstByte() throws Throwable {
        byte[] destination = new byte[4];
        int encoded = (int) ENCODE_ASCII_ARRAY.invokeExact(SOURCE, 0, destination, 0, 4);
        if (encoded != 4) {
            return -1;
        }
        return destination[0];
    }

    private static int encodeISOAndLoadFirstByte() throws Throwable {
        byte[] destination = new byte[4];
        int encoded = (int) ENCODE_ISO_ARRAY.invokeExact(SOURCE, 0, destination, 0, 4);
        if (encoded != 4) {
            return -1;
        }
        return destination[0];
    }

    private static int encodeByteISOAndLoadFirstByte() throws Throwable {
        byte[] destination = new byte[4];
        int encoded = (int) ENCODE_BYTE_ISO_ARRAY.invokeExact(UTF16_SOURCE, 0, destination, 0, 4);
        if (encoded != 4) {
            return -1;
        }
        return destination[0];
    }

    private static boolean runOriginalReproducer() {
        for (int i = 0; i < ITERATIONS; i++) {
            if (!Arrays.equals(toUtf8Bytes(SOURCE), EXPECTED)) {
                return false;
            }
        }
        return true;
    }

    private static boolean runASCIITest() throws Throwable {
        for (int i = 0; i < ITERATIONS; i++) {
            if (encodeASCIIAndLoadFirstByte() != FIRST_BYTE) {
                return false;
            }
        }
        return true;
    }

    private static boolean runISOTest() throws Throwable {
        for (int i = 0; i < ITERATIONS; i++) {
            if (encodeISOAndLoadFirstByte() != FIRST_BYTE) {
                return false;
            }
        }
        return true;
    }

    private static boolean runByteISOTest() throws Throwable {
        for (int i = 0; i < ITERATIONS; i++) {
            if (encodeByteISOAndLoadFirstByte() != FIRST_BYTE) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws Throwable {
        Asserts.assertTrue(runOriginalReproducer(), "Original reproducer failed");
        Asserts.assertTrue(runASCIITest(), "ASCII encoding failed");
        Asserts.assertTrue(runISOTest(), "ISO-8859-1 encoding failed");
        Asserts.assertTrue(runByteISOTest(), "Byte ISO-8859-1 encoding failed");
    }
}
