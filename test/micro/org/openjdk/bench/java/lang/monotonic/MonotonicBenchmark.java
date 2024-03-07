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

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Benchmark measuring monotonic list performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2)
public class MonotonicBenchmark {

    private static final int VALUE = 42;

    private static final Monotonic<Integer> MONOTONIC = init(Monotonic.of());
    private static final Supplier<Integer> DCL = new Dcl<>(() -> VALUE);

    private final Monotonic<Integer> monotonic = init(Monotonic.of());
    private final Supplier<Integer> dcl = new Dcl<>(() -> VALUE);

    @Setup
    public void setup() {
    }

    @Benchmark
    public int staticMonotonic() {
        return MONOTONIC.get();
    }

    @Benchmark
    public int staticCHI() {
        class Holder {
            static final int VALUE = 42;
        }
        return Holder.VALUE;
    }

    @Benchmark
    public int staticDCL() {
        return DCL.get();
    }


    @Benchmark
    public int instanceMonotonic() {
        return monotonic.get();
    }

    @Benchmark
    public Integer instanceDCL() {
        return dcl.get();
    }

    private static Monotonic<Integer> init(Monotonic<Integer> m) {
        m.bind(VALUE);
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
