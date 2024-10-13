/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.*;
import static java.nio.ByteOrder.BIG_ENDIAN;

/**
 * This benchmark creates an array of longs with random contents. The array
 * is then copied into a byte array (using big endian) using different
 * methods.
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
public class MemorySegmentVsBits {

    public static final VarHandle LONG_ARRAY_VH = MethodHandles.byteArrayViewVarHandle(long[].class, BIG_ENDIAN);

    Arena arena = Arena.ofConfined();

    @Param({"1", "2", "16", "64", "256"})
    public int size;
    private long[] longs;
    private byte[] bytes;

    private ByteBuffer byteBuffer;
    private LongBuffer longBuffer;
    private MemorySegment segment;
    private MemorySegment nativeSegment;

    private static final ValueLayout.OfLong OF_LONG = (JAVA_LONG.order() != BIG_ENDIAN)
            ? JAVA_LONG.withOrder(BIG_ENDIAN)
            : JAVA_LONG;

    @Setup
    public void setup() {
        longs = ThreadLocalRandom.current().longs(size).toArray();
        bytes = new byte[size * Long.BYTES];
        byteBuffer = ByteBuffer.wrap(bytes);
        longBuffer = byteBuffer.asLongBuffer();
        segment = MemorySegment.ofArray(bytes);
        nativeSegment = arena.allocate(size * Long.BYTES);
    }

    @TearDown
    public void tearDown() {
        arena.close();
    }

    @Benchmark
    public void bitsEquivalent() {
        for (int i = 0; i < size; i++) {
            putLong(bytes, i * Long.BYTES, longs[i]);
        }
    }
    @Benchmark
    public void byteVarHandle() {
        for (int i = 0; i < size; i++) {
            LONG_ARRAY_VH.set(bytes, i * Long.BYTES, longs[i]);
        }
    }
    @Benchmark
    public void byteBuffer() {
        for (int i = 0; i < size; i++) {
            byteBuffer.putLong(i * Long.BYTES, longs[i]);
        }
    }

    @Benchmark
    public void longBuffer() {
        for (int i = 0; i < size; i++) {
            longBuffer.put(i, longs[i]);
        }
    }

    @Benchmark
    public void panamaHeap() {
        for (int i = 0; i < size; i++) {
            segment.set(JAVA_LONG_UNALIGNED, i * Long.BYTES, longs[i]);
        }
    }

    @Benchmark
    public void panamaNative() {
        for (int i = 0; i < size; i++) {
            nativeSegment.set(OF_LONG, i * Long.BYTES, longs[i]);
        }
    }

    @Benchmark
    public void panamaNativeUnaligned() {
        for (int i = 0; i < size; i++) {
            nativeSegment.set(JAVA_LONG_UNALIGNED, i * Long.BYTES, longs[i]);
        }
    }

    // java.io.Bits is package private
    static void putLong(byte[] b, int off, long val) {
        b[off + 7] = (byte) (val);
        b[off + 6] = (byte) (val >>> 8);
        b[off + 5] = (byte) (val >>> 16);
        b[off + 4] = (byte) (val >>> 24);
        b[off + 3] = (byte) (val >>> 32);
        b[off + 2] = (byte) (val >>> 40);
        b[off + 1] = (byte) (val >>> 48);
        b[off] = (byte) (val >>> 56);
    }

}
