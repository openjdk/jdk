/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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

package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8276673 8280089 8349563
 * @summary Test abs nodes optimization in C2.
 * @key randomness
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestIRAbs
 */

public class TestIRAbs {
    private static final RestrictableGenerator<Integer> INTS = Generators.G.ints();
    private static final RestrictableGenerator<Long> LONGS = Generators.G.longs();

    private static final IntRange INT_RANGE = IntRange.generate(INTS);
    private static final LongRange LONG_RANGE = LongRange.generate(LONGS);

    private static final int INT_1 = INTS.next();
    private static final int INT_2 = INTS.next();
    private static final int INT_3 = INTS.next();
    private static final int INT_4 = INTS.next();
    private static final int INT_5 = INTS.next();
    private static final int INT_6 = INTS.next();
    private static final int INT_7 = INTS.next();
    private static final int INT_8 = INTS.next();

    private static final long LONG_1 = LONGS.next();
    private static final long LONG_2 = LONGS.next();
    private static final long LONG_3 = LONGS.next();
    private static final long LONG_4 = LONGS.next();
    private static final long LONG_5 = LONGS.next();
    private static final long LONG_6 = LONGS.next();
    private static final long LONG_7 = LONGS.next();
    private static final long LONG_8 = LONGS.next();

    public static char [] cspecial = {
        0, 42, 128, 256, 1024, 4096, 65535
    };

    public static int [] ispecial = {
        0, Integer.MAX_VALUE, Integer.MIN_VALUE, -42, 42, -1, 1
    };

    public static long [] lspecial = {
        0, Long.MAX_VALUE, Long.MIN_VALUE, -42, 42, -1, 1
    };

    public static float [] fspecial = {
        0.0f,
        -0.0f,
        Float.MAX_VALUE,
        Float.MIN_VALUE,
        -Float.MAX_VALUE,
        -Float.MIN_VALUE,
        Float.NaN,
        Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
        Integer.MAX_VALUE,
        Integer.MIN_VALUE,
        Long.MAX_VALUE,
        Long.MIN_VALUE,
        -1.0f,
        1.0f,
        -42.0f,
        42.0f
    };

    public static double [] dspecial = {
        0.0,
        -0.0,
        Double.MAX_VALUE,
        Double.MIN_VALUE,
        -Double.MAX_VALUE,
        -Double.MIN_VALUE,
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Integer.MAX_VALUE,
        Integer.MIN_VALUE,
        Long.MIN_VALUE,
        Long.MAX_VALUE,
        -1,
        1,
        42,
        -42,
        Math.PI,
        Math.E,
        Float.MAX_VALUE,
        Float.MIN_VALUE,
        -Float.MAX_VALUE,
        -Float.MIN_VALUE,
        Float.NaN,
        Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY
    };

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.ABS_I, IRNode.ABS_L, IRNode.ABS_F, IRNode.ABS_D}, applyIfPlatform = { "64-bit", "true" })
    public void testAbsConstant() {
        // Test abs(constant) optimization for int
        Asserts.assertEquals(Integer.MAX_VALUE, Math.abs(Integer.MAX_VALUE));
        Asserts.assertEquals(Integer.MIN_VALUE, Math.abs(Integer.MIN_VALUE));
        Asserts.assertEquals(Integer.MAX_VALUE, Math.abs(-Integer.MAX_VALUE));

        // Test abs(constant) optimization for long
        Asserts.assertEquals(Long.MAX_VALUE, Math.abs(Long.MAX_VALUE));
        Asserts.assertEquals(Long.MIN_VALUE, Math.abs(Long.MIN_VALUE));
        Asserts.assertEquals(Long.MAX_VALUE, Math.abs(-Long.MAX_VALUE));

        // Test abs(constant) optimization for float
        Asserts.assertTrue(Float.isNaN(Math.abs(Float.NaN)));
        Asserts.assertEquals(Float.POSITIVE_INFINITY, Math.abs(Float.NEGATIVE_INFINITY));
        Asserts.assertEquals(Float.POSITIVE_INFINITY, Math.abs(Float.POSITIVE_INFINITY));
        Asserts.assertEquals(0.0f, Math.abs(0.0f));
        Asserts.assertEquals(0.0f, Math.abs(-0.0f));
        Asserts.assertEquals(Float.MAX_VALUE, Math.abs(Float.MAX_VALUE));
        Asserts.assertEquals(Float.MIN_VALUE, Math.abs(Float.MIN_VALUE));
        Asserts.assertEquals(Float.MAX_VALUE, Math.abs(-Float.MAX_VALUE));
        Asserts.assertEquals(Float.MIN_VALUE, Math.abs(-Float.MIN_VALUE));

        // Test abs(constant) optimization for double
        Asserts.assertTrue(Double.isNaN(Math.abs(Double.NaN)));
        Asserts.assertEquals(Double.POSITIVE_INFINITY, Math.abs(Double.NEGATIVE_INFINITY));
        Asserts.assertEquals(Double.POSITIVE_INFINITY, Math.abs(Double.POSITIVE_INFINITY));
        Asserts.assertEquals(0.0, Math.abs(0.0));
        Asserts.assertEquals(0.0, Math.abs(-0.0));
        Asserts.assertEquals(Double.MAX_VALUE, Math.abs(Double.MAX_VALUE));
        Asserts.assertEquals(Double.MIN_VALUE, Math.abs(Double.MIN_VALUE));
        Asserts.assertEquals(Double.MAX_VALUE, Math.abs(-Double.MAX_VALUE));
        Asserts.assertEquals(Double.MIN_VALUE, Math.abs(-Double.MIN_VALUE));
    }

    @Test
    @IR(counts = {IRNode.ABS_I, "1"})
    public int testInt0(int x) {
        return Math.abs(Math.abs(x)); // transformed to Math.abs(x)
    }

    @Test
    @IR(failOn = {IRNode.SUB_I})
    @IR(counts = {IRNode.ABS_I, "1"})
    public int testInt1(int x) {
        return Math.abs(0 - x); // transformed to Math.abs(x)
    }

    @Run(test = {"testInt0", "testInt1"})
    public void checkTestInt(RunInfo info) {
        for (int i = 0; i < ispecial.length; i++) {
            Asserts.assertEquals(Math.abs(ispecial[i]), testInt0(ispecial[i]));
            Asserts.assertEquals(Math.abs(ispecial[i]), testInt1(ispecial[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ABS_L, "1"}, applyIfPlatform = { "64-bit", "true" })
    public long testLong0(long x) {
        return Math.abs(Math.abs(x)); // transformed to Math.abs(x)
    }

    @Test
    @IR(failOn = {IRNode.SUB_L}, applyIfPlatform = { "64-bit", "true" })
    @IR(counts = {IRNode.ABS_L, "1"}, applyIfPlatform = { "64-bit", "true" })
    public long testLong1(long x) {
        return Math.abs(0 - x); // transformed to Math.abs(x)
    }

    @Run(test = {"testLong0", "testLong1"})
    public void checkTestLong(RunInfo info) {
        for (int i = 0; i < lspecial.length; i++) {
            Asserts.assertEquals(Math.abs(lspecial[i]), testLong0(lspecial[i]));
            Asserts.assertEquals(Math.abs(lspecial[i]), testLong1(lspecial[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ABS_F, "1"})
    public float testFloat0(float x) {
        return Math.abs(Math.abs(x)); // transformed to Math.abs(x)
    }

    @Test
    @IR(failOn = {IRNode.SUB_F})
    @IR(counts = {IRNode.ABS_F, "1"})
    public float testFloat1(float x) {
        return Math.abs(0 - x); // transformed to Math.abs(x)
    }

    @Run(test = {"testFloat0", "testFloat1"})
    public void checkTestFloat(RunInfo info) {
        for (int i = 0; i < fspecial.length; i++) {
            Asserts.assertEquals(Math.abs(fspecial[i]), testFloat0(fspecial[i]));
            Asserts.assertEquals(Math.abs(fspecial[i]), testFloat1(fspecial[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ABS_D, "1"})
    public double testDouble0(double x) {
        return Math.abs(Math.abs(x)); // transformed to Math.abs(x)
    }

    @Test
    @IR(failOn = {IRNode.SUB_D})
    @IR(counts = {IRNode.ABS_D, "1"})
    public double testDouble1(double x) {
        return Math.abs(0 - x); // transformed to Math.abs(x)
    }

    @Run(test = {"testDouble0", "testDouble1"})
    public void checkTestDouble(RunInfo info) {
        for (int i = 0; i < dspecial.length; i++) {
            Asserts.assertEquals(Math.abs(dspecial[i]), testDouble0(dspecial[i]));
            Asserts.assertEquals(Math.abs(dspecial[i]), testDouble1(dspecial[i]));
        }
    }

    @Test
    @IR(failOn = {IRNode.ABS_I})
    public void testChar() {
        for (int i = 0; i < cspecial.length; i++) {
            Asserts.assertEquals(cspecial[i], (char) Math.abs(cspecial[i]));
        }
    }

    @Run(test = {"testIntRange1", "testIntRange2", "testIntRange3", "testIntRange4", "testIntRangeFolding"})
    public void checkIntRanges(RunInfo info) {
        for (int i : ispecial) {
            checkIntRange(i);
        }

        for (int j = 0; j < 20; j++) {
            int i = INTS.next();
            checkIntRange(i);
        }
    }

    @DontCompile
    public void checkIntRange(int i) {
        Asserts.assertEquals(Math.abs((i & 7) - 4) > 4, testIntRange1(i));
        Asserts.assertEquals(Math.abs((i & 7) - 4) < 0, testIntRange2(i));
        Asserts.assertEquals(Math.abs(-((i & 7) + 2)) < 2, testIntRange3(i));
        Asserts.assertEquals(Math.abs(-((i & 7) + 2)) > 9, testIntRange4(i));
        Asserts.assertEquals(testIntRangeFoldingInterpreter(i), testIntRangeFolding(i));
    }

    @Run(test = {"testLongRange1", "testLongRange2", "testLongRange3", "testLongRange4", "testLongRangeFolding"})
    public void checkLongRanges(RunInfo info) {
        for (long l : lspecial) {
          checkLongRange(l);
        }

        for (int j = 0; j < 20; j++) {
            long l = LONGS.next();
            checkLongRange(l);
        }
    }

    @DontCompile
    public void checkLongRange(long l) {
        Asserts.assertEquals(Math.abs((l & 7) - 4) > 4, testLongRange1(l));
        Asserts.assertEquals(Math.abs((l & 7) - 4) < 0, testLongRange2(l));
        Asserts.assertEquals(Math.abs(-((l & 7) + 2)) < 2, testLongRange3(l));
        Asserts.assertEquals(Math.abs(-((l & 7) + 2)) > 9, testLongRange4(l));
        Asserts.assertEquals(testLongRangeFoldingInterpreter(l), testLongRangeFolding(l));
    }

    // Int ranges

    @Test
    @IR(failOn = { IRNode.ABS_I })
    public boolean testIntRange1(int in) {
        // [-4, 3] => [0, 4]
        return Math.abs((in & 7) - 4) > 4;
    }

    @Test
    @IR(failOn = { IRNode.ABS_I })
    public boolean testIntRange2(int in) {
        // [-4, 3] => [0, 4]
        return Math.abs((in & 7) - 4) < 0;
    }

    @Test
    @IR(failOn = { IRNode.ABS_I })
    public boolean testIntRange3(int in) {
        // [-9, -2] => [2, 9]
        return Math.abs(-((in & 7) + 2)) < 2;
    }

    @Test
    @IR(failOn = { IRNode.ABS_I })
    public boolean testIntRange4(int in) {
        // [-9, -2] => [2, 9]
        return Math.abs(-((in & 7) + 2)) > 9;
    }

    @Test
    public int testIntRangeFolding(int in) {
        int c = INT_RANGE.clamp(in);
        int v = Math.abs(c);

        int sum = 0;
        if (v > INT_1) { sum += 1; }
        if (v > INT_2) { sum += 2; }
        if (v > INT_3) { sum += 4; }
        if (v > INT_4) { sum += 8; }
        if (v > INT_5) { sum += 16; }
        if (v > INT_6) { sum += 32; }
        if (v > INT_7) { sum += 64; }
        if (v > INT_8) { sum += 128; }

        return sum;
    }

    @DontCompile
    public int testIntRangeFoldingInterpreter(int in) {
        int c = INT_RANGE.clamp(in);
        int v = Math.abs(c);

        int sum = 0;
        if (v > INT_1) { sum += 1; }
        if (v > INT_2) { sum += 2; }
        if (v > INT_3) { sum += 4; }
        if (v > INT_4) { sum += 8; }
        if (v > INT_5) { sum += 16; }
        if (v > INT_6) { sum += 32; }
        if (v > INT_7) { sum += 64; }
        if (v > INT_8) { sum += 128; }

        return sum;
    }

    // Long ranges

    @Test
    @IR(failOn = { IRNode.ABS_L }, applyIfPlatform = { "64-bit", "true" })
    public boolean testLongRange1(long in) {
        // [-4, 3] => [0, 4]
        return Math.abs((in & 7) - 4) > 4;
    }

    @Test
    @IR(failOn = { IRNode.ABS_L }, applyIfPlatform = { "64-bit", "true" })
    public boolean testLongRange2(long in) {
        // [-4, 3] => [0, 4]
        return Math.abs((in & 7) - 4) < 0;
    }

    @Test
    @IR(failOn = { IRNode.ABS_L }, applyIfPlatform = { "64-bit", "true" })
    public boolean testLongRange3(long in) {
        // [-9, -2] => [2, 9]
        return Math.abs(-((in & 7) + 2)) < 2;
    }

    @Test
    @IR(failOn = { IRNode.ABS_L }, applyIfPlatform = { "64-bit", "true" })
    public boolean testLongRange4(long in) {
        // [-9, -2] => [2, 9]
        return Math.abs(-((in & 7) + 2)) > 9;
    }

    @Test
    public int testLongRangeFolding(long in) {
        long c = LONG_RANGE.clamp(in);
        long v = Math.abs(c);

        int sum = 0;
        if (v > LONG_1) { sum += 1; }
        if (v > LONG_2) { sum += 2; }
        if (v > LONG_3) { sum += 4; }
        if (v > LONG_4) { sum += 8; }
        if (v > LONG_5) { sum += 16; }
        if (v > LONG_6) { sum += 32; }
        if (v > LONG_7) { sum += 64; }
        if (v > LONG_8) { sum += 128; }

        return sum;
    }

    @DontCompile
    public int testLongRangeFoldingInterpreter(long in) {
        long c = LONG_RANGE.clamp(in);
        long v = Math.abs(c);

        int sum = 0;
        if (v > LONG_1) { sum += 1; }
        if (v > LONG_2) { sum += 2; }
        if (v > LONG_3) { sum += 4; }
        if (v > LONG_4) { sum += 8; }
        if (v > LONG_5) { sum += 16; }
        if (v > LONG_6) { sum += 32; }
        if (v > LONG_7) { sum += 64; }
        if (v > LONG_8) { sum += 128; }

        return sum;
    }

    record IntRange(int lo, int hi) {
        IntRange {
            if (lo > hi) {
                throw new IllegalArgumentException("lo > hi");
            }
        }

        int clamp(int v) {
            return Math.min(hi, Math.max(v, lo));
        }

        static IntRange generate(Generator<Integer> g) {
            var a = g.next();
            var b = g.next();
            if (a > b) {
                var tmp = a;
                a = b;
                b = tmp;
            }
            return new IntRange(a, b);
        }
    }

    record LongRange(long lo, long hi) {
        LongRange {
            if (lo > hi) {
                throw new IllegalArgumentException("lo > hi");
            }
        }

        long clamp(long v) {
            return Math.min(hi, Math.max(v, lo));
        }

        static LongRange generate(Generator<Long> g) {
            var a = g.next();
            var b = g.next();
            if (a > b) {
                var tmp = a;
                a = b;
                b = tmp;
            }
            return new LongRange(a, b);
        }
    }
}
