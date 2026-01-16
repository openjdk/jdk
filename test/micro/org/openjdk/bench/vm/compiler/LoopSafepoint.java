/*
 * Copyright (c) 2025 Alibaba Group Holding Limited. All Rights Reserved.
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@State(Scope.Thread)
public class LoopSafepoint {
    static int someInts0 = 1;
    static int someInts1 = 2;

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private void empty() {}

    // All benchmarks below are in sync with the IR test
    // `compiler.loopopts.TestRedundantSafepointElimination.java`.
    // Check the comments in the IR test for more details.

    @Benchmark
    public int topLevelCountedLoop() {
        int sum = 0;
        for (int i = 0; i < 100000; i++) {
            sum += someInts0;
        }
        return sum;
    }

    @Benchmark
    public int topLevelCountedLoopWithDomCall() {
        int sum = 0;
        for (int i = 0; i < 100000; i++) {
            empty();
            sum += someInts0;
        }
        return sum;
    }

    @Benchmark
    public int topLevelUncountedLoop() {
        int sum = 0;
        for (int i = 0; i < 100000; i += someInts0) {
            sum += someInts1;
        }
        return sum;
    }

    @Benchmark
    public int topLevelUncountedLoopWithDomCall() {
        int sum = 0;
        for (int i = 0; i < 100000; i += someInts0) {
            empty();
            sum += someInts1;
        }
        return sum;
    }

    @Benchmark
    public int outerLoopWithDomCall() {
        int sum = 0;
        for (int i = 0; i < 100; i += someInts0) {
            empty();
            for (int j = 0; j < 1000; j++) {
                sum += someInts1;
            }
        }
        return sum;
    }

    @Benchmark
    public int outerAndInnerLoopWithDomCall() {
        int sum = 0;
        for (int i = 0; i < 100; i += someInts0) {
            empty();
            for (int j = 0; j < 1000; j++) {
                empty();
                sum += someInts1;
            }
        }
        return sum;
    }

    @Benchmark
    public int outerLoopWithLocalNonCallSafepoint() {
        int sum = 0;
        for (int i = 0; i < 100; i += someInts0) {
            for (int j = 0; j < 1000; j++) {
                sum += someInts1;
            }
        }
        return sum;
    }

    @Benchmark
    public void loopNeedsToPreserveSafepoint() {
        int i = 0, stop;
        while (i < 1000) {
            stop = i + 10;
            while (i < stop) {
                i += 1;
            }
        }
    }
}
