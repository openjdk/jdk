/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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
* @key randomness
* @summary Test vectorization of numberOfTrailingZeros/numberOfLeadingZeros for Long
* @requires vm.compiler2.enabled
* @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*avx2.*") |
*           (os.simpleArch == "aarch64" & vm.cpu.features ~= ".*sve.*") |
*           (os.simpleArch == "riscv64" & vm.cpu.features ~= ".*zvbb.*")
* @library /test/lib /
* @run driver compiler.vectorization.TestNumberOfContinuousZeros
*/

package compiler.vectorization;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Asserts;

public class TestNumberOfContinuousZeros {
    private long[] inputLong;
    private int[] outputLong;
    private int[] inputInt;
    private int[] outputInt;
    private static final int LEN = 1024;
    private Random rng;

    public static void main(String args[]) {
        TestFramework.run();
    }

    public TestNumberOfContinuousZeros() {
        inputLong = new long[LEN];
        outputLong = new int[LEN];
        inputInt = new int[LEN];
        outputInt = new int[LEN];
        rng = new Random(42);
        for (int i = 0; i < LEN; ++i) {
            inputLong[i] = rng.nextLong();
            inputInt[i] = rng.nextInt();
        }
    }

    @Test
    @IR(counts = {IRNode.COUNT_TRAILING_ZEROS_VL, "> 0"})
    public void vectorizeNumberOfTrailingZerosLong() {
        for (int i = 0; i < LEN; ++i) {
            outputLong[i] = Long.numberOfTrailingZeros(inputLong[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.COUNT_LEADING_ZEROS_VL, "> 0"})
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
}

