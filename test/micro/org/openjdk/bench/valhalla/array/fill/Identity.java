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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Arrays;

public class Identity extends FillBase {

    public interface InterfaceInt {
        public int value();
    }

    public static class IdentityInt implements InterfaceInt {
        public final int value;
        public IdentityInt(int value) {
            this.value = value;
        }
        public int value() {
            return value;
        }
    }


    public static class RefState extends SizeState {
        public IdentityInt[] arr;

        @Setup
        public void setup() {
            arr = new IdentityInt[size];
            for (int i = 0; i < size; i++) {
                arr[i] = new IdentityInt(i);
            }
        }
    }

    public static class StaticHolder {
        public static IdentityInt VALUE = new IdentityInt(42);
    }

    @State(Scope.Thread)
    public static class InstanceHolder {
        public IdentityInt VALUE = new IdentityInt(42);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public IdentityInt get_ref(int i) {
        return new IdentityInt(i);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void fill_new_ref(IdentityInt[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = new IdentityInt(42);
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void arrayfill_new_ref(IdentityInt[] dst) {
        Arrays.fill(dst, new IdentityInt(42));
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void fill_local_ref(IdentityInt[] dst) {
        IdentityInt local = get_ref(42);
        for (int i = 0; i < dst.length; i++) {
            dst[i] = local;
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void arrayfill_local_ref(IdentityInt[] dst) {
        Arrays.fill(dst, get_ref(42));
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void fill_static_ref(IdentityInt[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = StaticHolder.VALUE;
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void arrayfill_static_ref(IdentityInt[] dst) {
        Arrays.fill(dst, StaticHolder.VALUE);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void fill_instance_ref(IdentityInt[] dst, InstanceHolder ih) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = ih.VALUE;
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void arrayfill_instance_ref(IdentityInt[] dst, InstanceHolder ih) {
        Arrays.fill(dst, ih.VALUE);
    }

    @Benchmark
    public void fill_new(RefState st1) {
        fill_new_ref(st1.arr);
    }

    @Benchmark
    public void arrayfill_new(RefState st1) {
        arrayfill_new_ref(st1.arr);
    }

    @Benchmark
    public void fill_local(RefState st1) {
        fill_local_ref(st1.arr);
    }

    @Benchmark
    public void arrayfill_local(RefState st1) {
        arrayfill_local_ref(st1.arr);
    }

    @Benchmark
    public void fill_static(RefState st1) {
        fill_static_ref(st1.arr);
    }

    @Benchmark
    public void arrayfill_static(RefState st1) {
        arrayfill_static_ref(st1.arr);
    }

    @Benchmark
    public void fill_instance(RefState st1, InstanceHolder ih) {
        fill_instance_ref(st1.arr, ih);
    }

    @Benchmark
    public void arrayfill_instance(RefState st1, InstanceHolder ih) {
        arrayfill_instance_ref(st1.arr, ih);
    }

}
