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
import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import static java.lang.foreign.ValueLayout.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(3)
@Threads(Threads.MAX)
public class ConcurrentClose {

    static final int SIZE = 10_000;

    @Benchmark
    public void sharedAccess() {
        try (Arena arena = Arena.ofShared()) {
            MemorySegment segment = arena.allocate(SIZE);
            access(segment);
        }
    }

    @Benchmark
    public void confinedAccess() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(SIZE);
            access(segment);
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int access(MemorySegment segment) {
        int sum = 0;
        for (int i = 0; i < segment.byteSize(); i++) {
            sum += segment.get(JAVA_BYTE, i);
        }
        return sum;
    }
}
