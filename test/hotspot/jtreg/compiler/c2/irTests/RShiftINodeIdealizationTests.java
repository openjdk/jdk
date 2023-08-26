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

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8315066
 * @summary Test that Ideal transformations of RShiftINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.RShiftINodeIdealizationTests
 */
public class RShiftINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test1", "test2", "test3"})
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
        Asserts.assertEQ(1, test1(a, b));
        Asserts.assertEQ(1, test2(a, b));
        Asserts.assertEQ(Integer.MIN_VALUE >> 4, test3(a, b));
    }

    @Test
    @IR(failOn = {IRNode.RSHIFT_I})
    public int test1(int x, int y) {
        return (Math.max(x, -100) >> (y | 3)) >= (-100 >> 3) ? 1 : 0;
    }

    @Test
    @IR(failOn = {IRNode.RSHIFT_I})
    public int test2(int x, int y) {
        return Integer.compareUnsigned((x >>> 1) >> (y | 8), Integer.MAX_VALUE >> 8) <= 0 ? 1 : 0;
    }

    @Test
    @IR(failOn = {IRNode.RSHIFT_I})
    public int test3(int x, int y) {
        return ((x | Integer.MIN_VALUE) >> (y | 8)) & (Integer.MIN_VALUE >> 4);
    }
}
