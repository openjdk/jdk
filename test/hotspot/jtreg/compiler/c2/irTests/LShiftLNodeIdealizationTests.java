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
 * @bug 8303238 8315066
 * @summary Test that Ideal transformations of LShiftLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.LShiftLNodeIdealizationTests
 */
public class LShiftLNodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test3", "test4", "test5",
                 "test6", "test7", "test8",
                 "test9", "test10", "test11"})
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
        Asserts.assertEQ((a >> 4L) << 8L, test3(a));
        Asserts.assertEQ((a >>> 4L) << 8L, test4(a));
        Asserts.assertEQ((a >> 8L) << 4L, test5(a));
        Asserts.assertEQ((a >>> 8L) << 4L, test6(a));
        Asserts.assertEQ(((a >> 4L) & 0xFFL) << 8L, test7(a));
        Asserts.assertEQ(((a >>> 4L) & 0xFFL) << 8L, test8(a));
        Asserts.assertEQ(1L, test9(a, b));
        Asserts.assertEQ(1L, test10(a, b));
        Asserts.assertEQ(0L, test11(a, b));
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    @IR(counts = { IRNode.AND, "1", IRNode.LSHIFT, "1" })
    // Checks (x >> 4) << 8 => (x << 4) & -16
    public long test3(long x) {
        return (x >> 4L) << 8L;
    }

    @Test
    @IR(failOn = { IRNode.URSHIFT })
    @IR(counts = { IRNode.AND, "1", IRNode.LSHIFT, "1" })
    // Checks (x >>> 4) << 8 => (x << 4) & -16
    public long test4(long x) {
        return (x >>> 4L) << 8L;
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT })
    @IR(counts = { IRNode.AND, "1", IRNode.RSHIFT, "1" })
    // Checks (x >> 8) << 4 => (x >> 4) & -16
    public long test5(long x) {
        return (x >> 8L) << 4L;
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT })
    @IR(counts = { IRNode.AND, "1", IRNode.URSHIFT, "1" })
    // Checks (x >>> 8) << 4 => (x >>> 4) & -16
    public long test6(long x) {
        return (x >>> 8L) << 4L;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    @IR(counts = { IRNode.AND, "1", IRNode.LSHIFT, "1" })
    // Checks ((x >> 4) & 0xFF) << 8 => (x << 4) & 0xFF00
    public long test7(long x) {
        return ((x >> 4L) & 0xFFL) << 8L;
    }

    @Test
    @IR(failOn = { IRNode.URSHIFT })
    @IR(counts = { IRNode.AND, "1", IRNode.LSHIFT, "1" })
    // Checks ((x >>> 4) & 0xFF) << 8 => (x << 4) & 0xFF00
    public long test8(long x) {
        return ((x >>> 4L) & 0xFFL) << 8L;
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT_L, IRNode.CMP_L})
    // Signed bounds
    public long test9(long x, long y) {
        return ((long)Math.max(Math.min((int)x, 100), -100) << (y & 8)) <= (100 << 8) ? 1 : 0;
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT_L, IRNode.CMP_UL})
    // Unsigned bounds
    public long test10(long x, long y) {
        return Long.compareUnsigned((long)Math.max(Math.min((int)x, 100), 0) << (y & 8), 100 << 8) <= 0 ? 1 : 0;
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT_L, IRNode.AND_L})
    // Bits
    public long test11(long x, long y) {
        return (x << (y | 3)) & 7;
    }
}
