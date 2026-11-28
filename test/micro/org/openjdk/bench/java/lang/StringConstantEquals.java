/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class StringConstantEquals {
    private static final int SIZE = 512;
    private static final Random RANDOM = new Random(100);

    private static final String STRING_0 = generateString(RANDOM, 0);
    private static final String STRING_1 = generateString(RANDOM, 1);
    private static final String STRING_3 = generateString(RANDOM, 3);
    private static final String STRING_4 = generateString(RANDOM, 4);
    private static final String STRING_12 = generateString(RANDOM, 12);
    private static final String STRING_15 = generateString(RANDOM, 15);
    private static final String STRING_16 = generateString(RANDOM, 16);
    private static final String STRING_17 = generateString(RANDOM, 17);

    private static String generateString(Random random, int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            char c = (char) ('a' + (random.nextInt(2)));
            sb.append(c);
        }

        return sb.toString();
    }

    @State(Scope.Benchmark)
    public static class BenchState {
        private final String[] strings0 = new String[SIZE];
        private final String[] strings1 = new String[SIZE];
        private final String[] strings3 = new String[SIZE];
        private final String[] strings4 = new String[SIZE];
        private final String[] strings12 = new String[SIZE];
        private final String[] strings15 = new String[SIZE];
        private final String[] strings16 = new String[SIZE];
        private final String[] strings17 = new String[SIZE];

        @Setup
        public void setup() {
            Random random = new Random(1000);
            for (int i = 0; i < SIZE; i++) {
                strings0[i] = generateString(random, 0);
                strings1[i] = generateString(random, 1);
                strings3[i] = generateString(random, 3);
                strings4[i] = generateString(random, 4);
                strings12[i] = generateString(random, 12);
                strings15[i] = generateString(random, 15);
                strings16[i] = generateString(random, 16);
                strings17[i] = generateString(random, 17);
            }
        }
    }

    @Benchmark
    public void string0(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(STRING_0.equals(state.strings0[i]));
        }
    }

    @Benchmark
    public void string1(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(STRING_1.equals(state.strings1[i]));
        }
    }

    @Benchmark
    public void string3(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(STRING_3.equals(state.strings3[i]));
        }
    }

    @Benchmark
    public void string4(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(STRING_4.equals(state.strings4[i]));
        }
    }

    @Benchmark
    public void string12(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(STRING_12.equals(state.strings12[i]));
        }
    }

    @Benchmark
    public void string15(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(STRING_15.equals(state.strings15[i]));
        }
    }

    @Benchmark
    public void string16(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(STRING_16.equals(state.strings16[i]));
        }
    }

    @Benchmark
    public void string17(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(STRING_17.equals(state.strings17[i]));
        }
    }
}

