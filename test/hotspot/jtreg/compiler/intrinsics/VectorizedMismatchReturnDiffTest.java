/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026 Alibaba Group Holding Limited. All Rights Reserved.
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

import jdk.internal.misc.Unsafe;
import jdk.internal.util.ArraysSupport;
import jdk.test.lib.Asserts;

/*
 * @test
 * @summary Verify that both the vectorizedMismatch intrinsic and Java
 *          implementation return same value
 * @requires vm.opt.final.UseVectorizedMismatchIntrinsic == true
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.util
 * @library /test/lib /
 * @run main/othervm -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=compileonly,compiler.intrinsics.VectorizedMismatchReturnDiffTest::testComp*
 *                   -Xbatch -XX:-TieredCompilation
 *                   compiler.intrinsics.VectorizedMismatchReturnDiffTest
 */

public class VectorizedMismatchReturnDiffTest {

    private static final int LOG2_ARRAY_LONG_INDEX_SCALE = ArraysSupport.LOG2_ARRAY_LONG_INDEX_SCALE;
    private static final int LOG2_ARRAY_INT_INDEX_SCALE = ArraysSupport.LOG2_ARRAY_INT_INDEX_SCALE;

    private byte[] byte_a = new byte[512];
    private byte[] byte_b = new byte[512];
    private char[] char_a = new char[256];
    private char[] char_b = new char[256];
    private short[] short_a = new short[256];
    private short[] short_b = new short[256];
    private int[] int_a = new int[128];
    private int[] int_b = new int[128];
    private float[] float_a = new float[128];
    private float[] float_b = new float[128];
    private long[] long_a = new long[64];
    private long[] long_b = new long[64];
    private double[] double_a = new double[64];
    private double[] double_b = new double[64];

    // ==================================================================================
    // Test methods
    //    testCompXXX are methods compiled by c2
    //    testJavaXXX are methods for verification
    // ==================================================================================

    int testCompByte(int length) {
        long offset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(byte_a, offset, byte_b, offset, length,
                ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE);
    }

    int testJavaByte(int length) {
        long offset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(byte_a, offset, byte_b, offset, length,
                ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE);
    }

    int testCompChar(int length) {
        long offset = Unsafe.ARRAY_CHAR_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(char_a, offset, char_b, offset, length,
                ArraysSupport.LOG2_ARRAY_CHAR_INDEX_SCALE);
    }

    int testJavaChar(int length) {
        long offset = Unsafe.ARRAY_CHAR_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(char_a, offset, char_b, offset, length,
                ArraysSupport.LOG2_ARRAY_CHAR_INDEX_SCALE);
    }

    int testCompShort(int length) {
        long offset = Unsafe.ARRAY_SHORT_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(short_a, offset, short_b, offset, length,
                ArraysSupport.LOG2_ARRAY_SHORT_INDEX_SCALE);
    }

    int testJavaShort(int length) {
        long offset = Unsafe.ARRAY_SHORT_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(short_a, offset, short_b, offset, length,
                ArraysSupport.LOG2_ARRAY_SHORT_INDEX_SCALE);
    }

    int testCompInt(int length) {
        long offset = Unsafe.ARRAY_INT_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(int_a, offset, int_b, offset, length,
                ArraysSupport.LOG2_ARRAY_INT_INDEX_SCALE);
    }

    int testJavaInt(int length) {
        long offset = Unsafe.ARRAY_INT_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(int_a, offset, int_b, offset, length,
                ArraysSupport.LOG2_ARRAY_INT_INDEX_SCALE);
    }

    int testCompFloat(int length) {
        long offset = Unsafe.ARRAY_FLOAT_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(float_a, offset, float_b, offset, length,
                ArraysSupport.LOG2_ARRAY_FLOAT_INDEX_SCALE);
    }

    int testJavaFloat(int length) {
        long offset = Unsafe.ARRAY_FLOAT_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(float_a, offset, float_b, offset, length,
                ArraysSupport.LOG2_ARRAY_FLOAT_INDEX_SCALE);
    }

    int testCompLong(int length) {
        long offset = Unsafe.ARRAY_LONG_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(long_a, offset, long_b, offset, length,
                ArraysSupport.LOG2_ARRAY_LONG_INDEX_SCALE);
    }

    int testJavaLong(int length) {
        long offset = Unsafe.ARRAY_LONG_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(long_a, offset, long_b, offset, length,
                ArraysSupport.LOG2_ARRAY_LONG_INDEX_SCALE);
    }

    int testCompDouble(int length) {
        long offset = Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(double_a, offset, double_b, offset, length,
                ArraysSupport.LOG2_ARRAY_DOUBLE_INDEX_SCALE);
    }

    int testJavaDouble(int length) {
        long offset = Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
        return ArraysSupport.vectorizedMismatch(double_a, offset, double_b, offset, length,
                ArraysSupport.LOG2_ARRAY_DOUBLE_INDEX_SCALE);
    }

    // ==================================================================================
    // Main
    // ==================================================================================

    interface MismatchFn {
        int call(int length);
    }

    static int checkResults(String typeName, MismatchFn fn1, MismatchFn fn2, int minLen, int maxLen) {
        int diffs = 0;
        for (int len = minLen; len <= maxLen; len++) {
            int ret1 = fn1.call(len);
            int ret2 = fn2.call(len);
            if (ret1 != ret2) {
                System.out.println("  UNMATCHED: length=" + len + ", intrinsic returns " + ret1 + ", java method returns " + ret2);
                diffs++;
            }
        }
        return diffs;
    }

    public static void main(String[] args) {
        VectorizedMismatchReturnDiffTest t = new VectorizedMismatchReturnDiffTest();

        // Warmup
        for (int i = 0; i < 20_000; i++) {
            t.testCompByte(64);
            t.testCompChar(32);
            t.testCompShort(32);
            t.testCompInt(16);
            t.testCompFloat(16);
            t.testCompLong(8);
            t.testCompDouble(8);
        }

        int diffs = 0;

        System.out.println("--- byte (log2Scale=0) ---");
        diffs += checkResults("byte", t::testCompByte, t::testJavaByte, 1, 128);

        System.out.println("--- char (log2Scale=1) ---");
        diffs += checkResults("char", t::testCompChar, t::testJavaChar, 1, 64);

        System.out.println("--- short (log2Scale=1) ---");
        diffs += checkResults("short", t::testCompShort, t::testJavaShort, 1, 64);

        System.out.println("--- int (log2Scale=2) ---");
        diffs += checkResults("int", t::testCompInt, t::testJavaInt, 1, 32);

        System.out.println("--- float (log2Scale=2) ---");
        diffs += checkResults("float", t::testCompFloat, t::testJavaFloat, 1, 32);

        System.out.println("--- long (log2Scale=3) ---");
        diffs += checkResults("long", t::testCompLong, t::testJavaLong, 1, 16);

        System.out.println("--- double (log2Scale=3) ---");
        diffs += checkResults("double", t::testCompDouble, t::testJavaDouble, 1, 16);

        Asserts.assertEQ(diffs, 0);
    }
}
