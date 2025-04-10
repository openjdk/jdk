/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.JAVA_INT;

@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {
        "-XX:+UnlockDiagnosticVMOptions",
        "-Xbatch",
        "-XX:MaxInlineLevel=15",
        "-XX:CompileCommand=PrintInlining,org.openjdk.bench.java.lang.foreign.AllocFromInlineTest::alloc15"})
public class AllocFromInlineTest {

    record FakeArena(MemorySegment segment) implements Arena {

        public FakeArena() {
            this(Arena.global().allocate(JAVA_INT));
        }

        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            return segment;
        }

        @Override
        public MemorySegment.Scope scope() {
            return Arena.global().scope();
        }

        @Override
        public void close() {}
    }

    private static final Arena ARENA = new FakeArena();

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc00() {
        return alloc_0();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc01() {
            return alloc_1();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc02() {
        return alloc_2();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc03() {
        return alloc_3();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc04() {
        return alloc_4();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc05() {
        return alloc_5();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc06() {
        return alloc_6();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc07() {
        return alloc_7();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc08() {
        return alloc_8();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc09() {
        return alloc_9();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc10() {
        return alloc_10();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc11() {
        return alloc_11();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc12() {
        return alloc_12();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc13() {
        return alloc_13();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc14() {
        return alloc_14();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public MemorySegment alloc15() {
        return alloc_15();
    }

    private MemorySegment alloc_0() {
        return ARENA.allocateFrom(JAVA_INT, 42);
    }

    private MemorySegment alloc_1() {
        return alloc_0();
    }

    private MemorySegment alloc_2() {
        return alloc_1();
    }

    private MemorySegment alloc_3() {
        return alloc_2();
    }

    private MemorySegment alloc_4() {
        return alloc_3();
    }

    private MemorySegment alloc_5() {
        return alloc_4();
    }

    private MemorySegment alloc_6() {
        return alloc_5();
    }

    private MemorySegment alloc_7() {
        return alloc_6();
    }

    private MemorySegment alloc_8() {
        return alloc_7();
    }

    private MemorySegment alloc_9() {
        return alloc_8();
    }

    private MemorySegment alloc_10() {
        return alloc_9();
    }

    private MemorySegment alloc_11() {
        return alloc_10();
    }

    private MemorySegment alloc_12() {
        return alloc_11();
    }

    private MemorySegment alloc_13() {
        return alloc_12();
    }

    private MemorySegment alloc_14() {
        return alloc_13();
    }

    private MemorySegment alloc_15() {
        return alloc_14();
    }

}
