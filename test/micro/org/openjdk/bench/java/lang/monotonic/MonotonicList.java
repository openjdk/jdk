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
public class MonotonicList {

    private static final int SIZE = 100;

    static class MonotonicHolder {

        private final Monotonic.List<Integer> list;

        public MonotonicHolder(Monotonic.List<Integer> list) {
            this.list = list;
        }

        Integer get(int index) {
            return list.get(index);
        }
    }

    static class Holder {

        private final List<Integer> list;

        public Holder(List<Integer> list) {
            this.list = list;
        }

        Integer get(int index) {
            return list.get(index);
        }
    }

    private static final List<Monotonic<Integer>> WRAPPED =
            IntStream.range(0, SIZE)
                    .mapToObj(i -> Monotonic.of(int.class))
                    .toList();
    static {
        WRAPPED.get(8).put(8);
    }

    private static final Monotonic.List<Integer> PRIMITIVE_LIST = initList(Monotonic.ofList(int.class, SIZE));
    private static final Monotonic.List<Integer> REFERENCE_LIST = initList(Monotonic.ofList(Integer.class, SIZE));
    private static final List<Integer> ARRAY_LIST = initList(new ArrayList<>(SIZE));

    private static final MonotonicHolder PRIMITIVE_HOLDER = new MonotonicHolder(initList(Monotonic.ofList(int.class, SIZE)));
    private static final MonotonicHolder REFERENCE_HOLDER = new MonotonicHolder(initList(Monotonic.ofList(Integer.class, SIZE)));
    private static final Holder ARRAY_HOLDER = new Holder(initList(new ArrayList<>(SIZE)));

    private final Monotonic.List<Integer> primitiveList = initList(Monotonic.ofList(int.class, SIZE));
    private final Monotonic.List<Integer> referenceList = initList(Monotonic.ofList(Integer.class, SIZE));
    private final List<Integer> arrayList = initList(new ArrayList<>(SIZE));
    private final List<Monotonic<Integer>> wrappedList;

    public MonotonicList() {
        this.wrappedList = IntStream.range(0, SIZE)
                .mapToObj(i -> Monotonic.of(int.class))
                .toList();
        wrappedList.get(8).put(8);
    }

    @Setup
    public void setup() {
    }

    @Benchmark
    public int staticGetPrimitive() {
        return PRIMITIVE_LIST.get(8);
    }

    @Benchmark
    public Integer staticGetReference() {
        return REFERENCE_LIST.get(8);
    }

    @Benchmark
    public Integer staticGetArrayList() {
        return ARRAY_LIST.get(8);
    }

    @Benchmark
    public int staticWrapped() {
        return PRIMITIVE_LIST.get(8);
    }

    @Benchmark
    public int holderGetPrimitive() {
        return PRIMITIVE_HOLDER.get(8);
    }

    @Benchmark
    public Integer holderGetReference() {
        return REFERENCE_HOLDER.get(8);
    }

    @Benchmark
    public Integer holderGetArrayList() {
        return ARRAY_HOLDER.get(8);
    }

    @Benchmark
    public int instanceGetPrimitive() {
        return primitiveList.get(8);
    }

    @Benchmark
    public Integer instanceGetReference() {
        return referenceList.get(8);
    }

    @Benchmark
    public Integer instanceGetArrayList() {
        return arrayList.get(8);
    }

    @Benchmark
    public Integer instanceWrapped() {
        return wrappedList.get(8).get();
    }

    private static <T extends List<Integer>> T initList(T list) {
        for (int i = list.size(); i < SIZE; i++) {
            list.add(null);
        }
        list.set(8, 8);
        return list;
    }

}
