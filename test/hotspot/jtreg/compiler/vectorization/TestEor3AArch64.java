/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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
 * @bug 8293488
 * @summary Test EOR3 Neon/SVE2 instruction for aarch64 SHA3 extension
 * @library /test/lib /
 * @requires os.arch == "aarch64"
 * @run driver compiler.vectorization.TestEor3AArch64
 */

public class TestEor3AArch64 {

    private static final int LENGTH = 2048;
    private static final Random RD = Utils.getRandomInstance();

    private static int[] ia;
    private static int[] ib;
    private static int[] ic;
    private static int[] ir;

    private static long[] la;
    private static long[] lb;
    private static long[] lc;
    private static long[] lr;

    static {
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ic = new int[LENGTH];
        ir = new int[LENGTH];

        la = new long[LENGTH];
        lb = new long[LENGTH];
        lc = new long[LENGTH];
        lr = new long[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ia[i] = RD.nextInt(30);
            ib[i] = RD.nextInt(30);
            ic[i] = RD.nextInt(30);

            la[i] = RD.nextLong(30);
            lb[i] = RD.nextLong(30);
            lc[i] = RD.nextLong(30);
        }
    }

    // Test for eor3 Neon and SVE2 instruction for integers
    @Test
    @IR(counts = {IRNode.XOR3_NEON, "> 0"}, applyIf = {"MaxVectorSize", "16"}, applyIfCPUFeature = {"sha3", "true"})
    @IR(counts = {IRNode.XOR3_SVE, "> 0"}, applyIfAnd = {"UseSVE", "2", "MaxVectorSize", "> 16"})
    public static void testIntEor3() {
        for (int i = 0; i < LENGTH; i++) {
            ir[i] = ia[i] ^ ib[i] ^ ic[i];
        }
    }

    @Run(test = "testIntEor3")
    public static void testIntEor3_runner() {
        testIntEor3();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals((ia[i] ^ ib[i] ^ ic[i]), ir[i]);
        }
    }

    // Test for eor3 Neon and SVE2 instruction for longs
    @Test
    @IR(counts = {IRNode.XOR3_NEON, "> 0"}, applyIf = {"MaxVectorSize", "16"}, applyIfCPUFeature = {"sha3", "true"})
    @IR(counts = {IRNode.XOR3_SVE, "> 0"}, applyIfAnd = {"UseSVE", "2", "MaxVectorSize", "> 16"})
    public static void testLongEor3() {
        for (int i = 0; i < LENGTH; i++) {
            lr[i] = la[i] ^ lb[i] ^ lc[i];
        }
    }

    @Run(test = "testLongEor3")
    public static void testLongEor3_runner() {
        testLongEor3();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals((la[i] ^ lb[i] ^ lc[i]), lr[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework.run();
    }
}
