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
package org.openjdk.bench.valhalla.array.fill;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Arrays;

@Fork(value = 3, jvmArgsAppend = {"--enable-preview"})
public class ValueOop extends FillBase {

    public static class IdentityInt {
        public final int value;
        public IdentityInt(int value) {
            this.value = value;
        }
        public int value() {
            return value;
        }
    }

    public interface InterfaceOop {
        public IdentityInt value();
    }

    public static value class ValueRef implements InterfaceOop {

        public final IdentityInt value;

        public ValueRef(IdentityInt value) {
            this.value = value;
        }

        public IdentityInt value() {
            return value;
        }

    }

    public static class ValState extends SizeState {
        public ValueRef[] arr;

        @Setup
        public void setup() {
            arr = new ValueRef[size];
            for (int i = 0; i < size; i++) {
                arr[i] = new ValueRef(new IdentityInt(i));
            }
        }
    }

    public static class StaticHolder {
        public static ValueRef VALUE = new ValueRef(new IdentityInt(42));
    }

    @State(Scope.Thread)
    public static class InstanceHolder {
        public ValueRef VALUE = new ValueRef(new IdentityInt(42));
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public ValueRef get_val(int i) {
        return new ValueRef(new IdentityInt(i));
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void fill_new_val(ValueRef[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = new ValueRef(new IdentityInt(42));
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void arrayfill_new_val(ValueRef[] dst) {
        Arrays.fill(dst, new ValueRef(new IdentityInt(42)));
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void fill_local_val(ValueRef[] dst) {
        ValueRef local = get_val(42);
        for (int i = 0; i < dst.length; i++) {
            dst[i] = local;
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void arrayfill_local_val(ValueRef[] dst) {
        Arrays.fill(dst, get_val(42));
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void fill_static_val(ValueRef[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = StaticHolder.VALUE;
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void arrayfill_static_val(ValueRef[] dst) {
        Arrays.fill(dst, StaticHolder.VALUE);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void fill_instance_val(ValueRef[] dst, InstanceHolder ih) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = ih.VALUE;
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void arrayfill_instance_val(ValueRef[] dst, InstanceHolder ih) {
        Arrays.fill(dst, ih.VALUE);
    }

    @Benchmark
    public void fill_new(ValState st1) {
        fill_new_val(st1.arr);
    }

    @Benchmark
    public void arrayfill_new(ValState st1) {
        arrayfill_new_val(st1.arr);
    }

    @Benchmark
    public void fill_local(ValState st1) {
        fill_local_val(st1.arr);
    }

    @Benchmark
    public void arrayfill_local(ValState st1) {
        arrayfill_local_val(st1.arr);
    }

    @Benchmark
    public void fill_static(ValState st1) {
        fill_static_val(st1.arr);
    }

    @Benchmark
    public void arrayfill_static(ValState st1) {
        arrayfill_static_val(st1.arr);
    }

    @Benchmark
    public void fill_instance(ValState st1, InstanceHolder ih) {
        fill_instance_val(st1.arr, ih);
    }

    @Benchmark
    public void arrayfill_instance(ValState st1, InstanceHolder ih) {
        arrayfill_instance_val(st1.arr, ih);
    }

}
