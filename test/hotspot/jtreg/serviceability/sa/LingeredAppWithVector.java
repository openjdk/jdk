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

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.Float16;
import jdk.incubator.vector.Float16Vector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.apps.LingeredApp;

public class LingeredAppWithVector extends LingeredApp {

    // Max species
    private static final VectorSpecies<Byte>      B_MAX = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short>     S_MAX = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer>   I_MAX = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long>      L_MAX = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Float>     F_MAX = FloatVector.SPECIES_MAX;
    private static final VectorSpecies<Double>    D_MAX = DoubleVector.SPECIES_MAX;
    private static final VectorSpecies<Float16> F16_MAX = Float16Vector.SPECIES_MAX;

    // 512-bit species
    private static final VectorSpecies<Byte>       B512 = ByteVector.SPECIES_512;
    private static final VectorSpecies<Short>      S512 = ShortVector.SPECIES_512;
    private static final VectorSpecies<Integer>    I512 = IntVector.SPECIES_512;
    private static final VectorSpecies<Long>       L512 = LongVector.SPECIES_512;
    private static final VectorSpecies<Float>      F512 = FloatVector.SPECIES_512;
    private static final VectorSpecies<Double>     D512 = DoubleVector.SPECIES_512;
    private static final VectorSpecies<Float16> F16_512 = Float16Vector.SPECIES_512;

    // 256-bit species
    private static final VectorSpecies<Byte>       B256 = ByteVector.SPECIES_256;
    private static final VectorSpecies<Short>      S256 = ShortVector.SPECIES_256;
    private static final VectorSpecies<Integer>    I256 = IntVector.SPECIES_256;
    private static final VectorSpecies<Long>       L256 = LongVector.SPECIES_256;
    private static final VectorSpecies<Float>      F256 = FloatVector.SPECIES_256;
    private static final VectorSpecies<Double>     D256 = DoubleVector.SPECIES_256;
    private static final VectorSpecies<Float16> F16_256 = Float16Vector.SPECIES_256;

    // 128-bit species
    private static final VectorSpecies<Byte>       B128 = ByteVector.SPECIES_128;
    private static final VectorSpecies<Short>      S128 = ShortVector.SPECIES_128;
    private static final VectorSpecies<Integer>    I128 = IntVector.SPECIES_128;
    private static final VectorSpecies<Long>       L128 = LongVector.SPECIES_128;
    private static final VectorSpecies<Float>      F128 = FloatVector.SPECIES_128;
    private static final VectorSpecies<Double>     D128 = DoubleVector.SPECIES_128;
    private static final VectorSpecies<Float16> F16_128 = Float16Vector.SPECIES_128;

    // 64-bit species
    private static final VectorSpecies<Byte>       B64 = ByteVector.SPECIES_64;
    private static final VectorSpecies<Short>      S64 = ShortVector.SPECIES_64;
    private static final VectorSpecies<Integer>    I64 = IntVector.SPECIES_64;
    private static final VectorSpecies<Long>       L64 = LongVector.SPECIES_64;
    private static final VectorSpecies<Float>      F64 = FloatVector.SPECIES_64;
    private static final VectorSpecies<Double>     D64 = DoubleVector.SPECIES_64;
    private static final VectorSpecies<Float16> F16_64 = Float16Vector.SPECIES_64;

    private static boolean isWarmup = true;
    private static String[] savedArgs;

    // The arrays are sized to the larger of the 512-bit and Max species. Smaller species read a prefix.
    private static final byte[]     byteArray = new byte[Math.max(B_MAX.length(), B512.length())];
    private static final short[]   shortArray = new short[Math.max(S_MAX.length(), S512.length())];
    private static final int[]       intArray = new int[Math.max(I_MAX.length(), I512.length())];
    private static final long[]     longArray = new long[Math.max(L_MAX.length(), L512.length())];
    private static final float[]   floatArray = new float[Math.max(F_MAX.length(), F512.length())];
    private static final double[] doubleArray = new double[Math.max(D_MAX.length(), D512.length())];
    private static final short[] float16Array = new short[Math.max(F16_MAX.length(), F16_512.length())];

    public static double test() {
        ByteVector      b_max = ByteVector.fromArray(B_MAX, byteArray, 0);
        ShortVector     s_max = ShortVector.fromArray(S_MAX, shortArray, 0);
        IntVector       i_max = IntVector.fromArray(I_MAX, intArray, 0);
        LongVector      l_max = LongVector.fromArray(L_MAX, longArray, 0);
        FloatVector     f_max = FloatVector.fromArray(F_MAX, floatArray, 0);
        DoubleVector    d_max = DoubleVector.fromArray(D_MAX, doubleArray, 0);
        Float16Vector f16_max = Float16Vector.fromArray(F16_MAX, float16Array, 0);

        ByteVector       b512 = ByteVector.fromArray(B512, byteArray, 0);
        ShortVector      s512 = ShortVector.fromArray(S512, shortArray, 0);
        IntVector        i512 = IntVector.fromArray(I512, intArray, 0);
        LongVector       l512 = LongVector.fromArray(L512, longArray, 0);
        FloatVector      f512 = FloatVector.fromArray(F512, floatArray, 0);
        DoubleVector     d512 = DoubleVector.fromArray(D512, doubleArray, 0);
        Float16Vector f16_512 = Float16Vector.fromArray(F16_512, float16Array, 0);

        ByteVector       b256 = ByteVector.fromArray(B256, byteArray, 0);
        ShortVector      s256 = ShortVector.fromArray(S256, shortArray, 0);
        IntVector        i256 = IntVector.fromArray(I256, intArray, 0);
        LongVector       l256 = LongVector.fromArray(L256, longArray, 0);
        FloatVector      f256 = FloatVector.fromArray(F256, floatArray, 0);
        DoubleVector     d256 = DoubleVector.fromArray(D256, doubleArray, 0);
        Float16Vector f16_256 = Float16Vector.fromArray(F16_256, float16Array, 0);

        ByteVector       b128 = ByteVector.fromArray(B128, byteArray, 0);
        ShortVector      s128 = ShortVector.fromArray(S128, shortArray, 0);
        IntVector        i128 = IntVector.fromArray(I128, intArray, 0);
        LongVector       l128 = LongVector.fromArray(L128, longArray, 0);
        FloatVector      f128 = FloatVector.fromArray(F128, floatArray, 0);
        DoubleVector     d128 = DoubleVector.fromArray(D128, doubleArray, 0);
        Float16Vector f16_128 = Float16Vector.fromArray(F16_128, float16Array, 0);

        ByteVector       b64 = ByteVector.fromArray(B64, byteArray, 0);
        ShortVector      s64 = ShortVector.fromArray(S64, shortArray, 0);
        IntVector        i64 = IntVector.fromArray(I64, intArray, 0);
        LongVector       l64 = LongVector.fromArray(L64, longArray, 0);
        FloatVector      f64 = FloatVector.fromArray(F64, floatArray, 0);
        DoubleVector     d64 = DoubleVector.fromArray(D64, doubleArray, 0);
        Float16Vector f16_64 = Float16Vector.fromArray(F16_64, float16Array, 0);

        checkpoint();

        double sum = 0;
        sum += b_max.reduceLanes(VectorOperators.ADD);
        sum += s_max.reduceLanes(VectorOperators.ADD);
        sum += i_max.reduceLanes(VectorOperators.ADD);
        sum += l_max.reduceLanes(VectorOperators.ADD);
        sum += f_max.reduceLanes(VectorOperators.ADD);
        sum += d_max.reduceLanes(VectorOperators.ADD);
        sum += f16_max.reduceLanes(VectorOperators.ADD);
        sum += b512.reduceLanes(VectorOperators.ADD);
        sum += s512.reduceLanes(VectorOperators.ADD);
        sum += i512.reduceLanes(VectorOperators.ADD);
        sum += l512.reduceLanes(VectorOperators.ADD);
        sum += f512.reduceLanes(VectorOperators.ADD);
        sum += d512.reduceLanes(VectorOperators.ADD);
        sum += f16_512.reduceLanes(VectorOperators.ADD);
        sum += b256.reduceLanes(VectorOperators.ADD);
        sum += s256.reduceLanes(VectorOperators.ADD);
        sum += i256.reduceLanes(VectorOperators.ADD);
        sum += l256.reduceLanes(VectorOperators.ADD);
        sum += f256.reduceLanes(VectorOperators.ADD);
        sum += d256.reduceLanes(VectorOperators.ADD);
        sum += f16_256.reduceLanes(VectorOperators.ADD);
        sum += b128.reduceLanes(VectorOperators.ADD);
        sum += s128.reduceLanes(VectorOperators.ADD);
        sum += i128.reduceLanes(VectorOperators.ADD);
        sum += l128.reduceLanes(VectorOperators.ADD);
        sum += f128.reduceLanes(VectorOperators.ADD);
        sum += d128.reduceLanes(VectorOperators.ADD);
        sum += f16_128.reduceLanes(VectorOperators.ADD);
        sum += b64.reduceLanes(VectorOperators.ADD);
        sum += s64.reduceLanes(VectorOperators.ADD);
        sum += i64.reduceLanes(VectorOperators.ADD);
        sum += l64.reduceLanes(VectorOperators.ADD);
        sum += f64.reduceLanes(VectorOperators.ADD);
        sum += d64.reduceLanes(VectorOperators.ADD);
        sum += f16_64.reduceLanes(VectorOperators.ADD);
        return sum;
    }

    public static void checkpoint() {
        if (isWarmup) {
            return;
        }
        LingeredApp.main(savedArgs);
    }

    public static void main(String[] args) {
        savedArgs = args;

        for (int i = 0; i < byteArray.length; i++)   byteArray[i]     = (byte) i;
        for (int i = 0; i < shortArray.length; i++)  shortArray[i]    = (short) i;
        for (int i = 0; i < intArray.length; i++)    intArray[i]      = i;
        for (int i = 0; i < longArray.length; i++)   longArray[i]     = i;
        for (int i = 0; i < floatArray.length; i++)  floatArray[i]    = i;
        for (int i = 0; i < doubleArray.length; i++) doubleArray[i]   = i;
        for (int i = 0; i < float16Array.length; i++) float16Array[i] = (short) i;

        for (int i = 0; i < 20_000; i++) {
            test();
        }

        isWarmup = false;
        test();
    }
}
