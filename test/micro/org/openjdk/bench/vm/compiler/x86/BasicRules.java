/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(value = 3, jvmArgsAppend = "-XX:-UseSuperWord")
@Warmup(time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(time = 1, timeUnit = TimeUnit.SECONDS)
public class BasicRules {
    static final int[] INT_ARRAY = new int[1024];
    static final long[] LONG_ARRAY = new long[1024];
    static final int INT_IMM = 100;
    static final long LONG_IMM = 100;

    @Benchmark
    public void andL_rReg_imm255(Blackhole bh) {
        for (int i = 0; i < LONG_ARRAY.length; i++) {
            long v = LONG_ARRAY[i];
            bh.consume(v);
            bh.consume(v & 0xFFL);
        }
    }

    @Benchmark
    public void andL_rReg_imm65535(Blackhole bh) {
        for (int i = 0; i < LONG_ARRAY.length; i++) {
            long v = LONG_ARRAY[i];
            bh.consume(v);
            bh.consume(v & 0xFFFFL);
        }
    }

    @Benchmark
    public void add_mem_con(Blackhole bh) {
        for (int i = 0; i < INT_ARRAY.length; i++) {
            bh.consume(INT_ARRAY[i] + 100);
        }
    }

    @Benchmark
    public void divL_10(Blackhole bh) {
        for (int i = 0; i < LONG_ARRAY.length; i++) {
            bh.consume(LONG_ARRAY[i] / 10L);
        }
    }

    @Benchmark
    public void salI_rReg_1(Blackhole bh) {
        for (int i = 0; i < INT_ARRAY.length; i++) {
            bh.consume(INT_ARRAY[i] << 1);
        }
    }

    @Benchmark
    public void sarI_rReg_1(Blackhole bh) {
        for (int i = 0; i < INT_ARRAY.length; i++) {
            bh.consume(INT_ARRAY[i] >> 1);
        }
    }

    @Benchmark
    public void shrI_rReg_1(Blackhole bh) {
        for (int i = 0; i < INT_ARRAY.length; i++) {
            bh.consume(INT_ARRAY[i] >>> 1);
        }
    }

    @Benchmark
    public void salL_rReg_1(Blackhole bh) {
        for (int i = 0; i < LONG_ARRAY.length; i++) {
            bh.consume(LONG_ARRAY[i] << 1);
        }
    }

    @Benchmark
    public void sarL_rReg_1(Blackhole bh) {
        for (int i = 0; i < LONG_ARRAY.length; i++) {
            bh.consume(LONG_ARRAY[i] >> 1);
        }
    }

    @Benchmark
    public void shrL_rReg_1(Blackhole bh) {
        for (int i = 0; i < LONG_ARRAY.length; i++) {
            bh.consume(LONG_ARRAY[i] >>> 1);
        }
    }

    @Benchmark
    public void subI_rReg_imm(Blackhole bh) {
        for (int i = 0; i < INT_ARRAY.length; i++) {
            bh.consume(INT_ARRAY[i] - INT_IMM);
        }
    }

    @Benchmark
    public void subL_rReg_imm(Blackhole bh) {
        for (int i = 0; i < LONG_ARRAY.length; i++) {
            bh.consume(LONG_ARRAY[i] - LONG_IMM);
        }
    }
}

