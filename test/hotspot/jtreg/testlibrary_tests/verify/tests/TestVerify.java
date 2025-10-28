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
        testException();

        testRawFloat();

        // Test recursive data: Object array of values, etc.
        testRecursive();

        testArbitraryClasses();
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

    public static void testException() {
        Exception e1 = new ArithmeticException("abc");
        Exception e2 = new ArithmeticException("abc");
        Exception e3 = new ArithmeticException();
        Exception e4 = new ArithmeticException("xyz");
        Exception e5 = new RuntimeException("abc");

        Verify.checkEQ(e1, e1);
        Verify.checkEQ(e1, e2);
        Verify.checkEQ(e3, e3);
        Verify.checkEQ(e1, e3); // one has no message

        checkNE(e1, e4);
        checkNE(e2, e4);
        Verify.checkEQ(e3, e4);

        Verify.checkEQ(e5, e5);
        checkNE(e1, e5);
        checkNE(e2, e5);
        checkNE(e3, e5);
        checkNE(e4, e5);
    }

    public static void testRawFloat() {
        float nanF1 = Float.intBitsToFloat(0x7f800001);
        float nanF2 = Float.intBitsToFloat(0x7fffffff);
        double nanD1 = Double.longBitsToDouble(0x7ff0000000000001L);
        double nanD2 = Double.longBitsToDouble(0x7fffffffffffffffL);

        float[] arrF1 = new float[]{nanF1};
        float[] arrF2 = new float[]{nanF2};
        double[] arrD1 = new double[]{nanD1};
        double[] arrD2 = new double[]{nanD2};

        Verify.checkEQ(nanF1, Float.NaN);
        Verify.checkEQ(nanF1, nanF1);
        Verify.checkEQWithRawBits(nanF1, nanF1);
        Verify.checkEQ(nanF1, nanF2);
        Verify.checkEQ(nanD1, Double.NaN);
        Verify.checkEQ(nanD1, nanD1);
        Verify.checkEQWithRawBits(nanD1, nanD1);
        Verify.checkEQ(nanD1, nanD2);

        Verify.checkEQ(arrF1, arrF1);
        Verify.checkEQWithRawBits(arrF1, arrF1);
        Verify.checkEQ(arrF1, arrF2);
        Verify.checkEQ(arrD1, arrD1);
        Verify.checkEQWithRawBits(arrD1, arrD1);
        Verify.checkEQ(arrD1, arrD2);

        checkNEWithRawBits(nanF1, nanF2);
        checkNEWithRawBits(nanD1, nanD2);

        checkNEWithRawBits(arrF1, arrF2);
        checkNEWithRawBits(arrD1, arrD2);
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
            checkNEWithRawBits(Float.intBitsToFloat(v1), Float.intBitsToFloat(v2));
        }
        for (int i = 0; i < 10; i++) {
            long v1 = (long)RANDOM.nextLong();
            long v2 = (long)(v1 ^ (1L << RANDOM.nextInt(64)));
            checkNE(v1, v2);
            checkNEWithRawBits(Double.longBitsToDouble(v1), Double.longBitsToDouble(v2));
        }
    }

    static class A {}

    static class B {}

    static class C extends B {}

    static class D {
        D(int x) {
            this.x = x;
        }

        private int x;
    }

    static class E {
        E(D d, E e1, E e2) {
            this.d = d;
            this.e1 = e1;
            this.e2 = e2;
        }

        private D d;
        public E e1;
        public E e2;
    }

    static class F {
        private int x;

        public F(int x) {
            this.x = x;
        }
    }

    static class F2 extends F {
        private int y;

        F2(int x, int y) {
            super(x);
            this.y = y;
        }
    }

    static class G {
        private float x;
        private float y;

        public G(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class H1 {
        public boolean bool = true;
        public byte b = (byte)242;
        public short s = (short)24242;
        public char c = (char)24242;
        public int i = 1335836768;
        public long l = 4242424242L;
        public float f = 42.0f;
        public double d = 42.0;
        public H1() {}
    }

    public static class H2 extends H1 {
        public H1 h1 = new H1();
        public H2() {}
    }

    static record R1() {}
    static record R2() {}
    static record R3(int x, int y) {}
    static record R4(R4 x, R4 y) {}

    public static void testArbitraryClasses() {
        A a1 = new A();
        A a2 = new A();
        B b1 = new B();
        B b2 = new B();
        C c1 = new C();
        C c2 = new C();

        // Structurally equivalent.
        Verify.checkEQ(a1, a1);
        Verify.checkEQ(a1, a2);
        Verify.checkEQ(b1, b1);
        Verify.checkEQ(b1, b2);
        Verify.checkEQ(c1, c1);
        Verify.checkEQ(c1, c2);

        // Must fail because of different classes.
        checkNE(a1, b1);
        checkNE(b1, a1);
        checkNE(a1, c1);
        checkNE(c1, a1);
        checkNE(b1, c1);
        checkNE(c1, b1);

        // Objects with primitive values.
        D d1 = new D(1);
        D d2 = new D(1);
        D d3 = new D(2);
        Verify.checkEQ(d1, d1);
        Verify.checkEQ(d1, d2);
        Verify.checkEQ(d2, d1);
        checkNE(d1, d3);
        checkNE(d3, d1);

        // Object fields, including cycles.
        E e1 = new E(d1, null, null);
        E e2 = new E(d1, null, null);
        E e3 = new E(d3, null, null);
        E e4 = new E(d1, e1, null);
        E e5 = new E(d1, e2, null);
        E e6 = new E(d1, null, null);
        e6.e1 = e6;
        E e7 = new E(d1, null, null);
        e7.e1 = e7;
        E e8 = new E(d1, e1, e1);
        E e9 = new E(d1, e1, e2);

        Verify.checkEQ(e1, e1);
        Verify.checkEQ(e1, e2);
        Verify.checkEQ(e2, e1);
        checkNE(e1, e3);
        checkNE(e3, e1);
        Verify.checkEQ(e6, e6);
        Verify.checkEQ(e6, e7);
        Verify.checkEQ(e7, e6);
        Verify.checkEQ(e8, e8);
        checkNE(e8, e9);
        checkNE(e9, e8);

        // Fields from superclass.
        F2 f1 = new F2(1, 1);
        F2 f2 = new F2(1, 1);
        F2 f3 = new F2(2, 1);
        F2 f4 = new F2(1, 2);

        Verify.checkEQ(f1, f1);
        Verify.checkEQ(f1, f2);
        Verify.checkEQ(f2, f1);
        checkNE(f1, f3);
        checkNE(f1, f4);
        checkNE(f3, f1);
        checkNE(f4, f1);
        checkNE(f3, f4);
        checkNE(f4, f3);

        G g1 = new G(1.0f, 1.0f);
        G g2 = new G(1.0f, 1.0f);
        G g3 = new G(Float.NaN, Float.NaN);
        G g4 = new G(Float.NaN, Float.NaN);

        Verify.checkEQ(g1, g1);
        Verify.checkEQ(g2, g1);
        Verify.checkEQ(g1, g2);
        Verify.checkEQ(g3, g3);
        Verify.checkEQ(g3, g4);
        Verify.checkEQ(g4, g3);
        checkNE(g1, g3);
        checkNE(g3, g1);

        // Nested class with primitive types, where the boxed types may not be cached,
        // and so they would create different boxed objects.
        Verify.checkEQ(new H2(), new H2());

        // Records.
        R1 r11 = new R1();
        R1 r12 = new R1();
        R2 r21 = new R2();
        R3 r31 = new R3(1, 1);
        R3 r32 = new R3(1, 1);
        R3 r33 = new R3(1, 2);
        R3 r34 = new R3(2, 1);

        Verify.checkEQ(r11, r11);
        Verify.checkEQ(r11, r12);
        Verify.checkEQ(r12, r11);
        checkNE(r11, r21);
        Verify.checkEQ(r31, r31);
        Verify.checkEQ(r31, r32);
        Verify.checkEQ(r32, r31);
        checkNE(r31, r33);
        checkNE(r33, r31);
        checkNE(r31, r34);
        checkNE(r34, r31);
        checkNE(r33, r34);

        R4 r41 = new R4(null, null);
        R4 r42 = new R4(null, null);
        R4 r43 = new R4(r41, null);
        R4 r44 = new R4(r42, null);
        R4 r45 = new R4(r43, r41);
        R4 r46 = new R4(r44, r42);
        R4 r47 = new R4(r44, r41);

        Verify.checkEQ(r45, r46);
        Verify.checkEQ(r46, r45);
        checkNE(r45, r47);
        checkNE(r47, r45);
        checkNE(r46, r47);
        checkNE(r47, r46);
    }

    public static void checkNE(Object a, Object b) {
         try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown: " + a + " vs " + b);
        } catch (VerifyException e) {}
    }

    public static void checkNEWithRawBits(Object a, Object b) {
         try {
            Verify.checkEQWithRawBits(a, b);
            throw new RuntimeException("Should have thrown: " + a + " vs " + b);
        } catch (VerifyException e) {}
    }
}
