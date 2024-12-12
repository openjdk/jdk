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
import java.nio.ByteBuffer;

import compiler.lib.verify.*;

public class TestVerify {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        testArrayByte();
        testArrayChar();
        testArrayShort();
        testArrayInt();
        testArrayLong();
        testArrayFloat();
        testArrayDouble();
        testNativeMemorySegment();
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
        try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Size mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        try {
            Verify.checkEQ(a, c);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Value mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}
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
        try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Size mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        try {
            Verify.checkEQ(a, c);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Value mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}
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
        try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Size mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        try {
            Verify.checkEQ(a, c);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Value mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}
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
        try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Size mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        try {
            Verify.checkEQ(a, c);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Value mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}
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
        try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Size mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        try {
            Verify.checkEQ(a, c);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Value mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}
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
        try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Size mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        try {
            Verify.checkEQ(a, c);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Value mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}
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
        try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Size mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        try {
            Verify.checkEQ(a, c);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Value mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}
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
        try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        c.set(ValueLayout.JAVA_BYTE, RANDOM.nextLong(c.byteSize()), (byte)1);

        // Value mismatch
        try {
            Verify.checkEQ(a, c);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}
    }
}
