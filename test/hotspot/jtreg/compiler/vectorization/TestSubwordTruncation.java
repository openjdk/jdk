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
 * @bug 8350177
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


    public static void main(String[] args) {
        TestFramework.run();
    }
}

