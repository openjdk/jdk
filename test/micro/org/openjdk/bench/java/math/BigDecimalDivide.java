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
import java.math.BigDecimal;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class BigDecimalDivide {

    private BigDecimal[][] xsArray, sArray, mArray, lArray, xlArray;
    private static final int TESTSIZE = 1000;

    @Setup
    public void setup() {
        Random r = new Random(1123);

        xsArray = new BigDecimal[TESTSIZE][2]; /*
         * Each array entry is at most 64 bits
         * in size
         */
        sArray = new BigDecimal[TESTSIZE][2]; /*
         * Each array entry is at most 256 bits
         * in size
         */
        mArray = new BigDecimal[TESTSIZE][2]; /*
         * Each array entry is at most 1024 bits
         * in size
         */
        lArray = new BigDecimal[TESTSIZE][2]; /*
         * Each array entry is at most 4096 bits
         * in size
         */
        xlArray = new BigDecimal[TESTSIZE][2]; /*
         * Each array entry is at most 16384 bits
         * in size
         */

        for (int i = 0; i < TESTSIZE; i++) {
            xsArray[i][0] = new BigDecimal(new BigInteger(r.nextInt(64), r));
            xsArray[i][1] = new BigDecimal(new BigInteger(r.nextInt(64), r));
            sArray[i][0] = new BigDecimal(new BigInteger(r.nextInt(256), r));
            sArray[i][1] = new BigDecimal(new BigInteger(r.nextInt(256), r));
            mArray[i][0] = new BigDecimal(new BigInteger(r.nextInt(1024), r));
            mArray[i][1] = new BigDecimal(new BigInteger(r.nextInt(1024), r));
            lArray[i][0] = new BigDecimal(new BigInteger(r.nextInt(4096), r));
            lArray[i][1] = new BigDecimal(new BigInteger(r.nextInt(4096), r));
            xlArray[i][0] = new BigDecimal(new BigInteger(r.nextInt(16384), r));
            xlArray[i][1] = new BigDecimal(new BigInteger(r.nextInt(16384), r));
        }
    }

    /** Test BigDecimal.divide(BigDecimal) with numbers long at most 64 bits  */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testExactDivideXS(Blackhole bh) {
        for (BigDecimal[] s : xsArray) {
            try {
                bh.consume(s[0].divide(s[1]));
            } catch (ArithmeticException e) {
            }
        }
    }

    /** Test BigDecimal.divide(BigDecimal) with numbers long at most 256 bits */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testExactDivideS(Blackhole bh) {
        for (BigDecimal[] s : sArray) {
            try {
                bh.consume(s[0].divide(s[1]));
            } catch (ArithmeticException e) {
            }
        }
    }

    /** Test BigDecimal.divide(BigDecimal) with numbers long at most 1024 bits */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testExactDivideM(Blackhole bh) {
        for (BigDecimal[] s : mArray) {
            try {
                bh.consume(s[0].divide(s[1]));
            } catch (ArithmeticException e) {
            }
        }
    }

    /** Test BigDecimal.divide(BigDecimal) with numbers long at most 4096 bits */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testExactDivideL(Blackhole bh) {
        for (BigDecimal[] s : lArray) {
            try {
                bh.consume(s[0].divide(s[1]));
            } catch (ArithmeticException e) {
            }
        }
    }

    /** Test BigDecimal.divide(BigDecimal) with numbers long at most 16384 bits */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testExactDivideXL(Blackhole bh) {
        for (BigDecimal[] s : xlArray) {
            try {
                bh.consume(s[0].divide(s[1]));
            } catch (ArithmeticException e) {
            }
        }
    }
}
