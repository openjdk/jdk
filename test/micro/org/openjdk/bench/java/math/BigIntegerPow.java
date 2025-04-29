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
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class BigIntegerPow {

    private BigInteger[] xsArray, sArray, mArray, lArray, xlArray;
    private int xsExp, sExp, mExp, lExp, xlExp;
    private static final int TESTSIZE = 2;

    /*
     * You can run this test via the command line:
     *    $ mvn clean install
     *    $ java -jar target/benchmarks.jar BigIntegerPow -prof gc
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BigIntegerPow.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(opt).run();
    }

    @Setup
    public void setup() {
        Random r = new Random(1123);

        xsExp = (1 << 20) - 1;
        xsArray = new BigInteger[TESTSIZE]; /*
         * Each array entry is atmost 64 bits
         * in size
         */
        sExp = (1 << 18) - 1;
        sArray = new BigInteger[TESTSIZE]; /*
         * Each array entry is atmost 256 bits
         * in size
         */
        mExp = (1 << 16) - 1;
        mArray = new BigInteger[TESTSIZE]; /*
         * Each array entry is atmost 1024 bits
         * in size
         */
        lExp = (1 << 14) - 1;
        lArray = new BigInteger[TESTSIZE]; /*
         * Each array entry is atmost 4096 bits
         * in size
         */
        xlExp = (1 << 12) - 1;
        xlArray = new BigInteger[TESTSIZE]; /*
         * Each array entry is atmost 16384 bits
         * in size
         */

        for (int i = 0; i < TESTSIZE; i++) {
            xsArray[i] = new BigInteger(64, r);
            sArray[i] = new BigInteger(256, r);
            mArray[i] = new BigInteger(1024, r);
            lArray[i] = new BigInteger(4096, r);
            xlArray[i] = new BigInteger(16384, r);
        }
    }

    /** Test BigInteger.pow() with numbers long at most 64 bits  */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowXS(Blackhole bh) {
        for (BigInteger s : xsArray) {
            bh.consume(s.pow(xsExp));
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

    /** Test BigInteger.pow() with numbers long at most 1024 bits */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowM(Blackhole bh) {
        for (BigInteger s : mArray) {
            bh.consume(s.pow(mExp));
        }
    }

    /** Test BigInteger.pow() with numbers long at most 4096 bits */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowL(Blackhole bh) {
        for (BigInteger s : lArray) {
            bh.consume(s.pow(lExp));
        }
    }

    /** Test BigInteger.pow() with numbers long at most 16384 bits */
    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testPowXL(Blackhole bh) {
        for (BigInteger s : xlArray) {
            bh.consume(s.pow(xlExp));
        }
    }
}
