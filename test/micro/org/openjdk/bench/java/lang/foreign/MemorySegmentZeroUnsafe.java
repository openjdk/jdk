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

import sun.misc.Unsafe;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = {"--enable-native-access=ALL-UNNAMED"})
public class MemorySegmentZeroUnsafe {

    static final Unsafe UNSAFE = Utils.unsafe;
    long src;

    @Param({"1", "2", "3", "4", "5", "6", "7", "8", "15", "16", "63", "64", "255", "256"})
    public int size;

    @Param({"true", "false"})
    public boolean aligned;

    private MemorySegment segment;
    private long address;

    @Setup
    public void setup() throws Throwable {
        Arena arena = Arena.global();
        long alignment = 1;
        // this complex logic is to ensure that if in the future we decide to batch writes with different
        // batches based on alignment, we would spot it here
        if (size == 2 || size == 3) {
            alignment = 2;
        } else if (size >= 4 && size <= 7) {
            alignment = 4;
        } else {
            alignment = 8;
        }
        if (aligned) {
            segment = arena.allocate(size, alignment);
        } else {
            // forcibly misaligned in both address AND size, given that would be the worst case
            segment = arena.allocate(size + 1, alignment).asSlice(1);
        }
        address = segment.address();
    }

    @Benchmark
    public void panama() {
        segment.fill((byte) 0);
    }

    @Benchmark
    public void unsafe() {
        UNSAFE.setMemory(address, size, (byte) 0);
    }
}
