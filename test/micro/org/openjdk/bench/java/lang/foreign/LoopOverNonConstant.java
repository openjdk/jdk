/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(3)
public class LoopOverNonConstant extends JavaLayouts {

    static final Unsafe unsafe = Utils.unsafe;

    static final int ELEM_SIZE = 1_000_000;
    static final int CARRIER_SIZE = (int)JAVA_INT.byteSize();
    static final int ALLOC_SIZE = ELEM_SIZE * CARRIER_SIZE;

    static final VarHandle VH_SEQ_INT = bindToZeroOffset(MemoryLayout.sequenceLayout(ELEM_SIZE, JAVA_INT).varHandle(PathElement.sequenceElement()));
    static final VarHandle VH_SEQ_INT_UNALIGNED = bindToZeroOffset(MemoryLayout.sequenceLayout(ELEM_SIZE, JAVA_INT.withByteAlignment(1)).varHandle(PathElement.sequenceElement()));

    static VarHandle bindToZeroOffset(VarHandle varHandle) {
        return MethodHandles.insertCoordinates(varHandle, 1, 0L);
    }

    Arena arena;
    MemorySegment segment;
    long unsafe_addr;

    ByteBuffer byteBuffer;

    @Setup
    public void setup() {
        unsafe_addr = unsafe.allocateMemory(ALLOC_SIZE);
        for (int i = 0; i < ELEM_SIZE; i++) {
            unsafe.putInt(unsafe_addr + (i * CARRIER_SIZE) , i);
        }
        arena = Arena.ofConfined();
        segment = arena.allocate(ALLOC_SIZE, 1);
        for (int i = 0; i < ELEM_SIZE; i++) {
            VH_INT.set(segment, (long) i, i);
        }
        byteBuffer = ByteBuffer.allocateDirect(ALLOC_SIZE).order(ByteOrder.nativeOrder());
        for (int i = 0; i < ELEM_SIZE; i++) {
            byteBuffer.putInt(i * CARRIER_SIZE , i);
        }
    }

    @TearDown
    public void tearDown() {
        arena.close();
        unsafe.invokeCleaner(byteBuffer);
        unsafe.freeMemory(unsafe_addr);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int unsafe_get() {
        return unsafe.getInt(unsafe_addr);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int segment_get() {
        return (int) VH_INT.get(segment, 0L);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int BB_get() {
        return byteBuffer.getInt(0);
    }

    @Benchmark
    public int unsafe_loop() {
        int res = 0;
        for (int i = 0; i < ELEM_SIZE; i ++) {
            res += unsafe.getInt(unsafe_addr + (i * CARRIER_SIZE));
        }
        return res;
    }

    @Benchmark
    public int segment_loop() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += (int) VH_INT.get(segment, (long) i);
        }
        return sum;
    }

    @Benchmark
    public int segment_loop_unaligned() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += (int) VH_INT_UNALIGNED.get(segment, (long) i);
        }
        return sum;
    }

    @Benchmark
    public int segment_loop_nested() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += (int) VH_SEQ_INT.get(segment, (long) i);
        }
        return sum;
    }

    @Benchmark
    public int segment_loop_nested_unaligned() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += (int) VH_SEQ_INT_UNALIGNED.get(segment, (long) i);
        }
        return sum;
    }

    @Benchmark
    public int segment_loop_instance() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += segment.get(JAVA_INT, i * CARRIER_SIZE);

        }
        return sum;
    }

    @Benchmark
    public int segment_loop_instance_index() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += segment.getAtIndex(JAVA_INT, i);

        }
        return sum;
    }

    @Benchmark
    public int segment_loop_instance_unaligned() {
        int res = 0;
        for (int i = 0; i < ELEM_SIZE; i ++) {
            res += segment.get(JAVA_INT_UNALIGNED, i * CARRIER_SIZE);
        }
        return res;
    }

    @Benchmark
    public int segment_loop_slice() {
        int sum = 0;
        MemorySegment base = segment.asSlice(0, segment.byteSize());
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += (int) VH_INT.get(base, (long) i);
        }
        return sum;
    }

    @Benchmark
    public int segment_loop_readonly() {
        int sum = 0;
        MemorySegment base = segment.asReadOnly();
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += (int) VH_INT.get(base, (long) i);
        }
        return sum;
    }

    @Benchmark
    public int BB_loop() {
        int sum = 0;
        ByteBuffer bb = byteBuffer;
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += bb.getInt(i * CARRIER_SIZE);
        }
        return sum;
    }

}
