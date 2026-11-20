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

import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
@State(Scope.Thread)
public class LowBitsMask {
    private static final int SIZE = 1024;

    private int[] values = new int[SIZE];
    private int[] counts = new int[SIZE];

    @Setup
    public void setup() {
        Random random = new Random(42);
        for (int i = 0; i < SIZE; i++) {
            values[i] = random.nextInt();
            counts[i] = random.nextInt();
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int independent() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += values[i] & ((1 << counts[i]) - 1);
        }
        return sum;
    }

    // each count depends on the previous mask, as in a bit reader
    @Benchmark
    @OperationsPerInvocation(SIZE)
    public int dependent() {
        int x = 0;
        for (int i = 0; i < SIZE; i++) {
            x = (values[i] & ((1 << x) - 1)) + counts[i];
        }
        return x;
    }
}
