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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Setup;

public class Identity extends CopyBase {

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

    public static class IntState extends SizeState {
        public InterfaceInt[] arr;

        @Setup
        public void setup() {
            arr = new InterfaceInt[size];
            for (int i = 0; i < size; i++) {
                arr[i] = new IdentityInt(i);
            }
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copy_ref(IdentityInt[] dst, IdentityInt[] src) {
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
    public void arraycopy_ref(IdentityInt[] dst, IdentityInt[] src) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void arraycopy_int(InterfaceInt[] dst, InterfaceInt[] src) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @Benchmark
    public void copy_ref_as_ref(RefState st1, RefState st2) {
        copy_ref(st1.arr, st2.arr);
    }

    @Benchmark
    public void arraycopy_ref_as_ref(RefState st1, RefState st2) {
        arraycopy_ref(st1.arr, st2.arr);
    }

}
