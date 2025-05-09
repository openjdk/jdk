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
package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

import static org.openjdk.bench.java.lang.foreign.CLayouts.C_DOUBLE;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = { "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=micro/native" })
public class CallOverheadByValue {

    public static final MemoryLayout POINT_LAYOUT = MemoryLayout.structLayout(
            C_DOUBLE, C_DOUBLE
    );
    private static final MethodHandle MH_UNIT_BY_VALUE;
    private static final MethodHandle MH_UNIT_BY_PTR;

    static {
        Linker abi = Linker.nativeLinker();
        System.loadLibrary("CallOverheadByValue");
        SymbolLookup loaderLibs = SymbolLookup.loaderLookup();
        MH_UNIT_BY_VALUE = abi.downcallHandle(
                loaderLibs.findOrThrow("unit"),
                FunctionDescriptor.of(POINT_LAYOUT)
        );
        MH_UNIT_BY_PTR = abi.downcallHandle(
                loaderLibs.findOrThrow("unit_ptr"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
    }

    private static final Arena arena = Arena.ofConfined();
    private static final MemorySegment point = arena.allocate(POINT_LAYOUT);
    private static final SegmentAllocator BY_VALUE_ALLOCATOR = (SegmentAllocator) (_, _) -> point;

    @TearDown
    public void tearDown() {
        arena.close();
    }

    @Benchmark
    public void byValue() throws Throwable {
        // point = unit();
        MemorySegment unused = (MemorySegment) MH_UNIT_BY_VALUE.invokeExact(BY_VALUE_ALLOCATOR);
    }

    @Benchmark
    public void byPtr() throws Throwable {
        // unit_ptr(&point);
        MH_UNIT_BY_PTR.invokeExact(point);
    }

    @Fork(value = 3, jvmArgsAppend = "-Djmh.executor=VIRTUAL")
    public static class OfVirtual extends CallOverheadByValue {}

}
