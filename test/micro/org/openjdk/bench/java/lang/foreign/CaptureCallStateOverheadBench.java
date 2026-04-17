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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;

/*
 * Note that this benchmark only measures the overhead of capturing the
 * "errno" state on a C function (strtol).
 *
 * Depending on the foreign language and execution platform, there may be
 * additional states that could be captured.
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--enable-native-access=ALL-UNNAMED"})

// This class benchmarks capturing the "errno" state.
// Depending on the execution platform, there may be further states.
public class CaptureCallStateOverheadBench {
    private static final boolean L32;
    private static final MethodHandle DOWNCALL_HANDLE_WITHOUT_STATE;
    private static final MethodHandle DOWNCALL_HANDLE_WITH_STATE;
    private Arena arena = null;
    private MemorySegment arg0 = null, arg1 = null, cs = null;
    private int arg2;

    // Set up as many fields as possible so they may be final.
    // Note that the Arena is allocated in setup such that it can be closed
    // in the corresponding teardown.
    static {
        Linker linker = Linker.nativeLinker();
        MemorySegment name = linker.defaultLookup().findOrThrow("strtol");
        MemoryLayout cLong = linker.canonicalLayouts().get("long"); // OfLong or OfInt.
        MemoryLayout cInt = linker.canonicalLayouts().get("int"); // Always OfInt.
        // Windows and 32-bit platforms treat the C "long" as 32-bit.
        L32 = cLong.byteSize() == 4;
        FunctionDescriptor signature = FunctionDescriptor.of(cLong,
                                                             ValueLayout.ADDRESS,
                                                             ValueLayout.ADDRESS,
                                                             cInt);
        Linker.Option ccs = Linker.Option.captureCallState("errno");
        DOWNCALL_HANDLE_WITHOUT_STATE = Linker.nativeLinker().downcallHandle(name, signature);
        DOWNCALL_HANDLE_WITH_STATE = Linker.nativeLinker().downcallHandle(name, signature, ccs);
    }

    @Setup
    public void setup() {
        arena = Arena.ofShared();
        arg0 = arena.allocateFrom("cafebab"); // Fits within int32.
        arg1 = MemorySegment.NULL;
        arg2 = 16;
        cs = arena.allocate(Linker.Option.captureStateLayout());
    }

    @Benchmark
    public void doNotUseCaptureCallState() throws Throwable {
        if (L32) {
            int unused = (int) DOWNCALL_HANDLE_WITHOUT_STATE.invokeExact(arg0, arg1, arg2);
        } else {
            long unused = (long) DOWNCALL_HANDLE_WITHOUT_STATE.invokeExact(arg0, arg1, arg2);
        }
    }

    @Benchmark
    public void useCaptureCallState() throws Throwable {
        if (L32) {
            int unused = (int) DOWNCALL_HANDLE_WITH_STATE.invokeExact(cs, arg0, arg1, arg2);
        } else {
            long unused = (long) DOWNCALL_HANDLE_WITH_STATE.invokeExact(cs, arg0, arg1, arg2);
        }
    }

    @TearDown
    public void tearDown() {
        arena.close();
    }
}
