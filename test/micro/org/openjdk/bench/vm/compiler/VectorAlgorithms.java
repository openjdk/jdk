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

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/**
 * The goal of this benchmark is to show the power of auto vectorization
 * and the Vector API.
 *
 * Please only modify this benchark in synchronization with the IR test:
 *   test/hotspot/jtreg/compiler/vectorization/TestVectorAlgorithms.java
 *
 * You may want to play with the following VM flags:
 *  - Disable auto vectorization:
 *      -XX:+UnlockDiagnosticVMOptions -XX:AutoVectorizationOverrideProfitability=0
 *  - Smaller vector size:
 *      -XX:MaxVectorSize=16
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 50, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgs = {"--add-modules=jdk.incubator.vector", "-XX:CompileCommand=inline,*VectorAlgorithmsImpl::*"})
public class VectorAlgorithms {
    @Param({"640000"})
    public int SIZE;

    @Param({"0"})
    public int SEED;

    public static Random RANDOM;
    public static int[] aI;
    public static int[] rI;

    @Setup
    public void init() {
        aI = new int[SIZE];
        rI = new int[SIZE];
        RANDOM = new Random(SEED);
    }

    @Setup(Level.Iteration)
    public void resetInputs() {
        Arrays.setAll(aI, i -> RANDOM.nextInt());
    }

    // ------------------------------------------------------------------------------------------
    //               Benchmarks just forward arguments and returns.
    // ------------------------------------------------------------------------------------------

    @Benchmark
    public int reduceAddI_loop() {
        return VectorAlgorithmsImpl.reduceAddI_loop(aI);
    }

    @Benchmark
    public int reduceAddI_reassociate() {
        return VectorAlgorithmsImpl.reduceAddI_reassociate(aI);
    }

    @Benchmark
    public int reduceAddI_VectorAPI_naive() {
        return VectorAlgorithmsImpl.reduceAddI_VectorAPI_naive(aI);
    }

    @Benchmark
    public int reduceAddI_VectorAPI_reduction_after_loop() {
        return VectorAlgorithmsImpl.reduceAddI_VectorAPI_reduction_after_loop(aI);
    }

    @Benchmark
    public Object scanAddI_loop() {
        return VectorAlgorithmsImpl.scanAddI_loop(aI, rI);
    }

    @Benchmark
    public Object scanAddI_loop_reassociate() {
        return VectorAlgorithmsImpl.scanAddI_loop_reassociate(aI, rI);
    }

    @Benchmark
    public Object scanAddI_VectorAPI_permute_add() {
        return VectorAlgorithmsImpl.scanAddI_VectorAPI_permute_add(aI, rI);
    }

    @Benchmark
    public int findMinIndex_loop() {
        return VectorAlgorithmsImpl.findMinIndex_loop(aI);
    }

    @Benchmark
    public int findMinIndex_VectorAPI() {
        return VectorAlgorithmsImpl.findMinIndex_VectorAPI(aI);
    }

    @Benchmark
    public Object reverse_loop() {
        return VectorAlgorithmsImpl.reverse_loop(aI, rI);
    }

    @Benchmark
    public Object reverse_VectorAPI() {
        return VectorAlgorithmsImpl.reverse_VectorAPI(aI, rI);
    }
}
