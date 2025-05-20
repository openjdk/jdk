/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.*;
import java.lang.foreign.*;

import java.util.concurrent.TimeUnit;

/**
 * The purpose of this benchmark is to see the effect of automatic alignment in auto vectorization.
 *
 * Note: If you are interested in a nice visualization of load and store misalignment, please look
 *       at the benchmark {@link VectorAutoAlignmentVisualization}.
 */

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public abstract class VectorAutoAlignment {
    @Param({"1024", "1152", "1280", "1408", "1536", "1664", "1792", "1920", "1984", "2048", "2114",
            "2176", "2304", "2432", "2560", "2688", "2816", "2944", "3072", "3200", "3328", "3456",
            "3584", "3712", "3840", "3968", "4096", "4224", "4352", "4480"})
    public int SIZE;

    private MemorySegment ms;

    @Setup
    public void init() throws Throwable {
        long totalSize = 4L * SIZE + 4L * SIZE;
        long alignment = 4 * 1024; // 4k = page size
        ms = Arena.ofAuto().allocate(totalSize, alignment);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void kernel1L1S(int offset_load, int offset_store) {
        for (int i = 0; i < SIZE - /* slack for offset */ 32; i++) {
            int v = ms.get(ValueLayout.JAVA_INT_UNALIGNED, 4L * i + 4L * offset_load + 4L * SIZE);
            ms.set(ValueLayout.JAVA_INT_UNALIGNED, 4L * i + 4L * offset_store, v);
        }
    }

    @Benchmark
    public void bench1L1S() throws Throwable {
        // Go over all possible offsets, to get an average performance.
        for (int offset_load = 0; offset_load < 32; offset_load++) {
            for (int offset_store = 0; offset_store < 32; offset_store++) {
                kernel1L1S(offset_load, offset_store);
            }
        }
    }

    @Fork(value = 1, jvmArgs = {
        "-XX:-UseSuperWord"
    })
    public static class NoVectorization extends VectorAutoAlignment {}

    @Fork(value = 1, jvmArgs = {
        "-XX:+UnlockDiagnosticVMOptions", "-XX:SuperWordAutomaticAlignment=0"
    })
    public static class NoAutoAlign extends VectorAutoAlignment {}

    @Fork(value = 1, jvmArgs = {
        "-XX:+UnlockDiagnosticVMOptions", "-XX:SuperWordAutomaticAlignment=1"
    })
    public static class AlignStore extends VectorAutoAlignment {}


    @Fork(value = 1, jvmArgs = {
        "-XX:+UnlockDiagnosticVMOptions", "-XX:SuperWordAutomaticAlignment=2"
    })
    public static class AlignLoad extends VectorAutoAlignment {}
}
