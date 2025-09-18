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
import compiler.lib.verify.Verify;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

public class ScalarFPtoIntCastTest {
    private static final int COUNT = 16;
    private float[] float_arr;
    private double[] double_arr;
    private long[] long_arr;
    private int[] int_arr;
    private short[] short_arr;
    private byte[] byte_arr;

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000).start();
    }

    public ScalarFPtoIntCastTest() {
        Random ran = new Random(0);
        int_arr = IntStream.range(0, COUNT)
            .mapToObj(i -> ran.nextInt(Byte.MAX_VALUE))
            .mapToInt(Integer::intValue)
            .toArray();
        long_arr = Arrays.stream(int_arr)
            .mapToLong(i -> i)
            .toArray();
        short_arr = new short[COUNT];
        byte_arr = new byte[COUNT];
        double_arr = new double[COUNT];
        float_arr = new float[COUNT];

        for (int i = 0; i < COUNT; i++) {
            int floor_val = int_arr[i];
            int ceil_val = floor_val + 1;
            short_arr[i] = (short) floor_val;
            byte_arr[i] = (byte) floor_val;
            double_arr[i] = ran.nextDouble(floor_val, ceil_val);
            float_arr[i] = (float) double_arr[i];
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_F2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_F2I, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_F2I_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void float2int() {
        for (int i = 0; i < COUNT; i++) {
            float float_val = float_arr[i];
            int computed = (int) float_val;
            int expected = int_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_F2L, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_F2L, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_F2L_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void float2long() {
        for (int i = 0; i < COUNT; i++) {
            float float_val = float_arr[i];
            long computed = (long) float_val;
            long expected = long_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_F2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_F2I, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_F2I_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void float2short() {
        for (int i = 0; i < COUNT; i++) {
            float float_val = float_arr[i];
            short computed = (short) float_val;
            short expected = short_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_F2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_F2I, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_F2I_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void float2byte() {
        for (int i = 0; i < COUNT; i++) {
            float float_val = float_arr[i];
            byte computed = (byte) float_val;
            byte expected = byte_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_D2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_D2I, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_D2I_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void double2int() {
        for (int i = 0; i < COUNT; i++) {
            double double_val = double_arr[i];
            int computed = (int) double_val;
            int expected = int_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_D2L, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_D2L, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_D2L_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void double2long() {
        for (int i = 0; i < COUNT; i++) {
            double double_val = double_arr[i];
            long computed = (long) double_val;
            long expected = long_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_D2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_D2I, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_D2I_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void double2short() {
        for (int i = 0; i < COUNT; i++) {
            double double_val = double_arr[i];
            short computed = (short) double_val;
            short expected = short_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_D2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_D2I, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_D2I_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void double2byte() {
        for (int i = 0; i < COUNT; i++) {
            double double_val = double_arr[i];
            byte computed = (byte) double_val;
            byte expected = byte_arr[i];
            Verify.checkEQ(computed, expected);
        }
    }
}
