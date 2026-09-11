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
package org.openjdk.bench.valhalla.sum;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Setup;

public class Identity extends SumBase {

    public interface InterfaceSum {
        public int sum();
    }

    public static class IdentityInt implements InterfaceSum {
        public final int v0;
        public IdentityInt(int value) {
            this.v0 = value;
        }
        public int sum() {
            return v0;
        }
    }

    public static class IdentityInt2_w2_d0 implements InterfaceSum {
        public final int v0, v1;

        public IdentityInt2_w2_d0(int v0, int v1) {
            this.v0 = v0;
            this.v1 = v1;
        }

        public int sum() {
            return v0 + v1;
        }
    }

    public static class IdentityInt2_w2_d1 implements InterfaceSum {
        public final IdentityInt v0, v1;

        public IdentityInt2_w2_d1(IdentityInt v0, IdentityInt v1) {
            this.v0 = v0;
            this.v1 = v1;
        }

        public IdentityInt2_w2_d1(int v0, int v1) {
            this(new IdentityInt(v0), new IdentityInt(v1));
        }

        public int sum() {
            return v0.sum() + v1.sum();
        }
    }

    public static class IdentityInt4_w4_d0 implements InterfaceSum {
        public final int v0, v1, v2, v3;

        public IdentityInt4_w4_d0(int v0, int v1, int v2, int v3) {
            this.v0 = v0;
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
        }

        public int sum() {
            return v0 + v1 + v2 + v3;
        }
    }

    public static class IdentityInt4_w4_d1 implements InterfaceSum {
        public final IdentityInt v0, v1, v2, v3;

        public IdentityInt4_w4_d1(IdentityInt v0, IdentityInt v1, IdentityInt v2, IdentityInt v3) {
            this.v0 = v0;
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
        }

        public IdentityInt4_w4_d1(int v0, int v1, int v2, int v3) {
            this(new IdentityInt(v0), new IdentityInt(v1), new IdentityInt(v2), new IdentityInt(v3));
        }

        public int sum() {
            return v0.sum() + v1.sum() + v2.sum() + v3.sum();
        }
    }

    public static class IdentityInt4_w2_d1 implements InterfaceSum {
        public final IdentityInt2_w2_d0 v0, v1;

        public IdentityInt4_w2_d1(IdentityInt2_w2_d0 v0, IdentityInt2_w2_d0 v1) {
            this.v0 = v0;
            this.v1 = v1;
        }

        public IdentityInt4_w2_d1(int v0, int v1, int v2, int v3) {
            this(new IdentityInt2_w2_d0(v0, v1), new IdentityInt2_w2_d0(v2, v3));
        }

        public int sum() {
            return v0.sum() + v1.sum();
        }
    }

    public static class IdentityInt4_w2_d2 implements InterfaceSum {

        public final IdentityInt2_w2_d1 v0, v1;

        public IdentityInt4_w2_d2(IdentityInt2_w2_d1 v0, IdentityInt2_w2_d1 v1) {
            this.v0 = v0;
            this.v1 = v1;
        }

        public IdentityInt4_w2_d2(int v0, int v1, int v2, int v3) {
            this(new IdentityInt2_w2_d1(v0, v1), new IdentityInt2_w2_d1(v2, v3));
        }
        public int sum() {
            return v0.sum() + v1.sum();
        }

    }

    public static class RefState_of_Int extends SizeState {
        public IdentityInt[] arr;
        @Setup
        public void setup() {
            arr = new IdentityInt[size * 4];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt(i);
            }
        }
    }

    public static class IntState_of_Int extends SizeState {
        public InterfaceSum[] arr;
        @Setup
        public void setup() {
            arr = new InterfaceSum[size * 4];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt(i);
            }
        }
    }

    public static class RefState_of_Int2_w2_d0 extends SizeState {
        public IdentityInt2_w2_d0[] arr;
        @Setup
        public void setup() {
            arr = new IdentityInt2_w2_d0[size * 2];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt2_w2_d0(2 * i, 2 * i + 1);
            }
        }
    }

    public static class IntState_of_Int2_w2_d0 extends SizeState {
        public InterfaceSum[] arr;
        @Setup
        public void setup() {
            arr = new InterfaceSum[size * 2];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt2_w2_d0(2 * i, 2 * i + 1);
            }
        }
    }

    public static class RefState_of_Int2_w2_d1 extends SizeState {
        public IdentityInt2_w2_d1[] arr;
        @Setup
        public void setup() {
            arr = new IdentityInt2_w2_d1[size * 2];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt2_w2_d1(2 * i, 2 * i + 1);
            }
        }
    }

    public static class IntState_of_Int2_w2_d1 extends SizeState {
        public InterfaceSum[] arr;
        @Setup
        public void setup() {
            arr = new InterfaceSum[size * 2];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt2_w2_d1(2 * i, 2 * i + 1);
            }
        }
    }

    public static class RefState_of_Int4_w4_d0 extends SizeState {
        public IdentityInt4_w4_d0[] arr;
        @Setup
        public void setup() {
            arr = new IdentityInt4_w4_d0[size];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt4_w4_d0(4 * i, 4 * i + 1, 4 * i + 2, 4 * i + 3);
            }
        }
    }

    public static class IntState_of_Int4_w4_d0 extends SizeState {
        public InterfaceSum[] arr;
        @Setup
        public void setup() {
            arr = new InterfaceSum[size];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt4_w4_d0(4 * i, 4 * i + 1, 4 * i + 2, 4 * i + 3);
            }
        }
    }

    public static class RefState_of_Int4_w4_d1 extends SizeState {
        public IdentityInt4_w4_d1[] arr;
        @Setup
        public void setup() {
            arr = new IdentityInt4_w4_d1[size];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt4_w4_d1(4 * i, 4 * i + 1, 4 * i + 2, 4 * i + 3);
            }
        }
    }

    public static class IntState_of_Int4_w4_d1 extends SizeState {
        public InterfaceSum[] arr;
        @Setup
        public void setup() {
            arr = new InterfaceSum[size];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt4_w4_d1(4 * i, 4 * i + 1, 4 * i + 2, 4 * i + 3);
            }
        }
    }

    public static class RefState_of_Int4_w2_d1 extends SizeState {
        public IdentityInt4_w2_d1[] arr;
        @Setup
        public void setup() {
            arr = new IdentityInt4_w2_d1[size];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt4_w2_d1(4 * i, 4 * i + 1, 4 * i + 2, 4 * i + 3);
            }
        }
    }

    public static class IntState_of_Int4_w2_d1 extends SizeState {
        public InterfaceSum[] arr;
        @Setup
        public void setup() {
            arr = new InterfaceSum[size];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt4_w2_d1(4 * i, 4 * i + 1, 4 * i + 2, 4 * i + 3);
            }
        }
    }

    public static class RefState_of_Int4_w2_d2 extends SizeState {
        public IdentityInt4_w2_d2[] arr;
        @Setup
        public void setup() {
            arr = new IdentityInt4_w2_d2[size];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt4_w2_d2(4 * i, 4 * i + 1, 4 * i + 2, 4 * i + 3);
            }
        }
    }

    public static class IntState_of_Int4_w2_d2 extends SizeState {
        public InterfaceSum[] arr;
        @Setup
        public void setup() {
            arr = new InterfaceSum[size];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new IdentityInt4_w2_d2(4 * i, 4 * i + 1, 4 * i + 2, 4 * i + 3);
            }
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int sum_interface(InterfaceSum[] src) {
        int s = 0;
        for (var v : src) {
            s += v.sum();
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int sum_ref_of_Int(IdentityInt[] src) {
        int s = 0;
        for (var v : src) {
            s += v.sum();
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int sum_ref_of_Int2_w2_d0(IdentityInt2_w2_d0[] src) {
        int s = 0;
        for (var v : src) {
            s += v.sum();
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int sum_ref_of_Int2_w2_d1(IdentityInt2_w2_d1[] src) {
        int s = 0;
        for (var v : src) {
            s += v.sum();
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int sum_ref_of_Int4_w4_d0(IdentityInt4_w4_d0[] src) {
        int s = 0;
        for (var v : src) {
            s += v.sum();
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int sum_ref_of_Int4_w4_d1(IdentityInt4_w4_d1[] src) {
        int s = 0;
        for (var v : src) {
            s += v.sum();
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int sum_ref_of_Int4_w2_d1(IdentityInt4_w2_d1[] src) {
        int s = 0;
        for (var v : src) {
            s += v.sum();
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int sum_ref_of_Int4_w2_d2(IdentityInt4_w2_d2[] src) {
        int s = 0;
        for (var v : src) {
            s += v.sum();
        }
        return s;
    }

    @Benchmark
    public int sum_interface_of_Int(IntState_of_Int st) {
        return sum_interface(st.arr);
    }

    @Benchmark
    public int sum_interface_of_Int2_w2_d0(IntState_of_Int2_w2_d0 st) {
        return sum_interface(st.arr);
    }

    @Benchmark
    public int sum_interface_of_Int2_w2_d1(IntState_of_Int2_w2_d1 st) {
        return sum_interface(st.arr);
    }

    @Benchmark
    public int sum_interface_of_Int4_w4_d0(IntState_of_Int4_w4_d0 st) {
        return sum_interface(st.arr);
    }

    @Benchmark
    public int sum_interface_of_Int4_w4_d1(IntState_of_Int4_w4_d1 st) {
        return sum_interface(st.arr);
    }

    @Benchmark
    public int sum_interface_of_Int4_w2_d1(IntState_of_Int4_w2_d1 st) {
        return sum_interface(st.arr);
    }

    @Benchmark
    public int sum_interface_of_Int4_w2_d2(IntState_of_Int4_w2_d2 st) {
        return sum_interface(st.arr);
    }

    @Benchmark
    public int sum_ref_of_Int(RefState_of_Int st) {
        return sum_ref_of_Int(st.arr);
    }

    @Benchmark
    public int sum_ref_of_Int2_w2_d0(RefState_of_Int2_w2_d0 st) {
        return sum_ref_of_Int2_w2_d0(st.arr);
    }

    @Benchmark
    public int sum_ref_of_Int2_w2_d1(RefState_of_Int2_w2_d1 st) {
        return sum_ref_of_Int2_w2_d1(st.arr);
    }

    @Benchmark
    public int sum_ref_of_Int4_w4_d0(RefState_of_Int4_w4_d0 st) {
        return sum_ref_of_Int4_w4_d0(st.arr);
    }

    @Benchmark
    public int sum_ref_of_Int4_w4_d1(RefState_of_Int4_w4_d1 st) {
        return sum_ref_of_Int4_w4_d1(st.arr);
    }

    @Benchmark
    public int sum_ref_of_Int4_w2_d1(RefState_of_Int4_w2_d1 st) {
        return sum_ref_of_Int4_w2_d1(st.arr);
    }

    @Benchmark
    public int sum_ref_of_Int4_w2_d2(RefState_of_Int4_w2_d2 st) {
        return sum_ref_of_Int4_w2_d2(st.arr);
    }

}
