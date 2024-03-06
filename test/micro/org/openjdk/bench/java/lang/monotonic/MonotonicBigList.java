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

/**
 * Benchmark measuring monotonic list performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2)
public class MonotonicBigList {

    private static final int SIZE = 100_000;

    private static final Monotonic.List<Integer> RANDOM_INDICES = random(Monotonic.ofList(int.class, SIZE));

    private static final Monotonic.List<Integer> PRIMITIVE_LIST = random(Monotonic.ofList(int.class, SIZE));
    private static final List<Integer> ARRAY_LIST = random(new ArrayList<>(SIZE));

    private final Monotonic.List<Integer> primitiveList = random(Monotonic.ofList(int.class, SIZE));
    private static final List<Integer> arrayList = random(new ArrayList<>(SIZE));

    @Setup
    public void setup() {
    }

    @Benchmark
    public int staticGetPrimitive() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += PRIMITIVE_LIST.get(RANDOM_INDICES.get(i));
        }
        return sum;
    }

    @Benchmark
    public int staticGetArrayList() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += ARRAY_LIST.get(RANDOM_INDICES.get(i));
        }
        return sum;
    }


    @Benchmark
    public int instancePrimitiveList() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += primitiveList.get(RANDOM_INDICES.get(i));
        }
        return sum;
    }


    @Benchmark
    public Integer instanceGetArrayList() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += arrayList.get(RANDOM_INDICES.get(i));
        }
        return sum;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Integer, T extends List<E>> T random(T list) {
        Random rnd = new Random();
        if (list instanceof Monotonic.List<?> ml) {
            for (int i = 0; i < SIZE; i++) {
                ((Monotonic.List<Integer>) ml).put(i, rnd.nextInt(0, SIZE));
            }
        } else {
            for (int i = 0; i < SIZE; i++) {
                ((List<Integer>) list).add(rnd.nextInt(0, Integer.SIZE));
            }
        }
        return list;
    }

}
