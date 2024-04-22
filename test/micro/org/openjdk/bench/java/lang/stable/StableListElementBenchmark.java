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
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/**
 * Benchmark measuring stable list performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"--add-exports=java.base/jdk.internal.lang=ALL-UNNAMED", "--enable-preview"})
@Threads(8)     // Some contention
public class StableListElementBenchmark {

    private static final IntFunction<Integer> FUNCTION = i -> i;
    private static final int SIZE = 100;

    private static final List<StableValue<Integer>> STORED = Stream.generate(StableValue::<Integer>of)
            .limit(SIZE)
            .toList();
    private static final List<StableValue<Integer>> LIST = StableValue.ofList(SIZE);

    static {
        initLazy(STORED);
        initLazy(LIST);
    }

    private static final List<Integer> ARRAY_LIST = initList(new ArrayList<>(SIZE));

    //private final List<Monotonic<Integer>> referenceList = initMono(Monotonic.ofList(SIZE));
    private final List<Integer> arrayList = initList(new ArrayList<>(SIZE));
    private final List<StableValue<Integer>> storedList;
    private final List<StableValue<Integer>> list;


    public StableListElementBenchmark() {
        this.storedList = Stream.generate(StableValue::<Integer>of)
                .limit(SIZE)
                .toList();
        initLazy(storedList);
        list = StableValue.ofList(SIZE);
        initLazy(list);
    }

    @Setup
    public void setup() {
    }

    @Benchmark
    public int instanceArrayList() {
        return arrayList.get(8);
    }

    @Benchmark
    public int instanceList() {
        return list.get(8).orThrow();
    }

    @Benchmark
    public int instanceStored() {
        return storedList.get(8).orThrow();
    }

    @Benchmark
    public int staticArrayList() {
        return ARRAY_LIST.get(8);
    }

    @Benchmark
    public int staticList() {
        return LIST.get(8).orThrow();
    }

    @Benchmark
    public int staticStored() {
        return STORED.get(8).orThrow();
    }

    private static void initLazy(List<StableValue<Integer>> list) {
        int index = 8;
        list.get(index).setOrThrow(FUNCTION.apply(index));
    }

    private static List<Integer> initList(List<Integer> list) {
        for (int i = 0; i < 9; i++) {
            list.add(FUNCTION.apply(i));
        }
        return list;
    }

}
