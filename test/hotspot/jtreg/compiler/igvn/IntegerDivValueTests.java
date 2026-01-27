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
package compiler.igvn;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8364766
 * @summary Test value method of DivINode and DivLNode
 * @key randomness
 * @library /test/lib /
 * @run driver compiler.igvn.IntegerDivValueTests
 */
public class IntegerDivValueTests {
    private static final RestrictableGenerator<Integer> INTS = Generators.G.ints();
    private static final RestrictableGenerator<Long> LONGS = Generators.G.longs();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @ForceInline
    private int getIntConstant(int value) {
        // Simply return the given value to avoid javac already optimizing the operation away
        return value;
    }

    private static final int INT_CONST_1 = INTS.next();
    private static final int INT_CONST_2 = INTS.next();

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public int testIntConstantFolding() {
        // All constants available during parsing
        return INT_CONST_1 / INT_CONST_2;
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public int testIntConstantFoldingSpecialCase() {
        // All constants available during parsing
        return getIntConstant(Integer.MIN_VALUE) / getIntConstant(-1);
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public int testIntRange(int in) {
        int a = (in & 7) + 16;
        return a / 12; // [16, 23] / 12 is constant 1
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public boolean testIntRange2(int in) {
        int a = (in & 7) + 16;
        return a / 4 > 3; // [16, 23] / 4 => [4, 5]
    }

    @Test
    @IR(counts = {IRNode.DIV_I, "1"})
    public boolean testIntRange3(int in, int in2) {
        int a = (in & 31) + 16;
        int b = (in2 & 3) + 5;
        return a / b > 4; // [16, 47] / [5, 8] => [2, 9]
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public boolean testIntRange4(int in, int in2) {
        int a = (in & 15); // [0, 15]
        int b = (in2 & 3) + 1; // [1, 4]
        return a / b >= 0;
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public boolean testIntRange5(int in, int in2) {
        int a = (in & 15) + 5; // [5, 20]
        int b = (in2 & 3) + 1; // [1, 4]
        return a / b > 0;
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public boolean testIntRange6(int in, int in2) {
        int a = (in & 15) + 5; // [5, 20]
        int b = (in2 & 7) - 1; // [-1, 5]
        if (b == 0) return false;
        return a / b < -20;
    }

    @Test
    @IR(counts = {IRNode.DIV_I, "1"})
    public boolean testIntRange7(int in, int in2) {
        int a = (in & 15) + 5; // [5, 20]
        int b = (in2 & 7) - 1; // [-1, 5]
        if (b == 0) return false;
        return a / b > 0;
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public int testIntRange8(int in, int in2) {
        int a = (in & 31) + 128; // [128, 159]
        int b = (in2 & 15) + 100; // [100, 115]
        return a / b; // [1, 1] -> can be constant
    }

    private static final int INT_LIMIT_1 = INTS.next();
    private static final int INT_LIMIT_2 = INTS.next();
    private static final int INT_LIMIT_3 = INTS.next();
    private static final int INT_LIMIT_4 = INTS.next();
    private static final int INT_LIMIT_5 = INTS.next();
    private static final int INT_LIMIT_6 = INTS.next();
    private static final int INT_LIMIT_7 = INTS.next();
    private static final int INT_LIMIT_8 = INTS.next();
    private static final int INT_RANGE_LIMIT_X_LO;
    private static final int INT_RANGE_LIMIT_X_HI;
    private static final int INT_RANGE_LIMIT_Y_LO;
    private static final int INT_RANGE_LIMIT_Y_HI;

    static {
        int limit1 = INTS.next();
        int limit2 = INTS.next();
        if (limit2 > limit1) {
            INT_RANGE_LIMIT_X_LO = limit1;
            INT_RANGE_LIMIT_X_HI = limit2;
        } else {
            INT_RANGE_LIMIT_X_LO = limit2;
            INT_RANGE_LIMIT_X_HI = limit1;
        }

        int limit3 = INTS.next();
        int limit4 = INTS.next();
        if (limit4 > limit3) {
            INT_RANGE_LIMIT_Y_LO = limit3;
            INT_RANGE_LIMIT_Y_HI = limit4;
        } else {
            INT_RANGE_LIMIT_Y_LO = limit4;
            INT_RANGE_LIMIT_Y_HI = limit3;
        }
    }

    @ForceInline
    private int clampInt(int val, int lo, int hi) {
        return Math.min(hi, Math.max(val, lo));
    }

    @ForceInline
    private int calculateIntSum(int z) {
        int sum = 0;
        if (z < INT_LIMIT_1) sum += 1;
        if (z < INT_LIMIT_2) sum += 2;
        if (z < INT_LIMIT_3) sum += 4;
        if (z < INT_LIMIT_4) sum += 8;
        if (z > INT_LIMIT_5) sum += 16;
        if (z > INT_LIMIT_6) sum += 32;
        if (z > INT_LIMIT_7) sum += 64;
        if (z > INT_LIMIT_8) sum += 128;

        return sum;
    }

    @Test
    public int testIntRandomLimits(int x, int y) {
        x = clampInt(x, INT_RANGE_LIMIT_X_LO, INT_RANGE_LIMIT_X_HI);
        y = clampInt(y, INT_RANGE_LIMIT_Y_LO, INT_RANGE_LIMIT_Y_HI);
        int z = x / y;

        return calculateIntSum(z);
    }

    @DontCompile
    public int testIntRandomLimitsInterpreted(int x, int y) {
        x = clampInt(x, INT_RANGE_LIMIT_X_LO, INT_RANGE_LIMIT_X_HI);
        y = clampInt(y, INT_RANGE_LIMIT_Y_LO, INT_RANGE_LIMIT_Y_HI);
        int z = x / y;

        return calculateIntSum(z);
    }

    @Run(test = {"testIntConstantFolding", "testIntConstantFoldingSpecialCase"})
    public void checkIntConstants(RunInfo info) {
        if (INT_CONST_2 == 0) {
            Asserts.assertThrows(ArithmeticException.class, () -> testIntConstantFolding());
        } else {
            Asserts.assertEquals(INT_CONST_1 / INT_CONST_2, testIntConstantFolding());
        }
        Asserts.assertEquals(Integer.MIN_VALUE, testIntConstantFoldingSpecialCase());
    }

    @Run(test = {"testIntRange", "testIntRange2", "testIntRange3", "testIntRange4", "testIntRange5", "testIntRange6", "testIntRange7", "testIntRange8", "testIntRandomLimits"})
    public void checkIntRanges(RunInfo info) {
        for (int j = 0; j < 20; j++) {
            int i1 = INTS.next();
            int i2 = INTS.next();
            checkInt(i1, i2);
        }
    }

    @DontCompile
    public void checkInt(int in, int in2) {
        int a;
        int b;
        a = (in & 7) + 16;
        Asserts.assertEquals(a / 12, testIntRange(in));

        a = (in & 7) + 16;
        Asserts.assertEquals(a / 4 > 3, testIntRange2(in));

        a = (in & 31) + 16;
        b = (in2 & 3) + 5;
        Asserts.assertEquals(a / b > 4, testIntRange3(in, in2));

        a = (in & 15);
        b = (in2 & 3) + 1;
        Asserts.assertEquals(a / b >= 0, testIntRange4(in, in2));

        a = (in & 15) + 5;
        b = (in2 & 3) + 1;
        Asserts.assertEquals(a / b > 0, testIntRange5(in, in2));

        a = (in & 15) + 5;
        b = (in2 & 7) - 1;
        Asserts.assertEquals(b == 0 ? false : a / b < -20, testIntRange6(in, in2));

        a = (in & 15) + 5;
        b = (in2 & 7) - 1;
        Asserts.assertEquals(b == 0 ? false : a / b > 0, testIntRange7(in, in2));

        a = (in & 31) + 128;
        b = (in2 & 15) + 100;
        Asserts.assertEquals(a / b, testIntRange8(in, in2));

        int res;
        try {
            res = testIntRandomLimitsInterpreted(a, b);
        } catch (ArithmeticException _) {
            try {
                testIntRandomLimits(a, b);
                Asserts.fail("Expected ArithmeticException");
                return; // unreachable
            } catch (ArithmeticException _) {
                return; // test succeeded, no result to assert
            }
        }
        Asserts.assertEQ(res, testIntRandomLimits(a, b));
    }

    // Long variants

    @ForceInline
    private long getLongConstant(long value) {
        // Simply return the given value to avoid javac already optimizing the operation away
        return value;
    }

    private static final long LONG_CONST_1 = LONGS.next();
    private static final long LONG_CONST_2 = LONGS.next();

    @Test
    //@IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    // This results in a series of nodes due to DivLNode::Ideal and in particular transform_long_divide, which operates on non-constant divisors.
    // transform_long_divide splits up the division into multiple other nodes, such as MulHiLNode, which does not have a good Value() implemantion.
    // When JDK-8366815 is fixed, these rules should be reenabled
    // Alternatively, a better MulHiLNode::Value() implemantion should also lead to constant folding
    public long testLongConstantFolding() {
        // All constants available during parsing
        return LONG_CONST_1 / LONG_CONST_2;
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public long testLongConstantFoldingSpecialCase() {
        // All constants available during parsing
        return getLongConstant(Long.MIN_VALUE) / getLongConstant(-1L);
    }

    @Test
    //@IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    // This results in a series of nodes due to DivLNode::Ideal and in particular transform_long_divide, which operates on non-constant divisors.
    // transform_long_divide splits up the division into multiple other nodes, such as MulHiLNode, which does not have a good Value() implemantion.
    // When JDK-8366815 is fixed, these rules should be reenabled
    // Alternatively, a better MulHiLNode::Value() implemantion should also lead to constant folding
    @IR(counts = {IRNode.RSHIFT_L, "> 0", IRNode.ADD_L, "> 0", IRNode.AND_L, "> 0"}, failOn = {IRNode.DIV})
    public long testLongRange(long in) {
        long a = (in & 7L) + 16L;
        return a / 12L; // [16, 23] / 12 is constant 1
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public boolean testLongRange2(long in) {
        long a = (in & 7L) + 16L;
        return a / 4L > 3L; // [16, 23] / 4 => [4, 5]
    }

    @Test
    @IR(counts = {IRNode.DIV_L, "1"})
    public boolean testLongRange3(long in, long in2) {
        long a = (in & 31L) + 16L;
        long b = (in2 & 3L) + 5L;
        return a / b > 4L; // [16, 47] / [5, 8] => [2, 9]
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})

    public boolean testLongRange4(long in, long in2) {
        long a = (in & 15L); // [0, 15]
        long b = (in2 & 3L) + 1L; // [1, 4]
        return a / b >= 0L;
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public boolean testLongRange5(long in, long in2) {
        long a = (in & 15L) + 5L; // [5, 20]
        long b = (in2 & 3L) + 1L; // [1, 4]
        return a / b > 0L;
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public boolean testLongRange6(long in, long in2) {
        long a = (in & 15L) + 5L; // [5, 20]
        long b = (in2 & 7L) - 1L; // [-1, 5]
        if (b == 0L) return false;
        return a / b < -20L;
    }

    @Test
    @IR(counts = {IRNode.DIV_L, "1"})
    public boolean testLongRange7(long in, long in2) {
        long a = (in & 15L) + 5L; // [5, 20]
        long b = (in2 & 7L) - 1L; // [-1, 5]
        if (b == 0L) return false;
        return a / b > 0L;
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.URSHIFT, IRNode.RSHIFT, IRNode.MUL, IRNode.ADD, IRNode.SUB, IRNode.AND})
    public long testLongRange8(long in, long in2) {
        long a = (in & 31L) + 128L; // [128, 159]
        long b = (in2 & 15L) + 100L; // [100, 115]
        return a / b; // [1, 1] -> can be constant
    }


    private static final long LONG_LIMIT_1 = LONGS.next();
    private static final long LONG_LIMIT_2 = LONGS.next();
    private static final long LONG_LIMIT_3 = LONGS.next();
    private static final long LONG_LIMIT_4 = LONGS.next();
    private static final long LONG_LIMIT_5 = LONGS.next();
    private static final long LONG_LIMIT_6 = LONGS.next();
    private static final long LONG_LIMIT_7 = LONGS.next();
    private static final long LONG_LIMIT_8 = LONGS.next();
    private static final long LONG_RANGE_LIMIT_X_LO;
    private static final long LONG_RANGE_LIMIT_X_HI;
    private static final long LONG_RANGE_LIMIT_Y_LO;
    private static final long LONG_RANGE_LIMIT_Y_HI;

    static {
        long limit1 = LONGS.next();
        long limit2 = LONGS.next();
        if (limit2 > limit1) {
            LONG_RANGE_LIMIT_X_LO = limit1;
            LONG_RANGE_LIMIT_X_HI = limit2;
        } else {
            LONG_RANGE_LIMIT_X_LO = limit2;
            LONG_RANGE_LIMIT_X_HI = limit1;
        }

        long limit3 = LONGS.next();
        long limit4 = LONGS.next();
        if (limit4 > limit3) {
            LONG_RANGE_LIMIT_Y_LO = limit3;
            LONG_RANGE_LIMIT_Y_HI = limit4;
        } else {
            LONG_RANGE_LIMIT_Y_LO = limit4;
            LONG_RANGE_LIMIT_Y_HI = limit3;
        }
    }

    @ForceInline
    private long clampLong(long val, long lo, long hi) {
        return Math.min(hi, Math.max(val, lo));
    }

    @ForceInline
    private int calculateLongSum(long z) {
        int sum = 0;
        if (z < LONG_LIMIT_1) sum += 1;
        if (z < LONG_LIMIT_2) sum += 2;
        if (z < LONG_LIMIT_3) sum += 4;
        if (z < LONG_LIMIT_4) sum += 8;
        if (z > LONG_LIMIT_5) sum += 16;
        if (z > LONG_LIMIT_6) sum += 32;
        if (z > LONG_LIMIT_7) sum += 64;
        if (z > LONG_LIMIT_8) sum += 128;

        return sum;
    }

    @Test
    public int testLongRandomLimits(long x, long y) {
        x = clampLong(x, LONG_RANGE_LIMIT_X_LO, LONG_RANGE_LIMIT_X_HI);
        y = clampLong(y, LONG_RANGE_LIMIT_Y_LO, LONG_RANGE_LIMIT_Y_HI);
        long z = x / y;

        return calculateLongSum(z);
    }

    @DontCompile
    public int testLongRandomLimitsInterpreted(long x, long y) {
        x = clampLong(x, LONG_RANGE_LIMIT_X_LO, LONG_RANGE_LIMIT_X_HI);
        y = clampLong(y, LONG_RANGE_LIMIT_Y_LO, LONG_RANGE_LIMIT_Y_HI);
        long z = x / y;

        return calculateLongSum(z);
    }

    @Run(test = {"testLongConstantFolding", "testLongConstantFoldingSpecialCase"})
    public void checkLongConstants(RunInfo infoLong) {
        if (LONG_CONST_2 == 0L) {
            Asserts.assertThrows(ArithmeticException.class, () -> testLongConstantFolding());
        } else {
            Asserts.assertEquals(LONG_CONST_1 / LONG_CONST_2, testLongConstantFolding());
        }
        Asserts.assertEquals(Long.MIN_VALUE, testLongConstantFoldingSpecialCase());
    }

    @Run(test = {"testLongRange", "testLongRange2", "testLongRange3", "testLongRange4", "testLongRange5", "testLongRange6", "testLongRange7", "testLongRange8", "testLongRandomLimits"})
    public void checkLongRanges(RunInfo info) {
        for (int j = 0; j < 20; j++) {
            long l1 = LONGS.next();
            long l2 = LONGS.next();
            checkLong(l1, l2);
        }
    }

    @DontCompile
    public void checkLong(long in, long in2) {
        long a;
        long b;
        a = (in & 7L) + 16L;
        Asserts.assertEquals(a / 12L, testLongRange(in));

        a = (in & 7L) + 16L;
        Asserts.assertEquals(a / 4L > 3L, testLongRange2(in));

        a = (in & 31L) + 16L;
        b = (in2 & 3L) + 5L;
        Asserts.assertEquals(a / b > 4L, testLongRange3(in, in2));

        a = (in & 15L);
        b = (in2 & 3L) + 1L;
        Asserts.assertEquals(a / b >= 0L, testLongRange4(in, in2));

        a = (in & 15L) + 5L;
        b = (in2 & 3L) + 1L;
        Asserts.assertEquals(a / b > 0L, testLongRange5(in, in2));

        a = (in & 15L) + 5L;
        b = (in2 & 7L) - 1L;
        Asserts.assertEquals(b == 0 ? false : a / b < -20L, testLongRange6(in, in2));

        a = (in & 15L) + 5L;
        b = (in2 & 7L) - 1L;
        Asserts.assertEquals(b == 0 ? false : a / b > 0L, testLongRange7(in, in2));

        a = (in & 31L) + 128L;
        b = (in2 & 15L) + 100L;
        Asserts.assertEquals(a / b, testLongRange8(in, in2));

        int res;
        try {
            res = testLongRandomLimitsInterpreted(a, b);
        } catch (ArithmeticException _) {
            try {
                testLongRandomLimits(a, b);
                Asserts.fail("Expected ArithmeticException");
                return; // unreachable
            } catch (ArithmeticException _) {
                return; // test succeeded, no result to assert
            }
        }
        Asserts.assertEQ(res, testLongRandomLimits(a, b));
    }
}
