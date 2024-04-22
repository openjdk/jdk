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
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/*
Benchmark                       Mode  Cnt  Score   Error  Units
StableBenchmark.instanceAtomic  avgt   10  2.135 ? 0.238  ns/op
StableBenchmark.instanceDCL     avgt   10  1.833 ? 0.057  ns/op
StableBenchmark.instanceList    avgt   10  1.629 ? 0.040  ns/op
StableBenchmark.instanceStable  avgt   10  1.201 ? 0.097  ns/op
StableBenchmark.staticAtomic    avgt   10  1.612 ? 0.210  ns/op
StableBenchmark.staticCHI       avgt   10  0.762 ? 0.116  ns/op
StableBenchmark.staticDCL       avgt   10  1.538 ? 0.081  ns/op
StableBenchmark.staticList      avgt   10  0.716 ? 0.069  ns/op
StableBenchmark.staticStable    avgt   10  0.722 ? 0.051  ns/op
 */

/**
 * Benchmark measuring StableValue performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"--add-exports=java.base/jdk.internal.lang=ALL-UNNAMED", "--enable-preview"})
@Threads(8)     // Some contention
public class StableBenchmark {

    private static final int VALUE = 42;

    private static final StableValue<Integer> STABLE = init(StableValue.of());
    private static final Supplier<Integer> DCL = new Dcl<>(() -> VALUE);
    private static final List<StableValue<Integer>> LIST = StableValue.ofList(1);
    private static final AtomicReference<Integer> ATOMIC = new AtomicReference<>(VALUE);

    private final StableValue<Integer> stable = init(StableValue.of());
    private final Supplier<Integer> dcl = new Dcl<>(() -> VALUE);
    private final List<StableValue<Integer>> list = StableValue.ofList(1);
    private final AtomicReference<Integer> atomic = new AtomicReference<>(VALUE);

    static {
        LIST.getFirst().setOrThrow(VALUE);
    }

    @Setup
    public void setup() {
        list.getFirst().setOrThrow(VALUE);
    }

    @Benchmark
    public int staticAtomic() {
        return ATOMIC.get();
    }

    @Benchmark
    public int staticStable() {
        return STABLE.orThrow();
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
    public int instanceAtomic() {
        return atomic.get();
    }

    @Benchmark
    public Integer instanceDCL() {
        return dcl.get();
    }

    @Benchmark
    public int instanceList() {
        return list.get(0).orThrow();
    }

    @Benchmark
    public int instanceStable() {
        return stable.orThrow();
    }

    private static StableValue<Integer> init(StableValue<Integer> m) {
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
