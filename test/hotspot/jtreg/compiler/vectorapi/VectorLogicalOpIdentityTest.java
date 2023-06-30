/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8288294
 * @key randomness
 * @library /test/lib /
 * @summary Add identity transformations for vector logic operations
 * @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*avx.*") | os.arch=="aarch64"
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorLogicalOpIdentityTest
 */

public class VectorLogicalOpIdentityTest {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;

    private static int LENGTH = 128;
    private static final Random RD = Utils.getRandomInstance();

    private static byte[] ba;
    private static byte[] br;
    private static short[] sa;
    private static short[] sr;
    private static int[] ia;
    private static int[] ib;
    private static int[] ir;
    private static long[] la;
    private static long[] lb;
    private static long[] lr;
    private static boolean[] m;
    private static boolean[] mr;

    static {
        ba = new byte[LENGTH];
        br = new byte[LENGTH];
        sa = new short[LENGTH];
        sr = new short[LENGTH];
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ir = new int[LENGTH];
        la = new long[LENGTH];
        lb = new long[LENGTH];
        lr = new long[LENGTH];
        m = new boolean[LENGTH];
        mr = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ba[i] = (byte) RD.nextInt(25);
            sa[i] = (short) RD.nextInt(25);
            ia[i] = RD.nextInt(25);
            ib[i] = RD.nextInt(25);
            la[i] = RD.nextLong(25);
            lb[i] = RD.nextLong(25);
            m[i] = RD.nextBoolean();
        }
    }

    private static long and(long a, long b) {
        return a & b;
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.AND_VB, counts = {IRNode.LOAD_VECTOR_B, ">=1"})
    public static void testAndMinusOne() {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        av.and((byte) -1).intoArray(br, 0);

        // Verify results
        for (int i = 0; i < B_SPECIES.length(); i++) {
            Asserts.assertEquals((byte) and(ba[i], (byte) -1), br[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.AND_VS, counts = {IRNode.STORE_VECTOR, ">=1"})
    public static void testAndZero() {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        av.and((short) 0).intoArray(sr, 0);

        // Verify results
        for (int i = 0; i < S_SPECIES.length(); i++) {
            Asserts.assertEquals((short) and(sa[i], (short) 0), sr[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.AND_VI, counts = {IRNode.LOAD_VECTOR_I, ">=1"})
    public static void testAndSame() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        av.and(av).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals((int) and(ia[i], ia[i]), ir[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.AND_VL, counts = {IRNode.LOAD_VECTOR_L, ">=1"})
    public static void testMaskedAndMinusOne1() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.broadcast(L_SPECIES, -1);
        av.lanewise(VectorOperators.AND, bv, mask).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            if (m[i]) {
                Asserts.assertEquals(and(la[i], -1), lr[i]);
            } else {
                Asserts.assertEquals(la[i], lr[i]);
            }
        }
    }

    // Masked AndV in this test should not be optimized out on SVE.
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.LOAD_VECTOR_B, ">=1"})
    @IR(failOn = IRNode.AND_VB, applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testMaskedAndMinusOne2() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, m, 0);
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector bv = ByteVector.broadcast(B_SPECIES, (byte) -1);
        bv.lanewise(VectorOperators.AND, av, mask).intoArray(br, 0);

        // Verify results
        for (int i = 0; i < B_SPECIES.length(); i++) {
            if (m[i]) {
                Asserts.assertEquals((byte) and(ba[i], (byte) -1), br[i]);
            } else {
                Asserts.assertEquals((byte) -1, br[i]);
            }
        }
    }

    // Masked AndV in this test should not be optimized out on SVE.
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.STORE_VECTOR, ">=1"})
    @IR(failOn = IRNode.AND_VS, applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testMaskedAndZero1() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, m, 0);
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector bv = ShortVector.broadcast(S_SPECIES, (short) 0);
        av.lanewise(VectorOperators.AND, bv, mask).intoArray(sr, 0);

        // Verify results
        for (int i = 0; i < S_SPECIES.length(); i++) {
            if (m[i]) {
                Asserts.assertEquals((short) and(sa[i], (short) 0), sr[i]);
            } else {
                Asserts.assertEquals(sa[i], sr[i]);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.AND_VI, counts = {IRNode.STORE_VECTOR, ">=1"})
    public static void testMaskedAndZero2() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.broadcast(I_SPECIES, 0);
        bv.lanewise(VectorOperators.AND, av, mask).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            if (m[i]) {
                Asserts.assertEquals((int) and(ba[i], 0), ir[i]);
            } else {
                Asserts.assertEquals(0, ir[i]);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.AND_VL, counts = {IRNode.LOAD_VECTOR_L, ">=1"})
    public static void testMaskedAndSame() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        av.lanewise(VectorOperators.AND, av, mask).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            if (m[i]) {
                Asserts.assertEquals(and(la[i], la[i]), lr[i]);
            } else {
                Asserts.assertEquals(la[i], lr[i]);
            }
        }
    }

    // Transform AndV(AndV(a, b), b) ==> AndV(a, b)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.AND_VI, "1"})
    public static void testAndSameValue1() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        (av.and(bv).and(bv)).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals((int) and(and(ia[i], ib[i]), ib[i]), ir[i]);
        }
    }

    // Transform AndV(AndV(a, b), a) ==> AndV(a, b)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.AND_VL, "1"})
    public static void testAndSameValue2() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        (av.and(bv).and(av)).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals(and(and(la[i], lb[i]), la[i]), lr[i]);
        }
    }

    // Transform AndV(b, AndV(a, b)) ==> AndV(a, b)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.AND_VI, "1"})
    public static void testAndSameValue3() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        (bv.and(av.and(bv))).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals((int) and(ib[i], and(ia[i], ib[i])), ir[i]);
        }
    }

    // Transform AndV(a, AndV(a, b)) ==> AndV(a, b)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.AND_VL, "1"})
    public static void testAndSameValue4() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        (av.and(av.and(bv))).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals(and(la[i], and(la[i], lb[i])), lr[i]);
        }
    }

    // Transform AndV(AndV(a, b, m), b, m) ==> AndV(a, b, m)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.AND_VI, "1"}, applyIfCPUFeatureOr = {"sve", "true", "avx512", "true"})
    public static void testAndMaskSameValue1() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.lanewise(VectorOperators.AND, bv, mask)
        .lanewise(VectorOperators.AND, bv, mask).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            if (m[i]) {
              Asserts.assertEquals((int) and(and(ia[i], ib[i]), ib[i]), ir[i]);
            } else {
              Asserts.assertEquals(ia[i], ir[i]);
            }
        }
    }

    // Transform AndV(AndV(a, b, m), a, m) ==> AndV(a, b, m)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.AND_VL, "1"}, applyIfCPUFeatureOr = {"sve", "true", "avx512", "true"})
    public static void testAndMaskSameValue2() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        av.lanewise(VectorOperators.AND, bv, mask)
        .lanewise(VectorOperators.AND, av, mask).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            if (m[i]) {
              Asserts.assertEquals(and(and(la[i], lb[i]), la[i]), lr[i]);
            } else {
              Asserts.assertEquals(la[i], lr[i]);
            }
        }
    }

    // Transform AndV(a, AndV(a, b, m), m) ==> AndV(a, b, m)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.AND_VI, "1"}, applyIfCPUFeatureOr = {"sve", "true", "avx512", "true"})
    public static void testAndMaskSameValue3() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.lanewise(VectorOperators.AND, av.lanewise(VectorOperators.AND, bv, mask), mask)
        .intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            if (m[i]) {
              Asserts.assertEquals((int) and(ia[i], and(ia[i], ib[i])), ir[i]);
            } else {
              Asserts.assertEquals(ia[i], ir[i]);
            }
        }
    }

    private static long or(long a, long b) {
        return a | b;
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.OR_VB, counts = {IRNode.STORE_VECTOR, ">=1"})
    public static void testOrMinusOne() {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        av.or((byte) -1).intoArray(br, 0);

        // Verify results
        for (int i = 0; i < B_SPECIES.length(); i++) {
            Asserts.assertEquals((byte) or(ba[i], (byte) -1), br[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.OR_VS, counts = {IRNode.LOAD_VECTOR_S, ">=1"})
    public static void testOrZero() {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        av.or((short) 0).intoArray(sr, 0);

        // Verify results
        for (int i = 0; i < S_SPECIES.length(); i++) {
            Asserts.assertEquals((short) or(sa[i], (short) 0), sr[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.OR_VI, counts = {IRNode.LOAD_VECTOR_I, ">=1"})
    public static void testOrSame() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        av.or(av).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals((int) or(ia[i], ia[i]), ir[i]);
        }
    }

    // Masked OrV in this test should not be optimized out on SVE.
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.STORE_VECTOR, ">=1"})
    @IR(failOn = IRNode.OR_VB, applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testMaskedOrMinusOne1() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, m, 0);
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector bv = ByteVector.broadcast(B_SPECIES, -1);
        av.lanewise(VectorOperators.OR, bv, mask).intoArray(br, 0);

        // Verify results
        for (int i = 0; i < B_SPECIES.length(); i++) {
            if (m[i]) {
                Asserts.assertEquals((byte) or(ba[i], -1), br[i]);
            } else {
                Asserts.assertEquals(ba[i], br[i]);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.OR_VB, counts = {IRNode.STORE_VECTOR, ">=1"})
    public static void testMaskedOrMinusOne2() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, m, 0);
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector bv = ByteVector.broadcast(B_SPECIES, (byte) -1);
        bv.lanewise(VectorOperators.OR, av, mask).intoArray(br, 0);

        // Verify results
        for (int i = 0; i < B_SPECIES.length(); i++) {
            if (m[i]) {
                Asserts.assertEquals((byte) or(ba[i], (byte) -1), br[i]);
            } else {
                Asserts.assertEquals((byte) -1, br[i]);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.OR_VS, counts = {IRNode.LOAD_VECTOR_S, ">=1"})
    public static void testMaskedOrZero1() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, m, 0);
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector bv = ShortVector.broadcast(S_SPECIES, (short) 0);
        av.lanewise(VectorOperators.OR, bv, mask).intoArray(sr, 0);

        // Verify results
        for (int i = 0; i < S_SPECIES.length(); i++) {
            if (m[i]) {
                Asserts.assertEquals((short) or(sa[i], (short) 0), sr[i]);
            } else {
                Asserts.assertEquals(sa[i], sr[i]);
            }
        }
    }

    // Masked OrV in this test should not be optimized out on SVE.
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.LOAD_VECTOR_B, ">=1"})
    @IR(failOn = IRNode.OR_VB, applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testMaskedOrZero2() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, m, 0);
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector bv = ByteVector.broadcast(B_SPECIES, 0);
        bv.lanewise(VectorOperators.OR, av, mask).intoArray(br, 0);

        // Verify results
        for (int i = 0; i < B_SPECIES.length(); i++) {
            if (m[i]) {
                Asserts.assertEquals((byte) or(ba[i], 0), br[i]);
            } else {
                Asserts.assertEquals((byte) 0, br[i]);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.OR_VL, counts = {IRNode.LOAD_VECTOR_L, ">=1"})
    public static void testMaskedOrSame() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        av.lanewise(VectorOperators.OR, av, mask).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            if (m[i]) {
                Asserts.assertEquals(or(la[i], la[i]), lr[i]);
            } else {
                Asserts.assertEquals(la[i], lr[i]);
            }
        }
    }

    // Transform OrV(OrV(a, b), b) ==> OrV(a, b)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.OR_VI, "1"})
    public static void testOrSameValue1() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        (av.or(bv).or(bv)).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals((int) or(or(ia[i], ib[i]), ib[i]), ir[i]);
        }
    }

    // Transform OrV(OrV(a, b), a) ==> OrV(a, b)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.OR_VL, "1"})
    public static void testOrSameValue2() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        (av.or(bv).or(av)).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals(or(or(la[i], lb[i]), la[i]), lr[i]);
        }
    }

    // Transform OrV(b, OrV(a, b)) ==> OrV(a, b)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.OR_VI, "1"})
    public static void testOrSameValue3() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        (bv.or(av.or(bv))).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals((int) or(ib[i], or(ia[i], ib[i])), ir[i]);
        }
    }

    // Transform OrV(a, OrV(a, b)) ==> OrV(a, b)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.OR_VL, "1"})
    public static void testOrSameValue4() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        (av.or(av.or(bv))).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals(or(la[i], or(la[i], lb[i])), lr[i]);
        }
    }

    // Transform OrV(OrV(a, b, m), b, m) ==> OrV(a, b, m)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.OR_VI, "1"}, applyIfCPUFeatureOr = {"sve", "true", "avx512", "true"})
    public static void testOrMaskSameValue1() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.lanewise(VectorOperators.OR, bv, mask)
        .lanewise(VectorOperators.OR, bv, mask).intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            if (m[i]) {
              Asserts.assertEquals((int) or(or(ia[i], ib[i]), ib[i]), ir[i]);
            } else {
              Asserts.assertEquals(ia[i], ir[i]);
            }
        }
    }

    // Transform OrV(OrV(a, b, m), a, m) ==> OrV(a, b, m)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.OR_VL, "1"}, applyIfCPUFeatureOr = {"sve", "true", "avx512", "true"})
    public static void testOrMaskSameValue2() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        av.lanewise(VectorOperators.OR, bv, mask)
        .lanewise(VectorOperators.OR, av, mask).intoArray(lr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            if (m[i]) {
              Asserts.assertEquals(or(or(la[i], lb[i]), la[i]), lr[i]);
            } else {
              Asserts.assertEquals(la[i], lr[i]);
            }
        }
    }

    // Transform OrV(a, OrV(a, b, m), m) ==> OrV(a, b, m)
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.OR_VI, "1"}, applyIfCPUFeatureOr = {"sve", "true", "avx512", "true"})
    public static void testOrMaskSameValue3() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        av.lanewise(VectorOperators.OR, av.lanewise(VectorOperators.OR, bv, mask), mask)
        .intoArray(ir, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            if (m[i]) {
              Asserts.assertEquals((int) or(ia[i], or(ia[i], ib[i])), ir[i]);
            } else {
              Asserts.assertEquals(ia[i], ir[i]);
            }
        }
    }

    private static long xor(long a, long b) {
        return a ^ b;
    }

    @Test
    @Warmup(10000)
    @IR(failOn = IRNode.XOR_VB, counts = {IRNode.STORE_VECTOR, ">=1"})
    public static void testXorSame() {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        av.lanewise(VectorOperators.XOR, av).intoArray(br, 0);

        // Verify results
        for (int i = 0; i < B_SPECIES.length(); i++) {
            Asserts.assertEquals((byte) xor(ba[i], ba[i]), br[i]);
        }
    }

    // Masked XorV in this test should not be optimized out on SVE.
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.STORE_VECTOR, ">=1"})
    @IR(failOn = IRNode.XOR_VS, applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    public static void testMaskedXorSame() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, m, 0);
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        av.lanewise(VectorOperators.XOR, av, mask).intoArray(sr, 0);

        // Verify results
        for (int i = 0; i < S_SPECIES.length(); i++) {
            if (m[i]) {
                Asserts.assertEquals((short) xor(sa[i], sa[i]), sr[i]);
            } else {
                Asserts.assertEquals(sa[i], sr[i]);
            }
        }
    }

    // Following are the vector mask logic operations tests
    @Test
    @Warmup(10000)
    @IR(failOn = {IRNode.AND_VI, IRNode.AND_V_MASK}, counts = {IRNode.STORE_VECTOR, ">=1"})
    public static void testMaskAndMinusOne() {
        VectorMask<Integer> ma = VectorMask.fromArray(I_SPECIES, m, 0);
        VectorMask<Integer> mb = I_SPECIES.maskAll(true);
        ma.and(mb).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(m[i], mr[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = {IRNode.AND_VS, IRNode.AND_V_MASK}, counts = {IRNode.STORE_VECTOR, ">=1"})
    public static void testMaskAndZero() {
        VectorMask<Short> ma = VectorMask.fromArray(S_SPECIES, m, 0);
        VectorMask<Short> mb = S_SPECIES.maskAll(false);
        ma.and(mb).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < S_SPECIES.length(); i++) {
            Asserts.assertEquals(false, mr[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = {IRNode.AND_VB, IRNode.AND_V_MASK}, counts = {IRNode.STORE_VECTOR, ">=1"})
    public static void testMaskAndSame() {
        VectorMask<Byte> ma = VectorMask.fromArray(B_SPECIES, m, 0);
        ma.and(ma).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < B_SPECIES.length(); i++) {
            Asserts.assertEquals(m[i], mr[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = {IRNode.OR_VS, IRNode.OR_V_MASK}, counts = {IRNode.STORE_VECTOR, ">=1"})
    public static void testMaskOrMinusOne() {
        VectorMask<Short> ma = VectorMask.fromArray(S_SPECIES, m, 0);
        VectorMask<Short> mb = S_SPECIES.maskAll(true);
        ma.or(mb).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < S_SPECIES.length(); i++) {
            Asserts.assertEquals(true, mr[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = {IRNode.OR_VI, IRNode.OR_V_MASK}, counts = {IRNode.STORE_VECTOR, ">=1"})
    public static void testMaskOrZero() {
        VectorMask<Integer> ma = VectorMask.fromArray(I_SPECIES, m, 0);
        VectorMask<Integer> mb = I_SPECIES.maskAll(false);
        ma.or(mb).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(m[i], mr[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = {IRNode.OR_VB, IRNode.OR_V_MASK}, counts = {IRNode.STORE_VECTOR, ">=1"})
    public static void testMaskOrSame() {
        VectorMask<Byte> ma = VectorMask.fromArray(B_SPECIES, m, 0);
        ma.or(ma).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < B_SPECIES.length(); i++) {
            Asserts.assertEquals(m[i], mr[i]);
        }
    }

    @Test
    @Warmup(10000)
    @IR(failOn = {IRNode.XOR_VI, IRNode.XOR_V_MASK}, counts = {IRNode.STORE_VECTOR, ">=1"})
    public static void testMaskXorSame() {
        VectorMask<Integer> ma = I_SPECIES.maskAll(true);
        ma.not().intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(false, mr[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
