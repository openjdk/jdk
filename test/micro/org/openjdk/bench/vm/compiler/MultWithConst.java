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
import org.openjdk.jmh.infra.*;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 2)
public class MultWithConst {
    @Param({ "512" })
    private static int SIZE;
    private int[] ints;

    @Setup
    public void init() {
        ints = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            ints[i] = i;
        }
    }

    // const = 2^n, (n > 0)
    // Test x * 8 => x << 3.
    @Benchmark
    public void testInt8(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * 8);
        }
    }

    @Benchmark
    public int testInt8AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += (ints[i] * 8);
        }
        return sum;
    }

    @Benchmark
    public void testInt8Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * 8;
        }
    }

    // const = 2^n + 1, (n > 0)
    // Test x * 9 => (x << 3) + x.
    @Benchmark
    public void testInt9(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * 9);
        }
    }

    @Benchmark
    public int testInt9AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += ints[i] * 9;
        }
        return sum;
    }

    @Benchmark
    public void testInt9Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * 9;
        }
    }

    // const = 2^n - 1, (n > 0)
    // Test x * 7 => (x << 3) - x.
    @Benchmark
    public void testInt7(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * 7);
        }
    }

    @Benchmark
    public int testInt7AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += (ints[i] * 7);
        }
        return sum;
    }

    @Benchmark
    public void testInt7Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * 7;
        }
    }

    // const = 2^m + 2^n, (0 < m <= 4, 0 < n <= 4)
    // Test x * 18 vs (x << 4) + (x << 1).
    @Benchmark
    public void testInt18(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * 18);
        }
    }

    @Benchmark
    public int testInt18AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += (ints[i] * 18);
        }
        return sum;
    }

    @Benchmark
    public void testInt18Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * 18;
        }
    }

    // const = 2^m + 2^n, (m > 4, 0 < n <= 4)
    // Test x * 36 vs (x << 5) + (x << 2)
    @Benchmark
    public void testInt36(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * 36);
        }
    }

    @Benchmark
    public int testInt36AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += (ints[i] * 36);
        }
        return sum;
    }

    @Benchmark
    public void testInt36Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * 36;
        }
    }

    // const = 2^m + 2^n, (m > 4, n > 4)
    // Test x * 96 vs (x << 6) + (x << 5)
    @Benchmark
    public void testInt96(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * 96);
        }
    }

    @Benchmark
    public int testInt96AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += (ints[i] * 96);
        }
        return sum;
    }

    @Benchmark
    public void testInt96Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * 96;
        }
    }

    // Negatives

    // const = -2^n, (n > 0)
    // Test x * -8 => -(x << 3).
    @Benchmark
    public void testIntN8(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * -8);
        }
    }

    @Benchmark
    public int testIntN8AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += ints[i] * -8;
        }
        return sum;
    }

    @Benchmark
    public void testIntN8Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * -8;
        }
    }

    // const = -(2^n + 1), (0 < n <= 4)
    // Test x * -9 vs -((x << 3) + x).
    @Benchmark
    public void testIntN9(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * -9);
        }
    }

    @Benchmark
    public int testIntN9AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += ints[i] * -9;
        }
        return sum;
    }

    @Benchmark
    public void testIntN9Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * -9;
        }
    }

    // const = -(2^n + 1), (n > 4)
    // Test x * -33 vs -((x << 5) + x).
    @Benchmark
    public void testIntN33(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * -33);
        }
    }

    @Benchmark
    public int testIntN33AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += ints[i] * -33;
        }
        return sum;
    }

    @Benchmark
    public void testIntN33Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * -33;
        }
    }

    // const = -(2^n - 1), (n > 0)
    // Test x * -7 => x - (x << 3).
    @Benchmark
    public void testIntN7(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * -7);
        }
    }

    @Benchmark
    public int testIntN7AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += ints[i] * -7;
        }
        return sum;
    }

    @Benchmark
    public void testIntN7Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * -7;
        }
    }

    // const = -(2^m + 2^n), (0 < m <= 4, 0 < n <= 4)
    // Test x * -18 vs -((x << 4) + (x << 1)).
    @Benchmark
    public void testIntN18(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * -18);
        }
    }

    @Benchmark
    public int testIntN18AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += (ints[i] * -18);
        }
        return sum;
    }

    @Benchmark
    public void testIntN18Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * -18;
        }
    }

    // const = -(2^m + 2^n), (m > 4, 0 < n <= 4)
    // Test x * -36 vs -((x << 5) + (x << 2))
    @Benchmark
    public void testIntN36(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * -36);
        }
    }

    @Benchmark
    public int testIntN36AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += (ints[i] * -36);
        }
        return sum;
    }

    @Benchmark
    public void testIntN36Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * -36;
        }
    }

    // const = -(2^m + 2^n), (m > 4, n > 4)
    // Test x * -96 vs -((x << 6) + (x << 5))
    @Benchmark
    public void testIntN96(Blackhole blackhole) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(ints[i] * -96);
        }
    }

    @Benchmark
    public int testIntN96AddSum() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += (ints[i] * -96);
        }
        return sum;
    }

    @Benchmark
    public void testIntN96Store() {
        for (int i = 0; i < SIZE; i++) {
            ints[i] = ints[i] * -96;
        }
    }
}