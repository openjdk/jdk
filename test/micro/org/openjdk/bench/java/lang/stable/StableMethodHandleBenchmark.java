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

import org.openjdk.bench.java.lang.stable.StableValueBenchmark.Dcl;
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

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.constant.ConstantDescs.*;

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
public class StableMethodHandleBenchmark {

    private static final MethodHandle FINAL_MH = identityHandle();
    private static final StableValue<MethodHandle> STABLE_MH;

    private static /* intentionally not final */ MethodHandle mh = identityHandle();
    private static final Dcl<MethodHandle> DCL = new Dcl<>(StableMethodHandleBenchmark::identityHandle);
    private static final AtomicReference<MethodHandle> ATOMIC_REFERENCE = new AtomicReference<>(identityHandle());
    private static final Map<String, MethodHandle> MAP = new ConcurrentHashMap<>();
    private static final Map<String, MethodHandle> STABLE_MAP = StableValue.map(Set.of("identityHandle"), _ -> identityHandle());

    static {
        STABLE_MH = StableValue.of();
        STABLE_MH.setOrThrow(identityHandle());
        MAP.put("identityHandle", identityHandle());
    }

    @Benchmark
    public int atomic() throws Throwable {
        return (int) ATOMIC_REFERENCE.get().invokeExact(1);
    }

    @Benchmark
    public int dcl() throws Throwable {
        return (int) DCL.get().invokeExact(1);
    }

    @Benchmark
    public int finalMh() throws Throwable {
        return (int) FINAL_MH.invokeExact(1);
    }

    @Benchmark
    public int map() throws Throwable {
        return (int) MAP.get("identityHandle").invokeExact(1);
    }

    @Benchmark
    public int nonFinalMh() throws Throwable {
        return (int) mh.invokeExact(1);
    }

    @Benchmark
    public int stableMap() throws Throwable {
        return (int) STABLE_MAP.get("identityHandle").invokeExact(1);
    }

    @Benchmark
    public int stableMh() throws Throwable {
        return (int) STABLE_MH.orElseThrow().invokeExact(1);
    }

    static MethodHandle identityHandle() {
        var lookup = MethodHandles.lookup();
        try {
            return lookup.findStatic(StableMethodHandleBenchmark.class, "identity", MethodType.methodType(int.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static int identity(int value) {
        return value;
    }

}
