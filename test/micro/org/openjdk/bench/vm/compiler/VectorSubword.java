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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class VectorSubword {
    @Param({"1024"})
    public int SIZE;

    private byte[] bytes;
    private short[] shorts;
    private char[] chars;
    private int[] ints;
    private long[] longs;

    @Setup
    public void init() {
        bytes = new byte[SIZE];
        shorts = new short[SIZE];
        chars = new char[SIZE];
        ints = new int[SIZE];
        longs = new long[SIZE];
    }

    // Narrowing

    @Benchmark
    public void shortToByte() {
        for (int i = 0; i < SIZE; i++) {
            bytes[i] = (byte) shorts[i];
        }
    }

    @Benchmark
    public void shortToChar() {
        for (int i = 0; i < SIZE; i++) {
            chars[i] = (char) shorts[i];
        }
    }

    @Benchmark
    public void charToByte() {
        for (int i = 0; i < SIZE; i++) {
            bytes[i] = (byte) chars[i];
        }
    }

    @Benchmark
    public void charToShort() {
        for (int i = 0; i < SIZE; i++) {
            shorts[i] = (short) chars[i];
        }
    }

    @Benchmark
    public void intToByte() {
        for (int i = 0; i < SIZE; i++) {
            bytes[i] = (byte) ints[i];
        }
    }

    @Benchmark
    public void intToShort() {
        for (int i = 0; i < SIZE; i++) {
            shorts[i] = (short) ints[i];
        }
    }

    @Benchmark
    public void intToChar() {
        for (int i = 0; i < SIZE; i++) {
            chars[i] = (char) ints[i];
        }
    }

    @Benchmark
    public void longToByte() {
        for (int i = 0; i < SIZE; i++) {
            bytes[i] = (byte) longs[i];
        }
    }

    @Benchmark
    public void longToShort() {
        for (int i = 0; i < SIZE; i++) {
            shorts[i] = (short) longs[i];
        }
    }

    @Benchmark
    public void longToChar() {
        for (int i = 0; i < SIZE; i++) {
            chars[i] = (char) longs[i];
        }
    }

    @Benchmark
    public void longToInt() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = (int) longs[i];
        }
    }

    // Widening

    @Benchmark
    public void byteToShort() {
        for (int i = 0; i < SIZE; i++) {
            shorts[i] = bytes[i];
        }
    }

    @Benchmark
    public void byteToChar() {
        for (int i = 0; i < SIZE; i++) {
            chars[i] = (char) bytes[i];
        }
    }

    @Benchmark
    public void byteToInt() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = bytes[i];
        }
    }

    @Benchmark
    public void byteToLong() {
        for (int i = 0; i < SIZE; i++) {
            longs[i] = bytes[i];
        }
    }

    @Benchmark
    public void shortToInt() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = shorts[i];
        }
    }

    @Benchmark
    public void shortToLong() {
        for (int i = 0; i < SIZE; i++) {
            longs[i] = shorts[i];
        }
    }

    @Benchmark
    public void charToInt() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = chars[i];
        }
    }

    @Benchmark
    public void charToLong() {
        for (int i = 0; i < SIZE; i++) {
            longs[i] = chars[i];
        }
    }

    @Benchmark
    public void intToLong() {
        for (int i = 0; i < SIZE; i++) {
            longs[i] = ints[i];
        }
    }

}
