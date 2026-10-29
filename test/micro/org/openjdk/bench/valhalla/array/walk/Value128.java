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
package org.openjdk.bench.valhalla.array.walk;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Setup;

@Fork(value = 3, jvmArgsAppend = {"--enable-preview"})
public class Value128 extends WalkBase {

    public interface InterfaceInt {
        public int value();
    }

    public static value class ValueInt4 implements InterfaceInt {

        public final int prevalue0;
        public final int prevalue1;
        public final int prevalue2;

        public final int value;

        public ValueInt4(int value) {
            this.prevalue0 = value;
            this.prevalue1 = value;
            this.prevalue2 = value;
            this.value = value;
        }

        public int value() {
            return value;
        }

    }

    public static class ValState extends SizeState {
        public ValueInt4[] arr;

        @Setup
        public void setup() {
            arr = new ValueInt4[size];
            int[] a = makeRandomRing(arr.length);
            for (int i = 0; i < a.length; i++) {
                arr[i] = new ValueInt4(a[i]);
            }
        }
    }

    public static class IntState extends SizeState {
        public InterfaceInt[] arr;

        @Setup
        public void setup() {
            arr = new InterfaceInt[size];
            int[] a = makeRandomRing(arr.length);
            for (int i = 0; i < a.length; i++) {
                arr[i] = new ValueInt4(a[i]);
            }
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int walk_val(ValueInt4[] values) {
        int steps = 0;
        for (int i = values[0].value(); i != 0; i = values[i].value()) steps++;
        return steps;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int walk_int(InterfaceInt[] values) {
        int steps = 0;
        for (int i = values[0].value(); i != 0; i = values[i].value()) steps++;
        return steps;
    }

    @Benchmark
    public int walk_val_as_val(ValState st) {
        return walk_val(st.arr);
    }

    @Benchmark
    public int walk_int_as_int(IntState st) {
        return walk_int(st.arr);
    }

}
