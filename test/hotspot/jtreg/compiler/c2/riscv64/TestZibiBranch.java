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
 * @summary Verify C2 selects beqi/bnei for the Zibi branch-with-immediate
 *          extension. Runs only on hardware that implements Zibi (see note
 *          below) and force-enables -XX:+UseZibi.
 * @library /test/lib /
 * @requires os.arch == "riscv64" & vm.cpu.features ~= ".*zibi.*"
 * @run main/othervm compiler.c2.riscv64.TestZibiBranch
 */

package compiler.c2.riscv64;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

// This test follows the conservative "run only on real hardware support"
// policy: it verifies that C2 actually selects beqi/bnei for the Zibi
// branch-with-immediate extension, and only runs where Zibi is present.
//
// Zibi source: there is currently no Linux hwprobe/HWCAP bit for Zibi, so the
// only mechanism that reports it in vm.cpu.features is the vendor detection
// path (PicoHeart Jupiter A2 enables ext_Zibi, whose feature string "zibi" is
// then appended to vm.cpu.features). The @requires gate above therefore only
// selects this test on hardware where Zibi was reliably detected. Each @IR rule
// is additionally gated with applyIfCPUFeature={"zibi","true"} so the codegen
// checks fire only when the VM confirms the feature is active.
//
// Each test method is shaped as a one-armed `if` with a store side effect so
// that C2 keeps a real two-way branch (a boolean-returning comparison would be
// folded into a branchless set/CMove sequence and would never select beqi/bnei).
//
// Zibi has no dedicated IR node; the CmpI/CmpL near/far branch instructs are the
// only place the extension shows up. We therefore match the instruct `format`
// marker (`#@cmpI_imm_branch` / `#@cmpL_imm_branch`, which also matches the
// `far_` variants as a substring) against the PrintOptoAssembly output. Without
// this the test would silently pass even if codegen fell back to a materialize-
// plus-reg-reg branch.
public class TestZibiBranch {

    static int  intCounter;
    static long longCounter;

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-TieredCompilation",
                                   "-XX:CompileThresholdScaling=0.3",
                                   "-XX:+UnlockExperimentalVMOptions",
                                   "-XX:+UseZibi");
    }

    // ---- int: constants Zibi CAN encode -> must select the Zibi branch ----

    // cimm == 0 encodes the comparison value -1.
    @Test
    @IR(counts = {"cmpI_imm_branch", ">= 1"},
        applyIfCPUFeature = {"zibi", "true"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    static void eqI_m1(int x) {
        if (x == -1) {
            intCounter++;
        }
    }

    @Test
    @IR(counts = {"cmpI_imm_branch", ">= 1"},
        applyIfCPUFeature = {"zibi", "true"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    static void neI_1(int x) {
        if (x != 1) {
            intCounter++;
        }
    }

    @Test
    @IR(counts = {"cmpI_imm_branch", ">= 1"},
        applyIfCPUFeature = {"zibi", "true"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    static void eqI_31(int x) {
        if (x == 31) {
            intCounter++;
        }
    }

    // ---- int: constants Zibi CANNOT encode -> must fall back, no Zibi branch ----

    @Test
    @IR(failOn = {"cmpI_imm_branch"},
        applyIfCPUFeature = {"zibi", "true"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    static void eqI_32(int x) {
        if (x == 32) {
            intCounter++;
        }
    }

    @Test
    @IR(failOn = {"cmpI_imm_branch"},
        applyIfCPUFeature = {"zibi", "true"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    static void eqI_0(int x) {
        if (x == 0) {
            intCounter++;
        }
    }

    // ---- long: constants Zibi CAN encode -> must select the Zibi branch ----

    @Test
    @IR(counts = {"cmpL_imm_branch", ">= 1"},
        applyIfCPUFeature = {"zibi", "true"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    static void eqL_m1(long x) {
        if (x == -1L) {
            longCounter++;
        }
    }

    @Test
    @IR(counts = {"cmpL_imm_branch", ">= 1"},
        applyIfCPUFeature = {"zibi", "true"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    static void neL_7(long x) {
        if (x != 7L) {
            longCounter++;
        }
    }

    // ---- long: constant Zibi CANNOT encode -> must fall back, no Zibi branch ----

    @Test
    @IR(failOn = {"cmpL_imm_branch"},
        applyIfCPUFeature = {"zibi", "true"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    static void eqL_32(long x) {
        if (x == 32L) {
            longCounter++;
        }
    }

    @Run(test = {"eqI_m1", "neI_1", "eqI_31", "eqI_32", "eqI_0",
                 "eqL_m1", "neL_7", "eqL_32"})
    static void run() {
        // Exercise both directions of every branch so the profile keeps a real
        // two-way branch (avoids the untaken side being pruned to an uncommon
        // trap) and so the functional result stays verifiable.

        // --- int, Zibi-encodable ---
        intCounter = 0;
        eqI_m1(-1);            // -1 == -1 -> taken
        eqI_m1(0);             // not taken
        eqI_m1(-2);            // not taken
        Asserts.assertEQ(intCounter, 1);

        intCounter = 0;
        neI_1(0);              // 0 != 1 -> taken
        neI_1(2);              // taken
        neI_1(1);              // not taken
        Asserts.assertEQ(intCounter, 2);

        intCounter = 0;
        eqI_31(31);            // taken
        eqI_31(30);            // not taken
        Asserts.assertEQ(intCounter, 1);

        // --- int, fallback (constant out of {-1, 1..31}) ---
        intCounter = 0;
        eqI_32(32);            // taken
        eqI_32(31);            // not taken
        Asserts.assertEQ(intCounter, 1);

        intCounter = 0;
        eqI_0(0);              // taken
        eqI_0(1);              // not taken
        Asserts.assertEQ(intCounter, 1);

        // --- long, Zibi-encodable ---
        longCounter = 0;
        eqL_m1(-1L);           // taken
        eqL_m1(0L);            // not taken
        Asserts.assertEQ(longCounter, 1L);

        longCounter = 0;
        neL_7(8L);             // taken
        neL_7(7L);             // not taken
        Asserts.assertEQ(longCounter, 1L);

        // --- long, fallback ---
        longCounter = 0;
        eqL_32(32L);           // taken
        eqL_32(31L);           // not taken
        Asserts.assertEQ(longCounter, 1L);
    }
}
