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
 * @test
 * @summary Test functionality of IntGenerator implementations.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver verify.tests.TestVerify
 */

package verify.tests;

import java.lang.foreign.*;
import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.verify.*;

public class TestVerify {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        // Test consecutive memory: array, MemorySegment, etc.
        testArrayByte();
        testArrayChar();
        testArrayShort();
        testArrayInt();
        testArrayLong();
        testArrayFloat();
        testArrayDouble();
        testNativeMemorySegment();

        // Test recursive data: Object array of values, etc.
        testRecursive();
    }

    public static void testArrayByte() {
        byte[] a = new byte[1000];
        byte[] b = new byte[1001];
        byte[] c = new byte[1000];

        Verify.checkEQ(a, a);
        Verify.checkEQ(b, b);
        Verify.checkEQ(a, c);
        Verify.checkEQ(c, a);

        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(a));
        Verify.checkEQ(MemorySegment.ofArray(b), MemorySegment.ofArray(b));
        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
        Verify.checkEQ(MemorySegment.ofArray(c), MemorySegment.ofArray(a));

        // Size mismatch
        checkNE(a, b);

        // Size mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(b));

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        checkNE(a, c);

        // Value mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
    }


    public static void testArrayShort() {
        short[] a = new short[1000];
        short[] b = new short[1001];
        short[] c = new short[1000];

        Verify.checkEQ(a, a);
        Verify.checkEQ(b, b);
        Verify.checkEQ(a, c);
        Verify.checkEQ(c, a);

        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(a));
        Verify.checkEQ(MemorySegment.ofArray(b), MemorySegment.ofArray(b));
        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
        Verify.checkEQ(MemorySegment.ofArray(c), MemorySegment.ofArray(a));

        // Size mismatch
        checkNE(a, b);

        // Size mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(b));

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        checkNE(a, c);

        // Value mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
    }


    public static void testArrayChar() {
        char[] a = new char[1000];
        char[] b = new char[1001];
        char[] c = new char[1000];

        Verify.checkEQ(a, a);
        Verify.checkEQ(b, b);
        Verify.checkEQ(a, c);
        Verify.checkEQ(c, a);

        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(a));
        Verify.checkEQ(MemorySegment.ofArray(b), MemorySegment.ofArray(b));
        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
        Verify.checkEQ(MemorySegment.ofArray(c), MemorySegment.ofArray(a));

        // Size mismatch
        checkNE(a, b);

        // Size mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(b));

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        checkNE(a, c);

        // Value mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
    }


    public static void testArrayInt() {
        int[] a = new int[1000];
        int[] b = new int[1001];
        int[] c = new int[1000];

        Verify.checkEQ(a, a);
        Verify.checkEQ(b, b);
        Verify.checkEQ(a, c);
        Verify.checkEQ(c, a);

        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(a));
        Verify.checkEQ(MemorySegment.ofArray(b), MemorySegment.ofArray(b));
        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
        Verify.checkEQ(MemorySegment.ofArray(c), MemorySegment.ofArray(a));

        // Size mismatch
        checkNE(a, b);

        // Size mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(b));

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        checkNE(a, c);

        // Value mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
    }


    public static void testArrayLong() {
        long[] a = new long[1000];
        long[] b = new long[1001];
        long[] c = new long[1000];

        Verify.checkEQ(a, a);
        Verify.checkEQ(b, b);
        Verify.checkEQ(a, c);
        Verify.checkEQ(c, a);

        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(a));
        Verify.checkEQ(MemorySegment.ofArray(b), MemorySegment.ofArray(b));
        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
        Verify.checkEQ(MemorySegment.ofArray(c), MemorySegment.ofArray(a));

        // Size mismatch
        checkNE(a, b);

        // Size mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(b));

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        checkNE(a, c);

        // Value mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
    }


    public static void testArrayFloat() {
        float[] a = new float[1000];
        float[] b = new float[1001];
        float[] c = new float[1000];

        Verify.checkEQ(a, a);
        Verify.checkEQ(b, b);
        Verify.checkEQ(a, c);
        Verify.checkEQ(c, a);

        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(a));
        Verify.checkEQ(MemorySegment.ofArray(b), MemorySegment.ofArray(b));
        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
        Verify.checkEQ(MemorySegment.ofArray(c), MemorySegment.ofArray(a));

        // Size mismatch
        checkNE(a, b);

        // Size mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(b));

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        checkNE(a, c);

        // Value mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
    }


    public static void testArrayDouble() {
        double[] a = new double[1000];
        double[] b = new double[1001];
        double[] c = new double[1000];

        Verify.checkEQ(a, a);
        Verify.checkEQ(b, b);
        Verify.checkEQ(a, c);
        Verify.checkEQ(c, a);

        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(a));
        Verify.checkEQ(MemorySegment.ofArray(b), MemorySegment.ofArray(b));
        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
        Verify.checkEQ(MemorySegment.ofArray(c), MemorySegment.ofArray(a));

        // Size mismatch
        checkNE(a, b);

        // Size mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(b));

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        checkNE(a, c);

        // Value mismatch
        checkNE(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
    }

    public static void testNativeMemorySegment() {
        MemorySegment a = Arena.ofAuto().allocate(1000, 1);
        MemorySegment b = Arena.ofAuto().allocate(1001, 1);
        MemorySegment c = Arena.ofAuto().allocate(1000, 1);

        Verify.checkEQ(a, a);
        Verify.checkEQ(b, b);
        Verify.checkEQ(a, c);
        Verify.checkEQ(c, a);

        // Size mismatch
        checkNE(a, b);

        c.set(ValueLayout.JAVA_BYTE, RANDOM.nextLong(c.byteSize()), (byte)1);

        // Value mismatch
        checkNE(a, c);
    }

    public static void testRecursive() {
        Verify.checkEQ(null, null);

        // Null mismatch
        checkNE(42, null);

        byte[] a = new byte[1000];
        int[]  b = new int[1000];
        int[]  c = new int[1001];
        int[]  d = new int[1000];

        Object[] o1 = new Object[]{a, a};
        Object[] o2 = new Object[]{a, a, a};
        Object[] o3 = new Object[]{a, a, null};
        Object[] o4 = new Object[]{a, a, b};
        Object[] o5 = new Object[]{a, a, c};
        Object[] o6 = new Object[]{a, a, d};

        Verify.checkEQ(o1, o1);
        Verify.checkEQ(o2, o2);
        Verify.checkEQ(o3, o3);
        Verify.checkEQ(o4, o6);

        // Size mismatch
        checkNE(o1, o2);

        // First level value mismatch: a vs null on position 2
        checkNE(o2, o3);

        // First level class mismatch: byte[] vs int[]
        checkNE(o2, o4);

        // Second level length mismatch on arrays b and c.
        checkNE(o4, o5);

        d[RANDOM.nextInt(d.length)] = 1;

        // Second level value mismatch between b and d.
        checkNE(o4, o6);

        // Now test all primitive array types.
        byte[]   aB = new byte[100];
        char[]   aC = new char[100];
        short[]  aS = new short[100];
        int[]    aI = new int[100];
        long[]   aL = new long[100];
        float[]  aF = new float[100];
        double[] aD = new double[100];

        Verify.checkEQ(new Object[] {aB, aC, aS, aI, aL, aF, aD}, new Object[] {aB, aC, aS, aI, aL, aF, aD});

        // First level class mismatch: char[] vs short[]
        checkNE(new Object[] {aC}, new Object[] {aS});

        // Verify MemorySegment
        MemorySegment mC = MemorySegment.ofArray(aC);
        MemorySegment mS = MemorySegment.ofArray(aS);
        Verify.checkEQ(new Object[] {mC}, new Object[] {mC});
        Verify.checkEQ(new Object[] {mS}, new Object[] {mS});

        // Second level type mismatch: backing type short[] vs char[]
        checkNE(new Object[] {mC}, new Object[] {mS});

        // Second level type mismatch: backing type int[] vs char[]
        MemorySegment mI = MemorySegment.ofArray(aI);
        checkNE(new Object[] {mI}, new Object[] {mC});

        // Verify boxed primitives:
        Byte bb1 = 42;
        Byte bb2 = 42;
        Byte bb3 = 11;

        Verify.checkEQ(new Object[] {(byte)42}, new Object[] {(byte)42});
        Verify.checkEQ(new Object[] {(byte)42}, new Object[] {bb1});
        Verify.checkEQ(new Object[] {bb1},      new Object[] {bb2});

        // Second level value mismatch: 42 vs 11
        checkNE(new Object[] {bb1},      new Object[] {bb3});

        Verify.checkEQ((byte)42,   (byte)42);
        Verify.checkEQ((short)42,  (short)42);
        Verify.checkEQ((char)42,   (char)42);
        Verify.checkEQ((int)42,    (int)42);
        Verify.checkEQ((long)42,   (long)42);
        Verify.checkEQ((float)42,  (float)42);
        Verify.checkEQ((double)42, (double)42);

        // Boxed type mismatch: float vs int
        checkNE((int)42, (float)42);

        // Boxed value mismatch.
        for (int i = 0; i < 10; i++) {
            byte v1 = (byte)RANDOM.nextInt();
            byte v2 = (byte)(v1 ^ (1 << RANDOM.nextInt(8)));
            checkNE(v1, v2);
        }
        for (int i = 0; i < 10; i++) {
            char v1 = (char)RANDOM.nextInt();
            char v2 = (char)(v1 ^ (1 << RANDOM.nextInt(16)));
            checkNE(v1, v2);
        }
        for (int i = 0; i < 10; i++) {
            char v1 = (char)RANDOM.nextInt();
            char v2 = (char)(v1 ^ (1 << RANDOM.nextInt(16)));
            checkNE(v1, v2);
        }
        for (int i = 0; i < 10; i++) {
            int v1 = (int)RANDOM.nextInt();
            int v2 = (int)(v1 ^ (1 << RANDOM.nextInt(32)));
            checkNE(v1, v2);
            checkNE(Float.intBitsToFloat(v1), Float.intBitsToFloat(v2));
        }
        for (int i = 0; i < 10; i++) {
            long v1 = (long)RANDOM.nextLong();
            long v2 = (long)(v1 ^ (1L << RANDOM.nextInt(64)));
            checkNE(v1, v2);
            checkNE(Double.longBitsToDouble(v1), Double.longBitsToDouble(v2));
        }
    }

    public static void checkNE(Object a, Object b) {
         try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}
    }
}
