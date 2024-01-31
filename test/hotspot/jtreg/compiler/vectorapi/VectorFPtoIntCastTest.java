/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
* @bug 8287835
* @summary Test float/double to integral cast
* @modules jdk.incubator.vector
* @requires vm.compiler2.enabled
* @library /test/lib /
* @run driver compiler.vectorapi.VectorFPtoIntCastTest
*/

package compiler.vectorapi;

import jdk.incubator.vector.*;
import jdk.incubator.vector.FloatVector;
import compiler.lib.ir_framework.*;
import java.util.Random;

public class VectorFPtoIntCastTest {
    private static final int COUNT = 16;
    private static final VectorSpecies<Long> lspec512 = LongVector.SPECIES_512;
    private static final VectorSpecies<Integer> ispec512 = IntVector.SPECIES_512;
    private static final VectorSpecies<Integer> ispec256 = IntVector.SPECIES_256;
    private static final VectorSpecies<Short> sspec256 = ShortVector.SPECIES_256;
    private static final VectorSpecies<Short> sspec128 = ShortVector.SPECIES_128;
    private static final VectorSpecies<Byte> bspec128 = ByteVector.SPECIES_128;
    private static final VectorSpecies<Byte> bspec64  = ByteVector.SPECIES_64;

    private float[] float_arr;
    private double[] double_arr;
    private long[] long_arr;
    private int[] int_arr;
    private short[] short_arr;
    private byte[] byte_arr;

    private FloatVector fvec256;
    private FloatVector fvec512;
    private DoubleVector dvec512;

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }

    public VectorFPtoIntCastTest() {
        float_arr = new float[COUNT];
        double_arr = new double[COUNT];
        long_arr = new long[COUNT];
        int_arr = new int[COUNT];
        short_arr = new short[COUNT];
        byte_arr = new byte[COUNT];

        Random ran = new Random(0);
        for (int i = 0; i < COUNT; i++) {
            float_arr[i] = ran.nextFloat();
            double_arr[i] = ran.nextDouble();
        }

        fvec256 = FloatVector.fromArray(FloatVector.SPECIES_256, float_arr, 0);
        fvec512 = FloatVector.fromArray(FloatVector.SPECIES_512, float_arr, 0);
        dvec512 = DoubleVector.fromArray(DoubleVector.SPECIES_512, double_arr, 0);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_F2I, IRNode.VECTOR_SIZE_16, "> 0"},
        applyIfCPUFeature = {"avx512f", "true"})
    public void float2int() {
        var cvec = (IntVector)fvec512.convertShape(VectorOperators.F2I, ispec512, 0);
        cvec.intoArray(int_arr, 0);
        checkf2int(cvec.length());
    }

    public void checkf2int(int len) {
        for (int i = 0; i < len; i++) {
            int expected = (int)float_arr[i];
            if (int_arr[i] != expected) {
                throw new RuntimeException("Invalid result: int_arr[" + i + "] = " + int_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_F2L, IRNode.VECTOR_SIZE_8, "> 0"},
        applyIfCPUFeature = {"avx512dq", "true"})
    public void float2long() {
        var cvec = (LongVector)fvec512.convertShape(VectorOperators.F2L, lspec512, 0);
        cvec.intoArray(long_arr, 0);
        checkf2long(cvec.length());
    }

    public void checkf2long(int len) {
        for (int i = 0; i < len; i++) {
            long expected = (long)float_arr[i];
            if (long_arr[i] != expected) {
                throw new RuntimeException("Invalid result: long_arr[" + i + "] = " + long_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_F2S, IRNode.VECTOR_SIZE_16, "> 0"},
        applyIfCPUFeature = {"avx512f", "true"})
    public void float2short() {
        var cvec = (ShortVector)fvec512.convertShape(VectorOperators.F2S, sspec256, 0);
        cvec.intoArray(short_arr, 0);
        checkf2short(cvec.length());
    }

    public void checkf2short(int len) {
        for (int i = 0; i < len; i++) {
            short expected = (short)float_arr[i];
            if (short_arr[i] != expected) {
                throw new RuntimeException("Invalid result: short_arr[" + i + "] = " + short_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_F2B, IRNode.VECTOR_SIZE_16, "> 0"},
        applyIfCPUFeature = {"avx512f", "true"})
    public void float2byte() {
        var cvec = (ByteVector)fvec512.convertShape(VectorOperators.F2B, bspec128, 0);
        cvec.intoArray(byte_arr, 0);
        checkf2byte(cvec.length());
    }

    public void checkf2byte(int len) {
        for (int i = 0; i < len; i++) {
            byte expected = (byte)float_arr[i];
            if (byte_arr[i] != expected) {
                throw new RuntimeException("Invalid result: byte_arr[" + i + "] = " + byte_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_D2I, IRNode.VECTOR_SIZE_8, "> 0"},
        applyIfCPUFeature = {"avx512f", "true"})
    public void double2int() {
        var cvec = (IntVector)dvec512.convertShape(VectorOperators.D2I, ispec256, 0);
        cvec.intoArray(int_arr, 0);
        checkd2int(cvec.length());
    }

    public void checkd2int(int len) {
        for (int i = 0; i < len; i++) {
            int expected = (int)double_arr[i];
            if (int_arr[i] != expected) {
                throw new RuntimeException("Invalid result: int_arr[" + i + "] = " + int_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_D2L, IRNode.VECTOR_SIZE_8, "> 0"},
        applyIfCPUFeature = {"avx512dq", "true"})
    public void double2long() {
        var cvec = (LongVector)dvec512.convertShape(VectorOperators.D2L, lspec512, 0);
        cvec.intoArray(long_arr, 0);
        checkd2long(cvec.length());
    }

    public void checkd2long(int len) {
        for (int i = 0; i < len; i++) {
            long expected = (long)double_arr[i];
            if (long_arr[i] != expected) {
                throw new RuntimeException("Invalid result: long_arr[" + i + "] = " + long_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_D2S, IRNode.VECTOR_SIZE_8, "> 0"},
        applyIfCPUFeature = {"avx512f", "true"})
    public void double2short() {
        var cvec = (ShortVector)dvec512.convertShape(VectorOperators.D2S, sspec128, 0);
        cvec.intoArray(short_arr, 0);
        checkd2short(cvec.length());
    }

    public void checkd2short(int len) {
        for (int i = 0; i < len; i++) {
            short expected = (short)double_arr[i];
            if (short_arr[i] != expected) {
                throw new RuntimeException("Invalid result: short_arr[" + i + "] = " + short_arr[i] + " != " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_D2B, IRNode.VECTOR_SIZE_8, "> 0"},
        applyIfCPUFeature = {"avx512f", "true"})
    public void double2byte() {
        var cvec = (ByteVector)dvec512.convertShape(VectorOperators.D2B, bspec64, 0);
        cvec.intoArray(byte_arr, 0);
        checkd2byte(cvec.length());
    }

    public void checkd2byte(int len) {
        for (int i = 0; i < len; i++) {
            byte expected = (byte)double_arr[i];
            if (byte_arr[i] != expected) {
                throw new RuntimeException("Invalid result: byte_arr[" + i + "] = " + byte_arr[i] + " != " + expected);
            }
        }
    }
}
