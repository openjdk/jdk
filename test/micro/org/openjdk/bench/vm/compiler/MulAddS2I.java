/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * For JDK-8321008
 * Test MulAddS2I vectorization.
 */

package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class MulAddS2I {
    @Param({"16384"}) // 1024*16
    int RANGE;
    @Param({"8191"})  // 1024*16/2-1
    int ITER;
    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    short[] sArr1;
    short[] sArr2;

    @Setup
    public void init() {
        sArr1 = new short[RANGE];
        sArr2 = new short[RANGE];
        for (int i = 0; i < RANGE; i++) {
            sArr1[i] = (short)(r.nextInt());
            sArr2[i] = (short)(r.nextInt());
        }
    }

    // ****************
    // NOTE: performance gain from testa - testc
    // ****************

    @Benchmark
    public void testa(Blackhole bh) {
        int[] out = new int[ITER];
        int[] out2 = new int[ITER];
        for (int i = 0; i < ITER; i++) {
            out[i] += ((sArr1[2*i] * sArr1[2*i]) + (sArr1[2*i+1] * sArr1[2*i+1]));
            out2[i] += out[i];
        }
        bh.consume(out);
    }

    @Benchmark
    public void testb(Blackhole bh) {
        int[] out = new int[ITER];
        int[] out2 = new int[ITER];
        for (int i = 0; i < ITER; i++) {
            out[i] += ((sArr1[2*i] * sArr2[2*i]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out2[i] += out[i];
        }
        bh.consume(out);
    }

    @Benchmark
    public void testc(Blackhole bh) {
        int[] out = new int[ITER];
        for (int i = 0; i < ITER; i++) {
            out[i] += ((sArr1[2*i] * sArr2[2*i]) + (sArr1[2*i+1] * sArr2[2*i+1]));
        }
        bh.consume(out);
    }

    // ****************
    // NOTE: performance regression from testd - testi
    // ****************

    @Benchmark
    public void testd(Blackhole bh) {
        int[] out = new int[ITER];
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with the same structure.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr1[2*i+2] * sArr2[2*i+2]) + (sArr1[2*i+3] * sArr2[2*i+3]));
        }
        bh.consume(out);
    }

    @Benchmark
    public void teste(Blackhole bh) {
        int[] out = new int[ITER];
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr2[2*i+2] * sArr1[2*i+2]) + (sArr1[2*i+3] * sArr2[2*i+3])); // swap(1 2)
        }
        bh.consume(out);
    }

    @Benchmark
    public void testf(Blackhole bh) {
        int[] out = new int[ITER];
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr2[2*i+2] * sArr1[2*i+2]) + (sArr2[2*i+3] * sArr1[2*i+3])); // swap(1 2), swap(3 4)
        }
        bh.consume(out);
    }

    @Benchmark
    public void testg(Blackhole bh) {
        int[] out = new int[ITER];
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr1[2*i+3] * sArr2[2*i+3]) + (sArr1[2*i+2] * sArr2[2*i+2])); // swap(1 3), swap(2 4)
        }
        bh.consume(out);
    }

    @Benchmark
    public void testh(Blackhole bh) {
        int[] out = new int[ITER];
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr2[2*i+3] * sArr1[2*i+3]) + (sArr2[2*i+2] * sArr1[2*i+2])); // swap(1 4), swap(2 3)
        }
        bh.consume(out);
    }

    @Benchmark
    public void testi(Blackhole bh) {
        int[] out = new int[ITER];
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps that prevent vectorization.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1])); // ok
            out[i+1] += ((sArr1[2*i+2] * sArr2[2*i+3]) + (sArr1[2*i+3] * sArr2[2*i+2])); // bad
        }
        bh.consume(out);
    }

    @Benchmark
    public void testj(Blackhole bh) {
        int[] out = new int[ITER];
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps that prevent vectorization.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+1]) + (sArr1[2*i+1] * sArr2[2*i+0])); // bad
            out[i+1] += ((sArr1[2*i+2] * sArr2[2*i+3]) + (sArr1[2*i+3] * sArr2[2*i+2])); // bad
        }
        bh.consume(out);
    }

    @Benchmark
    public void testk(Blackhole bh) {
        int[] out = new int[ITER];
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps that prevent vectorization.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+1]) + (sArr1[2*i+1] * sArr2[2*i+0])); // bad
            out[i+1] += ((sArr1[2*i+2] * sArr2[2*i+2]) + (sArr1[2*i+3] * sArr2[2*i+3])); // ok
        }
        bh.consume(out);
    }

    @Benchmark
    public void testl(Blackhole bh) {
        int[] out = new int[ITER];
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps that prevent vectorization.
            out[i+0] += ((sArr1[2*i+1] * sArr2[2*i+1]) + (sArr1[2*i+0] * sArr2[2*i+0])); // ok
            out[i+1] += ((sArr1[2*i+2] * sArr2[2*i+3]) + (sArr1[2*i+3] * sArr2[2*i+2])); // bad
        }
        bh.consume(out);
    }

}
