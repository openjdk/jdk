/*
 * Copyright (c) 2025 Alibaba Group Holding Limited. All Rights Reserved.
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

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@State(Scope.Thread)
public class LoopSafepoint {
    static int loopCount = 100000;
    static int anotherInt = 1;

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private void empty() {}

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private int constInt() {
        return 100000;
    }

    @Benchmark
    public int loopConst() {
        int sum = 0;
        for (int i = 0; i < 100000; i++) {
            sum += anotherInt;
            empty();
        }
        return sum;
    }

    @Benchmark
    public int loopVar() {
        int sum = 0;
        for (int i = 0; i < loopCount; i++) {
            sum += anotherInt;
            empty();
        }
        return sum;
    }

    @Benchmark
    public int loopFunc() {
        int sum = 0;
        for (int i = 0; i < constInt(); i++) {
            sum += anotherInt;
            empty();
        }
        return sum;
    }
}
