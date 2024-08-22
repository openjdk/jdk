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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import static java.lang.foreign.ValueLayout.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
public class ConcurrentClose {

    static final int SIZE = 10_000;
    static final VarHandle BYTES = MethodHandles.arrayElementVarHandle(byte[].class);

    MemorySegment segment;
    byte[] array;

    @Setup
    public void setup() {
        segment = Arena.global().allocate(SIZE);
        array = new byte[SIZE];
    }

    @Benchmark
    @GroupThreads(1)
    @Group("sharedClose")
    public void closing() {
        Arena arena = Arena.ofShared();
        arena.close();
    }

    @Benchmark
    @GroupThreads(1)
    @Group("sharedClose")
    public int memorySegmentAccess() {
        int sum = 0;
        for (long i = 0; i < segment.byteSize(); i++) {
            sum += segment.get(JAVA_BYTE, i);
        }
        return sum;
    }

    @Benchmark
    @GroupThreads(1)
    @Group("sharedClose")
    public int otherAccess() {
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            sum += (byte) BYTES.get(array, i);
        }
        return sum;
    }
}
