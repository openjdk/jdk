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

package org.openjdk.bench.java.lang.foreign.pointers;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(3)
@State(Scope.Benchmark)
public class PointerBench {

    final Arena arena = Arena.ofConfined();
    static final int ELEM_SIZE = 1_000_000;
    Pointer<Integer> intPointer = Pointer.allocate(NativeType.C_INT, ELEM_SIZE, arena);
    Pointer<Pointer<Integer>> intPointerPointer = Pointer.allocate(NativeType.C_INT_PTR, ELEM_SIZE, arena);
    Pointer<Point> pointPointer = Pointer.allocate(Point.TYPE, ELEM_SIZE, arena);
    MemorySegment intSegment = intPointer.segment();
    MemorySegment intPointerSegment = intPointerPointer.segment();
    MemorySegment pointSegment = pointPointer.segment();

    public static final AddressLayout UNSAFE_ADDRESS = ValueLayout.ADDRESS
            .withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE));

    @Setup
    public void setup() {
        for (int i = 0 ; i < ELEM_SIZE ; i++) {
            intSegment.setAtIndex(ValueLayout.JAVA_INT, i, i);
            intPointerSegment.setAtIndex(ValueLayout.ADDRESS, i, intSegment.asSlice(4 * i));
            pointSegment.setAtIndex(ValueLayout.JAVA_INT, (i * 2), i);
            pointSegment.setAtIndex(ValueLayout.JAVA_INT, (i * 2) + 1, i);
        }
    }

    @TearDown
    public void teardown() {
        arena.close();
    }

    @Benchmark
    public int testLoopPointerInt_ptr() {
        int sum = 0;
        for (int i = 0 ; i < ELEM_SIZE ; i++) {
            sum += intPointer.get(NativeType.C_INT, i);
        }
        return sum;
    }

    @Benchmark
    public int testLoopPointerPointerInt_ptr() {
        int sum = 0;
        for (int i = 0 ; i < ELEM_SIZE ; i++) {
            sum += intPointerPointer.get(NativeType.C_INT_PTR, i)
                                    .get(NativeType.C_INT, 0);
        }
        return sum;
    }

    @Benchmark
    public int testLoopPointerPoint_ptr() {
        int sum = 0;
        for (int i = 0 ; i < ELEM_SIZE ; i++) {
            sum += pointPointer.get(Point.TYPE, i).x();
        }
        return sum;
    }

    @Benchmark
    public int testLoopPointerInt_ptr_generic() {
        int sum = 0;
        for (int i = 0 ; i < ELEM_SIZE ; i++) {
            sum += genericGet(intPointer, NativeType.C_INT, i);
        }
        return sum;
    }

    static <Z> Z genericGet(Pointer<Z> pz, NativeType<Z> type, long offset) {
        return pz.get(type, offset);
    }

    @Benchmark
    public int testLoopPointerInt_segment() {
        int sum = 0;
        for (int i = 0 ; i < ELEM_SIZE ; i++) {
            sum += intSegment.getAtIndex(ValueLayout.JAVA_INT, i);
        }
        return sum;
    }

    @Benchmark
    public int testLoopPointerPointerInt_segment() {
        int sum = 0;
        for (long i = 0 ; i < ELEM_SIZE ; i++) {
            var segment = intPointerSegment.getAtIndex(UNSAFE_ADDRESS, i);
            sum += segment.get(ValueLayout.JAVA_INT, 0);
        }
        return sum;
    }

    @Benchmark
    public int testLoopPointerPoint_segment() {
        int sum = 0;
        for (int i = 0 ; i < ELEM_SIZE ; i++) {
            sum += pointSegment.getAtIndex(ValueLayout.JAVA_INT, i * 2);
        }
        return sum;
    }
}
