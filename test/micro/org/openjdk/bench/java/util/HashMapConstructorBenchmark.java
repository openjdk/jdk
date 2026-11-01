/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark comparing HashMap constructor performance against manual iteration - see
 * JDK-8368292 for details of the targeted megamorphic issue and JDK-8371656 for details
 * of the special-case optimization.
 *
 * Tests HashMap.<init>(Map) performance across different source map types, with and without
 * call site poisoning to simulate real-world megamorphic conditions.
 *
 * Uses BigInteger keys whose hashCode() is not cached and scales with magnitude,
 * exposing the cost of hash recomputation in non-optimized paths.
 *
 * The setup poisons polymorphic call sites by using ten different map types
 * in both the constructor and manual iteration patterns to ensure megamorphic behavior.
 *
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {"-XX:+UseParallelGC", "-Xmx3g"})
public class HashMapConstructorBenchmark {

    private static final int POISON_ITERATIONS = 80000;

    @Param({"0", "5", "25", "150"})
    private int mapSize;

    @Param({"true", "false"})
    private boolean poisonCallSites;

    @Param({"HashMap", "TreeMap", "ConcurrentHashMap", "UnmodifiableMap(HashMap)", "UnmodifiableMap(TreeMap)"})
    private String inputType;

    private HashMap<BigInteger, Integer> inputHashMap;
    private TreeMap<BigInteger, Integer> inputTreeMap;
    private LinkedHashMap<BigInteger, Integer> inputLinkedHashMap;
    private ConcurrentHashMap<BigInteger, Integer> inputConcurrentHashMap;
    private WeakHashMap<BigInteger, Integer> inputWeakHashMap;
    private Map<BigInteger, Integer> inputUnmodifiableMap;
    private Map<BigInteger, Integer> inputUnmodifiableTreeMap;

    private Map<BigInteger, Integer> sourceMap;

    @Setup(Level.Trial)
    public void setup(Blackhole bh) {
        Random rng = new Random(0);

        inputHashMap = new HashMap<>();
        inputTreeMap = new TreeMap<>();
        inputLinkedHashMap = new LinkedHashMap<>();
        inputConcurrentHashMap = new ConcurrentHashMap<>();
        inputWeakHashMap = new WeakHashMap<>();

        for (int i = 0; i < mapSize; i++) {
            BigInteger key = new BigInteger(128, rng);
            Integer value = i;
            inputHashMap.put(key, value);
            inputTreeMap.put(key, value);
            inputLinkedHashMap.put(key, value);
            inputConcurrentHashMap.put(key, value);
            inputWeakHashMap.put(key, value);
        }

        inputUnmodifiableMap = Collections.unmodifiableMap(new HashMap<>(inputHashMap));
        inputUnmodifiableTreeMap = Collections.unmodifiableMap(new TreeMap<>(inputTreeMap));

        sourceMap = switch (inputType) {
            case "HashMap" -> inputHashMap;
            case "TreeMap" -> inputTreeMap;
            case "ConcurrentHashMap" -> inputConcurrentHashMap;
            case "UnmodifiableMap(HashMap)" -> inputUnmodifiableMap;
            case "UnmodifiableMap(TreeMap)" -> inputUnmodifiableTreeMap;
            default -> throw new IllegalArgumentException("Unknown inputType: " + inputType);
        };

        if (poisonCallSites) {
            poisonCallSites(bh);
        }
    }

    private void poisonCallSites(Blackhole bh) {
        List<Map<BigInteger, Integer>> sources = List.of(inputHashMap,
                inputTreeMap,
                inputLinkedHashMap,
                inputConcurrentHashMap,
                inputWeakHashMap,
                inputUnmodifiableMap,
                inputUnmodifiableTreeMap,
                Collections.unmodifiableMap(inputLinkedHashMap),
                Collections.unmodifiableMap(inputConcurrentHashMap),
                Collections.unmodifiableMap(inputWeakHashMap));

        // Poison HashMap.<init>(Map) call site
        for (int i = 0; i < POISON_ITERATIONS; i++) {
            Map<BigInteger, Integer> source = sources.get(i % sources.size());
            HashMap<BigInteger, Integer> temp = new HashMap<>(source);
            bh.consume(temp);
        }

        // Poison entrySet iteration call sites
        for (int i = 0; i < POISON_ITERATIONS; i++) {
            Map<BigInteger, Integer> source = sources.get(i % sources.size());
            HashMap<BigInteger, Integer> temp = HashMap.newHashMap(source.size());
            for (Map.Entry<BigInteger, Integer> entry : source.entrySet()) {
                temp.put(entry.getKey(), entry.getValue());
            }
            bh.consume(temp);
        }
    }

    /**
     * Benchmark using HashMap's built-in constructor that takes a Map parameter.
     * Performance varies based on source map type and call site polymorphism.
     */
    @Benchmark
    public HashMap<BigInteger, Integer> hashMapConstructor() {
        return new HashMap<>(sourceMap);
    }

    /**
     * Benchmark using manual iteration over entrySet with individual put() calls.
     * This approach bypasses bulk operations and their polymorphic call sites.
     */
    @Benchmark
    public HashMap<BigInteger, Integer> manualEntrySetLoop() {
        HashMap<BigInteger, Integer> result = HashMap.newHashMap(sourceMap.size());
        for (Map.Entry<BigInteger, Integer> entry : sourceMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
