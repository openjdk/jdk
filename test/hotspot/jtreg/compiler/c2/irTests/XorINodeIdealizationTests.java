/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.MIN_VALUE;

/*
 * @test
 * @bug 8281453 8347645 8261008 8267332
 * @summary Test correctness of optimizations of xor
 * @library /test/lib /
 * @run driver compiler.c2.irTests.XorINodeIdealizationTests
 */
public class XorINodeIdealizationTests {
    private static final RestrictableGenerator<Integer> G = Generators.G.ints();
    private static final int CONST_1 = G.next();
    private static final int CONST_2 = G.next();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test1", "test2", "test3",
                 "test4", "test5", "test6",
                 "test7", "test8", "test9",
                 "test10", "test11", "test12",
                 "test13", "test14", "test15",
                 "test16", "test17",
                 "testConstXor", "testXorSelf"
    })
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();
        int c = RunInfo.getRandom().nextInt();
        int d = RunInfo.getRandom().nextInt();

        int min = MIN_VALUE;
        int max = MAX_VALUE;

        assertResult(0, 0, 0, 0);
        assertResult(a, b, c, d);
        assertResult(min, min, min, min);
        assertResult(max, max, max, max);
    }

    @DontCompile
    public void assertResult(int a, int b, int c, int d) {
        Asserts.assertEQ(b - a              , test1(a, b));
        Asserts.assertEQ(a - b              , test2(a, b));
        Asserts.assertEQ(b - a              , test3(a, b));
        Asserts.assertEQ(a - b              , test4(a, b));
        Asserts.assertEQ(b - a              , test5(a, b));
        Asserts.assertEQ(a + 1              , test6(a));
        Asserts.assertEQ(a                  , test7(a));
        Asserts.assertEQ((b + a) + 1        , test8(a, b));
        Asserts.assertEQ((-1 - a) - b       , test9(a, b));
        Asserts.assertEQ((b - a) + (-1)     , test10(a, b));
        Asserts.assertEQ((b - a) + (-1)     , test11(a, b));
        Asserts.assertEQ(~a                 , test12(a));
        Asserts.assertEQ(~a                 , test13(a));
        Asserts.assertEQ(~a                 , test14(a));
        Asserts.assertEQ(~a                 , test15(a));
        Asserts.assertEQ((~a + b) + (~a | c), test16(a, b, c));
        Asserts.assertEQ(-2023 - a          , test17(a));
        Asserts.assertEQ(CONST_1 ^ CONST_2  , testConstXor());
        Asserts.assertEQ(0                  , testXorSelf(a));
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (~x + y) + 1 => y - x
    public int test1(int x, int y) {
        return (~x + y) + 1;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (x + ~y) + 1 => x - y
    public int test2(int x, int y) {
        return (x + ~y) + 1;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks ~x + (y + 1) => y - x
    public int test3(int x, int y) {
        return ~x + (y + 1);
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks (x + 1) + ~y => x - y
    public int test4(int x, int y) {
        return (x + 1) + ~y;
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks ~x - ~y => y - x
    public int test5(int x, int y) {
        return ~x - ~y; // transformed to y - x
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.XOR})
    @IR(counts = {IRNode.ADD, "1"})
    // Checks 0 - ~x => x + 1
    public int test6(int x) {
        return 0 - ~x; // transformed to x + 1
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.XOR, IRNode.ADD})
    // Checks -1 - ~x => x
    public int test7(int x) {
        return -1 - ~x;
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.XOR})
    @IR(counts = {IRNode.ADD, "2"})
    // Checks y - ~x => (y + x) + 1
    public int test8(int x, int y) {
        return y - ~x;
    }

    @Test
    @IR(failOn = {IRNode.ADD, IRNode.XOR})
    @IR(counts = {IRNode.SUB, "2"})
    // Checks ~x - y => (-1 - x) -y
    public int test9(int x, int y) {
        return ~x - y;
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "1"})
    // Checks ~x + y => (y - x) + (-1)
    public int test10(int x, int y) {
        return ~x + y;
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.ADD, "1"})
    // Checks y + ~x => (y - x) + (-1)
    public int test11(int x, int y) {
        return y + ~x;
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.ADD})
    @IR(counts = {IRNode.XOR, "1"})
    // Checks ~(x + 0) => ~x, should not be transformed into -1-x
    public int test12(int x) {
        return ~(x + 0);
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.ADD})
    @IR(counts = {IRNode.XOR, "1"})
    // Checks ~(x - 0) => ~x, should not be transformed into -1-x
    public int test13(int x) {
        return ~(x - 0);
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.ADD})
    @IR(counts = {IRNode.XOR, "1"})
    // Checks ~x + 0 => ~x, should not be transformed into -1-x
    public int test14(int x) {
        return ~x + 0;
    }

    @Test
    @IR(failOn = {IRNode.SUB, IRNode.ADD})
    @IR(counts = {IRNode.XOR, "1"})
    // Checks ~x - 0 => ~x, should not be transformed into -1-x
    public int test15(int x) {
        return ~x - 0;
    }

    @Test
    @IR(counts = {IRNode.XOR, "1"})
    // Checks ~x + y should NOT be transformed into (y - x) + (-1)
    // because ~x has one non-arithmetic user.
    public int test16(int x, int y, int z) {
        int u = ~x + y;
        int v = ~x | z;
        return u + v;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks ~(x + c) => (-c-1) - x
    public int test17(int x) {
        return ~(x + 2022);
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.CON_I, "1"})
    // Checks (c1 ^ c2)  => c3 (constant folded)
    public int testConstXor() {
        return CONST_1 ^ CONST_2;
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.CON_I, "1"})
    // Checks (x ^ x)  => c (constant folded)
    public int testXorSelf(int x) {
        return x ^ x;
    }

    private static final boolean CONST_BOOL_1 = RunInfo.getRandom().nextBoolean();
    private static final boolean CONST_BOOL_2 = RunInfo.getRandom().nextBoolean();

    @Run(test={
            "testConstXorBool", "testXorSelfBool", "testXorIntAsBool"
    })
    public void runBooleanTests() {
        int c = G.next();
        int d = G.next();

        assertBooleanResult(true, c, d);
        assertBooleanResult(false, c, d);
    }

    @DontCompile
    public void assertBooleanResult(boolean b, int x, int y) {
        Asserts.assertEQ(CONST_BOOL_1 ^ CONST_BOOL_2, testConstXorBool());
        Asserts.assertEQ(false, testXorSelfBool(b));
        Asserts.assertEQ(true, testXorIntAsBool(x, y));
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.CON_I, "1"})
    // Checks (c1 ^ c2)  => c3 (constant folded)
    public boolean testConstXorBool() {
        return CONST_BOOL_1 ^ CONST_BOOL_2;
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.CON_I, "1"})
    // Checks (x ^ x)  => c (constant folded)
    public boolean testXorSelfBool(boolean x) {
        return x ^ x;
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.CON_I, "1"})
    // This test explicitly checks for constant folding over ints representing booleans.
    // Checks (x ^ y) => z in [0, 1] when x and y are known to be in [0, 1] (constant folded)
    public boolean testXorIntAsBool(int xi, int yi) {
        int xor = (xi & 1) ^ (yi & 1);
        return 0 <= xor && xor <= 1;
    }

    @Run(test = {
            "testFoldableXor", "testFoldableXorPow2", "testUnfoldableXorPow2",
            "testFoldableXorDifferingLength", "testXorMax",
            "testFoldableRange","testRandomLimits"
    })
    public void runRangeTests() {
        int a = G.next();
        int b = G.next();
        checkXor(a, b);

        for (a = 0; a < 32; a++) {
            for (b = a; b < 32; b++) {
                checkXor(a, b);
                checkXor(MAX_VALUE, MAX_VALUE - b);
            }
        }
    }

    @DontCompile
    public void checkXor(int a, int b) {
        Asserts.assertEQ(true, testFoldableXor(a, b));
        Asserts.assertEQ(((a & 0b1000) ^ (b & 0b1000)) < 0b1000, testUnfoldableXorPow2(a, b));
        Asserts.assertEQ(true, testFoldableXorPow2(a, b));
        Asserts.assertEQ(true, testFoldableXorDifferingLength(a, b));
        Asserts.assertEQ((a & MAX_VALUE) ^ (b & 0b11), testXorMax(a, b));
        Asserts.assertEQ(testRandomLimitsInterpreted(a, b), testRandomLimits(a, b));
        Asserts.assertEQ(true, testFoldableRange(a, b));
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.CON_I, "1"})
    public boolean testFoldableXorPow2(int x, int y) {
        return ((x & 0b1000) ^ (y & 0b1000)) < 0b10000;
    }

    @Test
    @IR(counts = {IRNode.XOR, "1"})
    public boolean testUnfoldableXorPow2(int x, int y) {
        return ((x & 0b1000) ^ (y & 0b1000)) < 0b1000;
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.CON_I, "1"})
    public boolean testFoldableXor(int x, int y) {
        var xor = (x & 0b111) ^ (y & 0b100);
        return xor < 0b1000;
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.CON_I, "1"})
    public boolean testFoldableXorDifferingLength(int x, int y) {
        var xor = (x & 0b111) ^ (y & 0b11);
        return xor < 0b1000;
    }

    @Test
    public int testXorMax(int x, int y) {
        return (x & MAX_VALUE) ^ (y & 0b11);
        // can't do the folding range check here since xor <= MAX_VALUE is
        // constant with or without the xor
    }

    private static final Range RANGE_1 = Range.generate(G.restricted(0, MAX_VALUE));
    private static final Range RANGE_2 = Range.generate(G.restricted(0, MAX_VALUE));
    private static final int UPPER_BOUND = Integer.max(0, Integer.highestOneBit(RANGE_1.hi() | RANGE_2.hi()) * 2 - 1);

    private static final int LIMIT_1 = G.next();
    private static final int LIMIT_2 = G.next();
    private static final int LIMIT_3 = G.next();
    private static final int LIMIT_4 = G.next();
    private static final int LIMIT_5 = G.next();
    private static final int LIMIT_6 = G.next();
    private static final int LIMIT_7 = G.next();
    private static final int LIMIT_8 = G.next();


    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.CON_I, "1"})
    public boolean testFoldableRange(int x, int y) {
        return (RANGE_1.clamp(x) ^ RANGE_2.clamp(y)) <= UPPER_BOUND;
    }

    @Test
    public int testRandomLimits(int x, int y) {
        x = RANGE_1.clamp(x);
        y = RANGE_2.clamp(y);

        int z = x ^ y;
        // This should now have a new range, possibly some [0, max]
        // Now let's test the range with some random if branches.
        int sum = 0;
        if (z > LIMIT_1) { sum += 1; }
        if (z > LIMIT_2) { sum += 2; }
        if (z > LIMIT_3) { sum += 4; }
        if (z > LIMIT_4) { sum += 8; }
        if (z > LIMIT_5) { sum += 16; }
        if (z > LIMIT_6) { sum += 32; }
        if (z > LIMIT_7) { sum += 64; }
        if (z > LIMIT_8) { sum += 128; }

        return sum;
    }

    @DontCompile
    private int testRandomLimitsInterpreted(int x, int y) {
        x = RANGE_1.clamp(x);
        y = RANGE_2.clamp(y);

        var z = x ^ y;
        // This should now have a new range, possibly some [0, max]
        // Now let's test the range with some random if branches.
        int sum = 0;
        if (z > LIMIT_1) { sum += 1; }
        if (z > LIMIT_2) { sum += 2; }
        if (z > LIMIT_3) { sum += 4; }
        if (z > LIMIT_4) { sum += 8; }
        if (z > LIMIT_5) { sum += 16; }
        if (z > LIMIT_6) { sum += 32; }
        if (z > LIMIT_7) { sum += 64; }
        if (z > LIMIT_8) { sum += 128; }

        return sum;
    }

    record Range(int lo, int hi) {
        Range {
            if (lo > hi) {
                throw new IllegalArgumentException("lo > hi");
            }
        }

        int clamp(int v) {
            return Math.min(hi, Math.max(v, lo));
        }

        static Range generate(Generator<Integer> g) {
            var a = g.next();
            var b = g.next();
            if (a > b) {
                var tmp = a;
                a = b;
                b = tmp;
            }
            return new Range(a, b);
        }
    }
}
