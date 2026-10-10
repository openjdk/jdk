/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.Check;
import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.CheckAt;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/*
 * @test
 * @bug 8393068
 * @key randomness
 * @summary Optimize vector byte shift operations using x86 GFNI instructions
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @requires vm.cpu.features ~= ".*gfni.*"
 * @run driver ${test.main.class}
 */

public class TestGFNIUniformByteShift {
    static final VectorSpecies<Byte> B128 = ByteVector.SPECIES_128;
    static final VectorSpecies<Byte> B256 = ByteVector.SPECIES_256;
    static final VectorSpecies<Byte> B512 = ByteVector.SPECIES_512;

    static final int LENGTH = 256;
    static final byte[] SRC = new byte[LENGTH];
    static final byte[][] LSHL = new byte[8][LENGTH];
    static final byte[][] ASHR = new byte[8][LENGTH];
    static final byte[][] LSHR = new byte[8][LENGTH];

    static final Generators RD = Generators.G;

    static {
        Generator<Integer> bytes = RD.ints();
        for (int i = 0; i < LENGTH; i++) {
            SRC[i] = bytes.next().byteValue();
        }
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }

    @Test
    @IR(counts = {IRNode.X86_VSHIFT_B_GFNI, ">= 3"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeatureAnd = {"gfni", "true", "avx", "true"})
    public static void test128() {
        for (int s = 0; s < 8; s++) {
            for (int i = 0; i < LENGTH; i += B128.length()) {
                ByteVector v = ByteVector.fromArray(B128, SRC, i);
                v.lanewise(VectorOperators.LSHL, s).intoArray(LSHL[s], i);
                v.lanewise(VectorOperators.ASHR, s).intoArray(ASHR[s], i);
                v.lanewise(VectorOperators.LSHR, s).intoArray(LSHR[s], i);
            }
        }
    }

    @Check(test = "test128", when = CheckAt.COMPILED)
    public static void check128() {
        check();
    }

    @Test
    @IR(counts = {IRNode.X86_VSHIFT_B_GFNI, ">= 3"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeatureAnd = {"gfni", "true", "avx2", "true"})
    public static void test256() {
        for (int s = 0; s < 8; s++) {
            for (int i = 0; i < LENGTH; i += B256.length()) {
                ByteVector v = ByteVector.fromArray(B256, SRC, i);
                v.lanewise(VectorOperators.LSHL, s).intoArray(LSHL[s], i);
                v.lanewise(VectorOperators.ASHR, s).intoArray(ASHR[s], i);
                v.lanewise(VectorOperators.LSHR, s).intoArray(LSHR[s], i);
            }
        }
    }

    @Check(test = "test256", when = CheckAt.COMPILED)
    public static void check256() {
        check();
    }

    @Test
    @IR(counts = {IRNode.X86_VSHIFT_B_GFNI, ">= 3"},
        phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeatureAnd = {"gfni", "true", "avx512bw", "true"})
    public static void test512() {
        for (int s = 0; s < 8; s++) {
            for (int i = 0; i < LENGTH; i += B512.length()) {
                ByteVector v = ByteVector.fromArray(B512, SRC, i);
                v.lanewise(VectorOperators.LSHL, s).intoArray(LSHL[s], i);
                v.lanewise(VectorOperators.ASHR, s).intoArray(ASHR[s], i);
                v.lanewise(VectorOperators.LSHR, s).intoArray(LSHR[s], i);
            }
        }
    }

    @Check(test = "test512", when = CheckAt.COMPILED)
    public static void check512() {
        check();
    }

    static void check() {
        for (int s = 0; s < 8; s++) {
            byte[] expL = new byte[LENGTH];
            byte[] expA = new byte[LENGTH];
            byte[] expU = new byte[LENGTH];
            for (int i = 0; i < LENGTH; i++) {
                int x = SRC[i];
                expL[i] = (byte) (x << s);
                expA[i] = (byte) (x >> s);
                expU[i] = (byte) ((x & 0xff) >>> s);
            }
            Verify.checkEQ(LSHL[s], expL);
            Verify.checkEQ(ASHR[s], expA);
            Verify.checkEQ(LSHR[s], expU);
        }
    }
}
