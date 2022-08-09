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
 * Tests transformation that converts "c0 - (x + c1)" into "(c0 - c1)
 * - x" in SubINode::Ideal and SubLNode::Ideal.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3 , jvmArgsAppend = {"-XX:-TieredCompilation", "-Xbatch", "-Xcomp"})
public class SubIdealC0Minus_YPlusC1_ {

    private static final int I_C0 = 1234567;

    private static final int I_C1 = 1234567;

    private static final long L_C0 = 123_456_789_123_456L;

    private static final long L_C1 = 123_456_789_123_456L;

    private final int size = 100_000_000;

    private int[] ints_a;

    private long[] longs_a;

    @Setup
    public void init() {
        ints_a = new int[size];
        longs_a = new long[size];
        for (int i = 0; i < size; i++) {
            ints_a[i] = i;
            longs_a[i] = i * i;
        }
    }

    @Benchmark
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
    }

    // Convert "c0 - (x + c1)" into "(c0 - c1) - x" for int.
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int helper(int x) {
        return I_C0 - (x + I_C1);
    }

    // Convert "c0 - (x + c1)" into "(c0 - c1) - x" for long.
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static long helper(long x) {
        return L_C0 - (x + L_C1);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static void sink(int v) {}

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static void sink(long v) {}
}
