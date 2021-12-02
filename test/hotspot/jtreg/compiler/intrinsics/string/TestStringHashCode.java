/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Validates String.hashCode intrinsic.
 * @library /compiler/patches /test/lib
 *
 * @build java.base/java.lang.Helper
 * @run main/othervm -Xcomp compiler.intrinsics.string.TestStringHashCode
 */

package compiler.intrinsics.string;

import java.nio.ByteOrder;
import java.util.random.RandomGenerator;
import jdk.test.lib.Asserts;

public class TestStringHashCode {
    static final int INVOCATIONS = 1_000_000;
    static final int[] STRING_LENGTHS = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000};

    static int hashCodeLatin1Vanilla(byte[] value) {
        int sum = 0;
        for (byte b : value) {
            sum = sum * 31 + Byte.toUnsignedInt(b);
        }
        return sum;
    }

    static int hashCodeUTF16Vanilla(byte[] value) {
        int sum = 0;
        int length = value.length / 2;
        for (int i = 0; i < length; i++) {
            sum = sum * 31 + getCharUTF16(value, i);
        }
        return sum;
    }

    static int getCharUTF16(byte[] val, int index) {
        index *= 2;
        int HI_BYTE_SHIFT, LO_BYTE_SHIFT;
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            HI_BYTE_SHIFT = Byte.SIZE;
            LO_BYTE_SHIFT = 0;
        } else {
            HI_BYTE_SHIFT = 0;
            LO_BYTE_SHIFT = Byte.SIZE;
        }
        return ((Byte.toUnsignedInt(val[index])     << HI_BYTE_SHIFT) |
                (Byte.toUnsignedInt(val[index + 1]) << LO_BYTE_SHIFT));
    }

    public static void main(String[] args) {
        var random = RandomGenerator.getDefault();
        for (int length : STRING_LENGTHS) {
            byte[] value = new byte[length];
            for (int i = 0; i < INVOCATIONS; i++) {
                random.nextBytes(value);
                Asserts.assertEquals(hashCodeLatin1Vanilla(value), Helper.hashCodeLatin1(value));
            }
        }

        for (int length : STRING_LENGTHS) {
            byte[] value = new byte[length * 2];
            for (int i = 0; i < INVOCATIONS; i++) {
                random.nextBytes(value);
                Asserts.assertEquals(hashCodeUTF16Vanilla(value), Helper.hashCodeUTF16(value));
            }
        }
    }
}
