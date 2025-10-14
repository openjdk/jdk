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
package org.openjdk.bench.java.lang;

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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(value = 3)
public class DoubleParse {

    private static final int N = 1_000_000;
    private static final int M = 100_000;

    private String[] toString;
    private String[] s1, s2, s4;

    @Setup
    public void setup() {
        Random r = new Random();

        toString = new String[N];
        for (int i = 0; i < toString.length;) {
            double v = Double.longBitsToDouble(r.nextLong());
            if (Double.isFinite(v)) {
                toString[i++] = Double.toString(v);
            }
        }

        s1 = new String[M];
        for (int i = 0; i < s1.length; ++i) {
            String f = "" + (r.nextLong() & 0x7fff_ffff_ffff_ffffL);
            s1[i] = f + "e" + (r.nextInt(600) - 300);
        }

        s2 = new String[M];
        for (int i = 0; i < s2.length; ++i) {
            String f = "" + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL);
            s2[i] = f + "e" + (r.nextInt(600) - 300);
        }

        s4 = new String[M];
        for (int i = 0; i < s4.length; ++i) {
            String f = "" + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL);
            s4[i] = f + "e" + (r.nextInt(600) - 300);
        }

    }

    @Benchmark
    @OperationsPerInvocation(N)
    public void testToString(Blackhole bh) {
        for (String s : toString) {
            try {
                bh.consume(Double.parseDouble(s));
            } catch (NumberFormatException _) {
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(M)
    public void testS1(Blackhole bh) {
        for (String s : s1) {
            try {
                bh.consume(Double.parseDouble(s));
            } catch (NumberFormatException _) {
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(M)
    public void testS2(Blackhole bh) {
        for (String s : s2) {
            try {
                bh.consume(Double.parseDouble(s));
            } catch (NumberFormatException _) {
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(M)
    public void testS4(Blackhole bh) {
        for (String s : s4) {
            try {
                bh.consume(Double.parseDouble(s));
            } catch (NumberFormatException _) {
            }
        }
    }

}
