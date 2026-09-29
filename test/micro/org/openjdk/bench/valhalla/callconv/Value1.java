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

@Fork(value = 3, jvmArgsAppend = {"--enable-preview"})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class Value1 {

    public static final int SIZE = 96;  // must be divisible by 2 and 3 and around 100

    public static value class ValueInt1 {

        public final int v0;

        public ValueInt1(int v0) {
            this.v0 = v0;
        }

        public int value() {
            return v0;
        }

        public static ValueInt1 valueOf(int value) {
            return new ValueInt1(value);
        }
    }

    public abstract static class InvocationLogic {
        public abstract ValueInt1 compute(ValueInt1 v1);
    }

    public static class InvokeImpl1 extends InvocationLogic {
        @Override
        public ValueInt1 compute(ValueInt1 v1) {
            return v1;
        }
    }

    public static class InvokeImpl2 extends InvocationLogic {
        @Override
        public ValueInt1 compute(ValueInt1 v1) {
            return v1;
        }
    }

    public static class InvokeImpl3 extends InvocationLogic {
        @Override
        public ValueInt1 compute(ValueInt1 v1) {
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

    ValueInt1 a0 = ValueInt1.valueOf(42);

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void value_args1(Blackhole bh, StateTargets st) {
        ValueInt1 v0 = a0;
        InvocationLogic[] arr = st.arr;
        for (InvocationLogic t : arr) {
            bh.consume(t.compute(v0));
        }
    }

}
