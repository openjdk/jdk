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

import compiler.lib.ir_framework.*;

import java.util.Arrays;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8378250
 * @summary Verify correctness of byte vector MUL reduction across all species.
 *          A register aliasing bug in mulreduce32B caused the upper half of
 *          sign-extended data to overwrite the source, producing wrong results
 *          when most lanes are 1 and a single lane differs.
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @run driver ${test.main.class}
 */
public class TestMultiplyReductionByte {

    static byte[] input = new byte[64];

    static int pos = Utils.getRandomInstance().nextInt(input.length);

    static {
        Arrays.fill(input, (byte) 1);
        input[pos] = -3;
    }

    @Test
    @IR(counts = {IRNode.MUL_REDUCTION_VI, ">=1"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"MaxVectorSize", ">=8"})
    static byte testMulReduce64() {
        return ByteVector.fromArray(ByteVector.SPECIES_64, input, 0)
                         .reduceLanes(VectorOperators.MUL);
    }

    @Run(test = "testMulReduce64")
    static void runMulReduce64() {
        input[pos] = 1;
        pos = (pos + 1) % ByteVector.SPECIES_64.length();
        input[pos] = -3;
        byte result = testMulReduce64();
        Asserts.assertEquals((byte) -3, result, "MUL reduction (64-bit), pos=" + pos);
    }

    @Test
    @IR(counts = {IRNode.MUL_REDUCTION_VI, ">=1"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"MaxVectorSize", ">=16"})
    static byte testMulReduce128() {
        return ByteVector.fromArray(ByteVector.SPECIES_128, input, 0)
                         .reduceLanes(VectorOperators.MUL);
    }

    @Run(test = "testMulReduce128")
    static void runMulReduce128() {
        input[pos] = 1;
        pos = (pos + 1) % ByteVector.SPECIES_128.length();
        input[pos] = -3;
        byte result = testMulReduce128();
        Asserts.assertEquals((byte) -3, result, "MUL reduction (128-bit), pos=" + pos);
    }

    @Test
    @IR(counts = {IRNode.MUL_REDUCTION_VI, ">=1"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"},
        applyIf = {"MaxVectorSize", ">=32"})
    static byte testMulReduce256() {
        return ByteVector.fromArray(ByteVector.SPECIES_256, input, 0)
                         .reduceLanes(VectorOperators.MUL);
    }

    @Run(test = "testMulReduce256")
    static void runMulReduce256() {
        input[pos] = 1;
        pos = (pos + 1) % ByteVector.SPECIES_256.length();
        input[pos] = -3;
        byte result = testMulReduce256();
        Asserts.assertEquals((byte) -3, result, "MUL reduction (256-bit), pos=" + pos);
    }

    @Test
    @IR(counts = {IRNode.MUL_REDUCTION_VI, ">=1"},
        applyIfCPUFeatureOr = {"avx512f", "true", "asimd", "true"},
        applyIf = {"MaxVectorSize", ">=64"})
    static byte testMulReduce512() {
        return ByteVector.fromArray(ByteVector.SPECIES_512, input, 0)
                         .reduceLanes(VectorOperators.MUL);
    }

    @Run(test = "testMulReduce512")
    static void runMulReduce512() {
        input[pos] = 1;
        pos = (pos + 1) % ByteVector.SPECIES_512.length();
        input[pos] = -3;
        byte result = testMulReduce512();
        Asserts.assertEquals((byte) -3, result, "MUL reduction (512-bit), pos=" + pos);
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
