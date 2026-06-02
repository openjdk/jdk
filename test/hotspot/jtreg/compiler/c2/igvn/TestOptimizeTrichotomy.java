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
 * @run main ${test.main.class}
 */

/*
 * @test id=Xcomp
 * @bug 8385157
 * @key randomness
 * @library /test/lib /
 * @run main ${test.main.class} -Xcomp -XX:-TieredCompilation -XX:CompileCommand=compileonly,${test.main.class}::test*
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
        TestFramework framework = new TestFramework();
        framework.addFlags(args);
        framework.start();
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
        // reproduces before started intrinsifying Ingeger.compareUnsigned
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
        int a = RANDOM.nextInt();
        int b = RANDOM.nextInt();
        boolean v0 = test2(a, b);
        boolean v1 = reference2(a, b);
        if (v0 != v1) {
            throw new RuntimeException("wrong value: " + v0 + " vs " + v1);
        }
    }

    // ------------------- IR tests to check that optimization was performed ------------------------

    // The following tests with constant bounds are expected to fold to a single CmpU.

    //@Test
    //@IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    //@IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"})
    //@Arguments(values = {Argument.NUMBER_42})
    //public static void test_lohi_ltle(int i) {
    //    if (i < -100_000 || i > 100_000) {
    //        throw new RuntimeException();
    //    }
    //}
}
