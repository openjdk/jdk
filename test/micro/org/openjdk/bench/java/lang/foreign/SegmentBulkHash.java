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
import jdk.internal.foreign.SegmentBulkOperations;
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
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--add-exports=java.base/jdk.internal.foreign=ALL-UNNAMED"})
public class SegmentBulkHash {

    @Param({"2", "4", "8", "12", "16", "64", "512", "4096", "32768", "262144", "2097152", "16777216", "134217728"})
    public int size;

    public static class Array extends SegmentBulkHash {

        byte[] array;

        @Setup
        public void setup() {
            byte[]  randomArray = new byte[size + 1];
            var rnd = new Random(42);
            rnd.nextBytes(randomArray);

            array = Arrays.copyOf(randomArray, size);
        }

        @Benchmark
        public int array() {
            return Arrays.hashCode(array);
        }

    }

    public static class Segment extends SegmentBulkHash {

        enum SegmentType {HEAP, NATIVE}
        enum Alignment {ALIGNED, UNALIGNED}

        @Param({"HEAP", "NATIVE"})
        String segmentType;

        @Param({"ALIGNED", "UNALIGNED"})
        String alignment;

        AbstractMemorySegmentImpl segment;

        @Setup
        public void setup() {
            // A long array is likely to be aligned at 8-byte boundaries
            long[] baseArray;

            baseArray = new long[size / Long.BYTES + 1];
            var rnd = new Random(42);
            for (int i = 0; i < baseArray.length; i++) {
                baseArray[i] = rnd.nextLong();
            }
            var heapSegment = MemorySegment.ofArray(baseArray);

            var s = switch (SegmentType.valueOf(segmentType)) {
                    case HEAP   -> heapSegment;
                    case NATIVE -> Arena.ofAuto().allocateFrom(JAVA_LONG, baseArray);
            };
            s = switch (Alignment.valueOf(alignment)) {
                case ALIGNED   -> s.asSlice(0, size);
                case UNALIGNED -> s.asSlice(1, size);
            };

            segment = (AbstractMemorySegmentImpl) s;
        }

        @Benchmark
        public int hash() {
            return SegmentBulkOperations.contentHash(segment, 0, size);
        }

        @Benchmark
        public int hashLoopIntInt() {
            int result = 1;
            for (int i = 0; i < (int)segment.byteSize(); i++) {
                result = 31 * result + segment.get(JAVA_BYTE, i);
            }
            return result;
        }

        @Benchmark
        public int hashLoopIntLong() {
            int result = 1;
            for (int i = 0; i < segment.byteSize(); i++) {
                result = 31 * result + segment.get(JAVA_BYTE, i);
            }
            return result;
        }

        @Benchmark
        public int hashLoopLongLong() {
            int result = 1;
            for (long i = 0; i < segment.byteSize(); i++) {
                result = 31 * result + segment.get(JAVA_BYTE, i);
            }
            return result;
        }

    }

}

