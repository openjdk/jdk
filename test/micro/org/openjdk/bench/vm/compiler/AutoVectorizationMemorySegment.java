/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import java.util.concurrent.TimeUnit;
import java.util.Random;

import java.lang.foreign.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
public class AutoVectorizationMemorySegment {
    @Param({"10000"})
    public static int SIZE;

    @Param({"ByteArray", "CharArray", "ShortArray", "IntArray", "LongArray", "FloatArray", "DoubleArray",
            "Native",
            "ByteBuffer", "ByteBufferDirect"})
    public static String BACKING_TYPE;

    @Param("42")
    private int seed;
    private Random r = new Random(seed);

    // MemorySegments, allocated according to BACKING_TYPE.
    private MemorySegment aMS;
    private MemorySegment bMS;
    private MemorySegment rMS;

    @Setup
    public void init() {
        aMS = allocate();
        bMS = allocate();
        rMS = allocate();
    }

    MemorySegment allocate() {
        switch (BACKING_TYPE) {
            case "ByteArray"        -> { return MemorySegment.ofArray(new byte[SIZE]); }
            case "CharArray"        -> { return MemorySegment.ofArray(new char[BACKING_SIZE / 2]); }
            case "ShortArray"       -> { return MemorySegment.ofArray(new short[BACKING_SIZE / 2]); }
            case "IntArray"         -> { return MemorySegment.ofArray(new int[BACKING_SIZE / 4]); }
            case "LongArray"        -> { return MemorySegment.ofArray(new long[BACKING_SIZE / 8]); }
            case "FloatArray"       -> { return MemorySegment.ofArray(new float[BACKING_SIZE / 4]); }
            case "DoubleArray"      -> { return MemorySegment.ofArray(new double[BACKING_SIZE / 8]); }
            case "ByteBuffer"       -> { return MemorySegment.ofBuffer(ByteBuffer.allocate(BACKING_SIZE)); }
            case "ByteBufferDirect" -> { return MemorySegment.ofBuffer(ByteBuffer.allocateDirect(BACKING_SIZE)); }
            case "Native"           -> { return Arena.ofAuto().allocate(BACKING_SIZE, 1); }
            default -> throw new RuntimeException("BACKING_TYPE not supported: " + BACKING_TYPE);
        };
    }

    @Benchmark
    public void elementwiseByteIncr() {
        for (long i = 0; i < aMS.byteSize(); i++) {
            byte v = rMS.get(ValueLayout.JAVA_BYTE, i);
            rMS.set(ValueLayout.JAVA_BYTE, i, (byte)(v + 1));
        }
    }

    @Benchmark
    public void elementwiseByteAdd() {
        for (long i = 0; i < aMS.byteSize(); i++) {
            byte v1 = aMS.get(ValueLayout.JAVA_BYTE, i);
            byte v2 = bMS.get(ValueLayout.JAVA_BYTE, i);
            rMS.set(ValueLayout.JAVA_BYTE, i, (byte)(v1 + v2));
        }
    }
}
