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

package org.openjdk.bench.valhalla.sandbox.corelibs.corelibs.mapprotos;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;

/**
 * Benchmark                                    (mapType)   (size)  Mode  Cnt    Score    Error  Units
 * XHashMapBench.put                             HASH_MAP  1000000  avgt    5  214.470 +/- 44.063  ms/op
 * XHashMapBench.put                            XHASH_MAP  1000000  avgt    5  215.772 +/- 31.595  ms/op
 * XHashMapBench.putAllWithBigMapToEmptyMap      HASH_MAP  1000000  avgt    5  126.472 +/- 38.452  ms/op
 * XHashMapBench.putAllWithBigMapToEmptyMap     XHASH_MAP  1000000  avgt    5  117.741 +/- 10.460  ms/op
 * XHashMapBench.putAllWithBigMapToNonEmptyMap   HASH_MAP  1000000  avgt    5  136.112 +/- 36.712  ms/op
 * XHashMapBench.putAllWithBigMapToNonEmptyMap  XHASH_MAP  1000000  avgt    5  144.681 +/-  8.755  ms/op
 * Finished running test 'micro:valhalla.corelibs.XHashMapBench'
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
public class HashMapBench {
    private IntFunction<Map<Integer, Integer>> mapSupplier;
    private Map<Integer, Integer> bigMapToAdd;

    @Param("1000000")
    private int size;

    @Param(value = {
            "org.openjdk.bench.valhalla.corelibs.mapprotos.HashMap",
//            "org.openjdk.bench.valhalla.corelibs.mapprotos.XHashMap",
            "java.util.HashMap",
        })
    private String mapType;

    @Setup
    public void setup() {
        try {
            Class<?> mapClass = Class.forName(mapType);
            mapSupplier =  (size) -> newInstance(mapClass, size);
        } catch (Exception ex) {
            System.out.printf("%s: %s%n", mapType, ex.getMessage());
            return;
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        this.bigMapToAdd = IntStream.range(0, size).boxed()
            .collect(toMap(i -> 7 + i * 128, i -> rnd.nextInt()));
    }

    Map<Integer, Integer> newInstance(Class<?> mapClass, int size) {
        try {
            return (Map<Integer, Integer>)mapClass.getConstructor(int.class).newInstance(size);
        } catch (Exception ex) {
            throw new RuntimeException("failed", ex);
        }
    }

    @Benchmark
    public int putAllWithBigMapToNonEmptyMap() {
        Map<Integer, Integer> map = mapSupplier.apply(16);
        map.put(-1, -1);
        map.putAll(bigMapToAdd);
        return map.size();
    }

    @Benchmark
    public int putAllWithBigMapToEmptyMap() {
        Map<Integer, Integer> map = mapSupplier.apply(16);
        map.putAll(bigMapToAdd);
        return map.size();
    }

    @Benchmark
    public int put() {
        Map<Integer, Integer> map = mapSupplier.apply(16);
        for (int k : bigMapToAdd.keySet()) {
            map.put(k, bigMapToAdd.get(k));
        }
        return map.size();
    }
}
