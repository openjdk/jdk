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
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.apps.LingeredApp;

public class LingeredAppWithVector extends LingeredApp {

    // 128-bit species
    private static final VectorSpecies<Byte>    B128 = ByteVector.SPECIES_128;
    private static final VectorSpecies<Short>   S128 = ShortVector.SPECIES_128;
    private static final VectorSpecies<Integer> I128 = IntVector.SPECIES_128;
    private static final VectorSpecies<Long>    L128 = LongVector.SPECIES_128;
    private static final VectorSpecies<Float>   F128 = FloatVector.SPECIES_128;
    private static final VectorSpecies<Double>  D128 = DoubleVector.SPECIES_128;

    // 64-bit species
    private static final VectorSpecies<Byte>    B64 = ByteVector.SPECIES_64;
    private static final VectorSpecies<Short>   S64 = ShortVector.SPECIES_64;
    private static final VectorSpecies<Integer> I64 = IntVector.SPECIES_64;
    private static final VectorSpecies<Float>   F64 = FloatVector.SPECIES_64;

    private static boolean isWarmup = true;
    private static String[] savedArgs;

    // Arrays are sized for the 128-bit species; the 64-bit species read a prefix.
    private static final byte[]   byteArray   = new byte[B128.length()];
    private static final short[]  shortArray  = new short[S128.length()];
    private static final int[]    intArray    = new int[I128.length()];
    private static final long[]   longArray   = new long[L128.length()];
    private static final float[]  floatArray  = new float[F128.length()];
    private static final double[] doubleArray = new double[D128.length()];

    public static double test() {
        ByteVector   b128 = ByteVector.fromArray(B128, byteArray, 0);
        ShortVector  s128 = ShortVector.fromArray(S128, shortArray, 0);
        IntVector    i128 = IntVector.fromArray(I128, intArray, 0);
        LongVector   l128 = LongVector.fromArray(L128, longArray, 0);
        FloatVector  f128 = FloatVector.fromArray(F128, floatArray, 0);
        DoubleVector d128 = DoubleVector.fromArray(D128, doubleArray, 0);
        ByteVector   b64 = ByteVector.fromArray(B64, byteArray, 0);
        ShortVector  s64 = ShortVector.fromArray(S64, shortArray, 0);
        IntVector    i64 = IntVector.fromArray(I64, intArray, 0);
        FloatVector  f64 = FloatVector.fromArray(F64, floatArray, 0);

        checkpoint();

        double sum = 0;
        sum += b128.reduceLanes(VectorOperators.ADD);
        sum += s128.reduceLanes(VectorOperators.ADD);
        sum += i128.reduceLanes(VectorOperators.ADD);
        sum += l128.reduceLanes(VectorOperators.ADD);
        sum += f128.reduceLanes(VectorOperators.ADD);
        sum += d128.reduceLanes(VectorOperators.ADD);
        sum += b64.reduceLanes(VectorOperators.ADD);
        sum += s64.reduceLanes(VectorOperators.ADD);
        sum += i64.reduceLanes(VectorOperators.ADD);
        sum += f64.reduceLanes(VectorOperators.ADD);
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

        for (int i = 0; i < byteArray.length; i++)   byteArray[i]   = (byte) i;
        for (int i = 0; i < shortArray.length; i++)  shortArray[i]  = (short) i;
        for (int i = 0; i < intArray.length; i++)    intArray[i]    = i;
        for (int i = 0; i < longArray.length; i++)   longArray[i]   = i;
        for (int i = 0; i < floatArray.length; i++)  floatArray[i]  = i;
        for (int i = 0; i < doubleArray.length; i++) doubleArray[i] = i;

        for (int i = 0; i < 20_000; i++) {
            test();
        }

        isWarmup = false;
        test();
    }
}
