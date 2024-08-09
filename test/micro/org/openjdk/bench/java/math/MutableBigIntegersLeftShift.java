/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.math;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.math.MutableBigIntegerBox;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class MutableBigIntegersLeftShift {

    private MutableBigIntegerBox[] shiftArray;
    private static final int TESTSIZE = 1000;

    @Setup
    public void setup() {
        Random r = new Random(1123);
        int numbits = r.nextInt(16384);

        shiftArray = new MutableBigIntegerBox[TESTSIZE]; /*
         * Each array entry is atmost 16k bits
         * in size
         */

        for (int i = 0; i < TESTSIZE; i++) {
            shiftArray[i] = new MutableBigIntegerBox(new BigInteger(numbits, r));
        }
    }

    /** Invokes the shiftLeft method of BigInteger with different values. */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testLeftShift(Blackhole bh) {
        Random rand = new Random();
        for (MutableBigIntegerBox s : shiftArray) {
            int shift = rand.nextInt((int) s.bitLength());
            bh.consume(s.shiftLeft(shift));
        }
    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @State(Scope.Thread)
    @Warmup(iterations = 5, time = 1)
    @Measurement(iterations = 5, time = 1)
    @Fork(value = 3)
    public static class SmallShifts {

        @Param({"32", "128", "256"})
        private int maxNumbits;

        /*
         * Small numbers, bit length in range [maxNumbits - 31, maxNumbits]
         */
        MutableBigIntegerBox[] smallShiftArray = new MutableBigIntegerBox[TESTSIZE];

        @Setup
        public void setup() {
            Random r = new Random(1123);
            for (int i = 0; i < TESTSIZE; i++) {
                int value = Math.abs(r.nextInt());
                smallShiftArray[i] = new MutableBigIntegerBox(new BigInteger(Math.max(maxNumbits - value % 32, 0), r));
            }
        }

        /** Invokes the shiftLeft method of small BigInteger with different values. */
        @Benchmark
        @OperationsPerInvocation(TESTSIZE)
        public void testLeftShift(Blackhole bh) {
            Random rand = new Random();
            for (MutableBigIntegerBox s : smallShiftArray) {
                int shift = rand.nextInt((int) s.bitLength());
                bh.consume(s.shiftLeft(shift));
            }
        }
    }
}
