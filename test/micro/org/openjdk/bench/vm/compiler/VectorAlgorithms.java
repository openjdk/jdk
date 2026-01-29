/*
 *  Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 5, jvmArgs = {"--add-modules=jdk.incubator.vector", "-XX:CompileCommand=inline,*VectorAlgorithmsImpl*::*"})
public class VectorAlgorithms {
    @Param({"640000"})
    public int SIZE;

    @Param({"10000"})
    public int NUM_X_OBJECTS;

    @Param({"0"})
    public int SEED;

    VectorAlgorithmsImpl.Data d;

    @Setup
    public void init() {
        d = new VectorAlgorithmsImpl.Data(SIZE, SEED, NUM_X_OBJECTS);
    }

    // ------------------------------------------------------------------------------------------
    //               Benchmarks just forward arguments and returns.
    // ------------------------------------------------------------------------------------------

    @Benchmark
    public Object fillI_loop() {
        return VectorAlgorithmsImpl.fillI_loop(d.rI1);
    }

    @Benchmark
    public Object fillI_VectorAPI() {
        return VectorAlgorithmsImpl.fillI_VectorAPI(d.rI1);
    }

    @Benchmark
    public Object fillI_Arrays() {
        return VectorAlgorithmsImpl.fillI_Arrays(d.rI1);
    }

    @Benchmark
    public Object iotaI_loop() {
        return VectorAlgorithmsImpl.iotaI_loop(d.rI1);
    }

    @Benchmark
    public Object iotaI_VectorAPI() {
        return VectorAlgorithmsImpl.iotaI_VectorAPI(d.rI1);
    }

    @Benchmark
    public Object copyI_loop() {
        return VectorAlgorithmsImpl.copyI_loop(d.aI, d.rI1);
    }

    @Benchmark
    public Object copyI_VectorAPI() {
        return VectorAlgorithmsImpl.copyI_VectorAPI(d.aI, d.rI1);
    }

    @Benchmark
    public Object copyI_System_arraycopy() {
        return VectorAlgorithmsImpl.copyI_System_arraycopy(d.aI, d.rI1);
    }

    @Benchmark
    public Object mapI_loop() {
        return VectorAlgorithmsImpl.mapI_loop(d.aI, d.rI1);
    }

    @Benchmark
    public Object mapI_VectorAPI() {
        return VectorAlgorithmsImpl.mapI_VectorAPI(d.aI, d.rI1);
    }

    @Benchmark
    public int reduceAddI_loop() {
        return VectorAlgorithmsImpl.reduceAddI_loop(d.aI);
    }

    @Benchmark
    public int reduceAddI_reassociate() {
        return VectorAlgorithmsImpl.reduceAddI_reassociate(d.aI);
    }

    @Benchmark
    public int reduceAddI_VectorAPI_naive() {
        return VectorAlgorithmsImpl.reduceAddI_VectorAPI_naive(d.aI);
    }

    @Benchmark
    public int reduceAddI_VectorAPI_reduction_after_loop() {
        return VectorAlgorithmsImpl.reduceAddI_VectorAPI_reduction_after_loop(d.aI);
    }

    @Benchmark
    public float dotProductF_loop() {
        return VectorAlgorithmsImpl.dotProductF_loop(d.aF, d.bF);
    }

    @Benchmark
    public float dotProductF_VectorAPI_naive() {
        return VectorAlgorithmsImpl.dotProductF_VectorAPI_naive(d.aF, d.bF);
    }

    @Benchmark
    public float dotProductF_VectorAPI_reduction_after_loop() {
        return VectorAlgorithmsImpl.dotProductF_VectorAPI_reduction_after_loop(d.aF, d.bF);
    }

    @Benchmark
    public int hashCodeB_loop() {
        return VectorAlgorithmsImpl.hashCodeB_loop(d.aB);
    }

    @Benchmark
    public int hashCodeB_Arrays() {
        return VectorAlgorithmsImpl.hashCodeB_Arrays(d.aB);
    }

    @Benchmark
    public int hashCodeB_VectorAPI_v1() {
        return VectorAlgorithmsImpl.hashCodeB_VectorAPI_v1(d.aB);
    }

    @Benchmark
    public int hashCodeB_VectorAPI_v2() {
        return VectorAlgorithmsImpl.hashCodeB_VectorAPI_v2(d.aB);
    }

    @Benchmark
    public Object scanAddI_loop() {
        return VectorAlgorithmsImpl.scanAddI_loop(d.aI, d.rI1);
    }

    @Benchmark
    public Object scanAddI_loop_reassociate() {
        return VectorAlgorithmsImpl.scanAddI_loop_reassociate(d.aI, d.rI1);
    }

    @Benchmark
    public Object scanAddI_VectorAPI_permute_add() {
        return VectorAlgorithmsImpl.scanAddI_VectorAPI_permute_add(d.aI, d.rI1);
    }

    @Benchmark
    public int findMinIndexI_loop() {
        return VectorAlgorithmsImpl.findMinIndexI_loop(d.aI);
    }

    @Benchmark
    public int findMinIndexI_VectorAPI() {
        return VectorAlgorithmsImpl.findMinIndexI_VectorAPI(d.aI);
    }

    @Benchmark
    public int findI_loop() {
        // Every invocation should have a different value for e, so that
        // we don't get branch-prediction that is too good. And also so
        // that the position where we exit is more evenly distributed.
        d.eI_idx = (d.eI_idx + 1) & 0xffff;
        int e = d.eI[d.eI_idx];
        return VectorAlgorithmsImpl.findI_loop(d.aI, e);
    }

    @Benchmark
    public int findI_VectorAPI() {
        d.eI_idx = (d.eI_idx + 1) & 0xffff;
        int e = d.eI[d.eI_idx];
        return VectorAlgorithmsImpl.findI_VectorAPI(d.aI, e);
    }

    @Benchmark
    public Object reverseI_loop() {
        return VectorAlgorithmsImpl.reverseI_loop(d.aI, d.rI1);
    }

    @Benchmark
    public Object reverseI_VectorAPI() {
        return VectorAlgorithmsImpl.reverseI_VectorAPI(d.aI, d.rI1);
    }

    @Benchmark
    public Object filterI_loop() {
        // Every invocation should have a different value for e, so that
        // we don't get branch-prediction that is too good. And also so
        // That the length of the resulting data is more evenly distributed.
        d.eI_idx = (d.eI_idx + 1) & 0xffff;
        int e = d.eI[d.eI_idx];
        return VectorAlgorithmsImpl.filterI_loop(d.aI, d.rI1, e);
    }

    @Benchmark
    public Object filterI_VectorAPI() {
        d.eI_idx = (d.eI_idx + 1) & 0xffff;
        int e = d.eI[d.eI_idx];
        return VectorAlgorithmsImpl.filterI_VectorAPI(d.aI, d.rI1, e);
    }

    @Benchmark
    public int reduceAddIFieldsX4_loop() {
        return VectorAlgorithmsImpl.reduceAddIFieldsX4_loop(d.oopsX4, d.memX4);
    }

    @Benchmark
    public int reduceAddIFieldsX4_VectorAPI() {
        return VectorAlgorithmsImpl.reduceAddIFieldsX4_VectorAPI(d.oopsX4, d.memX4);
    }

    @Benchmark
    public Object lowerCaseB_loop() {
        return VectorAlgorithmsImpl.lowerCaseB_loop(d.strB, d.rB1);
    }

    @Benchmark
    public Object lowerCaseB_VectorAPI_v1() {
        return VectorAlgorithmsImpl.lowerCaseB_VectorAPI_v1(d.strB, d.rB1);
    }

    @Benchmark
    public Object lowerCaseB_VectorAPI_v2() {
        return VectorAlgorithmsImpl.lowerCaseB_VectorAPI_v2(d.strB, d.rB1);
    }
}
