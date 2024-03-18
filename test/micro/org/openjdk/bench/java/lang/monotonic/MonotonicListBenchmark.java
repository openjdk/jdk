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
@Fork(value = 2)
public class MonotonicListBenchmark {

    private static final int SIZE = 100;

    private static final List<Monotonic<Integer>> WRAPPED =
            IntStream.range(0, SIZE)
                    .mapToObj(_ -> Monotonic.<Integer>of())
                    .toList();
    static {
        WRAPPED.get(8).bind(8);
    }

    private static final List<Monotonic<Integer>> MONOTONIC_LIST = initMono(Monotonic.ofList(SIZE));
    private static final List<Integer> ARRAY_LIST = initList(new ArrayList<>(SIZE));

    private final List<Monotonic<Integer>> referenceList = initMono(Monotonic.ofList(SIZE));
    private final List<Integer> arrayList = initList(new ArrayList<>(SIZE));
    private final List<Monotonic<Integer>> wrappedList;

    public MonotonicListBenchmark() {
        this.wrappedList = IntStream.range(0, SIZE)
                .mapToObj(i -> Monotonic.<Integer>of())
                .toList();
        wrappedList.get(8).bind(8);
    }

    @Setup
    public void setup() {
    }

    @Benchmark
    public Integer staticMonotonic() {
        return MONOTONIC_LIST.get(8).get();
    }

    @Benchmark
    public Integer staticArrayList() {
        return ARRAY_LIST.get(8);
    }

    @Benchmark
    public Integer instanceMonotonic() {
        return referenceList.get(8).get();
    }

    @Benchmark
    public Integer instanceArrayList() {
        return arrayList.get(8);
    }

    @Benchmark
    public Integer instanceWrapped() {
        return wrappedList.get(8).get();
    }

    private static List<Monotonic<Integer>> initMono(List<Monotonic<Integer>> list) {
        list.get(8).bind(8);
        return list;
    }

    private static List<Integer> initList(List<Integer> list) {
        for (int i = 0; i < 9; i++) {
            list.add(i);

        }
        return list;
    }

}
