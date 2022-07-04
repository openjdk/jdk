/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(value=1)
public class AutoVectorization2DArray {
    @Param({"16", "32", "64"})
    private int LEN;

    private byte[][] a_byte;
    private byte[][] b_byte;
    private byte[][] c_byte;

    private int[][] a_int;
    private int[][] b_int;
    private int[][] c_int;

    private double[][] a_double;
    private double[][] b_double;
    private double[][] c_double;

    @Setup
    public void init() {
        a_byte = new byte[LEN][LEN];
        b_byte = new byte[LEN][LEN];
        c_byte = new byte[LEN][LEN];

        a_int = new int[LEN][LEN];
        b_int = new int[LEN][LEN];
        c_int = new int[LEN][LEN];

        a_double = new double[LEN][LEN];
        b_double = new double[LEN][LEN];
        c_double = new double[LEN][LEN];
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private int run_byte(int count, byte[][] a , byte[][] b, byte[][] c) {
        for(int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                a[i][j] = (byte)(b[i][j] + c[i][j]);
            }
        }
        return a[count][count];
    }

    @Benchmark
    public void test_run_byte(Blackhole bh) {
        int r = 0;
        for(int i = 0 ; i < 100; i++) {
            r += run_byte(i % a_byte.length, a_byte, b_byte, c_byte);
        }
        bh.consume(r);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private int run_int(int count, int[][] a, int[][] b, int[][] c) {
        for(int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                a[i][j] = b[i][j] + c[i][j];
            }
        }
        return a[count][count];
    }

    @Benchmark
    public void test_run_int(Blackhole bh) {
        int r = 0;
        for(int i = 0 ; i < 100; i++) {
            r += run_int(i % a_int.length, a_int, b_int, c_int);
        }
        bh.consume(r);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private double run_double(int count, double[][] a, double[][] b, double[][] c) {
        for(int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                a[i][j] = b[i][j] + c[i][j];
            }
        }
        return a[count][count];
    }

    @Benchmark
    public void test_run_double(Blackhole bh) {
        double r = 0;
        for(int i = 0 ; i < 100; i++) {
            r += run_double(i % a_double.length, a_double, b_double, c_double);
        }
        bh.consume(r);
    }
}
