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
 * @summary Test that Ideal transformations of AndLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.AndLNodeIdealizationTests
 */
public class AndLNodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test1", "test2", "test3",
                 "test4"})
    public void runMethod() {
        long a = RunInfo.getRandom().nextLong();
        long b = RunInfo.getRandom().nextLong();

        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;

        assertResult(0, 0);
        assertResult(a, b);
        assertResult(min, min);
        assertResult(max, max);
    }

    @DontCompile
    public void assertResult(long a, long b) {
        Asserts.assertEQ(b << 8, test1(a, b));
        Asserts.assertEQ(1L, test2(a, b));
        Asserts.assertEQ(0L, test3(a, b));
        Asserts.assertEQ(1L, test4(a, b));
    }

    @Test
    @IR(failOn = {IRNode.AND_L, IRNode.OR_L})
    // All bits that can be unset in one operand is known to be unset in the other
    public long test1(long x, long y) {
        return (x | -256) & (y << 8);
    }

    @Test
    @IR(failOn = {IRNode.AND_L, IRNode.OR_L})
    // Bits set in both are set in the result
    public long test2(long x, long y) {
        return ((x | 3) & (y | 101)) & 1;
    }

    @Test
    @IR(failOn = {IRNode.AND_L})
    // Bits unset in either are unset in the result
    public long test3(long x, long y) {
        return (x & (y & 6)) & 1;
    }

    @Test
    @IR(failOn = {IRNode.AND_L})
    // The unsigned value of the result is smaller than both operands
    public long test4(long x, long y) {
        return Long.compareUnsigned(((byte)x + 150L) & y, 300) < 0 ? 1 : 0;
    }
}
