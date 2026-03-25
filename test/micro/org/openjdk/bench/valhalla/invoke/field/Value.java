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
package org.openjdk.bench.valhalla.invoke.field;

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

    public static class IntWrapper {
        final InterfaceInt f;

        public IntWrapper(InterfaceInt f) {
            this.f = f;
        }
    }

    public static class ValWrapper {
        final ValueInt0 f;

        public ValWrapper(ValueInt0 f) {
            this.f = f;
        }
    }

    @State(Scope.Thread)
    public static class Int0State {
        public IntWrapper[] arr;
        @Setup
        public void setup() {
            arr = new IntWrapper[SIZE];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IntWrapper(new ValueInt0(i));
            }
        }
    }

    @State(Scope.Thread)
    public static class Int1State {
        public IntWrapper[] arr;
        @Setup
        public void setup() {
            arr = new IntWrapper[SIZE];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IntWrapper(new ValueInt1(i));
            }
        }
    }

    @State(Scope.Thread)
    public static class Int2State {
        public IntWrapper[] arr;
        @Setup
        public void setup() {
            arr = new IntWrapper[SIZE];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IntWrapper(new ValueInt2(i));
            }
        }
    }

    @State(Scope.Thread)
    public static class Ref0State {
        public ValWrapper[] arr;
        @Setup
        public void setup() {
            arr = new ValWrapper[SIZE];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new ValWrapper(new ValueInt0(i));
            }
        }
    }


    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int reduce_int(IntWrapper[] arr) {
        int r = 0;
        for (int i = 0; i < arr.length; i++) {
            r += arr[i].f.value();
        }
        return r;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int reduce_val(ValWrapper[] arr) {
        int r = 0;
        for (int i = 0; i < arr.length; i++) {
            r += arr[i].f.value();
        }
        return r;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE * 6)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int target0(Ref0State st0, Ref0State st1, Ref0State st2, Ref0State st3, Ref0State st4, Ref0State st5) {
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
    public int target1(Int0State st0, Int0State st1, Int0State st2, Int0State st3, Int0State st4, Int0State st5) {
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
    public int target2(Int0State st0, Int0State st1, Int0State st2, Int1State st3, Int1State st4, Int1State st5) {
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
    public int target3(Int0State st0, Int0State st1, Int1State st2, Int1State st3, Int2State st4, Int2State st5) {
        return reduce_int(st0.arr) +
                reduce_int(st1.arr) +
                reduce_int(st2.arr) +
                reduce_int(st3.arr) +
                reduce_int(st4.arr) +
                reduce_int(st5.arr);
    }

}