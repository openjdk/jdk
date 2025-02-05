/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.util.stream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Benchmark for checking the effect of sized collectors.
 * <p>
 * See JDK-8072840
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class SizedCollectors {

    /**
     * Implementation notes:
     * - parallel version requires thread-safe sink, we use the same for sequential version for better comparison
     * - Q is chosen to do some non-trivial work, but not too much so that allocation and copying overhead is still a
     *   relevant factor. This is meant to be representative of use cases of streams in which data is reshaped rather
     *   than processed.
     */

    @Param("1000")
    private int N;

    @Param("10")
    private int Q;

    private Collector<Long, ?, ConcurrentMap<Long, Boolean>> sizedCollector;
    private Collector<Long, ?, ConcurrentMap<Long, Boolean>> unsizedCollector;

    private Collector<CharSequence, ?, String> sizedJoiner;
    private Collector<CharSequence, ?, String> unsizedJoiner;

    @Setup
    public void setup() {
        sizedCollector = Collector.ofSized(
            ConcurrentHashMap::new,
            ConcurrentHashMap::new,
            (map, input) -> map.put(input, doWork(input, Q)),
            (_, _) -> {
                throw new IllegalArgumentException();
            },
            Collector.Characteristics.CONCURRENT,
            Collector.Characteristics.UNORDERED
        );
        unsizedCollector = Collector.of(
            ConcurrentHashMap::new,
            (map, input) -> map.putIfAbsent(input, doWork(input, Q)),
            (_, _) -> {
                throw new IllegalArgumentException();
            },
            Collector.Characteristics.CONCURRENT,
            Collector.Characteristics.UNORDERED
        );

        sizedJoiner = Collectors.joining(",");
        unsizedJoiner = Collector.of(() -> new StringJoiner(",", "", ""),
            StringJoiner::add, StringJoiner::merge, StringJoiner::toString);
    }

    @Benchmark
    public Map<Long, Boolean> seq_sized() {
        return LongStream.range(0, N).boxed().collect(sizedCollector);
    }

    @Benchmark
    public Map<Long, Boolean> seq_unsized() {
        return LongStream.range(0, N).boxed().collect(unsizedCollector);
    }

    @Benchmark
    public Map<Long, Boolean> par_sized() {
        return LongStream.range(0, N).parallel().boxed().collect(sizedCollector);
    }

    @Benchmark
    public Map<Long, Boolean> par_unsized() {
        return LongStream.range(0, N).parallel().boxed().collect(unsizedCollector);
    }

    @Benchmark
    public String join_sized() {
        return LongStream.range(0, N).mapToObj(l -> "Hello " + doWork(l, Q)).collect(sizedJoiner);
    }

    @Benchmark
    public String join_unsized() {
        return LongStream.range(0, N).mapToObj(l -> "Hello " + doWork(l, Q)).collect(unsizedJoiner);
    }

    /**
     * Make some work.
     * This method have a couple of distinguishable properties:
     * - the run time is linear with Q
     * - the computation is dependent on input, preventing common reductions
     * - the returned result is dependent on loop result, preventing dead code elimination
     * - the returned result is almost always false
     * <p>
     * This code uses inlined version of ThreadLocalRandom.next() to mitigate the edge effects
     * of acquiring TLR every single call.
     *
     * @param input input
     * @return result
     */
    public static boolean doWork(long input, long count) {
        long t = input;
        for (int i = 0; i < count; i++) {
            t += (t * 0x5DEECE66DL + 0xBL) & (0xFFFFFFFFFFFFL);
        }
        return (t == 0);
    }
}
