/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.*;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=micro/native" })
public class PointerInvoke extends CLayouts {

    Arena arena = Arena.ofConfined();
    MemorySegment segment = arena.allocate(100, 1);

    static {
        System.loadLibrary("Ptr");
    }

    static final MethodHandle F_LONG_LONG, F_PTR_LONG, F_LONG_PTR, F_PTR_PTR;

    static {
        Linker abi = Linker.nativeLinker();
        SymbolLookup loaderLibs = SymbolLookup.loaderLookup();
        F_LONG_LONG = abi.downcallHandle(loaderLibs.findOrThrow("id_long_long"),
                FunctionDescriptor.of(C_LONG_LONG, C_LONG_LONG));
        F_PTR_LONG = abi.downcallHandle(loaderLibs.findOrThrow("id_ptr_long"),
                FunctionDescriptor.of(C_LONG_LONG, C_POINTER));
        F_LONG_PTR = abi.downcallHandle(loaderLibs.findOrThrow("id_long_ptr"),
                FunctionDescriptor.of(C_POINTER, C_LONG_LONG));
        F_PTR_PTR = abi.downcallHandle(loaderLibs.findOrThrow("id_ptr_ptr"),
                FunctionDescriptor.of(C_POINTER, C_POINTER));
    }

    @TearDown
    public void tearDown() {
        arena.close();
    }

    @Benchmark
    public long long_to_long() throws Throwable {
        return (long)F_LONG_LONG.invokeExact(segment.address());
    }

    @Benchmark
    public long ptr_to_long() throws Throwable {
        return (long)F_PTR_LONG.invokeExact(segment);
    }

    @Benchmark
    public long ptr_to_long_new_segment() throws Throwable {
        MemorySegment newSegment = segment.reinterpret(100, arena, null);
        return (long)F_PTR_LONG.invokeExact(newSegment);
    }

    @Benchmark
    public long long_to_ptr() throws Throwable {
        return ((MemorySegment)F_LONG_PTR.invokeExact(segment.address())).address();
    }

    @Benchmark
    public long ptr_to_ptr() throws Throwable {
        return ((MemorySegment)F_PTR_PTR.invokeExact(segment)).address();
    }

    @Benchmark
    public long ptr_to_ptr_new_segment() throws Throwable {
        MemorySegment newSegment = segment.reinterpret(100, arena, null);
        return ((MemorySegment)F_PTR_PTR.invokeExact(newSegment)).address();
    }
}
