/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

package org.openjdk.bench.java.util;

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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 4, time = 2)
@Fork(value = 3)
public class HashMapBench {
    private Map<Integer, Integer> mapToAdd;
    private Map<Integer, Integer> mapToAddLinear;

    @Param({"0", "1", "100000"})
    private int size;

    @Param({"0", "100000"})
    private int addSize;

    @Param
    private MapType mapType;

    public enum MapType {
        HASH_MAP,
        LINKED_HASH_MAP,
    }

    private Map<Integer, Integer> getMap() {
        return getMap(0);
    }

    private Map<Integer, Integer> getMap(int size) {
        switch (mapType) {
        case HASH_MAP:
            return new HashMap<>(size);
        case LINKED_HASH_MAP:
            return new LinkedHashMap<>(size);
        default:
            throw new AssertionError();
        }
    }

    @Setup
    public void setup() {
        mapToAdd = getMap(addSize);
        mapToAddLinear = getMap(addSize);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < addSize; ++i) {
            int r = rnd.nextInt();
            mapToAdd.put(r, r);
            mapToAddLinear.put(i, i);
        }
    }

    @Benchmark
    public int putAll() {
        Map<Integer, Integer> map = getMap(size);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < size; ++i) {
            int r = rnd.nextInt();
            map.put(r, r);
        }
        map.putAll(mapToAdd);
        return map.size();
    }

    @Benchmark
    public int putAllSameKeys() {
        Map<Integer, Integer> map = getMap(size);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < size; ++i) {
            map.put(i, i);
        }
        map.putAll(mapToAddLinear);
        return map.size();
    }

    @Benchmark
    public int put() {
        Map<Integer, Integer> map = getMap();
        for (int k : mapToAdd.keySet()) {
            map.put(k, mapToAdd.get(k));
        }
        return map.size();
    }
}
