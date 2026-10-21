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

package org.openjdk.bench.vm.compiler;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Conditional arithmetic and logical operations that can use Zicond on RISC-V.
 * Force conditional moves and disable vectorization to exercise scalar CMove
 * optimizations. On hardware supporting Zicond, compare -XX:+UseZicond and
 * -XX:-UseZicond using the same inputs and JVM options. To measure the effect
 * of a compiler change, compare builds with -XX:+UseZicond in both.
 * Load both operands before the conditional expression so that conditional
 * array loads do not prevent C2 from forming a CMove.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 4, time = 2)
@Fork(value = 3, jvmArgsAppend = {"-XX:+UseCMoveUnconditionally", "-XX:-UseSuperWord"})
@State(Scope.Thread)
public class ConditionalArithmetic {
    private static final int SIZE = 1024;

    @Param({"50", "90"})
    public int zeroPercent;

    private int[] basesI;
    private int[] valuesI;
    private int[] conditionsI;
    private long[] basesL;
    private long[] valuesL;
    private long[] conditionsL;

    @Setup
    public void setup() {
        Random random = new Random(42);
        basesI = new int[SIZE];
        valuesI = new int[SIZE];
        conditionsI = new int[SIZE];
        basesL = new long[SIZE];
        valuesL = new long[SIZE];
        conditionsL = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            basesI[i] = random.nextInt();
            valuesI[i] = random.nextInt();
            basesL[i] = random.nextLong();
            valuesL[i] = random.nextLong();
            boolean zero = random.nextInt(100) < zeroPercent;
            int nonZeroI = random.nextInt() | 1;
            long nonZeroL = random.nextLong() | 1L;
            conditionsI[i] = zero ? 0 : nonZeroI;
            conditionsL[i] = zero ? 0L : nonZeroL;
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int addEqI() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            int base = basesI[i];
            int value = valuesI[i];
            sum += conditionsI[i] == 0 ? base + value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int addNeI() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            int base = basesI[i];
            int value = valuesI[i];
            sum += conditionsI[i] != 0 ? base + value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int subEqI() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            int base = basesI[i];
            int value = valuesI[i];
            sum += conditionsI[i] == 0 ? base - value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int subNeI() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            int base = basesI[i];
            int value = valuesI[i];
            sum += conditionsI[i] != 0 ? base - value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int andEqI() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            int base = basesI[i];
            int value = valuesI[i];
            sum += conditionsI[i] == 0 ? base & value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int andNeI() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            int base = basesI[i];
            int value = valuesI[i];
            sum += conditionsI[i] != 0 ? base & value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int orEqI() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            int base = basesI[i];
            int value = valuesI[i];
            sum += conditionsI[i] == 0 ? base | value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int orNeI() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            int base = basesI[i];
            int value = valuesI[i];
            sum += conditionsI[i] != 0 ? base | value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int xorEqI() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            int base = basesI[i];
            int value = valuesI[i];
            sum += conditionsI[i] == 0 ? base ^ value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int xorNeI() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            int base = basesI[i];
            int value = valuesI[i];
            sum += conditionsI[i] != 0 ? base ^ value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long addEqL() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            long base = basesL[i];
            long value = valuesL[i];
            sum += conditionsL[i] == 0 ? base + value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long addNeL() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            long base = basesL[i];
            long value = valuesL[i];
            sum += conditionsL[i] != 0 ? base + value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long subEqL() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            long base = basesL[i];
            long value = valuesL[i];
            sum += conditionsL[i] == 0 ? base - value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long subNeL() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            long base = basesL[i];
            long value = valuesL[i];
            sum += conditionsL[i] != 0 ? base - value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long andEqL() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            long base = basesL[i];
            long value = valuesL[i];
            sum += conditionsL[i] == 0 ? base & value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long andNeL() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            long base = basesL[i];
            long value = valuesL[i];
            sum += conditionsL[i] != 0 ? base & value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long orEqL() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            long base = basesL[i];
            long value = valuesL[i];
            sum += conditionsL[i] == 0 ? base | value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long orNeL() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            long base = basesL[i];
            long value = valuesL[i];
            sum += conditionsL[i] != 0 ? base | value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long xorEqL() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            long base = basesL[i];
            long value = valuesL[i];
            sum += conditionsL[i] == 0 ? base ^ value : base;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long xorNeL() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            long base = basesL[i];
            long value = valuesL[i];
            sum += conditionsL[i] != 0 ? base ^ value : base;
        }
        return sum;
    }
}
