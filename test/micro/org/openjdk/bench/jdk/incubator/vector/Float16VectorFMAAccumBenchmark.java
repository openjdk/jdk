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
package org.openjdk.bench.jdk.incubator.vector;

import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;

/*
 * Register-blocked, loop-carried Float16Vector FMA accumulation -- the inner
 * "micro-kernel" shape used by FP16 GEMM and similar BLAS-3 kernels.
 */
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgs = {"--add-modules=jdk.incubator.vector", "-Xbatch", "-XX:-TieredCompilation"})
public class Float16VectorFMAAccumBenchmark {

    @Param({"256", "1024"})
    int K;

    static final VectorSpecies<Float16> SP = Float16Vector.SPECIES_PREFERRED;
    static final int L = SP.length();

    short[] bpack;
    short[] apack;

    static short f16(float v) { return Float16.float16ToRawShortBits(Float16.valueOf(v)); }

    @Setup(Level.Trial)
    public void setup() {
        bpack = new short[K * 6 * L];
        apack = new short[K * 4];
        // Small bounded values keep FP16 accumulation finite.
        for (int k = 0; k < K; k++) {
            for (int c = 0; c < 6 * L; c++) {
                bpack[k * 6 * L + c] = f16((((k + c) % 7) - 3) * 0.0625f);
            }
            for (int r = 0; r < 4; r++) {
                apack[k * 4 + r] = f16((((k + r) % 5) - 2) * 0.125f);
            }
        }
    }

    // 24 live accumulators (4 rows x 6 B vectors) -- the full GEMM micro-kernel
    // tile. At this tile size the 132-form (result tied to a multiplicand)
    // overflows the ZMM register file and spills to the stack, whereas the
    // 231-form accumulates in place.
    @Benchmark
    public short fmaAccum24() {
        Float16Vector z = Float16Vector.broadcast(SP, (short) 0);
        Float16Vector c00 = z, c01 = z, c02 = z, c03 = z, c04 = z, c05 = z,
                      c10 = z, c11 = z, c12 = z, c13 = z, c14 = z, c15 = z,
                      c20 = z, c21 = z, c22 = z, c23 = z, c24 = z, c25 = z,
                      c30 = z, c31 = z, c32 = z, c33 = z, c34 = z, c35 = z;
        int b = 0, a = 0;
        for (int k = 0; k < K; k++, b += 6 * L, a += 4) {
            Float16Vector b0 = Float16Vector.fromArray(SP, bpack, b);
            Float16Vector b1 = Float16Vector.fromArray(SP, bpack, b + L);
            Float16Vector b2 = Float16Vector.fromArray(SP, bpack, b + 2 * L);
            Float16Vector b3 = Float16Vector.fromArray(SP, bpack, b + 3 * L);
            Float16Vector b4 = Float16Vector.fromArray(SP, bpack, b + 4 * L);
            Float16Vector b5 = Float16Vector.fromArray(SP, bpack, b + 5 * L);
            Float16Vector av;
            av = Float16Vector.broadcast(SP, apack[a]);
            c00 = b0.lanewise(VectorOperators.FMA, av, c00);
            c01 = b1.lanewise(VectorOperators.FMA, av, c01);
            c02 = b2.lanewise(VectorOperators.FMA, av, c02);
            c03 = b3.lanewise(VectorOperators.FMA, av, c03);
            c04 = b4.lanewise(VectorOperators.FMA, av, c04);
            c05 = b5.lanewise(VectorOperators.FMA, av, c05);
            av = Float16Vector.broadcast(SP, apack[a + 1]);
            c10 = b0.lanewise(VectorOperators.FMA, av, c10);
            c11 = b1.lanewise(VectorOperators.FMA, av, c11);
            c12 = b2.lanewise(VectorOperators.FMA, av, c12);
            c13 = b3.lanewise(VectorOperators.FMA, av, c13);
            c14 = b4.lanewise(VectorOperators.FMA, av, c14);
            c15 = b5.lanewise(VectorOperators.FMA, av, c15);
            av = Float16Vector.broadcast(SP, apack[a + 2]);
            c20 = b0.lanewise(VectorOperators.FMA, av, c20);
            c21 = b1.lanewise(VectorOperators.FMA, av, c21);
            c22 = b2.lanewise(VectorOperators.FMA, av, c22);
            c23 = b3.lanewise(VectorOperators.FMA, av, c23);
            c24 = b4.lanewise(VectorOperators.FMA, av, c24);
            c25 = b5.lanewise(VectorOperators.FMA, av, c25);
            av = Float16Vector.broadcast(SP, apack[a + 3]);
            c30 = b0.lanewise(VectorOperators.FMA, av, c30);
            c31 = b1.lanewise(VectorOperators.FMA, av, c31);
            c32 = b2.lanewise(VectorOperators.FMA, av, c32);
            c33 = b3.lanewise(VectorOperators.FMA, av, c33);
            c34 = b4.lanewise(VectorOperators.FMA, av, c34);
            c35 = b5.lanewise(VectorOperators.FMA, av, c35);
        }
        Float16Vector r0 = c00.lanewise(VectorOperators.ADD, c01)
                              .lanewise(VectorOperators.ADD, c02.lanewise(VectorOperators.ADD, c03))
                              .lanewise(VectorOperators.ADD, c04.lanewise(VectorOperators.ADD, c05));
        Float16Vector r1 = c10.lanewise(VectorOperators.ADD, c11)
                              .lanewise(VectorOperators.ADD, c12.lanewise(VectorOperators.ADD, c13))
                              .lanewise(VectorOperators.ADD, c14.lanewise(VectorOperators.ADD, c15));
        Float16Vector r2 = c20.lanewise(VectorOperators.ADD, c21)
                              .lanewise(VectorOperators.ADD, c22.lanewise(VectorOperators.ADD, c23))
                              .lanewise(VectorOperators.ADD, c24.lanewise(VectorOperators.ADD, c25));
        Float16Vector r3 = c30.lanewise(VectorOperators.ADD, c31)
                              .lanewise(VectorOperators.ADD, c32.lanewise(VectorOperators.ADD, c33))
                              .lanewise(VectorOperators.ADD, c34.lanewise(VectorOperators.ADD, c35));
        Float16Vector acc = r0.lanewise(VectorOperators.ADD, r1)
                              .lanewise(VectorOperators.ADD, r2.lanewise(VectorOperators.ADD, r3));
        return acc.reduceLanes(VectorOperators.ADD);
    }
}
