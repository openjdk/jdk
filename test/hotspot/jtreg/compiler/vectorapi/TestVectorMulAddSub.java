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
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8275275
 * @key randomness
 * @library /test/lib /
 * @requires os.arch=="aarch64"
 * @summary AArch64: Fix performance regression after auto-vectorization on NEON
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.TestVectorMulAddSub
 */

public class TestVectorMulAddSub {

    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;

    private static int LENGTH = 1024;
    private static final Random RD = Utils.getRandomInstance();

    private static byte[] ba;
    private static byte[] bb;
    private static byte[] bc;
    private static byte[] br;
    private static short[] sa;
    private static short[] sb;
    private static short[] sc;
    private static short[] sr;
    private static int[] ia;
    private static int[] ib;
    private static int[] ic;
    private static int[] ir;
    private static long[] la;
    private static long[] lb;
    private static long[] lc;
    private static long[] lr;

    static {
        ba = new byte[LENGTH];
        bb = new byte[LENGTH];
        bc = new byte[LENGTH];
        br = new byte[LENGTH];
        sa = new short[LENGTH];
        sb = new short[LENGTH];
        sc = new short[LENGTH];
        sr = new short[LENGTH];
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ic = new int[LENGTH];
        ir = new int[LENGTH];
        la = new long[LENGTH];
        lb = new long[LENGTH];
        lc = new long[LENGTH];
        lr = new long[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ba[i] = (byte) RD.nextInt();
            bb[i] = (byte) RD.nextInt();
            bc[i] = (byte) RD.nextInt();
            sa[i] = (short) RD.nextInt();
            sb[i] = (short) RD.nextInt();
            sc[i] = (short) RD.nextInt();
            ia[i] = RD.nextInt();
            ib[i] = RD.nextInt();
            ic[i] = RD.nextInt();
            la[i] = RD.nextLong();
            lb[i] = RD.nextLong();
            lc[i] = RD.nextLong();
        }
    }

    @Test
    @IR(counts = {IRNode.VMLA, "> 0"})
    public static void testByteMulAdd() {
        for (int i = 0; i < LENGTH; i += B_SPECIES.length()) {
            ByteVector av = ByteVector.fromArray(B_SPECIES, ba, i);
            ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, i);
            ByteVector cv = ByteVector.fromArray(B_SPECIES, bc, i);
            av.add(bv.mul(cv)).intoArray(br, i);
        }
    }

    @Run(test = "testByteMulAdd")
    public static void testByteMulAdd_runner() {
        testByteMulAdd();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals((byte) (ba[i] + bb[i] * bc[i]), br[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.VMLA, "> 0"})
    public static void testShortMulAdd() {
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector av = ShortVector.fromArray(S_SPECIES, sa, i);
            ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, i);
            ShortVector cv = ShortVector.fromArray(S_SPECIES, sc, i);
            av.add(bv.mul(cv)).intoArray(sr, i);
        }
    }

    @Run(test = "testShortMulAdd")
    public static void testShortMulAdd_runner() {
        testShortMulAdd();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals((short) (sa[i] + sb[i] * sc[i]), sr[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.VMLA, "> 0"})
    public static void testIntMulAdd() {
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector av = IntVector.fromArray(I_SPECIES, ia, i);
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, i);
            IntVector cv = IntVector.fromArray(I_SPECIES, ic, i);
            av.add(bv.mul(cv)).intoArray(ir, i);
        }
    }

    @Run(test = "testIntMulAdd")
    public static void testIntMulAdd_runner() {
        testIntMulAdd();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals((ia[i] + ib[i] * ic[i]), ir[i]);
        }
    }

    @Test
    @IR(applyIf = {"UseSVE", " > 0"}, counts = {IRNode.VMLA, "> 0"})
    public static void testLongMulAdd() {
        for (int i = 0; i < LENGTH; i += L_SPECIES.length()) {
            LongVector av = LongVector.fromArray(L_SPECIES, la, i);
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, i);
            LongVector cv = LongVector.fromArray(L_SPECIES, lc, i);
            av.add(bv.mul(cv)).intoArray(lr, i);
        }
    }

    @Run(test = "testLongMulAdd")
    public static void testLongMulAdd_runner() {
        testLongMulAdd();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals((la[i] + lb[i] * lc[i]), lr[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.VMLS, "> 0"})
    public static void testByteMulSub() {
        for (int i = 0; i < LENGTH; i += B_SPECIES.length()) {
            ByteVector av = ByteVector.fromArray(B_SPECIES, ba, i);
            ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, i);
            ByteVector cv = ByteVector.fromArray(B_SPECIES, bc, i);
            av.sub(bv.mul(cv)).intoArray(br, i);
        }
    }

    @Run(test = "testByteMulSub")
    public static void testByteMulSub_runner() {
        testByteMulSub();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals((byte) (ba[i] - bb[i] * bc[i]), br[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.VMLS, "> 0"})
    public static void testShortMulSub() {
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector av = ShortVector.fromArray(S_SPECIES, sa, i);
            ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, i);
            ShortVector cv = ShortVector.fromArray(S_SPECIES, sc, i);
            av.sub(bv.mul(cv)).intoArray(sr, i);
        }
    }

    @Run(test = "testShortMulSub")
    public static void testShortMulSub_runner() {
        testShortMulSub();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals((short) (sa[i] - sb[i] * sc[i]), sr[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.VMLS, "> 0"})
    public static void testIntMulSub() {
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector av = IntVector.fromArray(I_SPECIES, ia, i);
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, i);
            IntVector cv = IntVector.fromArray(I_SPECIES, ic, i);
            av.sub(bv.mul(cv)).intoArray(ir, i);
        }
    }

    @Run(test = "testIntMulSub")
    public static void testIntMulSub_runner() {
        testIntMulSub();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals((ia[i] - ib[i] * ic[i]), ir[i]);
        }
    }

    @Test
    @IR(applyIf = {"UseSVE", " > 0"}, counts = {IRNode.VMLS, "> 0"})
    public static void testLongMulSub() {
        for (int i = 0; i < LENGTH; i += L_SPECIES.length()) {
            LongVector av = LongVector.fromArray(L_SPECIES, la, i);
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, i);
            LongVector cv = LongVector.fromArray(L_SPECIES, lc, i);
            av.sub(bv.mul(cv)).intoArray(lr, i);
        }
    }

    @Run(test = "testLongMulSub")
    public static void testLongMulSub_runner() {
        testLongMulSub();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals((la[i] - lb[i] * lc[i]), lr[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
