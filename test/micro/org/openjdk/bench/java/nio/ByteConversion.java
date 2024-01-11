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

    static final int SIZE = 256;

    @Param({"false", "true"})
    private boolean direct;

    private ByteBuffer buffer;

    private int off;

    @Setup
    public void setup() {
        ByteOrder targetBo = ByteOrder.nativeOrder() == BIG_ENDIAN ? LITTLE_ENDIAN : BIG_ENDIAN;
        if (direct) {
            buffer = ByteBuffer.allocateDirect(SIZE).order(targetBo);
        } else {
            buffer = ByteBuffer.allocate(SIZE).order(targetBo);
        }
    }

    @Benchmark
    public long single_long() {
        return buffer.getLong(off);
    }

    @Benchmark
    public int single_int() {
        return buffer.getInt(off);
    }

    @Benchmark
    public int single_short() {
        return buffer.getShort(off);
    }

    @Benchmark
    public long single_char() {
        return buffer.getChar(off);
    }

    @Benchmark
    public long multi_long() {
        return buffer.getLong(off) +
               buffer.getLong(off + 16) +
               buffer.getLong(off + 32) +
               buffer.getLong(off + 48) +
               buffer.getLong(off + 64) +
               buffer.getLong(off + 80) +
               buffer.getLong(off + 96) +
               buffer.getLong(off + 112);
    }

    @Benchmark
    public int multi_int() {
        return buffer.getInt(off) +
               buffer.getInt(off + 16) +
               buffer.getInt(off + 32) +
               buffer.getInt(off + 48) +
               buffer.getInt(off + 64) +
               buffer.getInt(off + 80) +
               buffer.getInt(off + 96) +
               buffer.getInt(off + 112);
    }

    @Benchmark
    public int multi_short() {
        return buffer.getShort(off) +
               buffer.getShort(off + 16) +
               buffer.getShort(off + 32) +
               buffer.getShort(off + 48) +
               buffer.getShort(off + 64) +
               buffer.getShort(off + 80) +
               buffer.getShort(off + 96) +
               buffer.getShort(off + 112);
    }

    @Benchmark
    public int multi_char() {
        return buffer.getChar(off) +
               buffer.getChar(off + 16) +
               buffer.getChar(off + 32) +
               buffer.getChar(off + 48) +
               buffer.getChar(off + 64) +
               buffer.getChar(off + 80) +
               buffer.getChar(off + 96) +
               buffer.getChar(off + 112);
    }

}
