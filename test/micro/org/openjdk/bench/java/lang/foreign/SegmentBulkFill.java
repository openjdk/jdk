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

import static java.lang.foreign.ValueLayout.JAVA_LONG;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
public class SegmentBulkFill {

    private static final byte ZERO = 0;

    @Param({"2", "4", "8", "12", "16", "64", "512", "4096", "32768", "262144", "2097152", "16777216", "134217728"})
    public int size;

    public static class Array extends SegmentBulkFill {

        byte[] array;
        ByteBuffer buffer;

        @Setup
        public void setup() {
            array = new byte[size];
            buffer = ByteBuffer.wrap(array);
        }

        @Benchmark
        public void arraysFill() {
            Arrays.fill(array, ZERO);
        }

        @Benchmark
        public void arraysFillLoop() {
            for (int i = 0; i < array.length; i++) {
                array[i] = ZERO;
            }
        }

        @Benchmark
        public void bufferFillLoop() {
            for (int i = 0; i < array.length; i++) {
                buffer.put(i, ZERO);
            }
        }

    }

    public static class Segment extends SegmentBulkFill {

        enum SegmentType {HEAP, NATIVE}
        enum Alignment {ALIGNED, UNALIGNED}

        @Param({"HEAP", "NATIVE"})
        String segmentType;

        @Param({"ALIGNED", "UNALIGNED"})
        String alignment;

        MemorySegment segment;

        @Setup
        public void setup() {
            // A long array is likely to be aligned at 8-byte boundaries
            long[] baseArray = new long[size / Long.BYTES + 1];
            var heapSegment = MemorySegment.ofArray(baseArray);

            segment = switch (SegmentType.valueOf(segmentType)) {
                case HEAP   -> heapSegment;
                case NATIVE -> Arena.ofAuto().allocateFrom(JAVA_LONG, baseArray);
            };
            segment = switch (Alignment.valueOf(alignment)) {
                case ALIGNED   -> segment.asSlice(0, size);
                case UNALIGNED -> segment.asSlice(1, size);
            };
        }

        @Benchmark
        @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.fill=31"})
        public void fill() {
            segment.fill(ZERO);
        }

        @Benchmark
        public void fillLoopIntInt() {
            for (int i = 0; i < (int)segment.byteSize(); i++) {
                segment.set(ValueLayout.JAVA_BYTE, i, ZERO);
            }
        }

        @Benchmark
        public void fillLoopIntLong() {
            for (int i = 0; i < segment.byteSize(); i++) {
                segment.set(ValueLayout.JAVA_BYTE, i, ZERO);
            }
        }

        @Benchmark
        public void fillLoopLongLong() {
            for (long i = 0; i < segment.byteSize(); i++) {
                segment.set(ValueLayout.JAVA_BYTE, i, ZERO);
            }
        }

        @Benchmark
        @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.fill=0"})
        public void fillUnsafe() {
            segment.fill(ZERO);
        }

    }

}
