/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
import java.util.Random;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class TestEor3 {
    @Param({"2048"})
    private int LENGTH;

    private int[] ia;
    private int[] ib;
    private int[] ic;
    private int[] id;

    private long[] la;
    private long[] lb;
    private long[] lc;
    private long[] ld;

    @Param("0")
    private int seed;
    private Random random = new Random(seed);

    @Setup
    public void init() {
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ic = new int[LENGTH];
        id = new int[LENGTH];

        la = new long[LENGTH];
        lb = new long[LENGTH];
        lc = new long[LENGTH];
        ld = new long[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ia[i] = random.nextInt();
            ib[i] = random.nextInt();
            ic[i] = random.nextInt();

            la[i] = random.nextLong();
            lb[i] = random.nextLong();
            lc[i] = random.nextLong();
        }
    }

    // Test EOR3 for int arrays
    @Benchmark
    public void test1Int() {
        for (int i = 0; i < LENGTH; i++) {
            id[i] = ia[i] ^ ib[i] ^ ic[i];
        }
    }

    // Test EOR3 for int arrays with multiple eor operations
    @Benchmark
    public void test2Int() {
        for (int i = 0; i < LENGTH; i++) {
            id[i] = ia[i] ^ ib[i] ^ ic[i] ^ ia[i] ^ ib[i];
        }
    }

    // Test EOR3 for long arrays
    @Benchmark
    public void test1Long() {
        for (int i = 0; i < LENGTH; i++) {
            ld[i] = la[i] ^ lb[i] ^ lc[i];
        }
    }

    // Test EOR3 for long arrays with multiple eor operations
    @Benchmark
    public void test2Long() {
        for (int i = 0; i < LENGTH; i++) {
            ld[i] = la[i] ^ lb[i] ^ lc[i] ^ la[i] ^ lb[i];
        }
    }
}
