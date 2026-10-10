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

public class Primitive032 extends CopyBase {

    public static class PrimitiveState extends SizeState {
        public int[] arr;

        @Setup
        public void setup() {
            arr = new int[size];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = i;
            }
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static void copy_primitive(int[] dst, int[] src) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i];
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static void arraycopy_primitive(int[] dst, int[] src) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @Benchmark
    public void copy(PrimitiveState st1, PrimitiveState st2) {
        copy_primitive(st1.arr, st2.arr);
    }

    @Benchmark
    public void arraycopy(PrimitiveState st1, PrimitiveState st2) {
        arraycopy_primitive(st1.arr, st2.arr);
    }

}
