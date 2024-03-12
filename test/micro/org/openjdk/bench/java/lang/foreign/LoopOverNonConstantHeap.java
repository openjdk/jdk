/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.foreign.MemorySegment;

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
import sun.misc.Unsafe;

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
public class LoopOverNonConstantHeap extends JavaLayouts {

    static final Unsafe unsafe = Utils.unsafe;

    static final int ELEM_SIZE = 1_000_000;
    static final int CARRIER_SIZE = (int)JAVA_INT.byteSize();
    static final int ALLOC_SIZE = ELEM_SIZE * CARRIER_SIZE;
    static final int UNSAFE_BYTE_BASE = unsafe.arrayBaseOffset(byte[].class);
    static final int UNSAFE_INT_BASE = unsafe.arrayBaseOffset(int[].class);

    MemorySegment segment, alignedSegment;
    byte[] base;
    int[] alignedBase;

    ByteBuffer byteBuffer;

    @Param(value = {"false", "true"})
    boolean polluteProfile;

    @Setup
    public void setup() {
        if (polluteProfile) {
            MemorySegment intB = MemorySegment.ofArray(new byte[ALLOC_SIZE]);
            MemorySegment intI = MemorySegment.ofArray(new int[ALLOC_SIZE]);
            MemorySegment intD = MemorySegment.ofArray(new double[ALLOC_SIZE]);
            MemorySegment intF = MemorySegment.ofArray(new float[ALLOC_SIZE]);
            Arena scope = Arena.ofAuto();
            MemorySegment s = scope.allocate(ALLOC_SIZE, 1);
            for (int i = 0; i < ALLOC_SIZE; i++) {
                intB.set(JAVA_BYTE, i, (byte)i);
                intI.setAtIndex(JAVA_INT, i, i);
                intD.setAtIndex(JAVA_DOUBLE, i, i);
                intF.setAtIndex(JAVA_FLOAT, i, i);
                s.set(JAVA_BYTE, i, (byte) i);
            }
        }

        base = new byte[ALLOC_SIZE];
        for (int i = 0; i < ELEM_SIZE; i++) {
            unsafe.putInt(base, UNSAFE_BYTE_BASE + (i * CARRIER_SIZE) , i);
        }
        alignedBase = new int[ELEM_SIZE];
        for (int i = 0; i < ELEM_SIZE; i++) {
            unsafe.putInt(base, UNSAFE_INT_BASE + (i * CARRIER_SIZE) , i);
        }
        segment = MemorySegment.ofArray(base);
        alignedSegment = MemorySegment.ofArray(alignedBase);
        byteBuffer = ByteBuffer.wrap(base).order(ByteOrder.nativeOrder());
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int unsafe_get() {
        return unsafe.getInt(base, UNSAFE_BYTE_BASE);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int segment_get() {
        return (int) VH_INT_UNALIGNED.get(segment, 0L);
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
            res += unsafe.getInt(base, UNSAFE_BYTE_BASE + (i * CARRIER_SIZE));
        }
        return res;
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
    public int segment_loop() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += (int) VH_INT.get(alignedSegment, (long) i);
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
    public int segment_loop_instance() {
        int res = 0;
        for (int i = 0; i < ELEM_SIZE; i ++) {
            res += alignedSegment.get(JAVA_INT, i * CARRIER_SIZE);
        }
        return res;
    }

    @Benchmark
    public int segment_loop_slice() {
        int sum = 0;
        MemorySegment base = alignedSegment.asSlice(0, alignedSegment.byteSize());
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += (int) VH_INT.get(base, (long) i);
        }
        return sum;
    }

    @Benchmark
    public int segment_loop_readonly() {
        int sum = 0;
        MemorySegment base = alignedSegment.asReadOnly();
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
