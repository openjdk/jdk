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

package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {
        "-XX:+UnlockDiagnosticVMOptions",
        "-Xbatch",
        "-XX:MaxInlineLevel=15",
        "-XX:CompileCommand=PrintInlining,org.openjdk.bench.java.lang.foreign.FFMVarHandleInlineTest::t_level15"})
public class FFMVarHandleInlineTest {

    private static final boolean RANDOM_ORDER = true;

    /**
     * Implements the following:
     * <pre>{@code
     * int JAVA_INT_UNALIGNED(long offset) {
     *     return MemorySegment
     *         .ofAddress(offset)
     *         .reinterpret(8L)
     *         .get(ValueLayout.JAVA_INT_UNALIGNED, 0L)
     * }
     * }</pre>
     */
    private static final VarHandle JAVA_INT_UNALIGNED;

    static {
        try {
            var ofAddress = MethodHandles.lookup()
                    .findStatic(MemorySegment.class, "ofAddress", MethodType.methodType(MemorySegment.class, long.class));

            var reinterpret = MethodHandles.lookup()
                    .findVirtual(MemorySegment.class, "reinterpret", MethodType.methodType(MemorySegment.class, long.class));

            var vh = ValueLayout.JAVA_INT_UNALIGNED.varHandle();

            vh = MethodHandles.insertCoordinates(vh, 1, 0L);
            vh = MethodHandles.filterCoordinates(vh, 0, MethodHandles.filterReturnValue(
                    ofAddress,
                    MethodHandles.insertArguments(reinterpret, 1, 8L)
            ));

            JAVA_INT_UNALIGNED = vh.withInvokeExactBehavior();
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // segment + offsets should comfortably fit in L1 cache

    private MemorySegment segment;

    private long[] offsets;

    @Param(value = {
            "1024", // 1kb
    })
    private int segmentSize;

    @Param(value = {
            //"8", // 64b
            //"512", // 4kb
            "2048", // 16kb
    })
    private int offsetCount;

    @Setup
    public void init() {
        var rand = new Random(42);

        // initialize segment with random values
        segment = Arena.ofAuto().allocate(segmentSize);
        for (int i = 0; i < segment.byteSize() / 8; i++) {
            segment.setAtIndex(ValueLayout.JAVA_LONG, i, rand.nextLong());
        }

        var ints = (int) (segment.byteSize() >> 2);

        // initialize offset array
        offsets = new long[offsetCount];
        for (int i = 0; i < offsets.length; i++) {
            if (RANDOM_ORDER) {
                offsets[i] = segment.address() + ((long) rand.nextInt(ints) << 2); // random
            } else {
                offsets[i] = segment.address() + ((long) (i % ints) << 2); // sequential
            }
        }

        // validate that all loops are implemented correctly
        var ref = t0_reference();
        if (
                ref != t_level8() ||
                        ref != t_level9() ||
                        ref != t_level10() ||
                        ref != t_level11()
        ) {
            throw new IllegalStateException();
        }
    }

    @TearDown
    public void tearDown() {
        Reference.reachabilityFence(segment);
    }

    //@Benchmark
    public int t0_reference() {
        var s = 0;
        for (long offset : offsets) {
            s += (int) JAVA_INT_UNALIGNED.get(offset);
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    //@Benchmark
    public int t_level8() {
        var s = 0;
        for (long offset : offsets) {
            s += level8(offset);
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    //@Benchmark
    public int t_level9() {
        var s = 0;
        for (long offset : offsets) {
            s += level9(offset);
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    //@Benchmark
    public int t_level10() {
        var s = 0;
        for (long offset : offsets) {
            s += level10(offset);
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public int t_level11() {
        var s = 0;
        for (long offset : offsets) {
            s += level11(offset);
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public int t_level12() {
        var s = 0;
        for (long offset : offsets) {
            s += level12(offset);
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public int t_level13() {

        var s = 0;
        for (long offset : offsets) {
            s += level13(offset);
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public int t_level14() {
        var s = 0;
        for (long offset : offsets) {
            s += level14(offset);
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public int t_level15() {
        var s = 0;
        for (long offset : offsets) {
            s += level15(offset);
        }
        return s;
    }

    private static int level15(long offset) {
        return level14(offset);
    }

    private static int level14(long offset) {
        return level13(offset);
    }

    private static int level13(long offset) {
        return level12(offset);
    }

    private static int level12(long offset) {
        return level11(offset);
    }

    private static int level11(long offset) {
        return level10(offset);
    }

    private static int level10(long offset) {
        return level9(offset);
    }

    private static int level9(long offset) {
        return level8(offset);
    }

    private static int level8(long offset) {
        return level7(offset);
    }

    private static int level7(long offset) {
        return level6(offset);
    }

    private static int level6(long offset) {
        return level5(offset);
    }

    private static int level5(long offset) {
        return level4(offset);
    }

    private static int level4(long offset) {
        return level3(offset);
    }

    private static int level3(long offset) {
        return level2(offset);
    }

    private static int level2(long offset) {
        return level1(offset);
    }

    private static int level1(long offset) {
        return level0(offset);
    }

    private static int level0(long offset) {
        return (int) JAVA_INT_UNALIGNED.get(offset);
    }

/*    private static int level0(long offset) {
        return MemorySegment.ofAddress(offset)
                .reinterpret(8L)
                .get(ValueLayout.JAVA_INT_UNALIGNED, 0L);
    }*/

}