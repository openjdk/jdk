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
public class LeadingZeros {
    private static final int SIZE = 512;

    @Benchmark
    public void testInt(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            state.result[i] = Integer.numberOfLeadingZeros(state.ints[i]);
        }

        blackhole.consume(state.result);
    }

    @Benchmark
    public void testLong(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            state.result[i] = Long.numberOfLeadingZeros(state.longs[i]);
        }

        blackhole.consume(state.result);
    }

    @State(Scope.Benchmark)
    public static class BenchState {
        private final int[] ints = new int[SIZE];
        private final long[] longs = new long[SIZE];

        private final int[] result = new int[SIZE];

        private Random random;

        public BenchState() {
        }

        @Setup
        public void setup() {
            this.random = new Random(1000);

            for (int i = 0; i < SIZE; i++) {
                ints[i] = this.random.nextInt();

                longs[i] = this.random.nextLong();
            }
        }
    }
}
