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

package org.openjdk.bench.java.lang.monotonic;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/**
 * Benchmark measuring lazy list performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = "--enable-preview")
@OperationsPerInvocation(1000)
/* 2024-04-02
Benchmark                                 Mode  Cnt  Score   Error  Units
LazyListBenchmark.instanceArrayList       avgt   10  0.432 ? 0.005  ns/op
LazyListBenchmark.instanceLazy            avgt   10  1.504 ? 0.020  ns/op
LazyListBenchmark.instanceMonotonicEager  avgt   10  0.715 ? 0.007  ns/op
LazyListBenchmark.staticArrayList         avgt   10  0.358 ? 0.006  ns/op
LazyListBenchmark.staticLazy              avgt   10  0.828 ? 0.004  ns/op
LazyListBenchmark.staticMonotonicEager    avgt   10  0.717 ? 0.004  ns/op
 */
public class LazyListBenchmark {

    private static final IntFunction<Integer> FUNCTION = i -> i;
    private static final int SIZE = 1_000;

    private static final List<Integer> LAZY = List.ofLazy(SIZE, FUNCTION);
    private final List<Lazy<Integer>> Lazy_EAGER = initMonotonic(Stream.generate(Lazy::<Integer>of)
            .limit(SIZE)
            .toList());

    private static final List<Integer> ARRAY_LIST = init(new ArrayList<>(SIZE));

    private final List<Integer> lazy = List.ofLazy(SIZE, FUNCTION);
    private final List<Lazy<Integer>> lazyEager = initMonotonic(Stream.generate(Lazy::<Integer>of)
            .limit(SIZE)
            .toList());
    private static final List<Integer> arrayList = init(new ArrayList<>(SIZE));

    @Setup
    public void setup() {
    }

    @Benchmark
    public Integer instanceArrayList() {
        int sum = 0;
        for (int i = 0; i < arrayList.size(); i++) {
            sum += arrayList.get(i);
        }
        return sum;
    }

    @Benchmark
    public int instanceMonotonicEager() {
        int sum = 0;
        for (int i = 0; i < lazyEager.size(); i++) {
            sum += lazyEager.get(i).orThrow();
        }
        return sum;
    }

    @Benchmark
    public int instanceLazy() {
        int sum = 0;
        for (int i = 0; i < lazy.size(); i++) {
            sum += lazy.get(i);
        }
        return sum;
    }

    @Benchmark
    public int staticArrayList() {
        int sum = 0;
        for (int i = 0; i < ARRAY_LIST.size(); i++) {
            sum += ARRAY_LIST.get(i);
        }
        return sum;
    }

    @Benchmark
    public int staticMonotonicEager() {
        int sum = 0;
        for (int i = 0; i < Lazy_EAGER.size(); i++) {
            sum += Lazy_EAGER.get(i).orThrow();
        }
        return sum;
    }

    @Benchmark
    public int staticLazy() {
        int sum = 0;
        for (int i = 0; i < LAZY.size(); i++) {
            sum += LAZY.get(i);
        }
        return sum;
    }

    private static List<Lazy<Integer>> initMonotonic(List<Lazy<Integer>> list) {
        for (int i = 0; i < SIZE; i++) {
            list.get(i).bindOrThrow(FUNCTION.apply(i));
        }
        return list;
    }

    private static List<Integer> init(List<Integer> list) {
        for (int i = 0; i < SIZE; i++) {
            list.add(FUNCTION.apply(i));
        }
        return list;
    }

}
