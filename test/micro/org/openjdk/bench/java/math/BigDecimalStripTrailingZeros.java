/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class BigDecimalStripTrailingZeros {

    private BigDecimal xsPow, sPow, mPow, lPow, xlPow;

    @Setup
    public void setup() {
        xsPow = new BigDecimal(BigInteger.TEN.pow(1 << 4));
        sPow = new BigDecimal(BigInteger.TEN.pow(1 << 5));
        mPow = new BigDecimal(BigInteger.TEN.pow(1 << 10));
        lPow = new BigDecimal(BigInteger.TEN.pow(1 << 15));
        xlPow = new BigDecimal(BigInteger.TEN.pow(1 << 20));
    }

    /** Test BigDecimal.stripTrailingZeros() with 10^16  */
    @Benchmark
    @OperationsPerInvocation(1)
    public void testXS(Blackhole bh) {
        bh.consume(xsPow.stripTrailingZeros());
    }

    /** Test BigDecimal.stripTrailingZeros() with 10^32 */
    @Benchmark
    @OperationsPerInvocation(1)
    public void testS(Blackhole bh) {
        bh.consume(sPow.stripTrailingZeros());
    }

    /** Test BigDecimal.stripTrailingZeros() with 10^1024 */
    @Benchmark
    @OperationsPerInvocation(1)
    public void testM(Blackhole bh) {
        bh.consume(mPow.stripTrailingZeros());
    }

    /** Test BigDecimal.stripTrailingZeros() with 10^32_768 */
    @Benchmark
    @OperationsPerInvocation(1)
    public void testL(Blackhole bh) {
        bh.consume(lPow.stripTrailingZeros());
    }

    /** Test BigDecimal.stripTrailingZeros() with 10^1_048_576 */
    @Benchmark
    @OperationsPerInvocation(1)
    public void testXL(Blackhole bh) {
        bh.consume(xlPow.stripTrailingZeros());
    }
}
