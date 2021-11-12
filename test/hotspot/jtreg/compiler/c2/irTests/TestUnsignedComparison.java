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

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.start();
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntEQ(int x, int y) {
        return x + Integer.MIN_VALUE == y + Integer.MIN_VALUE;
    }

    @Run(test = "testIntEQ")
    public void checkTestIntEQ() {
        for (int i = 0; i < INT_DATA.length; i++) {
            for (int j = 0; j < INT_DATA.length; j++) {
                Asserts.assertEquals(testIntEQ(INT_DATA[i], INT_DATA[j]),
                                     i == j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntNE(int x, int y) {
        return x + Integer.MIN_VALUE != y + Integer.MIN_VALUE;
    }

    @Run(test = "testIntNE")
    public void checkTestIntNE() {
        for (int i = 0; i < INT_DATA.length; i++) {
            for (int j = 0; j < INT_DATA.length; j++) {
                Asserts.assertEquals(testIntNE(INT_DATA[i], INT_DATA[j]),
                                     i != j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntLT(int x, int y) {
        return x + Integer.MIN_VALUE < y + Integer.MIN_VALUE;
    }

    @Run(test = "testIntLT")
    public void checkTestIntLT() {
        for (int i = 0; i < INT_DATA.length; i++) {
            for (int j = 0; j < INT_DATA.length; j++) {
                Asserts.assertEquals(testIntLT(INT_DATA[i], INT_DATA[j]),
                                     i < j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntLE(int x, int y) {
        return x + Integer.MIN_VALUE <= y + Integer.MIN_VALUE;
    }

    @Run(test = "testIntLE")
    public void checkTestIntLE() {
        for (int i = 0; i < INT_DATA.length; i++) {
            for (int j = 0; j < INT_DATA.length; j++) {
                Asserts.assertEquals(testIntLE(INT_DATA[i], INT_DATA[j]),
                                     i <= j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntGT(int x, int y) {
        return x + Integer.MIN_VALUE > y + Integer.MIN_VALUE;
    }

    @Run(test = "testIntGT")
    public void checkTestIntGT() {
        for (int i = 0; i < INT_DATA.length; i++) {
            for (int j = 0; j < INT_DATA.length; j++) {
                Asserts.assertEquals(testIntGT(INT_DATA[i], INT_DATA[j]),
                                     i > j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testIntGE(int x, int y) {
        return x + Integer.MIN_VALUE >= y + Integer.MIN_VALUE;
    }

    @Run(test = "testIntGE")
    public void checkTestIntGE() {
        for (int i = 0; i < INT_DATA.length; i++) {
            for (int j = 0; j < INT_DATA.length; j++) {
                Asserts.assertEquals(testIntGE(INT_DATA[i], INT_DATA[j]),
                                     i >= j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongEQ(long x, long y) {
        return x + Long.MIN_VALUE == y + Long.MIN_VALUE;
    }

    @Run(test = "testLongEQ")
    public void checkTestLongEQ() {
        for (int i = 0; i < LONG_DATA.length; i++) {
            for (int j = 0; j < LONG_DATA.length; j++) {
                Asserts.assertEquals(testLongEQ(LONG_DATA[i], LONG_DATA[j]),
                                     i == j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongNE(long x, long y) {
        return x + Long.MIN_VALUE != y + Long.MIN_VALUE;
    }

    @Run(test = "testLongNE")
    public void checkTestLongNE() {
        for (int i = 0; i < LONG_DATA.length; i++) {
            for (int j = 0; j < LONG_DATA.length; j++) {
                Asserts.assertEquals(testLongNE(LONG_DATA[i], LONG_DATA[j]),
                                     i != j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongLT(long x, long y) {
        return x + Long.MIN_VALUE < y + Long.MIN_VALUE;
    }

    @Run(test = "testLongLT")
    public void checkTestLongLT() {
        for (int i = 0; i < LONG_DATA.length; i++) {
            for (int j = 0; j < LONG_DATA.length; j++) {
                Asserts.assertEquals(testLongLT(LONG_DATA[i], LONG_DATA[j]),
                                     i < j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongLE(long x, long y) {
        return x + Long.MIN_VALUE <= y + Long.MIN_VALUE;
    }

    @Run(test = "testLongLE")
    public void checkTestLongLE() {
        for (int i = 0; i < LONG_DATA.length; i++) {
            for (int j = 0; j < LONG_DATA.length; j++) {
                Asserts.assertEquals(testLongLE(LONG_DATA[i], LONG_DATA[j]),
                                     i <= j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongGT(long x, long y) {
        return x + Long.MIN_VALUE > y + Long.MIN_VALUE;
    }

    @Run(test = "testLongGT")
    public void checkTestLongGT() {
        for (int i = 0; i < LONG_DATA.length; i++) {
            for (int j = 0; j < LONG_DATA.length; j++) {
                Asserts.assertEquals(testLongGT(LONG_DATA[i], LONG_DATA[j]),
                                     i > j);
            }
        }
    }

    @Test
    @IR(failOn = {CMP_REGEX, ADD_REGEX})
    @IR(counts = {CMPU_REGEX, "1"})
    public boolean testLongGE(long x, long y) {
        return x + Long.MIN_VALUE >= y + Long.MIN_VALUE;
    }

    @Run(test = "testLongGE")
    public void checkTestLongGE() {
        for (int i = 0; i < LONG_DATA.length; i++) {
            for (int j = 0; j < LONG_DATA.length; j++) {
                Asserts.assertEquals(testLongGE(LONG_DATA[i], LONG_DATA[j]),
                                     i >= j);
            }
        }
    }
}
