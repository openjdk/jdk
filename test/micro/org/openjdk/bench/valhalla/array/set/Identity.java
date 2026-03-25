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
package org.openjdk.bench.valhalla.array.set;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Setup;

public class Identity extends SetBase {

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
    public IdentityInt get_ref(int i) {
        return new IdentityInt(i);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public InterfaceInt get_int(int i) {
        return new IdentityInt(i);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void set_new_ref(IdentityInt[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = new IdentityInt(i);
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void set_new_int(InterfaceInt[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = new IdentityInt(i);
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void set_call_ref(IdentityInt[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = get_ref(i);
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void set_call_int(InterfaceInt[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = get_int(i);
        }
    }

    @Benchmark
    public void set_new_ref_as_ref(RefState st1) {
        set_new_ref(st1.arr);
    }

    @Benchmark
    public void set_new_int_as_int(IntState st1) {
        set_new_int(st1.arr);
    }

    @Benchmark
    public void set_call_ref_as_ref(RefState st1) {
        set_call_ref(st1.arr);
    }

    @Benchmark
    public void set_call_int_as_int(IntState st1) {
        set_call_int(st1.arr);
    }

}
