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
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
public class SegmentBulkCopy {

    @Param({"2", "3", "4", "5", "6", "7", "8", "12", "16", "64", "512",
            "4096", "32768", "262144", "2097152", "16777216", "134217728"})
    public int size;

    byte[] srcArray;
    byte[] dstArray;

    public static class Array extends SegmentBulkCopy {

        ByteBuffer srcBuffer;
        ByteBuffer dstBuffer;

        @Setup
        public void setup() {
            srcArray = new byte[size];
            dstArray = new byte[size];
            srcBuffer = ByteBuffer.wrap(srcArray);
            dstBuffer = ByteBuffer.wrap(dstArray);
        }

        @Benchmark
        public void arrayCopy() {
            System.arraycopy(srcArray, 0, dstArray, 0, size);
        }

        @Benchmark
        public void bufferCopy() {
            dstBuffer.put(srcBuffer);
        }

    }

    public static class Segment extends SegmentBulkCopy {

        enum SegmentType {HEAP, NATIVE}
        enum Alignment {ALIGNED, UNALIGNED}

        @Param({"HEAP", "NATIVE"})
        String segmentType;

        @Param({"ALIGNED", "UNALIGNED"})
        String alignment;

        MemorySegment srcSegment;
        MemorySegment dstSegment;

        @Setup
        public void setup() {
            srcArray = new byte[size + 1];
            dstArray = new byte[size + 1];

            switch (Segment.SegmentType.valueOf(segmentType)) {
                case HEAP -> {
                    srcSegment = MemorySegment.ofArray(srcArray);
                    dstSegment = MemorySegment.ofArray(dstArray);
                }
                case NATIVE -> {
                    srcSegment = Arena.ofAuto().allocate(srcArray.length, Long.BYTES);
                    dstSegment = Arena.ofAuto().allocate(dstArray.length, Long.BYTES);
                }
            }
            switch (Segment.Alignment.valueOf(alignment)) {
                case ALIGNED -> {
                    srcSegment = srcSegment.asSlice(0, size);
                    dstSegment = dstSegment.asSlice(0, size);
                }
                case UNALIGNED -> {
                    srcSegment = srcSegment.asSlice(1, size);
                    dstSegment = dstSegment.asSlice(1, size);
                }
            }
        }

        @Benchmark
        @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.copy=31"})
        public void copy() {
            MemorySegment.copy(srcSegment, 0, dstSegment, 0, size);
        }

        @Benchmark
        public void copyLoopIntInt() {
            for (int i = 0; i < (int) srcSegment.byteSize(); i++) {
                final byte v = srcSegment.get(ValueLayout.JAVA_BYTE, i);
                dstSegment.set(ValueLayout.JAVA_BYTE, i, v);
            }
        }

        @Benchmark
        public void copyLoopIntLong() {
            for (int i = 0; i < srcSegment.byteSize(); i++) {
                final byte v = srcSegment.get(ValueLayout.JAVA_BYTE, i);
                dstSegment.set(ValueLayout.JAVA_BYTE, i, v);
            }
        }

        @Benchmark
        public void copyLoopLongLong() {
            for (long i = 0; i < srcSegment.byteSize(); i++) {
                final byte v = srcSegment.get(ValueLayout.JAVA_BYTE, i);
                dstSegment.set(ValueLayout.JAVA_BYTE, i, v);
            }
        }

        @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.copy=0"})
        @Benchmark
        public void copyUnsafe() {
            MemorySegment.copy(srcSegment, 0, dstSegment, 0, size);
        }

    }

}
