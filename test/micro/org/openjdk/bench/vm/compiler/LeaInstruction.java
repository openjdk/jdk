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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:LoopUnrollLimit=1"})
@State(Scope.Thread)
public class LeaInstruction {
    static final int ITERATION = 1000;

    int x, y;

    @Benchmark
    public void IS_D_int(Blackhole bh) {
        int x = this.x;
        for (int i = 0; i < ITERATION; i++) {
            x = x * 4 + 10;
        }
        bh.consume(x);
    }

    @Benchmark
    public void B_I_D_int(Blackhole bh) {
        int x = this.x, y = this.y;
        for (int i = 0; i < ITERATION; i++) {
            x = x + y + 10;
            y = x + y + 20;
        }
        bh.consume(x);
        bh.consume(y);
    }

    @Benchmark
    public void B_IS_int(Blackhole bh) {
        int x = this.x, y = this.y;
        for (int i = 0; i < ITERATION; i++) {
            x = x + y * 4;
            y = x + y * 8;
        }
        bh.consume(x);
        bh.consume(y);
    }

    @Benchmark
    public void B_IS_D_int(Blackhole bh) {
        int x = this.x, y = this.y;
        for (int i = 0; i < ITERATION; i++) {
            x = x + y * 4 + 10;
            y = x + y * 8 + 20;
        }
        bh.consume(x);
        bh.consume(y);
    }

    @Benchmark
    public void IS_D_long(Blackhole bh) {
        long x = this.x;
        for (int i = 0; i < ITERATION; i++) {
            x = x * 4 + 10;
        }
        bh.consume(x);
    }

    @Benchmark
    public void B_I_D_long(Blackhole bh) {
        long x = this.x, y = this.y;
        for (int i = 0; i < ITERATION; i++) {
            x = x + y + 10;
            y = x + y + 20;
        }
        bh.consume(x);
        bh.consume(y);
    }

    @Benchmark
    public void B_IS_long(Blackhole bh) {
        long x = this.x, y = this.y;
        for (int i = 0; i < ITERATION; i++) {
            x = x + y * 4;
            y = x + y * 8;
        }
        bh.consume(x);
        bh.consume(y);
    }

    @Benchmark
    public void B_IS_D_long(Blackhole bh) {
        long x = this.x, y = this.y;
        for (int i = 0; i < ITERATION; i++) {
            x = x + y * 4 + 10;
            y = x + y * 8 + 20;
        }
        bh.consume(x);
        bh.consume(y);
    }
}
