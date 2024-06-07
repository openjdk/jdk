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

package compiler.vectorapi;

import compiler.lib.ir_framework.*;

import java.util.Random;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8290485
 * @key randomness
 * @library /test/lib /
 * @summary [vectorapi] REVERSE_BYTES for byte type should not emit any instructions
 * @requires vm.compiler2.enabled
 * @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*avx2.*") | os.arch == "aarch64" |
 *           (os.arch == "riscv64" & vm.cpu.features ~= ".*zvbb.*")
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorReverseBytesTest
 */

public class VectorReverseBytesTest {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;

    private static int LENGTH = 1024;
    private static final Random RD = Utils.getRandomInstance();

    private static byte[] input;
    private static byte[] output;
    private static boolean[] m;

    static {
        input = new byte[LENGTH];
        output = new byte[LENGTH];
        m = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            input[i] = (byte) RD.nextInt(25);
            m[i] = RD.nextBoolean();
        }
    }

    @Test
    @IR(failOn = IRNode.REVERSE_BYTES_VB)
    public static void testReverseBytesV() {
        for (int i = 0; i < LENGTH; i += B_SPECIES.length()) {
            ByteVector v = ByteVector.fromArray(B_SPECIES, input, i);
            v.lanewise(VectorOperators.REVERSE_BYTES).intoArray(output, i);
        }

        // Verify results
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(input[i], output[i]);
        }
    }

    @Test
    @IR(failOn = IRNode.REVERSE_BYTES_VB)
    @IR(failOn = IRNode.VECTOR_BLEND_B)
    public static void testReverseBytesVMasked() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += B_SPECIES.length()) {
            ByteVector v = ByteVector.fromArray(B_SPECIES, input, i);
            v.lanewise(VectorOperators.REVERSE_BYTES, mask).intoArray(output, i);
        }

        // Verify results
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(input[i], output[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
