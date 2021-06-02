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

package compiler.intrinsics;

/*
 * @test
 * @bug 8130832
 * @modules java.base/jdk.internal.util
 *
 *  @run main/othervm
 *       -XX:CompileCommand=quiet -XX:CompileCommand=compileonly,*::test*
 *       compiler.intrinsics.VectorizedMismatchTest
 */

import jdk.internal.misc.Unsafe;
import jdk.internal.util.ArraysSupport;

public class VectorizedMismatchTest {
    private boolean[] boolean_a = new boolean[128];
    private boolean[] boolean_b = new boolean[128];

    int testBooleanConstantLength() {
        boolean[] obja = boolean_a;
        boolean[] objb = boolean_b;
        long offset = Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
        int  scale  = ArraysSupport.LOG2_ARRAY_BOOLEAN_INDEX_SCALE;
        return ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  0, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  1, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 63, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 64, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,128, scale);
    }

    private byte[] byte_a = new byte[128];
    private byte[] byte_b = new byte[128];

    int testByteConstantLength() {
        byte[] obja = byte_a;
        byte[] objb = byte_b;
        long offset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
        int  scale  = ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE;
        return ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  0, scale) +
                ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  1, scale) +
                ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 63, scale) +
                ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 64, scale) +
                ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,128, scale);
    }

    private short[] short_a = new short[64];
    private short[] short_b = new short[64];

    int testShortConstantLength() {
        short[] obja = short_a;
        short[] objb = short_b;
        long offset = Unsafe.ARRAY_SHORT_BASE_OFFSET;
        int  scale  = ArraysSupport.LOG2_ARRAY_SHORT_INDEX_SCALE;
        return ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  0, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  1, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 31, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 32, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 64, scale);
    }

    private char[] char_a = new char[64];
    private char[] char_b = new char[64];

    int testCharConstantLength() {
        char[] obja = char_a;
        char[] objb = char_b;
        long offset = Unsafe.ARRAY_CHAR_BASE_OFFSET;
        int  scale  = ArraysSupport.LOG2_ARRAY_CHAR_INDEX_SCALE;
        return ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  0, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  1, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 31, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 32, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 64, scale);
    }

    private int[] int_a = new int[32];
    private int[] int_b = new int[32];

    int testIntConstantLength() {
        int[] obja = int_a;
        int[] objb = int_b;
        long offset = Unsafe.ARRAY_INT_BASE_OFFSET;
        int  scale  = ArraysSupport.LOG2_ARRAY_INT_INDEX_SCALE;
        return ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  0, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  1, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 15, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 16, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 32, scale);
    }

    private float[] float_a = new float[32];
    private float[] float_b = new float[32];

    int testFloatConstantLength() {
        float[] obja = float_a;
        float[] objb = float_b;
        long offset = Unsafe.ARRAY_FLOAT_BASE_OFFSET;
        int  scale  = ArraysSupport.LOG2_ARRAY_FLOAT_INDEX_SCALE;
        return ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  0, scale) +
                ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  1, scale) +
                ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 15, scale) +
                ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 16, scale) +
                ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 32, scale);
    }

    private long[] long_a = new long[16];
    private long[] long_b = new long[16];

    int testLongConstantLength() {
        long[] obja = long_a;
        long[] objb = long_b;
        long offset = Unsafe.ARRAY_LONG_BASE_OFFSET;
        int  scale  = ArraysSupport.LOG2_ARRAY_LONG_INDEX_SCALE;
        return ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  0, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  1, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  7, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  8, scale) +
               ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 16, scale);
    }

    private double[] double_a = new double[16];
    private double[] double_b = new double[16];

    int testDoubleConstantLength() {
        double[] obja = double_a;
        double[] objb = double_b;
        long offset = Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
        int  scale  = ArraysSupport.LOG2_ARRAY_DOUBLE_INDEX_SCALE;
        return ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  0, scale) +
                ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  1, scale) +
                ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  7, scale) +
                ArraysSupport.vectorizedMismatch(obja, offset, objb, offset,  8, scale) +
                ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, 16, scale);
    }

    public static void main(String[] args) {
        VectorizedMismatchTest t = new VectorizedMismatchTest();
        for (int i = 0; i < 20_000; i++) {
            t.testBooleanConstantLength();
            t.testByteConstantLength();
            t.testShortConstantLength();
            t.testCharConstantLength();
            t.testIntConstantLength();
            t.testFloatConstantLength();
            t.testLongConstantLength();
            t.testDoubleConstantLength();
        }
    }
}
