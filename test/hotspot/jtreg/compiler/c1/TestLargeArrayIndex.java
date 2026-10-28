/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All rights reserved.
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
 * @bug 8396020
 * @summary C1 must handle constant array index whose byte offsets exceed the signed 32-bit range
 * @requires vm.compiler1.enabled
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,compiler.c1.TestLargeArrayIndex::test*
 *                   compiler.c1.TestLargeArrayIndex
 */

package compiler.c1;

public class TestLargeArrayIndex {
    static void testFloatNegative(float[] values) {
        try {
            values[Integer.MIN_VALUE] = -0.0f;
            throw new RuntimeException("ArrayIndexOutOfBoundsException expected");
        } catch (ArrayIndexOutOfBoundsException expected) {
        }
    }

    static void testFloatPositive(float[] values) {
        try {
            values[Integer.MAX_VALUE] = 0.0f;
            throw new RuntimeException("ArrayIndexOutOfBoundsException expected");
        } catch (ArrayIndexOutOfBoundsException expected) {
        }
    }

    static void testLongNegative(long[] values) {
        try {
            values[Integer.MIN_VALUE] = 0L;
            throw new RuntimeException("ArrayIndexOutOfBoundsException expected");
        } catch (ArrayIndexOutOfBoundsException expected) {
        }
    }

    static void testBytePositive(byte[] values) {
        try {
            values[Integer.MAX_VALUE] = 0;
            throw new RuntimeException("ArrayIndexOutOfBoundsException expected");
        } catch (ArrayIndexOutOfBoundsException expected) {
        }
    }

    public static void main(String[] args) {
        float[] floats = new float[6];
        testFloatNegative(floats);
        testFloatPositive(floats);
        testLongNegative(new long[6]);
        testBytePositive(new byte[6]);
    }
}
