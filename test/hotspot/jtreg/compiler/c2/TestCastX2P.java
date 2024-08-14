/*
 * Copyright (c) 2024, Arm Limited. All rights reserved.
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

package compiler.c2;

import jdk.internal.misc.Unsafe;
import jdk.test.lib.Asserts;

/**
 * @test TestCastX2P
 * @summary AArch64: remove extra register copy when converting from long to pointer.
 * @bug 8336245
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -XX:-TieredCompilation compiler.c2.TestCastX2P
 */

public class TestCastX2P {

    public static final int LEN = 2040;

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    public static long lseed = 0xbeef;
    public static int iseed = 0xbeef;
    public static short sseed = (short) (0xef);
    public static byte bseed = (byte) (0xe);

    public static long off1 = 16;
    public static long off2 = 32;
    public static long off3 = 64;

    public static class TestLong {

        private static long address = UNSAFE.allocateMemory(LEN);

        static {
            for (int k = 0; k < 10_000; k++) {
                for (int i = 0; i < LEN/2; i++) {
                    UNSAFE.putLong(address+i, lseed);
                }
            }

            UNSAFE.putLong(address + off1 + 1030, lseed);
            UNSAFE.putLong(address + 1023, lseed);
            UNSAFE.putLong(address + off2 + 1001, lseed);
        }
    }

    public static class TestLongIndirect {

        private static long address = UNSAFE.allocateMemory(LEN);

        static {
            for (int k = 0; k < 1000; k++) {
                for (int i = 0; i < LEN/2; i++) {
                    UNSAFE.putLong(address+i, lseed);
                }
            }

            UNSAFE.putLong(address + off1, lseed);
            UNSAFE.putLong(address + off1 + off2, lseed);
            UNSAFE.putLong(address + off3, lseed);
        }
    }

    public static class TestInt {

        private static long address = UNSAFE.allocateMemory(LEN);

        static {
            for (int k = 0; k < 10_000; k++) {
                for (int i = 0; i < LEN/2; i++) {
                    UNSAFE.putInt(address+i, iseed);
                }
            }

            UNSAFE.putInt(address + off1 + 274, iseed);
            UNSAFE.putInt(address + 278, iseed);
            UNSAFE.putInt(address + off2 + 282, iseed);
        }
    }

    public static class TestIntIndirect {

        private static long address = UNSAFE.allocateMemory(LEN);

        static {
            for (int k = 0; k < 1000; k++) {
                for (int i = 0; i < LEN/2; i++) {
                    UNSAFE.putInt(address+i, iseed);
                }
            }

            UNSAFE.putInt(address + off1, iseed);
            UNSAFE.putInt(address + off1 + off2, iseed);
            UNSAFE.putInt(address + off3, iseed);
        }
    }

    public static class TestShort {

        private static long address = UNSAFE.allocateMemory(LEN);

        static {
            for (int k = 0; k < 10_000; k++) {
                for (int i = 0; i < LEN/2; i++) {
                    UNSAFE.putShort(address+i, sseed);
                }
            }

            UNSAFE.putShort(address + off1 + 257, sseed);
            UNSAFE.putShort(address + 277, sseed);
            UNSAFE.putShort(address + off2 + 283, sseed);
        }
    }

    public static class TestShortIndirect {

        private static long address = UNSAFE.allocateMemory(LEN);

        static {
            for (int k = 0; k < 1000; k++) {
                for (int i = 0; i < LEN/2; i++) {
                    UNSAFE.putShort(address+i, sseed);
                }
            }

            UNSAFE.putShort(address + off1, sseed);
            UNSAFE.putShort(address + off1 + off2, sseed);
            UNSAFE.putShort(address + off3, sseed);
        }
    }

    public static class TestByte {

        private static long address = UNSAFE.allocateMemory(LEN);

        static {
            for (int k = 0; k < 10_000; k++) {
                for (int i = 0; i < LEN/2; i++) {
                    UNSAFE.putByte(address+i, bseed);
                }
            }

            UNSAFE.putByte(address + off1 + 257, bseed);
            UNSAFE.putByte(address + 277, bseed);
            UNSAFE.putByte(address + off2 + 283, bseed);
        }
    }

    public static class TestByteIndirect {

        private static long address = UNSAFE.allocateMemory(LEN);

        static {
            for (int k = 0; k < 1000; k++) {
                for (int i = 0; i < LEN/2; i++) {
                    UNSAFE.putByte(address+i, bseed);
                }
            }

            UNSAFE.putByte(address + off1, bseed);
            UNSAFE.putByte(address + off1 + off2, bseed);
            UNSAFE.putByte(address + off3, bseed);
        }
    }

    static void test() {
        TestLong t1 = new TestLong();
        Asserts.assertEquals(UNSAFE.getLong(t1.address + off1 + 1030), lseed, "put long failed!");
        Asserts.assertEquals(UNSAFE.getLong(t1.address + 1023), lseed, "put long failed!");
        Asserts.assertEquals(UNSAFE.getLong(t1.address + off2 + 1001), lseed, "put long failed!");

        TestLongIndirect t2 = new TestLongIndirect();
        Asserts.assertEquals(UNSAFE.getLong(t2.address + off1), lseed, "put long failed!");
        Asserts.assertEquals(UNSAFE.getLong(t2.address + off1 + off2), lseed, "put long failed!");
        Asserts.assertEquals(UNSAFE.getLong(t2.address + off3), lseed, "put long failed!");

        TestInt t3 = new TestInt();
        Asserts.assertEquals(UNSAFE.getInt(t3.address + off1 + 274), iseed, "put int failed!");
        Asserts.assertEquals(UNSAFE.getInt(t3.address + 278), iseed, "put int failed!");
        Asserts.assertEquals(UNSAFE.getInt(t3.address + off2 + 282), iseed, "put int failed!");

        TestIntIndirect t4 = new TestIntIndirect();
        Asserts.assertEquals(UNSAFE.getInt(t4.address + off1), iseed, "put int failed!");
        Asserts.assertEquals(UNSAFE.getInt(t4.address + off1 + off2), iseed, "put int failed!");
        Asserts.assertEquals(UNSAFE.getInt(t4.address + off3), iseed, "put int failed!");

        TestShort t5 = new TestShort();
        Asserts.assertEquals(UNSAFE.getShort(t5.address + off1 + 257), sseed, "put short failed!");
        Asserts.assertEquals(UNSAFE.getShort(t5.address + 277), sseed, "put short failed!");
        Asserts.assertEquals(UNSAFE.getShort(t5.address + off2 + 283), sseed, "put short failed!");

        TestShortIndirect t6 = new TestShortIndirect();
        Asserts.assertEquals(UNSAFE.getShort(t6.address + off1), sseed, "put short failed!");
        Asserts.assertEquals(UNSAFE.getShort(t6.address + off1 + off2), sseed, "put short failed!");
        Asserts.assertEquals(UNSAFE.getShort(t6.address + off3), sseed, "put short failed!");

        TestByte t7 = new TestByte();
        Asserts.assertEquals(UNSAFE.getByte(t7.address + off1 + 257), bseed, "put byte failed!");
        Asserts.assertEquals(UNSAFE.getByte(t7.address + 277), bseed, "put byte failed!");
        Asserts.assertEquals(UNSAFE.getByte(t7.address + off2 + 283), bseed, "put byte failed!");

        TestByteIndirect t8 = new TestByteIndirect();
        Asserts.assertEquals(UNSAFE.getByte(t8.address + off1), bseed, "put byte failed!");
        Asserts.assertEquals(UNSAFE.getByte(t8.address + off1 + off2), bseed, "put byte failed!");
        Asserts.assertEquals(UNSAFE.getByte(t8.address + off3), bseed, "put byte failed!");
    }

    public static void main(String[] strArr) {
        test();
    }
}
