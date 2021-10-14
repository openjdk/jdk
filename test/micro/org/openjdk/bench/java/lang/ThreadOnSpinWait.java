/*
 * Copyright (c) 2021, Amazon.com Inc. or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;

/**
 * Three threads counting up to maxNum(default: 1 000 000). Two of them do pauses.
 * One thread counts without a pause.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class ThreadOnSpinWait {
    @Param({"1000000"})
    public int maxNum;

    @Benchmark
    @Group("count")
    @GroupThreads(1)
    public void withOnSpinWait() {
        for (int i = 0; i < maxNum; ++i) {
            nowork(i);
            Thread.onSpinWait();
        }
    }

    @Benchmark
    @Group("count")
    @GroupThreads(1)
    public void withSleep0() throws InterruptedException {
        for (int i = 0; i < maxNum; ++i) {
            nowork(i);
            Thread.sleep(0);
        }
    }

    @Benchmark
    @Group("count")
    @GroupThreads(1)
    public void withoutPause() {
        for (int i = 0; i < maxNum; ++i) {
            nowork(i);
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static void nowork(int v) {
    }
}
