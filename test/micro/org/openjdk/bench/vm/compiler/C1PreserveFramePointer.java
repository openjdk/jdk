/*
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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
 *
 */
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 8, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 8, time = 2, timeUnit = TimeUnit.SECONDS)
public abstract class C1PreserveFramePointer {

    @Benchmark
    public long calculate() {
        return calculate(12345678L);
    }

    public static long calculate(long x) {
        long v0 = x + 1L;
        long v1 = x + 2L;
        long v2 = x + 3L;
        long v3 = x + 4L;
        long v4 = x + 5L;
        long v5 = x + 6L;
        long v6 = x + 7L;
        long v7 = x + 8L;
        long v8 = x + 9L;
        long v9 = x + 10L;
        long v10 = x + 11L;
        long v11 = x + 12L;
        long v12 = x + 13L;
        return v0 + v1 + v2 + v3 + v4 + v5 + v6 +
               v7 + v8 + v9 + v10 + v11 + v12;
    }

    @Fork(value = 2, jvmArgsPrepend = { "-XX:+PreserveFramePointer", "-XX:-Inline", "-XX:TieredStopAtLevel=1"})
    public static class WithPreserveFramePointer extends C1PreserveFramePointer {}

    @Fork(value = 2, jvmArgsPrepend = { "-XX:-PreserveFramePointer", "-XX:-Inline", "-XX:TieredStopAtLevel=1"})
    public static class WithoutPreserveFramePointer extends C1PreserveFramePointer {}
}
