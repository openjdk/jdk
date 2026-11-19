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
 * @bug 8366333
 * @key randomness
 * @library /test/lib /
 * @summary IR test for VectorAPI compress
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorCompressTest
 */

public class VectorCompressTest {
    static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;
    static final int LENGTH = 512;
    static final Generators RD = Generators.G;
    static byte[] ba, bb;
    static short[] sa, sb;
    static int[] ia, ib;
    static long[] la, lb;
    static float[] fa, fb;
    static double[] da, db;
    static boolean[] ma;

    static {
        ba = new byte[LENGTH];
        bb = new byte[LENGTH];
        sa = new short[LENGTH];
        sb = new short[LENGTH];
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        la = new long[LENGTH];
        lb = new long[LENGTH];
        fa = new float[LENGTH];
        fb = new float[LENGTH];
        da = new double[LENGTH];
        db = new double[LENGTH];
        ma = new boolean[LENGTH];

        Generator<Integer> iGen = RD.ints();
        Generator<Long> lGen = RD.longs();
        Generator<Float> fGen = RD.floats();
        Generator<Double> dGen = RD.doubles();

        for (int i = 0; i < LENGTH; i++) {
            ba[i] = iGen.next().byteValue();
            sa[i] = iGen.next().shortValue();
            ma[i] = iGen.next() % 2 == 0;
        }
        RD.fill(iGen, ia);
        RD.fill(lGen, la);
        RD.fill(fGen, fa);
        RD.fill(dGen, da);
    }

    @DontInline
    static void verifyVectorCompressByte(int vlen) {
        int index = 0;
        for (int i = 0; i < vlen; i++) {
            if (ma[i]) {
                Asserts.assertEquals(ba[i], bb[index++]);
            }
        }
        for (int i = index; i < vlen; i++) {
            Asserts.assertEquals((byte)0, bb[i]);
        }
    }

    @DontInline
    static void verifyVectorCompressShort(int vlen) {
        int index = 0;
        for (int i = 0; i < vlen; i++) {
            if (ma[i]) {
                Asserts.assertEquals(sa[i], sb[index++]);
            }
        }
        for (int i = index; i < vlen; i++) {
            Asserts.assertEquals((short)0, sb[i]);
        }
    }

    @DontInline
    static void verifyVectorCompressInteger(int vlen) {
        int index = 0;
        for (int i = 0; i < vlen; i++) {
            if (ma[i]) {
                Asserts.assertEquals(ia[i], ib[index++]);
            }
        }
        for (int i = index; i < vlen; i++) {
            Asserts.assertEquals(0, ib[i]);
        }
    }

    @DontInline
    static void verifyVectorCompressLong(int vlen) {
        int index = 0;
        for (int i = 0; i < vlen; i++) {
            if (ma[i]) {
                Asserts.assertEquals(la[i], lb[index++]);
            }
        }
        for (int i = index; i < vlen; i++) {
            Asserts.assertEquals(0L, lb[i]);
        }
    }

    @DontInline
    static void verifyVectorCompressFloat(int vlen) {
        int index = 0;
        for (int i = 0; i < vlen; i++) {
            if (ma[i]) {
                Asserts.assertEquals(fa[i], fb[index++]);
            }
        }
        for (int i = index; i < vlen; i++) {
            Asserts.assertEquals(0.0f, fb[i]);
        }
    }

    @DontInline
    static void verifyVectorCompressDouble(int vlen) {
        int index = 0;
        for (int i = 0; i < vlen; i++) {
            if (ma[i]) {
                Asserts.assertEquals(da[i], db[index++]);
            }
        }
        for (int i = index; i < vlen; i++) {
            Asserts.assertEquals(0.0, db[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.COMPRESS_VB, "= 1" },
        applyIfCPUFeature = { "sve", "true" })
    @IR(counts = { IRNode.COMPRESS_VB, "= 1" },
        applyIfCPUFeatureAnd = {"avx512_vbmi2", "true", "avx512vl", "true"})
    public static void testVectorCompressByte() {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        VectorMask<Byte> m = VectorMask.fromArray(B_SPECIES, ma, 0);
        av.compress(m).intoArray(bb, 0);
        verifyVectorCompressByte(B_SPECIES.length());
    }

    @Test
    @IR(counts = { IRNode.COMPRESS_VS, "= 1" },
        applyIfCPUFeature = { "sve", "true" })
    @IR(counts = { IRNode.COMPRESS_VS, "= 1" },
        applyIfCPUFeatureAnd = {"avx512_vbmi2", "true", "avx512vl", "true"})
    public static void testVectorCompressShort() {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        VectorMask<Short> m = VectorMask.fromArray(S_SPECIES, ma, 0);
        av.compress(m).intoArray(sb, 0);
        verifyVectorCompressShort(S_SPECIES.length());
    }

    @Test
    @IR(counts = { IRNode.COMPRESS_VI, "= 1" },
        applyIfCPUFeature = { "sve", "true" })
    @IR(counts = { IRNode.COMPRESS_VI, "= 1" },
        applyIfCPUFeatureAnd = {"avx512f", "true", "avx512vl", "true"})
    public static void testVectorCompressInt() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        VectorMask<Integer> m = VectorMask.fromArray(I_SPECIES, ma, 0);
        av.compress(m).intoArray(ib, 0);
        verifyVectorCompressInteger(I_SPECIES.length());
    }

    @Test
    @IR(counts = { IRNode.COMPRESS_VL, "= 1" },
        applyIfCPUFeature = { "sve", "true" })
    @IR(counts = { IRNode.COMPRESS_VL, "= 1" },
        applyIfCPUFeatureAnd = {"avx512f", "true", "avx512vl", "true"})
    public static void testVectorCompressLong() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        VectorMask<Long> m = VectorMask.fromArray(L_SPECIES, ma, 0);
        av.compress(m).intoArray(lb, 0);
        verifyVectorCompressLong(L_SPECIES.length());
    }

    @Test
    @IR(counts = { IRNode.COMPRESS_VF, "= 1" },
        applyIfCPUFeature = { "sve", "true" })
    @IR(counts = { IRNode.COMPRESS_VF, "= 1" },
        applyIfCPUFeatureAnd = {"avx512f", "true", "avx512vl", "true"})
    public static void testVectorCompressFloat() {
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        VectorMask<Float> m = VectorMask.fromArray(F_SPECIES, ma, 0);
        av.compress(m).intoArray(fb, 0);
        verifyVectorCompressFloat(F_SPECIES.length());
    }

    @Test
    @IR(counts = { IRNode.COMPRESS_VD, "= 1" },
        applyIfCPUFeature = { "sve", "true" })
    @IR(counts = { IRNode.COMPRESS_VD, "= 1" },
        applyIfCPUFeatureAnd = {"avx512f", "true", "avx512vl", "true"})
    public static void testVectorCompressDouble() {
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        VectorMask<Double> m = VectorMask.fromArray(D_SPECIES, ma, 0);
        av.compress(m).intoArray(db, 0);
        verifyVectorCompressDouble(D_SPECIES.length());
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
