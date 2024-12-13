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
import jdk.internal.misc.Unsafe;

import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgs = { "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED" })
public class LoopOverPollutedSegments extends JavaLayouts {

    static final int ELEM_SIZE = 1_000_000;
    static final int CARRIER_SIZE = (int) JAVA_INT.byteSize();
    static final int ALLOC_SIZE = ELEM_SIZE * CARRIER_SIZE;

    static final Unsafe unsafe = Utils.unsafe;


    Arena confinedArena, sharedArena;
    MemorySegment nativeSegment, nativeSharedSegment, heapSegmentBytes, heapSegmentFloats;
    byte[] arr;
    long addr;

    @Setup
    public void setup() {
        addr = unsafe.allocateMemory(ALLOC_SIZE);
        for (int i = 0; i < ELEM_SIZE; i++) {
            unsafe.putInt(addr + (i * 4), i);
        }
        arr = new byte[ALLOC_SIZE];
        confinedArena = Arena.ofConfined();
        sharedArena = Arena.ofShared();
        Arena scope1 = confinedArena;
        nativeSegment = scope1.allocate(ALLOC_SIZE, 4);
        Arena scope = sharedArena;
        nativeSharedSegment = scope.allocate(ALLOC_SIZE, 4);
        heapSegmentBytes = MemorySegment.ofArray(new byte[ALLOC_SIZE]);
        heapSegmentFloats = MemorySegment.ofArray(new float[ELEM_SIZE]);

        for (int rep = 0 ; rep < 5 ; rep++) {
            for (int i = 0; i < ELEM_SIZE; i++) {
                unsafe.putInt(arr, Unsafe.ARRAY_BYTE_BASE_OFFSET + (i * 4), i);
                nativeSegment.setAtIndex(JAVA_INT_UNALIGNED, i, i);
                nativeSegment.setAtIndex(JAVA_FLOAT_UNALIGNED, i, i);
                nativeSharedSegment.setAtIndex(JAVA_INT_UNALIGNED, i, i);
                nativeSharedSegment.setAtIndex(JAVA_FLOAT_UNALIGNED, i, i);
                VH_INT_UNALIGNED.set(nativeSegment, (long)i, i);
                heapSegmentBytes.setAtIndex(JAVA_INT_UNALIGNED, i, i);
                heapSegmentBytes.setAtIndex(JAVA_FLOAT_UNALIGNED, i, i);
                VH_INT_UNALIGNED.set(heapSegmentBytes, (long)i, i);
                heapSegmentFloats.setAtIndex(JAVA_INT_UNALIGNED, i, i);
                heapSegmentFloats.setAtIndex(JAVA_FLOAT_UNALIGNED, i, i);
                VH_INT_UNALIGNED.set(heapSegmentFloats, (long)i, i);
            }
        }
    }

    @TearDown
    public void tearDown() {
        confinedArena.close();
        sharedArena.close();
        heapSegmentBytes = null;
        heapSegmentFloats = null;
        arr = null;
        unsafe.freeMemory(addr);
    }

    @Benchmark
    public int native_segment_VH() {
        int sum = 0;
        for (int k = 0; k < ELEM_SIZE; k++) {
            VH_INT_UNALIGNED.set(nativeSegment, (long)k, k + 1);
            int v = (int) VH_INT_UNALIGNED.get(nativeSegment, (long)k);
            sum += v;
        }
        return sum;
    }

    @Benchmark
    public int native_segment_instance() {
        int sum = 0;
        for (int k = 0; k < ELEM_SIZE; k++) {
            nativeSegment.setAtIndex(JAVA_INT_UNALIGNED, k, k + 1);
            int v = nativeSegment.getAtIndex(JAVA_INT_UNALIGNED, k);
            sum += v;
        }
        return sum;
    }

    @Benchmark
    public int heap_segment_ints_VH() {
        int sum = 0;
        for (int k = 0; k < ELEM_SIZE; k++) {
            VH_INT_UNALIGNED.set(heapSegmentBytes, (long)k, k + 1);
            int v = (int) VH_INT_UNALIGNED.get(heapSegmentBytes, (long)k);
            sum += v;
        }
        return sum;
    }

    @Benchmark
    public int heap_segment_ints_instance() {
        int sum = 0;
        for (int k = 0; k < ELEM_SIZE; k++) {
            heapSegmentBytes.setAtIndex(JAVA_INT_UNALIGNED, k, k + 1);
            int v = heapSegmentBytes.getAtIndex(JAVA_INT_UNALIGNED, k);
            sum += v;
        }
        return sum;
    }

    @Benchmark
    public int heap_segment_floats_VH() {
        int sum = 0;
        for (int k = 0; k < ELEM_SIZE; k++) {
            VH_INT_UNALIGNED.set(heapSegmentFloats, (long)k, k + 1);
            int v = (int)VH_INT_UNALIGNED.get(heapSegmentFloats, (long)k);
            sum += v;
        }
        return sum;
    }

    @Benchmark
    public int heap_segment_floats_instance() {
        int sum = 0;
        for (int k = 0; k < ELEM_SIZE; k++) {
            heapSegmentFloats.setAtIndex(JAVA_INT_UNALIGNED, k, k + 1);
            int v = heapSegmentFloats.getAtIndex(JAVA_INT_UNALIGNED, k);
            sum += v;
        }
        return sum;
    }

    @Benchmark
    public int heap_unsafe() {
        int sum = 0;
        for (int k = 0; k < ALLOC_SIZE; k += 4) {
            unsafe.putInt(arr, k + Unsafe.ARRAY_BYTE_BASE_OFFSET, k + 1);
            int v = unsafe.getInt(arr, k + Unsafe.ARRAY_BYTE_BASE_OFFSET);
            sum += v;
        }
        return sum;
    }

    @Benchmark
    public int native_unsafe() {
        int sum = 0;
        for (int k = 0; k < ALLOC_SIZE; k += 4) {
            unsafe.putInt(addr + k, k + 1);
            int v = unsafe.getInt(addr + k);
            sum += v;
        }
        return sum;
    }
}
