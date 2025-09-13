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
import java.util.Random;

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
        float_arr = new float[COUNT];
        double_arr = new double[COUNT];
        long_arr = new long[COUNT];
        int_arr = new int[COUNT];
        short_arr = new short[COUNT];
        byte_arr = new byte[COUNT];

        Random ran = new Random(0);
        for (int i = 0; i < COUNT; i++) {
            int floor_val = ran.nextInt(Byte.MAX_VALUE);
            int ceil_val = floor_val + 1;
            long_arr[i] = (long) floor_val;
            int_arr[i] = floor_val;
            short_arr[i] = (short) floor_val;
            byte_arr[i] = (byte) floor_val;
            float_arr[i] = ran.nextFloat(floor_val, ceil_val);
            double_arr[i] = ran.nextDouble(floor_val, ceil_val);
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_F2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_F2I, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_F2I_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void float2int() {
        checkf2int();
    }

    public void checkf2int() {
        for (int i = 0; i < COUNT; i++) {
            float float_val = float_arr[i];
            int expected = (int) float_val;
            if (int_arr[i] != expected) {
                throw new RuntimeException("Invalid result: int_arr[" + i + "] = " + int_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_F2L, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_F2L, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_F2L_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void float2long() {
        checkf2long();
    }

    public void checkf2long() {
        for (int i = 0; i < COUNT; i++) {
            float float_val = float_arr[i];
            long expected = (long) float_val;
            if (long_arr[i] != expected) {
                throw new RuntimeException("Invalid result: long_arr[" + i + "] = " + long_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_F2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_F2I, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_F2I_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void float2short() {
        checkf2short();
    }

    public void checkf2short() {
        for (int i = 0; i < COUNT; i++) {
            float float_val = float_arr[i];
            short expected = (short) float_val;
            if (short_arr[i] != expected) {
                throw new RuntimeException("Invalid result: short_arr[" + i + "] = " + short_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_F2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_F2I, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_F2I_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void float2byte() {
        checkf2byte();
    }

    public void checkf2byte() {
        for (int i = 0; i < COUNT; i++) {
            float float_val = float_arr[i];
            byte expected = (byte) float_val;
            if (byte_arr[i] != expected) {
                throw new RuntimeException("Invalid result: byte_arr[" + i + "] = " + byte_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_D2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_D2I, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_D2I_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void double2int() {
        checkd2int();
    }

    public void checkd2int() {
        for (int i = 0; i < COUNT; i++) {
            double double_val = double_arr[i];
            int expected = (int) double_val;
            if (int_arr[i] != expected) {
                throw new RuntimeException("Invalid result: int_arr[" + i + "] = " + int_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_D2L, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_D2L, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_D2L_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void double2long() {
        checkd2long();
    }

    public void checkd2long() {
        for (int i = 0; i < COUNT; i++) {
            double double_val = double_arr[i];
            long expected = (long) double_val;
            if (long_arr[i] != expected) {
                throw new RuntimeException("Invalid result: long_arr[" + i + "] = " + long_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_D2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_D2I, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_D2I_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void double2short() {
        checkd2short();
    }

    public void checkd2short() {
        for (int i = 0; i < COUNT; i++) {
            double double_val = double_arr[i];
            short expected = (short) double_val;
            if (short_arr[i] != expected) {
                throw new RuntimeException("Invalid result: short_arr[" + i + "] = " + short_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.CONV_D2I, "> 0"})
    @IR(counts = {IRNode.X86_SCONV_D2I, "> 0"},
        applyIfCPUFeature = {"avx10_2", "false"})
    @IR(counts = {IRNode.X86_SCONV_D2I_AVX10, "> 0"},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void double2byte() {
        checkd2byte();
    }

    public void checkd2byte() {
        for (int i = 0; i < COUNT; i++) {
            double double_val = double_arr[i];
            byte expected = (byte) double_val;
            if (byte_arr[i] != expected) {
                throw new RuntimeException("Invalid result: byte_arr[" + i + "] = " + byte_arr[i] + " != " + expected);
            }
        }
    }
}
