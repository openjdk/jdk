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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySegment.Scope;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED" })
public class AllocFromTest extends CLayouts {

    Arena arena = Arena.ofConfined();

    SlicingPool pool = new SlicingPool();

    @Param({"5", "20", "100", "500", "1000"})
    public int size;
    public byte[] arr;

    @Setup
    public void setup() {
        arr = new byte[size];
        Random random = new Random(0);
        random.nextBytes(arr);
    }

    @Benchmark
    public MemorySegment alloc_confined() {
        try (Arena arena = Arena.ofConfined()) {
            return arena.allocateFrom(ValueLayout.JAVA_BYTE, arr);
        }
    }

    @Benchmark
    public MemorySegment alloc_malloc_arena() {
        try (MallocArena arena = new MallocArena()) {
            return arena.allocateFrom(ValueLayout.JAVA_BYTE, arr);
        }
    }

    @Benchmark
    public MemorySegment alloc_unsafe_arena() {
        try (UnsafeArena arena = new UnsafeArena()) {
            return arena.allocateFrom(ValueLayout.JAVA_BYTE, arr);
        }
    }

    @Benchmark
    public MemorySegment alloc_pool_arena() {
        try (Arena arena = pool.acquire()) {
            return arena.allocateFrom(ValueLayout.JAVA_BYTE, arr);
        }
    }

    static class SlicingPool {
        final MemorySegment pool = Arena.ofAuto().allocate(1024);
        boolean isAcquired = false;

        public Arena acquire() {
            if (isAcquired) {
                throw new IllegalStateException("An allocator is already in use");
            }
            isAcquired = true;
            return new SlicingPoolAllocator();
        }

        class SlicingPoolAllocator implements Arena {

            final Arena arena = Arena.ofConfined();
            final SegmentAllocator slicing = SegmentAllocator.slicingAllocator(pool);

            public MemorySegment allocate(long byteSize, long byteAlignment) {
                return slicing.allocate(byteSize, byteAlignment)
                        .reinterpret(arena, null);
            }

            @Override
            public Scope scope() {
                return arena.scope();
            }

            public void close() {
                isAcquired = false;
                arena.close();
            }
        }
    }

    public static class MallocArena implements Arena {

        final Arena arena = Arena.ofConfined();

        @Override
        public Scope scope() {
            return arena.scope();
        }

        @Override
        public void close() {
            arena.close();
        }

        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            return CLayouts.allocateMemory(byteSize)
                    .reinterpret(byteSize, arena, CLayouts::freeMemory);
        }
    }

    public static class UnsafeArena implements Arena {

        final Arena arena = Arena.ofConfined();

        @Override
        public Scope scope() {
            return arena.scope();
        }

        @Override
        public void close() {
            arena.close();
        }

        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            return MemorySegment.ofAddress(Utils.unsafe.allocateMemory(byteSize))
                    .reinterpret(byteSize, arena, ms -> Utils.unsafe.freeMemory(ms.address()));
        }
    }
}
