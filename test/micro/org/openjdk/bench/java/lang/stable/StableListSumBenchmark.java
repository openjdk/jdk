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
import java.util.Random;
import java.util.concurrent.TimeUnit;
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
@OperationsPerInvocation(1000)
public class StableListSumBenchmark {

    private static final int SIZE = 1_000;

    private static final List<StableValue<Integer>> STORED = randomStable(Stream.generate(StableValue::<Integer>of)
            .limit(SIZE)
            .toList());

    private static final List<Integer> ARRAY_LIST = random(new ArrayList<>(SIZE));
    private static final List<StableValue<Integer>> LIST = randomStable(StableValue.ofList(SIZE));


    private final List<StableValue<Integer>> stored = randomStable(Stream.generate(StableValue::<Integer>of)
            .limit(SIZE)
            .toList());
    private final List<Integer> arrayList = random(new ArrayList<>(SIZE));
    private final List<StableValue<Integer>> list = randomStable(StableValue.ofList(SIZE));

    @Setup
    public void setup() {
    }

    @Benchmark
    public int instanceArrayList() {
        int sum = 0;
        for (int i = 0; i < arrayList.size(); i++) {
            sum += arrayList.get(i);
        }
        return sum;
    }

    @Benchmark
    public int instanceList() {
        int sum = 0;
        for (int i = 0; i < list.size(); i++) {
            sum += list.get(i).orThrow();
        }
        return sum;
    }

    @Benchmark
    public int instanceStored() {
        int sum = 0;
        for (int i = 0; i < stored.size(); i++) {
            sum += stored.get(i).orThrow();
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
    public int staticList() {
        int sum = 0;
        for (int i = 0; i < LIST.size(); i++) {
            sum += LIST.get(i).orThrow();
        }
        return sum;
    }

    @Benchmark
    public int staticStored() {
        int sum = 0;
        for (int i = 0; i < STORED.size(); i++) {
            sum += STORED.get(i).orThrow();
        }
        return sum;
    }

    private static List<StableValue<Integer>> randomStable(List<StableValue<Integer>> list) {
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
