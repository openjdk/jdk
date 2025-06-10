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

package org.openjdk.bench.java.lang.stable;

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
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.util.concurrent.TimeUnit.*;

@Warmup(iterations = 5, time = 5, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(value = 1, jvmArgs = { "--enable-preview" })
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(NANOSECONDS)
@State(Scope.Benchmark)
public class VarHandleHolderBenchmark {

    private static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("x"),
            ValueLayout.JAVA_INT.withName("y")
    );

    private static final long SIZEOF = LAYOUT.byteSize();
    private static final long OFFSET_X = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("x"));
    private static final long OFFSET_Y = LAYOUT.byteOffset(groupElement("y"));

    static final class MyVarHandleLookup implements Function<String, VarHandle> {
        @Override
        public VarHandle apply(String name) {
            return LAYOUT.arrayElementVarHandle(groupElement(name)).withInvokeExactBehavior();
        }
    }

    private static final Function<String, VarHandle> VAR_HANDLE_FUNCTION = new MyVarHandleLookup();

    private static final VarHandle VH_X = VAR_HANDLE_FUNCTION.apply("x");
    private static final VarHandle VH_Y = VAR_HANDLE_FUNCTION.apply("y");

    private static final Supplier<VarHandle> SV_X = StableValue.supplier(() -> VAR_HANDLE_FUNCTION.apply("x"));
    private static final Supplier<VarHandle> SV_Y = StableValue.supplier(() -> VAR_HANDLE_FUNCTION.apply("y"));

    private static final Map<String, VarHandle> U_MAP = Map.of(
            "x", VH_X,
            "y", VH_Y);

    private static final Map<String, VarHandle> U_MAP_ELEMENT = Map.of(
            "x", LAYOUT.varHandle(groupElement("x")),
            "y", LAYOUT.varHandle(groupElement("y")));

    private static final Map<String, VarHandle> S_MAP = StableValue.map(
            Set.of("x", "y"),
            VAR_HANDLE_FUNCTION);

    private static final Function<String, VarHandle> S_FUN = StableValue.function(
            Set.of("x", "y"),
            VAR_HANDLE_FUNCTION);

    private static final MemorySegment confined;
    static {
        var array = new int[512 * (int) SIZEOF / (int) ValueLayout.JAVA_INT.byteSize()];
        var heap = MemorySegment.ofArray(array);
        for(var i = 0; i < 512; i++) {
            heap.set(ValueLayout.JAVA_INT, i * SIZEOF + OFFSET_X, i);
            heap.set(ValueLayout.JAVA_INT, i * SIZEOF + OFFSET_Y, i);
        }
        confined = Arena.ofConfined().allocate(LAYOUT, 512);
        confined.copyFrom(heap);
    }

    @Benchmark
    public int confinedVarHandleLoop() {
        var sum = 0;
        for (var i = 0; i < 512; i++) {
            var x = (int) VH_X.get(confined, 0L, (long) i);
            var y = (int) VH_Y.get(confined, 0L, (long) i);
            sum += x /*+y*/;
        }
        return sum;
    }

    @Benchmark
    public int confinedStableValueLoop() {
        var sum = 0;
        for (var i = 0; i < 512; i++) {
            var x = (int) SV_X.get().get(confined, 0L, (long) i);
            var y = (int) SV_Y.get().get(confined, 0L, (long) i);
            sum += x + y;
        }
        return sum;
    }

    @Benchmark
    public int confinedStableMapLoop() {
        var sum = 0;
        for (var i = 0; i < 512; i++) {
            var x = (int) S_MAP.get("x").get(confined, 0L, (long) i);
            var y = (int) S_MAP.get("y").get(confined, 0L, (long) i);
            sum += x + y;
        }
        return sum;
    }

    @Benchmark
    public int confinedStableMapElementLoop() {
        var sum = 0;
        for (var i = 0; i < 512; i++) {
            var x = (int) U_MAP_ELEMENT.get("x").get(confined, i * 8L);
            var y = (int) U_MAP_ELEMENT.get("y").get(confined, i * 8L);
            sum += x + y;
        }
        return sum;
    }

    @Benchmark
    public int confinedUnmodifiableMapLoop() {
        var sum = 0;
        for (var i = 0; i < 512; i++) {
            var x = (int) U_MAP.get("x").get(confined, 0L, (long) i);
            var y = (int) U_MAP.get("y").get(confined, 0L, (long) i);
            sum += x + y;
        }
        return sum;
    }

    @Benchmark
    public int confinedStableFunctionLoop() {
        var sum = 0;
        for (var i = 0; i < 512; i++) {
            var x = (int) S_FUN.apply("x").get(confined, 0L, (long) i);
            var y = (int) S_FUN.apply("y").get(confined, 0L, (long) i);
            sum += x + y;
        }
        return sum;
    }
}