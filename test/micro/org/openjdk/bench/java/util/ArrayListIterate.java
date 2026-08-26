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

package org.openjdk.bench.java.util;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UseSerialGC", "-XX:+UnlockDiagnosticVMOptions", "-XX:LoopMaxUnroll=1", "-Xmx1g"})
@State(Scope.Benchmark)
public class ArrayListIterate {

    private static final int SIZE = 1048576;

    private ArrayList<Object> list;
    private int[] array;
    private String shortDescriptor;

    @Setup
    public void setup() {
        list = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            list.add(new Object());
        }
        array = new int[SIZE];
        shortDescriptor = "([IJLjava/lang/String;Z)Ljava/util/List;";
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void list_foreach(Blackhole bh) {
        for (Object o : list) {
            bh.consume(o);
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void list_indexed(Blackhole bh) {
        for (int i = 0; i < list.size(); i++) {
            bh.consume(list.get(i));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int array_index(Blackhole bh) {
        for (int i = 0; i < array.length; i++) {
            bh.consume(array[i]);
        }
        return array.length - 1;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int array_double_index(Blackhole bh) {
        int prev = -1;
        for (int i = 0; i < array.length; i++) {
            bh.consume(array[i]);
            prev = i;
        }
        return prev;
    }

    // The benchmark brought over from java.lang.constant.MethodTypeDescFactories
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public MethodTypeDesc mtd_ofDescriptor() {
        return MethodTypeDesc.ofDescriptor(shortDescriptor);
    }

}
