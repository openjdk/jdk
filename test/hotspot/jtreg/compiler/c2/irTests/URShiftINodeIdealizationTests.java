/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8297384 8315066
 * @summary Test that Ideal transformations of URShiftINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.URShiftINodeIdealizationTests
 */
public class URShiftINodeIdealizationTests {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test1", "test2", "test3",
                 "test4", "test5"})
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(0, 0);
        assertResult(a, b);
        assertResult(min, min);
        assertResult(max, max);
    }

    @DontCompile
    public void assertResult(int a, int b) {
        Asserts.assertEQ((a << 2022) >>> 2022, test1(a));
        Asserts.assertEQ((a >> 2022) >>> 31, test2(a));
        Asserts.assertEQ(1, test3(a, b));
        Asserts.assertEQ(1, test4(a, b));
        Asserts.assertEQ(0, test5(a, b));
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT, IRNode.URSHIFT })
    @IR(counts = { IRNode.AND, "1" })
    // Checks (x << 2022) >>> 2022 => x & C where C = ((1 << (32 - 6)) - 1)
    public int test1(int x) {
        return (x << 2022) >>> 2022;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    @IR(counts = { IRNode.URSHIFT, "1" })
    // Checks (x >> 2022) >>> 31 => x >>> 31
    public int test2(int x) {
        return (x >> 2022) >>> 31;
    }

    @Test
    @IR(failOn = {IRNode.URSHIFT_I})
    public int test3(int x, int y) {
        return (Math.max(x, -100) >>> y) >= -100 ? 1 : 0;
    }

    @Test
    @IR(failOn = {IRNode.URSHIFT_I})
    public int test4(int x, int y) {
        return Integer.compareUnsigned(x >>> (y | 8), -1 >>> 8) <= 0 ? 1 : 0;
    }

    @Test
    @IR(failOn = {IRNode.URSHIFT_I})
    public int test5(int x, int y) {
        return (x >>> (y | 2)) & (Integer.MIN_VALUE >>> 1);
    }
}
