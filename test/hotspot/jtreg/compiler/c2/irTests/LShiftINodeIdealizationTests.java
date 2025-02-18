/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8297384 8303238
 * @summary Test that Ideal transformations of LShiftINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.LShiftINodeIdealizationTests
 */
public class LShiftINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "test1", "test2", "test3", "test4", "test5", "test6", "test7", "test8",
                  "testDoubleShift1", "testDoubleShift2", "testDoubleShift3", "testDoubleShift4", "testDoubleShift5"
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
        Asserts.assertEQ(((a << 2) << 3) << 1, testDoubleShift2(a));
        Asserts.assertEQ((a << 31) << 1, testDoubleShift3(a));
        Asserts.assertEQ((a << 1) << 31, testDoubleShift4(a));
        Asserts.assertEQ(((a << 30) << 1) << 1, testDoubleShift5(a));
    }

    @Test
    @IR(counts = { IRNode.LSHIFT, "1"})
    // Checks (x << 2) << 3 => x << 5
    public int testDoubleShift1(int x) {
        return (x << 2) << 3;
    }

    @Test
    @IR(counts = { IRNode.LSHIFT, "1"})
    // Checks ((x << 2) << 3) << 1 => x << 6
    public int testDoubleShift2(int x) {
        return ((x << 2) << 3) << 1;
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT })
    // Checks (x << 31) << 1 => 0
    public int testDoubleShift3(int x) {
        return (x << 31) << 1;
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT })
    // Checks (x << 31) << 1 => 0
    public int testDoubleShift4(int x) {
        return (x << 31) << 1;
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT })
    // Checks ((x << 30) << 1) << 1 => 0
    public int testDoubleShift5(int x) {
        return ((x << 30) << 1) << 1;
    }
}
