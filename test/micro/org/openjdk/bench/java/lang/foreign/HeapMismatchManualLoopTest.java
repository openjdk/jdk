/*
 *  Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import jdk.internal.misc.Unsafe;
import java.util.Objects;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgs = { "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED" })
public class HeapMismatchManualLoopTest {

    @Param({"4", "8", "16", "32", "64", "128"})
    public int ELEM_SIZE;

    static final Unsafe unsafe = Utils.unsafe;

    byte[] srcArray;
    byte[] dstArray;
    MemorySegment srcSegment;
    MemorySegment dstSegment;
    ByteBuffer srcBuffer;
    ByteBuffer dstBuffer;
    long srcByteSize;
    long dstByteSize;

    @Setup
    public void setup() {
        srcArray = new byte[ELEM_SIZE];
        dstArray = new byte[ELEM_SIZE];
        srcSegment = MemorySegment.ofArray(srcArray);
        dstSegment = MemorySegment.ofArray(dstArray);
        srcBuffer = ByteBuffer.wrap(srcArray);
        dstBuffer = ByteBuffer.wrap(dstArray);
        srcByteSize = ELEM_SIZE;
        dstByteSize = ELEM_SIZE;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int array_mismatch() {
        for (int i = 0; i < srcArray.length ; i++) {
            if (srcArray[i] != dstArray[i]) {
                return i;
            }
        }
        return -1;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public long segment_mismatch() {
        for (long i = 0; i < srcSegment.byteSize() ; i++) {
            if (srcSegment.get(ValueLayout.JAVA_BYTE, i) != dstSegment.get(ValueLayout.JAVA_BYTE, i)) {
                return i;
            }
        }
        return -1;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int buffer_mismatch() {
        for (int i = 0; i < srcBuffer.capacity() ; i++) {
            if (srcBuffer.get(i) != dstBuffer.get(i)) {
                return i;
            }
        }
        return -1;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public long unsafe_mismatch() {
        for (long i = 0; i < srcByteSize ; i++) {
            Objects.checkIndex(i, srcByteSize);
            Objects.checkIndex(i, dstByteSize);
            long offset = Unsafe.ARRAY_BYTE_BASE_OFFSET + i * Unsafe.ARRAY_BYTE_INDEX_SCALE;
            if (unsafe.getByte(srcArray, offset) != unsafe.getByte(dstArray, offset)) {
                return i;
            }
        }
        return -1;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public long unsafe_mismatch2() {
        for (long i = 0; i < srcByteSize ; i++) {
            long offset = Unsafe.ARRAY_BYTE_BASE_OFFSET + i * Unsafe.ARRAY_BYTE_INDEX_SCALE;
            if (unsafe.getByte(srcArray, offset) != unsafe.getByte(dstArray, offset)) {
                return i;
            }
        }
        return -1;
    }
}
