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
import static compiler.lib.generators.Generators.G;

/*
 * @test
 * @bug 8303238
 * @summary Test that Ideal transformations of LShiftLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.LShiftLNodeIdealizationTests
 */
public class LShiftLNodeIdealizationTests {
    private static final long CON0 = G.longs().next();
    private static final long CON1 = G.longs().next();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test3", "test4", "test5", "test6", "test7", "test8",
            "testDoubleShift1",
            "testDoubleShift2",
            "testDoubleShift3",
            "testDoubleShift4",
            "testDoubleShift5",
            "testDoubleShift6",
            "testDoubleShift7",
            "testDoubleShift8",
            "testDoubleShift9",
            "testRandom",
    })
    public void runMethod() {
        long a = RunInfo.getRandom().nextLong();
        long b = RunInfo.getRandom().nextLong();
        long c = RunInfo.getRandom().nextLong();
        long d = RunInfo.getRandom().nextLong();

        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;

        assertResult(0);
        assertResult(a);
        assertResult(b);
        assertResult(c);
        assertResult(d);
        assertResult(min);
        assertResult(max);
    }

    @DontCompile
    public void assertResult(long a) {
        Asserts.assertEQ((a >> 4L) << 8L, test3(a));
        Asserts.assertEQ((a >>> 4L) << 8L, test4(a));
        Asserts.assertEQ((a >> 8L) << 4L, test5(a));
        Asserts.assertEQ((a >>> 8L) << 4L, test6(a));
        Asserts.assertEQ(((a >> 4L) & 0xFFL) << 8L, test7(a));
        Asserts.assertEQ(((a >>> 4L) & 0xFFL) << 8L, test8(a));

        assertDoubleShiftResult(a);
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

    @DontCompile
    public void assertDoubleShiftResult(long a) {
        Asserts.assertEQ((a << 2L) << 3L, testDoubleShift1(a));
        Asserts.assertEQ(a << 5L, testDoubleShift1(a));

        Asserts.assertEQ(((a << 2L) << 3L) << 1L, testDoubleShift2(a));
        Asserts.assertEQ(a << 6L, testDoubleShift2(a));

        Asserts.assertEQ((a << 63L) << 1L, testDoubleShift3(a));
        Asserts.assertEQ(0L, testDoubleShift3(a));

        Asserts.assertEQ((a << 1L) << 63L, testDoubleShift4(a));
        Asserts.assertEQ(0L, testDoubleShift4(a));

        Asserts.assertEQ(((a << 62L) << 1L) << 1L, testDoubleShift5(a));
        Asserts.assertEQ(0L, testDoubleShift5(a));

        Asserts.assertEQ((a * 4L) << 3L, testDoubleShift6(a));
        Asserts.assertEQ(a << 5L, testDoubleShift6(a));

        Asserts.assertEQ((a << 3L) * 4L, testDoubleShift7(a));
        Asserts.assertEQ(a << 5L, testDoubleShift7(a));

        Asserts.assertEQ(a << 65L, testDoubleShift8(a));
        Asserts.assertEQ(a << 1L, testDoubleShift8(a));

        Asserts.assertEQ((a << 62L) << 3L, testDoubleShift9(a));
        Asserts.assertEQ(0L, testDoubleShift9(a));

        Asserts.assertEQ((a << CON0) << CON1, testRandom(a));
    }

    @Test
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks (x << 2) << 3 => x << 5
    public long testDoubleShift1(long x) {
        return (x << 2L) << 3L;
    }

    @Test
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks ((x << 2) << 3) << 1 => x << 6
    public long testDoubleShift2(long x) {
        return ((x << 2L) << 3L) << 1L;
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT})
    // Checks (x << 63) << 1 => 0
    public long testDoubleShift3(long x) {
        return (x << 63L) << 1L;
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT})
    // Checks (x << 1) << 63 => 0
    public long testDoubleShift4(long x) {
        return (x << 1L) << 63L;
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT})
    // Checks ((x << 62) << 1) << 1 => 0
    public long testDoubleShift5(long x) {
        return ((x << 62L) << 1L) << 1L;
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks (x * 4) << 3 => x << 5
    public long testDoubleShift6(long x) {
        return (x * 4L) << 3L;
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks (x << 3) * 4 => x << 5
    public long testDoubleShift7(long x) {
        return (x << 3L) * 4L;
    }

    @Test
    @IR(counts = {IRNode.LSHIFT, "1"})
    // Checks x << 65 => x << 1
    public long testDoubleShift8(long x) {
        return x << 65L;
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT})
    // Checks (x << 62) << 3 => 0
    public long testDoubleShift9(long x) {
        return (x << 62L) << 3L;
    }

    @Test
    @IR(counts = {IRNode.LSHIFT, "<= 1"})
    public long testRandom(long x) {
        return (x << CON0) << CON1;
    }
}
