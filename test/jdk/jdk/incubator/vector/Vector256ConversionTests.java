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
 *      Vector256ConversionTests
 */

@Test
public class Vector256ConversionTests extends AbstractVectorConversionTest {

    static final VectorSpecies<Integer> ispec256 = IntVector.SPECIES_256;
    static final VectorSpecies<Float> fspec256 = FloatVector.SPECIES_256;
    static final VectorSpecies<Long> lspec256 = LongVector.SPECIES_256;
    static final VectorSpecies<Double> dspec256 = DoubleVector.SPECIES_256;
    static final VectorSpecies<Byte> bspec256 = ByteVector.SPECIES_256;
    static final VectorSpecies<Short> sspec256 = ShortVector.SPECIES_256;


    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2B_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2S_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2I_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2L_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2F_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2D_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec256, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec256, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec256, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec256, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec256, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec256, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec256, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec256, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec256, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec256, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec256, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec256, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspec256, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspec256, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspec256, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspec256, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspec256, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspec256, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspec256, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspec256, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_256_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_256_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_256_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_256_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_256_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2B_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2S_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2I_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2L_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2F_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2D_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec256, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec256, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec256, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec256, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec256, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec256, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec256, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec256, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec256, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec256, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec256, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec256, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspec256, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspec256, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspec256, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspec256, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspec256, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspec256, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspec256, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspec256, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_256_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_256_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_256_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_256_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_256_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2B_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2S_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2I_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2L_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2F_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2D_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec256, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec256, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec256, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec256, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec256, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec256, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec256, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec256, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec256, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec256, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec256, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec256, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspec256, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspec256, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspec256, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspec256, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspec256, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspec256, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspec256, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspec256, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_256_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_256_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_256_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_256_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_256_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2B_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2S_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2I_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2L_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2F_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2D_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec256, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec256, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec256, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec256, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec256, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec256, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec256, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec256, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec256, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec256, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec256, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec256, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec256, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec256, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec256, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec256, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec256, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec256, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec256, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec256, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec256, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec256, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec256, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec256, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec256, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec256, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec256, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec256, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec256, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec256, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispec256, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispec256, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispec256, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispec256, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispec256, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispec256, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispec256, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispec256, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispec256, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispec256, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispec256, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispec256, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispec256, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispec256, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispec256, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispec256, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispec256, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispec256, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispec256, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispec256, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_256_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_256_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_256_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_256_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_256_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2B_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2S_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2I_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2L_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2F_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2D_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec256, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec256, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec256, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec256, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec256, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec256, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec256, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec256, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec256, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec256, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec256, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec256, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspec256, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspec256, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspec256, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspec256, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspec256, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspec256, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspec256, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspec256, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_256_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_256_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_256_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_256_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_256_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2B_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2S_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2I_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2L_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2F_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2D_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec256, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec256, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec256, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec256, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec256, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec256, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec256, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec256, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec256, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec256, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec256, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec256, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec256, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec256, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspec256, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspec256, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspec256, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspec256, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ByteVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspec256, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspec256, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspec256, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspec256, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspec256, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * ShortVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspec256, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspec256, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspec256, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspec256, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspec256, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * IntVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspec256, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspec256, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspec256, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspec256, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspec256, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * LongVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspec256, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspec256, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspec256, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspec256, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspec256, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * FloatVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspec256, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_256_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_64.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspec256, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_256_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_128.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspec256, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_256_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_256.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspec256, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_256_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_512.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspec256, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_256_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec256.length()) * DoubleVector.SPECIES_MAX.length();
        Vector256ConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspec256, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }
}
