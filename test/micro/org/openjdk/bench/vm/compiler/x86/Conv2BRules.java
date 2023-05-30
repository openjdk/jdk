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
package org.openjdk.bench.vm.compiler.x86;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations = 4, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
public class Conv2BRules {
    @Benchmark
    public void testNotEquals0(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < 128; i++) {
            int j = state.ints[i];
            blackhole.consume(j != 0);
        }
    }

    @Benchmark
    public void testEquals0(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < 128; i++) {
            int j = state.ints[i];
            blackhole.consume(j == 0);
        }
    }

    @Benchmark
    public void testEquals1(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < 128; i++) {
            int j = state.ints[i];
            blackhole.consume(j == 1);
        }
    }

    @Benchmark
    public void testNotEqualsNull(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < 128; i++) {
            Object o = state.objs[i];
            blackhole.consume(o != null);
        }
    }

    @Benchmark
    public void testEqualsNull(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < 128; i++) {
            Object o = state.objs[i];
            blackhole.consume(o == null);
        }
    }

    @State(Scope.Benchmark)
    public static class BenchState {
        int[] ints;
        Object[] objs;

        public BenchState() {

        }

        @Setup(Level.Iteration)
        public void setup() {
            Random random = new Random(1000);
            ints = new int[128];
            objs = new Object[128];
            for (int i = 0; i < 128; i++) {
                ints[i] = random.nextInt(3);
                objs[i] = random.nextInt(3) == 0 ? null : new Object();
            }
        }
    }
}
