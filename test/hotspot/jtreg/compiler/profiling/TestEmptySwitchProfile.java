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
 * @summary Test that switch profiles do not override C2 type information and empty profiles are ignored.
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
    private static int profileTableSwitch(int val) {
        // C1 profiles non-constant case 0 but constant-folds hot case 2, leaving its profile count at zero.
        return tableSwitch(val) + tableSwitch(2);
    }

    // Test that the nonempty profile does not override C2's constant case 2.
    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP)
    private static int testTableSwitch() {
        return tableSwitch(2);
    }

    @Run(test = "testTableSwitch")
    @Warmup(10_000)
    private static void runTestTableSwitch(RunInfo info) {
        if (info.isWarmUp()) {
            profileTableSwitch(0);
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
    private static int profileLookupSwitch(int val) {
        // C1 profiles non-constant case 0 but constant-folds hot case 50_000, leaving its profile count at zero.
        return lookupSwitch(val) + lookupSwitch(50_000);
    }

    // Test that the nonempty profile does not override C2's constant case 50_000.
    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP)
    private static int testLookupSwitch() {
        return lookupSwitch(50_000);
    }

    @Run(test = "testLookupSwitch")
    @Warmup(10_000)
    private static void runTestLookupSwitch(RunInfo info) {
        if (info.isWarmUp()) {
            profileLookupSwitch(0);
        } else {
            Asserts.assertEquals(testLookupSwitch(), 44);
        }
    }

    @ForceInline
    private static int emptyTableSwitch(int val) {
        return switch (val) {
            case 0 -> 42;
            case 1 -> 43;
            case 2 -> 44;
            default -> -42;
        };
    }

    @ForceInline
    private static int emptyLookupSwitch(int val) {
        return switch (val) {
            case 0 -> 42;
            case 1_000 -> 43;
            case 50_000 -> 44;
            default -> -42;
        };
    }

    @ForceCompile(CompLevel.C1_FULL_PROFILE)
    @DontCompile(Compiler.C2)
    private static int profileEmptySwitches() {
        // C1 constant-folds both switches, leaving both profile counts at zero.
        return emptyTableSwitch(2) + emptyLookupSwitch(50_000);
    }

    // Test that C2 treats entirely empty profiles as unavailable when both switch keys are non-constant.
    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP)
    private static int testNonConstantWithEmptyProfile(int tableVal, int lookupVal) {
        return emptyTableSwitch(tableVal) + emptyLookupSwitch(lookupVal);
    }

    @Run(test = "testNonConstantWithEmptyProfile")
    @Warmup(10_000)
    private static void runTestNonConstantWithEmptyProfile(RunInfo info) {
        if (info.isWarmUp()) {
            profileEmptySwitches();
        } else {
            Asserts.assertEquals(testNonConstantWithEmptyProfile(2, 50_000), 88);
        }
    }
}
