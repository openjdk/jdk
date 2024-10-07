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
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=micro/native" })
public class CallOverheadVirtual {

    @Benchmark
    public void jni_blank() throws Throwable {
        blank();
    }

    @Benchmark
    public void panama_blank() throws Throwable {
        func_v.invokeExact(func_addr);
    }

    @Benchmark
    public void panama_blank_critical() throws Throwable {
        func_critical_v.invokeExact(func_addr);
    }

    @Benchmark
    public int jni_identity() throws Throwable {
        return identity(10);
    }

    public MemorySegment panama_identity_struct_confined() throws Throwable {
        return (MemorySegment) identity_struct_v.invokeExact(identity_struct_addr, recycling_allocator, confinedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_struct_shared() throws Throwable {
        return (MemorySegment) identity_struct_v.invokeExact(identity_struct_addr, recycling_allocator, sharedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_struct_confined_3() throws Throwable {
        return (MemorySegment) identity_struct_3_v.invokeExact(identity_struct_3_addr, recycling_allocator, confinedPoint, confinedPoint, confinedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_struct_shared_3() throws Throwable {
        return (MemorySegment) identity_struct_3_v.invokeExact(identity_struct_3_addr, recycling_allocator, sharedPoint, sharedPoint, sharedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_shared() throws Throwable {
        return (MemorySegment) identity_memory_address_v.invokeExact(identity_memory_address_addr, sharedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_confined() throws Throwable {
        return (MemorySegment) identity_memory_address_v.invokeExact(identity_memory_address_addr, confinedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_shared_3() throws Throwable {
        return (MemorySegment) identity_memory_address_3_v.invokeExact(identity_memory_address_3_addr, sharedPoint, sharedPoint, sharedPoint);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_confined_3() throws Throwable {
        return (MemorySegment) identity_memory_address_3_v.invokeExact(identity_memory_address_3_addr, confinedPoint, confinedPoint, confinedPoint);
    }

    @Benchmark
    public int panama_identity() throws Throwable {
        return (int) identity_v.invokeExact(identity_addr, 10);
    }

    @Benchmark
    public int panama_identity_critical() throws Throwable {
        return (int) identity_critical_v.invokeExact(identity_addr, 10);
    }

    @Benchmark
    public MemorySegment panama_identity_struct() throws Throwable {
        return (MemorySegment) identity_struct_v.invokeExact(identity_struct_addr, recycling_allocator, point);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_null() throws Throwable {
        return (MemorySegment) identity_memory_address_v.invokeExact(identity_memory_address_addr, MemorySegment.NULL);
    }

    @Benchmark
    public void panama_args_01() throws Throwable {
        args1_v.invokeExact(args1_addr, 10L);
    }

    @Benchmark
    public void panama_args_02() throws Throwable {
        args2_v.invokeExact(args2_addr, 10L, 11D);
    }

    @Benchmark
    public void panama_args_03() throws Throwable {
        args3_v.invokeExact(args3_addr, 10L, 11D, 12L);
    }

    @Benchmark
    public void panama_args_04() throws Throwable {
        args4_v.invokeExact(args4_addr, 10L, 11D, 12L, 13D);
    }

    @Benchmark
    public void panama_args_05() throws Throwable {
        args5_v.invokeExact(args5_addr, 10L, 11D, 12L, 13D, 14L);
    }

    @Benchmark
    public void panama_args_10() throws Throwable {
        args10_v.invokeExact(args10_addr,
                           10L, 11D, 12L, 13D, 14L,
                           15D, 16L, 17D, 18L, 19D);
    }
}
