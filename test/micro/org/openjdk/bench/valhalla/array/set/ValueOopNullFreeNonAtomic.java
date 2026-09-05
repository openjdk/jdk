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

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Setup;

@Fork(value = 3, jvmArgsAppend = {"--enable-preview", "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED"})
public class ValueOopNullFreeNonAtomic extends SetBase {

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

    @LooselyConsistentValue
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
            arr = (ValueRef[]) ValueClass.newNullRestrictedAtomicArray(ValueRef.class, size, new ValueRef(new IdentityInt(0)));
            for (int i = 0; i < size; i++) {
                arr[i] = new ValueRef(new IdentityInt(i));
            }
        }
    }

    public static class IntState extends SizeState {
        public InterfaceOop[] arr;

        @Setup
        public void setup() {
            arr = new InterfaceOop[size];
            for (int i = 0; i < size; i++) {
                arr[i] = new ValueRef(new IdentityInt(i));
            }
        }

    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public ValueRef get_val(int i) {
        return new ValueRef(new IdentityInt(i));
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public InterfaceOop get_int(int i) {
        return new ValueRef(new IdentityInt(i));
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void set_new_val(ValueRef[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = new ValueRef(new IdentityInt(i));
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void set_new_int(InterfaceOop[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = new ValueRef(new IdentityInt(i));
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void set_call_val(ValueRef[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = get_val(i);
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void set_call_int(InterfaceOop[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = get_int(i);
        }
    }

    @Benchmark
    public void set_new_val_as_val(ValState st1) {
        set_new_val(st1.arr);
    }

    @Benchmark
    public void set_new_int_as_int(IntState st1) {
        set_new_int(st1.arr);
    }

    @Benchmark
    public void set_call_val_as_val(ValState st1) {
        set_call_val(st1.arr);
    }

    @Benchmark
    public void set_call_int_as_int(IntState st1) {
        set_call_int(st1.arr);
    }

}
