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
 * @bug 8267265 8315066
 * @summary Test that Ideal transformations of AddLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.AddLNodeIdealizationTests
 */
public class AddLNodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"additions", "xMinusX", "test1",
                 "test2", "test3", "test4",
                 "test5", "test6", "test7",
                 "test8", "test9", "test10",
                 "test11", "test12", "test13",
                 "test14", "test15", "test16",
                 "test17", "test18", "test19",
                 "test20", "test21", "test22",
                 "test23", "test24", "test25",
                 "test26", "test27", "test28",
                 "test29", "test30", "test31"})
    public void runMethod() {
        long a = RunInfo.getRandom().nextLong();
        long b = RunInfo.getRandom().nextLong();
        long c = RunInfo.getRandom().nextLong();
        long d = RunInfo.getRandom().nextLong();

        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;

        assertResult(0, 0, 0, 0);
        assertResult(a, b, c, d);
        assertResult(min, min, min, min);
        assertResult(max, max, max, max);
    }

    @DontCompile
    public void assertResult(long a, long b, long c, long d) {
        Asserts.assertEQ(((a+a) + (a+a))                , additions(a));
        Asserts.assertEQ(0L                             , xMinusX(a));
        Asserts.assertEQ(a + 1 + 2                      , test1(a));
        Asserts.assertEQ((a + 2021) + b                 , test2(a, b));
        Asserts.assertEQ(a + (b + 2021)                 , test3(a, b));
        Asserts.assertEQ((1 - a) + 2                    , test4(a));
        Asserts.assertEQ((a - b) + (c - d)              , test5(a, b, c, d));
        Asserts.assertEQ((a - b) + (b + c)              , test6(a, b, c));
        Asserts.assertEQ((a - b) + (c + b)              , test7(a, b, c));
        Asserts.assertEQ((a - b) + (c - a)              , test8(a, b, c));
        Asserts.assertEQ(a + (0 - b)                    , test9(a, b));
        Asserts.assertEQ((0 - b) + a                    , test10(a, b));
        Asserts.assertEQ((a - b) + b                    , test11(a, b));
        Asserts.assertEQ(b + (a - b)                    , test12(a, b));
        Asserts.assertEQ(a + 0                          , test13(a));
        Asserts.assertEQ(0 + a                          , test14(a));
        Asserts.assertEQ(a*b + a*c                      , test15(a, b, c));
        Asserts.assertEQ(a*b + b*c                      , test16(a, b, c));
        Asserts.assertEQ(a*c + b*c                      , test17(a, b, c));
        Asserts.assertEQ(a*b + c*a                      , test18(a, b, c));
        Asserts.assertEQ((a - b) + 123_456_789_123L     , test19(a, b));
        Asserts.assertEQ((a - b) + -123_456_788_877L    , test20(a, b));
        Asserts.assertEQ((a - b) + 123_456_789_123L     , test21(a, b));
        Asserts.assertEQ((a - b) + -123_456_788_877L    , test22(a, b));
        Asserts.assertEQ(Math.max(a, b) + Math.min(a, b), test23(a, b));
        Asserts.assertEQ(Math.min(a, b) + Math.max(a, b), test24(a, b));
        Asserts.assertEQ(1L                             , test25(a, b));
        Asserts.assertEQ((a >>> 1) + (b >>> 1) >= 0 ? 1L : 0L, test26(a, b));
        Asserts.assertEQ(1L                             , test27(a, b));
        Asserts.assertEQ(1L                             , test28(a, b));
        Asserts.assertEQ(Long.compareUnsigned((a | (Long.MIN_VALUE >>> 2)) + (b | (Long.MIN_VALUE >>> 2)), Long.MIN_VALUE >>> 1) >= 0 ? 1L : 0L, test29(a, b));
        Asserts.assertEQ(1L                             , test30(a, b));
        Asserts.assertEQ(0L                             , test31(a, b));
    }

    @Test
    @IR(counts = {IRNode.ADD, "2"})
    // Checks (x + x) + (x + x) => a=(x + x); r=a+a
    public long additions(long x) {
        return (x + x) + (x + x);
    }

    @Test
    @IR(failOn = {IRNode.ADD, IRNode.SUB})
    // Checks (x - x) => 0 and 0 - 0 => 0
    public long xMinusX(long x) {
        return (x - x) + (x - x);
    }

    @Test
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (x + c1) + c2 => x + c3 where c3 = c1 + c2
    public long test1(long x) {
        return (x + 1) + 2;
    }

    @Test
    @IR(counts = {IRNode.ADD, "2"})
    // Checks (x + c1) + y => (x + y) + c1
    public long test2(long x, long y) {
        return (x + 2021) + y;
    }

    @Test
    @IR(counts = {IRNode.ADD, "2"})
    // Checks x + (y + c1) => (x + y) + c1
    public long test3(long x, long y) {
        return x + (y + 2021);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (c1 - x) + c2 => c3 - x where c3 = c1 + c2
    public long test4(long x) {
        return (1 - x) + 2;
    }

    @Test
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "2",
                 })
    // Checks (a - b) + (c - d) => (a + c) - (b + d)
    public long test5(long a, long b, long c, long d) {
        return (a - b) + (c - d);
    }

    @Test
    @IR(failOn = {IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (a - b) + (b + c) => (a + c)
    public long test6(long a, long b, long c) {
        return (a - b) + (b + c);
    }

    @Test
    @IR(failOn = {IRNode.SUB})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks (a - b) + (c + b) => (a + c)
    public long test7(long a, long b, long c) {
        return (a - b) + (c + b);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (a - b) + (c - a) => (c - b)
    public long test8(long a, long b, long c) {
        return (a - b) + (c - a);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks x + (0 - y) => (x - y)
    public long test9(long x, long y) {
        return x + (0 - y);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (0 - y) + x => (x - y)
    public long test10(long x, long y) {
        return (0 - y) + x;
    }

    @Test
    @IR(failOn = {IRNode.ADD, IRNode.SUB})
    // Checks (x - y) + y => x
    public long test11(long x, long y) {
        return (x - y) + y;
    }

    @Test
    @IR(failOn = {IRNode.ADD, IRNode.SUB})
    // Checks y + (x - y) => x
    public long test12(long x, long y) {
        return y + (x - y);
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    // Checks x + 0 => x
    public long test13(long x) {
        return x + 0;
    }

    @Test
    @IR(failOn = {IRNode.ADD})
    // Checks 0 + x => x
    public long test14(long x) {
        return 0 + x;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.ADD, "1"
                 })
    // Checks "a*b + a*c => a*(b+c)
    public long test15(long a, long b, long c) {
        return a*b + a*c;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.ADD, "1"
                 })
    // Checks a*b + b*c => b*(a+c)
    public long test16(long a, long b, long c) {
        return a*b + b*c;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.ADD, "1"
                 })
    // Checks a*c + b*c => (a+b)*c
    public long test17(long a, long b, long c) {
        return a*c + b*c;
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.ADD, "1"
                 })
    // Checks a*b + c*a => a*(b+c)
    public long test18(long a, long b, long c) {
        return a*b + c*a;
    }

    @Test
    @IR(counts = {IRNode.SUB_L, "1",
                  IRNode.ADD_L, "1",
                  IRNode.CON_L, "1"})
    // Checks x + (con - y) => (x - y) + con
    // where con > 0
    public long test19(long x, long y) {
        return x + (123_456_789_000L - y) + 123;
        // transformed to (x - y) + 123_456_789_123L;
    }

    @Test
    @IR(counts = {IRNode.SUB_L, "1",
                  IRNode.ADD_L, "1",
                  IRNode.CON_L, "1"})
    // Checks x + (con - y) => (x - y) + con
    // where con < 0
    public long test20(long x, long y) {
        return x + (-123_456_789_000L - y) + 123;
        // transformed to (x - y) + -123_456_788_877L;
    }

    @Test
    @IR(counts = {IRNode.SUB_L, "1",
                  IRNode.ADD_L, "1",
                  IRNode.CON_L, "1"})
    // Checks (con - y) + x => (x - y) + con
    // where con > 0
    public long test21(long x, long y) {
        return x + (123_456_789_000L - y) + 123;
        // transformed to (x - y) + 123_456_789_123L;
    }

    @Test
    @IR(counts = {IRNode.SUB_L, "1",
                  IRNode.ADD_L, "1",
                  IRNode.CON_L, "1"})
    // Checks (con - y) + x => (x - y) + con
    // where con < 0
    public long test22(long x, long y) {
        return x + (-123_456_789_000L - y) + 123;
        // transformed to (x - y) + -123_456_788_877L;
    }

    @Test
    @IR(failOn = { IRNode.MAX, IRNode.MIN })
    @IR(counts = { IRNode.ADD, "1" })
    // Checks Math.max(a, b) + Math.min(a, b) => a + b
    public long test23(long a, long b) {
        return Math.max(a, b) + Math.min(a, b);
    }

    @Test
    @IR(failOn = { IRNode.MAX, IRNode.MIN })
    @IR(counts = { IRNode.ADD, "1" })
    // Checks Math.min(a, b) + Math.max(a, b) => a + b
    public long test24(long a, long b) {
        return Math.min(a, b) + Math.max(a, b);
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.RSHIFT_L})
    // Signed bounds
    public long test25(long a, long b) {
        long sum = (a >> 2) + (b >> 2);
        return sum >= Long.MIN_VALUE >> 1 && sum < Long.MAX_VALUE >> 1 ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.ADD_L, "1", IRNode.URSHIFT_L, "2"})
    // Signed bounds cannot be inferred if overflow
    public long test26(long a, long b) {
        long sum = (a >>> 1) + (b >>> 1);
        return sum >= 0 ? 1 : 0;
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.URSHIFT_L})
    // Signed bounds, both lo and hi overflow
    public long test27(long a, long b) {
        long sum = ((a | Long.MIN_VALUE) >>> 1) + ((b | Long.MIN_VALUE) >>> 1);
        return sum < -1 ? 1 : 0;
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.URSHIFT_L})
    // Unsigned bounds
    public long test28(long a, long b) {
        long sum = (a >>> 2) + (b >>> 2) + 1000;
        return (Long.compareUnsigned(sum, 1000) >= 0 &&
                Long.compareUnsigned(sum, Long.MIN_VALUE + 1000) < 0) ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.ADD_L, "1"})
    // Unsigned bounds cannot be inferred if overflow
    public long test29(long a, long b) {
        long sum = (a | (Long.MIN_VALUE >>> 2)) + (b | (Long.MIN_VALUE >>> 2));
        return Long.compareUnsigned(sum, Long.MIN_VALUE >>> 1) >= 0 ? 1 : 0;
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.URSHIFT_L})
    // Unsigned bounds, both ulo and uhi overflow
    public long test30(long a, long b) {
        long sum = (a | Long.MIN_VALUE) + ((b >>> 2) | Long.MIN_VALUE);
        return Long.compareUnsigned(sum, Long.MIN_VALUE >> 1) < 0 ? 1 : 0;
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.LSHIFT_L})
    // Bits
    public long test31(long a, long b) {
        return ((a << 5) + (b << 5)) & 31;
    }
}
