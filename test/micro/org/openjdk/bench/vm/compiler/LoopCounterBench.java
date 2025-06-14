/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class LoopCounterBench {
    static final int SIZE = 1000;

    int increment;
    long[] src, dst;

    @State(Scope.Benchmark)
    public static class UncommonTest {
        @Param({"0.0", "0.01", "0.1", "0.2", "0.5"})
        double prob;

        boolean[] test;

        @Setup
        public void setup() {
            test = new boolean[SIZE];
            for (int i = 0; i < SIZE * prob; i++) {
                test[i] = true;
            }
        }
    }

    @Setup
    public void setup() {
        src = new long[SIZE];
        dst = new long[SIZE];
        increment = 1;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static void call() {}

    @CompilerControl(CompilerControl.Mode.INLINE)
    private static void spillEverything() {
        for (int i = 0; i < 100; i++) {
            call();
        }
    }

    @Benchmark
    public long[] field_ret() {
        for (int i = 0; i < src.length; i = i + increment) {
            dst[i] = src[i];
        }
        return dst;
    }

    @Benchmark
    public long[] localVar_ret() {
        final int inc = increment;
        for (int i = 0; i < src.length; i = i + inc) {
            dst[i] = src[i];
        }
        return dst;
    }

    @Benchmark
    public long[] reloadAtEntry_ret() {
        int inc = increment;
        long[] dst = this.dst;
        long[] src = this.src;
        spillEverything();
        for (int i = 0; i < src.length; i += inc) {
            dst[i] = src[i];
        }
        return dst;
    }

    @Benchmark
    public long[] spillUncommon_ret(UncommonTest param) {
        int inc = increment;
        long[] dst = this.dst;
        long[] src = this.src;
        for (int i = 0; i < src.length; i += inc) {
            if (param.test[i]) {
                spillEverything();
            }
            dst[i] = src[i];
        }
        return dst;
    }
}
