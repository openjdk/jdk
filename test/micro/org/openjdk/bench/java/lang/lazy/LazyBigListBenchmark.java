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

package org.openjdk.bench.java.lang.lazy;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
@OperationsPerInvocation(1_000_000)
public class LazyBigListBenchmark {

    private static final int SIZE = 1_000_000;

    //private static final List<Monotonic<Integer>> MONOTONIC_LAZY = randomMono(Monotonic.ofList(SIZE));
    private static final List<Lazy<Integer>> LAZY_EAGER = randomLazy(Stream.generate(Lazy::<Integer>of)
            .limit(SIZE)
            .toList());

    private static final List<Integer> ARRAY_LIST = random(new ArrayList<>(SIZE));

    //private final List<Monotonic<Integer>> monotonicLazy = randomMono(Monotonic.ofList(SIZE));
    private final List<Lazy<Integer>> lazyEager = randomLazy(IntStream.range(0, SIZE)
                     .mapToObj(_ -> Lazy.<Integer>of())
                     .toList());
    private final List<Integer> arrayList = random(new ArrayList<>(SIZE));

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
    public int instanceLazyEager() {
        int sum = 0;
        for (int i = 0; i < lazyEager.size(); i++) {
            sum += lazyEager.get(i).orThrow();
        }
        return sum;
    }

/*    @Benchmark
    public int instanceMonotonicLazy() {
        int sum = 0;
        for (int i = 0; i < monotonicLazy.size(); i++) {
            sum += monotonicLazy.get(i).orThrow();
        }
        return sum;
    }*/

    @Benchmark
    public int staticArrayList() {
        int sum = 0;
        for (int i = 0; i < ARRAY_LIST.size(); i++) {
            sum += ARRAY_LIST.get(i);
        }
        return sum;
    }

    @Benchmark
    public int staticLazyEager() {
        int sum = 0;
        for (int i = 0; i < LAZY_EAGER.size(); i++) {
            sum += LAZY_EAGER.get(i).orThrow();
        }
        return sum;
    }

/*    @Benchmark
    public int staticMonotonicLazy() {
        int sum = 0;
        for (int i = 0; i < MONOTONIC_LAZY.size(); i++) {
            sum += MONOTONIC_LAZY.get(i).orThrow();
        }
        return sum;
    }*/

    private static List<Lazy<Integer>> randomLazy(List<Lazy<Integer>> list) {
        Random rnd = new Random();
        for (int i = 0; i < SIZE; i++) {
            list.get(i).setOrThrow(rnd.nextInt(0, SIZE));
        }
        return list;
    }

    private static List<Integer> random(List<Integer> list) {
        Random rnd = new Random();
        for (int i = 0; i < SIZE; i++) {
            list.add(rnd.nextInt(0, Integer.SIZE));
        }
        return list;
    }

}
