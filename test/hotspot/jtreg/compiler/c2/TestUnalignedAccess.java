/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestUnalignedAccess
 * @summary AArch64: C2 compilation hits offset_ok_for_immed: assert "c2 compiler bug".
 * @bug 8319690
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main/othervm compiler.c2.TestUnalignedAccess
 * @run main/othervm -Xcomp -XX:-TieredCompilation -Xmx1g
 *                   -XX:CompileCommand=compileonly,compiler.c2.TestUnalignedAccess*::<clinit>
 *                   compiler.c2.TestUnalignedAccess
 */

public class TestUnalignedAccess {

    public static final int LEN = 2040;

    static final Unsafe UNSAFE = Unsafe.getUnsafe();
    static void sink(int x) {}

    public static long lseed = 1;
    public static int iseed = 2;
    public static short sseed = 3;
    public static byte bseed = 4;
    public static long lres = lseed;
    public static int ires = iseed;
    public static short sres = sseed;
    public static byte bres = bseed;

    public static class TestLong {

        private static final byte[] BYTES = new byte[LEN];
        private static final long rawdata = 0xbeef;
        private static final long data;

        static {
            sink(2);
            // Signed immediate byte offset: range -256 to 255
            // Positive immediate byte offset: a multiple of 8 in the range 0 to 32760
            // Other immediate byte offsets can't be encoded in the instruction field.

            // 1030 can't be encoded as "base + offset" mode into the instruction field.
            UNSAFE.putLongUnaligned(BYTES, 1030, rawdata);
            lres += UNSAFE.getLongUnaligned(BYTES, 1030);
            // 127 can be encoded into simm9 field.
            UNSAFE.putLongUnaligned(BYTES, 127, lres);
            lres += UNSAFE.getLongUnaligned(BYTES, 127);
            // 1096 can be encoded into uimm12 field.
            UNSAFE.putLongUnaligned(BYTES, 1096, lres);
            data = UNSAFE.getLongUnaligned(BYTES, 1096);
        }

    }

    public static class TestInt {

        private static final byte[] BYTES = new byte[LEN];
        private static final int rawdata = 0xbeef;
        private static final int data;
        static {
            sink(2);
            // Signed immediate byte offset: range -256 to 255
            // Positive immediate byte offset, a multiple of 4 in the range 0 to 16380
            // Other immediate byte offsets can't be encoded in the instruction field.

            // 274 can't be encoded as "base + offset" mode into the instruction field.
            UNSAFE.putIntUnaligned(BYTES, 274, rawdata);
            ires += UNSAFE.getIntUnaligned(BYTES, 274);
            // 255 can be encoded into simm9 field.
            UNSAFE.putIntUnaligned(BYTES, 255, ires);
            ires += UNSAFE.getIntUnaligned(BYTES, 255);
            // 528 can be encoded into uimm12 field.
            UNSAFE.putIntUnaligned(BYTES, 528, ires);
            data = UNSAFE.getIntUnaligned(BYTES, 528);
        }

    }

    public static class TestShort {

        private static final byte[] BYTES = new byte[LEN];
        private static final short rawdata = (short)0xbeef;
        private static final short data;
        static {
            sink(2);
            // Signed immediate byte offset: range -256 to 255
            // Positive immediate byte offset: a multiple of 2 in the range 0 to 8190
            // Other immediate byte offsets can't be encoded in the instruction field.

            // 257 can't be encoded as "base + offset" mode into the instruction field.
            UNSAFE.putShortUnaligned(BYTES, 257, rawdata);
            sres = (short) (sres + UNSAFE.getShortUnaligned(BYTES, 257));
            // 253 can be encoded into simm9 field.
            UNSAFE.putShortUnaligned(BYTES, 253, sres);
            sres = (short) (sres + UNSAFE.getShortUnaligned(BYTES, 253));
            // 272 can be encoded into uimm12 field.
            UNSAFE.putShortUnaligned(BYTES, 272, sres);
            data = UNSAFE.getShortUnaligned(BYTES, 272);
        }

    }

    public static class TestByte {

        private static final byte[] BYTES = new byte[LEN];
        private static final byte rawdata = (byte)0x3f;
        private static final byte data;
        static {
            sink(2);
            // Signed immediate byte offset: range -256 to 255
            // Positive immediate byte offset: range 0 to 4095
            // Other immediate byte offsets can't be encoded in the instruction field.

            // 272 can be encoded into simm9 field.
            UNSAFE.putByte(BYTES, 272, rawdata);
            bres = (byte) (bres + UNSAFE.getByte(BYTES, 272));
            // 53 can be encoded into simm9 field.
            UNSAFE.putByte(BYTES, 53, bres);
            bres = (byte) (bres + UNSAFE.getByte(BYTES, 53));
            // 1027 can be encoded into uimm12 field.
            UNSAFE.putByte(BYTES, 1027, bres);
            data = UNSAFE.getByte(BYTES, 1027);
        }

    }

    static void test() {
        TestLong ta = new TestLong();
        Asserts.assertEquals(ta.data, (ta.rawdata + lseed) * 2, "putUnaligned long failed!");

        TestInt tb = new TestInt();
        Asserts.assertEquals(tb.data, (tb.rawdata + iseed) * 2, "putUnaligned int failed!");

        TestShort tc = new TestShort();
        Asserts.assertEquals(tc.data, (short) (((short) (tc.rawdata + sseed)) * 2), "putUnaligned short failed!");

        TestByte td = new TestByte();
        Asserts.assertEquals(td.data, (byte) (((byte) (td.rawdata + bseed)) * 2), "put byte failed!");
    }

    public static void main(String[] strArr) {
        test();
    }
}
