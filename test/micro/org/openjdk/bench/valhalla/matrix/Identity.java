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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Setup;

import java.util.concurrent.ThreadLocalRandom;


public class Identity extends MatrixBase {

    public static IdentityComplex[][] create_matrix_ref(int size) {
        return new IdentityComplex[size][size];
    }

    public static Complex[][] create_matrix_int(int size) {
        return new Complex[size][size];
    }

    public static abstract class RefState extends SizeState {
        IdentityComplex[][] A;
        IdentityComplex[][] B;

        static void populate(IdentityComplex[][] m) {
            int size = m.length;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    m[i][j] = new IdentityComplex(ThreadLocalRandom.current().nextDouble(), ThreadLocalRandom.current().nextDouble());
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
                    m[i][j] = new IdentityComplex(ThreadLocalRandom.current().nextDouble(), ThreadLocalRandom.current().nextDouble());
                }
            }
        }
    }

    public static class Ref_as_Ref extends RefState {
        @Setup
        public void setup() {
            populate(A = create_matrix_ref(size));
            populate(B = create_matrix_ref(size));
        }
    }

    public static class Ref_as_Int extends IntState {
        @Setup
        public void setup() {
            populate(A = create_matrix_ref(size));
            populate(B = create_matrix_ref(size));
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
    public IdentityComplex[][] mult_ref_as_ref(Ref_as_Ref st) {
        IdentityComplex[][] A = st.A;
        IdentityComplex[][] B = st.B;
        int size = st.size;
        IdentityComplex[][] R = create_matrix_ref(size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                IdentityComplex s = new IdentityComplex(0,0);
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
    public Complex[][] mult_ref_as_int(Ref_as_Int st) {
        Complex[][] A = st.A;
        Complex[][] B = st.B;
        int size = st.size;
        Complex[][] R = create_matrix_ref(size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Complex s = new IdentityComplex(0,0);
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
                Complex s = new IdentityComplex(0,0);
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

    public static class IdentityComplex implements Complex {

        private final double re;
        private final double im;

        public IdentityComplex(double re, double im) {
            this.re =  re;
            this.im =  im;
        }

        @Override
        public double re() { return re; }

        @Override
        public double im() { return im; }

        @Override
        public IdentityComplex add(Complex that) {
            return new IdentityComplex(this.re + that.re(), this.im + that.im());
        }

        public IdentityComplex add(IdentityComplex that) {
            return new IdentityComplex(this.re + that.re, this.im + that.im);
        }

        @Override
        public IdentityComplex mul(Complex that) {
            return new IdentityComplex(this.re * that.re() - this.im * that.im(),
                    this.re * that.im() + this.im * that.re());
        }

        public IdentityComplex mul(IdentityComplex that) {
            return new IdentityComplex(this.re * that.re - this.im * that.im,
                    this.re * that.im + this.im * that.re);
        }

    }

}
