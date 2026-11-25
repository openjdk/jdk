/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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
package org.openjdk.bench.java.lang;

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

import java.util.concurrent.TimeUnit;

/**
 * Overlapping (conjoint) System.arraycopy: source and destination are the same
 * array and their ranges overlap.
 *
 * <pre>{@code
 * Forward: dst below src, a forward copy is safe.
 *
 * src:           [================================]  (from = DISTANCE)
 * dst:  [================================]           (to   = 0)
 *                |<------ overlap ------>|
 *       0        DISTANCE
 *
 * Backward: dst above src, the copy must run from the end.
 *
 * src:  [================================]           (from = 0)
 * dst:           [================================]  (to   = DISTANCE)
 *                |<------ overlap ------>|
 *       0        DISTANCE
 * }</pre>
 *
 * <p>Schematic: one character per element. Every length exceeds DISTANCE, so
 * the ranges always overlap.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class ArrayCopyConjoint {

    // Distance in elements between the source and the destination position.
    private static final int DISTANCE = 9;

    @Param({"10", "16", "70", "150", "600", "1200"})
    int length;

    int forwardSrcPos, forwardDstPos, backwardSrcPos, backwardDstPos;
    byte[] byteArr;
    char[] charArr;
    int[] intArr;
    long[] longArr;

    @Setup
    public void setup() {
        if (length <= DISTANCE) {
            throw new IllegalArgumentException(
                    "length must exceed the position distance " + DISTANCE + ": " + length);
        }

        // dst below src: overlapping copy that may run forwards.
        forwardSrcPos = DISTANCE;
        forwardDstPos = 0;

        // dst above src: overlapping copy that must run backwards.
        backwardSrcPos = 0;
        backwardDstPos = DISTANCE;

        int size = length + DISTANCE;
        byteArr = new byte[size];
        charArr = new char[size];
        intArr = new int[size];
        longArr = new long[size];
    }

    @Benchmark
    public void testByteForward() {
        System.arraycopy(byteArr, forwardSrcPos, byteArr, forwardDstPos, length);
    }

    @Benchmark
    public void testByteBackward() {
        System.arraycopy(byteArr, backwardSrcPos, byteArr, backwardDstPos, length);
    }

    @Benchmark
    public void testCharForward() {
        System.arraycopy(charArr, forwardSrcPos, charArr, forwardDstPos, length);
    }

    @Benchmark
    public void testCharBackward() {
        System.arraycopy(charArr, backwardSrcPos, charArr, backwardDstPos, length);
    }

    @Benchmark
    public void testIntForward() {
        System.arraycopy(intArr, forwardSrcPos, intArr, forwardDstPos, length);
    }

    @Benchmark
    public void testIntBackward() {
        System.arraycopy(intArr, backwardSrcPos, intArr, backwardDstPos, length);
    }

    @Benchmark
    public void testLongForward() {
        System.arraycopy(longArr, forwardSrcPos, longArr, forwardDstPos, length);
    }

    @Benchmark
    public void testLongBackward() {
        System.arraycopy(longArr, backwardSrcPos, longArr, backwardDstPos, length);
    }
}
