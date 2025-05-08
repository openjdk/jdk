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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.profile.GCProfiler;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 1, time = 1)
@Fork(value = 3)
public class BigIntegerPow {

    private static final int TESTSIZE = 1;

    private int xsExp = (1 << 20) - 1;
    /* Each array entry is atmost 64 bits in size */
    private BigInteger[] xsArray = new BigInteger[TESTSIZE];

    private int sExp = (1 << 18) - 1;
    /* Each array entry is atmost 256 bits in size */
    private BigInteger[] sArray = new BigInteger[TESTSIZE];

    private int mExp = (1 << 16) - 1;
    /* Each array entry is atmost 1024 bits in size */
    private BigInteger[] mArray = new BigInteger[TESTSIZE];

    private int lExp = (1 << 14) - 1;
    /* Each array entry is atmost 4096 bits in size */
    private BigInteger[] lArray = new BigInteger[TESTSIZE];

    private int xlExp = (1 << 12) - 1;
    /* Each array entry is atmost 16384 bits in size */
    private BigInteger[] xlArray = new BigInteger[TESTSIZE];

    private int[] randomExps;

    /*
     * You can run this test via the command line:
     *    $ make test TEST="micro:java.math.BigIntegerPow" MICRO="OPTIONS=-prof gc"
     */

    @Setup
    public void setup() {
        Random r = new Random(1123);

        randomExps = new int[TESTSIZE];
        for (int i = 0; i < TESTSIZE; i++) {
            xsArray[i] = new BigInteger(64, r);
            sArray[i] = new BigInteger(256, r);
            mArray[i] = new BigInteger(1024, r);
            lArray[i] = new BigInteger(4096, r);
            xlArray[i] = new BigInteger(16384, r);
            randomExps[i] = r.nextInt(1 << 12);
        }
    }

    /** Test BigInteger.pow() with numbers long at most 64 bits  */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowXS(Blackhole bh) {
        for (BigInteger xs : xsArray) {
            bh.consume(xs.pow(xsExp));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowXSRandomExps(Blackhole bh) {
        int i = 0;
        for (BigInteger xs : xsArray) {
            bh.consume(xs.pow(randomExps[i++]));
        }
    }

    /** Test BigInteger.pow() with numbers long at most 256 bits */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowS(Blackhole bh) {
        for (BigInteger s : sArray) {
            bh.consume(s.pow(sExp));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowSRandomExps(Blackhole bh) {
        int i = 0;
        for (BigInteger s : sArray) {
            bh.consume(s.pow(randomExps[i++]));
        }
    }

    /** Test BigInteger.pow() with numbers long at most 1024 bits */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowM(Blackhole bh) {
        for (BigInteger m : mArray) {
            bh.consume(m.pow(mExp));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowMRandomExps(Blackhole bh) {
        int i = 0;
        for (BigInteger m : mArray) {
            bh.consume(m.pow(randomExps[i++]));
        }
    }

    /** Test BigInteger.pow() with numbers long at most 4096 bits */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowL(Blackhole bh) {
        for (BigInteger l : lArray) {
            bh.consume(l.pow(lExp));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowLRandomExps(Blackhole bh) {
        int i = 0;
        for (BigInteger l : lArray) {
            bh.consume(l.pow(randomExps[i++]));
        }
    }

    /** Test BigInteger.pow() with numbers long at most 16384 bits */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowXL(Blackhole bh) {
        for (BigInteger xl : xlArray) {
            bh.consume(xl.pow(xlExp));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowXLRandomExps(Blackhole bh) {
        int i = 0;
        for (BigInteger xl : xlArray) {
            bh.consume(xl.pow(randomExps[i++]));
        }
    }
}
