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

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--add-exports=java.base/jdk.internal.foreign=ALL-UNNAMED"})
public class SegmentBulkHash {

    @Param({"8", "64"})
    public int ELEM_SIZE;

    byte[] array;
    AbstractMemorySegmentImpl heapSegment;
    AbstractMemorySegmentImpl nativeSegment;

    @Setup
    public void setup() {
        // Always use the same alignment regardless of size
        nativeSegment = (AbstractMemorySegmentImpl) Arena.ofAuto().allocate(ELEM_SIZE, 16);
        var rnd = new Random(42);
        for (int i = 0; i < ELEM_SIZE; i++) {
            nativeSegment.set(JAVA_BYTE, i, (byte) rnd.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE));
        }
        array = nativeSegment.toArray(JAVA_BYTE);
        heapSegment = (AbstractMemorySegmentImpl) MemorySegment.ofArray(array);
    }

    @Benchmark
    public int array() {
        return Arrays.hashCode(array);
    }

    @Benchmark
    public int heapSegment() {
        return SegmentBulkOperations.contentHash(heapSegment, 0, ELEM_SIZE);
    }

    @Benchmark
    public int nativeSegment() {
        return SegmentBulkOperations.contentHash(nativeSegment, 0, ELEM_SIZE);
    }

    @Benchmark
    public int nativeSegmentJava() {
        int result = 1;
        for (long i = 0; i < ELEM_SIZE; i++) {
            result = 31 * result + nativeSegment.get(JAVA_BYTE, i);
        }
        return result;
    }

}

