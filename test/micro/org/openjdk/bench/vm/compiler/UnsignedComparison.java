/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@State(Scope.Thread)
public class UnsignedComparison {
    private static final int ITERATIONS = 1000;

    private static final int CONST_OPERAND = 4;
    private static final int INT_MIN = Integer.MIN_VALUE;
    private static final long LONG_MIN = Long.MIN_VALUE;

    int arg0 = 0, arg1 = 4;

    @Setup(Level.Invocation)
    public void toggle() {
        arg0 = (arg0 + 1) & 7;
    }

    @Benchmark
    public void intVarDirect(Blackhole bh) {
        for (int i = 0; i < ITERATIONS; i++) {
            bh.consume(arg0 + INT_MIN < arg1 + INT_MIN);
        }
    }

    @Benchmark
    public void intVarLibLT(Blackhole bh) {
        for (int i = 0; i < ITERATIONS; i++) {
            bh.consume(Integer.compareUnsigned(arg0, arg1) < 0);
        }
    }

    @Benchmark
    public void intVarLibGT(Blackhole bh) {
        for (int i = 0; i < ITERATIONS; i++) {
            bh.consume(Integer.compareUnsigned(arg0, arg1) > 0);
        }
    }

    @Benchmark
    public void intConDirect(Blackhole bh) {
        for (int i = 0; i < ITERATIONS; i++) {
            bh.consume(arg0 + INT_MIN < CONST_OPERAND + INT_MIN);
        }
    }

    @Benchmark
    public void intConLibLT(Blackhole bh) {
        for (int i = 0; i < ITERATIONS; i++) {
            bh.consume(Integer.compareUnsigned(arg0, CONST_OPERAND) < 0);
        }
    }

    @Benchmark
    public void intConLibGT(Blackhole bh) {
        for (int i = 0; i < ITERATIONS; i++) {
            bh.consume(Integer.compareUnsigned(arg0, CONST_OPERAND) > 0);
        }
    }

    @Benchmark
    public void longVarDirect(Blackhole bh) {
        for (int i = 0; i < ITERATIONS; i++) {
            bh.consume(arg0 + LONG_MIN < arg1 + LONG_MIN);
        }
    }

    @Benchmark
    public void longVarLibLT(Blackhole bh) {
        for (int i = 0; i < ITERATIONS; i++) {
            bh.consume(Long.compareUnsigned(arg0, arg1) < 0);
        }
    }

    @Benchmark
    public void longVarLibGT(Blackhole bh) {
        for (int i = 0; i < ITERATIONS; i++) {
            bh.consume(Long.compareUnsigned(arg0, arg1) > 0);
        }
    }

    @Benchmark
    public void longConDirect(Blackhole bh) {
        for (int i = 0; i < ITERATIONS; i++) {
            bh.consume(arg0 + LONG_MIN < CONST_OPERAND + LONG_MIN);
        }
    }

    @Benchmark
    public void longConLibLT(Blackhole bh) {
        for (int i = 0; i < ITERATIONS; i++) {
            bh.consume(Long.compareUnsigned(arg0, CONST_OPERAND) < 0);
        }
    }

    @Benchmark
    public void longConLibGT(Blackhole bh) {
        for (int i = 0; i < ITERATIONS; i++) {
            bh.consume(Long.compareUnsigned(arg0, CONST_OPERAND) > 0);
        }
    }
}
