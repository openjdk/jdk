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
package org.openjdk.bench.valhalla.acmp.array;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/*
 *  For proper results it should be executed:
 *  java -jar target/benchmarks.jar org.openjdk.bench.valhalla.acmp.array.Identity  -wmb "org.openjdk.bench.valhalla.acmp.array.Identity.*050"
 */

@Fork(3)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class Identity {

    public static final int SIZE = 100;

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int cmp_branch_obj(Object[] objects1, Object[] objects2) {
        int s = 0;
        for (int i = 0; i < SIZE; i++) {
            if (objects1[i] == objects2[i]) {
                s += 1;
            } else {
                s -= 1;
            }
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int cmp_branch_ref(IdentityInt[] objects1, IdentityInt[] objects2) {
        int s = 0;
        for (int i = 0; i < SIZE; i++) {
            if (objects1[i] == objects2[i]) {
                s += 1;
            } else {
                s -= 1;
            }
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static boolean cmp_result_ref(IdentityInt[] objects1, IdentityInt[] objects2) {
        boolean s = false;
        for (int i = 0; i < SIZE; i++) {
            s ^= objects1[i] == objects2[i];
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static boolean cmp_result_obj(Object[] objects1, Object[] objects2) {
        boolean s = false;
        for (int i = 0; i < SIZE; i++) {
            s ^= objects1[i] == objects2[i];
        }
        return s;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int branch_obj_equals000(ObjState00 st) {
        return cmp_branch_obj(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int branch_obj_equals025(ObjState25 st) {
        return cmp_branch_obj(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int branch_obj_equals050(ObjState50 st) {
        return cmp_branch_obj(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int branch_obj_equals075(ObjState75 st) {
        return cmp_branch_obj(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int branch_obj_equals100(ObjState100 st) {
        return cmp_branch_obj(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int branch_ref_equals000(RefState00 st) {
        return cmp_branch_ref(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int branch_ref_equals025(RefState25 st) {
        return cmp_branch_ref(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int branch_ref_equals050(RefState50 st) {
        return cmp_branch_ref(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int branch_ref_equals075(RefState75 st) {
        return cmp_branch_ref(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int branch_ref_equals100(RefState100 st) {
        return cmp_branch_ref(st.arr1, st.arr2);
    }


    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public boolean result_obj_equals000(ObjState00 st) {
        return cmp_result_obj(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public boolean result_obj_equals025(ObjState25 st) {
        return cmp_result_obj(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public boolean result_obj_equals050(ObjState50 st) {
        return cmp_result_obj(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public boolean result_obj_equals075(ObjState75 st) {
        return cmp_result_obj(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public boolean result_obj_equals100(ObjState100 st) {
        return cmp_result_obj(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public boolean result_ref_equals000(RefState00 st) {
        return cmp_result_ref(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public boolean result_ref_equals025(RefState25 st) {
        return cmp_result_ref(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public boolean result_ref_equals050(RefState50 st) {
        return cmp_result_ref(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public boolean result_ref_equals075(RefState75 st) {
        return cmp_result_ref(st.arr1, st.arr2);
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public boolean result_ref_equals100(RefState100 st) {
        return cmp_result_ref(st.arr1, st.arr2);
    }

    public static class IdentityInt {

        public final int v0;

        public IdentityInt(int v0) {
            this.v0 = v0;
        }

        public int value() {
            return v0;
        }

    }

    private static void populate(Object[] arr1, Object[] arr2, int eq) {
        if (eq <= 0) {
            arr1[0] = null;
            arr2[0] = new IdentityInt(1);
            arr1[1] = new IdentityInt(2);
            arr2[1] = null;
            for (int i = 2; i < SIZE; i++) {
                arr1[i] = new IdentityInt(2 * i);
                arr2[i] = new IdentityInt(2 * i + 1);
            }
        } else if (eq >= 100) {
            arr2[0] = arr1[0] = null;
            for (int i = 1; i < SIZE; i++) {
                arr2[i] = arr1[i] = new IdentityInt(i);
            }
        } else {
            BitSet eqset = new Random(42).ints(0, SIZE).distinct().limit(eq * SIZE / 100).collect(BitSet::new, BitSet::set, BitSet::or);
            boolean samenulls = true;
            int distinctnulls = 0;
            for (int i = 0; i < SIZE; i++) {
                if (eqset.get(i)) {
                    if(samenulls) {
                        arr2[i] = arr1[i] = null;
                        samenulls = false;
                    } else {
                        arr2[i] = arr1[i] = new IdentityInt(i);
                    }
                } else {
                    switch (distinctnulls) {
                        case 0:
                            arr1[i] = null;
                            arr2[i] = new IdentityInt(2 * i + 1);
                            distinctnulls = 1;
                            break;
                        case 1:
                            arr1[i] = new IdentityInt(2 * i);
                            arr2[i] = null;
                            distinctnulls  = 2;
                            break;
                        default:
                            arr1[i] = new IdentityInt(2 * i);
                            arr2[i] = new IdentityInt(2 * i + 1);
                            break;
                    }
                }
            }

        }
    }

    @State(Scope.Thread)
    public abstract static class ObjState {
        Object[] arr1, arr2;

        public void setup(int eq) {
            arr1 = new Object[SIZE];
            arr2 = new Object[SIZE];
            populate(arr1, arr2, eq);
        }
    }

    @State(Scope.Thread)
    public abstract static class RefState {
        IdentityInt[] arr1, arr2;

        public void setup(int eq) {
            arr1 = new IdentityInt[SIZE];
            arr2 = new IdentityInt[SIZE];
            populate(arr1, arr2, eq);
        }
    }

    public static class ObjState00 extends ObjState {
        @Setup
        public void setup() {
            setup(0);
        }
    }

    public static class ObjState25 extends ObjState {
        @Setup
        public void setup() {
            setup(25);
        }
    }

    public static class ObjState50 extends ObjState {
        @Setup
        public void setup() {
            setup(50);
        }
    }

    public static class ObjState75 extends ObjState {
        @Setup
        public void setup() {
            setup(75);
        }
    }

    public static class ObjState100 extends ObjState {
        @Setup
        public void setup() {
            setup(100);
        }
    }

    public static class RefState00 extends RefState {
        @Setup
        public void setup() {
            setup(0);
        }
    }

    public static class RefState25 extends RefState {
        @Setup
        public void setup() {
            setup(25);
        }
    }

    public static class RefState50 extends RefState {
        @Setup
        public void setup() {
            setup(50);
        }
    }

    public static class RefState75 extends RefState {
        @Setup
        public void setup() {
            setup(75);
        }
    }

    public static class RefState100 extends RefState {
        @Setup
        public void setup() {
            setup(100);
        }
    }

}
