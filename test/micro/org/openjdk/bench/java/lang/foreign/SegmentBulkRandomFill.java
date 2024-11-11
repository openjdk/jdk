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
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
@OperationsPerInvocation(100_000)
public class SegmentBulkRandomFill {

    private static final int INSTANCES = 100_000;

    MemorySegment[] heapSegments;
    MemorySegment[] nativeSegments;
    MemorySegment[] unalignedSegments;

    @Setup
    public void setup() {
        heapSegments = new MemorySegment[INSTANCES];
        nativeSegments = new MemorySegment[INSTANCES];
        unalignedSegments = new MemorySegment[INSTANCES];
        var rnd = new Random(42);
        var arena = Arena.ofAuto();
        for (int i = 0; i < INSTANCES; i++) {
            var array = new byte[rnd.nextInt(1024)];
            heapSegments[i] = MemorySegment.ofArray(array);
            nativeSegments[i] = arena.allocate(array.length, 8);
            unalignedSegments[i] = arena.allocate(array.length + 1, 8).asSlice(1);
        }
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.fill=31"})
    @Benchmark
    public void heapSegmentFillJava() {
        for (int i = 0; i < INSTANCES; i++) {
            heapSegments[i].fill((byte) 0);
        }
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.fill=0"})
    @Benchmark
    public void heapSegmentFillUnsafe() {
        for (int i = 0; i < INSTANCES; i++) {
            heapSegments[i].fill((byte) 0);
        }
    }

    @Benchmark
    public void heapSegmentFillLoop() {
        for (int i = 0; i < INSTANCES; i++) {
            final long end = heapSegments[i].byteSize();
            for (long j = 0; j < end; j++) {
                heapSegments[i].set(ValueLayout.JAVA_BYTE, j, (byte) 0);
            }
        }
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.fill=31"})
    @Benchmark
    public void nativeSegmentFillJava() {
        for (int i = 0; i < INSTANCES; i++) {
            nativeSegments[i].fill((byte) 0);
        }
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.fill=0"})
    @Benchmark
    public void nativeSegmentFillUnsafe() {
        for (int i = 0; i < INSTANCES; i++) {
            nativeSegments[i].fill((byte) 0);
        }
    }

    @Benchmark
    public void nativeSegmentFillLoop() {
        for (int i = 0; i < INSTANCES; i++) {
            final long end = heapSegments[i].byteSize();
            for (long j = 0; j < end; j++) {
                nativeSegments[i].set(ValueLayout.JAVA_BYTE, j, (byte) 0);
            }
        }
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.fill=31"})
    @Benchmark
    public void unalignedSegmentFillJava() {
        for (int i = 0; i < INSTANCES; i++) {
            unalignedSegments[i].fill((byte) 0);
        }
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.fill=0"})
    @Benchmark
    public void unalignedSegmentFillUnsafe() {
        for (int i = 0; i < INSTANCES; i++) {
            unalignedSegments[i].fill((byte) 0);
        }
    }

    @Benchmark
    public void unalignedSegmentFillLoop() {
        for (int i = 0; i < INSTANCES; i++) {
            final long end = heapSegments[i].byteSize();
            for (long j = 0; j < end; j++) {
                unalignedSegments[i].set(ValueLayout.JAVA_BYTE, j, (byte) 0);
            }
        }
    }

}
