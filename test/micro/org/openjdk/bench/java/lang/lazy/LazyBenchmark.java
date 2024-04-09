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

package org.openjdk.bench.java.lang.lazy;

import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Benchmark measuring Lazy performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = "--enable-preview")
/* 2024-04-02
Nullable

Benchmark                             Mode  Cnt  Score   Error  Units
MonotonicBenchmark.instanceDCL        avgt   10  1.477 ? 0.124  ns/op
MonotonicBenchmark.instanceMonotonic  avgt   10  0.930 ? 0.038  ns/op <- 40% faster than DCL
MonotonicBenchmark.staticCHI          avgt   10  0.567 ? 0.015  ns/op
MonotonicBenchmark.staticDCL          avgt   10  1.223 ? 0.002  ns/op
MonotonicBenchmark.staticMonotonic    avgt   10  0.560 ? 0.004  ns/op <- 54% faster than DCL

Non-nullable

Benchmark                             Mode  Cnt  Score   Error  Units
MonotonicBenchmark.instanceDCL        avgt   10  1.489 ? 0.096  ns/op
MonotonicBenchmark.instanceMonotonic  avgt   10  0.901 ? 0.014  ns/op <- Only 3% improvement
MonotonicBenchmark.staticCHI          avgt   10  0.577 ? 0.048  ns/op
MonotonicBenchmark.staticDCL          avgt   10  1.238 ? 0.042  ns/op
MonotonicBenchmark.staticMonotonic    avgt   10  0.576 ? 0.044  ns/op

 */
public class LazyBenchmark {

    private static final int VALUE = 42;

    private static final LazyValue<Integer> LAZY = init(LazyValue.of());
    private static final Supplier<Integer> DCL = new Dcl<>(() -> VALUE);
    private static final List<LazyValue<Integer>> LIST = LazyValue.ofList(1);

    private final LazyValue<Integer> lazy = init(LazyValue.of());
    private final Supplier<Integer> dcl = new Dcl<>(() -> VALUE);
    private final List<LazyValue<Integer>> list = LazyValue.ofList(1);

    static {
        LIST.getFirst().setOrThrow(VALUE);
    }

    @Setup
    public void setup() {
        list.getFirst().setOrThrow(VALUE);
    }

    @Benchmark
    public int staticLazy() {
        return LAZY.orThrow();
    }

    @Benchmark
    public int staticList() {
        return LIST.get(0).orThrow();
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
    public int instanceLazy() {
        return lazy.orThrow();
    }

    @Benchmark
    public int instanceList() {
        return list.get(0).orThrow();
    }

    @Benchmark
    public Integer instanceDCL() {
        return dcl.get();
    }

    private static LazyValue<Integer> init(LazyValue<Integer> m) {
        m.setOrThrow(VALUE);
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
