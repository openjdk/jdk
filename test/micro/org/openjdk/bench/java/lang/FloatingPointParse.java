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

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 3, time = 3)
@Fork(value = 3)
public class FloatingPointParse {

    private static final int N = 1_000_000;
    private static final int M = 100_000;

    private String[] doubleToString, floatToString, s1, s2, s4, s10;

    @Setup
    public void setup() {
        Random r = new Random();

        doubleToString = new String[N];
        for (int i = 0; i < doubleToString.length;) {
            double v = Double.longBitsToDouble(r.nextLong());
            if (Double.isFinite(v)) {
                doubleToString[i++] = Double.toString(v);
            }
        }

        floatToString = new String[N];
        for (int i = 0; i < floatToString.length;) {
            float v = Float.intBitsToFloat(r.nextInt());
            if (Float.isFinite(v)) {
                floatToString[i++] = Float.toString(v);
            }
        }

        s1 = new String[M];
        for (int i = 0; i < s1.length; ++i) {
            String f = "0." + (r.nextLong() & 0x7fff_ffff_ffff_ffffL);
            s1[i] = f + "e" + (r.nextInt(600) - 300);
        }

        s2 = new String[M];
        for (int i = 0; i < s2.length; ++i) {
            String f = "0." + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL);
            s2[i] = f + "e" + (r.nextInt(600) - 300);
        }

        s4 = new String[M];
        for (int i = 0; i < s4.length; ++i) {
            String f = "0." + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL);
            s4[i] = f + "e" + (r.nextInt(600) - 300);
        }

        s10 = new String[M];
        for (int i = 0; i < s10.length; ++i) {
            String f = "0." + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL)
                    + (r.nextLong() & 0x7fff_ffff_ffff_ffffL);
            s10[i] = f + "e" + (r.nextInt(600) - 300);
        }

    }

    @Benchmark
    @OperationsPerInvocation(N)
    public void parseDoubleToString(Blackhole bh) {
        for (String s : doubleToString) {
            bh.consume(Double.parseDouble(s));
        }
    }

    @Benchmark
    @OperationsPerInvocation(M)
    public void parseDoubleS1(Blackhole bh) {
        for (String s : s1) {
            bh.consume(Double.parseDouble(s));
        }
    }

    @Benchmark
    @OperationsPerInvocation(M)
    public void parseDoubleS2(Blackhole bh) {
        for (String s : s2) {
            bh.consume(Double.parseDouble(s));
        }
    }

    @Benchmark
    @OperationsPerInvocation(M)
    public void parseDoubleS4(Blackhole bh) {
        for (String s : s4) {
            bh.consume(Double.parseDouble(s));
        }
    }

    @Benchmark
    @OperationsPerInvocation(M)
    public void parseDoubleS10(Blackhole bh) {
        for (String s : s10) {
            bh.consume(Double.parseDouble(s));
        }
    }

    @Benchmark
    @OperationsPerInvocation(N)
    public void parseFloatToString(Blackhole bh) {
        for (String s : floatToString) {
            bh.consume(Float.parseFloat(s));
        }
    }

    @Benchmark
    @OperationsPerInvocation(M)
    public void parseFloatS1(Blackhole bh) {
        for (String s : s1) {
            bh.consume(Float.parseFloat(s));
        }
    }

    @Benchmark
    @OperationsPerInvocation(M)
    public void parseFloatS2(Blackhole bh) {
        for (String s : s2) {
            bh.consume(Float.parseFloat(s));
        }
    }

    @Benchmark
    @OperationsPerInvocation(M)
    public void parseFloatS4(Blackhole bh) {
        for (String s : s4) {
            bh.consume(Float.parseFloat(s));
        }
    }

    @Benchmark
    @OperationsPerInvocation(M)
    public void parseFloatS10(Blackhole bh) {
        for (String s : s10) {
            bh.consume(Float.parseFloat(s));
        }
    }

}
