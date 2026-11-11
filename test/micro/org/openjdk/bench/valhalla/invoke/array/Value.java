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
package org.openjdk.bench.valhalla.invoke.array;

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

import java.util.concurrent.TimeUnit;

@Fork(value = 3, jvmArgsAppend = {"--enable-preview"})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class Value {

    /*
        virtual method invocations:
        target0 - statically known target method.
        target1 - the single invoked method (should be inlined)
        target2 - two invoked method (should be inlined, cache-inline)
        target3 - thee invoked method (shouldn't be inlined)

     */


    public static final int SIZE = 128;

    public interface InterfaceInt {
        public int value();
    }

    public static value class ValueInt0 implements InterfaceInt {
        public final int value;
        public ValueInt0(int value) {
            this.value = value;
        }
        @Override
        public int value() {
            return value;
        }
    }

    public static value class ValueInt1 implements InterfaceInt {
        public final int value;
        public ValueInt1(int value) {
            this.value = value;
        }
        @Override
        public int value() {
            return value;
        }
    }

    public static value class ValueInt2 implements InterfaceInt {
        public final int value;
        public ValueInt2(int value) {
            this.value = value;
        }
        @Override
        public int value() {
            return value;
        }
    }

    @State(Scope.Thread)
    public static class Int0State {
        public InterfaceInt[] arr;
        @Setup
        public void setup() {
            arr = new InterfaceInt[SIZE];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new ValueInt0(i);
            }
        }
    }

    @State(Scope.Thread)
    public static class Int1State {
        public InterfaceInt[] arr;
        @Setup
        public void setup() {
            arr = new InterfaceInt[SIZE];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new ValueInt1(i);
            }
        }
    }

    @State(Scope.Thread)
    public static class Int2State {
        public InterfaceInt[] arr;
        @Setup
        public void setup() {
            arr = new InterfaceInt[SIZE];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new ValueInt2(i);
            }
        }
    }

    @State(Scope.Thread)
    public static class Val0State {
        public ValueInt0[] arr;
        @Setup
        public void setup() {
            arr = new ValueInt0[SIZE];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new ValueInt0(i);
            }
        }
    }

    @State(Scope.Thread)
    public static class Val1State {
        public ValueInt1[] arr;
        @Setup
        public void setup() {
            arr = new ValueInt1[SIZE];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new ValueInt1(i);
            }
        }
    }

    @State(Scope.Thread)
    public static class Val22State {
        public ValueInt2[] arr;
        @Setup
        public void setup() {
            arr = new ValueInt2[SIZE];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new ValueInt2(i);
            }
        }
    }


    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int reduce_int(InterfaceInt[] arr) {
        int r = 0;
        for (int i = 0; i < arr.length; i++) {
            r += arr[i].value();
        }
        return r;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int reduce_val(ValueInt0[] arr) {
        int r = 0;
        for (int i = 0; i < arr.length; i++) {
            r += arr[i].value();
        }
        return r;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE * 6)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int target0(Val0State st0, Val0State st1, Val0State st2, Val0State st3, Val0State st4, Val0State st5) {
        return reduce_val(st0.arr) +
               reduce_val(st1.arr) +
               reduce_val(st2.arr) +
               reduce_val(st3.arr) +
               reduce_val(st4.arr) +
               reduce_val(st5.arr);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE * 6)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int target1_v(Val0State st0, Val0State st1, Val0State st2, Val0State st3, Val0State st4, Val0State st5) {
        return reduce_int(st0.arr) +
               reduce_int(st1.arr) +
               reduce_int(st2.arr) +
               reduce_int(st3.arr) +
               reduce_int(st4.arr) +
               reduce_int(st5.arr);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE * 6)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int target1_i(Int0State st0, Int0State st1, Int0State st2, Int0State st3, Int0State st4, Int0State st5) {
        return reduce_int(st0.arr) +
               reduce_int(st1.arr) +
               reduce_int(st2.arr) +
               reduce_int(st3.arr) +
               reduce_int(st4.arr) +
               reduce_int(st5.arr);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE * 6)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int target1_vi(Val0State st0, Int0State st1, Val0State st2, Int0State st3, Val0State st4, Int0State st5) {
        return reduce_int(st0.arr) +
                reduce_int(st1.arr) +
                reduce_int(st2.arr) +
                reduce_int(st3.arr) +
                reduce_int(st4.arr) +
                reduce_int(st5.arr);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE * 6)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int target2_v(Val0State st0, Val0State st1, Val0State st2, Val1State st3, Val1State st4, Val1State st5) {
        return reduce_int(st0.arr) +
               reduce_int(st1.arr) +
               reduce_int(st2.arr) +
               reduce_int(st3.arr) +
               reduce_int(st4.arr) +
               reduce_int(st5.arr);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE * 6)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int target2_i(Int0State st0, Int0State st1, Int0State st2, Int1State st3, Int1State st4, Int1State st5) {
        return reduce_int(st0.arr) +
               reduce_int(st1.arr) +
               reduce_int(st2.arr) +
               reduce_int(st3.arr) +
               reduce_int(st4.arr) +
               reduce_int(st5.arr);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE * 6)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int target2_vi(Val0State st0, Int0State st1, Val0State st2, Int1State st3, Val1State st4, Int1State st5) {
        return reduce_int(st0.arr) +
                reduce_int(st1.arr) +
                reduce_int(st2.arr) +
                reduce_int(st3.arr) +
                reduce_int(st4.arr) +
                reduce_int(st5.arr);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE * 6)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int target3_v(Val0State st0, Val0State st1, Val1State st2, Val1State st3, Val22State st4, Val22State st5) {
        return reduce_int(st0.arr) +
                reduce_int(st1.arr) +
                reduce_int(st2.arr) +
                reduce_int(st3.arr) +
                reduce_int(st4.arr) +
                reduce_int(st5.arr);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE * 6)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int target3_i(Int0State st0, Int0State st1, Int1State st2, Int1State st3, Int2State st4, Int2State st5) {
        return reduce_int(st0.arr) +
                reduce_int(st1.arr) +
                reduce_int(st2.arr) +
                reduce_int(st3.arr) +
                reduce_int(st4.arr) +
                reduce_int(st5.arr);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE * 6)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int target3_vi(Val0State st0, Int0State st1, Val1State st2, Int1State st3, Val22State st4, Int2State st5) {
        return reduce_int(st0.arr) +
                reduce_int(st1.arr) +
                reduce_int(st2.arr) +
                reduce_int(st3.arr) +
                reduce_int(st4.arr) +
                reduce_int(st5.arr);
    }

}