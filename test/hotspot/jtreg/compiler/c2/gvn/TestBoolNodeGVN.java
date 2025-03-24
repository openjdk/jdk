/*
 * Copyright (c) 2024 Red Hat and/or its affiliates. All rights reserved.
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

package compiler.c2.gvn;

import compiler.lib.ir_framework.*;

import java.util.Random;

/**
 * @test
 * @bug 8327381
 * @summary Refactor boolean node tautology transformations
 * @library /test/lib /
 * @run driver compiler.c2.gvn.TestBoolNodeGVN
 */
public class TestBoolNodeGVN {
    public static void main(String[] args) {
        TestFramework.run();
        testCorrectness();
    }

    /**
     * Test changing ((x & m) u<= m) or ((m & x) u<= m) to always true, same with ((x & m) u< m+1) and ((m & x) u< m+1)
     * The test is only applicable to x64, aarch64 and riscv64 for having <code>Integer.compareUnsigned</code>
     * intrinsified.
     */
    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testShouldReplaceCpmUCase1(int x, int m) {
        return !(Integer.compareUnsigned((x & m), m) > 0); // assert in inversions to generates the pattern looking for
    }
    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testShouldReplaceCpmUCase2(int x, int m) {
        return !(Integer.compareUnsigned((m & x), m) > 0);
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.RANDOM_EACH})
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testShouldReplaceCpmUCase3(int x, int m) {
        m = Math.max(0, m);
        return Integer.compareUnsigned((x & m), m + 1) < 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.RANDOM_EACH})
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testShouldReplaceCpmUCase4(int x, int m) {
        m = Math.max(0, m);
        return Integer.compareUnsigned((m & x), m + 1) < 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"}, // m could be -1 and thus optimization cannot be applied
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testShouldNotReplaceCpmUCase1(int x, int m) {
        return Integer.compareUnsigned((x & m), m + 1) < 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"}, // m could be -1 and thus optimization cannot be applied
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testShouldNotReplaceCpmUCase2(int x, int m) {
        return Integer.compareUnsigned((m & x), m + 1) < 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testShouldHaveCpmUCase1(int x, int m) {
        return !(Integer.compareUnsigned((x & m), m - 1) > 0);
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testShouldHaveCpmUCase2(int x, int m) {
        return !(Integer.compareUnsigned((m & x), m - 1) > 0);
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testShouldHaveCpmUCase3(int x, int m) {
        return Integer.compareUnsigned((x & m), m + 2) < 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testShouldHaveCpmUCase4(int x, int m) {
        return Integer.compareUnsigned((m & x), m + 2) < 0;
    }

    private static void testCorrectness() {
        int[] values = {
                -100, -42, -16, -8, -5, -1, 0, 1, 5, 8, 16, 42, 100,
                new Random().nextInt(), Integer.MAX_VALUE, Integer.MIN_VALUE
        };

        for (int x : values) {
            for (int m : values) {
                if (!testShouldReplaceCpmUCase1(x, m) ||
                    !testShouldReplaceCpmUCase2(x, m) ||
                    !testShouldReplaceCpmUCase3(x, m) ||
                    !testShouldReplaceCpmUCase4(x, m)) {
                    throw new RuntimeException("Bad result for x = " + x + " and m = " + m + ", expected always true");
                }
            }
        }
    }
}
