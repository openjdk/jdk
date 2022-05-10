/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
public class VectorShiftRight {
    @Param({"1024"})
    public int SIZE;

    private byte[]  bytesA,  bytesB;
    private short[] shortsA, shortsB;
    private char[]  charsA,  charsB;
    private int[]   intsA,   intsB;
    private long[]  longsA,  longsB;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    @Param("3")
    private int shiftCount;

    @Setup
    public void init() {
        bytesA  = new byte[SIZE];
        shortsA = new short[SIZE];
        charsA  = new char[SIZE];
        intsA   = new int[SIZE];
        longsA  = new long[SIZE];

        bytesB  = new byte[SIZE];
        shortsB = new short[SIZE];
        charsB  = new char[SIZE];
        intsB   = new int[SIZE];
        longsB  = new long[SIZE];

        for (int i = 0; i < SIZE; i++) {
            bytesA[i]  = (byte) r.nextInt();
            shortsA[i] = (short) r.nextInt();
            charsA[i]  = (char) r.nextInt();
            intsA[i]   = r.nextInt();
            longsA[i]  = r.nextLong();
       }
    }

    @Benchmark
    public void rShiftByte() {
        for (int i = 0; i < SIZE; i++) {
            bytesB[i] = (byte) (bytesA[i] >> shiftCount);
        }
    }

    @Benchmark
    public void urShiftByte() {
        for (int i = 0; i < SIZE; i++) {
            bytesB[i] = (byte) (bytesA[i] >>> shiftCount);
        }
    }

    @Benchmark
    public void rShiftShort() {
        for (int i = 0; i < SIZE; i++) {
            shortsB[i] = (short) (shortsA[i] >> shiftCount);
        }
    }

    @Benchmark
    public void urShiftChar() {
        for (int i = 0; i < SIZE; i++) {
            charsB[i] = (char) (charsA[i] >>> shiftCount);
        }
    }

    @Benchmark
    public void rShiftInt() {
        for (int i = 0; i < SIZE; i++) {
            intsB[i] = intsA[i] >> shiftCount;
        }
    }

    @Benchmark
    public void urShiftInt() {
        for (int i = 0; i < SIZE; i++) {
            intsB[i] = intsA[i] >>> shiftCount;
        }
    }

    @Benchmark
    public void rShiftLong() {
        for (int i = 0; i < SIZE; i++) {
            longsB[i] = longsA[i] >> shiftCount;
        }
    }

    @Benchmark
    public void urShiftLong() {
        for (int i = 0; i < SIZE; i++) {
            longsB[i] = longsA[i] >>> shiftCount;
        }
    }
}
