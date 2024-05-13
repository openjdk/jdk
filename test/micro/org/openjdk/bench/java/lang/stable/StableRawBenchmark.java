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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Benchmark measuring StableValue raw performance in instance contexts
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgsAppend = {"--add-exports=java.base/jdk.internal.lang=ALL-UNNAMED", "--enable-preview"})
@Threads(Threads.MAX)   // Benchmark under contention
public class StableRawBenchmark {

    private static final int VALUE = 42;
    private static final int VALUE2 = 13;

    // Use two distinct values to prevent inlining of values

    private final StableValue<Integer> stable = init(StableValue.of(), VALUE);
    private final StableValue<Integer> stable2 = init(StableValue.of(), VALUE2);
    private final Supplier<Integer> dcl = new Dcl<>(() -> VALUE);
    private final Supplier<Integer> dcl2 = new Dcl<>(() -> VALUE2);
    private final AtomicReference<Integer> atomic = new AtomicReference<>(VALUE);
    private final AtomicReference<Integer> atomic2 = new AtomicReference<>(VALUE2);

    @Benchmark
    public int atomic() {
        return atomic.get() + atomic2.get();
    }

    @Benchmark
    public int dcl() {
        return dcl.get() + dcl2.get();
    }

    @Benchmark
    public int stable() {
        return stable.orThrow() + stable2.orThrow();
    }

    private static StableValue<Integer> init(StableValue<Integer> m, Integer value) {
        m.trySet(value);
        return m;
    }

    // Handles null values
    private static class Dcl<V> implements Supplier<V> {

        private final Supplier<V> supplier;

        private volatile V value;
        private boolean bound;

        public Dcl(Supplier<V> supplier) {
            this.supplier = supplier;
        }

        @Override
        public V get() {
            V v = value;
            if (v == null) {
                if (!bound) {
                    synchronized (this) {
                        v = value;
                        if (v == null) {
                            if (!bound) {
                                value = v = supplier.get();
                                bound = true;
                            }
                        }
                    }
                }
            }
            return v;
        }
    }

}
