/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
* @bug 8364305
* @summary Test scalar float/double to integral cast
* @requires vm.compiler2.enabled
* @library /test/lib /
* @run main/othervm/native compiler.floatingpoint.ScalarFPtoIntCastTest
*/

package compiler.floatingpoint;

import compiler.lib.ir_framework.*;
import compiler.lib.generators.Generator;
import static compiler.lib.generators.Generators.G;
import compiler.lib.verify.Verify;

public class ScalarFPtoIntCastTest {
    private static final int COUNT = 16;

    private float[] float_arr;
    private double[] double_arr;
    private long[] long_float_arr;
    private long[] long_double_arr;
    private int[] int_float_arr;
    private int[] int_double_arr;
    private short[] short_float_arr;
    private short[] short_double_arr;
    private byte[] byte_float_arr;
    private byte[] byte_double_arr;

    private static final Generator<Float> genF = G.floats();
    private static final Generator<Double> genD = G.doubles();

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.start();
    }

    public ScalarFPtoIntCastTest() {
        long_float_arr = new long[COUNT];
        long_double_arr = new long[COUNT];
        int_float_arr = new int[COUNT];
        int_double_arr = new int[COUNT];
        short_float_arr = new short[COUNT];
        short_double_arr = new short[COUNT];
        byte_float_arr = new byte[COUNT];
        byte_double_arr = new byte[COUNT];
        float_arr = new float[COUNT];
        double_arr = new double[COUNT];

        G.fill(genF, float_arr);
        G.fill(genD, double_arr);
        for (int i = 0; i < COUNT; i++) {
            long_float_arr[i] = (long) float_arr[i];
            long_double_arr[i] = (long) double_arr[i];
            int_float_arr[i] = (int) float_arr[i];
            int_double_arr[i] = (int) double_arr[i];
            short_float_arr[i] = (short) float_arr[i];
            short_double_arr[i] = (short) double_arr[i];
            byte_float_arr[i] = (byte) float_arr[i];
            byte_double_arr[i] = (byte) double_arr[i];
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_F2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_F2I, "> 0"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_F2I_AVX10_2, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void float2int() {
        for (int i = 0; i < COUNT; i++) {
            float float_val = float_arr[i];
            int computed = (int) float_val;
            int expected = int_float_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_F2L, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_F2L, "> 0"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_F2L_AVX10_2, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void float2long() {
        for (int i = 0; i < COUNT; i++) {
            float float_val = float_arr[i];
            long computed = (long) float_val;
            long expected = long_float_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_F2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_F2I, "> 0"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_F2I_AVX10_2, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void float2short() {
        for (int i = 0; i < COUNT; i++) {
            float float_val = float_arr[i];
            short computed = (short) float_val;
            short expected = short_float_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_F2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_F2I, "> 0"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_F2I_AVX10_2, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void float2byte() {
        for (int i = 0; i < COUNT; i++) {
            float float_val = float_arr[i];
            byte computed = (byte) float_val;
            byte expected = byte_float_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_D2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_D2I, "> 0"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_D2I_AVX10_2, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void double2int() {
        for (int i = 0; i < COUNT; i++) {
            double double_val = double_arr[i];
            int computed = (int) double_val;
            int expected = int_double_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_D2L, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_D2L, "> 0"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_D2L_AVX10_2, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void double2long() {
        for (int i = 0; i < COUNT; i++) {
            double double_val = double_arr[i];
            long computed = (long) double_val;
            long expected = long_double_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_D2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_D2I, "> 0"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_D2I_AVX10_2, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void double2short() {
        for (int i = 0; i < COUNT; i++) {
            double double_val = double_arr[i];
            short computed = (short) double_val;
            short expected = short_double_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_D2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_D2I, "> 0"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_D2I_AVX10_2, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void double2byte() {
        for (int i = 0; i < COUNT; i++) {
            double double_val = double_arr[i];
            byte computed = (byte) double_val;
            byte expected = byte_double_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }
}
