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
package org.openjdk.bench.valhalla.field.copy;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Setup;

@Fork(value = 3, jvmArgsAppend = {"--enable-preview"})
public class Value032 extends CopyBase {

    public static class ValWrapper {
        public ValueInt f;

        public ValWrapper(ValueInt f) {
            this.f = f;
        }
    }

    public static class IntWrapper {
        public InterfaceInt f;

        public IntWrapper(ValueInt f) {
            this.f = f;
        }
    }


    public interface InterfaceInt {
        public int value();
    }

    public static value class ValueInt implements InterfaceInt {
        public final int value;
        public ValueInt(int value) {
            this.value = value;
        }
        public int value() {
            return value;
        }
    }


    public static class ValState extends SizeState {
        public ValWrapper[] arr;

        @Setup
        public void setup() {
            arr = new ValWrapper[size];
            for (int i = 0; i < size; i++) {
                arr[i] = new ValWrapper(new ValueInt(i));
            }
        }
    }

    public static class IntState extends SizeState {
        public IntWrapper[] arr;

        @Setup
        public void setup() {
            arr = new IntWrapper[size];
            for (int i = 0; i < size; i++) {
                arr[i] = new IntWrapper(new ValueInt(i));
            }
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copy(ValWrapper[] dst, ValWrapper[] src) {
        for (int i = 0; i < src.length; i++) {
            dst[i].f = src[i].f;
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copy_to_int(IntWrapper[] dst, ValWrapper[] src) {
        for (int i = 0; i < src.length; i++) {
            dst[i].f = src[i].f;
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copy_to_val(ValWrapper[] dst, IntWrapper[] src) {
        for (int i = 0; i < src.length; i++) {
            dst[i].f = (ValueInt) src[i].f;
        }
    }

    @Benchmark
    public void copy_val(ValState st1, ValState st2) {
        copy(st1.arr, st2.arr);
    }

    @Benchmark
    public void copy_val_to_int(IntState st1, ValState st2) {
        copy_to_int(st1.arr, st2.arr);
    }

    @Benchmark
    public void copy_int_to_val(ValState st1, IntState st2) {
        copy_to_val(st1.arr, st2.arr);
    }

}
