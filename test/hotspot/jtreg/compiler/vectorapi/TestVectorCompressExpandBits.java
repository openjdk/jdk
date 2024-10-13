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

package compiler.vectorapi;

import compiler.lib.ir_framework.*;

import java.util.Random;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8301012
 * @library /test/lib /
 * @requires os.arch == "aarch64" & vm.cpu.features ~= ".*sve2.*" & vm.cpu.features ~= ".*svebitperm.*"
 * @summary [vectorapi]: Intrinsify CompressBitsV/ExpandBitsV and add the AArch64 SVE backend implementation
 * @modules jdk.incubator.vector
 * @run driver compiler.vectorapi.TestVectorCompressExpandBits
 */

public class TestVectorCompressExpandBits {
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_PREFERRED;

    private static int LENGTH = 1024;
    private static final Random RD = Utils.getRandomInstance();

    private static int[] ia;
    private static int[] ib;
    private static int[] ir;
    private static long[] la;
    private static long[] lb;
    private static long[] lr;

    static {
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ir = new int[LENGTH];
        la = new long[LENGTH];
        lb = new long[LENGTH];
        lr = new long[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ia[i] = RD.nextInt(25);
            ib[i] = RD.nextInt(25);
            la[i] = RD.nextLong(25);
            lb[i] = RD.nextLong(25);
        }
    }

    // Test for vectorized Integer.compress operation in SVE2
    @Test
    @IR(counts = {IRNode.COMPRESS_BITS_VI, "> 0"})
    public static void testIntCompress() {
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector av = IntVector.fromArray(I_SPECIES, ia, i);
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, i);
            av.lanewise(VectorOperators.COMPRESS_BITS, bv).intoArray(ir, i);
        }
    }

    @Run(test = "testIntCompress")
    public static void testIntCompress_runner() {
        testIntCompress();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(Integer.compress(ia[i], ib[i]), ir[i]);
        }
    }

    // Test for vectorized Integer.expand operation in SVE2
    @Test
    @IR(counts = {IRNode.EXPAND_BITS_VI, "> 0"})
    public static void testIntExpand() {
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector av = IntVector.fromArray(I_SPECIES, ia, i);
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, i);
            av.lanewise(VectorOperators.EXPAND_BITS, bv).intoArray(ir, i);
        }
    }

    @Run(test = "testIntExpand")
    public static void testIntExpand_runner() {
        testIntExpand();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(Integer.expand(ia[i], ib[i]), ir[i]);
        }
    }

    // Test for vectorized Long.compress operation in SVE2
    @Test
    @IR(counts = {IRNode.COMPRESS_BITS_VL, "> 0"})
    public static void testLongCompress() {
        for (int i = 0; i < LENGTH; i += L_SPECIES.length()) {
            LongVector av = LongVector.fromArray(L_SPECIES, la, i);
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, i);
            av.lanewise(VectorOperators.COMPRESS_BITS, bv).intoArray(lr, i);
        }
    }

    @Run(test = "testLongCompress")
    public static void testLongCompress_runner() {
        testLongCompress();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(Long.compress(la[i], lb[i]), lr[i]);
        }
    }

    // Test for vectorized Long.expand operation in SVE2
    @Test
    @IR(counts = {IRNode.EXPAND_BITS_VL, "> 0"})
    public static void testLongExpand() {
        for (int i = 0; i < LENGTH; i += L_SPECIES.length()) {
            LongVector av = LongVector.fromArray(L_SPECIES, la, i);
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, i);
            av.lanewise(VectorOperators.EXPAND_BITS, bv).intoArray(lr, i);
        }
    }

    @Run(test = "testLongExpand")
    public static void testLongExpand_runner() {
        testLongExpand();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(Long.expand(la[i], lb[i]), lr[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector",
                                   "-XX:UseSVE=2");
    }
}
