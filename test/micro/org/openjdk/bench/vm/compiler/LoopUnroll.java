/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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
import org.openjdk.jmh.infra.*;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 5, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(value=1)
public class LoopUnroll {
    @Param({"16", "32", "64", "128", "256", "512", "1024"})
    private int VECLEN;

    private byte[][] a;
    private byte[][] b;
    private byte[][] c;

    @Setup
    public void init() {
        a = new byte[VECLEN][VECLEN];
        b = new byte[VECLEN][VECLEN];
        c = new byte[VECLEN][VECLEN];
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private int run_workload1(int count, byte[][] a , byte[][] b, byte[][] c) {
        for(int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                a[i][j] = (byte)(b[i][j] + c[i][j]);
            }
        }
        return a[count][count];
    }

    @Benchmark
    public void workload1_caller(Blackhole bh) {
        int r = 0;
        for(int i = 0 ; i < 100; i++) {
            r += run_workload1(i % a.length, a, b, c);
        }
        bh.consume(r);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private int run_workload2(int count, byte[][] a , byte[][] b) {
        for(int i = 0; i < b.length; i++) {
            for (int j = 0; j < b[0].length; j++) {
                a[i][j] = b[i][j];
            }
        }
        return a[count][count];
    }

    @Benchmark
    public void workload2_caller(Blackhole bh) {
        int r = 0;
        for(int i = 0 ; i < 100; i++) {
            r += run_workload2(i % a.length, a, b);
        }
        bh.consume(r);
    }
}
