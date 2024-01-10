/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;

import java.lang.invoke.MethodHandle;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED" })
public class CriticalCalls {

    static final MethodHandle PINNED;
    static final MethodHandle NOT_PINNED;

    static {
        System.loadLibrary("CriticalCalls");
        SymbolLookup lookup = SymbolLookup.loaderLookup();

        MemorySegment sumIntsSym = lookup.find("sum_ints").get();
        FunctionDescriptor sumIntsDesc = FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT);

        PINNED = Linker.nativeLinker().downcallHandle(
            sumIntsSym,
            sumIntsDesc,
            Linker.Option.critical(true));
        NOT_PINNED = Linker.nativeLinker().downcallHandle(
            sumIntsSym,
            sumIntsDesc,
            Linker.Option.critical(false));
    }

    @Param({"100", "10000", "1000000"})
    int size;

    int[] arr;
    SegmentAllocator recycler;

    @Setup
    public void setup() {
        arr = new int[size];
        for (int i = 0; i < size; i++) {
            arr[i] = i;
        }

        recycler = SegmentAllocator.prefixAllocator(Arena.ofAuto().allocate(JAVA_INT, arr.length));
    }

    @Benchmark
    public int callPinned() throws Throwable {
        return (int) PINNED.invokeExact(MemorySegment.ofArray(arr), arr.length);
    }

    @Benchmark
    public int callNotPinned() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeArr = arena.allocateFrom(JAVA_INT, arr);
            return (int) NOT_PINNED.invokeExact(nativeArr, arr.length);
        }
    }

    @Benchmark
    public int callRecycled() throws Throwable {
        MemorySegment nativeArr = recycler.allocateFrom(JAVA_INT, arr);
        return (int) NOT_PINNED.invokeExact(nativeArr, arr.length);
    }
}
