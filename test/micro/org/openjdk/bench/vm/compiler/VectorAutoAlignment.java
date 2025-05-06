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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import java.lang.invoke.*;
import java.lang.foreign.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
public class VectorAutoAlignment {
    @Param({"2000"})
    public int SIZE;

    @Param({  "0",   "1",   "2",   "3",   "4",   "5",   "6",   "7",   "8",   "9",
             "10",  "11",  "12",  "13",  "14",  "15",  "16",  "17",  "18",  "19",
             "20",  "21",  "22",  "23",  "24",  "25",  "26",  "27",  "28",  "29",
             "30",  "31"})
    //@Param({  "0",   "1"})
    public int OFFSET_LOAD;

    @Param({  "0",   "1",   "2",   "3",   "4",   "5",   "6",   "7",   "8",   "9",
             "10",  "11",  "12",  "13",  "14",  "15",  "16",  "17",  "18",  "19",
             "20",  "21",  "22",  "23",  "24",  "25",  "26",  "27",  "28",  "29",
             "30",  "31"})
    //@Param({  "0",   "1"})
    public int OFFSET_STORE;

    @Param({"2000"})
    public int DISTANCE;

    // To get compile-time constants for OFFSET_LOAD, OFFSET_STORE, and DISTANCE
    static final MutableCallSite MUTABLE_CONSTANT_OFFSET_LOAD = new MutableCallSite(MethodType.methodType(int.class));
    static final MethodHandle MUTABLE_CONSTANT_OFFSET_LOAD_HANDLE = MUTABLE_CONSTANT_OFFSET_LOAD.dynamicInvoker();
    static final MutableCallSite MUTABLE_CONSTANT_OFFSET_STORE = new MutableCallSite(MethodType.methodType(int.class));
    static final MethodHandle MUTABLE_CONSTANT_OFFSET_STORE_HANDLE = MUTABLE_CONSTANT_OFFSET_STORE.dynamicInvoker();
    static final MutableCallSite MUTABLE_CONSTANT_DISTANCE = new MutableCallSite(MethodType.methodType(int.class));
    static final MethodHandle MUTABLE_CONSTANT_DISTANCE_HANDLE = MUTABLE_CONSTANT_DISTANCE.dynamicInvoker();

    private MemorySegment ms;

    @Setup
    public void init() throws Throwable {
        long totalSize = 4L * SIZE + 4L * DISTANCE;
        long alignment = 4 * 1024; // 4k = page size
        ms = Arena.ofAuto().allocate(totalSize, alignment);

        MethodHandle offset_load_con = MethodHandles.constant(int.class, OFFSET_LOAD);
        MUTABLE_CONSTANT_OFFSET_LOAD.setTarget(offset_load_con);
        MethodHandle offset_store_con = MethodHandles.constant(int.class, OFFSET_STORE);
        MUTABLE_CONSTANT_OFFSET_STORE.setTarget(offset_store_con);
        MethodHandle distance_con = MethodHandles.constant(int.class, DISTANCE);
        MUTABLE_CONSTANT_DISTANCE.setTarget(distance_con);
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private int offset_load_con() throws Throwable {
        return (int) MUTABLE_CONSTANT_OFFSET_LOAD_HANDLE.invokeExact();
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private int offset_store_con() throws Throwable {
        return (int) MUTABLE_CONSTANT_OFFSET_STORE_HANDLE.invokeExact();
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private int distance_con() throws Throwable {
        return (int) MUTABLE_CONSTANT_DISTANCE_HANDLE.invokeExact();
    }

    @Benchmark
    public void bench1L1S() throws Throwable {
        int offset_load = offset_load_con();
        int offset_store = offset_store_con();
        int distance = distance_con();
        for (int i = 0; i < SIZE - /* slack for offset */ 32; i++) {
            int v = ms.get(ValueLayout.JAVA_INT_UNALIGNED, 4L * i + 4L * offset_load + 4L * distance);
            ms.set(ValueLayout.JAVA_INT_UNALIGNED, 4L * i + 4L * offset_store, v);
        }
    }
}
