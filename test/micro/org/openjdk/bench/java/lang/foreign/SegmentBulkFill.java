/*
 *  Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
public class SegmentBulkFill {

    @Param({"2", "3", "4", "5", "6", "7", "8", "64", "512",
            "4096", "32768", "262144", "2097152", "16777216", "134217728"})
    public int ELEM_SIZE;

    byte[] array;
    MemorySegment heapSegment;
    MemorySegment nativeSegment;
    MemorySegment unalignedSegment;
    ByteBuffer buffer;

    @Setup
    public void setup() {
        array = new byte[ELEM_SIZE];
        heapSegment = MemorySegment.ofArray(array);
        nativeSegment = Arena.ofAuto().allocate(ELEM_SIZE, 8);
        unalignedSegment = Arena.ofAuto().allocate(ELEM_SIZE + 1, 8).asSlice(1);
        buffer = ByteBuffer.wrap(array);
    }

    @Benchmark
    public void arraysFill() {
        Arrays.fill(array, (byte) 0);
    }

    @Fork(value = 3, jvmArgsAppend = {"-Djava.lang.foreign.native.threshold.power.fill=31"})
    @Benchmark
    public void heapSegmentFillJava() {
        heapSegment.fill((byte) 0);
    }

    @Fork(value = 3, jvmArgsAppend = {"-Djava.lang.foreign.native.threshold.power.fill=0"})
    @Benchmark
    public void heapSegmentFillUnsafe() {
        heapSegment.fill((byte) 0);
    }

    @Fork(value = 3, jvmArgsAppend = {"-Djava.lang.foreign.native.threshold.power.fill=31"})
    @Benchmark
    public void nativeSegmentFillJava() {
        nativeSegment.fill((byte) 0);
    }

    @Fork(value = 3, jvmArgsAppend = {"-Djava.lang.foreign.native.threshold.power.fill=0"})
    @Benchmark
    public void nativeSegmentFillUnsafe() {
        nativeSegment.fill((byte) 0);
    }

    @Fork(value = 3, jvmArgsAppend = {"-Djava.lang.foreign.native.threshold.power.fill=31"})
    @Benchmark
    public void unalignedSegmentFillJava() {
        unalignedSegment.fill((byte) 0);
    }

    @Fork(value = 3, jvmArgsAppend = {"-Djava.lang.foreign.native.threshold.power.fill=0"})
    @Benchmark
    public void unalignedSegmentFillUnsafe() {
        unalignedSegment.fill((byte) 0);
    }

}
