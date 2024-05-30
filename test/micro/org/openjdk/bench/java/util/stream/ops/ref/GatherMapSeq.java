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
import java.util.Arrays;
import java.util.stream.Collector;
import java.util.stream.Gatherer;
import static org.openjdk.bench.java.util.stream.ops.ref.BenchmarkGathererImpls.map;

/**
 * Benchmark for map() operation implemented as Gatherer, with the default map implementation of Stream as baseline.
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 4, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 7, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(jvmArgsAppend = "--enable-preview", value = 1)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class GatherMapSeq {

    /**
     * Implementation notes:
     *   - parallel version requires thread-safe sink, we use the same for sequential version for better comparison
     *   - operations are explicit inner classes to untangle unwanted lambda effects
     *   - the result of applying consecutive operations is the same, in order to have the same number of elements in sink
     */

    @Param({"10", "100", "1000000"})
    private int size;

    private Function<Long, Long> m1, m2, m3;

    private Gatherer<Long, ?, Long> gather_m1, gather_m2, gather_m3, gather_all, gather_m1_111;

    private Long[] cachedInputArray;

    private final static Collector<Long,LongAccumulator,Long> accumulate =
            Collector.of(LongAccumulator::new,
                         LongAccumulator::add,
                         (l,r) -> { l.merge(r); return l; },
                         LongAccumulator::get);

    @Setup
    public void setup() {
        cachedInputArray = new Long[size];
        for(int i = 0;i < size;++i)
            cachedInputArray[i] = Long.valueOf(i);
        m1 = new Function<Long, Long>() { @Override public Long apply(Long l) {
                return l*2;
            } };
        m2 = new Function<Long, Long>() { @Override public Long apply(Long l) {
                return l*2;
            } };
        m3 = new Function<Long, Long>() { @Override public Long apply(Long l) {
                return l*2;
            } };
        gather_m1 = map(m1);
        gather_m2 = map(m2);
        gather_m3 = map(m3);
        gather_all = gather_m1.andThen(gather_m2.andThen(gather_m3));
        gather_m1_111 = gather_m1.andThen(gather_m1.andThen(gather_m1));
    }

    @Benchmark
    public long seq_invoke_baseline() {
        return Arrays.stream(cachedInputArray)
                .map(m1)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_invoke_gather() {
        return Arrays.stream(cachedInputArray)
                .gather(map(m1))
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_invoke_gather_preallocated() {
        return Arrays.stream(cachedInputArray)
                .gather(gather_m1)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_chain_111_baseline() {
        return Arrays.stream(cachedInputArray)
                .map(m1)
                .map(m1)
                .map(m1)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_chain_111_gather_separate() {
        return Arrays.stream(cachedInputArray)
                .gather(map(m1))
                .gather(map(m1))
                .gather(map(m1))
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_chain_111_gather_composed() {
        return Arrays.stream(cachedInputArray)
                .gather(map(m1).andThen(map(m1)).andThen(map(m1)))
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_chain_111_gather_precomposed() {
        return Arrays.stream(cachedInputArray)
                .gather(gather_m1_111)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_chain_123_baseline() {
        return Arrays.stream(cachedInputArray)
                .map(m1)
                .map(m2)
                .map(m3)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_chain_123_gather_separate() {
        return Arrays.stream(cachedInputArray)
                .gather(map(m1))
                .gather(map(m2))
                .gather(map(m3))
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_chain_123_gather_composed() {
        return Arrays.stream(cachedInputArray)
                .gather(map(m1).andThen(map(m2)).andThen(map(m3)))
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_chain_123_gather_precomposed() {
        return Arrays.stream(cachedInputArray)
                .gather(gather_all)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }
}
