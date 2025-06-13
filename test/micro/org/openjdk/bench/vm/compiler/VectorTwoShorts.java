/*
 * Copyright (c) 2025, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class VectorTwoShorts {
    @Param({"64", "128", "512", "1024"})
    public int LEN;

    private short[] sA;
    private short[] sB;
    private short[] sC;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() {
        sA = new short[LEN];
        sB = new short[LEN];
        sC = new short[LEN];

        for (int i = 0; i < LEN; i++) {
            sA[i] = (short) r.nextInt();
            sB[i] = (short) r.nextInt();
        }
    }

    @Benchmark
    public void addVec2S() {
        for (int i = 0; i < LEN - 3; i++) {
            sC[i + 3] = (short) (sA[i] + sB[i]);
        }
    }

    @Benchmark
    public void mulVec2S() {
        for (int i = 0; i < LEN - 3; i++) {
            sC[i + 3] = (short) (sA[i] * sB[i]);
        }
    }

    @Benchmark
    public void reverseBytesVec2S() {
        for (int i = 0; i < LEN - 3; i++) {
            sC[i + 3] = (short) Short.reverseBytes(sA[i]);
        }
    }
}