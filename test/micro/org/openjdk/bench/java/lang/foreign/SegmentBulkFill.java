/*
 *  Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.misc.ScopedMemoryAccess;
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
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED"})
public class SegmentBulkFill {

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

    @Param({"2", "3", "4", "5", "6", "7", "8", "12", "64", "512",
            "4096", "32768", "262144", "2097152", "16777216", "134217728"})
    public int ELEM_SIZE;

    byte[] array;
    AbstractMemorySegmentImpl heapSegment;
    AbstractMemorySegmentImpl nativeSegment;
    AbstractMemorySegmentImpl unalignedSegment;
    ByteBuffer buffer;

    @Setup
    public void setup() {
        array = new byte[ELEM_SIZE];
        heapSegment = (AbstractMemorySegmentImpl)MemorySegment.ofArray(array);
        nativeSegment = (AbstractMemorySegmentImpl)Arena.ofAuto().allocate(ELEM_SIZE, 8);
        unalignedSegment = (AbstractMemorySegmentImpl)Arena.ofAuto().allocate(ELEM_SIZE + 1, 8).asSlice(1);
        buffer = ByteBuffer.wrap(array);
    }

    @Benchmark
    public void arraysFill() {
        Arrays.fill(array, (byte) 0);
    }

    @Benchmark
    public void arraysFillLoop() {
        for (int i = 0; i < array.length; i++) {
            array[i] = 0;
        }
    }

    @Benchmark
    public void bufferFillLoop() {
        for (int i = 0; i < array.length; i++) {
            buffer.put(i, (byte)0);
        }
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.fill=31"})
    @Benchmark
    public void heapSegmentFillJava() {
        heapSegment.fill((byte) 0);
    }

    @Benchmark
    public void heapSegmentFillUnsafe() {
        SCOPED_MEMORY_ACCESS.setMemory(heapSegment.sessionImpl(), heapSegment.unsafeGetBase(), heapSegment.unsafeGetOffset(), heapSegment.byteSize(), (byte) 0);
    }

    @Benchmark
    public void heapSegmentFillLoop() {
        for (long i = 0; i < heapSegment.byteSize(); i++) {
            heapSegment.set(ValueLayout.JAVA_BYTE, i, (byte) 0);
        }
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.fill=31"})
    @Benchmark
    public void nativeSegmentFillJava() {
        nativeSegment.fill((byte) 0);
    }

    @Benchmark
    public void nativeSegmentFillUnsafe() {
        SCOPED_MEMORY_ACCESS.setMemory(nativeSegment.sessionImpl(), nativeSegment.unsafeGetBase(), nativeSegment.unsafeGetOffset(), nativeSegment.byteSize(), (byte) 0);
    }

    @Benchmark
    public void nativeSegmentFillLoop() {
        for (long i = 0; i < nativeSegment.byteSize(); i++) {
            nativeSegment.set(ValueLayout.JAVA_BYTE, i, (byte) 0);
        }
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.fill=31"})
    @Benchmark
    public void unalignedSegmentFillJava() {
        unalignedSegment.fill((byte) 0);
    }

    @Benchmark
    public void unalignedSegmentFillUnsafe() {
        SCOPED_MEMORY_ACCESS.setMemory(unalignedSegment.sessionImpl(), unalignedSegment.unsafeGetBase(), unalignedSegment.unsafeGetOffset(), unalignedSegment.byteSize(), (byte) 0);
    }

    @Benchmark
    public void unalignedSegmentFillLoop() {
        for (long i = 0; i < unalignedSegment.byteSize(); i++) {
            unalignedSegment.set(ValueLayout.JAVA_BYTE, i, (byte) 0);
        }
    }

}
