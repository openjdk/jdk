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
package org.openjdk.bench.java.util.stream.ops.ref;

import org.openjdk.bench.java.util.stream.ops.DoubleAccumulator;
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
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.DoubleFunction;
import java.util.stream.Stream;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;
import java.util.stream.IntStream;
import java.util.Arrays;

/**
 * Benchmark for flatMap() operation.
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class FlatMap {

    /**
     * Implementation notes:
     *   - parallel version requires thread-safe sink, we use the same for sequential version for better comparison
     *   - operations are explicit inner classes to untangle unwanted lambda effects
     *   - the result of applying consecutive operations is the same, in order to have the same number of elements in sink
     */

    @Param({"10", "100", "1000"})
    private int size;

    private Function<Long, Stream<Long>> funArrayStream;
    private Function<Long, Stream<Long>> funIterateStream;
    private LongFunction<LongStream> funLongStream;
    private LongFunction<LongStream> funIterateLongStream;
    private IntFunction<IntStream> funIntStream;
    private IntFunction<IntStream> funIterateIntStream;
    private DoubleFunction<DoubleStream> funDoubleStream;
    private DoubleFunction<DoubleStream> funIterateDoubleStream;

    private Long[] cachedRefArray;
    private int[] cachedIntArray;
    private long[] cachedLongArray;
    private double[] cachedDoubleArray;

    @Setup
    public void setup() {
        final int cachedSize = size;
        cachedRefArray = new Long[cachedSize];
        cachedIntArray = new int[cachedSize];
        cachedLongArray = new long[cachedSize];
        cachedDoubleArray = new double[cachedSize];
        for(int i = 0;i < cachedRefArray.length;++i) {
            cachedRefArray[i]    = Long.valueOf(i);
            cachedIntArray[i]    = i;
            cachedLongArray[i]   = i;
            cachedDoubleArray[i] = i;
        }

        funArrayStream = new Function<Long, Stream<Long>>() { @Override public Stream<Long> apply(Long l) {
            return Arrays.stream(cachedRefArray);
        } };
        funIterateStream = new Function<Long, Stream<Long>>() { @Override public Stream<Long> apply(Long l) {
            return Stream.iterate(0L, i -> i + 1).limit(cachedSize); } };
        funLongStream = new LongFunction<LongStream>() { @Override public LongStream apply(long l) {
            return Arrays.stream(cachedLongArray); } };
        funIterateLongStream = new LongFunction<LongStream>() { @Override public LongStream apply(long l) {
            return LongStream.iterate(0L, i -> i + 1).limit(cachedSize); } };
        funIntStream = new IntFunction<IntStream>() { @Override public IntStream apply(int i) {
            return Arrays.stream(cachedIntArray); } };
        funIterateIntStream = new IntFunction<IntStream>() { @Override public IntStream apply(int i) {
            return IntStream.iterate(0, ii -> ii + 1).limit(cachedSize); } };
        funDoubleStream = new DoubleFunction<DoubleStream>() { @Override public DoubleStream apply(double d) {
            return Arrays.stream(cachedDoubleArray); } };
        funIterateDoubleStream = new DoubleFunction<DoubleStream>() { @Override public DoubleStream apply(double d) {
            return DoubleStream.iterate(0d, i -> i + 1d).limit(cachedSize); } };
    }

    @Benchmark
    public long seq_array_ref() {
        return funArrayStream.apply(0L)
                .flatMap(funArrayStream)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long par_array_ref() {
        return funArrayStream.apply(0L)
                .parallel()
                .flatMap(funArrayStream)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_array_long() {
        return funLongStream.apply(0L)
                .flatMap(funLongStream)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long par_array_long() {
        return funLongStream.apply(0L)
                .parallel()
                .flatMap(funLongStream)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_array_int() {
        return funIntStream.apply(0)
                .flatMap(funIntStream)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long par_array_int() {
        return funIntStream.apply(0)
                .parallel()
                .flatMap(funIntStream)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public double seq_array_double() {
        return funDoubleStream.apply(0d)
                .flatMap(funDoubleStream)
                .collect(DoubleAccumulator::new, DoubleAccumulator::add, DoubleAccumulator::merge).get();
    }

    @Benchmark
    public double par_array_double() {
        return funDoubleStream.apply(0d)
                .parallel()
                .flatMap(funDoubleStream)
                .collect(DoubleAccumulator::new, DoubleAccumulator::add, DoubleAccumulator::merge).get();
    }

    @Benchmark
    public long seq_iterate_ref() {
        return funIterateStream.apply(0L)
                .flatMap(funIterateStream)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long par_iterate_ref() {
        return funIterateStream.apply(0L)
                .parallel()
                .flatMap(funIterateStream)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }


    @Benchmark
    public long seq_iterate_long() {
        return funIterateLongStream.apply(0L)
                .flatMap(funIterateLongStream)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long par_iterate_long() {
        return funIterateLongStream.apply(0L)
                .parallel()
                .flatMap(funIterateLongStream)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long seq_iterate_int() {
        return funIterateIntStream.apply(0)
                .flatMap(funIterateIntStream)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public long par_iterate_int() {
        return funIterateIntStream.apply(0)
                .parallel()
                .flatMap(funIterateIntStream)
                .collect(LongAccumulator::new, LongAccumulator::add, LongAccumulator::merge).get();
    }

    @Benchmark
    public double seq_iterate_double() {
        return funIterateDoubleStream.apply(0d)
                .flatMap(funIterateDoubleStream)
                .collect(DoubleAccumulator::new, DoubleAccumulator::add, DoubleAccumulator::merge).get();
    }

    @Benchmark
    public double par_iterate_double() {
        return funIterateDoubleStream.apply(0d)
                .parallel()
                .flatMap(funIterateDoubleStream)
                .collect(DoubleAccumulator::new, DoubleAccumulator::add, DoubleAccumulator::merge).get();
    }

}
