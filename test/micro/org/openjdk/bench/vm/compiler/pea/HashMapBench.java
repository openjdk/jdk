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

package org.openjdk.bench.vm.compiler.pea;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.HashMap;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 3)
public class HashMapBench {
    class Key {
        int x;
        Key(int x) { this.x = x; }
        public int hashCode() { return x; }
        public boolean equals(Object other) { return this.hashCode() == other.hashCode(); }
    }

    static void blackhole(Object o) {}

    @Param("1024")
    private int size;

    @Param({"1", "2", "4"})
    private int fillInterval;

    ArrayList<Key> keys;
    HashMap<Key, Object> map;

    @Setup
    public void setUp() {
        keys = new ArrayList<>();
        map = new HashMap<>();
        for (int i = 0; i < size; ++i) {
            keys.add(new Key(i));
            if (i % fillInterval == 0) {
                map.put(new Key(i), new Object());
            }
        }
    }

    @Benchmark
    public void replace() {
        for (int i = 0; i < size; ++i) {
            map.replace(keys.get(i), new Object());
        }
    }

    @Benchmark
    public void computeIfAbsent() {
        for (int i = 0; i < size; ++i) {
            map.computeIfAbsent(new Key(i), key -> new Object());
        }
    }

    @Benchmark
    public void cacheUpdate() {
        for (int i = 0; i < size; ++i) {
            Key key = new Key(i);
            if (!map.containsKey(key)) {
                map.put(key, new Object());
            }
        }
    }
}

