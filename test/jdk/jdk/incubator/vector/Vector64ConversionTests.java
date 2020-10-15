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
 *      Vector64ConversionTests
 */

@Test
public class Vector64ConversionTests extends AbstractVectorConversionTest {

    static final VectorSpecies<Integer> ispec64 = IntVector.SPECIES_64;
    static final VectorSpecies<Float> fspec64 = FloatVector.SPECIES_64;
    static final VectorSpecies<Long> lspec64 = LongVector.SPECIES_64;
    static final VectorSpecies<Double> dspec64 = DoubleVector.SPECIES_64;
    static final VectorSpecies<Byte> bspec64 = ByteVector.SPECIES_64;
    static final VectorSpecies<Short> sspec64 = ShortVector.SPECIES_64;


    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2B_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2S_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2I_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2L_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2F_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertB2D_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2B_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2S_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec64, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec64, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec64, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2I_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec64, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec64, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec64, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2L_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2F_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void convertShapeB2D_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2B_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        conversion_kernel(bspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), B2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2S_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        conversion_kernel(bspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.B2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec64, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec64, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec64, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2I_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        conversion_kernel(bspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.B2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec64, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec64, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec64, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2L_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        conversion_kernel(bspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.B2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2F_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        conversion_kernel(bspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.B2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void castShapeB2D_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        conversion_kernel(bspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.B2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2B_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,ByteVector,Byte,Byte>
        reinterpret_kernel(bspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2S_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,ShortVector,Byte,Short>
        reinterpret_kernel(bspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspec64, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspec64, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspec64, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspec64, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2I_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,IntVector,Byte,Integer>
        reinterpret_kernel(bspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspec64, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspec64, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspec64, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspec64, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2L_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,LongVector,Byte,Long>
        reinterpret_kernel(bspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2F_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,FloatVector,Byte,Float>
        reinterpret_kernel(bspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_64_To_64(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_64_To_128(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_64_To_256(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_64_To_512(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "byteUnaryOpProvider")
    static void reinterpretShapeB2D_64_To_MAX(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(1024);
        int olen =  (a.length / bspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ByteVector,DoubleVector,Byte,Double>
        reinterpret_kernel(bspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2B_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2S_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2I_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2L_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2F_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertD2D_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2B_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2S_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec64, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec64, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec64, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2I_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec64, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec64, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec64, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2L_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2F_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void convertShapeD2D_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2B_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        conversion_kernel(dspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.D2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2S_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        conversion_kernel(dspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.D2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec64, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec64, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec64, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2I_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        conversion_kernel(dspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.D2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec64, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec64, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec64, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2L_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        conversion_kernel(dspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.D2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2F_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        conversion_kernel(dspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.D2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void castShapeD2D_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        conversion_kernel(dspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), D2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2B_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,ByteVector,Double,Byte>
        reinterpret_kernel(dspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2S_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,ShortVector,Double,Short>
        reinterpret_kernel(dspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspec64, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspec64, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspec64, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspec64, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2I_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,IntVector,Double,Integer>
        reinterpret_kernel(dspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspec64, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspec64, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspec64, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspec64, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2L_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,LongVector,Double,Long>
        reinterpret_kernel(dspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2F_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,FloatVector,Double,Float>
        reinterpret_kernel(dspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_64_To_64(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_64_To_128(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_64_To_256(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_64_To_512(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void reinterpretShapeD2D_64_To_MAX(IntFunction<double[]> fa) {
        double[] a = fa.apply(1024);
        int olen =  (a.length / dspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<DoubleVector,DoubleVector,Double,Double>
        reinterpret_kernel(dspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2B_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2S_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2I_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2L_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2F_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertF2D_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2B_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2S_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec64, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec64, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec64, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2I_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec64, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec64, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec64, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2L_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2F_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void convertShapeF2D_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2B_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        conversion_kernel(fspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.F2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2S_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        conversion_kernel(fspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.F2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec64, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec64, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec64, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2I_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        conversion_kernel(fspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.F2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec64, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec64, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec64, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2L_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        conversion_kernel(fspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.F2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2F_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        conversion_kernel(fspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), F2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void castShapeF2D_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        conversion_kernel(fspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.F2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2B_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,ByteVector,Float,Byte>
        reinterpret_kernel(fspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2S_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,ShortVector,Float,Short>
        reinterpret_kernel(fspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspec64, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspec64, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspec64, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspec64, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2I_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,IntVector,Float,Integer>
        reinterpret_kernel(fspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspec64, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspec64, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspec64, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspec64, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2L_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,LongVector,Float,Long>
        reinterpret_kernel(fspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2F_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,FloatVector,Float,Float>
        reinterpret_kernel(fspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_64_To_64(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_64_To_128(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_64_To_256(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_64_To_512(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void reinterpretShapeF2D_64_To_MAX(IntFunction<float[]> fa) {
        float[] a = fa.apply(1024);
        int olen =  (a.length / fspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<FloatVector,DoubleVector,Float,Double>
        reinterpret_kernel(fspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2B_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2S_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2I_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2L_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2F_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertI2D_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec64, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec64, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec64, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2B_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec64, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec64, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec64, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2S_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec64, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec64, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec64, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2I_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec64, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec64, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec64, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2L_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec64, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec64, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec64, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2F_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void convertShapeI2D_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec64, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec64, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec64, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2B_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        conversion_kernel(ispec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.I2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec64, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec64, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec64, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2S_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        conversion_kernel(ispec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.I2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec64, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec64, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec64, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2I_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        conversion_kernel(ispec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), I2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec64, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec64, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec64, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2L_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        conversion_kernel(ispec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.I2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec64, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec64, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec64, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2F_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        conversion_kernel(ispec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.I2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void castShapeI2D_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        conversion_kernel(ispec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.I2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispec64, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispec64, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispec64, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispec64, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2B_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,ByteVector,Integer,Byte>
        reinterpret_kernel(ispec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispec64, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispec64, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispec64, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispec64, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2S_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,ShortVector,Integer,Short>
        reinterpret_kernel(ispec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispec64, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispec64, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispec64, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispec64, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2I_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,IntVector,Integer,Integer>
        reinterpret_kernel(ispec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispec64, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispec64, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispec64, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispec64, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2L_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,LongVector,Integer,Long>
        reinterpret_kernel(ispec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispec64, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispec64, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispec64, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispec64, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2F_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,FloatVector,Integer,Float>
        reinterpret_kernel(ispec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_64_To_64(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_64_To_128(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_64_To_256(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_64_To_512(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void reinterpretShapeI2D_64_To_MAX(IntFunction<int[]> fa) {
        int[] a = fa.apply(1024);
        int olen =  (a.length / ispec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<IntVector,DoubleVector,Integer,Double>
        reinterpret_kernel(ispec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2B_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2S_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2I_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2L_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2F_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertL2D_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2B_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2S_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec64, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec64, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec64, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2I_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec64, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec64, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec64, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2L_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2F_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void convertShapeL2D_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2B_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        conversion_kernel(lspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.L2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2S_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        conversion_kernel(lspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), VectorOperators.L2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec64, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec64, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec64, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2I_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        conversion_kernel(lspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.L2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec64, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec64, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec64, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2L_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        conversion_kernel(lspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), L2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2F_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        conversion_kernel(lspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.L2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void castShapeL2D_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        conversion_kernel(lspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.L2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2B_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,ByteVector,Long,Byte>
        reinterpret_kernel(lspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2S_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,ShortVector,Long,Short>
        reinterpret_kernel(lspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspec64, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspec64, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspec64, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspec64, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2I_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,IntVector,Long,Integer>
        reinterpret_kernel(lspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspec64, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspec64, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspec64, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspec64, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2L_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,LongVector,Long,Long>
        reinterpret_kernel(lspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2F_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,FloatVector,Long,Float>
        reinterpret_kernel(lspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_64_To_64(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_64_To_128(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_64_To_256(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_64_To_512(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "longUnaryOpProvider")
    static void reinterpretShapeL2D_64_To_MAX(IntFunction<long[]> fa) {
        long[] a = fa.apply(1024);
        int olen =  (a.length / lspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<LongVector,DoubleVector,Long,Double>
        reinterpret_kernel(lspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }


    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2B_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2S_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2I_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2L_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2F_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertS2D_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERT, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2B_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2S_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec64, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec64, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec64, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2I_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec64, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec64, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec64, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2L_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2F_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void convertShapeS2D_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CONVERTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2B_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        conversion_kernel(sspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Byte.class, olen),
                          getBoxedArray(Byte.class, olen),
                          Arrays.asList(a), VectorOperators.S2B,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2S_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        conversion_kernel(sspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Short.class, olen),
                          getBoxedArray(Short.class, olen),
                          Arrays.asList(a), S2S,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec64, IntVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec64, IntVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec64, IntVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec64, IntVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2I_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        conversion_kernel(sspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Integer.class, olen),
                          getBoxedArray(Integer.class, olen),
                          Arrays.asList(a), VectorOperators.S2I,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec64, LongVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec64, LongVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec64, LongVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec64, LongVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2L_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        conversion_kernel(sspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Long.class, olen),
                          getBoxedArray(Long.class, olen),
                          Arrays.asList(a), VectorOperators.S2L,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2F_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        conversion_kernel(sspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Float.class, olen),
                          getBoxedArray(Float.class, olen),
                          Arrays.asList(a), VectorOperators.S2F,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void castShapeS2D_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        conversion_kernel(sspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                          getBoxedArray(Double.class, olen),
                          getBoxedArray(Double.class, olen),
                          Arrays.asList(a), VectorOperators.S2D,
                          ConvAPI.CASTSHAPE, a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspec64, ByteVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspec64, ByteVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspec64, ByteVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspec64, ByteVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2B_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ByteVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,ByteVector,Short,Byte>
        reinterpret_kernel(sspec64, ByteVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Byte.class, olen),
                           getBoxedArray(Byte.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspec64, ShortVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspec64, ShortVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspec64, ShortVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspec64, ShortVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2S_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * ShortVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,ShortVector,Short,Short>
        reinterpret_kernel(sspec64, ShortVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Short.class, olen),
                           getBoxedArray(Short.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspec64, IntVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspec64, IntVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspec64, IntVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspec64, IntVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2I_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * IntVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,IntVector,Short,Integer>
        reinterpret_kernel(sspec64, IntVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Integer.class, olen),
                           getBoxedArray(Integer.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspec64, LongVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspec64, LongVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspec64, LongVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspec64, LongVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2L_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * LongVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,LongVector,Short,Long>
        reinterpret_kernel(sspec64, LongVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Long.class, olen),
                           getBoxedArray(Long.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspec64, FloatVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspec64, FloatVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspec64, FloatVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspec64, FloatVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2F_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * FloatVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,FloatVector,Short,Float>
        reinterpret_kernel(sspec64, FloatVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Float.class, olen),
                           getBoxedArray(Float.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_64_To_64(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_64.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspec64, DoubleVector.SPECIES_64, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_64_To_128(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_128.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspec64, DoubleVector.SPECIES_128, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_64_To_256(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_256.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspec64, DoubleVector.SPECIES_256, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_64_To_512(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_512.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspec64, DoubleVector.SPECIES_512, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void reinterpretShapeS2D_64_To_MAX(IntFunction<short[]> fa) {
        short[] a = fa.apply(1024);
        int olen =  (a.length / sspec64.length()) * DoubleVector.SPECIES_MAX.length();
        Vector64ConversionTests.<ShortVector,DoubleVector,Short,Double>
        reinterpret_kernel(sspec64, DoubleVector.SPECIES_MAX, getBoxedArray(a),
                           getBoxedArray(Double.class, olen),
                           getBoxedArray(Double.class, olen),
                           Arrays.asList(a), a.length);

    }
}
