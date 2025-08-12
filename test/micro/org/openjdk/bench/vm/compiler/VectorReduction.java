/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
public abstract class VectorReduction {
    @Param({"512"})
    public int COUNT;

    private int[] intsA;
    private int[] intsB;
    private int[] intsC;
    private int[] intsD;
    private long[] longsA;
    private long[] longsB;
    private long[] longsC;
    private long[] longsD;
    private double[] doublesA;
    private double[] doublesB;
    private double[] doublesC;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    private static int globalResI;

    @Setup
    public void init() {
        intsA = new int[COUNT];
        intsB = new int[COUNT];
        intsC = new int[COUNT];
        intsD = new int[COUNT];
        longsA = new long[COUNT];
        longsB = new long[COUNT];
        longsC = new long[COUNT];
        longsD = new long[COUNT];
        doublesA = new double[COUNT];
        doublesB = new double[COUNT];
        doublesC = new double[COUNT];

        for (int i = 0; i < COUNT; i++) {
            intsA[i] = r.nextInt();
            intsB[i] = r.nextInt();
            intsC[i] = r.nextInt();
            longsA[i] = r.nextLong();
            longsB[i] = r.nextLong();
            longsC[i] = r.nextLong();
            doublesA[i] = r.nextDouble();
            doublesB[i] = r.nextDouble();
            doublesC[i] = r.nextDouble();
        }
    }

    @Benchmark
    public void andRedI(Blackhole bh) {
        int resI = 0xFFFF;
        for (int i = 0; i < COUNT; i++) {
            intsD[i] = (intsA[i] * intsB[i]) + (intsA[i] * intsC[i]) + (intsB[i] * intsC[i]);
            resI &= intsD[i];
        }
        bh.consume(resI);
    }

    @Benchmark
    public void orRedI(Blackhole bh) {
        int resI = 0x0000;
        for (int i = 0; i < COUNT; i++) {
            intsD[i] = (intsA[i] * intsB[i]) + (intsA[i] * intsC[i]) + (intsB[i] * intsC[i]);
            resI |= intsD[i];
        }
        bh.consume(resI);
    }

    @Benchmark
    public void xorRedI(Blackhole bh) {
        int resI = 0x0000;
        for (int i = 0; i < COUNT; i++) {
            intsD[i] = (intsA[i] * intsB[i]) + (intsA[i] * intsC[i]) + (intsB[i] * intsC[i]);
            resI ^= intsD[i];
        }
        bh.consume(resI);
    }

    @Benchmark
    public void andRedL(Blackhole bh) {
        long resL = 0xFFFFFFFF;
        for (int i = 0; i < COUNT; i++) {
            longsD[i] = (longsA[i] + longsB[i]) + (longsA[i] + longsC[i]) + (longsB[i] + longsC[i]);
            resL &= longsD[i];
        }
        bh.consume(resL);
    }

    @Benchmark
    public void orRedL(Blackhole bh) {
        long resL = 0x00000000;
        for (int i = 0; i < COUNT; i++) {
            longsD[i] = (longsA[i] + longsB[i]) + (longsA[i] + longsC[i]) + (longsB[i] + longsC[i]);
            resL |= longsD[i];
        }
        bh.consume(resL);
    }

    @Benchmark
    public void xorRedL(Blackhole bh) {
        long resL = 0x00000000;
        for (int i = 0; i < COUNT; i++) {
            longsD[i] = (longsA[i] + longsB[i]) + (longsA[i] + longsC[i]) + (longsB[i] + longsC[i]);
            resL ^= longsD[i];
        }
        bh.consume(resL);
    }

    @Benchmark
    public void mulRedD(Blackhole bh) {
        double resD = 0.0;
        for (int i = 0; i < COUNT; i++) {
            resD += (doublesA[i] * doublesB[i]) + (doublesA[i] * doublesC[i]) +
                     (doublesB[i] * doublesC[i]);
        }
        bh.consume(resD);
    }

    @Benchmark
    public void andRedIPartiallyUnrolled(Blackhole bh) {
        int resI = 0xFFFF;
        for (int i = 0; i < COUNT / 2; i++) {
            int j = 2*i;
            intsD[j] = (intsA[j] * intsB[j]) + (intsA[j] * intsC[j]) + (intsB[j] * intsC[j]);
            resI &= intsD[j];
            j = 2*i + 1;
            intsD[j] = (intsA[j] * intsB[j]) + (intsA[j] * intsC[j]) + (intsB[j] * intsC[j]);
            resI &= intsD[j];
        }
        bh.consume(resI);
    }

    @Benchmark
    public void andRedIOnGlobalAccumulator() {
        globalResI = 0xFFFF;
        for (int i = 0; i < COUNT; i++) {
            intsD[i] = (intsA[i] * intsB[i]) + (intsA[i] * intsC[i]) + (intsB[i] * intsC[i]);
            globalResI &= intsD[i];
        }
    }

    @Fork(value = 2, jvmArgs = {
        "-XX:+UseSuperWord"
    })
    public static class WithSuperword extends VectorReduction {

    }

    @Fork(value = 2, jvmArgs = {
        "-XX:-UseSuperWord"
    })
    public static class NoSuperword extends VectorReduction {
    }

}

