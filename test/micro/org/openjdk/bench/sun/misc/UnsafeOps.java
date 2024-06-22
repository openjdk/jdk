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
package org.openjdk.bench.sun.misc;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import sun.misc.Unsafe;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@SuppressWarnings("removal")
public class UnsafeOps {
    static final Unsafe U;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (Unsafe) f.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException();
        }
    }

    private static class TestClass {
        long value;
    }

    private Object object;
    private long valueOffset;
    private long address;

    @Setup
    public void setup() throws Exception {
        object = new TestClass();
        Field f = TestClass.class.getDeclaredField("value");
        valueOffset = U.objectFieldOffset(f);

        address = U.allocateMemory(1000);
    }

    @TearDown
    public void finish() {
        U.freeMemory(address);
    }

    @Benchmark
    public void putLongOnHeap() {
        U.putLong(object, 0, 99);
    }

    @Benchmark
    public long getLongOnHeap() {
        return U.getLong(object, 0);
    }

    @Benchmark
    public void putLongOffHeap() {
        U.putLong(null, address, 99);
    }

    @Benchmark
    public long getLongOffHeap() {
        return U.getLong(null, address);
    }
}
