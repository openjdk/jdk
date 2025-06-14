/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Benchmark measuring StableValue performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgsAppend = {
        "--enable-preview"
})
@Threads(Threads.MAX)   // Benchmark under contention
@OperationsPerInvocation(100)
public class StableFunctionBenchmark {

    private static final int SIZE = 100;
    private static final Set<Integer> SET = IntStream.range(0, SIZE).boxed().collect(Collectors.toSet());

    private static final Map<Integer, Integer> MAP = StableValue.map(SET, Function.identity());
    private static final Function<Integer, Integer> FUNCTION = StableValue.function(SET, Function.identity());

    private final Map<Integer, Integer> map = StableValue.map(SET, Function.identity());
    private final Function<Integer, Integer> function = StableValue.function(SET, Function.identity());

    @Benchmark
    public int map() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += map.get(i);
        }
        return sum;
    }

    @Benchmark
    public int function() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += function.apply(i);
        }
        return sum;
    }

    @Benchmark
    public int staticSMap() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += MAP.get(i);
        }
        return sum;
    }

    @Benchmark
    public int staticIntFunction() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += FUNCTION.apply(i);
        }
        return sum;
    }

}
