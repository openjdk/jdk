/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.vectorapi;

import jdk.incubator.vector.*;

/*
 * @test
 * @bug 8381579
 * @summary Verify shift operations on floating-point vectors throw UnsupportedOperationException.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *      compiler.vectorapi.TestShiftOpOnFloatingVector
 */

public class TestShiftOpOnFloatingVector {

    static final int ITERATIONS = 4_000;
    static final int ARRAY_LEN = 100;

    static void testROL() {
        double[] arr = new double[ARRAY_LEN];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i * 1.5 + 7.89;
        }
        for (int i = 0; i < arr.length; i += DoubleVector.SPECIES_PREFERRED.length()) {
            DoubleVector v = DoubleVector.fromArray(DoubleVector.SPECIES_PREFERRED, arr, i);
            DoubleVector r = v.lanewise(VectorOperators.ROL, 7);
            r.intoArray(arr, i);
        }
    }

    static void testROR() {
        double[] arr = new double[ARRAY_LEN];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i * 1.5 + 7.89;
        }
        for (int i = 0; i < arr.length; i += DoubleVector.SPECIES_PREFERRED.length()) {
            DoubleVector v = DoubleVector.fromArray(DoubleVector.SPECIES_PREFERRED, arr, i);
            DoubleVector r = v.lanewise(VectorOperators.ROR, 3);
            r.intoArray(arr, i);
        }
    }

    static void testLSHL() {
        float[] arr = new float[ARRAY_LEN];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i * 0.5f + 1.0f;
        }
        for (int i = 0; i < arr.length; i += FloatVector.SPECIES_PREFERRED.length()) {
            FloatVector v = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, arr, i);
            FloatVector r = v.lanewise(VectorOperators.LSHL, 2);
            r.intoArray(arr, i);
        }
    }

    static void testLSHR() {
        float[] arr = new float[ARRAY_LEN];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i * 0.3f + 2.0f;
        }
        for (int i = 0; i < arr.length; i += FloatVector.SPECIES_PREFERRED.length()) {
            FloatVector v = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, arr, i);
            FloatVector r = v.lanewise(VectorOperators.LSHR, 5);
            r.intoArray(arr, i);
        }
    }

    static void testASHR() {
        double[] arr = new double[ARRAY_LEN];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i * 2.0 + 3.0;
        }
        for (int i = 0; i < arr.length; i += DoubleVector.SPECIES_PREFERRED.length()) {
            DoubleVector v = DoubleVector.fromArray(DoubleVector.SPECIES_PREFERRED, arr, i);
            DoubleVector r = v.lanewise(VectorOperators.ASHR, 4);
            r.intoArray(arr, i);
        }
    }

    static void runTest(Runnable test, String name) {
        for (int i = 0; i < ITERATIONS; i++) {
            try {
                test.run();
                throw new AssertionError(name + ": Expected UnsupportedOperationException was not thrown");
            } catch (UnsupportedOperationException e) {
                // expected
            }
        }
    }

    public static void main(String[] args) {
        runTest(TestShiftOpOnFloatingVector::testROL,  "ROL");
        runTest(TestShiftOpOnFloatingVector::testROR,  "ROR");
        runTest(TestShiftOpOnFloatingVector::testLSHL, "LSHL");
        runTest(TestShiftOpOnFloatingVector::testLSHR, "LSHR");
        runTest(TestShiftOpOnFloatingVector::testASHR, "ASHR");
    }
}
