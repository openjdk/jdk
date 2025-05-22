/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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

/**
* @test
* @bug 8297172 8331993 8349637
* @key randomness
* @summary Test vectorization of numberOfTrailingZeros/numberOfLeadingZeros for Long
* @requires vm.compiler2.enabled
* @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*avx2.*") |
*           (os.simpleArch == "aarch64" & vm.cpu.features ~= ".*sve.*") |
*           (os.simpleArch == "riscv64" & vm.cpu.features ~= ".*zvbb.*") |
*           ((os.arch == "ppc64" | os.arch == "ppc64le") & vm.cpu.features ~= ".*darn.*")
* @library /test/lib /
* @modules jdk.incubator.vector
* @run driver compiler.vectorization.TestNumberOfContinuousZeros
*/

package compiler.vectorization;

import jdk.incubator.vector.*;
import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class TestNumberOfContinuousZeros {
    private static final int[] SPECIAL = { 0x01FFFFFF, 0x03FFFFFE, 0x07FFFFFC, 0x0FFFFFF8, 0x1FFFFFF0, 0x3FFFFFE0, 0xFFFFFFFF };
    private long[] inputLong;
    private int[] outputLong;
    private int[] inputInt;
    private int[] outputInt;
    private static final int LEN = 1024;
    private Random rng;

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    public TestNumberOfContinuousZeros() {
        inputLong = new long[LEN];
        outputLong = new int[LEN];
        inputInt = new int[LEN];
        outputInt = new int[LEN];
        rng = Utils.getRandomInstance();
        for (int i = 0; i < LEN; ++i) {
            inputLong[i] = rng.nextLong();
            inputInt[i] = rng.nextInt();
        }
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"},
        counts = {IRNode.COUNT_TRAILING_ZEROS_VL, "> 0"})
    public void vectorizeNumberOfTrailingZerosLong() {
        for (int i = 0; i < LEN; ++i) {
            outputLong[i] = Long.numberOfTrailingZeros(inputLong[i]);
        }
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"},
        counts = {IRNode.COUNT_LEADING_ZEROS_VL, "> 0"})
    public void vectorizeNumberOfLeadingZerosLong() {
        for (int i = 0; i < LEN; ++i) {
            outputLong[i] = Long.numberOfLeadingZeros(inputLong[i]);
        }
    }

    @Run(test = {"vectorizeNumberOfTrailingZerosLong", "vectorizeNumberOfLeadingZerosLong"})
    public void checkResultLong() {
        vectorizeNumberOfTrailingZerosLong();
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(outputLong[i], Long.numberOfTrailingZeros(inputLong[i]));
        }
        vectorizeNumberOfLeadingZerosLong();
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(outputLong[i], Long.numberOfLeadingZeros(inputLong[i]));
        }
    }


    @Test
    @IR(counts = {IRNode.COUNT_TRAILING_ZEROS_VI, "> 0"})
    public void vectorizeNumberOfTrailingZerosInt() {
        for (int i = 0; i < LEN; ++i) {
            outputInt[i] = Integer.numberOfTrailingZeros(inputInt[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.COUNT_LEADING_ZEROS_VI, "> 0"})
    public void vectorizeNumberOfLeadingZerosInt() {
        for (int i = 0; i < LEN; ++i) {
            outputInt[i] = Integer.numberOfLeadingZeros(inputInt[i]);
        }
    }

    @Run(test = {"vectorizeNumberOfTrailingZerosInt", "vectorizeNumberOfLeadingZerosInt"})
    public void checkResultInt() {
        vectorizeNumberOfTrailingZerosInt();
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(outputInt[i], Integer.numberOfTrailingZeros(inputInt[i]));
        }
        vectorizeNumberOfLeadingZerosInt();
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(outputInt[i], Integer.numberOfLeadingZeros(inputInt[i]));
        }
    }

    @Setup
    static Object[] setupSpecialIntArray() {
        int[] res = new int[LEN];

        for (int i = 0; i < LEN; i++) {
            res[i] = SPECIAL[i % SPECIAL.length];
        }

        return new Object[] { res };
    }

    @Test
    @IR(counts = {IRNode.COUNT_LEADING_ZEROS_VI, "> 0"})
    @Arguments(setup = "setupSpecialIntArray")
    public Object[] testSpecialIntLeadingZeros(int[] ints) {
        int[] res = new int[LEN];

        for (int i = 0; i < LEN; ++i) {
            res[i] = Integer.numberOfLeadingZeros(ints[i]);
        }

        return new Object[] { ints, res };
    }

    @Check(test = "testSpecialIntLeadingZeros")
    public void checkSpecialIntLeadingZeros(Object[] vals) {
        int[] in = (int[]) vals[0];
        int[] out = (int[]) vals[1];

        for (int i = 0; i < LEN; ++i) {
            int value = Integer.numberOfLeadingZeros(in[i]);

            if (out[i] != value) {
                throw new IllegalStateException("Expected lzcnt(" + in[i] + ") to be " + value + " but got " + out[i]);
            }
        }
    }

    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

    @Test
    @IR(counts = {IRNode.COUNT_LEADING_ZEROS_VI, "> 0"})
    @Arguments(setup = "setupSpecialIntArray")
    public Object[] checkSpecialIntLeadingZerosVector(int[] ints) {
        int[] res = new int[LEN];

        for (int i = 0; i < ints.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, ints, i);
            av.lanewise(VectorOperators.LEADING_ZEROS_COUNT).intoArray(res, i);
        }

        return new Object[] { ints, res };
    }

    @Check(test = "checkSpecialIntLeadingZerosVector")
    public void checkSpecialIntLeadingZerosVector(Object[] vals) {
        int[] ints = (int[]) vals[0];
        int[] res = (int[]) vals[1];

        // Verification

        int[] check = new int[LEN];

        for (int i = 0; i < ints.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, ints, i);
            av.lanewise(VectorOperators.LEADING_ZEROS_COUNT).intoArray(check, i);
        }

        for (int i = 0; i < LEN; i++) {
            if (res[i] != check[i]) {
                throw new IllegalStateException("Expected " + check[i] + " but got " + res[i]);
            }
        }
    }
}

