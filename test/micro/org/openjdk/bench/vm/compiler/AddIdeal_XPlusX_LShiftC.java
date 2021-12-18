/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Tests transformation from "(x + x) << c" to "x << (c + 1)".
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class AddIdeal_XPlusX_LShiftC {

    private final int size = 100_000;

    private int[] ints_a;

    private long[] longs_a;

    @Setup
    public void init() {
        ints_a = new int[size];
        longs_a = new long[size];
        for (int i = 0; i < size; i++) {
            ints_a[i] = i;
            longs_a[i] = i * i * i;
        }
    }

    /* @Benchmark
    public void baseline() {
        for (int i = 0; i < size; i++) {
            sink(ints_a[i]);
            sink(longs_a[i]);
        }
    }

    @Benchmark
    public void test() {
        for (int i = 0; i < size; i++) {
            sink(helper(ints_a[i]));
            sink(helper(longs_a[i]));
        }
    } */

    @Benchmark
    public int testInt() {
        return helper(ints_a[4711]);
    }

    @Benchmark
    public long testLong() {
        return helper(longs_a[4711]);
    }

    // Convert "(x + x) << 10" into "x << 11" for int.
    // (x << 11) >>> 11 is then further converted into zero-extends.
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int helper(int x) {
        return ((x + x) << 10) >>> 11;
    }

    // Convert "(x + x) << 40" into "x << 41" for long.
    // (x << 41) >>> 41 is then further converted into zero-extends.
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static long helper(long x) {
        return ((x + x) << 40) >>> 41;
    }

    /* @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static void sink(int v) {}

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static void sink(long v) {} */
}
