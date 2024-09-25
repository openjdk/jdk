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

/*
 * @test id=vanilla
 * @bug 8328938
 * @summary Test autovectorization with large scale and stride
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main compiler.loopopts.superword.TestLargeScaleAndStride
 */

/*
 * @test id=AlignVector
 * @bug 8328938
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+AlignVector compiler.loopopts.superword.TestLargeScaleAndStride
 */

package compiler.loopopts.superword;

import jdk.internal.misc.Unsafe;

public class TestLargeScaleAndStride {
    static final Unsafe UNSAFE = Unsafe.getUnsafe();
    static int RANGE = 100_000;

    public static void main(String[] args) {
        byte[] a = new byte[100];
        fill(a);

        byte[] gold1a = a.clone();
        byte[] gold1b = a.clone();
        byte[] gold2a = a.clone();
        byte[] gold2b = a.clone();
        byte[] gold2c = a.clone();
        byte[] gold2d = a.clone();
        byte[] gold3  = a.clone();
        test1a(gold1a);
        test1b(gold1b);
        test2a(gold2a);
        test2b(gold2b);
        test2c(gold2c);
        test2d(gold2d);
        test3(gold3);

        for (int i = 0; i < 100; i++) {
            byte[] c = a.clone();
            test1a(c);
            verify(c, gold1a);
        }

        for (int i = 0; i < 100; i++) {
            byte[] c = a.clone();
            test1b(c);
            verify(c, gold1b);
        }

        for (int i = 0; i < 100; i++) {
            byte[] c = a.clone();
            test2a(c);
            verify(c, gold2a);
        }

        for (int i = 0; i < 100; i++) {
            byte[] c = a.clone();
            test2b(c);
            verify(c, gold2b);
        }

        for (int i = 0; i < 100; i++) {
            byte[] c = a.clone();
            test2c(c);
            verify(c, gold2c);
        }

        for (int i = 0; i < 100; i++) {
            byte[] c = a.clone();
            test2d(c);
            verify(c, gold2d);
        }

        for (int i = 0; i < 100; i++) {
            byte[] c = a.clone();
            test3(c);
            verify(c, gold3);
        }
    }

    static void fill(byte[] a) {
        for (int i = 0; i < a.length; i++) {
          a[i] = (byte)i;
        }
    }

    static void verify(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                throw new RuntimeException("wrong value: " + i + ": " + a[i] + " != " + b[i]);
            }
        }
    }

    static void test1a(byte[] a) {
        int scale = 1 << 31;
        for (int i = 0; i < RANGE; i+=2) {
            long base = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
            // i is a multiple of 2
            // 2 * (1 >> 31) -> overflow to zero
            int j = scale * i; // always zero
            byte v0 = UNSAFE.getByte(a, base + (int)(j + 0));
            byte v1 = UNSAFE.getByte(a, base + (int)(j + 1));
            byte v2 = UNSAFE.getByte(a, base + (int)(j + 2));
            byte v3 = UNSAFE.getByte(a, base + (int)(j + 3));
            UNSAFE.putByte(a, base + (int)(j + 0), (byte)(v0 + 1));
            UNSAFE.putByte(a, base + (int)(j + 1), (byte)(v1 + 1));
            UNSAFE.putByte(a, base + (int)(j + 2), (byte)(v2 + 1));
            UNSAFE.putByte(a, base + (int)(j + 3), (byte)(v3 + 1));
        }
    }

    static void test1b(byte[] a) {
        int scale = 1 << 31;
        for (int i = RANGE-2; i >= 0; i-=2) {
            long base = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
            // i is a multiple of 2
            // 2 * (1 >> 31) -> overflow to zero
            int j = scale * i; // always zero
            byte v0 = UNSAFE.getByte(a, base + (int)(j + 0));
            byte v1 = UNSAFE.getByte(a, base + (int)(j + 1));
            byte v2 = UNSAFE.getByte(a, base + (int)(j + 2));
            byte v3 = UNSAFE.getByte(a, base + (int)(j + 3));
            UNSAFE.putByte(a, base + (int)(j + 0), (byte)(v0 + 1));
            UNSAFE.putByte(a, base + (int)(j + 1), (byte)(v1 + 1));
            UNSAFE.putByte(a, base + (int)(j + 2), (byte)(v2 + 1));
            UNSAFE.putByte(a, base + (int)(j + 3), (byte)(v3 + 1));
        }
    }

    static void test2a(byte[] a) {
        int scale = 1 << 30;
        for (int i = 0; i < RANGE; i+=4) {
            long base = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
            // i is a multiple of 4
            // 4 * (1 >> 30) -> overflow to zero
            int j = scale * i; // always zero
            byte v0 = UNSAFE.getByte(a, base + (int)(j + 0));
            byte v1 = UNSAFE.getByte(a, base + (int)(j + 1));
            byte v2 = UNSAFE.getByte(a, base + (int)(j + 2));
            byte v3 = UNSAFE.getByte(a, base + (int)(j + 3));
            UNSAFE.putByte(a, base + (int)(j + 0), (byte)(v0 + 1));
            UNSAFE.putByte(a, base + (int)(j + 1), (byte)(v1 + 1));
            UNSAFE.putByte(a, base + (int)(j + 2), (byte)(v2 + 1));
            UNSAFE.putByte(a, base + (int)(j + 3), (byte)(v3 + 1));
        }
    }


    static void test2b(byte[] a) {
        int scale = 1 << 30;
        for (int i = RANGE-4; i >= 0; i-=4) {
            long base = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
            // i is a multiple of 4
            // 4 * (1 >> 30) -> overflow to zero
            int j = scale * i; // always zero
            byte v0 = UNSAFE.getByte(a, base + (int)(j + 0));
            byte v1 = UNSAFE.getByte(a, base + (int)(j + 1));
            byte v2 = UNSAFE.getByte(a, base + (int)(j + 2));
            byte v3 = UNSAFE.getByte(a, base + (int)(j + 3));
            UNSAFE.putByte(a, base + (int)(j + 0), (byte)(v0 + 1));
            UNSAFE.putByte(a, base + (int)(j + 1), (byte)(v1 + 1));
            UNSAFE.putByte(a, base + (int)(j + 2), (byte)(v2 + 1));
            UNSAFE.putByte(a, base + (int)(j + 3), (byte)(v3 + 1));
        }
    }

    static void test2c(byte[] a) {
        int scale = -(1 << 30);
        for (int i = 0; i < RANGE; i+=4) {
            long base = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
            // i is a multiple of 4
            // 4 * (1 >> 30) -> overflow to zero
            int j = scale * i; // always zero
            byte v0 = UNSAFE.getByte(a, base + (int)(j + 0));
            byte v1 = UNSAFE.getByte(a, base + (int)(j + 1));
            byte v2 = UNSAFE.getByte(a, base + (int)(j + 2));
            byte v3 = UNSAFE.getByte(a, base + (int)(j + 3));
            UNSAFE.putByte(a, base + (int)(j + 0), (byte)(v0 + 1));
            UNSAFE.putByte(a, base + (int)(j + 1), (byte)(v1 + 1));
            UNSAFE.putByte(a, base + (int)(j + 2), (byte)(v2 + 1));
            UNSAFE.putByte(a, base + (int)(j + 3), (byte)(v3 + 1));
        }
    }

    static void test2d(byte[] a) {
        int scale = -(1 << 30);
        for (int i = RANGE-4; i >= 0; i-=4) {
            long base = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
            // i is a multiple of 4
            // 4 * (1 >> 30) -> overflow to zero
            int j = scale * i; // always zero
            byte v0 = UNSAFE.getByte(a, base + (int)(j + 0));
            byte v1 = UNSAFE.getByte(a, base + (int)(j + 1));
            byte v2 = UNSAFE.getByte(a, base + (int)(j + 2));
            byte v3 = UNSAFE.getByte(a, base + (int)(j + 3));
            UNSAFE.putByte(a, base + (int)(j + 0), (byte)(v0 + 1));
            UNSAFE.putByte(a, base + (int)(j + 1), (byte)(v1 + 1));
            UNSAFE.putByte(a, base + (int)(j + 2), (byte)(v2 + 1));
            UNSAFE.putByte(a, base + (int)(j + 3), (byte)(v3 + 1));
        }
    }

    static void test3(byte[] a) {
        int scale =   1 << 28;
        int stride =  1 << 4;
        int start = -(1 << 30);
        int end =     1 << 30;
        for (int i = start; i < end; i+=stride) {
            long base = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
            int j = scale * i; // always zero
            byte v0 = UNSAFE.getByte(a, base + (int)(j + 0));
            byte v1 = UNSAFE.getByte(a, base + (int)(j + 1));
            byte v2 = UNSAFE.getByte(a, base + (int)(j + 2));
            byte v3 = UNSAFE.getByte(a, base + (int)(j + 3));
            UNSAFE.putByte(a, base + (int)(j + 0), (byte)(v0 + 1));
            UNSAFE.putByte(a, base + (int)(j + 1), (byte)(v1 + 1));
            UNSAFE.putByte(a, base + (int)(j + 2), (byte)(v2 + 1));
            UNSAFE.putByte(a, base + (int)(j + 3), (byte)(v3 + 1));
        }
    }
}
