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
package org.openjdk.bench.valhalla.array.readpartial;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

@Fork(value = 3, jvmArgsAppend = {"--enable-preview", "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED"})
public class ValueOopNullFreeNonAtomic extends ReadBase {

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
    public static value class ValueRef4 implements InterfaceOop {
        public final IdentityInt prevalue0;
        public final IdentityInt prevalue1;
        public final IdentityInt prevalue2;
        public final IdentityInt value;

        public ValueRef4(IdentityInt value) {
            this.prevalue0 = value;
            this.prevalue1 = value;
            this.prevalue2 = value;
            this.value = value;
        }

        public IdentityInt value() {
            return value;
        }

    }

    public static class ValState extends SizeState {
        public ValueRef4[] arr;

        @Setup
        public void setup() {
            arr = (ValueRef4[]) ValueClass.newNullRestrictedAtomicArray(ValueRef4.class, size, new ValueRef4(new IdentityInt(0)));
            for (int i = 0; i < size; i++) {
                arr[i] = new ValueRef4(new IdentityInt(i));
            }
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void read_val(ValueRef4[] src, Blackhole bh) {
        for (int i = 0; i < src.length; i++) {
            bh.consume(src[i].value);
        }
    }

    @Benchmark
    public void read(ValState st1, Blackhole bh) {
        read_val(st1.arr, bh);
    }

}
