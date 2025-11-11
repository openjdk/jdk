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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
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
public class StableSupplierBenchmark {

    private static final int VALUE = 42;
    private static final int VALUE2 = 23;

    private static final StableValue<Integer> STABLE = init(StableValue.of(), VALUE);
    private static final StableValue<Integer> STABLE2 = init(StableValue.of(), VALUE2);
    private static final Supplier<Integer> SUPPLIER = StableValue.supplier(() -> VALUE);
    private static final Supplier<Integer> SUPPLIER2 = StableValue.supplier(() -> VALUE);

    private final StableValue<Integer> stable = init(StableValue.of(), VALUE);
    private final StableValue<Integer> stable2 = init(StableValue.of(), VALUE2);
    private final Supplier<Integer> supplier = StableValue.supplier(() -> VALUE);
    private final Supplier<Integer> supplier2 = StableValue.supplier(() -> VALUE2);

    @Benchmark
    public int stable() {
        return stable.orElseThrow() + stable2.orElseThrow();
    }

    @Benchmark
    public int supplier() {
        return supplier.get() + supplier2.get();
    }

    @Benchmark
    public int staticStable() {
        return STABLE.orElseThrow() + STABLE2.orElseThrow();
    }

    @Benchmark
    public int staticSupplier() {
        return SUPPLIER.get() + SUPPLIER2.get();
    }

    private static StableValue<Integer> init(StableValue<Integer> m, Integer value) {
        m.trySet(value);
        return m;
    }

}
