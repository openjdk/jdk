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
import java.util.function.Supplier;

/**
 * Benchmark measuring memoized supplier performance
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgsAppend = {"--add-exports=java.base/jdk.internal.lang=ALL-UNNAMED", "--enable-preview"})
@Threads(Threads.MAX)   // Benchmark under contention
public class MemoizedSupplierBenchmark {

    private static final int ITERATIONS = 17;
    private static final int VALUE = 42;
    private static final int VALUE2 = 23;

    private static final Supplier<Integer> S = () -> VALUE;
    private static final Supplier<Integer> S2 = () -> VALUE2;

    private final Supplier<Integer> m = StableValue.memoizedSupplier(S);
    private final Supplier<Integer> m2 = StableValue.memoizedSupplier(S2);
    private final Supplier<Integer> dcl = new Dcl<>(S);
    private final Supplier<Integer> dcl2 = new Dcl<>(S);

    private static final Supplier<Integer> M = StableValue.memoizedSupplier(S);
    private static final Supplier<Integer> M2 = StableValue.memoizedSupplier(S2);
    private static final Supplier<Integer> DCL = new Dcl<>(S);
    private static final Supplier<Integer> DCL2 = new Dcl<>(S);

    @Benchmark
    public int dcl() {
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += dcl.get() + dcl2.get();
        }
        return sum;
    }

    @Benchmark
    public int memoized() {
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += m.get() + m2.get();
        }
        return sum;
    }

    @Benchmark
    public int sSupplier() {
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += S.get() + S2.get();
        }
        return sum;
    }


    @Benchmark
    public int sDcl() {
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += DCL.get() + DCL2.get();
        }
        return sum;
    }

    @Benchmark
    public int sMemoized() {
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += M.get() + M2.get();
        }
        return sum;
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
