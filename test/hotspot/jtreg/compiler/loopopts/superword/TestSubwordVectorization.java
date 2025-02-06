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

package compiler.loopopts.superword;


import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8342095
 * @key randomness
 * @summary Ensure that vectorization of conversions between subword types works as expected.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestSubwordVectorization
 */

public class TestSubwordVectorization {
    private static final Generator<Integer> G = Generators.G.ints();

    private static final int SIZE = 1024;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Setup
    static Object[] setupIntArray() {
        int[] res = new int[SIZE];

        for (int i = 0; i < SIZE; i++) {
            res[i] = G.next();
        }

        return new Object[] { res };
    }

    @Setup
    static Object[] setupShortArray() {
        short[] res = new short[SIZE];

        for (int i = 0; i < SIZE; i++) {
            res[i] = G.next().shortValue();
        }

        return new Object[] { res };
    }

    @Setup
    static Object[] setupByteArray() {
        byte[] res = new byte[SIZE];

        for (int i = 0; i < SIZE; i++) {
            res[i] = G.next().byteValue();
        }

        return new Object[] { res };
    }

    // Narrowing

    @Test
    @IR(applyIfCPUFeature = { "avx", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_I2S, IRNode.VECTOR_SIZE + "min(max_int, max_short)", ">0" })
    @Arguments(setup = "setupIntArray")
    public Object[] testIntToShort(int[] ints) {
        short[] res = new short[SIZE];

        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) ints[i];
        }

        return new Object[] { ints, res };
    }

    @Check(test = "testIntToShort")
    public void checkTestIntToShort(Object[] vals) {
        int[] ints = (int[]) vals[0];
        short[] res = (short[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            short value = (short) ints[i];

            if (res[i] != value) {
                throw new IllegalStateException("Int to short test failed: Expected " + value + " but got " + res[i]);
            }
        }
    }

    @Test
    @IR(applyIfCPUFeature = { "avx", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_I2B, IRNode.VECTOR_SIZE + "min(max_int, max_byte)", ">0" })
    @Arguments(setup = "setupIntArray")
    public Object[] testIntToByte(int[] ints) {
        byte[] res = new byte[SIZE];

        for (int i = 0; i < SIZE; i++) {
            res[i] = (byte) ints[i];
        }

        return new Object[] { ints, res };
    }

    @Check(test = "testIntToByte")
    public void checkTestIntToByte(Object[] vals) {
        int[] ints = (int[]) vals[0];
        byte[] res = (byte[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            byte value = (byte) ints[i];

            if (res[i] != value) {
                throw new IllegalStateException("Int to byte test failed: Expected " + value + " but got " + res[i]);
            }
        }
    }

    @Test
    @IR(applyIfCPUFeature = { "avx", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_S2B, IRNode.VECTOR_SIZE + "min(max_short, max_byte)", ">0" })
    @Arguments(setup = "setupShortArray")
    public Object[] testShortToByte(short[] shorts) {
        byte[] res = new byte[SIZE];

        for (int i = 0; i < SIZE; i++) {
            res[i] = (byte) shorts[i];
        }

        return new Object[] { shorts, res };
    }

    @Check(test = "testShortToByte")
    public void checkTestShortToByte(Object[] vals) {
        short[] shorts = (short[]) vals[0];
        byte[] res = (byte[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            byte value = (byte) shorts[i];

            if (res[i] != value) {
                throw new IllegalStateException("Short to byte test failed: Expected " + value + " but got " + res[i]);
            }
        }
    }

    // Widening

    @Test
    @IR(applyIfCPUFeature = { "avx", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_S2I, IRNode.VECTOR_SIZE + "min(max_short, max_int)", ">0" })
    @Arguments(setup = "setupShortArray")
    public Object[] testShortToInt(short[] shorts) {
        int[] res = new int[SIZE];

        for (int i = 0; i < SIZE; i++) {
            res[i] = shorts[i];
        }

        return new Object[] { shorts, res };
    }

    @Check(test = "testShortToInt")
    public void checkTestShortToInt(Object[] vals) {
        short[] shorts = (short[]) vals[0];
        int[] res = (int[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            int value = shorts[i];

            if (res[i] != value) {
                throw new IllegalStateException("Short to int test failed: Expected " + value + " but got " + res[i]);
            }
        }
    }

    @Test
    @IR(applyIfCPUFeature = { "avx", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_B2I, IRNode.VECTOR_SIZE + "min(max_byte, max_int)", ">0" })
    @Arguments(setup = "setupByteArray")
    public Object[] testByteToInt(byte[] bytes) {
        int[] res = new int[SIZE];

        for (int i = 0; i < SIZE; i++) {
            res[i] = bytes[i];
        }

        return new Object[] { bytes, res };
    }

    @Check(test = "testByteToInt")
    public void checkTestByteToInt(Object[] vals) {
        byte[] bytes = (byte[]) vals[0];
        int[] res = (int[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            int value = bytes[i];

            if (res[i] != value) {
                throw new IllegalStateException("Byte to int test failed: Expected " + value + " but got " + res[i]);
            }
        }
    }

    @Test
    @IR(applyIfCPUFeature = { "avx", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_B2S, IRNode.VECTOR_SIZE + "min(max_byte, max_short)", ">0" })
    @Arguments(setup = "setupByteArray")
    public Object[] testByteToShort(byte[] bytes) {
        short[] res = new short[SIZE];

        for (int i = 0; i < SIZE; i++) {
            res[i] = bytes[i];
        }

        return new Object[] { bytes, res };
    }

    @Check(test = "testByteToShort")
    public void checkTestByteToShort(Object[] vals) {
        byte[] bytes = (byte[]) vals[0];
        short[] res = (short[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            short value = bytes[i];

            if (res[i] != value) {
                throw new IllegalStateException("Byte to short test failed: Expected " + value + " but got " + res[i]);
            }
        }
    }
}
