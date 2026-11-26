/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.ir_framework.*;

import static compiler.lib.ir_framework.IRNode.*;

/*
 * @test
 * @bug 8276162 8397406
 * @summary Test that unsigned comparison transformation works as intended.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestUnsignedComparison
 */
public class TestUnsignedComparison {
    private static final int INT_MIN = Integer.MIN_VALUE;
    private static final int INT_MAX = Integer.MAX_VALUE;
    private static final long LONG_MIN = Long.MIN_VALUE;
    private static final long LONG_MAX = Long.MAX_VALUE;
    private static final int TRUE_RESULT = 1;
    private static final int FALSE_RESULT = -1;

    // Integers are sorted in unsignedly increasing order
    private static final int[] INT_DATA = {
        0,
        1,
        2,
        3,
        0x8000_0000,
        0x8000_0001,
        0x8000_0002,
        0x8000_0003,
        0xFFFF_FFFE,
        0xFFFF_FFFF,
    };

    // Longs are sorted in unsignedly increasing order
    private static final long[] LONG_DATA = {
        0L,
        1L,
        2L,
        3L,
        0x00000000_80000000L,
        0x00000000_FFFFFFFFL,
        0x00000001_00000000L,
        0x80000000_00000000L,
        0x80000000_00000001L,
        0x80000000_00000002L,
        0x80000000_00000003L,
        0x80000000_80000000L,
        0xFFFFFFFF_FFFFFFFEL,
        0xFFFFFFFF_FFFFFFFFL,
    };

    // Constants to compare against, add MIN_VALUE beforehand for convenience
    private static final int CONST_INDEX = 6;
    private static final int INT_CONST = INT_DATA[CONST_INDEX] + INT_MIN;
    private static final long LONG_CONST = LONG_DATA[CONST_INDEX] + LONG_MIN;

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.start();
    }

    @Test
    @IR(failOn = {CMP_I, ADD_I})
    @IR(counts = {CMP_U, "1"})
    public boolean testIntVarEQ(int x, int y) {
        return x + INT_MIN == y + INT_MIN;
    }

    @Test
    @IR(failOn = {CMP_I, ADD_I})
    @IR(counts = {CMP_U, "1"})
    public boolean testIntVarNE(int x, int y) {
        return x + INT_MIN != y + INT_MIN;
    }

    @Test
    @IR(failOn = {CMP_I, ADD_I})
    @IR(counts = {CMP_U, "1"})
    public boolean testIntVarLT(int x, int y) {
        return x + INT_MIN < y + INT_MIN;
    }

    @Test
    @IR(failOn = {CMP_I, ADD_I})
    @IR(counts = {CMP_U, "1"})
    public boolean testIntVarLE(int x, int y) {
        return x + INT_MIN <= y + INT_MIN;
    }

    @Test
    @IR(failOn = {CMP_I, ADD_I})
    @IR(counts = {CMP_U, "1"})
    public boolean testIntVarGT(int x, int y) {
        return x + INT_MIN > y + INT_MIN;
    }

    @Test
    @IR(failOn = {CMP_I, ADD_I})
    @IR(counts = {CMP_U, "1"})
    public boolean testIntVarGE(int x, int y) {
        return x + INT_MIN >= y + INT_MIN;
    }

    @Run(test = {"testIntVarEQ", "testIntVarNE",
                 "testIntVarLT", "testIntVarLE",
                 "testIntVarGT", "testIntVarGE"})
    public void checkTestIntVar() {
        // Verify the transformation "cmp (add X min_jint) (add Y min_jint)"
        // to "cmpu X Y"
        for (int i = 0; i < INT_DATA.length; i++) {
            for (int j = 0; j < INT_DATA.length; j++) {
                Asserts.assertEquals(testIntVarEQ(INT_DATA[i], INT_DATA[j]),
                                     i == j);
                Asserts.assertEquals(testIntVarNE(INT_DATA[i], INT_DATA[j]),
                                     i != j);
                Asserts.assertEquals(testIntVarLT(INT_DATA[i], INT_DATA[j]),
                                     i <  j);
                Asserts.assertEquals(testIntVarLE(INT_DATA[i], INT_DATA[j]),
                                     i <= j);
                Asserts.assertEquals(testIntVarGT(INT_DATA[i], INT_DATA[j]),
                                     i >  j);
                Asserts.assertEquals(testIntVarGE(INT_DATA[i], INT_DATA[j]),
                                     i >= j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_I, ADD_I})
    @IR(counts = {CMP_U, "1"})
    public boolean testIntConEQ(int x) {
        return x + INT_MIN == INT_CONST;
    }

    @Test
    @IR(failOn = {CMP_I, ADD_I})
    @IR(counts = {CMP_U, "1"})
    public boolean testIntConNE(int x) {
        return x + INT_MIN != INT_CONST;
    }

    @Test
    @IR(failOn = {CMP_I, ADD_I})
    @IR(counts = {CMP_U, "1"})
    public boolean testIntConLT(int x) {
        return x + INT_MIN < INT_CONST;
    }

    @Test
    @IR(failOn = {CMP_I, ADD_I})
    @IR(counts = {CMP_U, "1"})
    public boolean testIntConLE(int x) {
        return x + INT_MIN <= INT_CONST;
    }

    @Test
    @IR(failOn = {CMP_I, ADD_I})
    @IR(counts = {CMP_U, "1"})
    public boolean testIntConGT(int x) {
        return x + INT_MIN > INT_CONST;
    }

    @Test
    @IR(failOn = {CMP_I, ADD_I})
    @IR(counts = {CMP_U, "1"})
    public boolean testIntConGE(int x) {
        return x + INT_MIN >= INT_CONST;
    }

    @Run(test = {"testIntConEQ", "testIntConNE",
                 "testIntConLT", "testIntConLE",
                 "testIntConGT", "testIntConGE"})
    public void checkTestIntCon() {
        // Verify the transformation "cmp (add X min_jint) c"
        // to "cmpu X (c + min_jint)"
        for (int i = 0; i < INT_DATA.length; i++) {
            Asserts.assertEquals(testIntConEQ(INT_DATA[i]),
                                 i == CONST_INDEX);
            Asserts.assertEquals(testIntConNE(INT_DATA[i]),
                                 i != CONST_INDEX);
            Asserts.assertEquals(testIntConLT(INT_DATA[i]),
                                 i <  CONST_INDEX);
            Asserts.assertEquals(testIntConLE(INT_DATA[i]),
                                 i <= CONST_INDEX);
            Asserts.assertEquals(testIntConGT(INT_DATA[i]),
                                 i >  CONST_INDEX);
            Asserts.assertEquals(testIntConGE(INT_DATA[i]),
                                 i >= CONST_INDEX);
        }
    }

    /*
     * The decrement-and-compare methods return `int` instead of `boolean` to prevent further
     * optimization, such as `is_x2logic`, from changing the IR that needs to be checked.
     */

    @Test
    @IR(failOn = {CMP_U, ADD_I, SUB_I})
    @IR(counts = {CMP_I, "1"})
    public int testIntSubOneLT(int x) {
        return Integer.compareUnsigned(x - 1, x) < 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {CMP_U, ADD_I, SUB_I})
    @IR(counts = {CMP_I, "1"})
    public int testIntSubOneLE(int x) {
        return Integer.compareUnsigned(x - 1, x) <= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {CMP_U, ADD_I, SUB_I})
    @IR(counts = {CMP_I, "1"})
    public int testIntSubOneGT(int x) {
        return Integer.compareUnsigned(x - 1, x) > 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {CMP_U, ADD_I, SUB_I})
    @IR(counts = {CMP_I, "1"})
    public int testIntSubOneGE(int x) {
        return Integer.compareUnsigned(x - 1, x) >= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Run(test = {"testIntSubOneLT", "testIntSubOneLE", "testIntSubOneGT", "testIntSubOneGE"})
    public void checkTestIntSubOne() {
        for (int x : INT_DATA) {
            assertTestIntSubOne(x);
        }
        assertTestIntSubOne(INT_MAX);
        assertTestIntSubOne(RunInfo.getRandom().nextInt());
    }

    @DontCompile
    private void assertTestIntSubOne(int x) {
        Asserts.assertEquals(testIntSubOneLT(x), x != 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubOneLE(x), x != 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubOneGT(x), x == 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubOneGE(x), x == 0 ? TRUE_RESULT : FALSE_RESULT);
    }

    @Test
    @IR(failOn = {CMP_UL, ADD_L, SUB_L})
    @IR(counts = {CMP_L, "1"})
    public int testLongSubOneLT(long x) {
        return Long.compareUnsigned(x - 1, x) < 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {CMP_UL, ADD_L, SUB_L})
    @IR(counts = {CMP_L, "1"})
    public int testLongSubOneLE(long x) {
        return Long.compareUnsigned(x - 1, x) <= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {CMP_UL, ADD_L, SUB_L})
    @IR(counts = {CMP_L, "1"})
    public int testLongSubOneGT(long x) {
        return Long.compareUnsigned(x - 1, x) > 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {CMP_UL, ADD_L, SUB_L})
    @IR(counts = {CMP_L, "1"})
    public int testLongSubOneGE(long x) {
        return Long.compareUnsigned(x - 1, x) >= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Run(test = {"testLongSubOneLT", "testLongSubOneLE", "testLongSubOneGT", "testLongSubOneGE"})
    public void checkTestLongSubOne() {
        for (long x : LONG_DATA) {
            assertTestLongSubOne(x);
        }
        assertTestLongSubOne(LONG_MAX);
        assertTestLongSubOne(RunInfo.getRandom().nextLong());
    }

    @DontCompile
    private void assertTestLongSubOne(long x) {
        Asserts.assertEquals(testLongSubOneLT(x), x != 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubOneLE(x), x != 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubOneGT(x), x == 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubOneGE(x), x == 0 ? TRUE_RESULT : FALSE_RESULT);
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubThreeLT(int x) {
        return Integer.compareUnsigned(x - 3, x) < 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubThreeLE(int x) {
        return Integer.compareUnsigned(x - 3, x) <= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubThreeGT(int x) {
        return Integer.compareUnsigned(x - 3, x) > 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubThreeGE(int x) {
        return Integer.compareUnsigned(x - 3, x) >= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubNegativeThreeLT(int x) {
        return Integer.compareUnsigned(x - (-3), x) < 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubNegativeThreeLE(int x) {
        return Integer.compareUnsigned(x - (-3), x) <= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubNegativeThreeGT(int x) {
        return Integer.compareUnsigned(x - (-3), x) > 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubNegativeThreeGE(int x) {
        return Integer.compareUnsigned(x - (-3), x) >= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubMinLT(int x) {
        return Integer.compareUnsigned(x - INT_MIN, x) < 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubMinLE(int x) {
        return Integer.compareUnsigned(x - INT_MIN, x) <= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubMinGT(int x) {
        return Integer.compareUnsigned(x - INT_MIN, x) > 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubMinGE(int x) {
        return Integer.compareUnsigned(x - INT_MIN, x) >= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubMaxLT(int x) {
        return Integer.compareUnsigned(x - INT_MAX, x) < 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubMaxLE(int x) {
        return Integer.compareUnsigned(x - INT_MAX, x) <= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubMaxGT(int x) {
        return Integer.compareUnsigned(x - INT_MAX, x) > 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_I, SUB_I})
    @IR(counts = {CMP_U, "1"})
    public int testIntSubMaxGE(int x) {
        return Integer.compareUnsigned(x - INT_MAX, x) >= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Run(test = {
        "testIntSubThreeLT", "testIntSubThreeLE", "testIntSubThreeGT", "testIntSubThreeGE",
        "testIntSubNegativeThreeLT", "testIntSubNegativeThreeLE", "testIntSubNegativeThreeGT", "testIntSubNegativeThreeGE",
        "testIntSubMinLT", "testIntSubMinLE", "testIntSubMinGT", "testIntSubMinGE",
        "testIntSubMaxLT", "testIntSubMaxLE", "testIntSubMaxGT", "testIntSubMaxGE"
    })
    public void checkTestIntSubConstant() {
        for (int x : INT_DATA) {
            assertTestIntSubConstant(x);
        }
        for (int c : new int[] {3, -3, INT_MIN, INT_MAX}) {
            assertTestIntSubConstant(c - 1);
            assertTestIntSubConstant(c);
            assertTestIntSubConstant(c + 1);
        }
        assertTestIntSubConstant(RunInfo.getRandom().nextInt());
    }

    @DontCompile
    private void assertTestIntSubConstant(int x) {
        int cmpThree = Integer.compareUnsigned(x - 3, x);
        Asserts.assertEquals(testIntSubThreeLT(x), cmpThree < 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubThreeLE(x), cmpThree <= 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubThreeGT(x), cmpThree > 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubThreeGE(x), cmpThree >= 0 ? TRUE_RESULT : FALSE_RESULT);
        int cmpNegativeThree = Integer.compareUnsigned(x - (-3), x);
        Asserts.assertEquals(testIntSubNegativeThreeLT(x), cmpNegativeThree < 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubNegativeThreeLE(x), cmpNegativeThree <= 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubNegativeThreeGT(x), cmpNegativeThree > 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubNegativeThreeGE(x), cmpNegativeThree >= 0 ? TRUE_RESULT : FALSE_RESULT);
        int cmpMin = Integer.compareUnsigned(x - INT_MIN, x);
        Asserts.assertEquals(testIntSubMinLT(x), cmpMin < 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubMinLE(x), cmpMin <= 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubMinGT(x), cmpMin > 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubMinGE(x), cmpMin >= 0 ? TRUE_RESULT : FALSE_RESULT);
        int cmpMax = Integer.compareUnsigned(x - INT_MAX, x);
        Asserts.assertEquals(testIntSubMaxLT(x), cmpMax < 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubMaxLE(x), cmpMax <= 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubMaxGT(x), cmpMax > 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testIntSubMaxGE(x), cmpMax >= 0 ? TRUE_RESULT : FALSE_RESULT);
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubThreeLT(long x) {
        return Long.compareUnsigned(x - 3, x) < 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubThreeLE(long x) {
        return Long.compareUnsigned(x - 3, x) <= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubThreeGT(long x) {
        return Long.compareUnsigned(x - 3, x) > 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubThreeGE(long x) {
        return Long.compareUnsigned(x - 3, x) >= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubNegativeThreeLT(long x) {
        return Long.compareUnsigned(x - (-3), x) < 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubNegativeThreeLE(long x) {
        return Long.compareUnsigned(x - (-3), x) <= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubNegativeThreeGT(long x) {
        return Long.compareUnsigned(x - (-3), x) > 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubNegativeThreeGE(long x) {
        return Long.compareUnsigned(x - (-3), x) >= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubMinLT(long x) {
        return Long.compareUnsigned(x - LONG_MIN, x) < 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubMinLE(long x) {
        return Long.compareUnsigned(x - LONG_MIN, x) <= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubMinGT(long x) {
        return Long.compareUnsigned(x - LONG_MIN, x) > 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubMinGE(long x) {
        return Long.compareUnsigned(x - LONG_MIN, x) >= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubMaxLT(long x) {
        return Long.compareUnsigned(x - LONG_MAX, x) < 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubMaxLE(long x) {
        return Long.compareUnsigned(x - LONG_MAX, x) <= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubMaxGT(long x) {
        return Long.compareUnsigned(x - LONG_MAX, x) > 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Test
    @IR(failOn = {ADD_L, SUB_L})
    @IR(counts = {CMP_UL, "1"})
    public int testLongSubMaxGE(long x) {
        return Long.compareUnsigned(x - LONG_MAX, x) >= 0 ? TRUE_RESULT : FALSE_RESULT;
    }

    @Run(test = {
        "testLongSubThreeLT", "testLongSubThreeLE", "testLongSubThreeGT", "testLongSubThreeGE",
        "testLongSubNegativeThreeLT", "testLongSubNegativeThreeLE", "testLongSubNegativeThreeGT", "testLongSubNegativeThreeGE",
        "testLongSubMinLT", "testLongSubMinLE", "testLongSubMinGT", "testLongSubMinGE",
        "testLongSubMaxLT", "testLongSubMaxLE", "testLongSubMaxGT", "testLongSubMaxGE"
    })
    public void checkTestLongSubConstant() {
        for (long x : LONG_DATA) {
            assertTestLongSubConstant(x);
        }
        for (long c : new long[] {3, -3, LONG_MIN, LONG_MAX}) {
            assertTestLongSubConstant(c - 1);
            assertTestLongSubConstant(c);
            assertTestLongSubConstant(c + 1);
        }
        assertTestLongSubConstant(RunInfo.getRandom().nextLong());
    }

    @DontCompile
    private void assertTestLongSubConstant(long x) {
        int cmpThree = Long.compareUnsigned(x - 3, x);
        Asserts.assertEquals(testLongSubThreeLT(x), cmpThree < 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubThreeLE(x), cmpThree <= 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubThreeGT(x), cmpThree > 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubThreeGE(x), cmpThree >= 0 ? TRUE_RESULT : FALSE_RESULT);
        int cmpNegativeThree = Long.compareUnsigned(x - (-3), x);
        Asserts.assertEquals(testLongSubNegativeThreeLT(x), cmpNegativeThree < 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubNegativeThreeLE(x), cmpNegativeThree <= 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubNegativeThreeGT(x), cmpNegativeThree > 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubNegativeThreeGE(x), cmpNegativeThree >= 0 ? TRUE_RESULT : FALSE_RESULT);
        int cmpMin = Long.compareUnsigned(x - LONG_MIN, x);
        Asserts.assertEquals(testLongSubMinLT(x), cmpMin < 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubMinLE(x), cmpMin <= 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubMinGT(x), cmpMin > 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubMinGE(x), cmpMin >= 0 ? TRUE_RESULT : FALSE_RESULT);
        int cmpMax = Long.compareUnsigned(x - LONG_MAX, x);
        Asserts.assertEquals(testLongSubMaxLT(x), cmpMax < 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubMaxLE(x), cmpMax <= 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubMaxGT(x), cmpMax > 0 ? TRUE_RESULT : FALSE_RESULT);
        Asserts.assertEquals(testLongSubMaxGE(x), cmpMax >= 0 ? TRUE_RESULT : FALSE_RESULT);
    }

    @Test
    @IR(failOn = {CMP_L, ADD_L})
    @IR(counts = {CMP_UL, "1"})
    public boolean testLongVarEQ(long x, long y) {
        return x + LONG_MIN == y + LONG_MIN;
    }

    @Test
    @IR(failOn = {CMP_L, ADD_L})
    @IR(counts = {CMP_UL, "1"})
    public boolean testLongVarNE(long x, long y) {
        return x + LONG_MIN != y + LONG_MIN;
    }

    @Test
    @IR(failOn = {CMP_L, ADD_L})
    @IR(counts = {CMP_UL, "1"})
    public boolean testLongVarLT(long x, long y) {
        return x + LONG_MIN < y + LONG_MIN;
    }

    @Test
    @IR(failOn = {CMP_L, ADD_L})
    @IR(counts = {CMP_UL, "1"})
    public boolean testLongVarLE(long x, long y) {
        return x + LONG_MIN <= y + LONG_MIN;
    }

    @Test
    @IR(failOn = {CMP_L, ADD_L})
    @IR(counts = {CMP_UL, "1"})
    public boolean testLongVarGT(long x, long y) {
        return x + LONG_MIN > y + LONG_MIN;
    }

    @Test
    @IR(failOn = {CMP_L, ADD_L})
    @IR(counts = {CMP_UL, "1"})
    public boolean testLongVarGE(long x, long y) {
        return x + LONG_MIN >= y + LONG_MIN;
    }

    @Run(test = {"testLongVarEQ", "testLongVarNE",
                 "testLongVarLT", "testLongVarLE",
                 "testLongVarGT", "testLongVarGE"})
    public void checkTestLongVar() {
        // Verify the transformation "cmp (add X min_jlong) (add Y min_jlong)"
        // to "cmpu X Y"
        for (int i = 0; i < LONG_DATA.length; i++) {
            for (int j = 0; j < LONG_DATA.length; j++) {
                Asserts.assertEquals(testLongVarEQ(LONG_DATA[i], LONG_DATA[j]),
                                     i == j);
                Asserts.assertEquals(testLongVarNE(LONG_DATA[i], LONG_DATA[j]),
                                     i != j);
                Asserts.assertEquals(testLongVarLT(LONG_DATA[i], LONG_DATA[j]),
                                     i <  j);
                Asserts.assertEquals(testLongVarLE(LONG_DATA[i], LONG_DATA[j]),
                                     i <= j);
                Asserts.assertEquals(testLongVarGT(LONG_DATA[i], LONG_DATA[j]),
                                     i >  j);
                Asserts.assertEquals(testLongVarGE(LONG_DATA[i], LONG_DATA[j]),
                                     i >= j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_L, ADD_L})
    @IR(counts = {CMP_UL, "1"})
    public boolean testLongConEQ(long x) {
        return x + LONG_MIN == LONG_CONST;
    }

    @Test
    @IR(failOn = {CMP_L, ADD_L})
    @IR(counts = {CMP_UL, "1"})
    public boolean testLongConNE(long x) {
        return x + LONG_MIN != LONG_CONST;
    }

    @Test
    @IR(failOn = {CMP_L, ADD_L})
    @IR(counts = {CMP_UL, "1"})
    public boolean testLongConLT(long x) {
        return x + LONG_MIN < LONG_CONST;
    }

    @Test
    @IR(failOn = {CMP_L, ADD_L})
    @IR(counts = {CMP_UL, "1"})
    public boolean testLongConLE(long x) {
        return x + LONG_MIN <= LONG_CONST;
    }

    @Test
    @IR(failOn = {CMP_L, ADD_L})
    @IR(counts = {CMP_UL, "1"})
    public boolean testLongConGT(long x) {
        return x + LONG_MIN > LONG_CONST;
    }

    @Test
    @IR(failOn = {CMP_L, ADD_L})
    @IR(counts = {CMP_UL, "1"})
    public boolean testLongConGE(long x) {
        return x + LONG_MIN >= LONG_CONST;
    }

    @Run(test = {"testLongConEQ", "testLongConNE",
                 "testLongConLT", "testLongConLE",
                 "testLongConGT", "testLongConGE"})
    public void checkTestLongConGE() {
        // Verify the transformation "cmp (add X min_jlong) c"
        // to "cmpu X (c + min_jlong)"
        for (int i = 0; i < LONG_DATA.length; i++) {
            Asserts.assertEquals(testLongConEQ(LONG_DATA[i]),
                                 i == CONST_INDEX);
            Asserts.assertEquals(testLongConNE(LONG_DATA[i]),
                                 i != CONST_INDEX);
            Asserts.assertEquals(testLongConLT(LONG_DATA[i]),
                                 i <  CONST_INDEX);
            Asserts.assertEquals(testLongConLE(LONG_DATA[i]),
                                 i <= CONST_INDEX);
            Asserts.assertEquals(testLongConGT(LONG_DATA[i]),
                                 i >  CONST_INDEX);
            Asserts.assertEquals(testLongConGE(LONG_DATA[i]),
                                 i >= CONST_INDEX);
        }
    }
}
