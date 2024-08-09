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

package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8332856
 * @summary Test that Ideal transformations of converting eq/ne (cmp (and (urshift X const1) const2) 0) work as expected
 * @library /test/lib /
 * @run main compiler.c2.irTests.ConstantShiftAndEqZeroTests
 */
public class ConstantShiftAndEqZeroTests {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT, IRNode.URSHIFT }, counts = { IRNode.AND_I, "1" })
    public boolean testBitTest(int a) {
        return ((a >> 12) & 0b1) == 0;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT, IRNode.URSHIFT }, counts = { IRNode.AND_I, "1" })
    public boolean testMaskTest(int a) {
        return ((a >> 12) & 0b101) == 0;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT, IRNode.URSHIFT }, counts = { IRNode.AND_I, "1" })
    public boolean testLargeShiftBitTest(int a) {
        return ((a >> 300) & 0b1) == 0;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT, IRNode.URSHIFT }, counts = { IRNode.AND_I, "1" })
    public boolean testLargeShiftMaskTest(int a) {
        return ((a >> 300) & 0b101) == 0;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT, IRNode.URSHIFT }, counts = { IRNode.AND_I, "1" })
    public boolean test30ShiftBitTest(int a) {
        return ((a >> 30) & 0b1) == 0;
    }

    // We expect a RShift here as the shift cannot be converted to a URShiftI without changing behavior
    @Test
    @IR(counts = { IRNode.AND_I, "1", IRNode.RSHIFT_I, "1"})
    public boolean test30ShiftMaskTest(int a) {
        return ((a >> 30) & 0b101) == 0;
    }

    @Test
    @IR(counts = { IRNode.AND_I, "1", IRNode.RSHIFT_I, "1"})
    public boolean test31ShiftMaskTest(int a) {
        return ((a >> 31) & 0b101) == 0;
    }

    // The AND can be omitted completly in this case, as only one bit is left after shifting
    @Test
    @IR(failOn = { IRNode.AND }, counts = {IRNode.URSHIFT_I, "1"})
    public boolean test31ShiftBitTest(int a) {
        return ((a >> 31) & 0b1) == 0;
    }

    // Tests with Unsigned shifts
    @Test
    @IR(failOn = { IRNode.RSHIFT, IRNode.URSHIFT }, counts = { IRNode.AND_I, "1" })
    public boolean testUnsignedBitTest(int a) {
        return ((a >>> 12) & 0b1) == 0;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT, IRNode.URSHIFT }, counts = { IRNode.AND_I, "1" })
    public boolean testUnsignedMaskTest(int a) {
        return ((a >>> 12) & 0b101) == 0;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT, IRNode.URSHIFT }, counts = { IRNode.AND_I, "1" })
    public boolean testUnsignedLargeShiftBitTest(int a) {
        return ((a >>> 300) & 0b1) == 0;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT, IRNode.URSHIFT }, counts = { IRNode.AND_I, "1" })
    public boolean testUnsignedLargeShiftMaskTest(int a) {
        return ((a >>> 300) & 0b101) == 0;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT, IRNode.URSHIFT }, counts = { IRNode.AND_I, "1" })
    public boolean testUnsigned30ShiftBitTest(int a) {
        return ((a >>> 30) & 0b1) == 0;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT, IRNode.URSHIFT }, counts = { IRNode.AND_I, "1" })
    public boolean testUnsigned30ShiftMaskTest(int a) {
        return ((a >>> 30) & 0b101) == 0;
    }

    // The AND can be omitted completly in these cases
    @Test
    @IR(failOn = { IRNode.AND }, counts = { IRNode.URSHIFT_I, "1" })
    public boolean testUnsigned31ShiftMaskTest(int a) {
        return ((a >>> 31) & 0b101) == 0;
    }


    @Test
    @IR(failOn = { IRNode.AND }, counts = {IRNode.URSHIFT_I, "1"})
    public boolean testUnsigned31ShiftBitTest(int a) {
        return ((a >>> 31) & 0b1) == 0;
    }

    @Run(test = { "testBitTest", "testMaskTest", "testLargeShiftBitTest", "testLargeShiftMaskTest", "test30ShiftBitTest", "test30ShiftMaskTest", "test31ShiftBitTest", "test31ShiftMaskTest",
            "testUnsignedBitTest", "testUnsignedMaskTest", "testUnsignedLargeShiftBitTest", "testUnsignedLargeShiftMaskTest", "testUnsigned30ShiftBitTest", "testUnsigned30ShiftMaskTest", "testUnsigned31ShiftBitTest", "testUnsigned31ShiftMaskTest"})
    public void runTests() {
        testConstantShiftAndEqZero(-1);
        testConstantShiftAndEqZero(0);
        testConstantShiftAndEqZero(1);
        testConstantShiftAndEqZero(10);
        testConstantShiftAndEqZero(20);
        testConstantShiftAndEqZero(0b101 >> 12);
        testConstantShiftAndEqZero(Short.MAX_VALUE);
        testConstantShiftAndEqZero(Integer.MAX_VALUE);
        testConstantShiftAndEqZero(Integer.MIN_VALUE);
        testConstantShiftAndEqZero(RANDOM.nextInt());
    }

    @DontCompile
    public void testConstantShiftAndEqZero(int a) {
        Asserts.assertEQ(((a >> 12) & 0b1) == 0, testBitTest(a));
        Asserts.assertEQ(((a >> 12) & 0b101) == 0, testMaskTest(a));
        Asserts.assertEQ(((a >> 300) & 0b1) == 0, testLargeShiftBitTest(a));
        Asserts.assertEQ(((a >> 300) & 0b101) == 0, testLargeShiftMaskTest(a));
        Asserts.assertEQ(((a >> 30) & 0b1) == 0, test30ShiftBitTest(a));
        Asserts.assertEQ(((a >> 30) & 0b101) == 0, test30ShiftMaskTest(a));
        Asserts.assertEQ(((a >> 31) & 0b1) == 0, test31ShiftBitTest(a));
        Asserts.assertEQ(((a >> 31) & 0b101) == 0, test31ShiftMaskTest(a));
        Asserts.assertEQ(((a >>> 12) & 0b1) == 0, testUnsignedBitTest(a));
        Asserts.assertEQ(((a >>> 12) & 0b101) == 0, testUnsignedMaskTest(a));
        Asserts.assertEQ(((a >>> 300) & 0b1) == 0, testUnsignedLargeShiftBitTest(a));
        Asserts.assertEQ(((a >>> 300) & 0b101) == 0, testUnsignedLargeShiftMaskTest(a));
        Asserts.assertEQ(((a >>> 30) & 0b1) == 0, testUnsigned30ShiftBitTest(a));
        Asserts.assertEQ(((a >>> 30) & 0b101) == 0, testUnsigned30ShiftMaskTest(a));
        Asserts.assertEQ(((a >>> 31) & 0b1) == 0, testUnsigned31ShiftBitTest(a));
        Asserts.assertEQ(((a >>> 31) & 0b101) == 0, testUnsigned31ShiftMaskTest(a));
    }
}
