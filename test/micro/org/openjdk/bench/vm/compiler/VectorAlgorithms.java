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
 *  - Disable fill loop detection, so we don't use intrinsic but auto vectorization:
 *      -XX:-OptimizeFill
 *  - Lilliput can also have an effect, because it can change alignment and have
 *    an impact on which exact intrinsic is chosen (e.g. fill and copy):
 *      -XX:+UseCompactObjectHeaders
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

    @Param({"10000"})
    public int NUM_X_OBJECTS;

    @Param({"0"})
    public int SEED;

    public static Random RANDOM;
    public static int[] aI;
    public static int[] rI;

    public static int[] eI;
    public static int idx = 0;

    public static int[] oopsX4;
    public static int[] memX4;

    @Setup
    public void init() {
        RANDOM = new Random(SEED);

        aI = new int[SIZE];
        rI = new int[SIZE];

        eI = new int[0x10000];

        oopsX4 = new int[SIZE];
        memX4 = new int[NUM_X_OBJECTS * 4];
    }

    @Setup(Level.Iteration)
    public void resetInputs() {
        Arrays.setAll(aI, i -> RANDOM.nextInt());

        // Populate with some random values from aI, and some totally random values.
        for (int i = 0; i < eI.length; i++) {
            eI[i] = (RANDOM.nextInt(10) == 0) ? RANDOM.nextInt() : aI[RANDOM.nextInt(SIZE)];
        }

        for (int i = 0; i < oopsX4.length; i++) {
            // assign either a zero=null, or assign a random oop.
            oopsX4[i] = (RANDOM.nextInt(10) == 0) ? 0 : RANDOM.nextInt(NUM_X_OBJECTS) * 4;
        }
        // Just fill the whole array with random values.
        // The relevant field is only at every "4 * i + 3" though.
        for (int i = 0; i < memX4.length; i++) {
            memX4[i] = RANDOM.nextInt();
        }
    }

    // ------------------------------------------------------------------------------------------
    //               Benchmarks just forward arguments and returns.
    // ------------------------------------------------------------------------------------------

    @Benchmark
    public Object fillI_loop() {
        return VectorAlgorithmsImpl.fillI_loop(rI);
    }

    @Benchmark
    public Object fillI_VectorAPI() {
        return VectorAlgorithmsImpl.fillI_VectorAPI(rI);
    }

    @Benchmark
    public Object fillI_Arrays() {
        return VectorAlgorithmsImpl.fillI_Arrays(rI);
    }

    @Benchmark
    public Object iotaI_loop() {
        return VectorAlgorithmsImpl.iotaI_loop(rI);
    }

    @Benchmark
    public Object iotaI_VectorAPI() {
        return VectorAlgorithmsImpl.iotaI_VectorAPI(rI);
    }

    @Benchmark
    public Object copyI_loop() {
        return VectorAlgorithmsImpl.copyI_loop(aI, rI);
    }

    @Benchmark
    public Object copyI_VectorAPI() {
        return VectorAlgorithmsImpl.copyI_VectorAPI(aI, rI);
    }

    @Benchmark
    public Object copyI_System_arraycopy() {
        return VectorAlgorithmsImpl.copyI_System_arraycopy(aI, rI);
    }

    @Benchmark
    public Object mapI_loop() {
        return VectorAlgorithmsImpl.mapI_loop(aI, rI);
    }

    @Benchmark
    public Object mapI_VectorAPI() {
        return VectorAlgorithmsImpl.mapI_VectorAPI(aI, rI);
    }

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
    public int findMinIndexI_loop() {
        return VectorAlgorithmsImpl.findMinIndexI_loop(aI);
    }

    @Benchmark
    public int findMinIndexI_VectorAPI() {
        return VectorAlgorithmsImpl.findMinIndexI_VectorAPI(aI);
    }

    @Benchmark
    public int findI_loop() {
        idx = (idx + 1) & 0xffff;
        int e = aI[idx];
        return VectorAlgorithmsImpl.findI_loop(aI, e);
    }

    @Benchmark
    public int findI_VectorAPI() {
        idx = (idx + 1) & 0xffff;
        int e = aI[idx];
        return VectorAlgorithmsImpl.findI_VectorAPI(aI, e);
    }

    @Benchmark
    public Object reverseI_loop() {
        return VectorAlgorithmsImpl.reverseI_loop(aI, rI);
    }

    @Benchmark
    public Object reverseI_VectorAPI() {
        return VectorAlgorithmsImpl.reverseI_VectorAPI(aI, rI);
    }

    @Benchmark
    public Object filterI_loop() {
        idx = (idx + 1) & 0xffff;
        int e = aI[idx];
        return VectorAlgorithmsImpl.filterI_loop(aI, rI, e);
    }

    @Benchmark
    public Object filterI_VectorAPI() {
        idx = (idx + 1) & 0xffff;
        int e = aI[idx];
        return VectorAlgorithmsImpl.filterI_VectorAPI(aI, rI, e);
    }

    @Benchmark
    public int reduceAddIFieldsX4_loop() {
        return VectorAlgorithmsImpl.reduceAddIFieldsX4_loop(oopsX4, memX4);
    }

    @Benchmark
    public int reduceAddIFieldsX4_VectorAPI() {
        return VectorAlgorithmsImpl.reduceAddIFieldsX4_VectorAPI(oopsX4, memX4);
    }
}
