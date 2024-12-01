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

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class BigIntegers {

    private BigInteger[] hugeArray, largeArray, smallArray, shiftArray;
    public String[] dummyStringArray;
    public Object[] dummyArr;
    private static final int TESTSIZE = 1000;

    @Setup
    public void setup() {
        Random r = new Random(1123);
        int numbits = r.nextInt(16384);

        hugeArray = new BigInteger[TESTSIZE]; /*
         * Huge numbers larger than
         * MAX_LONG
         */
        largeArray = new BigInteger[TESTSIZE]; /*
         * Large numbers less than
         * MAX_LONG but larger than
         * MAX_INT
         */
        smallArray = new BigInteger[TESTSIZE]; /*
         * Small number less than
         * MAX_INT
         */
        shiftArray = new BigInteger[TESTSIZE]; /*
         * Each array entry is atmost 16k bits
         * in size
         */

        dummyStringArray = new String[TESTSIZE];
        dummyArr = new Object[TESTSIZE];

        for (int i = 0; i < TESTSIZE; i++) {
            int value = Math.abs(r.nextInt());

            hugeArray[i] = new BigInteger("" + ((long) value + (long) Integer.MAX_VALUE)
                    + ((long) value + (long) Integer.MAX_VALUE));
            largeArray[i] = new BigInteger("" + ((long) value + (long) Integer.MAX_VALUE));
            smallArray[i] = new BigInteger("" + ((long) value / 1000));
            shiftArray[i] = new BigInteger(numbits, r);
        }
    }

    /** Test BigInteger.toString() with huge numbers larger than MAX_LONG */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testHugeToString(Blackhole bh) {
        for (BigInteger s : hugeArray) {
            bh.consume(s.toString());
        }
    }

    /** Test BigInteger.toString() with large numbers less than MAX_LONG but larger than MAX_INT */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testLargeToString(Blackhole bh) {
        for (BigInteger s : largeArray) {
            bh.consume(s.toString());
        }
    }

    /** Test BigInteger.toString() with small numbers less than MAX_INT */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testSmallToString(Blackhole bh) {
        for (BigInteger s : smallArray) {
            bh.consume(s.toString());
        }
    }

    /** Invokes the multiply method of BigInteger with various different values. */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testMultiply(Blackhole bh) {
        BigInteger tmp = null;
        for (BigInteger s : hugeArray) {
            if (tmp == null) {
                tmp = s;
                continue;
            }
            tmp = tmp.multiply(s);
        }
        bh.consume(tmp);
    }

    /** Test divide with huge/small numbers */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE * TESTSIZE)
    public void testHugeSmallDivide(Blackhole bh) {
        for (BigInteger s : hugeArray) {
            for (BigInteger t : smallArray) {
                bh.consume(s.divide(t));
            }
        }
    }

    /** Test divide with large/small numbers */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE * TESTSIZE)
    public void testLargeSmallDivide(Blackhole bh) {
        for (BigInteger s : largeArray) {
            for (BigInteger t : smallArray) {
                bh.consume(s.divide(t));
            }
        }
    }

    /** Test divide with huge/large numbers */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE * TESTSIZE)
    public void testHugeLargeDivide(Blackhole bh) {
        for (BigInteger s : hugeArray) {
            for (BigInteger t : largeArray) {
                bh.consume(s.divide(t));
            }
        }
    }

    /** Invokes the multiply method of BigInteger with various different values. */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testAdd(Blackhole bh) {
        BigInteger tmp = null;
        for (BigInteger s : hugeArray) {
            if (tmp == null) {
                tmp = s;
                continue;
            }
            tmp = tmp.add(s);
        }
        bh.consume(tmp);
    }

    /** Invokes the shiftLeft method of BigInteger with different values. */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testLeftShift(Blackhole bh) {
        Random rand = new Random();
        int shift = rand.nextInt(30) + 1;
        BigInteger tmp = null;
        for (BigInteger s : shiftArray) {
            if (tmp == null) {
                tmp = s;
                continue;
            }
            tmp = tmp.shiftLeft(shift);
        }
        bh.consume(tmp);
    }

    /** Invokes the shiftRight method of BigInteger with different values. */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testRightShift(Blackhole bh) {
        Random rand = new Random();
        int shift = rand.nextInt(30) + 1;
        BigInteger tmp = null;
        for (BigInteger s : shiftArray) {
            if (tmp == null) {
                tmp = s;
                continue;
            }
            tmp = tmp.shiftRight(shift);
        }
        bh.consume(tmp);
    }

    /** Invokes the gcd method of BigInteger with different values. */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testGcd(Blackhole bh) {
        for (int i = 0; i < TESTSIZE; i++) {
            BigInteger i1 = shiftArray[TESTSIZE - i - 1];
            BigInteger i2 = shiftArray[i];
            bh.consume(i2.gcd(i1));
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
         * Small numbers, bits count in range [maxNumbits - 31, maxNumbits]
         */
        BigInteger[] smallShiftArray = new BigInteger[TESTSIZE];

        @Setup
        public void setup() {
            Random r = new Random(1123);
            for (int i = 0; i < TESTSIZE; i++) {
                int value = Math.abs(r.nextInt());
                smallShiftArray[i] = new BigInteger(Math.max(maxNumbits - value % 32, 0), r);
            }
        }

        /** Invokes the shiftLeft method of small BigInteger with different values. */
        @Benchmark
        @OperationsPerInvocation(TESTSIZE)
        public void testLeftShift(Blackhole bh) {
            Random rand = new Random();
            int shift = rand.nextInt(30) + 1;
            for (BigInteger s : smallShiftArray) {
                bh.consume(s.shiftLeft(shift));
            }
        }

        /** Invokes the shiftRight method of small BigInteger with different values. */
        @Benchmark
        @OperationsPerInvocation(TESTSIZE)
        public void testRightShift(Blackhole bh) {
            Random rand = new Random();
            int shift = rand.nextInt(30) + 1;
            for (BigInteger s : smallShiftArray) {
                bh.consume(s.shiftRight(shift));
            }
        }
    }
}
