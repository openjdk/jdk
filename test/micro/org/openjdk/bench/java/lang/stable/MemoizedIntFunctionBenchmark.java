/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.stable;

import jdk.internal.lang.StableValue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/**
 * Benchmark measuring memoized IntFunction performance
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgsAppend = {"--add-exports=java.base/jdk.internal.lang=ALL-UNNAMED", "--enable-preview"})
@Threads(Threads.MAX)   // Benchmark under contention
public class MemoizedIntFunctionBenchmark {

    private static final int ITERATIONS = 17;
    private static final int VALUE = 42;
    private static final int VALUE2 = 23;

    private static final List<Integer> VALUES = List.of(VALUE, VALUE2);

    private static final IntFunction<Integer> F = VALUES::get;
    private final IntFunction<Integer> f = VALUES::get;

    private final IntFunction<Integer> m = StableValue.memoizedIntFunction(2, VALUES::get);
    private static final IntFunction<Integer> M = StableValue.memoizedIntFunction(2, VALUES::get);;

    @Benchmark
    public int intFunction() {
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += f.apply(0) + f.apply(1);
        }
        return sum;
    }

    @Benchmark
    public int memoized() {
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += m.apply(0) + m.apply(1);
        }
        return sum;
    }

    @Benchmark
    public int sIntFunction() {
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += F.apply(0) + F.apply(1);
        }
        return sum;
    }

    @Benchmark
    public int sMemoized() {
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += M.apply(0) + M.apply(1);
        }
        return sum;
    }

}
