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

/*
 * @test
 * @bug 8393511
 * @key randomness
 * @summary x86 vector count-trailing-zeros uses vpopcnt when BITALG or VPOPCNTDQ is available
 * @requires vm.compiler2.enabled
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.vectorapi;

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public class TestVectorCountTrailingZeros {
    public static final VectorSpecies<Byte> BSP = ByteVector.SPECIES_PREFERRED;
    public static final VectorSpecies<Short> SSP = ShortVector.SPECIES_PREFERRED;
    public static final VectorSpecies<Integer> ISP = IntVector.SPECIES_PREFERRED;
    public static final VectorSpecies<Long> LSP = LongVector.SPECIES_PREFERRED;

    private static final int LENGTH = 1024;

    private static final byte[] srcB = new byte[LENGTH];
    private static final byte[] dstB = new byte[LENGTH];
    private static final short[] srcS = new short[LENGTH];
    private static final short[] dstS = new short[LENGTH];
    private static final int[] srcI = new int[LENGTH];
    private static final int[] dstI = new int[LENGTH];
    private static final long[] srcL = new long[LENGTH];
    private static final long[] dstL = new long[LENGTH];

    static {
        Generator<Integer> ints = Generators.G.ints();
        Generator<Long> longs = Generators.G.longs();
        for (int i = 0; i < LENGTH; i++) {
            srcB[i] = ints.next().byteValue();
            srcS[i] = ints.next().shortValue();
        }
        Generators.G.fill(ints, srcI);
        Generators.G.fill(longs, srcL);
        // A zero lane and a single-bit lane in every vector, including the all-ones case.
        for (int i = 0; i < LENGTH; i += BSP.length()) {
            srcB[i] = 0;
            if (1 < BSP.length()) {
                srcB[i + 1] = (byte) (1 << (i % 7));
            }
            if (2 < BSP.length()) {
                srcB[i + 2] = (byte) 0xFF;
            }
        }
        for (int i = 0; i < LENGTH; i += SSP.length()) {
            srcS[i] = 0;
            if (1 < SSP.length()) {
                srcS[i + 1] = (short) (1 << (i % 15));
            }
            if (2 < SSP.length()) {
                srcS[i + 2] = (short) 0xFFFF;
            }
        }
        for (int i = 0; i < LENGTH; i += ISP.length()) {
            srcI[i] = 0;
            if (1 < ISP.length()) {
                srcI[i + 1] = 1 << (i % 31);
            }
            if (2 < ISP.length()) {
                srcI[i + 2] = -1;
            }
        }
        for (int i = 0; i < LENGTH; i += LSP.length()) {
            srcL[i] = 0L;
            if (1 < LSP.length()) {
                srcL[i + 1] = 1L << (i % 63);
            }
            if (2 < LSP.length()) {
                srcL[i + 2] = -1L;
            }
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    @Test
    @IR(counts = {IRNode.X86_VCOUNT_TRAILING_ZEROS_POPCNT_EVEX, " >0 "},
        failOn = {IRNode.X86_VCOUNT_TRAILING_ZEROS_BYTE_REG_EVEX},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeatureAnd = {"avx512_bitalg", "true", "avx512vl", "true"})
    @IR(failOn = {IRNode.X86_VCOUNT_TRAILING_ZEROS_POPCNT_EVEX},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature = {"avx512_bitalg", "false"})
    public static void testByte() {
        for (int i = 0; i < LENGTH; i += BSP.length()) {
            ByteVector.fromArray(BSP, srcB, i)
                    .lanewise(VectorOperators.TRAILING_ZEROS_COUNT)
                    .intoArray(dstB, i);
        }
    }

    @Check(test = "testByte")
    public static void checkByte() {
        int lanes = BSP.length();
        Verify.checkEQ(LENGTH % lanes, 0);
        for (int i = 0; i < LENGTH; i++) {
            byte a = srcB[i];
            byte expected = (byte) (a != 0 ? Integer.numberOfTrailingZeros(a) : 8);
            Verify.checkEQ(dstB[i], expected);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VCOUNT_TRAILING_ZEROS_POPCNT_EVEX, " >0 "},
        failOn = {IRNode.X86_VCOUNT_TRAILING_ZEROS_SHORT_REG_EVEX},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeatureAnd = {"avx512_bitalg", "true", "avx512vl", "true"})
    @IR(failOn = {IRNode.X86_VCOUNT_TRAILING_ZEROS_POPCNT_EVEX},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature = {"avx512_bitalg", "false"})
    public static void testShort() {
        for (int i = 0; i < LENGTH; i += SSP.length()) {
            ShortVector.fromArray(SSP, srcS, i)
                    .lanewise(VectorOperators.TRAILING_ZEROS_COUNT)
                    .intoArray(dstS, i);
        }
    }

    @Check(test = "testShort")
    public static void checkShort() {
        int lanes = SSP.length();
        Verify.checkEQ(LENGTH % lanes, 0);
        for (int i = 0; i < LENGTH; i++) {
            short a = srcS[i];
            short expected = (short) (a != 0 ? Integer.numberOfTrailingZeros(a) : 16);
            Verify.checkEQ(dstS[i], expected);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VCOUNT_TRAILING_ZEROS_POPCNT_EVEX, " >0 "},
        failOn = {IRNode.X86_VCOUNT_TRAILING_ZEROS_REG_EVEX},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeatureAnd = {"avx512_vpopcntdq", "true", "avx512vl", "true"})
    @IR(failOn = {IRNode.X86_VCOUNT_TRAILING_ZEROS_POPCNT_EVEX},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature = {"avx512_vpopcntdq", "false"})
    public static void testInt() {
        for (int i = 0; i < LENGTH; i += ISP.length()) {
            IntVector.fromArray(ISP, srcI, i)
                    .lanewise(VectorOperators.TRAILING_ZEROS_COUNT)
                    .intoArray(dstI, i);
        }
    }

    @Check(test = "testInt")
    public static void checkInt() {
        int lanes = ISP.length();
        Verify.checkEQ(LENGTH % lanes, 0);
        for (int i = 0; i < LENGTH; i++) {
            Verify.checkEQ(dstI[i], Integer.numberOfTrailingZeros(srcI[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VCOUNT_TRAILING_ZEROS_POPCNT_EVEX, " >0 "},
        failOn = {IRNode.X86_VCOUNT_TRAILING_ZEROS_REG_EVEX},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeatureAnd = {"avx512_vpopcntdq", "true", "avx512vl", "true"})
    @IR(failOn = {IRNode.X86_VCOUNT_TRAILING_ZEROS_POPCNT_EVEX},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature = {"avx512_vpopcntdq", "false"})
    public static void testLong() {
        for (int i = 0; i < LENGTH; i += LSP.length()) {
            LongVector.fromArray(LSP, srcL, i)
                    .lanewise(VectorOperators.TRAILING_ZEROS_COUNT)
                    .intoArray(dstL, i);
        }
    }

    @Check(test = "testLong")
    public static void checkLong() {
        int lanes = LSP.length();
        Verify.checkEQ(LENGTH % lanes, 0);
        for (int i = 0; i < LENGTH; i++) {
            Verify.checkEQ(dstL[i], (long) Long.numberOfTrailingZeros(srcL[i]));
        }
    }
}
