/*
 * Copyright (c) 2025, Arm Limited. All rights reserved.
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
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Random;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
/**
 * @test
 * @bug 8348868
 * @library /test/lib /
 * @summary Verify that SelectFromTwoVector IR node is correctly being
 *          generated on aarch64 and x86
 * @modules jdk.incubator.vector
 * @run driver compiler.vectorapi.TestSelectFromTwoVectorOp
 */

public class TestSelectFromTwoVectorOp {
    private static final int SIZE = 1024;
    private static final Generators random = Generators.G;

    private static byte[] ba;
    private static byte[] bb;
    private static byte[] bres;
    private static byte[][] bindex;

    private static short[] sa;
    private static short[] sb;
    private static short[] sres;
    private static short[][] sindex;

    private static int[] ia;
    private static int[] ib;
    private static int[] ires;
    private static int[][] iindex;

    private static float[] fa;
    private static float[] fb;
    private static float[] fres;
    private static float[][] findex;

    private static long[] la;
    private static long[] lb;
    private static long[] lres;
    private static long[][] lindex;

    private static double[] da;
    private static double[] db;
    private static double[] dres;
    private static double[][] dindex;

    // Stores the possible number of elements that can be
    // held in various vector sizes/shapes
    private static int [] nums = {2, 4, 8, 16, 32, 64};

   static {
        ba   = new byte[SIZE];
        bb   = new byte[SIZE];
        bres = new byte[SIZE];
        bindex = new byte[4][SIZE];

        sa   = new short[SIZE];
        sb   = new short[SIZE];
        sres = new short[SIZE];
        sindex = new short[4][SIZE];

        ia   = new int[SIZE];
        ib   = new int[SIZE];
        ires = new int[SIZE];
        iindex = new int[4][SIZE];

        fa   = new float[SIZE];
        fb   = new float[SIZE];
        fres = new float[SIZE];
        findex = new float[4][SIZE];

        la   = new long[SIZE];
        lb   = new long[SIZE];
        lres = new long[SIZE];
        lindex = new long[3][SIZE];

        da   = new double[SIZE];
        db   = new double[SIZE];
        dres = new double[SIZE];
        dindex = new double[3][SIZE];

        // Populate the indices
        for (int i = 0; i < bindex.length; i++) {
            bindex[i] = new byte[SIZE];
            sindex[i] = new short[SIZE];
            iindex[i] = new int[SIZE];
            findex[i] = new float[SIZE];

            // The index array contains indices in the range of [0, vector_length * 2)
            Generator<Integer> byteGen1 = random.uniformInts(0, (nums[i + 2] * 2) - 1);
            Generator<Integer> shortGen1 = random.uniformInts(0, (nums[i + 1] * 2) - 1);

            for (int j = 0; j < SIZE; j++) {
                bindex[i][j] = byteGen1.next().byteValue();
                sindex[i][j] = shortGen1.next().shortValue();
            }

            if (i < dindex.length) {
              dindex[i] = new double[SIZE];
              lindex[i] = new long[SIZE];

              random.fill(random.uniformDoubles(0, (double) ((nums[i] * 2) - 1)), dindex[i]);
              random.fill(random.uniformLongs(0, (long) ((nums[i] * 2) - 1)), lindex[i]);
            }

            random.fill(random.uniformInts(0, (nums[i] * 2) - 1), iindex[i]);
            random.fill(random.uniformFloats(0, (float)((nums[i] * 2) - 1)), findex[i]);
        }

        // Populate the sources
        Generator<Integer> byteGen = random.uniformInts(Byte.MIN_VALUE, Byte.MAX_VALUE);
        Generator<Integer> shortGen = random.uniformInts(Short.MIN_VALUE, Short.MAX_VALUE);

        for (int i = 0; i < SIZE; i++) {
            ba[i] = byteGen.next().byteValue();
            bb[i] = byteGen.next().byteValue();

            sa[i] = shortGen.next().shortValue();
            sb[i] = shortGen.next().shortValue();
        }

        random.fill(random.ints(), ia);
        random.fill(random.ints(), ib);
        random.fill(random.floats(), fa);
        random.fill(random.floats(), fb);
        random.fill(random.longs(), la);
        random.fill(random.longs(), lb);
        random.fill(random.doubles(), da);
        random.fill(random.doubles(), db);
    }

    // Test SelectFromTwoVector operation for Bytes
    @ForceInline
    public static void ByteSelectFromTwoVectorKernel(VectorSpecies SPECIES, byte[] ba,
                                                     byte[] bb, byte[] bindex) {
        for (int i = 0; i < SPECIES.loopBound(ba.length); i += SPECIES.length()) {
            ByteVector.fromArray(SPECIES, bindex, i)
                .selectFrom(ByteVector.fromArray(SPECIES, ba, i),
                            ByteVector.fromArray(SPECIES, bb, i))
                .intoArray(bres, i);
        }
    }

    @Test
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_8, ">0"},
        applyIfCPUFeature = {"asimd", "true"},
        applyIf = {"MaxVectorSize", ">=8"})
    public static void selectFromTwoVector_Byte64() {
        ByteSelectFromTwoVectorKernel(ByteVector.SPECIES_64, ba, bb, bindex[0]);
    }

    @Test
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_16, ">0"},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_16, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_16, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_16, ">0"},
        applyIfCPUFeatureAnd = {"avx512_vbmi", "true", "avx512vl", "true"},
        applyIf = {"MaxVectorSize", ">=16"})
    public static void selectFromTwoVector_Byte128() {
        ByteSelectFromTwoVectorKernel(ByteVector.SPECIES_128, ba, bb, bindex[1]);
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_32},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_32, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_32, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_32, ">0"},
        applyIfCPUFeatureAnd = {"avx512_vbmi", "true", "avx512vl", "true"},
        applyIf = {"MaxVectorSize", ">=32"})
    public static void selectFromTwoVector_Byte256() {
        ByteSelectFromTwoVectorKernel(ByteVector.SPECIES_256, ba, bb, bindex[2]);
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_64},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_64, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_64, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VB, IRNode.VECTOR_SIZE_64, ">0"},
        applyIfCPUFeatureAnd = {"avx512_vbmi", "true", "avx512f", "true"},
        applyIf = {"MaxVectorSize", ">=64"})
    public static void selectFromTwoVector_Byte512() {
        ByteSelectFromTwoVectorKernel(ByteVector.SPECIES_512, ba, bb, bindex[3]);
    }

    // Test SelectFromTwoVector operation for Shorts
    @ForceInline
    public static void ShortSelectFromTwoVectorKernel(VectorSpecies SPECIES, short[] sa,
                                                      short[] sb, short[] sindex) {
        for (int i = 0; i < SPECIES.loopBound(sa.length); i += SPECIES.length()) {
            ShortVector.fromArray(SPECIES, sindex, i)
                .selectFrom(ShortVector.fromArray(SPECIES, sa, i),
                            ShortVector.fromArray(SPECIES, sb, i))
                .intoArray(sres, i);
        }
    }

    @Test
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_4, ">0"},
        applyIfCPUFeature = {"asimd", "true"},
        applyIf = {"MaxVectorSize", ">=8"})
    public static void selectFromTwoVector_Short64() {
        ShortSelectFromTwoVectorKernel(ShortVector.SPECIES_64, sa, sb, sindex[0]);
    }

    @Test
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_8, ">0"},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_8, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_8, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_8, ">0"},
        applyIfCPUFeatureAnd = {"avx512bw", "true", "avx512vl", "true"},
        applyIf = {"MaxVectorSize", ">=16"})
    public static void selectFromTwoVector_Short128() {
        ShortSelectFromTwoVectorKernel(ShortVector.SPECIES_128, sa, sb, sindex[1]);
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_16},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_16, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_16, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_16, ">0"},
        applyIfCPUFeatureAnd = {"avx512bw", "true", "avx512vl", "true"},
        applyIf = {"MaxVectorSize", ">=32"})
    public static void selectFromTwoVector_Short256() {
        ShortSelectFromTwoVectorKernel(ShortVector.SPECIES_256, sa, sb, sindex[2]);
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_32},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_32, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_32, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VS, IRNode.VECTOR_SIZE_32, ">0"},
        applyIfCPUFeatureAnd = {"avx512bw", "true", "avx512f", "true"},
        applyIf = {"MaxVectorSize", ">=64"})
    public static void selectFromTwoVector_Short512() {
        ShortSelectFromTwoVectorKernel(ShortVector.SPECIES_512, sa, sb, sindex[3]);
    }

    // Test SelectFromTwoVector operation for Ints
    @ForceInline
    public static void IntSelectFromTwoVectorKernel(VectorSpecies SPECIES, int[] ia,
                                                    int[] ib, int[] iindex) {
        for (int i = 0; i < SPECIES.loopBound(ia.length); i += SPECIES.length()) {
            IntVector.fromArray(SPECIES, iindex, i)
                .selectFrom(IntVector.fromArray(SPECIES, ia, i),
                            IntVector.fromArray(SPECIES, ib, i))
                .intoArray(ires, i);
        }
    }

    @Test
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_2, ">0"},
        applyIfCPUFeatureOr = {"asimd", "true"},
        applyIf = {"MaxVectorSize", ">=8"})
    public static void selectFromTwoVector_Int64() {
        IntSelectFromTwoVectorKernel(IntVector.SPECIES_64, ia, ib, iindex[0]);
    }

    @Test
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_4, ">0"},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_4, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_4, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_4, ">0"},
        applyIfCPUFeature = {"avx512vl", "true"},
        applyIf = {"MaxVectorSize", ">=16"})
    public static void selectFromTwoVector_Int128() {
        IntSelectFromTwoVectorKernel(IntVector.SPECIES_128, ia, ib, iindex[1]);
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_8},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_8, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_8, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_8, ">0"},
        applyIfCPUFeature = {"avx512vl", "true"},
        applyIf = {"MaxVectorSize", ">=32"})
    public static void selectFromTwoVector_Int256() {
        IntSelectFromTwoVectorKernel(IntVector.SPECIES_256, ia, ib, iindex[2]);
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_16},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_16, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_16, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VI, IRNode.VECTOR_SIZE_16, ">0"},
        applyIfCPUFeature = {"avx512f", "true"},
        applyIf = {"MaxVectorSize", ">=64"})
    public static void selectFromTwoVector_Int512() {
        IntSelectFromTwoVectorKernel(IntVector.SPECIES_512, ia, ib, iindex[3]);
    }

    // Test SelectFromTwoVector operation for Floats
    @ForceInline
    public static void FloatSelectFromTwoVectorKernel(VectorSpecies SPECIES, float[] fa,
                                                      float[] fb, float[] findex) {
        for (int i = 0; i < SPECIES.loopBound(ia.length); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, findex, i)
                .selectFrom(FloatVector.fromArray(SPECIES, fa, i),
                            FloatVector.fromArray(SPECIES, fb, i))
                .intoArray(fres, i);
        }
    }

    @Test
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_2, ">0"},
        applyIfCPUFeatureOr = {"asimd", "true"},
        applyIf = {"MaxVectorSize", ">=8"})
    public static void selectFromTwoVector_Float64() {
        FloatSelectFromTwoVectorKernel(FloatVector.SPECIES_64, fa, fb, findex[0]);
    }

    @Test
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_4, ">0"},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_4, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_4, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_4, ">0"},
        applyIfCPUFeature = {"avx512vl", "true"},
        applyIf = {"MaxVectorSize", ">=16"})
    public static void selectFromTwoVector_Float128() {
        FloatSelectFromTwoVectorKernel(FloatVector.SPECIES_128, fa, fb, findex[1]);
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_8},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_8, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_8, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_8, ">0"},
        applyIfCPUFeature = {"avx512vl", "true"},
        applyIf = {"MaxVectorSize", ">=32"})
    public static void selectFromTwoVector_Float256() {
        FloatSelectFromTwoVectorKernel(FloatVector.SPECIES_256, fa, fb, findex[2]);
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_16},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_16, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_16, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VF, IRNode.VECTOR_SIZE_16, ">0"},
        applyIfCPUFeature = {"avx512f", "true"},
        applyIf = {"MaxVectorSize", ">=64"})
    public static void selectFromTwoVector_Float512() {
        FloatSelectFromTwoVectorKernel(FloatVector.SPECIES_512, fa, fb, findex[3]);
    }

    // Test SelectFromTwoVector operation for Doubles
    @ForceInline
    public static void DoubleSelectFromTwoVectorKernel(VectorSpecies SPECIES, double[] da,
                                                       double[] db, double[] dindex) {
        for (int i = 0; i < SPECIES.loopBound(ia.length); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, dindex, i)
                .selectFrom(DoubleVector.fromArray(SPECIES, da, i),
                            DoubleVector.fromArray(SPECIES, db, i))
                .intoArray(dres, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VD, IRNode.VECTOR_SIZE_2},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VD, IRNode.VECTOR_SIZE_2, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VD, IRNode.VECTOR_SIZE_2, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VD, IRNode.VECTOR_SIZE_2, ">0"},
        applyIfCPUFeature = {"avx512vl", "true"},
        applyIf = {"MaxVectorSize", ">=16"})
    public static void selectFromTwoVector_Double128() {
        DoubleSelectFromTwoVectorKernel(DoubleVector.SPECIES_128, da, db, dindex[0]);
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VD, IRNode.VECTOR_SIZE_4},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VD, IRNode.VECTOR_SIZE_4, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VD, IRNode.VECTOR_SIZE_4, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VD, IRNode.VECTOR_SIZE_4, ">0"},
        applyIfCPUFeature = {"avx512vl", "true"},
        applyIf = {"MaxVectorSize", ">=32"})
    public static void selectFromTwoVector_Double256() {
        DoubleSelectFromTwoVectorKernel(DoubleVector.SPECIES_256, da, db, dindex[1]);
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VD, IRNode.VECTOR_SIZE_8},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VD, IRNode.VECTOR_SIZE_8, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VD, IRNode.VECTOR_SIZE_8, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VD, IRNode.VECTOR_SIZE_8, ">0"},
        applyIfCPUFeature = {"avx512f", "true"},
        applyIf = {"MaxVectorSize", ">=64"})
    public static void selectFromTwoVector_Double512() {
        DoubleSelectFromTwoVectorKernel(DoubleVector.SPECIES_512, da, db, dindex[2]);
    }

    // Test SelectFromTwoVector operation for Longs
    @ForceInline
    public static void LongSelectFromTwoVectorKernel(VectorSpecies SPECIES, long[] la,
                                                     long[] lb, long[] lindex) {
        for (int i = 0; i < SPECIES.loopBound(ia.length); i += SPECIES.length()) {
            LongVector.fromArray(SPECIES, lindex, i)
                .selectFrom(LongVector.fromArray(SPECIES, la, i),
                            LongVector.fromArray(SPECIES, lb, i))
                .intoArray(lres, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VL, IRNode.VECTOR_SIZE_2},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VL, IRNode.VECTOR_SIZE_2, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VL, IRNode.VECTOR_SIZE_2, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">16"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VL, IRNode.VECTOR_SIZE_2, ">0"},
        applyIfCPUFeature = {"avx512vl", "true"},
        applyIf = {"MaxVectorSize", ">=16"})
    public static void selectFromTwoVector_Long128() {
        LongSelectFromTwoVectorKernel(LongVector.SPECIES_128, la, lb, lindex[0]);
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VL, IRNode.VECTOR_SIZE_4},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VL, IRNode.VECTOR_SIZE_4, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VL, IRNode.VECTOR_SIZE_4, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">32"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VL, IRNode.VECTOR_SIZE_4, ">0"},
        applyIfCPUFeature = {"avx512vl", "true"},
        applyIf = {"MaxVectorSize", ">=32"})
    public static void selectFromTwoVector_Long256() {
        LongSelectFromTwoVectorKernel(LongVector.SPECIES_256, la, lb, lindex[1]);
    }

    @Test
    @IR(failOn = {IRNode.SELECT_FROM_TWO_VECTOR_VL, IRNode.VECTOR_SIZE_8},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve2", "false"},
        applyIf = {"MaxVectorSize", ">=64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VL, IRNode.VECTOR_SIZE_8, ">0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", "64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VL, IRNode.VECTOR_SIZE_8, "0"},
        applyIfCPUFeature = {"sve2", "true"},
        applyIf = {"MaxVectorSize", ">64"})
    @IR(counts = {IRNode.SELECT_FROM_TWO_VECTOR_VL, IRNode.VECTOR_SIZE_8, ">0"},
        applyIfCPUFeature = {"avx512f", "true"},
        applyIf = {"MaxVectorSize", ">=64"})
    public static void selectFromTwoVector_Long512() {
        LongSelectFromTwoVectorKernel(LongVector.SPECIES_512, la, lb, lindex[2]);
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
