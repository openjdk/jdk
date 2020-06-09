/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


package micro.benchmarks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class ConcurrentHashBenchmark extends BenchmarkBase {

    private static final int MULTIPLIER = 10;
    private static final int SIZE = 100000;
    private static final int PARALLELISM = 4;
    private static final Integer[] KEYS = new Integer[SIZE];
    private static final String[] STRKEYS = new String[SIZE];

    private ConcurrentHashMap<Integer, Integer> prefilledMap;
    private ConcurrentHashMap<String, String> prefilledStringMap;

    @Setup(Level.Trial)
    public void setupInts() {
        this.prefilledMap = new ConcurrentHashMap<>();
        for (int i = 0; i < SIZE; i++) {
            KEYS[i] = Integer.valueOf(i);
            Integer key = KEYS[i];
            this.prefilledMap.put(key, key);
        }
    }

    @Setup(Level.Trial)
    public void setupStrings() {
        this.prefilledStringMap = new ConcurrentHashMap<>();
        for (int i = 0; i < SIZE; i++) {
            STRKEYS[i] = String.valueOf(i);
            String key = STRKEYS[i];
            this.prefilledStringMap.put(key, key);
        }
    }

    private int keySum() {
        int sum = 0;
        for (int i = 0; i < SIZE * MULTIPLIER; i++) {
            Integer key = KEYS[i % SIZE];
            sum += prefilledMap.get(key);
        }
        return sum;
    }

    private int inParallel(Supplier<Integer> action) {
        final int[] results = new int[PARALLELISM];
        Thread[] threads = new Thread[PARALLELISM];
        for (int i = 0; i < PARALLELISM; i++) {
            final int index = i;
            threads[i] = new Thread() {
                @Override
                public void run() {
                    results[index] = action.get();
                }
            };
        }
        for (int i = 0; i < PARALLELISM; i++) {
            threads[i].start();
        }
        int result = 0;
        for (int i = 0; i < PARALLELISM; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            result += results[i];
        }
        return result;
    }

    @OutputTimeUnit(TimeUnit.SECONDS)
    @Benchmark
    public int sequentialGet() {
        return keySum();
    }

    @OutputTimeUnit(TimeUnit.SECONDS)
    @Benchmark
    public int parallelGet() {
        return inParallel(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return keySum();
            }
        });
    }

    private int keyHash() {
        int hash = 0;
        for (int i = 0; i < SIZE * MULTIPLIER; i++) {
            String key = STRKEYS[i % SIZE];
            hash ^= prefilledStringMap.get(key).hashCode();
        }
        return hash;
    }

    @OutputTimeUnit(TimeUnit.SECONDS)
    @Benchmark
    public int sequentialStringGet() {
        return keyHash();
    }

    @OutputTimeUnit(TimeUnit.SECONDS)
    @Benchmark
    public int parallelStringGet() {
        return inParallel(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return keyHash();
            }
        });
    }

    @OutputTimeUnit(TimeUnit.SECONDS)
    @Benchmark
    public int sequentialPut() {
        final ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        for (int i = 0; i < SIZE; i++) {
            Integer key = KEYS[i];
            map.put(key, key);
        }
        return map.get(SIZE / 2);
    }
}
