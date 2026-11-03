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
package org.openjdk.bench.valhalla.matrix;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Setup;

import java.util.concurrent.ThreadLocalRandom;


@Fork(value = 3, jvmArgsAppend = {"--enable-preview", "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED"})
public class ValueNullFreeNonAtomic extends MatrixBase {

    public static ValueComplex[][] create_matrix_val(int size) {
            ValueComplex[][] x;
            x = new ValueComplex[size][];
            for (int i = 0; i < size; i++) {
                x[i] = (ValueComplex[]) ValueClass.newNullRestrictedNonAtomicArray(ValueComplex.class, size, new ValueComplex(0, 0));
            }
            return x;
    }

    public static Complex[][] create_matrix_int(int size) {
        return new Complex[size][size];
    }

    public static abstract class ValState extends SizeState {
        ValueComplex[][] A;
        ValueComplex[][] B;

        static void populate(ValueComplex[][] m) {
            int size = m.length;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    m[i][j] = new ValueComplex(ThreadLocalRandom.current().nextDouble(), ThreadLocalRandom.current().nextDouble());
                }
            }
        }
    }

    public static abstract class IntState extends SizeState {
        Complex[][] A;
        Complex[][] B;

        static void populate(Complex[][] m) {
            int size = m.length;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    m[i][j] = new ValueComplex(ThreadLocalRandom.current().nextDouble(), ThreadLocalRandom.current().nextDouble());
                }
            }
        }
    }

    public static class Val_as_Val extends ValState {
        @Setup
        public void setup() {
            populate(A = create_matrix_val(size));
            populate(B = create_matrix_val(size));
        }
    }

    public static class Val_as_Int extends IntState {
        @Setup
        public void setup() {
            populate(A = create_matrix_val(size));
            populate(B = create_matrix_val(size));
        }
    }

    public static class Int_as_Int extends IntState {
        @Setup
        public void setup() {
            populate(A = create_matrix_int(size));
            populate(B = create_matrix_int(size));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public ValueComplex[][] mult_val_as_val(Val_as_Val st) {
        ValueComplex[][] A = st.A;
        ValueComplex[][] B = st.B;
        int size = st.size;
        ValueComplex[][] R = create_matrix_val(size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                ValueComplex s = new ValueComplex(0,0);
                for (int k = 0; k < size; k++) {
                    s = s.add(A[i][k].mul(B[k][j]));
                }
                R[i][j] = s;
            }
        }
        return R;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Complex[][] mult_val_as_int(Val_as_Int st) {
        Complex[][] A = st.A;
        Complex[][] B = st.B;
        int size = st.size;
        Complex[][] R = create_matrix_val(size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Complex s = new ValueComplex(0,0);
                for (int k = 0; k < size; k++) {
                    s = s.add(A[i][k].mul(B[k][j]));
                }
                R[i][j] = s;
            }
        }
        return R;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Complex[][] mult_int_as_int(Int_as_Int st) {
        Complex[][] A = st.A;
        Complex[][] B = st.B;
        int size = st.size;
        Complex[][] R = create_matrix_int(size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Complex s = new ValueComplex(0,0);
                for (int k = 0; k < size; k++) {
                    s = s.add(A[i][k].mul(B[k][j]));
                }
                R[i][j] = s;
            }
        }
        return R;
    }

    public interface Complex {
        double re();
        double im();
        Complex add(Complex that);
        Complex mul(Complex that);
    }

    @LooselyConsistentValue
    public static value class ValueComplex implements Complex {

        private final double re;
        private final double im;

        public ValueComplex(double re, double im) {
            this.re =  re;
            this.im =  im;
        }

        @Override
        public double re() { return re; }

        @Override
        public double im() { return im; }

        @Override
        public ValueComplex add(Complex that) {
            return new ValueComplex(this.re + that.re(), this.im + that.im());
        }

        public ValueComplex add(ValueComplex that) {
            return new ValueComplex(this.re + that.re, this.im + that.im);
        }

        @Override
        public ValueComplex mul(Complex that) {
            return new ValueComplex(this.re * that.re() - this.im * that.im(),
                    this.re * that.im() + this.im * that.re());
        }

        public ValueComplex mul(ValueComplex that) {
            return new ValueComplex(this.re * that.re - this.im * that.im,
                    this.re * that.im + this.im * that.re);
        }

    }

}
