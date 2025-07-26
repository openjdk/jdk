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
 * @run driver compiler.vectorapi.VectorGatherSubwordTest MaxVectorSize_16
 * @run driver compiler.vectorapi.VectorGatherSubwordTest MaxVectorSize_32
 * @run driver compiler.vectorapi.VectorGatherSubwordTest MaxVectorSize_64
 * @run driver compiler.vectorapi.VectorGatherSubwordTest
 */
public class VectorGatherSubwordTest {
    private static final VectorSpecies<Byte> BSPEC_64 = ByteVector.SPECIES_64;
    private static final VectorSpecies<Byte> BSPEC_128 = ByteVector.SPECIES_128;
    private static final VectorSpecies<Byte> BSPEC_256 = ByteVector.SPECIES_256;
    private static final VectorSpecies<Byte> BSPEC_512 = ByteVector.SPECIES_512;
    private static final VectorSpecies<Short> SSPEC_64 = ShortVector.SPECIES_64;
    private static final VectorSpecies<Short> SSPEC_128 = ShortVector.SPECIES_128;
    private static final VectorSpecies<Short> SSPEC_256 = ShortVector.SPECIES_256;
    private static final VectorSpecies<Short> SSPEC_512 = ShortVector.SPECIES_512;

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
        indexes = new int[5][];

        Generator<Integer> byteGen = random.uniformInts(Byte.MIN_VALUE, Byte.MAX_VALUE);
        Generator<Integer> shortGen = random.uniformInts(Short.MIN_VALUE, Short.MAX_VALUE);
        for (int i = 0; i < LENGTH; i++) {
            ba[i] = byteGen.next().byteValue();
            sa[i] = shortGen.next().shortValue();
            m[i] = i % 2 == 0;
        }

        int[] nums = {4, 8, 16, 32, 64};
        for (int i = 0; i < 5; i++) {
            indexes[i] = new int[nums[i]];
            random.fill(random.uniformInts(0, nums[i] - 1), indexes[i]);
        }
    }

    // Tests for gather load of byte vector with different vector species.

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "2"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "64"})
    public void testLoadGatherByte64() {
        ByteVector.fromArray(BSPEC_64, ba, 0, indexes[1], 0)
                  .intoArray(br, 0);
    }

    @Check(test = "testLoadGatherByte64")
    public void verifyLoadGatherByte64() {
        for (int i = 0; i < BSPEC_64.length(); i++) {
            Asserts.assertEquals(ba[indexes[1][i]], br[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "4"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "2"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "64"})
    public void testLoadGatherByte128() {
        ByteVector.fromArray(BSPEC_128, ba, 0, indexes[2], 0)
                  .intoArray(br, 0);
    }

    @Check(test = "testLoadGatherByte128")
    public void verifyLoadGatherByte128() {
        for (int i = 0; i < BSPEC_128.length(); i++) {
            Asserts.assertEquals(ba[indexes[2][i]], br[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "4"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "2"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "64"})
    public void testLoadGatherByte256() {
        ByteVector.fromArray(BSPEC_256, ba, 0, indexes[3], 0)
                  .intoArray(br, 0);
    }

    @Check(test = "testLoadGatherByte256")
    public void verifyLoadGatherByte256() {
        for (int i = 0; i < BSPEC_256.length(); i++) {
            Asserts.assertEquals(ba[indexes[3][i]], br[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "4"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "64"})
    public void testLoadGatherByte512() {
        ByteVector.fromArray(BSPEC_512, ba, 0, indexes[4], 0)
                  .intoArray(br, 0);
    }

    @Check(test = "testLoadGatherByte512")
    public void verifyLoadGatherByte512() {
        for (int i = 0; i < BSPEC_512.length(); i++) {
            Asserts.assertEquals(ba[indexes[4][i]], br[i]);
        }
    }

    // Tests for gather load of short vector with different vector species.

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 32"})
    public void testLoadGatherShort64() {
        ShortVector.fromArray(SSPEC_64, sa, 0, indexes[0], 0)
                   .intoArray(sr, 0);
    }

    @Check(test = "testLoadGatherShort64")
    public void verifyLoadGatherShort64() {
        for (int i = 0; i < SSPEC_64.length(); i++) {
            Asserts.assertEquals(sa[indexes[0][i]], sr[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "2"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "64"})
    public void testLoadGatherShort128() {
        ShortVector.fromArray(SSPEC_128, sa, 0, indexes[1], 0)
                   .intoArray(sr, 0);
    }

    @Check(test = "testLoadGatherShort128")
    public void verifyLoadGatherShort128() {
        for (int i = 0; i < SSPEC_128.length(); i++) {
            Asserts.assertEquals(sa[indexes[1][i]], sr[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "2"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "64"})
    public void testLoadGatherShort256() {
        ShortVector.fromArray(SSPEC_256, sa, 0, indexes[2], 0)
                   .intoArray(sr, 0);
    }

    @Check(test = "testLoadGatherShort256")
    public void verifyLoadGatherShort256() {
        for (int i = 0; i < SSPEC_256.length(); i++) {
            Asserts.assertEquals(sa[indexes[2][i]], sr[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER, "2"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "64"})
    public void testLoadGatherShort512() {
        ShortVector.fromArray(SSPEC_512, sa, 0, indexes[3], 0)
                   .intoArray(sr, 0);
    }

    @Check(test = "testLoadGatherShort512")
    public void verifyLoadGatherShort512() {
        for (int i = 0; i < SSPEC_512.length(); i++) {
            Asserts.assertEquals(sa[indexes[3][i]], sr[i]);
        }
    }

    // Tests for masked gather load of byte vector with different vector species.

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "2"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 32"})
    public void testLoadGatherMaskedByte64() {
        VectorMask<Byte> mask = VectorMask.fromArray(BSPEC_64, m, 0);
        ByteVector.fromArray(BSPEC_64, ba, 0, indexes[1], 0, mask)
                  .intoArray(br, 0);
    }

    @Check(test = "testLoadGatherMaskedByte64")
    public void verifyLoadGatherMaskedByte64() {
        for (int i = 0; i < BSPEC_64.length(); i++) {
            Asserts.assertEquals(m[i] ? ba[indexes[1][i]] : 0, br[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "4"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "2"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "64"})
    public void testLoadGatherMaskedByte128() {
        VectorMask<Byte> mask = VectorMask.fromArray(BSPEC_128, m, 0);
        ByteVector.fromArray(BSPEC_128, ba, 0, indexes[2], 0, mask)
                  .intoArray(br, 0);
    }

    @Check(test = "testLoadGatherMaskedByte128")
    public void verifyLoadGatherMaskedByte128() {
        for (int i = 0; i < BSPEC_128.length(); i++) {
            Asserts.assertEquals(m[i] ? ba[indexes[2][i]] : 0, br[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "4"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "2"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "64"})
    public void testLoadGatherMaskedByte256() {
        VectorMask<Byte> mask = VectorMask.fromArray(BSPEC_256, m, 0);
        ByteVector.fromArray(BSPEC_256, ba, 0, indexes[3], 0, mask)
                  .intoArray(br, 0);
    }

    @Check(test = "testLoadGatherMaskedByte256")
    public void verifyLoadGatherMaskedByte256() {
        for (int i = 0; i < BSPEC_256.length(); i++) {
            Asserts.assertEquals(m[i] ? ba[indexes[3][i]] : 0, br[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "4"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "64"})
    public void testLoadGatherMaskedByte512() {
        VectorMask<Byte> mask = VectorMask.fromArray(BSPEC_512, m, 0);
        ByteVector.fromArray(BSPEC_512, ba, 0, indexes[4], 0, mask)
                  .intoArray(br, 0);
    }

    @Check(test = "testLoadGatherMaskedByte512")
    public void verifyLoadGatherMaskedByte512() {
        for (int i = 0; i < BSPEC_512.length(); i++) {
            Asserts.assertEquals(m[i] ? ba[indexes[4][i]] : 0, br[i]);
        }
    }

    // Tests for masked gather load of short vector with different vector species.

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 16"})
    public void testLoadGatherMaskedShort64() {
        VectorMask<Short> mask = VectorMask.fromArray(SSPEC_64, m, 0);
        ShortVector.fromArray(SSPEC_64, sa, 0, indexes[0], 0, mask)
                   .intoArray(sr, 0);
    }

    @Check(test = "testLoadGatherMaskedShort64")
    public void verifyLoadGatherMaskedShort64() {
        for (int i = 0; i < SSPEC_64.length(); i++) {
            Asserts.assertEquals(m[i] ? sa[indexes[0][i]] : 0, sr[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "2"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 32"})
    public void testLoadGatherMaskedShort128() {
        VectorMask<Short> mask = VectorMask.fromArray(SSPEC_128, m, 0);
        ShortVector.fromArray(SSPEC_128, sa, 0, indexes[1], 0, mask)
                   .intoArray(sr, 0);
    }

    @Check(test = "testLoadGatherMaskedShort128")
    public void verifyLoadGatherMaskedShort128() {
        for (int i = 0; i < SSPEC_128.length(); i++) {
            Asserts.assertEquals(m[i] ? sa[indexes[1][i]] : 0, sr[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "2"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "64"})
    public void testLoadGatherMaskedShort256() {
        VectorMask<Short> mask = VectorMask.fromArray(SSPEC_256, m, 0);
        ShortVector.fromArray(SSPEC_256, sa, 0, indexes[2], 0, mask)
                   .intoArray(sr, 0);
    }

    @Check(test = "testLoadGatherMaskedShort256")
    public void verifyLoadGatherMaskedShort256() {
        for (int i = 0; i < SSPEC_256.length(); i++) {
            Asserts.assertEquals(m[i] ? sa[indexes[2][i]] : 0, sr[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_GATHER_MASKED, "2"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", "64"})
    public void testLoadGatherMaskedShort512() {
        VectorMask<Short> mask = VectorMask.fromArray(SSPEC_512, m, 0);
        ShortVector.fromArray(SSPEC_512, sa, 0, indexes[3], 0, mask)
                   .intoArray(sr, 0);
    }

    @Check(test = "testLoadGatherMaskedShort512")
    public void verifyLoadGatherMaskedShort512() {
        for (int i = 0; i < SSPEC_512.length(); i++) {
            Asserts.assertEquals(m[i] ? sa[indexes[3][i]] : 0, sr[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000)
                     .addFlags("--add-modules=jdk.incubator.vector",
                               "-XX:-TieredCompilation");
        // Set MaxVectorSize for tests.
        if (args != null && args.length > 0) {
            String vmFlags = "";
            switch (args[0]) {
                case "MaxVectorSize_16":
                    vmFlags = "-XX:MaxVectorSize=16";
                    break;
                case "MaxVectorSize_32":
                    vmFlags = "-XX:MaxVectorSize=32";
                    break;
                case "MaxVectorSize_64":
                    vmFlags = "-XX:MaxVectorSize=64";
                    break;
                default:
                    throw new RuntimeException("Unexpected args");
            }
            testFramework.addFlags(vmFlags);
        }
        testFramework.start();
    }
}
