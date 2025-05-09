/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Benchmark measuring StableValue performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgsAppend = {
        "--enable-preview"
})
@Threads(Threads.MAX)   // Benchmark under contention
@OperationsPerInvocation(2)
public class StableValueBenchmark {

    private static final int VALUE = 42;
    private static final int VALUE2 = 23;

    private static final StableValue<Integer> STABLE = init(StableValue.of(), VALUE);
    private static final StableValue<Integer> STABLE2 = init(StableValue.of(), VALUE2);
    private static final StableValue<Integer> DCL = init(StableValue.of(), VALUE);
    private static final StableValue<Integer> DCL2 = init(StableValue.of(), VALUE2);
    private static final AtomicReference<Integer> ATOMIC = new AtomicReference<>(VALUE);
    private static final AtomicReference<Integer> ATOMIC2 = new AtomicReference<>(VALUE2);
    private static final Holder HOLDER = new Holder(VALUE);
    private static final Holder HOLDER2 = new Holder(VALUE2);
    private static final RecordHolder RECORD_HOLDER = new RecordHolder(VALUE);
    private static final RecordHolder RECORD_HOLDER2 = new RecordHolder(VALUE2);

    private final StableValue<Integer> stable = init(StableValue.of(), VALUE);
    private final StableValue<Integer> stable2 = init(StableValue.of(), VALUE2);
    private final StableValue<Integer> stableNull = StableValue.of();
    private final StableValue<Integer> stableNull2 = StableValue.of();
    private final Supplier<Integer> dcl = new Dcl<>(() -> VALUE);
    private final Supplier<Integer> dcl2 = new Dcl<>(() -> VALUE2);
    private final AtomicReference<Integer> atomic = new AtomicReference<>(VALUE);
    private final AtomicReference<Integer> atomic2 = new AtomicReference<>(VALUE2);
    private final Supplier<Integer> supplier = () -> VALUE;
    private final Supplier<Integer> supplier2 = () -> VALUE2;


    @Setup
    public void setup() {
        stableNull.trySet(null);
        stableNull2.trySet(null);
    }

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
        return stable.orElseThrow() + stable2.orElseThrow();
    }

    @Benchmark
    public int stableNull() {
        return (stableNull.orElseThrow() == null ? VALUE : VALUE2) + (stableNull2.orElseThrow() == null ? VALUE : VALUE2);
    }

    // Reference case
    @Benchmark
    public int refSupplier() {
        return supplier.get() + supplier2.get();
    }

    @Benchmark
    public int staticAtomic() {
        return ATOMIC.get() + ATOMIC2.get();
    }

    @Benchmark
    public int staticDcl() {
        return DCL.orElseThrow() + DCL2.orElseThrow();
    }

    @Benchmark
    public int staticHolder() {
        return HOLDER.get() + HOLDER2.get();
    }

    @Benchmark
    public int staticRecordHolder() {
        return RECORD_HOLDER.get() + RECORD_HOLDER2.get();
    }

    @Benchmark
    public int staticStable() {
        return STABLE.orElseThrow() + STABLE2.orElseThrow();
    }


    private static StableValue<Integer> init(StableValue<Integer> m, Integer value) {
        m.trySet(value);
        return m;
    }

    private static final class Holder {

        private final StableValue<Integer> delegate = StableValue.of();

        Holder(int value) {
            delegate.setOrThrow(value);
        }

        int get() {
            return delegate.orElseThrow();
        }

    }

    private record RecordHolder(StableValue<Integer> delegate) {

        RecordHolder(int value) {
            this(StableValue.of());
            delegate.setOrThrow(value);
        }

        int get() {
            return delegate.orElseThrow();
        }

    }


    // Handles null values
    public static class Dcl<V> implements Supplier<V> {

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
