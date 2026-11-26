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
package org.openjdk.bench.valhalla.callconv;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class Primitive8 {

    public static final int SIZE = 96;  // must be divisible by 2 and 3 and around 100

    public abstract static class InvocationLogic {
        public abstract int compute(int v1, int v2, int v3, int v4, int v5, int v6, int v7, int v8);
    }

    public static class InvokeImpl1 extends InvocationLogic {
        @Override
        public int compute(int v1, int v2, int v3, int v4, int v5, int v6, int v7, int v8) {
            return v1;
        }
    }

    public static class InvokeImpl2 extends InvocationLogic {
        @Override
        public int compute(int v1, int v2, int v3, int v4, int v5, int v6, int v7, int v8) {
            return v1;
        }
    }

    public static class InvokeImpl3 extends InvocationLogic {
        @Override
        public int compute(int v1, int v2, int v3, int v4, int v5, int v6, int v7, int v8) {
            return v1;
        }
    }

    @State(Scope.Thread)
    public static class StateTargets {
        InvocationLogic[] arr;

        @Setup
        public void setup() {
            arr = new InvocationLogic[SIZE];
            Arrays.setAll(arr, i -> getImpl(i, 3));
        }

        private  InvocationLogic getImpl(int i, int targets) {
            return switch (i % targets) {
                case 0 -> new InvokeImpl1();
                case 1 -> new InvokeImpl2();
                default -> new InvokeImpl3();
            };
        }
    }

    int a0 = 42;
    int a1 = 43;
    int a2 = 44;
    int a3 = 45;
    int a4 = 46;
    int a5 = 47;
    int a6 = 48;
    int a7 = 49;

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void primitive_args8(Blackhole bh, StateTargets st) {
        InvocationLogic[] arr = st.arr;
        for (InvocationLogic t : arr) {
            bh.consume(t.compute(a0, a1, a2, a3, a4, a5, a6, a7));
        }
    }

}
