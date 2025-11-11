/*
 *  Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.misc.Unsafe;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = { "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED" })
public class BulkOps {

    static final Unsafe unsafe = Utils.unsafe;

    static final int ELEM_SIZE = 1_000_000;
    static final int CARRIER_SIZE = (int)JAVA_INT.byteSize();
    static final int ALLOC_SIZE = ELEM_SIZE * CARRIER_SIZE;
    static final byte MAGIC_VALUE = 42;

    final Arena arena = Arena.ofShared();

    final long unsafe_addr = unsafe.allocateMemory(ALLOC_SIZE);
    final MemorySegment segment = arena.allocate(ALLOC_SIZE, 1);

    final IntBuffer buffer = IntBuffer.allocate(ELEM_SIZE);

    final int[] ints = new int[ELEM_SIZE];
    final MemorySegment bytesSegment = MemorySegment.ofArray(ints);
    final long UNSAFE_INT_OFFSET = unsafe.arrayBaseOffset(int[].class);

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
    public void unsafe_fill() {
        unsafe.setMemory(unsafe_addr, ALLOC_SIZE, MAGIC_VALUE);
    }

    @Benchmark
    public void segment_fill() {
        segment.fill(MAGIC_VALUE);
    }

    @Benchmark
    public void segment_fill_int_long_loop() {
        for (int i = 0 ; i < segment.byteSize() ; i++) {
            segment.set(ValueLayout.JAVA_BYTE, i, MAGIC_VALUE);
        }
    }

    @Benchmark
    public void segment_fill_int_int_loop() {
        for (int i = 0 ; i < (int)segment.byteSize() ; i++) {
            segment.set(ValueLayout.JAVA_BYTE, i, MAGIC_VALUE);
        }
    }

    @Benchmark
    public void segment_fill_long_long_loop() {
        for (long i = 0 ; i < segment.byteSize() ; i++) {
            segment.set(ValueLayout.JAVA_BYTE, i, MAGIC_VALUE);
        }
    }


    @Benchmark
    public void unsafe_copy() {
        unsafe.copyMemory(ints, UNSAFE_INT_OFFSET, null, unsafe_addr, ALLOC_SIZE);
    }

    @Benchmark
    public void segment_copy() {
        segment.copyFrom(bytesSegment);
    }

    @Benchmark
    public void segment_copy_static() {
        MemorySegment.copy(ints, 0, segment, JAVA_INT_UNALIGNED, 0, ints.length);
    }

    @Benchmark
    public void segment_copy_static_int_long_loop() {
        long limit = bytesSegment.byteSize() / JAVA_INT_UNALIGNED.byteSize();
        for (int i = 0 ; i < limit ; i++) {
            segment.setAtIndex(JAVA_INT_UNALIGNED, i, ints[i]);
        }
    }

    @Benchmark
    public void segment_copy_static_int_int_loop() {
        long limit = bytesSegment.byteSize() / JAVA_INT_UNALIGNED.byteSize();
        for (int i = 0 ; i < (int)limit ; i++) {
            segment.setAtIndex(JAVA_INT_UNALIGNED, i, ints[i]);
        }
    }

    @Benchmark
    public void segment_copy_static_long_long_loop() {
        long limit = bytesSegment.byteSize() / JAVA_INT_UNALIGNED.byteSize();
        for (long i = 0 ; i < limit ; i++) {
            segment.setAtIndex(JAVA_INT_UNALIGNED, i, ints[(int)i]);
        }
    }

    @Benchmark
    public void segment_copy_static_small() {
        MemorySegment.copy(ints, 0, segment, JAVA_INT_UNALIGNED, 0, 10);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void segment_copy_static_small_dontinline() {
        MemorySegment.copy(ints, 0, segment, JAVA_INT_UNALIGNED, 0, 10);
    }

    @Benchmark
    public void segment_copy_static_small_int_long_loop() {
        for (int i = 0 ; i < 10L ; i++) {
            segment.setAtIndex(JAVA_INT_UNALIGNED, i, ints[i]);
        }
    }

    @Benchmark
    public void segment_copy_static_small_int_int_loop() {
        for (int i = 0 ; i < 10 ; i++) {
            segment.setAtIndex(JAVA_INT_UNALIGNED, i, ints[i]);
        }
    }

    @Benchmark
    public void segment_copy_static_small_long_long_loop() {
        for (long i = 0 ; i < 10 ; i++) {
            segment.setAtIndex(JAVA_INT_UNALIGNED, i, ints[(int)i]);
        }
    }

    @Benchmark
    public void unsafe_copy_small() {
        unsafe.copyMemory(ints, UNSAFE_INT_OFFSET, null, unsafe_addr, 10 * CARRIER_SIZE);
    }

    @Benchmark
    public void buffer_copy_small() {
        buffer.put(0, ints, 0, 10);
    }

    @Benchmark
    public void buffer_copy() {
        buffer.put(0, ints, 0, ints.length);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void segment_copy_static_dontinline() {
        MemorySegment.copy(ints, 0, segment, JAVA_INT_UNALIGNED, 0, ints.length);
    }

    @Benchmark
    public long mismatch_large_segment_int_long_loop() {
        for (int i = 0 ; i < mismatchSegmentLarge1.byteSize() ; i++) {
            if (mismatchSegmentLarge1.get(ValueLayout.JAVA_BYTE, i) != mismatchSegmentLarge2.get(ValueLayout.JAVA_BYTE, i)) {
                return i;
            }
        }
        return -1;
    }

    @Benchmark
    public long mismatch_large_segment_int_int_loop() {
        for (int i = 0 ; i < (int)mismatchSegmentLarge1.byteSize() ; i++) {
            if (mismatchSegmentLarge1.get(ValueLayout.JAVA_BYTE, i) != mismatchSegmentLarge2.get(ValueLayout.JAVA_BYTE, i)) {
                return i;
            }
        }
        return -1;
    }

    @Benchmark
    public long mismatch_large_segment_long_long_loop() {
        for (long i = 0 ; i < mismatchSegmentLarge1.byteSize() ; i++) {
            if (mismatchSegmentLarge1.get(ValueLayout.JAVA_BYTE, i) != mismatchSegmentLarge2.get(ValueLayout.JAVA_BYTE, i)) {
                return i;
            }
        }
        return -1;
    }

    @Benchmark
    public long mismatch_large_segment() {
        return mismatchSegmentLarge1.mismatch(mismatchSegmentLarge2);
    }

    @Benchmark
    public int mismatch_large_bytebuffer() {
        return mismatchBufferLarge1.mismatch(mismatchBufferLarge2);
    }

    @Benchmark
    public long mismatch_small_segment_int_long_loop() {
        for (int i = 0 ; i < mismatchSegmentSmall1.byteSize() ; i++) {
            if (mismatchSegmentSmall1.get(ValueLayout.JAVA_BYTE, i) != mismatchSegmentSmall2.get(ValueLayout.JAVA_BYTE, i)) {
                return i;
            }
        }
        return -1;
    }

    @Benchmark
    public long mismatch_small_segment_int_int_loop() {
        for (int i = 0 ; i < (int)mismatchSegmentSmall1.byteSize() ; i++) {
            if (mismatchSegmentSmall1.get(ValueLayout.JAVA_BYTE, i) != mismatchSegmentSmall2.get(ValueLayout.JAVA_BYTE, i)) {
                return i;
            }
        }
        return -1;
    }

    @Benchmark
    public long mismatch_small_segment_long_long_loop() {
        for (long i = 0 ; i < mismatchSegmentSmall1.byteSize() ; i++) {
            if (mismatchSegmentSmall1.get(ValueLayout.JAVA_BYTE, i) != mismatchSegmentSmall2.get(ValueLayout.JAVA_BYTE, i)) {
                return i;
            }
        }
        return -1;
    }

    @Benchmark
    public long mismatch_small_segment() {
        return mismatchSegmentSmall1.mismatch(mismatchSegmentSmall2);
    }

    @Benchmark
    public int mismatch_small_bytebuffer() {
        return mismatchBufferSmall1.mismatch(mismatchBufferSmall2);
    }
}
