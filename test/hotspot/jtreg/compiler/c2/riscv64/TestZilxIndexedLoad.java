/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Rivos Inc. All rights reserved.
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
 * @summary Verify that C2 selects the Zilx indexed-load instructs for a
 *          base + (optionally scaled) index address with no displacement.
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 * @requires os.arch == "riscv64" & vm.cpu.features ~= ".*zilx.*"
 * @run driver compiler.c2.riscv64.TestZilxIndexedLoad
 */

// This test guards the Zilx C2 instruction-selection rules (loadX_zilx in
// riscv.ad) against regression. It is deliberately a *codegen* test: the loaded
// values are irrelevant, only which mach instruct is selected matters.
//
// Why two-argument Unsafe with a pure (non-constant) offset -- and not a plain
// array access or single-argument off-heap Unsafe?
//
//   * Every Zilx memory operand encodes only base + (index << scale) with
//     disp == 0 (Zilx reuses the AMO opcode and has no immediate offset field).
//   * A normal Java array access arr[i] carries the constant array-header
//     offset. AddPNode::Ideal reassociates that constant into the load's
//     displacement and leaves the scaled index as a separate shadd, so a plain
//     array load can never select a disp == 0 indexed operand.
//   * Single-argument off-heap access getInt(long) sinks the whole address
//     computation into a CastX2P and matches indirectX2P, not Zilx.
//
// A two-argument Unsafe access on an on-heap object with offset == ((long)i<<k)
// (or the AndL(ConvI2L(i)) zero-extended form) and NO additive constant
// produces exactly AddP(base, LShiftL(ConvI2L(i), k)) with disp == 0, which is
// the only shape pd_clone_address_expressions folds into a Zilx addressing
// mode. Each @Test takes the index as a parameter and performs a single access
// so no loop strength-reduction can reshape the address before matching. The
// reads land inside the (generously sized) backing array and never fault.
//
// @IR gating: the @requires clause restricts the run to CPUs whose
// vm.cpu.features report zilx, and every rule additionally sets
// applyIfCPUFeature = {"zilx", "true"} so the codegen checks fire only when the
// VM confirms the feature is active. The counts regex matches the "#@loadX_zilx"
// instruct marker that PrintOptoAssembly emits from each instruct's format
// string, so it fires iff that specific Zilx instruct is selected.

package compiler.c2.riscv64;

import compiler.lib.ir_framework.*;
import jdk.internal.misc.Unsafe;
import jdk.test.lib.Utils;
import java.util.Random;

public class TestZilxIndexedLoad {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Random RANDOM = Utils.getRandomInstance();

    // Largest index used by any test method.
    static final int MAX_IDX = 1024;
    // Object-relative reads use offsets up to MAX_IDX << 3; size the backing
    // store well past that so every access stays inside the array.
    static final byte[] BUF = new byte[MAX_IDX * 16];

    public static void main(String[] args) {
        BUF[0] = 1; // avoid an all-zero buffer looking trivially constant
        TestFramework.runWithFlags("--add-modules", "java.base",
                                   "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                                   "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZilx");
    }

    // base + (index << 2), 32-bit signed load -> lxsw / loadI_zilx
    @Test
    @IR(counts = {"loadI_zilx", ">= 1"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY,
        applyIfCPUFeature = {"zilx", "true"})
    static int testIntScaled(byte[] buf, int i) {
        return UNSAFE.getInt(buf, ((long) i) << 2);
    }

    // base + (index << 3), 64-bit load -> lxsd / loadL_zilx
    @Test
    @IR(counts = {"loadL_zilx", ">= 1"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY,
        applyIfCPUFeature = {"zilx", "true"})
    static long testLongScaled(byte[] buf, int i) {
        return UNSAFE.getLong(buf, ((long) i) << 3);
    }

    // base + (index << 1), 16-bit signed load -> lxsh / loadS_zilx
    @Test
    @IR(counts = {"loadS_zilx", ">= 1"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY,
        applyIfCPUFeature = {"zilx", "true"})
    static int testShortScaled(byte[] buf, int i) {
        return UNSAFE.getShort(buf, ((long) i) << 1);
    }

    // base + zext32(index), unscaled byte -> lxsb / loadB_zilx (indIndexUwB, or
    // indIndexL if the compiler proves the index is already non-negative). Both
    // select the same instruct, so the marker is loadB_zilx either way.
    @Test
    @IR(counts = {"loadB_zilx", ">= 1"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY,
        applyIfCPUFeature = {"zilx", "true"})
    static int testByteZext(byte[] buf, int i) {
        return UNSAFE.getByte(buf, ((long) i) & 0xFFFFFFFFL);
    }

    @Run(test = {"testIntScaled", "testLongScaled", "testShortScaled", "testByteZext"})
    @Warmup(10000)
    static void run() {
        int i = RANDOM.nextInt(MAX_IDX);
        // Values are irrelevant; keep the results live so the loads are not
        // eliminated as dead code.
        int a = testIntScaled(BUF, i);
        long b = testLongScaled(BUF, i);
        int c = testShortScaled(BUF, i);
        int d = testByteZext(BUF, i);
        if (((a + c + d) ^ b) == 0xDEADBEEFL) {
            // Extremely unlikely; only present so the JIT cannot prove the
            // accumulated results are unused.
            throw new RuntimeException("sink " + a + b + c + d);
        }
    }
}
