/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.*;

import java.lang.foreign.*;
import java.lang.foreign.MemorySegment.Scope;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--enable-native-access=ALL-UNNAMED"})
public class NestedArenasAllocTest {

    @Benchmark
    public long alloc_confined_nested() {
        return allocateNested();
    }

    @Benchmark
    // Reserve jvmArgsAppend for OfVirtual; method-level append arguments take
    // precedence over the append arguments declared by a subclass. Method-level
    // jvmArgs replaces class-level jvmArgs, so repeat the required access options.
    @Fork(jvmArgs = {
            "--enable-native-access=ALL-UNNAMED",
            "-Djdk.internal.foreign.native.confined.pool.power.size=-1"
    })
    public long alloc_confined_nested_no_pool() {
        return allocateNested();
    }

    private static long allocateNested() {
        // Four nested and cached arenas and one overflow
        try (Arena a0 = Arena.ofConfined();
             Arena a1 = Arena.ofConfined();
             Arena a2 = Arena.ofConfined();
             Arena a3 = Arena.ofConfined();
             Arena a4 = Arena.ofConfined();) {
            return allocateFour(a0)
                    + allocateFour(a1)
                    + allocateFour(a2)
                    + allocateFour(a3)
                    + allocateFour(a4);
        }
    }

    private static long allocateFour(Arena arena) {
        return arena.allocate(ValueLayout.JAVA_LONG).address()
                + arena.allocate(ValueLayout.JAVA_LONG).address()
                + arena.allocate(ValueLayout.JAVA_LONG).address()
                + arena.allocate(ValueLayout.JAVA_LONG).address();
    }

    @Fork(value = 3, jvmArgsAppend = "-Djmh.executor=VIRTUAL")
    public static class OfVirtual extends NestedArenasAllocTest {}
}
