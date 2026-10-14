/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8301012 8391921
 * @key randomness
 * @library /test/lib /
 * @requires (os.arch == "aarch64" & vm.cpu.features ~= ".*sve2.*" & vm.cpu.features ~= ".*svebitperm.*") |
 *           (os.arch == "riscv64" & vm.cpu.features ~= ".*rvv.*")
 * @summary [vectorapi]: Test intrinsification of CompressBitsV/ExpandBitsV
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
    private static boolean[] laneMask;

    static {
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ir = new int[LENGTH];
        la = new long[LENGTH];
        lb = new long[LENGTH];
        lr = new long[LENGTH];
        laneMask = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ia[i] = RD.nextInt(25);
            ib[i] = RD.nextInt(25);
            la[i] = RD.nextLong(25);
            lb[i] = RD.nextLong(25);
            laneMask[i] = RD.nextBoolean();
        }
    }

    // Test for vectorized Integer.compress operation
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

    @Test
    @IR(counts = {IRNode.COMPRESS_BITS_VI, "> 0"})
    @IR(counts = {IRNode.RISCV_VCOMPRESS_BITS_MASKED, "> 0"}, applyIfPlatform = {"riscv64", "true"})
    public static void testIntCompressMasked() {
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector av = IntVector.fromArray(I_SPECIES, ia, i);
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, i);
            VectorMask<Integer> vm = VectorMask.fromArray(I_SPECIES, laneMask, i);
            av.lanewise(VectorOperators.COMPRESS_BITS, bv, vm).intoArray(ir, i);
        }
    }

    @Run(test = "testIntCompressMasked")
    public static void testIntCompressMasked_runner() {
        testIntCompressMasked();
        for (int i = 0; i < LENGTH; i++) {
            int expected = laneMask[i] ? Integer.compress(ia[i], ib[i]) : ia[i];
            Asserts.assertEquals(expected, ir[i]);
        }
    }

    // Test for vectorized Integer.expand operation
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

    @Test
    @IR(counts = {IRNode.EXPAND_BITS_VI, "> 0"})
    @IR(counts = {IRNode.RISCV_VEXPAND_BITS_MASKED, "> 0"}, applyIfPlatform = {"riscv64", "true"})
    public static void testIntExpandMasked() {
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector av = IntVector.fromArray(I_SPECIES, ia, i);
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, i);
            VectorMask<Integer> vm = VectorMask.fromArray(I_SPECIES, laneMask, i);
            av.lanewise(VectorOperators.EXPAND_BITS, bv, vm).intoArray(ir, i);
        }
    }

    @Run(test = "testIntExpandMasked")
    public static void testIntExpandMasked_runner() {
        testIntExpandMasked();
        for (int i = 0; i < LENGTH; i++) {
            int expected = laneMask[i] ? Integer.expand(ia[i], ib[i]) : ia[i];
            Asserts.assertEquals(expected, ir[i]);
        }
    }

    // Test for vectorized Long.compress operation
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

    @Test
    @IR(counts = {IRNode.COMPRESS_BITS_VL, "> 0"})
    @IR(counts = {IRNode.RISCV_VCOMPRESS_BITS_MASKED, "> 0"}, applyIfPlatform = {"riscv64", "true"})
    public static void testLongCompressMasked() {
        for (int i = 0; i < LENGTH; i += L_SPECIES.length()) {
            LongVector av = LongVector.fromArray(L_SPECIES, la, i);
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, i);
            VectorMask<Long> vm = VectorMask.fromArray(L_SPECIES, laneMask, i);
            av.lanewise(VectorOperators.COMPRESS_BITS, bv, vm).intoArray(lr, i);
        }
    }

    @Run(test = "testLongCompressMasked")
    public static void testLongCompressMasked_runner() {
        testLongCompressMasked();
        for (int i = 0; i < LENGTH; i++) {
            long expected = laneMask[i] ? Long.compress(la[i], lb[i]) : la[i];
            Asserts.assertEquals(expected, lr[i]);
        }
    }

    // Test for vectorized Long.expand operation
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

    @Test
    @IR(counts = {IRNode.EXPAND_BITS_VL, "> 0"})
    @IR(counts = {IRNode.RISCV_VEXPAND_BITS_MASKED, "> 0"}, applyIfPlatform = {"riscv64", "true"})
    public static void testLongExpandMasked() {
        for (int i = 0; i < LENGTH; i += L_SPECIES.length()) {
            LongVector av = LongVector.fromArray(L_SPECIES, la, i);
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, i);
            VectorMask<Long> vm = VectorMask.fromArray(L_SPECIES, laneMask, i);
            av.lanewise(VectorOperators.EXPAND_BITS, bv, vm).intoArray(lr, i);
        }
    }

    @Run(test = "testLongExpandMasked")
    public static void testLongExpandMasked_runner() {
        testLongExpandMasked();
        for (int i = 0; i < LENGTH; i++) {
            long expected = laneMask[i] ? Long.expand(la[i], lb[i]) : la[i];
            Asserts.assertEquals(expected, lr[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector");
        if (Platform.isAArch64()) {
            testFramework.addFlags("-XX:UseSVE=2");
        }
        testFramework.start();
    }
}
