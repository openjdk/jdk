/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.MemorySegment.Scope;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=micro/native" })
public class StrLenTest extends CLayouts {

    Arena arena = Arena.ofConfined();

    SegmentAllocator segmentAllocator;
    SegmentAllocator arenaAllocator = new RingAllocator(arena);
    SlicingPool pool = new SlicingPool();

    @Param({"5", "20", "100"})
    public int size;
    public String str;

    static {
        System.loadLibrary("StrLen");
    }

    static final MethodHandle STRLEN;

    static {
        Linker abi = Linker.nativeLinker();
        STRLEN = abi.downcallHandle(abi.defaultLookup().findOrThrow("strlen"),
                FunctionDescriptor.of(C_INT, C_POINTER));
    }

    @Setup
    public void setup() {
        str = makeString(size);
        segmentAllocator = SegmentAllocator.prefixAllocator(arena.allocate(size + 1, 1));
    }

    @TearDown
    public void tearDown() {
        arena.close();
    }

    @Benchmark
    public int jni_strlen() throws Throwable {
        return strlen(str);
    }

    @Benchmark
    public int panama_strlen_alloc() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocateFrom(str);
            return (int)STRLEN.invokeExact(segment);
        }
    }

    @Benchmark
    public int panama_strlen_ring() throws Throwable {
        return (int)STRLEN.invokeExact(arenaAllocator.allocateFrom(str));
    }

    @Benchmark
    public int panama_strlen_pool() throws Throwable {
        try (Arena arena = pool.acquire()) {
            return (int) STRLEN.invokeExact(arena.allocateFrom(str));
        }
    }

    @Benchmark
    public int panama_strlen_prefix() throws Throwable {
        return (int)STRLEN.invokeExact(segmentAllocator.allocateFrom(str));
    }

    @Benchmark
    public int panama_strlen_unsafe() throws Throwable {
        MemorySegment address = makeStringUnsafe(str);
        int res = (int) STRLEN.invokeExact(address);
        freeMemory(address);
        return res;
    }

    static MemorySegment makeStringUnsafe(String s) {
        byte[] bytes = s.getBytes();
        int len = bytes.length;
        MemorySegment address = allocateMemory(len + 1);
        MemorySegment str = address.asSlice(0, len + 1);
        str.copyFrom(MemorySegment.ofArray(bytes));
        str.set(JAVA_BYTE, len, (byte)0);
        return address;
    }

    static native int strlen(String str);

    static String makeString(int size) {
        String lorem = """
                Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et
                 dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip
                 ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu
                 fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt
                 mollit anim id est laborum.
                """;
        return lorem.substring(0, size);
    }

    static class RingAllocator implements SegmentAllocator {
        final MemorySegment segment;
        SegmentAllocator current;
        long rem;

        public RingAllocator(Arena session) {
            this.segment = session.allocate(1024, 1);
            reset();
        }

        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            if (rem < byteSize) {
                reset();
            }
            MemorySegment res = current.allocate(byteSize, byteAlignment);
            long lastOffset = res.address() - segment.address() + res.byteSize();
            rem = segment.byteSize() - lastOffset;
            return res;
        }

        void reset() {
            current = SegmentAllocator.slicingAllocator(segment);
            rem = segment.byteSize();
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
}
