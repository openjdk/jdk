/*
 * Copyright (c) 2025, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(value=3)
public class CountedLoopCastIV {
    @Param({"1024", "1536", "2048"})
    private int limit;

    private static final int LEN = 2048;
    private int start;
    private int[] arr;

    @Setup
    public void init() {
        arr = new int[LEN];
        for (int i = 0; i < LEN; i++) {
            arr[i] = i;
        }

        start = 0;
        limit = Math.min(limit, LEN - 4);
    }

    @Benchmark
    public void loop_iv_int() {
        int i = start;
        while (i < limit) {
            Objects.checkIndex(i, LEN - 1);
            int a = arr[i + 1];
            Objects.checkIndex(i, LEN - 3);
            arr[i + 3] = a;
            i++;
        }
    }

    @Benchmark
    public void loop_iv_long() {
        for (long i = start; i < limit; i++) {
            Objects.checkIndex(i, LEN - 1);
            int a = arr[(int)i + 1];
            Objects.checkIndex(i, LEN - 3);
            arr[(int)i + 3] = a;
        }
    }
}
