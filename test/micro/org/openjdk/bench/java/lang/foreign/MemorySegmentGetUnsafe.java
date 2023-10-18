/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import sun.misc.Unsafe;

import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = {"--enable-native-access=ALL-UNNAMED"})
public class MemorySegmentGetUnsafe {

    static final Unsafe UNSAFE = Utils.unsafe;
    static final MethodHandle OF_ADDRESS_UNSAFE;

    static {
        try {
            OF_ADDRESS_UNSAFE = MethodHandles.lookup().findStatic(MemorySegmentGetUnsafe.class,
                    "ofAddressUnsafe", MethodType.methodType(MemorySegment.class, long.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static final VarHandle INT_HANDLE = adaptSegmentHandle(JAVA_INT.varHandle());

    static VarHandle adaptSegmentHandle(VarHandle handle) {
        handle = MethodHandles.insertCoordinates(handle, 1, 0L);
        handle = MethodHandles.filterCoordinates(handle, 0, OF_ADDRESS_UNSAFE);
        return handle;
    }

    static MemorySegment ofAddressUnsafe(long address) {
        return MemorySegment.ofAddress(address).reinterpret(JAVA_INT.byteSize());
    }

    long addr;

    @Setup
    public void setup() throws Throwable {
        addr = Arena.global().allocate(JAVA_INT).address();
    }

    @Benchmark
    public int panama() {
        return (int) INT_HANDLE.get(addr);
    }

    @Benchmark
    public int unsafe() {
        return UNSAFE.getInt(addr);
    }
}
