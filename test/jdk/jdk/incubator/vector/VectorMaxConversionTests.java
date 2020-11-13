/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import jdk.incubator.vector.*;
import jdk.internal.vm.annotation.ForceInline;

import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.nio.*;
import java.util.function.IntFunction;

/**
 * @test
 * @modules jdk.incubator.vector
 * @modules java.base/jdk.internal.vm.annotation
 * @run testng/othervm  -XX:-TieredCompilation --add-opens jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED
 *      VectorMaxConversionTests
 */

@Test
public class VectorMaxConversionTests extends AbstractVectorConversionTest {

    static final VectorSpecies<Integer> ispecMax = IntVector.SPECIES_MAX;
    static final VectorSpecies<Float> fspecMax = FloatVector.SPECIES_MAX;
    static final VectorSpecies<Long> lspecMax = LongVector.SPECIES_MAX;
    static final VectorSpecies<Double> dspecMax = DoubleVector.SPECIES_MAX;
    static final VectorSpecies<Byte> bspecMax = ByteVector.SPECIES_MAX;
    static final VectorSpecies<Short> sspecMax = ShortVector.SPECIES_MAX;


    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2B_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2S_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2I_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2L_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2F_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2D_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_Max_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_Max_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_Max_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_Max_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_Max_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2B_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2S_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2I_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2L_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2F_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2D_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_Max_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_Max_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_Max_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_Max_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_Max_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2B_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2S_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2I_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2L_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2F_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2D_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_Max_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_Max_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_Max_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_Max_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_Max_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2B_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2S_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2I_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2L_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2F_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2D_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispecMax, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispecMax, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispecMax, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispecMax, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispecMax, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispecMax, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispecMax, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispecMax, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispecMax, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispecMax, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispecMax, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispecMax, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispecMax, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispecMax, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispecMax, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispecMax, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispecMax, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispecMax, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispecMax, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispecMax, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispecMax, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispecMax, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispecMax, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispecMax, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_Max_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_Max_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_Max_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_Max_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_Max_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2B_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2S_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2I_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2L_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2F_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2D_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_Max_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_Max_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_Max_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_Max_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_Max_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2B_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2S_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2I_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2L_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2F_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2D_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspecMax, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspecMax, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspecMax, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspecMax, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ByteVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspecMax, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspecMax, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspecMax, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspecMax, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspecMax, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * ShortVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspecMax, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspecMax, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspecMax, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspecMax, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspecMax, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * IntVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspecMax, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspecMax, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspecMax, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspecMax, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspecMax, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * LongVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspecMax, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspecMax, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspecMax, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspecMax, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspecMax, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * FloatVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspecMax, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_Max_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_64.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspecMax, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_Max_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_128.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspecMax, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_Max_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_256.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspecMax, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_Max_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_512.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspecMax, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_Max_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspecMax.length()) * DoubleVector.SPECIES_MAX.length();
        VectorMaxConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspecMax, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }
}
