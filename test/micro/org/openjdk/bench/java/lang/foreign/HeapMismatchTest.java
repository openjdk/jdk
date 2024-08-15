/*
 *  Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.*;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
public class HeapMismatchTest {

    @Param({"4", "8", "16", "32", "64", "128"})
    public int ELEM_SIZE;

    byte[] srcArray;
    byte[] dstArray;
    MemorySegment srcSegment;
    MemorySegment dstSegment;
    ByteBuffer srcBuffer;
    ByteBuffer dstBuffer;

    @Setup
    public void setup() {
        srcArray = new byte[ELEM_SIZE];
        dstArray = new byte[ELEM_SIZE];
        srcSegment = MemorySegment.ofArray(srcArray);
        dstSegment = MemorySegment.ofArray(dstArray);
        srcBuffer = ByteBuffer.wrap(srcArray);
        dstBuffer = ByteBuffer.wrap(dstArray);
    }

    @Benchmark
    public boolean array_mismatch() {
        return Arrays.mismatch(srcArray, 0, ELEM_SIZE, dstArray, 0, ELEM_SIZE) == -1;
    }

    @Benchmark
    public boolean segment_mismatch() {
        return MemorySegment.mismatch(srcSegment, 0, ELEM_SIZE, dstSegment, 0, ELEM_SIZE) == -1;
    }

    @Benchmark
    public boolean buffer_mismatch() {
        return dstBuffer.equals(srcBuffer);
    }

    @Benchmark
    public boolean segment_staged() {
        return switch (ELEM_SIZE) {
            case 1 -> dstSegment.get(ValueLayout.JAVA_BYTE, 0) == srcSegment.get(ValueLayout.JAVA_BYTE, 0);
            case 2 -> dstSegment.get(ValueLayout.JAVA_SHORT_UNALIGNED, 0) == srcSegment.get(ValueLayout.JAVA_SHORT_UNALIGNED, 0);
            default -> MemorySegment.mismatch(srcSegment, 0, ELEM_SIZE, dstSegment, 0, ELEM_SIZE) == -1;
        };
    }

}
