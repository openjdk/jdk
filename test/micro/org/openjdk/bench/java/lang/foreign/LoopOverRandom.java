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
package org.openjdk.bench.java.lang.foreign;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import jdk.internal.misc.Unsafe;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
public class LoopOverRandom extends JavaLayouts {
    static final int SEED = 0;

    static final long ELEM_SIZE = ValueLayout.JAVA_INT.byteSize();
    static final int ELEM_COUNT = 1_000;
    static final long ALLOC_SIZE = ELEM_COUNT * ELEM_SIZE;

    static final Unsafe unsafe = Utils.unsafe;

    Arena arena;
    MemorySegment segment;
    int[] indices;

    static final MemorySegment ALL = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);

    @Setup
    public void setup() {
        indices = new Random(SEED).ints(0, ELEM_COUNT).limit(ELEM_COUNT).toArray();
        arena = Arena.ofConfined();
        segment = arena.allocate(ALLOC_SIZE);
        for (int i = 0; i < ELEM_COUNT; i++) {
            segment.setAtIndex(ValueLayout.JAVA_INT, i, i);
        }
    }

    @TearDown
    public void tearDown() {
        arena.close();
    }

    @Benchmark
    public long segment_loop() {
        int sum = 0;
        for (int i = 0; i < ELEM_COUNT; i++) {
            sum += segment.getAtIndex(ValueLayout.JAVA_INT_UNALIGNED, indices[i]);
            target_dontInline();
        }
        return sum;
    }

    @Benchmark
    public long segment_loop_all() {
        int sum = 0;
        for (int i = 0; i < ELEM_COUNT; i++) {
            sum += ALL.get(ValueLayout.JAVA_INT_UNALIGNED, segment.address() + indices[i] * ELEM_SIZE);
            target_dontInline();
        }
        return sum;
    }

    @Benchmark
    public long segment_loop_asUnchecked() {
        int sum = 0;
        for (int i = 0; i < ELEM_COUNT; i++) {
            sum += asUnchecked(segment).getAtIndex(ValueLayout.JAVA_INT_UNALIGNED, indices[i]);
            target_dontInline();
        }
        return sum;
    }

    @Benchmark
    public long unsafe_loop() {
        int sum = 0;
        for (int i = 0; i < ELEM_COUNT; i++) {
            sum += unsafe.getInt(segment.address() + indices[i] * ELEM_SIZE);
            target_dontInline();
        }
        return sum;
    }

    MemorySegment asUnchecked(MemorySegment segment) {
        return MemorySegment.ofAddress(segment.address()).reinterpret(Long.MAX_VALUE);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void target_dontInline() {
        // this method was intentionally left blank
    }
}
