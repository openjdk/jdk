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

import jdk.internal.foreign.CaptureStateUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--add-exports=java.base/jdk.internal.foreign=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED"})
public class CaptureStateUtilBench {

    private static final String ERRNO_NAME = "errno";

    private static final VarHandle ERRNO_HANDLE = Linker.Option.captureStateLayout()
            .varHandle(MemoryLayout.PathElement.groupElement(ERRNO_NAME));

    private static final long SIZE = Linker.Option.captureStateLayout().byteSize();

    private static final MethodHandle DUMMY_EXPLICIT_ALLOC = dummyExplicitAlloc();
    private static final MethodHandle DUMMY_TL_ALLOC = dummyTlAlloc();

    @Benchmark
    public int explicitAllocationSuccess() throws Throwable {
        try (var arena = Arena.ofConfined()) {
            return (int) DUMMY_EXPLICIT_ALLOC.invokeExact(arena.allocate(SIZE), 0, 0);
        }
    }

    @Benchmark
    public int explicitAllocationFail() throws Throwable {
        try (var arena = Arena.ofConfined()) {
            return (int) DUMMY_EXPLICIT_ALLOC.invokeExact(arena.allocate(SIZE), -1, 1);
        }
    }

    @Benchmark
    public int adaptedSysCallSuccess() throws Throwable {
        return (int) DUMMY_TL_ALLOC.invokeExact(0, 0);
    }

    @Benchmark
    public int adaptedSysCallFail() throws Throwable {
        return (int) DUMMY_TL_ALLOC.invokeExact( -1, 1);
    }

    private static MethodHandle dummyExplicitAlloc() {
        try {
            return MethodHandles.lookup().findStatic(CaptureStateUtilBench.class,
                    "dummy", MethodType.methodType(int.class, MemorySegment.class, int.class, int.class));
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException(roe);
        }
    }

    private static MethodHandle dummyTlAlloc() {
        final MethodHandle handle = dummyExplicitAlloc();
        return CaptureStateUtil.adaptSystemCall(handle, ERRNO_NAME);
    }

    // Dummy method that is just returning the provided parameters
    private static int dummy(MemorySegment segment, int result, int errno) {
        if (errno != 0) {
            // Assuming the capture state is only modified upon detecting an error.
            ERRNO_HANDLE.set(segment, 0, errno);
        }
        return result;
    }

    @Fork(value = 3, jvmArgsAppend = "-Djmh.executor=VIRTUAL")
    public static class OfVirtual extends CaptureStateUtilBench {}

}
