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

package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.*;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=micro/native" })
public class CallByRefHighArity {

    static {
        System.loadLibrary("CallByRefHighArity");
    }

    @Param
    SegmentKind kind;

    public enum SegmentKind {
        CONFINED,
        SHARED,
        GLOBAL,
        HEAP
    }

    Supplier<MemorySegment> segmentSupplier;
    Arena arena;

    @Setup
    public void setup() {
        if (kind == SegmentKind.CONFINED) {
            arena = Arena.ofConfined();
            MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_INT, 0);
            segmentSupplier = () -> segment;
        } else if (kind == SegmentKind.SHARED) {
            arena = Arena.ofShared();
            MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_INT, 0);
            segmentSupplier = () -> segment;
        } else if (kind == SegmentKind.HEAP) {
            byte[] array = new byte[8];
            MemorySegment segment = MemorySegment.ofArray(array);
            segmentSupplier = () -> segment;
        } else { // global
            segmentSupplier = () -> MemorySegment.ofAddress(0);
        }
    }

    @TearDown
    public void tearDown() {
        if (arena != null) {
            arena.close();
        }
    }

    // A shared library that exports the functions below
    private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    // void noop_params0() {}
    private static final MethodHandle MH_NOOP_PARAMS0 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid(), Linker.Option.critical(true))
            .bindTo(LOOKUP.find("noop_params0").orElseThrow());

    // void noop_params1(void *param0) {}
    private static final MethodHandle MH_NOOP_PARAMS1 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS
            ), Linker.Option.critical(true))
            .bindTo(LOOKUP.find("noop_params1").orElseThrow());

    // void noop_params2(void *param0, void *param1) {}
    private static final MethodHandle MH_NOOP_PARAMS2 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            ), Linker.Option.critical(true))
            .bindTo(LOOKUP.find("noop_params2").orElseThrow());

    // void noop_params3(void *param0, void *param1, void *param2) {}
    private static final MethodHandle MH_NOOP_PARAMS3 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            ), Linker.Option.critical(true))
            .bindTo(LOOKUP.find("noop_params3").orElseThrow());

    // void noop_params4(void *param0, void *param1, void *param2, void *param3) {}
    private static final MethodHandle MH_NOOP_PARAMS4 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            ), Linker.Option.critical(true))
            .bindTo(LOOKUP.find("noop_params4").orElseThrow());

    // void noop_params5(int param0, int param1, void *param2, void *param3, void *param4) {}
    private static final MethodHandle MH_NOOP_PARAMS5 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            ), Linker.Option.critical(true))
            .bindTo(LOOKUP.find("noop_params5").orElseThrow());

    // void noop_params10(void *param0, void *param1, void *param2, void *param3, void *param4,
    //                    void *param5, void *param6, void *param7, void *param8, void *param9) {}
    private static final MethodHandle MH_NOOP_PARAMS10 = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            ), Linker.Option.critical(true))
            .bindTo(LOOKUP.find("noop_params10").orElseThrow());

    @Benchmark
    public void noop_params0() {
        try {
            MH_NOOP_PARAMS0.invokeExact();
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Benchmark
    public void noop_params1() {
        try {
            MH_NOOP_PARAMS1.invokeExact(
                    segmentSupplier.get()
            );
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Benchmark
    public void noop_params2() {
        try {
            MH_NOOP_PARAMS2.invokeExact(
                    segmentSupplier.get(),
                    segmentSupplier.get()
            );
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Benchmark
    public void noop_params3() {
        try {
            MH_NOOP_PARAMS3.invokeExact(
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get()
            );
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Benchmark
    public void noop_params4() {
        try {
            MH_NOOP_PARAMS4.invokeExact(
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get()
            );
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Benchmark
    public void noop_params5() {
        try {
            MH_NOOP_PARAMS5.invokeExact(
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get()
            );
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Benchmark
    public void noop_params10() {
        try {
            MH_NOOP_PARAMS10.invokeExact(
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get(),
                    segmentSupplier.get()
            );
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }
}
