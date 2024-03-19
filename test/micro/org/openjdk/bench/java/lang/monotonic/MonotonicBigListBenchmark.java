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

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Benchmark measuring monotonic list performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = "--enable-preview")
public class MonotonicBigListBenchmark {

    private static final int SIZE = 100_000;

    private static final List<Monotonic<Integer>> MONOTONIC_LIST = randomMono(Monotonic.ofList(SIZE));
    private static final List<Integer> ARRAY_LIST = random(new ArrayList<>(SIZE));

    private final List<Monotonic<Integer>> monotonicList = randomMono(Monotonic.ofList(SIZE));
    private final List<Monotonic<Integer>> monotonicList2 = randomMono(IntStream.range(0, SIZE)
                     .mapToObj(_ -> Monotonic.<Integer>of())
                     .toList());
    private static final List<Integer> arrayList = random(new ArrayList<>(SIZE));

    @Setup
    public void setup() {
    }

    @Benchmark
    public int staticMonotonic() {
        int sum = 0;
        for (int i = 0; i < MONOTONIC_LIST.size(); i++) {
            sum += MONOTONIC_LIST.get(i).get();
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
    public int instanceMonotonic() {
        int sum = 0;
        for (int i = 0; i < monotonicList.size(); i++) {
            sum += monotonicList.get(i).get();
        }
        return sum;
    }

    @Benchmark
    public int instanceMonotonic2() {
        int sum = 0;
        for (int i = 0; i < monotonicList2.size(); i++) {
            sum += monotonicList2.get(i).get();
        }
        return sum;
    }

    @Benchmark
    public Integer instanceArrayList() {
        int sum = 0;
        for (int i = 0; i < arrayList.size(); i++) {
            sum += arrayList.get(i);
        }
        return sum;
    }

    private static List<Monotonic<Integer>> randomMono(List<Monotonic<Integer>> list) {
        Random rnd = new Random();
        for (int i = 0; i < SIZE; i++) {
            list.get(i).bind(rnd.nextInt(0, SIZE));
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
