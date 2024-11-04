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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import jdk.internal.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgs = { "-XX:-TieredCompilation" })
public class LoopOverNonConstantAsType extends JavaLayouts {

    static final Unsafe unsafe = Utils.unsafe;

    static final int ELEM_SIZE = 1_000_000;
    static final int CARRIER_SIZE = (int)JAVA_LONG.byteSize();
    static final int ALLOC_SIZE = ELEM_SIZE * CARRIER_SIZE;

    @Param({"false", "true"})
    public boolean asTypeCompiled;

    Arena arena;
    MemorySegment segment;
    long unsafe_addr;

    @Setup
    public void setup() {
        unsafe_addr = unsafe.allocateMemory(ALLOC_SIZE);
        for (int i = 0; i < ELEM_SIZE; i++) {
            unsafe.putInt(unsafe_addr + (i * CARRIER_SIZE) , i);
        }
        arena = Arena.ofConfined();
        segment = arena.allocate(ALLOC_SIZE, 1);
        for (int i = 0; i < ELEM_SIZE; i++) {
            VH_INT.set(segment, (long) i, i);
        }
        if (asTypeCompiled) {
            compileAsType();
        }
    }

    public interface T { }

    static final int TYPE_SIZE = 100;
    static final Class<?>[] types;

    static {
        types = new Class<?>[TYPE_SIZE];
        ClassLoader customLoader = new URLClassLoader(new URL[0], LoopOverNonConstantAsType.class.getClassLoader());
        for (int i = 0 ; i < TYPE_SIZE ; i++) {
            types[i] = Proxy.newProxyInstance(customLoader,
                    new Class<?>[] { T.class }, (_, _, _) -> null).getClass();
        }
    }

    void compileAsType() {
        for (Class<?> type : types) {
            MethodHandle handle = MethodHandles.zero(Object.class);
            Class<?>[] args = new Class<?>[254];
            Arrays.fill(args, Object.class);
            handle = MethodHandles.dropArguments(handle, 0, args);
            for (int j = 0; j < args.length ; j++) {
                handle = handle.asType(handle.type().changeParameterType(j, type));
            }
        }
    }

    @TearDown
    public void tearDown() {
        arena.close();
        unsafe.freeMemory(unsafe_addr);
    }

    @Benchmark
    public long unsafe_loop() {
        long res = 0;
        for (int i = 0; i < ELEM_SIZE; i ++) {
            res += unsafe.getLong(unsafe_addr + (i * CARRIER_SIZE));
        }
        return res;
    }

    @Benchmark
    public long segment_loop() {
        long sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += segment.get(JAVA_LONG, i * CARRIER_SIZE);
        }
        return sum;
    }
}
