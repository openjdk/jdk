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

public class Primitive extends MatrixBase {

    public static class PrimState extends SizeState {
        double[][] A;
        double[][] B;

        @Setup
        public void setup() {
            A = populate(new double[size][size * 2]);
            B = populate(new double[size][size * 2]);
        }

        private double[][] populate(double[][] m) {
            int size = m.length;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    m[i][j * 2] = ThreadLocalRandom.current().nextDouble();
                    m[i][j * 2 + 1] = ThreadLocalRandom.current().nextDouble();
                }
            }
            return m;
        }

    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public double[][] multiply(PrimState st) {
        double[][] A = st.A;
        double[][] B = st.B;
        int size = st.size;
        double[][] R = new double[size][size * 2];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double s_re = 0;
                double s_im = 0;
                for (int k = 0; k < size; k++) {
                    double are = A[i][k * 2];
                    double aim = A[i][k * 2 + 1];
                    double bre = B[k][j * 2];
                    double bim = B[k][j * 2 + 1];
                    s_re += are * bre - aim * bim;
                    s_im += are * bim + bre * aim;
                }
                R[i][j * 2] = s_re;
                R[i][j * 2 + 1] = s_im;
            }
        }
        return R;
    }

//    @Benchmark
//    public double[][] multiplyCacheFriendly() {
//        int size = A.length;
//        double[][] R = new double[size][size * 2];
//        for (int i = 0; i < size; i++) {
//            for (int k = 0; k < size; k++) {
//                double are = A[i][k * 2 + 0];
//                double aim = A[i][k * 2 + 1];
//                for (int j = 0; j < size; j++) {
//                    double bre = B[k][j * 2 + 0];
//                    double bim = B[k][j * 2 + 1];
//                    R[i][j * 2 + 0] += are * bre - aim * bim;
//                    R[i][j * 2 + 1] += are * bim + bre * aim;
//                }
//            }
//        }
//        return R;
//    }


}
