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
 * @bug 8327381 8364970
 * @summary Refactor boolean node tautology transformations
 * @library /test/lib /
 * @run driver compiler.c2.gvn.TestBoolNodeGVN
 */
public class TestBoolNodeGVN {
    public static void main(String[] args) {
        TestFramework.run();
    }

    /**
     * Test CmpUNode::Value_cmpu_and_mask optimizations for cases 1a and 1b.
     * The test is only applicable to x64, aarch64 and riscv64 for having
     * <code>Integer.compareUnsigned</code> intrinsified.
     */

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1aDoNotOptimizeForEQxm(int x, int m) {
        // [BoolTest::eq] 1a) x & m =u m is unknown
        return Integer.compareUnsigned((x & m), m) == 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1aDoNotOptimizeForEQmx(int x, int m) {
        // [BoolTest::eq] 1a) m & x =u m is unknown
        return Integer.compareUnsigned((m & x), m) == 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1aDoNotOptimizeForNExm(int x, int m) {
        // [BoolTest::ne] 1a) x & m ≠u m is unknown
        return Integer.compareUnsigned((x & m), m) != 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1aDoNotOptimizeForNEmx(int x, int m) {
        // [BoolTest::ne] 1a) m & x ≠u m is unknown
        return Integer.compareUnsigned((m & x), m) != 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1aOptimizeAsTrueForLExm(int x, int m) {
        // [BoolTest::le] 1a) x & m ≤u m is always true
        return Integer.compareUnsigned((x & m), m) <= 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1aOptimizeAsTrueForLEmx(int x, int m) {
        // [BoolTest::le] 1a) m & x ≤u m is always true
        return Integer.compareUnsigned((m & x), m) <= 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1aDoNotOptimizeForGExm(int x, int m) {
        // [BoolTest::ge] 1a) x & m ≥u m is unknown
        return Integer.compareUnsigned((x & m), m) >= 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1aDoNotOptimizeForGEmx(int x, int m) {
        // [BoolTest::ge] 1a) m & x ≥u m is unknown
        return Integer.compareUnsigned((m & x), m) >= 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1aDoNotOptimizeForLTxm(int x, int m) {
        // [BoolTest::lt] 1a) x & m <u m is unknown
        return Integer.compareUnsigned((x & m), m) < 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1aDoNotOptimizeForLTmx(int x, int m) {
        // [BoolTest::lt] 1a) m & x <u m is unknown
        return Integer.compareUnsigned((m & x), m) < 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1aOptimizeAsFalseForGTxm(int x, int m) {
        // [BoolTest::gt] 1a) x & m >u m is always false
        return Integer.compareUnsigned((x & m), m) > 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1aOptimizeAsFalseForGTmx(int x, int m) {
        // [BoolTest::gt] 1a) m & x >u m is always false
        return Integer.compareUnsigned((m & x), m) > 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1bOptimizeAsFalseForEQxm(int x, int m) {
        // [BoolTest::eq] 1b) x & m =u m + 1 is always false (if m ≠ -1)
        m = Math.max(0, m);
        return Integer.compareUnsigned((x & m), m + 1) == 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1bOptimizeAsFalseForEQmx(int x, int m) {
        // [BoolTest::eq] 1b) m & x =u m + 1 is always false (if m ≠ -1)
        m = Math.max(0, m);
        return Integer.compareUnsigned((m & x), m + 1) == 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1bOptimizeAsTrueForNExm(int x, int m) {
        // [BoolTest::ne] 1b) x & m ≠u m + 1 is always true (if m ≠ -1)
        m = Math.max(0, m);
        return Integer.compareUnsigned((x & m), m + 1) != 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1bOptimizeAsTrueForNEmx(int x, int m) {
        // [BoolTest::ne] 1b) m & x ≠u m + 1 is always true (if m ≠ -1)
        m = Math.max(0, m);
        return Integer.compareUnsigned((m & x), m + 1) != 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1bOptimizeAsTrueForLExm(int x, int m) {
        // [BoolTest::le] 1b) x & m ≤u m + 1 is always true (if m ≠ -1)
        m = Math.max(0, m);
        return Integer.compareUnsigned((x & m), m + 1) <= 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1bOptimizeAsTrueForLEmx(int x, int m) {
        // [BoolTest::le] 1b) m & x ≤u m + 1 is always true (if m ≠ -1)
        m = Math.max(0, m);
        return Integer.compareUnsigned((m & x), m + 1) <= 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1bOptimizeAsFalseForGExm(int x, int m) {
        // [BoolTest::ge] 1b) x & m ≥u m + 1 is always false (if m ≠ -1)
        m = Math.max(0, m);
        return Integer.compareUnsigned((x & m), m + 1) >= 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1bOptimizeAsFalseForGEmx(int x, int m) {
        // [BoolTest::ge] 1b) m & x ≥u m + 1 is always false (if m ≠ -1)
        m = Math.max(0, m);
        return Integer.compareUnsigned((m & x), m + 1) >= 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1bOptimizeAsTrueForLTxm(int x, int m) {
        // [BoolTest::lt] 1b) x & m <u m + 1 is always true (if m ≠ -1)
        m = Math.max(0, m);
        return Integer.compareUnsigned((x & m), m + 1) < 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1bOptimizeAsTrueForLTmx(int x, int m) {
        // [BoolTest::lt] 1b) m & x <u m + 1 is always true (if m ≠ -1)
        m = Math.max(0, m);
        return Integer.compareUnsigned((m & x), m + 1) < 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1bOptimizeAsFalseForGTxm(int x, int m) {
        // [BoolTest::gt] 1b) x & m >u m + 1 is always false (if m ≠ -1)
        m = Math.max(0, m);
        return Integer.compareUnsigned((x & m), m + 1) > 0;
    }

    @Test
    @IR(failOn = IRNode.CMP_U,
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testCase1bOptimizeAsFalseForGTmx(int x, int m) {
        // [BoolTest::gt] 1b) m & x >u m + 1 is always false (if m ≠ -1)
        m = Math.max(0, m);
        return Integer.compareUnsigned((m & x), m + 1) > 0;
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
        return Integer.compareUnsigned((x & m), m - 1) <= 0;
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.CMP_U, "1"},
        phase = CompilePhase.AFTER_PARSING,
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"})
    public static boolean testShouldHaveCpmUCase2(int x, int m) {
        return Integer.compareUnsigned((m & x), m - 1) <= 0;
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

    @Run(test = { "testCase1aOptimizeAsTrueForLExm",
                  "testCase1aOptimizeAsTrueForLEmx",
                  "testCase1aOptimizeAsFalseForGTxm",
                  "testCase1aOptimizeAsFalseForGTmx",
                  "testCase1bOptimizeAsFalseForEQxm",
                  "testCase1bOptimizeAsFalseForEQmx",
                  "testCase1bOptimizeAsTrueForNExm",
                  "testCase1bOptimizeAsTrueForNEmx",
                  "testCase1bOptimizeAsTrueForLExm",
                  "testCase1bOptimizeAsTrueForLEmx",
                  "testCase1bOptimizeAsFalseForGExm",
                  "testCase1bOptimizeAsFalseForGEmx",
                  "testCase1bOptimizeAsTrueForLTxm",
                  "testCase1bOptimizeAsTrueForLTmx",
                  "testCase1bOptimizeAsFalseForGTxm",
                  "testCase1bOptimizeAsFalseForGTmx" })
    private static void testCorrectness() {
        int[] values = {
                -100, -42, -16, -8, -5, -1, 0, 1, 5, 8, 16, 42, 100,
                new Random().nextInt(), Integer.MAX_VALUE, Integer.MIN_VALUE
        };

        for (int x : values) {
            for (int m : values) {
                if (!testCase1aOptimizeAsTrueForLExm(x, m) ||
                    !testCase1aOptimizeAsTrueForLEmx(x, m) ||
                    testCase1aOptimizeAsFalseForGTxm(x, m) ||
                    testCase1aOptimizeAsFalseForGTmx(x, m) ||
                    testCase1bOptimizeAsFalseForEQxm(x, m) ||
                    testCase1bOptimizeAsFalseForEQmx(x, m) ||
                    !testCase1bOptimizeAsTrueForNExm(x, m) ||
                    !testCase1bOptimizeAsTrueForNEmx(x, m) ||
                    !testCase1bOptimizeAsTrueForLExm(x, m) ||
                    !testCase1bOptimizeAsTrueForLEmx(x, m) ||
                    testCase1bOptimizeAsFalseForGExm(x, m) ||
                    testCase1bOptimizeAsFalseForGEmx(x, m) ||
                    !testCase1bOptimizeAsTrueForLTxm(x, m) ||
                    !testCase1bOptimizeAsTrueForLTmx(x, m) ||
                    testCase1bOptimizeAsFalseForGTxm(x, m) ||
                    testCase1bOptimizeAsFalseForGTmx(x, m)) {
                    throw new RuntimeException("Bad result for x = " + x + " and m = " + m);
                }
            }
        }
    }
}
