/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.util.stream.ops.ref;

import org.openjdk.bench.java.util.stream.ops.LongAccumulator;
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

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.Arrays;
import java.util.stream.Gatherer;
import static org.openjdk.bench.java.util.stream.ops.ref.BenchmarkGathererImpls.filter;
import static org.openjdk.bench.java.util.stream.ops.ref.BenchmarkGathererImpls.map;

/**
 * Benchmark for misc operations implemented as Gatherer, with the default map implementation of Stream as baseline.
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 4, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 7, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(jvmArgsAppend = "--enable-preview", value = 1)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class GatherMiscPar {

    /**
     * Implementation notes:
     *   - parallel version requires thread-safe sink, we use the same for sequential version for better comparison
     *   - operations are explicit inner classes to untangle unwanted lambda effects
     *   - the result of applying consecutive operations is the same, in order to have the same number of elements in sink
     */

    @Param({"10","100","1000000"})
    private int size;

    private Function<Long, Long> timesTwo, halved;
    private Predicate<Long> evens, odds;

    private Gatherer<Long, ?, Long> gathered;

    private Long[] cachedInputArray;

    @Setup
    public void setup() {
        cachedInputArray = new Long[size];
        for(int i = 0;i < size;++i)
            cachedInputArray[i] = Long.valueOf(i);

        timesTwo = new Function<Long, Long>() { @Override public Long apply(Long l) {
                return l*2;
            } };
        halved = new Function<Long, Long>() { @Override public Long apply(Long l) { return l/2; } };

        evens = new Predicate<Long>() { @Override public boolean test(Long l) {
            return l % 2 == 0;
        } };
        odds = new Predicate<Long>() { @Override public boolean test(Long l) {
            return l % 2 != 0;
        } };

        gathered = filter(odds)
                    .andThen(map(timesTwo))
                    .andThen(map(halved))
                    .andThen(filter(evens));
    }

    @Benchmark
    public long par_misc_baseline() {
        return Arrays.stream(cachedInputArray)
                .parallel()
                .filter(odds)
                .map(timesTwo)
                .map(halved)
                .filter(evens)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long par_misc_gather() {
        return Arrays.stream(cachedInputArray)
                .parallel()
                .gather(filter(odds))
                .gather(map(timesTwo))
                .gather(map(halved))
                .gather(filter(evens))
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long par_misc_gather_precomposed() {
        return Arrays.stream(cachedInputArray)
                .parallel()
                .gather(gathered)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }
}
