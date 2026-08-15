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
* @bug 8387213
* @summary Float16Vector.fma must accumulate in-place (231-form) so a register
*          blocked FMA reduction does not spill accumulators to the stack.
* @modules jdk.incubator.vector
* @library /test/lib /
* @run driver ${test.main.class}
*/

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.*;
import static jdk.incubator.vector.Float16.*;
import compiler.lib.generators.Generator;
import static compiler.lib.generators.Generators.G;

public class TestFloat16VectorFMAAccumSpill {

    static final VectorSpecies<Float16> SPECIES = Float16Vector.SPECIES_PREFERRED;
    static final int L = SPECIES.length();

    // Register-blocked GEMM micro-kernel tile: ROWS accumulators (from A) times
    // COLS B vectors == 24 loop-carried accumulators. At this tile size the
    // 132-form (result tied to a multiplicand) cannot keep an accumulator live in
    // its own register and spills to the stack, whereas the 231-form (result tied
    // to the addend, i.e. vfmadd231ph) accumulates in place with no spills.
    static final int ROWS = 4;
    static final int COLS = 6;
    static final int K    = 256;

    final short[] apack;                 // K * ROWS scalars (raw binary16 bits)
    final short[] bpack;                 // K * COLS * L elements
    final short[] output;                // ROWS * COLS * L result tile

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    public TestFloat16VectorFMAAccumSpill() {
        apack  = new short[K * ROWS];
        bpack  = new short[K * COLS * L];
        output = new short[ROWS * COLS * L];
        Generator<Short> gen = G.float16s();
        for (int i = 0; i < apack.length; i++) {
            apack[i] = gen.next();
        }
        for (int i = 0; i < bpack.length; i++) {
            bpack[i] = gen.next();
        }
    }

    // The FMA must be intrinsified (FmaVHF) and, in the final code, the
    // register-blocked reduction must not spill any vector accumulator to the
    // stack (no MemToRegSpillCopy of a vector type). A regression to the 132-form
    // reintroduces the accumulator-preserving copies/spills and fails this test.
    @Test
    @IR(counts = {IRNode.FMA_VHF, ">0"},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(failOn = {IRNode.MEM_TO_REG_SPILL_COPY_TYPE, "vector[xyz]"},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    void fmaAccum() {
        Float16Vector c00 = (Float16Vector) SPECIES.zero();
        Float16Vector c01 = c00, c02 = c00, c03 = c00, c04 = c00, c05 = c00;
        Float16Vector c10 = c00, c11 = c00, c12 = c00, c13 = c00, c14 = c00, c15 = c00;
        Float16Vector c20 = c00, c21 = c00, c22 = c00, c23 = c00, c24 = c00, c25 = c00;
        Float16Vector c30 = c00, c31 = c00, c32 = c00, c33 = c00, c34 = c00, c35 = c00;

        for (int k = 0; k < K; k++) {
            int abase = k * ROWS;
            Float16Vector a0 = Float16Vector.broadcast(SPECIES, apack[abase]);
            Float16Vector a1 = Float16Vector.broadcast(SPECIES, apack[abase + 1]);
            Float16Vector a2 = Float16Vector.broadcast(SPECIES, apack[abase + 2]);
            Float16Vector a3 = Float16Vector.broadcast(SPECIES, apack[abase + 3]);

            int bbase = k * COLS * L;
            Float16Vector b0 = Float16Vector.fromArray(SPECIES, bpack, bbase);
            Float16Vector b1 = Float16Vector.fromArray(SPECIES, bpack, bbase + L);
            Float16Vector b2 = Float16Vector.fromArray(SPECIES, bpack, bbase + 2 * L);
            Float16Vector b3 = Float16Vector.fromArray(SPECIES, bpack, bbase + 3 * L);
            Float16Vector b4 = Float16Vector.fromArray(SPECIES, bpack, bbase + 4 * L);
            Float16Vector b5 = Float16Vector.fromArray(SPECIES, bpack, bbase + 5 * L);

            c00 = (Float16Vector) a0.lanewise(VectorOperators.FMA, b0, c00);
            c01 = (Float16Vector) a0.lanewise(VectorOperators.FMA, b1, c01);
            c02 = (Float16Vector) a0.lanewise(VectorOperators.FMA, b2, c02);
            c03 = (Float16Vector) a0.lanewise(VectorOperators.FMA, b3, c03);
            c04 = (Float16Vector) a0.lanewise(VectorOperators.FMA, b4, c04);
            c05 = (Float16Vector) a0.lanewise(VectorOperators.FMA, b5, c05);
            c10 = (Float16Vector) a1.lanewise(VectorOperators.FMA, b0, c10);
            c11 = (Float16Vector) a1.lanewise(VectorOperators.FMA, b1, c11);
            c12 = (Float16Vector) a1.lanewise(VectorOperators.FMA, b2, c12);
            c13 = (Float16Vector) a1.lanewise(VectorOperators.FMA, b3, c13);
            c14 = (Float16Vector) a1.lanewise(VectorOperators.FMA, b4, c14);
            c15 = (Float16Vector) a1.lanewise(VectorOperators.FMA, b5, c15);
            c20 = (Float16Vector) a2.lanewise(VectorOperators.FMA, b0, c20);
            c21 = (Float16Vector) a2.lanewise(VectorOperators.FMA, b1, c21);
            c22 = (Float16Vector) a2.lanewise(VectorOperators.FMA, b2, c22);
            c23 = (Float16Vector) a2.lanewise(VectorOperators.FMA, b3, c23);
            c24 = (Float16Vector) a2.lanewise(VectorOperators.FMA, b4, c24);
            c25 = (Float16Vector) a2.lanewise(VectorOperators.FMA, b5, c25);
            c30 = (Float16Vector) a3.lanewise(VectorOperators.FMA, b0, c30);
            c31 = (Float16Vector) a3.lanewise(VectorOperators.FMA, b1, c31);
            c32 = (Float16Vector) a3.lanewise(VectorOperators.FMA, b2, c32);
            c33 = (Float16Vector) a3.lanewise(VectorOperators.FMA, b3, c33);
            c34 = (Float16Vector) a3.lanewise(VectorOperators.FMA, b4, c34);
            c35 = (Float16Vector) a3.lanewise(VectorOperators.FMA, b5, c35);
        }

        store(0, 0, c00); store(0, 1, c01); store(0, 2, c02);
        store(0, 3, c03); store(0, 4, c04); store(0, 5, c05);
        store(1, 0, c10); store(1, 1, c11); store(1, 2, c12);
        store(1, 3, c13); store(1, 4, c14); store(1, 5, c15);
        store(2, 0, c20); store(2, 1, c21); store(2, 2, c22);
        store(2, 3, c23); store(2, 4, c24); store(2, 5, c25);
        store(3, 0, c30); store(3, 1, c31); store(3, 2, c32);
        store(3, 3, c33); store(3, 4, c34); store(3, 5, c35);
    }

    void store(int r, int c, Float16Vector v) {
        v.intoArray(output, (r * COLS + c) * L);
    }

    @Check(test = "fmaAccum")
    void check() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                for (int lane = 0; lane < L; lane++) {
                    // Scalar reference in the same per-lane accumulation order.
                    Float16 acc = valueOf(0.0f);
                    for (int k = 0; k < K; k++) {
                        Float16 a = shortBitsToFloat16(apack[k * ROWS + r]);
                        Float16 b = shortBitsToFloat16(bpack[k * COLS * L + c * L + lane]);
                        acc = fma(a, b, acc);
                    }
                    Float16 actual = shortBitsToFloat16(output[(r * COLS + c) * L + lane]);
                    Verify.checkEQ(actual, acc);
                }
            }
        }
    }
}
