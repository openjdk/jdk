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
package org.openjdk.bench.valhalla.array.copy;

import jdk.internal.value.ValueClass;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Setup;

@Fork(value = 3, jvmArgsAppend = {"--enable-preview", "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED"})
public class Value032NullFree extends CopyBase {

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
        public ValueInt[] arr;

        @Setup
        public void setup() {
            arr = (ValueInt[]) ValueClass.newNullRestrictedAtomicArray(ValueInt.class, size, new ValueInt(0));
            for (int i = 0; i < size; i++) {
                arr[i] = new ValueInt(i);
            }
        }
    }

    public static class IntState extends SizeState {
        public InterfaceInt[] arr;

        @Setup
        public void setup() {
            arr = new InterfaceInt[size];
            for (int i = 0; i < size; i++) {
                arr[i] = new ValueInt(i);
            }
        }

    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copy_val(ValueInt[] dst, ValueInt[] src) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i];
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copy_int(InterfaceInt[] dst, InterfaceInt[] src) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i];
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void arraycopy_val(ValueInt[] dst, ValueInt[] src) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void arraycopy_int(InterfaceInt[] dst, InterfaceInt[] src) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @Benchmark
    public void copy_val_as_val(ValState st1, ValState st2) {
        copy_val(st1.arr, st2.arr);
    }

    @Benchmark
    public void arraycopy_val_as_val(ValState st1, ValState st2) {
        arraycopy_val(st1.arr, st2.arr);
    }

    @Benchmark
    public void copy_int_as_int(IntState st1, IntState st2) {
        copy_int(st1.arr, st2.arr);
    }

    @Benchmark
    public void arraycopy_int_as_int(IntState st1, IntState st2) {
        arraycopy_int(st1.arr, st2.arr);
    }


}
