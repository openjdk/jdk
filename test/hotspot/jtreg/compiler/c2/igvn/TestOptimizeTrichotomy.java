/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test id=vanilla
 * @bug 8385157
 * @key randomness
 * @summary Test RegionNode::optimize_trichotomy, including cases that
 *          are expected to optimize, and others that should not, because
 *          it would lead to wrong results.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

/*
 * @test id=Xcomp
 * @bug 8385157
 * @key randomness
 * @library /test/lib /
 * @run driver ${test.main.class} -Xcomp -XX:-TieredCompilation -XX:CompileCommand=compileonly,${test.main.class}::test*
 */

package compiler.c2.igvn;

import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.ir_framework.*;

/**
 * This test here covers some basic cases of RegionNode::optimize_trichotomy.
 * Note: TestFoldComparesFuzzer.java was originally designed to fuzz a related
 *       optimization (IfNode::fold_compares), which also deals with folding
 *       multiple comparisons into one (or none). That fuzzer test also covers
 *       the trichotomy optimization, and is the reason JDK-8385157 was reported.
 */
public class TestOptimizeTrichotomy {

    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.runWithFlags(args);
    }

    private record IntPair(int x, int y) {
        // Balanced trichotomy:
        // we statistically ensure all paths of the signed trichotomy are taken,
        // so we don't get any unstable-if traps.
        static IntPair balancedTrichotomy() {
            int a = RANDOM.nextInt();
            int b = RANDOM.nextInt();
            return switch (RANDOM.nextInt(3)) {
                case 0 -> new IntPair(a, a);
                default -> new IntPair(a, b);
            };
        }
    }

    private record LongPair(long x, long y) {
        // Balanced trichotomy:
        // we statistically ensure all paths of the signed trichotomy are taken,
        // so we don't get any unstable-if traps.
        static LongPair balancedTrichotomy() {
            long a = RANDOM.nextLong();
            long b = RANDOM.nextLong();
            return switch (RANDOM.nextInt(3)) {
                case 0 -> new LongPair(a, a);
                default -> new LongPair(a, b);
            };
        }
    }

    // ------------------------- Failing cases for JDK-8385157 ------------------------------

    // test0: Test2.java as reported in JDK-8385157, reported wrong result with -Xcomp
    public static boolean test0_flag = true;
    public static final boolean test0_gold = test0(42);

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    public static boolean test0(int n) {
        int a = test0_flag ? -3 : Integer.MAX_VALUE;
        // Note: the following CmpI is optimized into a CmpU
        //   n + Integer.MIN_VALUE > a + Integer.MIN_VALUE
        // ->
        //   n >u a
        // And so this ends up in the same pattern as test1, but
        // reproduces before started intrinsifying Integer.compareUnsigned
        if (a > n || n + Integer.MIN_VALUE > a + Integer.MIN_VALUE) {
            return true;
        }
        return false;
    }

    @Check(test = "test0")
    public void checkTest0(boolean val) {
        if (val != test0_gold) {
            throw new RuntimeException("wrong value: " + val + " vs " + test0_gold);
        }
    }

    // test1: Test4.java as reported in JDK-8385157, reported wrong result with -Xcomp
    public static boolean test1_flag = true;
    public static final boolean test1_gold = test1(42);

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    public static boolean test1(int n) {
        int a = test1_flag ? -3 : Integer.MAX_VALUE;
        // RegionNode::optimize_trichotomy
        // thinks that the comparisons below operate on the same inputs compare(n, a),
        // but misses to check the Opcode for CmpI vs CmpU.
        if (a > n || Integer.compareUnsigned(n, a) > 0) {
            return true;
        }
        return false;
    }

    @Check(test = "test1")
    public void checkTest1(boolean val) {
        if (val != test1_gold) {
            throw new RuntimeException("wrong value: " + val + " vs " + test1_gold);
        }
    }

    // Another case found by the TestFoldComparesFuzzer.java
    @Test
    static boolean test2(int a, int b) {
        a = Math.min(1886969202, Math.max(-2002597787, a));
        b = Math.min(130, Math.max(-33554430, b));
        if (!(b  >=  a) || (Integer.compareUnsigned(a, b) <= 0)) {
            return true;
        }
        return false;
    }

    @DontCompile
    static boolean reference2(int a, int b) {
        a = Math.min(1886969202, Math.max(-2002597787, a));
        b = Math.min(130, Math.max(-33554430, b));
        if (!(b  >=  a) || (Integer.compareUnsigned(a, b) <= 0)) {
            return true;
        }
        return false;
    }

    @Run(test = {"test2"})
    static void runTest2() {
        var p = IntPair.balancedTrichotomy();
        boolean v0 = test2(p.x, p.y);
        boolean v1 = reference2(p.x, p.y);
        if (v0 != v1) {
            throw new RuntimeException("wrong value: " + v0 + " vs " + v1);
        }
    }

    // ------------------- IR tests to check that optimization was performed ------------------------

    @Test
    @IR(counts = {IRNode.CMP_I, "= 1", IRNode.CMP_U, "= 0", IRNode.CMOVE_I, "= 0", IRNode.IF, "= 2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 1", IRNode.CMP_U, "= 0", IRNode.CMOVE_I, "= 1", IRNode.IF, "= 0"})
    // Should be able to optimize to "a <= b", already during parsing.
    static boolean testIR0(int a, int b) {
        if (a < b || a == b) {
            return true;
        }
        return false;
    }

    @DontCompile
    static boolean referenceIR0(int a, int b) {
        if (a < b || a == b) {
            return true;
        }
        return false;
    }

    @Run(test = {"testIR0"})
    static void runTestIR0() {
        var p = IntPair.balancedTrichotomy();
        boolean v0 = testIR0(p.x, p.y);
        boolean v1 = referenceIR0(p.x, p.y);
        if (v0 != v1) {
            throw new RuntimeException("wrong value: " + v0 + " vs " + v1);
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 1", IRNode.CMP_U, "= 0", IRNode.CMOVE_I, "= 0", IRNode.IF, "= 2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 1", IRNode.CMP_U, "= 0", IRNode.CMOVE_I, "= 1", IRNode.IF, "= 0"})
    // Should be able to optimize to "a >= b", already during parsing.
    static boolean testIR1(int a, int b) {
        if (a > b || a == b) {
            return true;
        }
        return false;
    }

    @DontCompile
    static boolean referenceIR1(int a, int b) {
        if (a > b || a == b) {
            return true;
        }
        return false;
    }

    @Run(test = {"testIR1"})
    static void runTestIR1() {
        var p = IntPair.balancedTrichotomy();
        boolean v0 = testIR1(p.x, p.y);
        boolean v1 = referenceIR1(p.x, p.y);
        if (v0 != v1) {
            throw new RuntimeException("wrong value: " + v0 + " vs " + v1);
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1", IRNode.CMOVE_I, "= 0", IRNode.IF, "= 2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1", IRNode.CMOVE_I, "= 1", IRNode.IF, "= 0"})
    // Should be able to optimize to "a <=u b", already during parsing.
    static boolean testIR2(int a, int b) {
        if ((Integer.compareUnsigned(a, b) < 0) || (Integer.compareUnsigned(a, b) == 0)) {
            return true;
        }
        return false;
    }

    @DontCompile
    static boolean referenceIR2(int a, int b) {
        if ((Integer.compareUnsigned(a, b) < 0) || (Integer.compareUnsigned(a, b) == 0)) {
            return true;
        }
        return false;
    }

    @Run(test = {"testIR2"})
    static void runTestIR2() {
        var p = IntPair.balancedTrichotomy();
        boolean v0 = testIR2(p.x, p.y);
        boolean v1 = referenceIR2(p.x, p.y);
        if (v0 != v1) {
            throw new RuntimeException("wrong value: " + v0 + " vs " + v1);
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1", IRNode.CMOVE_I, "= 0", IRNode.IF, "= 2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1", IRNode.CMOVE_I, "= 1", IRNode.IF, "= 0"})
    // Should be able to optimize to "a >=u b", already during parsing.
    static boolean testIR3(int a, int b) {
        if ((Integer.compareUnsigned(a, b) > 0) || (Integer.compareUnsigned(a, b) == 0)) {
            return true;
        }
        return false;
    }

    @DontCompile
    static boolean referenceIR3(int a, int b) {
        if ((Integer.compareUnsigned(a, b) > 0) || (Integer.compareUnsigned(a, b) == 0)) {
            return true;
        }
        return false;
    }

    @Run(test = {"testIR3"})
    static void runTestIR3() {
        var p = IntPair.balancedTrichotomy();
        boolean v0 = testIR3(p.x, p.y);
        boolean v1 = referenceIR3(p.x, p.y);
        if (v0 != v1) {
            throw new RuntimeException("wrong value: " + v0 + " vs " + v1);
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_L, "= 1", IRNode.CMP_UL, "= 0", IRNode.CMOVE_I, "= 0", IRNode.IF, "= 2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 1", IRNode.CMP_UL, "= 0", IRNode.CMOVE_I, "= 1", IRNode.IF, "= 0"})
    // Should be able to optimize to "a <= b", already during parsing.
    static boolean testIR4(long a, long b) {
        if (a < b || a == b) {
            return true;
        }
        return false;
    }

    @DontCompile
    static boolean referenceIR4(long a, long b) {
        if (a < b || a == b) {
            return true;
        }
        return false;
    }

    @Run(test = {"testIR4"})
    static void runTestIR4() {
        var p = LongPair.balancedTrichotomy();
        boolean v0 = testIR4(p.x, p.y);
        boolean v1 = referenceIR4(p.x, p.y);
        if (v0 != v1) {
            throw new RuntimeException("wrong value: " + v0 + " vs " + v1);
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_L, "= 1", IRNode.CMP_UL, "= 0", IRNode.CMOVE_I, "= 0", IRNode.IF, "= 2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 1", IRNode.CMP_UL, "= 0", IRNode.CMOVE_I, "= 1", IRNode.IF, "= 0"})
    // Should be able to optimize to "a >= b", already during parsing.
    static boolean testIR5(long a, long b) {
        if (a > b || a == b) {
            return true;
        }
        return false;
    }

    @DontCompile
    static boolean referenceIR5(long a, long b) {
        if (a > b || a == b) {
            return true;
        }
        return false;
    }

    @Run(test = {"testIR5"})
    static void runTestIR5() {
        var p = LongPair.balancedTrichotomy();
        boolean v0 = testIR5(p.x, p.y);
        boolean v1 = referenceIR5(p.x, p.y);
        if (v0 != v1) {
            throw new RuntimeException("wrong value: " + v0 + " vs " + v1);
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1", IRNode.CMOVE_I, "= 0", IRNode.IF, "= 2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1", IRNode.CMOVE_I, "= 1", IRNode.IF, "= 0"})
    // Should be able to optimize to "a <=u b", already during parsing.
    static boolean testIR6(long a, long b) {
        if ((Long.compareUnsigned(a, b) < 0) || (Long.compareUnsigned(a, b) == 0)) {
            return true;
        }
        return false;
    }

    @DontCompile
    static boolean referenceIR6(long a, long b) {
        if ((Long.compareUnsigned(a, b) < 0) || (Long.compareUnsigned(a, b) == 0)) {
            return true;
        }
        return false;
    }

    @Run(test = {"testIR6"})
    static void runTestIR6() {
        var p = LongPair.balancedTrichotomy();
        boolean v0 = testIR6(p.x, p.y);
        boolean v1 = referenceIR6(p.x, p.y);
        if (v0 != v1) {
            throw new RuntimeException("wrong value: " + v0 + " vs " + v1);
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1", IRNode.CMOVE_I, "= 0", IRNode.IF, "= 2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_L, "= 0", IRNode.CMP_UL, "= 1", IRNode.CMOVE_I, "= 1", IRNode.IF, "= 0"})
    // Should be able to optimize to "a >=u b", already during parsing.
    static boolean testIR7(long a, long b) {
        if ((Long.compareUnsigned(a, b) > 0) || (Long.compareUnsigned(a, b) == 0)) {
            return true;
        }
        return false;
    }

    @DontCompile
    static boolean referenceIR7(long a, long b) {
        if ((Long.compareUnsigned(a, b) > 0) || (Long.compareUnsigned(a, b) == 0)) {
            return true;
        }
        return false;
    }

    @Run(test = {"testIR7"})
    static void runTestIR7() {
        var p = LongPair.balancedTrichotomy();
        boolean v0 = testIR7(p.x, p.y);
        boolean v1 = referenceIR7(p.x, p.y);
        if (v0 != v1) {
            throw new RuntimeException("wrong value: " + v0 + " vs " + v1);
        }
    }

    // ------------------- And for fun, an IR test where we don't optimize ------------------------

    @Test
    @IR(counts = {IRNode.CMP_I, "= 1", IRNode.CMP_U, "= 1", IRNode.CMOVE_I, "= 0", IRNode.IF, "= 2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 1", IRNode.CMP_U, "= 1", IRNode.CMOVE_I, "= 0", IRNode.IF, "= 2"})
    static boolean testIR8(int a, int b) {
        if (a == b || (Integer.compareUnsigned(a, b) > 0)) {
            return true;
        }
        return false;
    }

    @DontCompile
    static boolean referenceIR8(int a, int b) {
        if (a == b || (Integer.compareUnsigned(a, b) > 0)) {
            return true;
        }
        return false;
    }

    @Run(test = {"testIR8"})
    static void runTestIR8() {
        var p = IntPair.balancedTrichotomy();
        boolean v0 = testIR8(p.x, p.y);
        boolean v1 = referenceIR8(p.x, p.y);
        if (v0 != v1) {
            throw new RuntimeException("wrong value: " + v0 + " vs " + v1);
        }
    }
}
