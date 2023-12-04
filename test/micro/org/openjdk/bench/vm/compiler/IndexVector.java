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

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class IndexVector {
    @Param({"65536"})
    private int count;

    private int[] idx;
    private int[] src;
    private int[] dst;
    private float[] f;

    @Setup
    public void init() {
        idx = new int[count];
        src = new int[count];
        dst = new int[count];
        f = new float[count];
        Random ran = new Random(0);
        for (int i = 0; i < count; i++) {
            src[i] = ran.nextInt();
        }
    }

    @Benchmark
    public void indexArrayFill() {
        for (int i = 0; i < count; i++) {
            idx[i] = i;
        }
    }

    @Benchmark
    public void exprWithIndex1() {
        for (int i = 0; i < count; i++) {
            dst[i] = src[i] * (i & 7);
        }
    }

    @Benchmark
    public void exprWithIndex2() {
        for (int i = 0; i < count; i++) {
            f[i] = i * i + 100;
        }
    }
}

