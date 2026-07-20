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

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.LazyConstant;
import java.util.function.Supplier;

/**
 * Benchmark measuring lazy constant performance
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

    private static final LazyConstant<Integer> STABLE = init(VALUE);
    private static final LazyConstant<Integer> STABLE2 = init(VALUE2);
    private static final Supplier<Integer> DCL = new Dcl<>(() -> VALUE);
    private static final Supplier<Integer> DCL2 = new Dcl<>(() -> VALUE2);
    private static final AtomicReference<Integer> ATOMIC = new AtomicReference<>(VALUE);
    private static final AtomicReference<Integer> ATOMIC2 = new AtomicReference<>(VALUE2);
    private static final Holder HOLDER = new Holder(VALUE);
    private static final Holder HOLDER2 = new Holder(VALUE2);
    private static final RecordHolder RECORD_HOLDER = new RecordHolder(VALUE);
    private static final RecordHolder RECORD_HOLDER2 = new RecordHolder(VALUE2);

    private static final LazyConstant<Optional<Integer>> OPTIONAL_42 = LazyConstant.of(() -> Optional.of(42));
    private static final LazyConstant<Optional<Integer>> OPTIONAL_42_2 = LazyConstant.of(() -> Optional.of(42));
    private static final LazyConstant<Optional<Integer>> OPTIONAL_EMPTY = LazyConstant.of(Optional::empty);
    private static final LazyConstant<Optional<Integer>> OPTIONAL_EMPTY2 = LazyConstant.of(Optional::empty);

    private final LazyConstant<Integer> stable = init(VALUE);
    private final LazyConstant<Integer> stable2 = init(VALUE2);
    private final Supplier<Integer> dcl = new Dcl<>(() -> VALUE);
    private final Supplier<Integer> dcl2 = new Dcl<>(() -> VALUE2);
    private final AtomicReference<Integer> atomic = new AtomicReference<>(VALUE);
    private final AtomicReference<Integer> atomic2 = new AtomicReference<>(VALUE2);
    private final Supplier<Integer> supplier = () -> VALUE;
    private final Supplier<Integer> supplier2 = () -> VALUE2;

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
        return stable.get() + stable2.get();
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
        return DCL.get() + DCL2.get();
    }

    @Benchmark
    public int staticHolder() {
        return HOLDER.get() + HOLDER2.get();
    }

    @Benchmark
    public int staticOptional42() {
        return OPTIONAL_42.get().orElseThrow() + OPTIONAL_42_2.get().orElseThrow();
    }

    @Benchmark
    public boolean staticOptionalEmpty() {
        return OPTIONAL_EMPTY.get().isEmpty() ^ OPTIONAL_EMPTY2.get().isEmpty();
    }

    @Benchmark
    public int staticRecordHolder() {
        return RECORD_HOLDER.get() + RECORD_HOLDER2.get();
    }

    @Benchmark
    public int staticStable() {
        return STABLE.get() + STABLE2.get();
    }


    private static LazyConstant<Integer> init(Integer value) {
        return LazyConstant.of(() -> value);
    }

    private static final class Holder {

        private final LazyConstant<Integer> delegate;

        Holder(int value) {
            delegate = LazyConstant.of(() -> value);
        }

        int get() {
            return delegate.get();
        }

    }

    private record RecordHolder(LazyConstant<Integer> delegate) {

        RecordHolder(int value) {
            this(LazyConstant.of(() -> value));
        }

        int get() {
            return delegate.get();
        }

    }

    // Handles null values
    public static class Dcl<V> implements Supplier<V> {

        private final Supplier<V> supplier;

        private volatile V value;

        public Dcl(Supplier<V> supplier) {
            this.supplier = supplier;
        }

        @Override
        public V get() {
            V v = value;
            if (v == null) {
                synchronized (this) {
                    v = value;
                    if (v == null) {
                        value = v = supplier.get();
                    }
                }
            }
            return v;
        }
    }

}
