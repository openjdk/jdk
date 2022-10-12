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

package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8276162
 * @summary Test that unsigned comparison transformation works as intended.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestUnsignedComparison
 */
public class TestUnsignedComparison {
    private static final String CMP_REGEX = "(\\d+(\\s){2}(" + "Cmp(I|L)" + ".*)+(\\s){2}===.*)";
    private static final String CMPU_REGEX = "(\\d+(\\s){2}(" + "Cmp(U|UL)" + ".*)+(\\s){2}===.*)";
    private static final String ADD_REGEX = "(\\d+(\\s){2}(" + "Add(I|L)" + ".*)+(\\s){2}===.*)";

    private static final int INT_MIN = Integer.MIN_VALUE;
    private static final long LONG_MIN = Long.MIN_VALUE;

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
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntVarEQ(int x, int y) {
        return x + INT_MIN == y + INT_MIN;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntVarNE(int x, int y) {
        return x + INT_MIN != y + INT_MIN;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntVarLT(int x, int y) {
        return x + INT_MIN < y + INT_MIN;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntVarLE(int x, int y) {
        return x + INT_MIN <= y + INT_MIN;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntVarGT(int x, int y) {
        return x + INT_MIN > y + INT_MIN;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
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
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntConEQ(int x) {
        return x + INT_MIN == INT_CONST;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntConNE(int x) {
        return x + INT_MIN != INT_CONST;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntConLT(int x) {
        return x + INT_MIN < INT_CONST;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntConLE(int x) {
        return x + INT_MIN <= INT_CONST;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntConGT(int x) {
        return x + INT_MIN > INT_CONST;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
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

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongVarEQ(long x, long y) {
        return x + LONG_MIN == y + LONG_MIN;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongVarNE(long x, long y) {
        return x + LONG_MIN != y + LONG_MIN;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongVarLT(long x, long y) {
        return x + LONG_MIN < y + LONG_MIN;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongVarLE(long x, long y) {
        return x + LONG_MIN <= y + LONG_MIN;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongVarGT(long x, long y) {
        return x + LONG_MIN > y + LONG_MIN;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
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
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongConEQ(long x) {
        return x + LONG_MIN == LONG_CONST;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongConNE(long x) {
        return x + LONG_MIN != LONG_CONST;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongConLT(long x) {
        return x + LONG_MIN < LONG_CONST;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongConLE(long x) {
        return x + LONG_MIN <= LONG_CONST;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongConGT(long x) {
        return x + LONG_MIN > LONG_CONST;
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
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
