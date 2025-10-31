/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Test various java.lang.Integer operations
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
public class Integers {

    @Param("500")
    private int size;

    private int bound;
    private String[] strings;
    private int[] intsTiny;
    private int[] intsSmall;
    private int[] intsBig;
    private int[] res;
    private int[] hexsTiny;
    private int[] hexsSmall;
    private int[] hexsBig;

    @Setup
    public void setup() {
        Random r  = new Random(0);
        bound = 50;
        strings   = new String[size];
        intsTiny  = new int[size];
        intsSmall = new int[size];
        intsBig   = new int[size];
        res       = new int[size];
        hexsTiny  = new int[size];
        hexsSmall = new int[size];
        hexsBig   = new int[size];
        for (int i = 0; i < size; i++) {
            strings[i] = "" + (r.nextInt(10000) - (5000));
            intsTiny[i] = r.nextInt(99);
            intsSmall[i] = 100 * i + i + 103;
            intsBig[i] = ((100 * i + i) << 24) + 4543 + i * 4;

            hexsTiny[i] = r.nextInt(0xFF);
            hexsSmall[i] = 0x100 * i + i + 0x103;
            hexsBig[i] = ((0x100 * i + i) << 24) + 0x4543 + i * 4;
        }
    }

    @Benchmark
    public void parseInt(Blackhole bh) {
        for (String s : strings) {
            bh.consume(Integer.parseInt(s));
        }
    }

    @Benchmark
    public void decode(Blackhole bh) {
        for (String s : strings) {
            bh.consume(Integer.decode(s));
        }
    }

    /** Performs toString on small values, just a couple of digits. */
    @Benchmark
    public void toStringSmall(Blackhole bh) {
        for (int i : intsSmall) {
            bh.consume(Integer.toString(i));
        }
    }

    /** Performs toString on very small values, just one or two digits. */
    @Benchmark
    public void toStringTiny(Blackhole bh) {
        for (int i : intsTiny) {
            bh.consume(Integer.toString(i));
        }
    }

    /** Performs toString on large values, roughly 10 digits. */
    @Benchmark
    public void toStringBig(Blackhole bh) {
        for (int i : intsBig) {
            bh.consume(Integer.toString(i));
        }
    }

    @Benchmark
    public void toHexStringTiny(Blackhole bh) {
        for (int i : hexsTiny) {
            bh.consume(Integer.toHexString(i));
        }
    }

    @Benchmark
    public void toHexStringSmall(Blackhole bh) {
        for (int i : hexsSmall) {
            bh.consume(Integer.toHexString(i));
        }
    }

    @Benchmark
    public void toHexStringBig(Blackhole bh) {
        for (int i : hexsBig) {
            bh.consume(Integer.toHexString(i));
        }
    }

    /** Performs expand on small values */
    @Benchmark
    public void expand(Blackhole bh) {
        for (int i : intsSmall) {
            bh.consume(Integer.expand(i, 0xFF00F0F0));
        }
    }

    /** Performs compress on large values */
    @Benchmark
    public void compress(Blackhole bh) {
        for (int i : intsBig) {
            bh.consume(Integer.compress(i, 0x000F0F1F));
        }
    }

    @Benchmark
    public void shiftRight(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            bh.consume(intsBig[i] >> intsSmall[i]);
        }
    }

    @Benchmark
    public void shiftURight(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            bh.consume(intsBig[i] >>> intsSmall[i]);
        }
    }

    @Benchmark
    public void shiftLeft(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            bh.consume(intsBig[i] << intsSmall[i]);
        }
    }

    @Benchmark
    public void compareUnsignedIndirect(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            int r = (Integer.compareUnsigned(intsSmall[i], bound - 16) < 0) ? 1 : 0;
            bh.consume(r);
        }
    }

    @Benchmark
    public void compareUnsignedDirect(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            int r = Integer.compareUnsigned(intsSmall[i], bound - 16);
            bh.consume(r);
        }
    }

    @Benchmark
    public void reverseBytes() {
        for (int i = 0; i < size; i++) {
            res[i] = Integer.reverseBytes(intsSmall[i]);
        }
    }

    @Benchmark
    public void reverse() {
        for (int i = 0; i < size; i++) {
            res[i] = Integer.reverse(intsSmall[i]);
        }
    }
}
