/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
*           (os.simpleArch == "aarch64" & vm.cpu.features ~= ".*sve.*" & (vm.opt.UseSVE == "null" | vm.opt.UseSVE > 0))
* @library /test/lib /
* @run driver compiler.vectorization.TestNumberOfContinuousZeros
*/

package compiler.vectorization;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Asserts;

public class TestNumberOfContinuousZeros {
    private long[] input;
    private int[] output;
    private static final int LEN = 1024;
    private Random rng;

    public static void main(String args[]) {
        TestFramework.run();
    }

    public TestNumberOfContinuousZeros() {
        input = new long[LEN];
        output = new int[LEN];
        rng = new Random(42);
        for (int i = 0; i < LEN; ++i) {
            input[i] = rng.nextLong();
        }
    }

    @Test
    @IR(counts = {IRNode.COUNTTRAILINGZEROS_VL, "> 0"})
    public void vectorizeNumberOfTrailingZeros() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = Long.numberOfTrailingZeros(input[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.COUNTLEADINGZEROS_VL, "> 0"})
    public void vectorizeNumberOfLeadingZeros() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = Long.numberOfLeadingZeros(input[i]);
        }
    }

    @Run(test = {"vectorizeNumberOfTrailingZeros", "vectorizeNumberOfLeadingZeros"})
    public void checkResult() {
        vectorizeNumberOfTrailingZeros();
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(output[i], Long.numberOfTrailingZeros(input[i]));
        }
        vectorizeNumberOfLeadingZeros();
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(output[i], Long.numberOfLeadingZeros(input[i]));
        }
    }
}

