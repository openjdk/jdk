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

package compiler.vectorization;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;
import compiler.lib.generators.*;

/*
 * @test
 * @bug 8350177 8362171
 * @summary Ensure that truncation of subword vectors produces correct results
 * @library /test/lib /
 * @run driver compiler.vectorization.TestSubwordTruncation
 */

public class TestSubwordTruncation {
    private static final RestrictableGenerator<Integer> G = Generators.G.ints();
    private static final int SIZE = 10000;

    @Setup
    static Object[] setupShortArray() {
        short[] arr = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            arr[i] = G.next().shortValue();
        }

        return new Object[] { arr };
    }


    @Setup
    static Object[] setupByteArray() {
        byte[] arr = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            arr[i] = G.next().byteValue();
        }

        return new Object[] { arr };
    }

    @Setup
    static Object[] setupCharArray() {
        char[] arr = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            arr[i] = (char) G.next().shortValue();
        }

        return new Object[] { arr };
    }

    // Shorts

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "=0" })
    @Arguments(setup = "setupShortArray")
    public Object[] testShortLeadingZeros(short[] in) {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
             res[i] = (short) Integer.numberOfLeadingZeros(in[i]);
        }

        return new Object[] { in, res };
    }

    @Check(test = "testShortLeadingZeros")
    public void checkTestShortLeadingZeros(Object[] vals) {
        short[] in = (short[]) vals[0];
        short[] res = (short[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            short val = (short) Integer.numberOfLeadingZeros(in[i]);
            if (res[i] != val) {
                throw new IllegalStateException("Expected " + val + " but got " + res[i] + " for " + in[i]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "=0" })
    @Arguments(setup = "setupShortArray")
    public Object[] testShortTrailingZeros(short[] in) {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
             res[i] = (short) Integer.numberOfTrailingZeros(in[i]);
        }

        return new Object[] { in, res };
    }

    @Check(test = "testShortTrailingZeros")
    public void checkTestShortTrailingZeros(Object[] vals) {
        short[] in = (short[]) vals[0];
        short[] res = (short[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            short val = (short) Integer.numberOfTrailingZeros(in[i]);
            if (res[i] != val) {
                throw new IllegalStateException("Expected " + val + " but got " + res[i] + " for " + in[i]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "=0" })
    @Arguments(setup = "setupShortArray")
    public Object[] testShortReverse(short[] in) {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
             res[i] = (short) Integer.reverse(in[i]);
        }

        return new Object[] { in, res };
    }

    @Check(test = "testShortReverse")
    public void checkTestShortReverse(Object[] vals) {
        short[] in = (short[]) vals[0];
        short[] res = (short[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            short val = (short) Integer.reverse(in[i]);
            if (res[i] != val) {
                throw new IllegalStateException("Expected " + val + " but got " + res[i] + " for " + in[i]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "=0" })
    @Arguments(setup = "setupShortArray")
    public Object[] testShortBitCount(short[] in) {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
             res[i] = (short) Integer.bitCount(in[i]);
        }

        return new Object[] { in, res };
    }

    @Check(test = "testShortBitCount")
    public void checkTestShortBitCount(Object[] vals) {
        short[] in = (short[]) vals[0];
        short[] res = (short[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            short val = (short) Integer.bitCount(in[i]);
            if (res[i] != val) {
                throw new IllegalStateException("Expected " + val + " but got " + res[i] + " for " + in[i]);
            }
        }
    }

    // Chars

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "=0" })
    @Arguments(setup = "setupCharArray")
    public Object[] testCharLeadingZeros(char[] in) {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
             res[i] = (char) Integer.numberOfLeadingZeros(in[i]);
        }

        return new Object[] { in, res };
    }

    @Check(test = "testCharLeadingZeros")
    public void checkTestCharLeadingZeros(Object[] vals) {
        char[] in = (char[]) vals[0];
        char[] res = (char[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            char val = (char) Integer.numberOfLeadingZeros(in[i]);
            if (res[i] != val) {
                throw new IllegalStateException("Expected " + val + " but got " + res[i] + " for " + in[i]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "=0" })
    @Arguments(setup = "setupCharArray")
    public Object[] testCharTrailingZeros(char[] in) {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
             res[i] = (char) Integer.numberOfTrailingZeros(in[i]);
        }

        return new Object[] { in, res };
    }

    @Check(test = "testCharTrailingZeros")
    public void checkTestCharTrailingZeros(Object[] vals) {
        char[] in = (char[]) vals[0];
        char[] res = (char[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            char val = (char) Integer.numberOfTrailingZeros(in[i]);
            if (res[i] != val) {
                throw new IllegalStateException("Expected " + val + " but got " + res[i] + " for " + in[i]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "=0" })
    @Arguments(setup = "setupCharArray")
    public Object[] testCharReverse(char[] in) {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
             res[i] = (char) Integer.reverse(in[i]);
        }

        return new Object[] { in, res };
    }

    @Check(test = "testCharReverse")
    public void checkTestCharReverse(Object[] vals) {
        char[] in = (char[]) vals[0];
        char[] res = (char[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            char val = (char) Integer.reverse(in[i]);
            if (res[i] != val) {
                throw new IllegalStateException("Expected " + val + " but got " + res[i] + " for " + in[i]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "=0" })
    @Arguments(setup = "setupCharArray")
    public Object[] testCharBitCount(char[] in) {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
             res[i] = (char) Integer.bitCount(in[i]);
        }

        return new Object[] { in, res };
    }

    @Check(test = "testCharBitCount")
    public void checkTestCharBitCount(Object[] vals) {
        char[] in = (char[]) vals[0];
        char[] res = (char[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            char val = (char) Integer.bitCount(in[i]);
            if (res[i] != val) {
                throw new IllegalStateException("Expected " + val + " but got " + res[i] + " for " + in[i]);
            }
        }
    }

    // Bytes

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "=0" })
    @Arguments(setup = "setupByteArray")
    public Object[] testByteLeadingZeros(byte[] in) {
        byte[] res = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
             res[i] = (byte) Integer.numberOfLeadingZeros(in[i]);
        }

        return new Object[] { in, res };
    }

    @Check(test = "testByteLeadingZeros")
    public void checkTestByteLeadingZeros(Object[] vals) {
        byte[] in = (byte[]) vals[0];
        byte[] res = (byte[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            byte val = (byte) Integer.numberOfLeadingZeros(in[i]);
            if (res[i] != val) {
                throw new IllegalStateException("Expected " + val + " but got " + res[i] + " for " + in[i]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "=0" })
    @Arguments(setup = "setupByteArray")
    public Object[] testByteTrailingZeros(byte[] in) {
        byte[] res = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
             res[i] = (byte) Integer.numberOfTrailingZeros(in[i]);
        }

        return new Object[] { in, res };
    }

    @Check(test = "testByteTrailingZeros")
    public void checkTestByteTrailingZeros(Object[] vals) {
        byte[] in = (byte[]) vals[0];
        byte[] res = (byte[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            byte val = (byte) Integer.numberOfTrailingZeros(in[i]);
            if (res[i] != val) {
                throw new IllegalStateException("Expected " + val + " but got " + res[i] + " for " + in[i]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "=0" })
    @Arguments(setup = "setupByteArray")
    public Object[] testByteReverse(byte[] in) {
        byte[] res = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
             res[i] = (byte) Integer.reverse(in[i]);
        }

        return new Object[] { in, res };
    }

    @Check(test = "testByteReverse")
    public void checkTestByteReverse(Object[] vals) {
        byte[] in = (byte[]) vals[0];
        byte[] res = (byte[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            byte val = (byte) Integer.reverse(in[i]);
            if (res[i] != val) {
                throw new IllegalStateException("Expected " + val + " but got " + res[i] + " for " + in[i]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "=0" })
    @Arguments(setup = "setupByteArray")
    public Object[] testByteBitCount(byte[] in) {
        byte[] res = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
             res[i] = (byte) Integer.bitCount(in[i]);
        }

        return new Object[] { in, res };
    }

    @Check(test = "testByteBitCount")
    public void checkTestByteBitCount(Object[] vals) {
        byte[] in = (byte[]) vals[0];
        byte[] res = (byte[]) vals[1];

        for (int i = 0; i < SIZE; i++) {
            byte val = (byte) Integer.bitCount(in[i]);
            if (res[i] != val) {
                throw new IllegalStateException("Expected " + val + " but got " + res[i] + " for " + in[i]);
            }
        }
    }

    int intField;
    short shortField;

    @Test
    @IR(counts = { IRNode.MOD_I, ">0" })
    public void testMod() {
        for (int i = 1; i < SIZE; i++) {
            for (int j = 1; j < 204; j++) {
                shortField %= intField | 1;
            }
        }
    }

    @Test
    @IR(counts = { IRNode.CMP_LT_MASK, ">0" })
    @Arguments(setup = "setupByteArray")
    public Object[] testCmpLTMask(byte[] in) {
        char[] res = new char[SIZE];

        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (in[i] >= 0 ? in[i] : 256 + in[i]);
        }

        return new Object[] { in, res };
    }

    @Test
    @IR(counts = { IRNode.ROUND_F, ">0" })
    @Arguments(setup = "setupByteArray")
    public Object[] testRoundF(byte[] in) {
        short[] res = new short[SIZE];

        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) Math.round(in[i] * 10.F);
        }

        return new Object[] { in, res };
    }

    @Test
    @IR(counts = { IRNode.ROUND_D, ">0" })
    @Arguments(setup = "setupByteArray")
    public Object[] testRoundD(byte[] in) {
        short[] res = new short[SIZE];

        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) Math.round(in[i] * 10.0);
        }

        return new Object[] { in, res };
    }

    public static void main(String[] args) {
        TestFramework.run();
    }
}

