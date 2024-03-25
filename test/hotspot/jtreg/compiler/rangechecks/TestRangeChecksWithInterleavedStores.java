/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.rangechecks;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/**
 * @test
 * @bug 8320718
 * @summary Test that range check-like pairs of checks are not folded into
 *          single checks in the presence of interleaved stores. Doing so would
 *          risk illegally hoisting the stores above their corresponding checks.
 * @library /test/lib /
 * @run driver compiler.rangechecks.TestExplicitRangeChecksIR
 */

public class TestRangeChecksWithInterleavedStores {

    static int sum = 0;
    static boolean alwaysFalse = false;

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addScenarios(new Scenario(1),
                                   new Scenario(2, "-XX:+StressGCM"));
        testFramework.start();
    }

    @Test
    @IR(counts = {IRNode.UNSTABLE_IF_TRAP, "2"})
    @IR(failOn = IRNode.UNSTABLE_FUSED_IF_TRAP)
    static boolean testTwoChecksWithPinnedStore(int i) {
        if (i < 0) {
            return false;
        }
        // This store is pinned to the above if-check. If the above if-check was
        // fused with the one below, the store would be free to be scheduled on
        // method entry, incrementing 'sum' even if i < 0.
        sum++;
        if (i >= 10) {
            return false;
        }
        return true;
    }

    @Run(test = {"testTwoChecksWithPinnedStore"}, mode = RunMode.STANDALONE)
    public void runTestTwoChecksWithPinnedStore(RunInfo info) {
        sum = 0;
        for (int i = 0; i < 10_000; ++i) {
            testTwoChecksWithPinnedStore(5);
        }
        TestFramework.assertCompiledByC2(info.getTest());
        Asserts.assertEQ(10_000, sum);
        sum = 0;
        testTwoChecksWithPinnedStore(-1);
        TestFramework.assertDeoptimizedByC2(info.getTest());
        Asserts.assertEQ(0, sum);
    }

    @Test
    @IR(counts = {IRNode.UNSTABLE_IF_TRAP, "3"})
    @IR(failOn = IRNode.UNSTABLE_FUSED_IF_TRAP)
    static boolean testThreeChecksWithPinnedStore(int i) {
        if (i < 0) {
            return false;
        }
        // This store is pinned to the above if-check. If the above if-check was
        // fused with the one below (i >= 10), the store would be free to be
        // scheduled on method entry, incrementing 'sum' even if i < 0.
        sum++;
        if (alwaysFalse) {
            return false;
        }
        if (i >= 10) {
            return false;
        }
        return true;
    }

    @Run(test = {"testThreeChecksWithPinnedStore"}, mode = RunMode.STANDALONE)
    public void runTestThreeChecksWithPinnedStore(RunInfo info) {
        sum = 0;
        for (int i = 0; i < 10_000; ++i) {
            testThreeChecksWithPinnedStore(5);
        }
        TestFramework.assertCompiledByC2(info.getTest());
        Asserts.assertEQ(10_000, sum);
        sum = 0;
        testThreeChecksWithPinnedStore(-1);
        TestFramework.assertDeoptimizedByC2(info.getTest());
        Asserts.assertEQ(0, sum);
    }
}
