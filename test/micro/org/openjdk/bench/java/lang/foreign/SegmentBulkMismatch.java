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
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
public class SegmentBulkMismatch {

    @Param({"2", "4", "8", "12", "16", "64", "512", "4096", "32768", "262144", "2097152", "16777216", "134217728"})
    public int size;

    public static class Array extends SegmentBulkMismatch {

        byte[] srcArray;
        byte[] dstArray;

        @Setup
        public void setup() {
            srcArray = new byte[size];
            var rnd = new Random(42);
            rnd.nextBytes(srcArray);
            dstArray = Arrays.copyOf(srcArray, size);
        }

        @Benchmark
        public long array() {
            return Arrays.mismatch(srcArray, dstArray);
        }

    }

    public static class Segment extends SegmentBulkMismatch {

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
            // A long array is likely to be aligned at 8-byte boundaries
            long[] baseArray;

            baseArray = new long[size / Long.BYTES + 1];
            var rnd = new Random(42);
            for (int i = 0; i < baseArray.length; i++) {
                baseArray[i] = rnd.nextLong();
            }

            switch (SegmentType.valueOf(segmentType)) {
                case HEAP -> {
                    srcSegment = MemorySegment.ofArray(baseArray);
                    dstSegment = MemorySegment.ofArray(baseArray.clone());
                }
                case NATIVE -> {
                    var s = MemorySegment.ofArray(baseArray);
                    srcSegment = Arena.ofAuto().allocateFrom(JAVA_LONG, baseArray);
                    dstSegment = Arena.ofAuto().allocateFrom(JAVA_LONG, baseArray);
                }
            }
            switch (Alignment.valueOf(alignment)) {
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

        @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.mismatch=31"})
        @Benchmark
        public long mismatch() {
            return srcSegment.mismatch(dstSegment);
        }

        @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.mismatch=0"})
        @Benchmark
        public long mismatchUnsafe() {
            return srcSegment.mismatch(dstSegment);
        }

        @Benchmark
        public long mismatchLoopIntInt() {
            // Simplified version that assumes the segments are of equal size
            for (int i = 0; i < (int)srcSegment.byteSize(); i++) {
                if (srcSegment.get(ValueLayout.JAVA_BYTE, i) != dstSegment.get(ValueLayout.JAVA_BYTE, i)) {
                    return i;
                }
            }
            return -1;
        }

        @Benchmark
        public long mismatchLoopIntLong() {
            // Simplified version that assumes the segments are of equal size
            for (int i = 0; i < srcSegment.byteSize(); i++) {
                if (srcSegment.get(ValueLayout.JAVA_BYTE, i) != dstSegment.get(ValueLayout.JAVA_BYTE, i)) {
                    return i;
                }
            }
            return -1;
        }

        @Benchmark
        public long mismatchLoopLongLong() {
            // Simplified version that assumes the segments are of equal size
            for (long i = 0; i < srcSegment.byteSize(); i++) {
                if (srcSegment.get(ValueLayout.JAVA_BYTE, i) != dstSegment.get(ValueLayout.JAVA_BYTE, i)) {
                    return i;
                }
            }
            return -1;
        }

    }

}

