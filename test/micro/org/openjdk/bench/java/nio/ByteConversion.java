/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package org.openjdk.bench.java.nio;

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
import java.nio.*;
import java.util.concurrent.TimeUnit;
import static java.nio.ByteOrder.*;

/**
 * Benchmark for byte-converting operations on java.nio.Buffer
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(3)
public class ByteConversion {

    private static final int READS = 128;

    // Read at large stride to dodge auto-vectorization
    private static final int STRIDE = 128;

    @Param({"false", "true"})
    private boolean direct;

    private ByteBuffer buffer;

    @Setup
    public void setup() {
        final int size = READS * STRIDE;
        ByteOrder targetBo = ByteOrder.nativeOrder() == BIG_ENDIAN ? LITTLE_ENDIAN : BIG_ENDIAN;
        if (direct) {
            buffer = ByteBuffer.allocateDirect(size).order(targetBo);
        } else {
            buffer = ByteBuffer.allocate(size).order(targetBo);
        }
    }

    @Benchmark
    public long cont_longs() {
        long s = 0;
        for (int c = 0; c < READS; c++) {
            s += buffer.getLong(c * 8);
        }
        return s;
    }

    @Benchmark
    public long stride_longs() {
        long s = 0;
        for (int c = 0; c < READS; c++) {
            s += buffer.getLong(c * STRIDE);
        }
        return s;
    }

    @Benchmark
    public int cond_ints() {
        int s = 0;
        for (int c = 0; c < READS; c++) {
            s += buffer.getInt(c * 4);
        }
        return s;
    }

    @Benchmark
    public int stride_ints() {
        int s = 0;
        for (int c = 0; c < READS; c++) {
            s += buffer.getInt(c * STRIDE);
        }
        return s;
    }

    @Benchmark
    public int cont_shorts() {
        int s = 0;
        for (int c = 0; c < READS; c++) {
            s += buffer.getShort(c * 2);
        }
        return s;
    }

    @Benchmark
    public int stride_shorts() {
        int s = 0;
        for (int c = 0; c < READS; c++) {
            s += buffer.getShort(c * STRIDE);
        }
        return s;
    }

    @Benchmark
    public long cont_chars() {
        int s = 0;
        for (int c = 0; c < READS; c++) {
            s += buffer.getChar(c * 2);
        }
        return s;
    }

    @Benchmark
    public long stride_chars() {
        int s = 0;
        for (int c = 0; c < READS; c++) {
            s += buffer.getChar(c * STRIDE);
        }
        return s;
    }

}
