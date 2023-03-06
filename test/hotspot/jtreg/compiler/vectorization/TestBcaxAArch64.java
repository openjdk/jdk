/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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

package compiler.vectorization;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8303553
 * @summary AArch64: Add BCAX backend rule
 * @library /test/lib /
 * @requires os.arch == "aarch64"
 * @run driver compiler.vectorization.TestBcaxAArch64
 */

public class TestBcaxAArch64 {
    private static final int LENGTH = 2048;
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
            ba[i] = (byte) RD.nextInt(30);
            bb[i] = (byte) RD.nextInt(30);
            bc[i] = (byte) RD.nextInt(30);

            sa[i] = (short) RD.nextInt(30);
            sb[i] = (short) RD.nextInt(30);
            sc[i] = (short) RD.nextInt(30);

            ia[i] = RD.nextInt(30);
            ib[i] = RD.nextInt(30);
            ic[i] = RD.nextInt(30);

            la[i] = RD.nextLong(30);
            lb[i] = RD.nextLong(30);
            lc[i] = RD.nextLong(30);
        }
    }

    @Test
    @IR(counts = {IRNode.VBCAX_I_NEON, "> 0"}, applyIf = {"MaxVectorSize", "<= 16"}, applyIfCPUFeature = {"sha3", "true"})
    @IR(counts = {IRNode.VBCAX_I_SVE, "> 0"}, applyIfAnd = {"UseSVE", "> 1", "MaxVectorSize", "> 16"})
    public static void testByteBCAX() {
        for (int i = 0; i < LENGTH; i++) {
            br[i] = (byte) (ba[i] ^ (bb[i] & (~bc[i])));
        }
    }

    @Run(test = "testByteBCAX")
    public static void testByteBCAX_runner() {
        testByteBCAX();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals((byte)(ba[i] ^ (bb[i] & (~bc[i]))), br[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.VBCAX_I_NEON, "> 0"}, applyIf = {"MaxVectorSize", "<= 16"}, applyIfCPUFeature = {"sha3", "true"})
    @IR(counts = {IRNode.VBCAX_I_SVE, "> 0"}, applyIfAnd = {"UseSVE", "> 1", "MaxVectorSize", "> 16"})
    public static void testShortBCAX() {
        for (int i = 0; i < LENGTH; i++) {
            sr[i] = (short) (sa[i] ^ (sb[i] & (~sc[i])));
        }
    }

    @Run(test = "testShortBCAX")
    public static void testShortBCAX_runner() {
        testShortBCAX();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals((short)(sa[i] ^ (sb[i] & (~sc[i]))), sr[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.VBCAX_I_NEON, "> 0"}, applyIf = {"MaxVectorSize", "<= 16"}, applyIfCPUFeature = {"sha3", "true"})
    @IR(counts = {IRNode.VBCAX_I_SVE, "> 0"}, applyIfAnd = {"UseSVE", "> 1", "MaxVectorSize", "> 16"})
    public static void testIntBCAX() {
        for (int i = 0; i < LENGTH; i++) {
            ir[i] = ia[i] ^ (ib[i] & (~ic[i]));
        }
    }

    @Run(test = "testIntBCAX")
    public static void testIntBCAX_runner() {
        testIntBCAX();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(ia[i] ^ (ib[i] & (~ic[i])), ir[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.VBCAX_L_NEON, "> 0"}, applyIf = {"MaxVectorSize", "16"}, applyIfCPUFeature = {"sha3", "true"})
    @IR(counts = {IRNode.VBCAX_L_SVE, "> 0"}, applyIfAnd = {"UseSVE", "> 1", "MaxVectorSize", "> 16"})
    public static void testLongBCAX() {
        for (int i = 0; i < LENGTH; i++) {
            lr[i] = la[i] ^ (lb[i] & (~lc[i]));
        }
    }

    @Run(test = "testLongBCAX")
    public static void testLongBCAX_runner() {
        testLongBCAX();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(la[i] ^ (lb[i] & (~lc[i])), lr[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework.run();
    }
}
