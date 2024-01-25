/*
 *  Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import sun.misc.Unsafe;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(3)
public class BulkOps {

    static final Unsafe unsafe = Utils.unsafe;

    static final int ELEM_SIZE = 1_000_000;
    static final int CARRIER_SIZE = (int)JAVA_INT.byteSize();
    static final int ALLOC_SIZE = ELEM_SIZE * CARRIER_SIZE;

    final Arena arena = Arena.ofShared();

    final long unsafe_addr = unsafe.allocateMemory(ALLOC_SIZE);
    final MemorySegment segment = arena.allocate(ALLOC_SIZE, 1);

    final IntBuffer buffer = IntBuffer.allocate(ELEM_SIZE);

    final int[] ints = new int[ELEM_SIZE];
    final MemorySegment bytesSegment = MemorySegment.ofArray(ints);
    final int UNSAFE_INT_OFFSET = unsafe.arrayBaseOffset(int[].class);

    // large(ish) segments/buffers with same content, 0, for mismatch, non-multiple-of-8 sized
    static final int SIZE_WITH_TAIL = (1024 * 1024) + 7;
    final MemorySegment mismatchSegmentLarge1;

    {
        mismatchSegmentLarge1 = arena.allocate(SIZE_WITH_TAIL, 1);
    }

    final MemorySegment mismatchSegmentLarge2 = arena.allocate(SIZE_WITH_TAIL, 1);
    final ByteBuffer mismatchBufferLarge1 = ByteBuffer.allocateDirect(SIZE_WITH_TAIL);
    final ByteBuffer mismatchBufferLarge2 = ByteBuffer.allocateDirect(SIZE_WITH_TAIL);

    // mismatch at first byte
    final MemorySegment mismatchSegmentSmall1 = arena.allocate(7, 1);
    final MemorySegment mismatchSegmentSmall2 = arena.allocate(7, 1);
    final ByteBuffer mismatchBufferSmall1 = ByteBuffer.allocateDirect(7);
    final ByteBuffer mismatchBufferSmall2 = ByteBuffer.allocateDirect(7);

    @Setup
    public void setup() {
        mismatchSegmentSmall1.fill((byte) 0xFF);
        mismatchBufferSmall1.put((byte) 0xFF).clear();
        // verify expected mismatch indices
        long si = mismatchSegmentLarge1.mismatch(mismatchSegmentLarge2);
        if (si != -1)
            throw new AssertionError("Unexpected mismatch index:" + si);
        int bi = mismatchBufferLarge1.mismatch(mismatchBufferLarge2);
        if (bi != -1)
            throw new AssertionError("Unexpected mismatch index:" + bi);
        si = mismatchSegmentSmall1.mismatch(mismatchSegmentSmall2);
        if (si != 0)
            throw new AssertionError("Unexpected mismatch index:" + si);
        bi = mismatchBufferSmall1.mismatch(mismatchBufferSmall2);
        if (bi != 0)
            throw new AssertionError("Unexpected mismatch index:" + bi);

        for (int i = 0; i < ints.length ; i++) {
            ints[i] = i;
        }
    }

    @TearDown
    public void tearDown() {
        arena.close();
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void unsafe_fill() {
        unsafe.setMemory(unsafe_addr, ALLOC_SIZE, (byte)42);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void segment_fill() {
        segment.fill((byte)42);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void unsafe_copy() {
        unsafe.copyMemory(ints, UNSAFE_INT_OFFSET, null, unsafe_addr, ALLOC_SIZE);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void segment_copy() {
        segment.copyFrom(bytesSegment);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void segment_copy_static() {
        MemorySegment.copy(ints, 0, segment, JAVA_INT_UNALIGNED, 0, ints.length);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void segment_copy_static_small() {
        MemorySegment.copy(ints, 0, segment, JAVA_INT_UNALIGNED, 0, 10);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void segment_copy_static_small_dontinline() {
        MemorySegment.copy(ints, 0, segment, JAVA_INT_UNALIGNED, 0, 10);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void unsafe_copy_small() {
        unsafe.copyMemory(ints, UNSAFE_INT_OFFSET, null, unsafe_addr, 10 * CARRIER_SIZE);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void buffer_copy_small() {
        buffer.put(0, ints, 0, 10);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void buffer_copy() {
        buffer.put(0, ints, 0, ints.length);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void segment_copy_static_dontinline() {
        MemorySegment.copy(ints, 0, segment, JAVA_INT_UNALIGNED, 0, ints.length);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public long mismatch_large_segment() {
        return mismatchSegmentLarge1.mismatch(mismatchSegmentLarge2);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int mismatch_large_bytebuffer() {
        return mismatchBufferLarge1.mismatch(mismatchBufferLarge2);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public long mismatch_small_segment() {
        return mismatchSegmentSmall1.mismatch(mismatchSegmentSmall2);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int mismatch_small_bytebuffer() {
        return mismatchBufferSmall1.mismatch(mismatchBufferSmall2);
    }
}
