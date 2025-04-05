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

import java.nio.ByteBuffer;
import java.lang.foreign.*;

/**
 * We test MemorySegment vectorization:
 * - Various backing types
 * - Various access types / sizes
 * - Various pointer forms
 *
 * Related IR test: test/hotspot/jtreg/compiler/loopopts/superword/TestMemorySegment.java
 */

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

    private int  zeroInvarI = 0;
    private long zeroInvarL = 0;

    @Setup
    public void init() {
        aMS = allocate();
        bMS = allocate();
        rMS = allocate();
    }

    MemorySegment allocate() {
        switch (BACKING_TYPE) {
            case "ByteArray"        -> { return MemorySegment.ofArray(new byte[SIZE]); }
            case "CharArray"        -> { return MemorySegment.ofArray(new char[SIZE / 2]); }
            case "ShortArray"       -> { return MemorySegment.ofArray(new short[SIZE / 2]); }
            case "IntArray"         -> { return MemorySegment.ofArray(new int[SIZE / 4]); }
            case "LongArray"        -> { return MemorySegment.ofArray(new long[SIZE / 8]); }
            case "FloatArray"       -> { return MemorySegment.ofArray(new float[SIZE / 4]); }
            case "DoubleArray"      -> { return MemorySegment.ofArray(new double[SIZE / 8]); }
            case "ByteBuffer"       -> { return MemorySegment.ofBuffer(ByteBuffer.allocate(SIZE)); }
            case "ByteBufferDirect" -> { return MemorySegment.ofBuffer(ByteBuffer.allocateDirect(SIZE)); }
            case "Native"           -> { return Arena.ofAuto().allocate(SIZE, 1); }
            default -> throw new RuntimeException("BACKING_TYPE not supported: " + BACKING_TYPE);
        }
    }

    @Benchmark
    public void longLoopElementwiseByteAdd() {
        for (long i = 0; i < aMS.byteSize(); i++) {
            byte v1 = aMS.get(ValueLayout.JAVA_BYTE, i);
            byte v2 = bMS.get(ValueLayout.JAVA_BYTE, i);
            rMS.set(ValueLayout.JAVA_BYTE, i, (byte)(v1 + v2));
        }
    }

    // see TestMemorySegment.testMemorySegmentBadExitCheck
    @Benchmark
    public void intLoopWithLongLimitElementByteIncrSameLongAdr() {
        for (int i = 0; i < rMS.byteSize(); i++) {
            long adr = i;
            byte v = rMS.get(ValueLayout.JAVA_BYTE, adr);
            rMS.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testIntLoop_iv_byte
    @Benchmark
    public void intLoopElementWiseByteIncrSameLongAdr() {
        for (int i = 0; i < (int)rMS.byteSize(); i++) {
            long adr = i;
            byte v = rMS.get(ValueLayout.JAVA_BYTE, adr);
            rMS.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testIntLoop_longIndex_intInvar_sameAdr_byte
    @Benchmark
    public void intLoopElementWiseByteIncrWithIntInvarSameLongAdr() {
        for (int i = 0; i < (int)rMS.byteSize(); i++) {
            long adr = (long)(i) + (long)(zeroInvarI);
            byte v = rMS.get(ValueLayout.JAVA_BYTE, adr);
            rMS.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testIntLoop_longIndex_longInvar_sameAdr_byte
    @Benchmark
    public void intLoopElementWiseByteIncrWithLongInvarSameLongAdr() {
        for (int i = 0; i < (int)rMS.byteSize(); i++) {
            long adr = (long)(i) + (long)(zeroInvarL);
            byte v = rMS.get(ValueLayout.JAVA_BYTE, adr);
            rMS.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testIntLoop_longIndex_intInvar_byte
    @Benchmark
    public void intLoopElementWiseByteIncrWithIntInvarSeparateLongAdr() {
        for (int i = 0; i < (int)rMS.byteSize(); i++) {
            long adr1 = (long)(i) + (long)(zeroInvarI);
            byte v = rMS.get(ValueLayout.JAVA_BYTE, adr1);
            long adr2 = (long)(i) + (long)(zeroInvarI);
            rMS.set(ValueLayout.JAVA_BYTE, adr2, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testIntLoop_longIndex_longInvar_byte
    @Benchmark
    public void intLoopElementWiseByteIncrWithLongInvarSeparateLongAdr() {
        for (int i = 0; i < (int)rMS.byteSize(); i++) {
            long adr1 = (long)(i) + (long)(zeroInvarL);
            byte v = rMS.get(ValueLayout.JAVA_BYTE, adr1);
            long adr2 = (long)(i) + (long)(zeroInvarL);
            rMS.set(ValueLayout.JAVA_BYTE, adr2, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testIntLoop_intIndex_intInvar_byte
    @Benchmark
    public void intLoopElementWiseByteIncrWithIntInvarSameIntIndex() {
        for (int i = 0; i < (int)rMS.byteSize(); i++) {
            int int_index = i + zeroInvarI;
            byte v = rMS.get(ValueLayout.JAVA_BYTE, int_index);
            rMS.set(ValueLayout.JAVA_BYTE, int_index, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testIntLoop_iv_int
    @Benchmark
    public void intLoopElementWiseIntIncrSameLongAdr() {
        for (int i = 0; i < (int)rMS.byteSize()/4; i++ ) {
            long adr = 4L * i;
            int v = rMS.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            rMS.set(ValueLayout.JAVA_INT_UNALIGNED, adr, (int)(v + 1));
        }
    }

    // see TestMemorySegment.testIntLoop_longIndex_intInvar_sameAdr_int
    @Benchmark
    public void intLoopElementWiseIntIncrWithIntInvarSameLongAdr() {
        for (int i = 0; i < (int)rMS.byteSize()/4; i++) {
            long adr = 4L * (long)(i) + 4L * (long)(zeroInvarI);
            int v = rMS.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            rMS.set(ValueLayout.JAVA_INT_UNALIGNED, adr, (int)(v + 1));
        }
    }

    // see TestMemorySegment.testIntLoop_longIndex_longInvar_sameAdr_int
    @Benchmark
    public void intLoopElementWiseIntIncrWithLongInvarSameLongAdr() {
        for (int i = 0; i < (int)rMS.byteSize()/4; i++) {
            long adr = 4L * (long)(i) + 4L * (long)(zeroInvarL);
            int v = rMS.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            rMS.set(ValueLayout.JAVA_INT_UNALIGNED, adr, (int)(v + 1));
        }
    }

    // see TestMemorySegment.testIntLoop_longIndex_intInvar_int
    @Benchmark
    public void intLoopElementWiseIntIncrWithIntInvarSeparateLongAdr() {
        for (int i = 0; i < (int)rMS.byteSize()/4; i++) {
            long adr1 = 4L * (long)(i) + 4L * (long)(zeroInvarI);
            int v = rMS.get(ValueLayout.JAVA_INT_UNALIGNED, adr1);
            long adr2 = 4L * (long)(i) + 4L * (long)(zeroInvarI);
            rMS.set(ValueLayout.JAVA_INT_UNALIGNED, adr2, (int)(v + 1));
        }
    }

    // see TestMemorySegment.testIntLoop_longIndex_longInvar_int
    @Benchmark
    public void intLoopElementWiseIntIncrWithLongInvarSeparateLongAdr() {
        for (int i = 0; i < (int)rMS.byteSize()/4; i++) {
            long adr1 = 4L * (long)(i) + 4L * (long)(zeroInvarL);
            int v = rMS.get(ValueLayout.JAVA_INT_UNALIGNED, adr1);
            long adr2 = 4L * (long)(i) + 4L * (long)(zeroInvarL);
            rMS.set(ValueLayout.JAVA_INT_UNALIGNED, adr2, (int)(v + 1));
        }
    }

    // see TestMemorySegment.testIntLoop_intIndex_intInvar_int
    @Benchmark
    public void intLoopElementWiseIntIncrWithIntInvarSameIntIndex() {
        for (int i = 0; i < (int)rMS.byteSize()/4; i++) {
            int int_index = i + zeroInvarI;
            int v = rMS.get(ValueLayout.JAVA_INT_UNALIGNED, 4L * int_index);
            rMS.set(ValueLayout.JAVA_INT_UNALIGNED, 4L * int_index, (int)(v + 1));
        }
    }

    // see TestMemorySegment.testLongLoop_iv_byte
    @Benchmark
    public void longLoopElementWiseByteIncrSameLongAdr() {
        for (long i = 0; i < rMS.byteSize(); i++) {
            long adr = i;
            byte v = rMS.get(ValueLayout.JAVA_BYTE, adr);
            rMS.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testLongLoop_longIndex_intInvar_sameAdr_byte
    @Benchmark
    public void longLoopElementWiseByteIncrWithIntInvarSameLongAdr() {
        for (long i = 0; i < rMS.byteSize(); i++) {
            long adr = (long)(i) + (long)(zeroInvarI);
            byte v = rMS.get(ValueLayout.JAVA_BYTE, adr);
            rMS.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testLongLoop_longIndex_longInvar_sameAdr_byte
    @Benchmark
    public void longLoopElementWiseByteIncrWithLongInvarSameLongAdr() {
        for (long i = 0; i < rMS.byteSize(); i++) {
            long adr = (long)(i) + (long)(zeroInvarL);
            byte v = rMS.get(ValueLayout.JAVA_BYTE, adr);
            rMS.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testLongLoop_longIndex_intInvar_byte
    @Benchmark
    public void longLoopElementWiseByteIncrWithIntInvarSeparateLongAdr() {
        for (long i = 0; i < rMS.byteSize(); i++) {
            long adr1 = (long)(i) + (long)(zeroInvarI);
            byte v = rMS.get(ValueLayout.JAVA_BYTE, adr1);
            long adr2 = (long)(i) + (long)(zeroInvarI);
            rMS.set(ValueLayout.JAVA_BYTE, adr2, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testLongLoop_longIndex_longInvar_byte
    @Benchmark
    public void longLoopElementWiseByteIncrWithLongInvarSeparateLongAdr() {
        for (long i = 0; i < rMS.byteSize(); i++) {
            long adr1 = (long)(i) + (long)(zeroInvarL);
            byte v = rMS.get(ValueLayout.JAVA_BYTE, adr1);
            long adr2 = (long)(i) + (long)(zeroInvarL);
            rMS.set(ValueLayout.JAVA_BYTE, adr2, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testLongLoop_intIndex_intInvar_byte
    @Benchmark
    public void longLoopElementWiseByteIncrWithIntInvarSameIntIndex() {
        for (long i = 0; i < rMS.byteSize(); i++) {
            int int_index = (int)(i + zeroInvarI);
            byte v = rMS.get(ValueLayout.JAVA_BYTE, int_index);
            rMS.set(ValueLayout.JAVA_BYTE, int_index, (byte)(v + 1));
        }
    }

    // see TestMemorySegment.testLongLoop_iv_int
    @Benchmark
    public void longLoopElementWiseIntIncrSameLongAdr() {
        for (long i = 0; i < rMS.byteSize()/4; i++ ) {
            long adr = 4L * i;
            int v = rMS.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            rMS.set(ValueLayout.JAVA_INT_UNALIGNED, adr, (int)(v + 1));
        }
    }

    // see TestMemorySegment.testLongLoop_longIndex_intInvar_sameAdr_int
    @Benchmark
    public void longLoopElementWiseIntIncrWithIntInvarSameLongAdr() {
        for (long i = 0; i < rMS.byteSize()/4; i++) {
            long adr = 4L * (long)(i) + 4L * (long)(zeroInvarI);
            int v = rMS.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            rMS.set(ValueLayout.JAVA_INT_UNALIGNED, adr, (int)(v + 1));
        }
    }

    // see TestMemorySegment.testLongLoop_longIndex_longInvar_sameAdr_int
    @Benchmark
    public void longLoopElementWiseIntIncrWithLongInvarSameLongAdr() {
        for (long i = 0; i < rMS.byteSize()/4; i++) {
            long adr = 4L * (long)(i) + 4L * (long)(zeroInvarL);
            int v = rMS.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            rMS.set(ValueLayout.JAVA_INT_UNALIGNED, adr, (int)(v + 1));
        }
    }

    // see TestMemorySegment.testLongLoop_longIndex_intInvar_int
    @Benchmark
    public void longLoopElementWiseIntIncrWithIntInvarSeparateLongAdr() {
        for (long i = 0; i < rMS.byteSize()/4; i++) {
            long adr1 = 4L * (long)(i) + 4L * (long)(zeroInvarI);
            int v = rMS.get(ValueLayout.JAVA_INT_UNALIGNED, adr1);
            long adr2 = 4L * (long)(i) + 4L * (long)(zeroInvarI);
            rMS.set(ValueLayout.JAVA_INT_UNALIGNED, adr2, (int)(v + 1));
        }
    }

    // see TestMemorySegment.testLongLoop_longIndex_longInvar_int
    @Benchmark
    public void longLoopElementWiseIntIncrWithLongInvarSeparateLongAdr() {
        for (long i = 0; i < rMS.byteSize()/4; i++) {
            long adr1 = 4L * (long)(i) + 4L * (long)(zeroInvarL);
            int v = rMS.get(ValueLayout.JAVA_INT_UNALIGNED, adr1);
            long adr2 = 4L * (long)(i) + 4L * (long)(zeroInvarL);
            rMS.set(ValueLayout.JAVA_INT_UNALIGNED, adr2, (int)(v + 1));
        }
    }

    // see TestMemorySegment.testLongLoop_intIndex_intInvar_int
    @Benchmark
    public void longLoopElementWiseIntIncrWithIntInvarSameIntIndex() {
        for (long i = 0; i < rMS.byteSize()/4; i++) {
            int int_index = (int)(i + zeroInvarI);
            int v = rMS.get(ValueLayout.JAVA_INT_UNALIGNED, 4L * int_index);
            rMS.set(ValueLayout.JAVA_INT_UNALIGNED, 4L * int_index, (int)(v + 1));
        }
    }
}
