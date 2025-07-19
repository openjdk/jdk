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
import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8351623
 * @summary VectorAPI: Add SVE implementation for subword gather load operation
 * @key randomness
 * @library /test/lib /
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorGatherSubwordTest
 */
public class VectorGatherSubwordTest {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_PREFERRED;

    private static int LENGTH = 128;
    private static final Generators random = Generators.G;

    private static byte[] ba;
    private static byte[] br;
    private static short[] sa;
    private static short[] sr;
    private static boolean[] m;
    private static int[][] indexes;

    static {
        ba = new byte[LENGTH];
        br = new byte[LENGTH];
        sa = new short[LENGTH];
        sr = new short[LENGTH];
        m = new boolean[LENGTH];
        indexes = new int[2][];

        Generator<Integer> byteGen = random.uniformInts(Byte.MIN_VALUE, Byte.MAX_VALUE);
        Generator<Integer> shortGen = random.uniformInts(Short.MIN_VALUE, Short.MAX_VALUE);
        for (int i = 0; i < LENGTH; i++) {
            ba[i] = byteGen.next().byteValue();
            sa[i] = shortGen.next().shortValue();
            m[i] = i % 2 == 0;
        }

        int[] nums = {B_SPECIES.length(), S_SPECIES.length()};
        for (int i = 0; i < 2; i++) {
            indexes[i] = new int[nums[i]];
            random.fill(random.uniformInts(0, nums[i] - 1), indexes[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, " >0 "}, applyIfCPUFeature = {"sve", "true"})
    public void testLoadGatherByte() {
        for (int i = 0; i < LENGTH; i += B_SPECIES.length()) {
            ByteVector.fromArray(B_SPECIES, ba, i, indexes[0], 0)
                      .intoArray(br, i);
        }
    }

    @Check(test = "testLoadGatherByte")
    public void verifyLoadGatherByte() {
        for (int i = 0; i < LENGTH; i += B_SPECIES.length()) {
            for (int j = 0; j < B_SPECIES.length(); j++) {
                Asserts.assertEquals(ba[i + indexes[0][j]], br[i + j]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, " >0 "}, applyIfCPUFeature = {"sve", "true"})
    public void testLoadGatherShort() {
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector.fromArray(S_SPECIES, sa, i, indexes[1], 0)
                       .intoArray(sr, i);
        }
    }

    @Check(test = "testLoadGatherShort")
    public void verifyLoadGatherShort() {
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            for (int j = 0; j < S_SPECIES.length(); j++) {
                Asserts.assertEquals(sa[i + indexes[1][j]], sr[i + j]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, " >0 "}, applyIfCPUFeature = {"sve", "true"})
    public void testLoadGatherMaskedByte() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += B_SPECIES.length()) {
            ByteVector.fromArray(B_SPECIES, ba, i, indexes[0], 0, mask)
                      .intoArray(br, i);
        }
    }

    @Check(test = "testLoadGatherMaskedByte")
    public void verifyLoadGatherMaskedByte() {
        for (int i = 0; i < LENGTH; i += B_SPECIES.length()) {
            for (int j = 0; j < B_SPECIES.length(); j++) {
                Asserts.assertEquals(m[j] ? ba[i + indexes[0][j]] : 0, br[i + j]);
            }
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, " >0 "}, applyIfCPUFeature = {"sve", "true"})
    public void testLoadGatherMaskedShort() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector.fromArray(S_SPECIES, sa, i, indexes[1], 0, mask)
                       .intoArray(sr, i);
        }
    }

    @Check(test = "testLoadGatherMaskedShort")
    public void verifyLoadGatherMaskedShort() {
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            for (int j = 0; j < S_SPECIES.length(); j++) {
                Asserts.assertEquals(m[j] ? sa[i + indexes[1][j]] : 0, sr[i + j]);
            }
        }
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
