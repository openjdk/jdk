/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8320646
 * @summary Auto-vectorize Float.floatToFloat16, Float.float16ToFloat APIs, with NaN
 * @requires vm.compiler2.enabled
 * @requires (os.arch == "riscv64" & vm.cpu.features ~= ".*zvfh.*")
 * @library /test/lib /
 * @run driver compiler.vectorization.TestFloatConversionsVectorNaN
 */

package compiler.vectorization;

import java.util.HexFormat;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

public class TestFloatConversionsVectorNaN {
    private static final int ARRLEN = 1024;
    private static final int ITERS  = 11000;
    private static float  [] finp;
    private static short  [] sout;
    private static short  [] sinp;
    private static float  [] fout;

    public static void main(String args[]) {
        TestFramework.runWithFlags("-XX:-TieredCompilation",
                                   "-XX:CompileThresholdScaling=0.3");
        System.out.println("PASSED");
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_F2HF, IRNode.VECTOR_SIZE + "min(max_float, max_short)", "> 0"})
    public void test_float_float16(short[] sout, float[] finp) {
        for (int i = 0; i < finp.length; i++) {
            sout[i] = Float.floatToFloat16(finp[i]);
        }
    }

    @Run(test = {"test_float_float16"}, mode = RunMode.STANDALONE)
    public void kernel_test_float_float16() {
        int errno = 0;
        finp = new float[ARRLEN];
        sout = new short[ARRLEN];

        // Setup
        for (int i = 0; i < ARRLEN; i++) {
            if (i%39 == 0) {
                int x = 0x7f800000 + ((i/39) << 13);
                x = (i%2 == 0) ? x : (x | 0x80000000);
                finp[i] = Float.intBitsToFloat(x);
            } else {
                finp[i] = (float) i * 1.4f;
            }
        }
        int ranges[][] = {
            {128, 64},
            {256, 19},
            {384-19, 19},
            {512-19, 17},
            {640+19, 19},
            {768+19, 32},
            {896-19, 32}
        };
        for (int range[] : ranges) {
            int start = range[0];
            int offset = range[1];
            for (int i = start; i < start+offset; i++) {
                int x = 0x7f800000 + (i << 13);
                finp[i] = Float.intBitsToFloat(x);
            }
        }

        // Test
        for (int i = 0; i < ITERS; i++) {
            test_float_float16(sout, finp);
        }

        // Verifying the result
        for (int i = 0; i < ARRLEN; i++) {
            errno += assertEquals(i, finp[i], Float.floatToFloat16(finp[i]), sout[i]);
        }

        if (errno > 0) {
            throw new RuntimeException("errors occur");
        }
    }

    static int assertEquals(int idx, float f, short expected, short actual) {
        HexFormat hf = HexFormat.of();
        String msg = "floatToFloat16 wrong result: idx: " + idx + ", \t" + f +
                     ",\t expected: " + hf.toHexDigits(expected) +
                     ",\t actual: " + hf.toHexDigits(actual);
        if ((expected & 0x7c00) != 0x7c00) {
            if (expected != actual) {
                System.err.println(msg);
                return 1;
            }
        } else if ((expected & 0x3ff) != 0) {
            if (((actual & 0x7c00) != 0x7c00) || (actual & 0x3ff) == 0) {
                System.err.println(msg);
                return 1;
            }
        }
        return 0;
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_HF2F, IRNode.VECTOR_SIZE + "min(max_float, max_short)", "> 0"})
    public void test_float16_float(float[] fout, short[] sinp) {
        for (int i = 0; i < sinp.length; i++) {
            fout[i] = Float.float16ToFloat(sinp[i]);
        }
    }

    @Run(test = {"test_float16_float"}, mode = RunMode.STANDALONE)
    public void kernel_test_float16_float() {
        int errno = 0;
        sinp = new short[ARRLEN];
        fout = new float[ARRLEN];

        // Setup
        for (int i = 0; i < ARRLEN; i++) {
            if (i%39 == 0) {
                int x = 0x7c00 + i;
                x = (i%2 == 0) ? x : (x | 0x8000);
                sinp[i] = (short)x;
            } else {
                sinp[i] = (short)i;
            }
        }

        int ranges[][] = {
            {128, 64},
            {256, 19},
            {384-19, 19},
            {512-19, 17},
            {640+19, 19},
            {768+19, 32},
            {896-19, 32}
        };
        for (int range[] : ranges) {
            int start = range[0];
            int offset = range[1];
            for (int i = start; i < start+offset; i++) {
                int x = 0x7c00 + i;
                x = (i%2 == 0) ? x : (x | 0x8000);
                sinp[i] = (short)x;
            }
        }

        // Test
        for (int i = 0; i < ITERS; i++) {
            test_float16_float(fout, sinp);
        }

        // Verifying the result
        for (int i = 0; i < ARRLEN; i++) {
            errno += assertEquals(i, sinp[i], Float.float16ToFloat(sinp[i]), fout[i]);
        }

        if (errno > 0) {
            throw new RuntimeException("errors occur");
        }
    }

    static int assertEquals(int idx, short s, float expected, float actual) {
        String msg = "float16ToFloat wrong result: idx: " + idx + ", \t" + s +
                     ",\t expected: " + expected + ",\t" + Integer.toHexString(Float.floatToIntBits(expected)) +
                     ",\t actual: " + actual + ",\t" + Integer.toHexString(Float.floatToIntBits(actual));
        if (!Float.isNaN(expected) || !Float.isNaN(actual)) {
            if (expected != actual) {
                System.err.println(msg);
                return 1;
            }
        }
        return 0;
    }
}
