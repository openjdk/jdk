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
import java.util.List;
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
    @Param({"0", "1", "5", "75"})
    int size;

    @Param({"ArrayList", "LinkedList"})
    String type;

    List<String> source;

    @Setup(Level.Trial)
    public void setup() {
        switch (type) {
            case "ArrayList" -> source = new ArrayList<>(size);
            case "LinkedList" -> source = new LinkedList<>();
        }
        for (int i = 0; i < size; i++) source.add("key" + i);
    }

    @Benchmark
    public ArrayList<String> addAll() {
        ArrayList<String> result = new ArrayList<>(size);
        result.addAll(source);
        return result;
    }

    static void poisonCallSites() {
        HashMap<String, String> hashMapSource = new HashMap<>();
        TreeSet<String> treeSetSource = new TreeSet<>();
        WeakHashMap<String, String> weakHashMapSource = new WeakHashMap<>();
        for (int i = 0; i < 75; i++) {
            hashMapSource.put("key" + i, "value" + i);
            treeSetSource.add("key" + i);
            weakHashMapSource.put("key" + i, "value" + i);
        }
        // Poison ArrayList.addAll() with different Collection types
        for (int i = 0; i < 40_000; i++) {
            ArrayList<Object> temp = new ArrayList<>();
            temp.addAll(hashMapSource.entrySet());
            temp.addAll(treeSetSource);
            temp.addAll(weakHashMapSource.keySet());
        }
    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @State(Scope.Benchmark)
    @Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
    @Fork(value = 1, jvmArgs = { "-XX:+UseParallelGC", "-Xmx3g" })
    public static class SingletonSet {
        Set<String> singletonSetSource = Collections.singleton("key");

        @Param({ "false", "true" })
        private boolean poison;

        @Setup(Level.Trial)
        public void setup() {
            if (poison) poisonCallSites();
        }

        @Benchmark
        public ArrayList<String> addAllSingletonSet() {
            ArrayList<String> result = new ArrayList<>(1);
            result.addAll(singletonSetSource);
            return result;
        }
    }
}
