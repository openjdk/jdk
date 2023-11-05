/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.MemorySegment;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED" })
public class CallOverheadConstant {

    @Benchmark
    public void jni_blank() throws Throwable {
        blank();
    }

    @Benchmark
    public void panama_blank() throws Throwable {
        func.invokeExact();
    }

    @Benchmark
    public void panama_blank_critical() throws Throwable {
        func_critical.invokeExact();
    }

    @Benchmark
    public int jni_identity() throws Throwable {
        return identity(10);
    }

    @Benchmark
    public int panama_identity() throws Throwable {
        return (int) identity.invokeExact(10);
    }

    @Benchmark
    public int panama_identity_critical() throws Throwable {
        return (int) identity_critical.invokeExact(10);
    }

    @Benchmark
    public MemorySegment panama_identity_struct_confined() throws Throwable {
        return (MemorySegment) identity_struct.invokeExact(recycling_allocator, confinedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_struct_shared() throws Throwable {
        return (MemorySegment) identity_struct.invokeExact(recycling_allocator, sharedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_struct_confined_3() throws Throwable {
        return (MemorySegment) identity_struct_3.invokeExact(recycling_allocator, confinedPoint, confinedPoint, confinedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_struct_shared_3() throws Throwable {
        return (MemorySegment) identity_struct_3.invokeExact(recycling_allocator, sharedPoint, sharedPoint, sharedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_shared() throws Throwable {
        return (MemorySegment) identity_memory_address.invokeExact(sharedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_confined() throws Throwable {
        return (MemorySegment) identity_memory_address.invokeExact(confinedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_shared_3() throws Throwable {
        return (MemorySegment) identity_memory_address_3.invokeExact(sharedPoint, sharedPoint, sharedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_confined_3() throws Throwable {
        return (MemorySegment) identity_memory_address_3.invokeExact(confinedPoint, confinedPoint, confinedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_null() throws Throwable {
        return (MemorySegment) identity_memory_address.invokeExact(MemorySegment.NULL);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_null_3() throws Throwable {
        return (MemorySegment) identity_memory_address_3.invokeExact(MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_null_non_exact() throws Throwable {
        return (MemorySegment) identity_memory_address.invoke(MemorySegment.NULL);
    }

    @Benchmark
    public void panama_args_01() throws Throwable {
        args1.invokeExact(10L);
    }

    @Benchmark
    public void panama_args_02() throws Throwable {
        args2.invokeExact(10L, 11D);
    }

    @Benchmark
    public void panama_args_03() throws Throwable {
        args3.invokeExact(10L, 11D, 12L);
    }

    @Benchmark
    public void panama_args_04() throws Throwable {
        args4.invokeExact(10L, 11D, 12L, 13D);
    }

    @Benchmark
    public void panama_args_05() throws Throwable {
        args5.invokeExact(10L, 11D, 12L, 13D, 14L);
    }

    @Benchmark
    public void panama_args_10() throws Throwable {
        args10.invokeExact(10L, 11D, 12L, 13D, 14L,
                           15D, 16L, 17D, 18L, 19D);
    }
}
