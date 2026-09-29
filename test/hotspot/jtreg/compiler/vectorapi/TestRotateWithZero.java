/*
 * Copyright (c) 2025, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

import compiler.lib.generators.*;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8351627
 * @summary C2 AArch64 ROR/ROL: assert((1 << ((T>>1)+3)) > shift) failed: Invalid Shift value
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run main/othervm -XX:-TieredCompilation compiler.vectorapi.TestRotateWithZero
 */
public class TestRotateWithZero {
    private static final int INVOCATIONS = 10000;
    private static final int LENGTH = 2048;
    private static final Generators random = Generators.G;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_PREFERRED;

    private static int[] arr1;
    private static int[] arr2;
    private static int[] res;
    private static short[] sarr1;
    private static short[] sarr2;
    private static short[] sres;

    static {
        arr1 = new int[LENGTH];
        arr2 = new int[LENGTH];
        res = new int[LENGTH];
        sarr1 = new short[LENGTH];
        sarr2 = new short[LENGTH];
        sres = new short[LENGTH];

        random.fill(random.ints(), arr1);
        Generator<Integer> shortGen = random.uniformInts(Short.MIN_VALUE, Short.MAX_VALUE);
        for (int i = 0; i < LENGTH; i++) {
            sarr1[i] = shortGen.next().shortValue();
            sarr2[i] = (short)0;
            arr2[i] = 0;
        }
    }

    private static void rotateRightWithZero() {
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector v = IntVector.fromArray(I_SPECIES, arr1, i);
            v.lanewise(VectorOperators.ROR, 0).intoArray(res, i);
        }
    }

    private static void rotateLeftWithZero() {
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector v = IntVector.fromArray(I_SPECIES, arr1, i);
            v.lanewise(VectorOperators.ROL, 0).intoArray(res, i);
        }
    }

    private static void rotateRightWithZeroConst() {
        IntVector vzero = IntVector.zero(I_SPECIES);
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector v = IntVector.fromArray(I_SPECIES, arr1, i);
            v.lanewise(VectorOperators.ROR, vzero).intoArray(res, i);
        }
    }

    private static void rotateLeftWithZeroConst() {
        IntVector vzero = IntVector.zero(I_SPECIES);
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector v = IntVector.fromArray(I_SPECIES, arr1, i);
            v.lanewise(VectorOperators.ROL, vzero).intoArray(res, i);
        }
    }

    private static void rotateRightWithZeroArr() {
        IntVector vzero = IntVector.fromArray(I_SPECIES, arr2, 0);
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector v = IntVector.fromArray(I_SPECIES, arr1, i);
            v.lanewise(VectorOperators.ROR, vzero).intoArray(res, i);
        }
    }

    private static void rotateLeftWithZeroArr() {
        IntVector vzero = IntVector.fromArray(I_SPECIES, arr2, 0);
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector v = IntVector.fromArray(I_SPECIES, arr1, i);
            v.lanewise(VectorOperators.ROL, vzero).intoArray(res, i);
        }
    }

    private static void rotateRightWithZeroVar() {
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector v = IntVector.fromArray(I_SPECIES, arr1, i);
            v.lanewise(VectorOperators.ROR, arr2[i]).intoArray(res, i);
        }
    }

    private static void rotateLeftWithZeroVar() {
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector v = IntVector.fromArray(I_SPECIES, arr1, i);
            v.lanewise(VectorOperators.ROL, arr2[i]).intoArray(res, i);
        }
    }

    private static void rotateRightWithZero_subword() {
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector v = ShortVector.fromArray(S_SPECIES, sarr1, i);
            v.lanewise(VectorOperators.ROR, 0).intoArray(sres, i);
        }
    }

    private static void rotateLeftWithZero_subword() {
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector v = ShortVector.fromArray(S_SPECIES, sarr1, i);
            v.lanewise(VectorOperators.ROL, 0).intoArray(sres, i);
        }
    }

    private static void rotateRightWithZeroConst_subword() {
        ShortVector vzero = ShortVector.zero(S_SPECIES);
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector v = ShortVector.fromArray(S_SPECIES, sarr1, i);
            v.lanewise(VectorOperators.ROR, vzero).intoArray(sres, i);
        }
    }

    private static void rotateLeftWithZeroConst_subword() {
        ShortVector vzero = ShortVector.zero(S_SPECIES);
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector v = ShortVector.fromArray(S_SPECIES, sarr1, i);
            v.lanewise(VectorOperators.ROL, vzero).intoArray(sres, i);
        }
    }

    private static void rotateRightWithZeroArr_subword() {
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector v1 = ShortVector.fromArray(S_SPECIES, sarr1, i);
            ShortVector v2 = ShortVector.fromArray(S_SPECIES, sarr2, i);
            v1.lanewise(VectorOperators.ROR, v2).intoArray(sres, i);
        }
    }

    private static void rotateLeftWithZeroArr_subword() {
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector v1 = ShortVector.fromArray(S_SPECIES, sarr1, i);
            ShortVector v2 = ShortVector.fromArray(S_SPECIES, sarr2, i);
            v1.lanewise(VectorOperators.ROL, v2).intoArray(sres, i);
        }
    }

    private static void rotateRightWithZeroVar_subword() {
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector v = ShortVector.fromArray(S_SPECIES, sarr1, i);
            v.lanewise(VectorOperators.ROR, sarr2[i]).intoArray(sres, i);
        }
    }

    private static void rotateLeftWithZeroVar_subword() {
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector v = ShortVector.fromArray(S_SPECIES, sarr1, i);
            v.lanewise(VectorOperators.ROL, sarr2[i]).intoArray(sres, i);
        }
    }

    private static void checkResults(int[] ref, int[] res) {
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(ref[i], res[i]);
        }
    }

    private static void checkResults(short[] ref, short[] res) {
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(ref[i], res[i]);
        }
    }

    private static void test() {
        // Test rotate with a immediate 0
        for (int i = 0; i < INVOCATIONS; i++) {
            rotateRightWithZero();
        }
        checkResults(arr1, res);

        for (int i = 0; i < INVOCATIONS; i++) {
            rotateLeftWithZero();
        }
        checkResults(arr1, res);

        // Test rotate with a constant vector with all zeros
        for (int i = 0; i < INVOCATIONS; i++) {
            rotateRightWithZeroConst();
        }
        checkResults(arr1, res);

        for (int i = 0; i < INVOCATIONS; i++) {
            rotateLeftWithZeroConst();
        }
        checkResults(arr1, res);

        // Test rotate with a vector loaded from the memory
        // filled with zeros
        for (int i = 0; i < INVOCATIONS; i++) {
            rotateRightWithZeroArr();
        }
        checkResults(arr1, res);

        for (int i = 0; i < INVOCATIONS; i++) {
            rotateLeftWithZeroArr();
        }
        checkResults(arr1, res);

        // Test rotate with a variable assigned with zero.
        for (int i = 0; i < INVOCATIONS; i++) {
            rotateRightWithZeroVar();
        }
        checkResults(arr1, res);

        for (int i = 0; i < INVOCATIONS; i++) {
            rotateLeftWithZeroVar();
        }
        checkResults(arr1, res);
    }

    private static void test_subword() {
        // Test rotate with a immediate 0
        for (int i = 0; i < INVOCATIONS; i++) {
            rotateRightWithZero_subword();
        }
        checkResults(sarr1, sres);

        for (int i = 0; i < INVOCATIONS; i++) {
            rotateLeftWithZero_subword();
        }
        checkResults(sarr1, sres);

        // Test rotate with a constant vector with all zeros
        for (int i = 0; i < INVOCATIONS; i++) {
            rotateRightWithZeroConst_subword();
        }
        checkResults(sarr1, sres);

        for (int i = 0; i < INVOCATIONS; i++) {
            rotateLeftWithZeroConst_subword();
        }
        checkResults(sarr1, sres);

        // Test rotate with a vector loaded from the memory
        // filled with zeros
        for (int i = 0; i < INVOCATIONS; i++) {
            rotateRightWithZeroArr_subword();
        }
        checkResults(sarr1, sres);

        for (int i = 0; i < INVOCATIONS; i++) {
            rotateLeftWithZeroArr_subword();
        }
        checkResults(sarr1, sres);

        // Test rotate with a variable assigned with zero.
        for (int i = 0; i < INVOCATIONS; i++) {
            rotateRightWithZeroVar_subword();
        }
        checkResults(sarr1, sres);

        for (int i = 0; i < INVOCATIONS; i++) {
            rotateLeftWithZeroVar_subword();
        }
        checkResults(sarr1, sres);
    }

    public static void main(String[] args) {
        test();
        test_subword();
    }
}
