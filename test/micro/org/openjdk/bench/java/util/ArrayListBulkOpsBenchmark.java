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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmark measuring ArrayList addAll() performance.
 *
 * Tests the performance of ArrayList.addAll() when copying from another ArrayList.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = { "-XX:+UseParallelGC", "-Xmx3g" })
public class ArrayListBulkOpsBenchmark {

    @Param({ "false", "true" })
    private boolean enablePoisoning;

    private ArrayList<String> arrayListSource0;
    private ArrayList<String> arrayListSource5;
    private ArrayList<String> arrayListSource75;
    private LinkedList<String> linkedListSource0;
    private LinkedList<String> linkedListSource5;
    private LinkedList<String> linkedListSource75;
    private Set<String> singletonSetSource;
    private HashMap<String, String> hashMapSource;
    private TreeSet<String> treeSetSource;
    private WeakHashMap<String, String> weakHashMapSource;

    @Benchmark
    public ArrayList<String> addAllArrayList0(Blackhole bh) {
        ArrayList<String> result = new ArrayList<>(0);
        result.addAll(arrayListSource0);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public ArrayList<String> addAllArrayList5(Blackhole bh) {
        ArrayList<String> result = new ArrayList<>(5);
        result.addAll(arrayListSource5);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public ArrayList<String> addAllArrayList75(Blackhole bh) {
        ArrayList<String> result = new ArrayList<>(75);
        result.addAll(arrayListSource75);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public ArrayList<String> addAllLinkedList0(Blackhole bh) {
        ArrayList<String> result = new ArrayList<>(0);
        result.addAll(linkedListSource0);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public ArrayList<String> addAllLinkedList5(Blackhole bh) {
        ArrayList<String> result = new ArrayList<>(5);
        result.addAll(linkedListSource5);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public ArrayList<String> addAllLinkedList75(Blackhole bh) {
        ArrayList<String> result = new ArrayList<>(75);
        result.addAll(linkedListSource75);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public ArrayList<String> addAllSingletonSet(Blackhole bh) {
        ArrayList<String> result = new ArrayList<>(1);
        result.addAll(singletonSetSource);
        bh.consume(result);
        return result;
    }

    @Setup(Level.Trial)
    public void setup() {
        // Create source collections of different sizes
        arrayListSource0 = new ArrayList<>();
        arrayListSource5 = new ArrayList<>();
        arrayListSource75 = new ArrayList<>();
        linkedListSource0 = new LinkedList<>();
        linkedListSource5 = new LinkedList<>();
        linkedListSource75 = new LinkedList<>();

        for (int i = 0; i < 5; i++) {
            arrayListSource5.add("key" + i);
            linkedListSource5.add("key" + i);
        }

        for (int i = 0; i < 75; i++) {
            arrayListSource75.add("key" + i);
            linkedListSource75.add("key" + i);
        }

        // SingletonSet always contains exactly one element
        singletonSetSource = Collections.singleton("key0");

        // Create poisoning collections
        hashMapSource = new HashMap<>();
        treeSetSource = new TreeSet<>();
        weakHashMapSource = new WeakHashMap<>();
        for (int i = 0; i < 75; i++) {
            hashMapSource.put("key" + i, "value" + i);
            treeSetSource.add("key" + i);
            weakHashMapSource.put("key" + i, "value" + i);
        }

        if (enablePoisoning) {
            poisonCallSites();
        }
    }

    private void poisonCallSites() {
        // Poison ArrayList.addAll() with different Collection types
        for (int i = 0; i < 40000; i++) {
            ArrayList<Object> temp = new ArrayList<>();
            temp.addAll(hashMapSource.entrySet());
            temp.addAll(treeSetSource);
            temp.addAll(weakHashMapSource.keySet());
        }
    }
}
