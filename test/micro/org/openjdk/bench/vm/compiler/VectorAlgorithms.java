/*
 *  Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.vm.compiler;

import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/**
 * The goal of this benchmark is to show the power of auto vectorization
 * and the Vector API.
 *
 * Please only modify this benchark in synchronization with the IR test:
 *   test/hotspot/jtreg/compiler/vectorization/TestVectorAlgorithms.java
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 1, jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class VectorAlgorithms {
    private static final VectorSpecies<Integer> SPECIES_I    = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> SPECIES_I512 = IntVector.SPECIES_512;

    @Param({"640000"})
    public int SIZE;

    public static int[] aI;

    @Setup
    public void init() {
        aI = new int[SIZE];
    }

    // ------------------------------------------------------------------------------------------
    //               Benchmarks just forward arguments and returns.
    // ------------------------------------------------------------------------------------------

    @Benchmark
    public int bench_reduceAddI_loop() {
        return reduceAddI_loop(aI);
    }

    @Benchmark
    public int bench_reduceAddI_reassociate() {
        return reduceAddI_reassociate(aI);
    }

    @Benchmark
    public int bench_reduceAddI_VectorAPI_naive() {
        return reduceAddI_VectorAPI_naive(aI);
    }

    @Benchmark
    public int bench_reduceAddI_VectorAPI_reduction_after_loop() {
        return reduceAddI_VectorAPI_reduction_after_loop(aI);
    }

    // ------------------------------------------------------------------------------------------
    //               Below: just copied from TestVectorAlgorithms.java
    //               Only stripped @Test and @IR annotations.
    // ------------------------------------------------------------------------------------------

    public int reduceAddI_loop(int[] a) {
        int sum = 0;
        for (int i = 0; i < a.length; i++) {
            // Relying on simple reduction loop should vectorize since JDK26.
            sum += a[i];
        }
        return sum;
    }

    public int reduceAddI_reassociate(int[] a) {
        int sum = 0;
        int i;
        for (i = 0; i < a.length - 3; i+=4) {
            // Unroll 4x, reassociate inside.
            sum += a[i] + a[i + 1] + a[i + 2] + a[i + 3];
        }
        for (; i < a.length; i++) {
            // Tail
            sum += a[i];
        }
        return sum;
    }

    public int reduceAddI_VectorAPI_naive(int[] a) {
        var sum = 0;
        int i;
        for (i = 0; i < SPECIES_I.loopBound(a.length); i += SPECIES_I.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I, a, i);
            sum += v.reduceLanes(VectorOperators.ADD);
        }
        for (; i < a.length; i++) {
            sum += a[i];
        }
        return sum;
    }

    public int reduceAddI_VectorAPI_reduction_after_loop(int[] a) {
        var acc = IntVector.broadcast(SPECIES_I, 0);
        int i;
        for (i = 0; i < SPECIES_I.loopBound(a.length); i += SPECIES_I.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I, a, i);
            acc = acc.add(v);
        }
        int sum = acc.reduceLanes(VectorOperators.ADD);
        for (; i < a.length; i++) {
            sum += a[i];
        }
        return sum;
    }

    //@Benchmark
    //public void scanAddI_loop() {
    //    int sum = 0;
    //    for (int i = 0; i < AI.length; i++) {
    //        sum += AI[i];
    //        RI[i] = sum;
    //    }
    //}

    //@Benchmark
    //public void scanAddI_loop_reassociate() {
    //    int sum = 0;
    //    for (int i = 0; i < AI.length; i+=4) {
    //        // We cut the latency by a factor of 4, but increase the number of additions.
    //        int old_sum = sum;
    //        int v0 = AI[i + 0];
    //        int v1 = AI[i + 1];
    //        int v2 = AI[i + 2];
    //        int v3 = AI[i + 3];
    //        int v01 = v0 + v1;
    //        int v23 = v2 + v3;
    //        int v0123 = v01 + v23;
    //        sum += v0123;
    //        RI[i + 0] = old_sum + v0;
    //        RI[i + 1] = old_sum + v01;
    //        RI[i + 2] = old_sum + v01 + v2;
    //        RI[i + 3] = old_sum + v0123;
    //    }
    //}

    //@Benchmark
    //public void scanAddI_VectorAPI_shift_blend_add() {
    //    // Using Naive Parallel Algorithm: Hills and Steele
    //    int sum = 0;
    //    for (int i = 0; i < SPECIES_I512.loopBound(AI.length); i += SPECIES_I512.length()) {
    //        IntVector v = IntVector.fromArray(SPECIES_I512, AI, i);
    //        v = v.add(v.lanewise(VectorOperators.LSHL, 1 ).blend(0, VectorMask.fromLong(SPECIES_I512, 0b1111111111111110)));
    //        v = v.add(v.lanewise(VectorOperators.LSHL, 2 ).blend(0, VectorMask.fromLong(SPECIES_I512, 0b1111111111111100)));
    //        v = v.add(v.lanewise(VectorOperators.LSHL, 4 ).blend(0, VectorMask.fromLong(SPECIES_I512, 0b1111111111110000)));
    //        v = v.add(v.lanewise(VectorOperators.LSHL, 8 ).blend(0, VectorMask.fromLong(SPECIES_I512, 0b1111111100000000)));
    //        v = v.add(sum);
    //        v.intoArray(RI, i);
    //        sum = v.lane(SPECIES_I512.length() - 1);
    //    }
    //}

    //@Benchmark
    //public void scanAddI_VectorAPI_permute_add() {
    //    // Using Naive Parallel Algorithm: Hills and Steele
    //    int sum = 0;
    //    var shf1 = VectorShuffle.fromArray(SPECIES_I512, new int[]{-1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14}, 0);
    //    var shf2 = VectorShuffle.fromArray(SPECIES_I512, new int[]{-1, -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13}, 0);
    //    var shf3 = VectorShuffle.fromArray(SPECIES_I512, new int[]{-1, -1, -1, -1,  0,  1,  2,  3,  4,  6,  7,  8,  9, 10, 11, 12}, 0);
    //    var shf4 = VectorShuffle.fromArray(SPECIES_I512, new int[]{-1, -1, -1, -1, -1, -1, -1, -1,  0,  1,  2,  3,  4,  6,  7,  8}, 0);
    //    var mask1 = VectorMask.fromLong(SPECIES_I512, 0b1111111111111110);
    //    var mask2 = VectorMask.fromLong(SPECIES_I512, 0b1111111111111100);
    //    var mask3 = VectorMask.fromLong(SPECIES_I512, 0b1111111111110000);
    //    var mask4 = VectorMask.fromLong(SPECIES_I512, 0b1111111100000000);
    //    for (int i = 0; i < SPECIES_I512.loopBound(AI.length); i += SPECIES_I512.length()) {
    //        IntVector v = IntVector.fromArray(SPECIES_I512, AI, i);
    //        v = v.add(v.rearrange(shf1), mask1);
    //        v = v.add(v.rearrange(shf2), mask2);
    //        v = v.add(v.rearrange(shf3), mask3);
    //        v = v.add(v.rearrange(shf4), mask4);
    //        v = v.add(sum);
    //        v.intoArray(RI, i);
    //        sum = v.lane(SPECIES_I512.length() - 1);
    //    }
    //}
}
