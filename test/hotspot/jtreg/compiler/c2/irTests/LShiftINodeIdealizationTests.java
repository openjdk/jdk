/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static compiler.lib.generators.Generators.G;

/*
 * @test
 * @bug 8297384 8303238
 * @summary Test that Ideal transformations of LShiftINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.LShiftINodeIdealizationTests
 */
public class LShiftINodeIdealizationTests {
    private static final int CON0 = G.ints().next();
    private static final int CON1 = G.ints().next();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test1", "test2", "test3", "test4", "test5", "test6", "test7", "test8",
            "testDoubleShift1",
            "testDoubleShift2",
            "testDoubleShift3",
            "testDoubleShift4",
            "testDoubleShift5",
            "testDoubleShift6",
            "testDoubleShift7",
            "testDoubleShift8",
            "testDoubleShift9",
            "testDoubleShiftSliceAndStore",
            "testRandom",
            "testShiftValue",
            "testShiftValueOverflow",
            "testShiftMultiple32",
            "testShiftOfAddSameInput",
            "testLargeShiftOfAddSameInput",
            "testShiftOfAddConstant",
            "testLShiftOfAndOfRShiftSameCon",
            "testLShiftOfAndOfURShiftSameCon",
            "testLShiftOfAndOfRShift",
            "testLShiftOfAndOfURShift",
            "testLShiftOfAndOfCon",
            "testShiftOfSubConstant",
    })
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();
        int c = RunInfo.getRandom().nextInt();
        int d = RunInfo.getRandom().nextInt();

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(0);
        assertResult(a);
        assertResult(b);
        assertResult(c);
        assertResult(d);
        assertResult(min);
        assertResult(max);

        Asserts.assertEQ(42 << 1, testShiftValue(42));
        Asserts.assertEQ(Integer.MAX_VALUE << 1, testShiftValueOverflow(Integer.MAX_VALUE));
        Asserts.assertEQ((Integer.MAX_VALUE-1) << 1, testShiftValueOverflow(Integer.MAX_VALUE-1));

        assertResult(a, b);
        assertResult(c, d);
        assertResult(a, min);
        assertResult(a, max);
        assertResult(min, a);
        assertResult(max, a);
        assertResult(min, max);
        assertResult(max, min);
        assertResult(min, min);
        assertResult(max, max);
    }

    private void assertResult(int a, int b) {
        otherInput = b;
        Asserts.assertEQ(((a >> 4) & b) << 4, testLShiftOfAndOfRShiftSameCon(a));
        Asserts.assertEQ(((a >>> 4) & b) << 4, testLShiftOfAndOfURShiftSameCon(a));
        Asserts.assertEQ(((a >> 4) & b) << 8, testLShiftOfAndOfRShift(a));
        Asserts.assertEQ(((a >>> 4) & b) << 8, testLShiftOfAndOfURShift(a));
    }

    @DontCompile
    public void assertResult(int a) {
        Asserts.assertEQ((a >> 2022) << 2022, test1(a));
        Asserts.assertEQ((a >>> 2022) << 2022, test2(a));
        Asserts.assertEQ((a >> 4) << 8, test3(a));
        Asserts.assertEQ((a >>> 4) << 8, test4(a));
        Asserts.assertEQ((a >> 8) << 4, test5(a));
        Asserts.assertEQ((a >>> 8) << 4, test6(a));
        Asserts.assertEQ(((a >> 4) & 0xFF) << 8, test7(a));
        Asserts.assertEQ(((a >>> 4) & 0xFF) << 8, test8(a));
        Asserts.assertEQ(a, testShiftMultiple32(a));
        Asserts.assertEQ((a + a) << 1, testShiftOfAddSameInput(a));
        Asserts.assertEQ((a + a) << 31, testLargeShiftOfAddSameInput(a));
        Asserts.assertEQ(((a + 1) << 1) + 1, testShiftOfAddConstant(a));
        Asserts.assertEQ((a & ((1 << (32 - 10)) -1)) << 10, testLShiftOfAndOfCon(a));
        Asserts.assertEQ(((1 - a) << 1) + 1, testShiftOfSubConstant(a));

        assertDoubleShiftResult(a);
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT, IRNode.RSHIFT })
    @IR(counts = { IRNode.AND, "1" })
    // Checks (x >> 2022) << 2022 => x & C where C = -(1 << 6)
    public int test1(int x) {
        return (x >> 2022) << 2022;
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT, IRNode.URSHIFT })
    @IR(counts = { IRNode.AND, "1" })
    // Checks (x >>> 2022) << 2022 => x & C where C = -(1 << 6)
    public int test2(int x) {
        return (x >>> 2022) << 2022;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    @IR(counts = { IRNode.AND, "1", IRNode.LSHIFT, "1" })
    // Checks (x >> 4) << 8 => (x << 4) & -16
    public int test3(int x) {
        return (x >> 4) << 8;
    }

    @Test
    @IR(failOn = { IRNode.URSHIFT })
    @IR(counts = { IRNode.AND, "1", IRNode.LSHIFT, "1" })
    // Checks (x >>> 4) << 8 => (x << 4) & -16
    public int test4(int x) {
        return (x >>> 4) << 8;
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT })
    @IR(counts = { IRNode.AND, "1", IRNode.RSHIFT, "1" })
    // Checks (x >> 8) << 4 => (x >> 4) & -16
    public int test5(int x) {
        return (x >> 8) << 4;
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT })
    @IR(counts = { IRNode.AND, "1", IRNode.URSHIFT, "1" })
    // Checks (x >>> 8) << 4 => (x >>> 4) & -16
    public int test6(int x) {
        return (x >>> 8) << 4;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    @IR(counts = { IRNode.AND, "1", IRNode.LSHIFT, "1" })
    // Checks ((x >> 4) & 0xFF) << 8 => (x << 4) & 0xFF00
    public int test7(int x) {
        return ((x >> 4) & 0xFF) << 8;
    }

    @Test
    @IR(failOn = { IRNode.URSHIFT })
    @IR(counts = { IRNode.AND, "1", IRNode.LSHIFT, "1" })
    // Checks ((x >>> 4) & 0xFF) << 8 => (x << 4) & 0xFF00
    public int test8(int x) {
        return ((x >>> 4) & 0xFF) << 8;
    }

    @DontCompile
    public void assertDoubleShiftResult(int a) {
        Asserts.assertEQ((a << 2) << 3, testDoubleShift1(a));
        Asserts.assertEQ(a << 5, testDoubleShift1(a));

        Asserts.assertEQ(((a << 2) << 3) << 1, testDoubleShift2(a));
        Asserts.assertEQ(a << 6, testDoubleShift2(a));

        Asserts.assertEQ((a << 31) << 1, testDoubleShift3(a));
        Asserts.assertEQ(0, testDoubleShift3(a));

        Asserts.assertEQ((a << 1) << 31, testDoubleShift4(a));
        Asserts.assertEQ(0, testDoubleShift4(a));

        Asserts.assertEQ(((a << 30) << 1) << 1, testDoubleShift5(a));
        Asserts.assertEQ(0, testDoubleShift5(a));

        Asserts.assertEQ((a * 4) << 3, testDoubleShift6(a));
        Asserts.assertEQ(a << 5, testDoubleShift6(a));

        Asserts.assertEQ((a << 3) * 4, testDoubleShift7(a));
        Asserts.assertEQ(a << 5, testDoubleShift7(a));

        Asserts.assertEQ(a << 33, testDoubleShift8(a));
        Asserts.assertEQ(a << 1, testDoubleShift8(a));

        Asserts.assertEQ((a << 30) << 3, testDoubleShift9(a));
        Asserts.assertEQ(0, testDoubleShift9(a));

        short[] arr = new short[1];
        arr[0] = (short)a;
        Asserts.assertEQ((short)(a << 3), testDoubleShiftSliceAndStore(arr)[0]);

        Asserts.assertEQ((a << CON0) << CON1, testRandom(a));
    }

    @Test
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks (x << 2) << 3 => x << 5
    public int testDoubleShift1(int x) {
        return (x << 2) << 3;
    }

    @Test
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks ((x << 2) << 3) << 1 => x << 6
    public int testDoubleShift2(int x) {
        return ((x << 2) << 3) << 1;
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT})
    // Checks (x << 31) << 1 => 0
    public int testDoubleShift3(int x) {
        return (x << 31) << 1;
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT})
    // Checks (x << 1) << 31 => 0
    public int testDoubleShift4(int x) {
        return (x << 1) << 31;
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT})
    // Checks ((x << 30) << 1) << 1 => 0
    public int testDoubleShift5(int x) {
        return ((x << 30) << 1) << 1;
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks (x * 4) << 3 => x << 5
    public int testDoubleShift6(int x) {
        return (x * 4) << 3;
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks (x << 3) * 4 => x << 5
    public int testDoubleShift7(int x) {
        return (x << 3) * 4;
    }

    @Test
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks x << 33 => x << 1
    public int testDoubleShift8(int x) {
        return x << 33;
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT})
    // Checks (x << 30) << 3 => 0
    public int testDoubleShift9(int x) {
        return (x << 30) << 3;
    }

    @Test
    @IR(failOn = {IRNode.MUL, IRNode.RSHIFT})
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks (short) (a[0] << 3) => (((a[0] << 3) << 16) >> 16) => ((a[0] << 19) >> 16) => (a[0] << 3)
    public short[] testDoubleShiftSliceAndStore(short[] a) {
        short[] res = new short[1];
        res[0] = (short) (a[0] << 3);
        return res;
    }

    @Test
    @IR(counts = {IRNode.LSHIFT, "<= 1"})
    public int testRandom(int x) {
        return (x << CON0) << CON1;
    }

    @Test
    @IR(counts = {IRNode.LSHIFT, "1"}, failOn = { IRNode.IF } )
    public int testShiftValue(int x) {
        x = Integer.min(Integer.max(x, 10), 100);
        int shift = x << 1;
        if (shift > 200 || shift < 20) {
            throw new RuntimeException("never taken");
        }
        return shift;
    }

    @Test
    @IR(counts = {IRNode.LSHIFT, "1", IRNode.IF, "2" } )
    public int testShiftValueOverflow(int x) {
        x = Integer.max(x, Integer.MAX_VALUE - 1);
        int shift = x << 1;
        if (shift != -2 && shift != -4) {
            throw new RuntimeException("never taken");
        }
        return shift;
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT_I } )
    public int testShiftMultiple32(int x) {
        return x << 128;
    }

    @Test
    @IR(counts = { IRNode.LSHIFT_I, "1" }, failOn = { IRNode.ADD_I } )
    public int testShiftOfAddSameInput(int x) {
        return (x + x) << 1;
    }

    @Test
    @IR(counts = { IRNode.LSHIFT_I, "1", IRNode.ADD_I, "1" } )
    public int testLargeShiftOfAddSameInput(int x) {
        return (x + x) << 31;
    }

    @Test
    @IR(counts = { IRNode.LSHIFT_I, "1",  IRNode.ADD_I, "1" } )
    public int testShiftOfAddConstant(int x) {
        return ((x + 1) << 1) + 1;
    }

    static short shortField;
    static byte byteField;

    @Test
    @IR(counts = { IRNode.ADD_I, "1"} , failOn = { IRNode.LSHIFT_I, IRNode.RSHIFT_I } )
    @Arguments( values = { Argument.NUMBER_42 })
    public void testStoreShort1(int x) {
        shortField = (short)(x + x);
    }

    @Test
    @IR(counts = { IRNode.ADD_I, "1"} , failOn = { IRNode.LSHIFT_I, IRNode.RSHIFT_I } )
    @Arguments( values = { Argument.NUMBER_42 })
    public void testStoreByte1(int x) {
        byteField = (byte)(x + x);
    }

    @Test
    @IR(counts = { IRNode.ADD_I, "1"} , failOn = { IRNode.LSHIFT_I, IRNode.RSHIFT_I } )
    @Arguments( values = { Argument.NUMBER_42 })
    public void testStoreShort2(int x) {
        shortField = (short)(x + 1);
    }

    @Test
    @IR(counts = { IRNode.ADD_I, "1"} , failOn = { IRNode.LSHIFT_I, IRNode.RSHIFT_I } )
    @Arguments( values = { Argument.NUMBER_42 })
    public void testStoreByte2(int x) {
        byteField = (byte)(x + 1);
    }

    @Test
    @IR(counts = { IRNode.SUB_I, "1"} , failOn = { IRNode.LSHIFT_I, IRNode.RSHIFT_I } )
    @Arguments( values = { Argument.NUMBER_42 })
    public void testStoreShort3(int x) {
        shortField = (short)(1 - x);
    }

    @Test
    @IR(counts = { IRNode.SUB_I, "1"} , failOn = { IRNode.LSHIFT_I, IRNode.RSHIFT_I } )
    @Arguments( values = { Argument.NUMBER_42 })
    public void testStoreByte3(int x) {
        byteField = (byte)(1 - x);
    }

    static int otherInput;

    @Test
    @IR(counts = { IRNode.AND_I, "1", IRNode.LSHIFT_I, "1" } , failOn = { IRNode.RSHIFT_I } )
    public int testLShiftOfAndOfRShiftSameCon(int x) {
        int shift = x >> 4;
        int y = otherInput;
        return (shift & y) << 4;
    }

    @Test
    @IR(counts = { IRNode.AND_I, "1", IRNode.LSHIFT_I, "1" } , failOn = { IRNode.URSHIFT_I } )
    public int testLShiftOfAndOfURShiftSameCon(int x) {
        int shift = x >>> 4;
        int y = otherInput;
        return (shift & y) << 4;
    }

    @Test
    @IR(counts = { IRNode.AND_I, "2", IRNode.LSHIFT_I, "2" } , failOn = { IRNode.RSHIFT_I } )
    public int testLShiftOfAndOfRShift(int x) {
        int shift = x >> 4;
        int y = otherInput;
        return (shift & y) << 8;
    }

    @Test
    @IR(counts = { IRNode.AND_I, "2", IRNode.LSHIFT_I, "2" } , failOn = { IRNode.URSHIFT_I } )
    public int testLShiftOfAndOfURShift(int x) {
        int shift = x >>> 4;
        int y = otherInput;
        return (shift & y) << 8;
    }

    @Test
    @IR(counts = { IRNode.LSHIFT_I, "1" } , failOn = { IRNode.AND_I } )
    public int testLShiftOfAndOfCon(int x) {
        return (x & ((1 << (32 - 10)) -1)) << 10;
    }

    @Test
    @IR(counts = { IRNode.LSHIFT_I, "1",  IRNode.SUB_I, "1" }, failOn =  { IRNode.ADD_I })
    public int testShiftOfSubConstant(int x) {
        return ((1 - x) << 1) + 1;
    }
}
