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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1)
@State(Scope.Thread)
public class LeaPeephole {
    static final int ITERATION = 1000;

    int x, y;

    @Benchmark
    public void B_I_int(Blackhole bh) {
        int x = this.x;
        int y = this.y;
        for (int i = 0; i < ITERATION; i++) {
            int x1 = x + y;
            x = x1 + y;
            y = x1 + x;
        }
        bh.consume(x);
        bh.consume(y);
    }

    @Benchmark
    public void B_D_int(Blackhole bh) {
        int x = this.x;
        int y = this.y;
        for (int i = 0; i < ITERATION; i++) {
            bh.consume(x + 10);
            bh.consume(x + 20);
            bh.consume(x + 30);
            bh.consume(y + 10);
            bh.consume(y + 20);
            bh.consume(y + 30);
            x = x >> 1;
            y = y >> 2;
        }
    }

    @Benchmark
    public void I_S_int(Blackhole bh) {
        int x = this.x;
        int y = this.y;
        for (int i = 0; i < ITERATION; i++) {
            bh.consume(x << 1);
            bh.consume(x << 2);
            bh.consume(x << 3);
            bh.consume(y << 1);
            bh.consume(y << 2);
            bh.consume(y << 3);
            x = x >> 1;
            y = y >> 2;
        }
    }

    @Benchmark
    public void B_I_long(Blackhole bh) {
        long x = this.x;
        long y = this.y;
        for (int i = 0; i < ITERATION; i++) {
            long x1 = x + y;
            x = x1 + y;
            y = x1 + x;
        }
        bh.consume(x);
        bh.consume(y);
    }

    @Benchmark
    public void B_D_long(Blackhole bh) {
        long x = this.x;
        long y = this.y;
        for (int i = 0; i < ITERATION; i++) {
            bh.consume(x + 10);
            bh.consume(x + 20);
            bh.consume(x + 30);
            bh.consume(y + 10);
            bh.consume(y + 20);
            bh.consume(y + 30);
            x = x >> 1;
            y = y >> 2;
        }
    }

    @Benchmark
    public void I_S_long(Blackhole bh) {
        long x = this.x;
        long y = this.y;
        for (int i = 0; i < ITERATION; i++) {
            bh.consume(x << 1);
            bh.consume(x << 2);
            bh.consume(x << 3);
            bh.consume(y << 1);
            bh.consume(y << 2);
            bh.consume(y << 3);
            x = x >> 1;
            y = y >> 2;
        }
    }
}
