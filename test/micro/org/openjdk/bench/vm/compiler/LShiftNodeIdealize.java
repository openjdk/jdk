/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
public class LShiftNodeIdealize {
    private static final int SIZE = 3000;

    @Benchmark
    public void testShiftInt(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume((i >> 4) << 8);
        }
    }

    @Benchmark
    public void testShiftInt2(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume((i >> 8) << 4);
        }
    }

    @Benchmark
    public void testShiftAndInt(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(((i >> 4) & 0x01) << 8);
        }
    }

    @Benchmark
    public void testShiftLong(Blackhole blackhole) {
        for (long i = 0; i < SIZE; i++) {
            blackhole.consume((i >> 4L) << 8L);
        }
    }

    @Benchmark
    public void testShiftLong2(Blackhole blackhole) {
        for (long i = 0; i < SIZE; i++) {
            blackhole.consume((i >> 8L) << 4L);
        }
    }

    @Benchmark
    public void testShiftAndLong(Blackhole blackhole) {
        for (long i = 0; i < SIZE; i++) {
            blackhole.consume(((i >> 4L) & 0x01L) << 8L);
        }
    }

    @Benchmark
    public void testRgbaToAbgr(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < 64; i++) {
            blackhole.consume(rgbaToAbgr(state.ints[i]));
        }
    }

    private static int rgbaToAbgr(int i) {
        int r = i & 0xFF;
        int g = (i & 0xFF00) >> 8;
        int b = (i & 0xFF0000) >> 16;
        int a = (i & 0xFF000000) >> 24;

        return (r << 24) | (g << 16) | (b << 8) | a;
    }

    @State(Scope.Benchmark)
    public static class BenchState {
        int[] ints;
        Random random = new Random();

        public BenchState() {

        }

        @Setup
        public void setup() {
            ints = new int[64];
            for (int i = 0; i < 64; i++) {
                ints[i] = random.nextInt();
            }
        }
    }
}
