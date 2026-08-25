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

/**
 * @test
 * @bug 8385134
 * @summary Test that C2 ignores an empty switch profile.
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /
 * @run driver compiler.profiling.TestEmptySwitchProfile
 */

package compiler.profiling;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.Compiler;
import jdk.test.lib.Asserts;

public class TestEmptySwitchProfile {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @ForceInline
    private static int tableSwitch(int val) {
        return switch (val) {
            case 0 -> 42;
            case 1 -> 43;
            case 2 -> 44;
            default -> -42;
        };
    }

    @ForceCompile(CompLevel.C1_FULL_PROFILE)
    @DontCompile(Compiler.C2)
    private static int profileTableSwitch() {
        // C1 inlines and constant-folds the tableswitch, omitting profiling
        return tableSwitch(2);
    }

    // Test that C2 does not emit an uncommon trap for (hot) case 2
    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP)
    private static int testTableSwitch() {
        return tableSwitch(2);
    }

    @Run(test = "testTableSwitch")
    @Warmup(10_000)
    private static void runTestTableSwitch(RunInfo info) {
        if (info.isWarmUp()) {
            profileTableSwitch();
        } else {
            Asserts.assertEquals(testTableSwitch(), 44);
        }
    }

    // Same as above but with lookupswitch instead
    @ForceInline
    private static int lookupSwitch(int val) {
        return switch (val) {
            case 0 -> 42;
            case 1_000 -> 43;
            case 50_000 -> 44;
            default -> -42;
        };
    }

    @ForceCompile(CompLevel.C1_FULL_PROFILE)
    @DontCompile(Compiler.C2)
    private static int profileLookupSwitch() {
        // C1 inlines and constant-folds the lookupswitch, omitting profiling
        return lookupSwitch(50_000);
    }

    // Test that C2 does not emit an uncommon trap for (hot) case 50_000
    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP)
    private static int testLookupSwitch() {
        return lookupSwitch(50_000);
    }

    @Run(test = "testLookupSwitch")
    @Warmup(10_000)
    private static void runTestLookupSwitch(RunInfo info) {
        if (info.isWarmUp()) {
            profileLookupSwitch();
        } else {
            Asserts.assertEquals(testLookupSwitch(), 44);
        }
    }
}

