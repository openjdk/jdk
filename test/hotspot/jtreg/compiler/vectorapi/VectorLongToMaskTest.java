/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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

package compiler.vectorapi;

import java.util.Random;

import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorMask;
import jdk.test.lib.Utils;

import org.testng.annotations.Test;

/**
 * @test
 * @bug 8278584
 * @library /test/lib
 * @summary Test the codegen for VectorMask.fromLong
 * @modules jdk.incubator.vector
 *
 * @run testng/othervm -XX:CompileThreshold=100 compiler.vectorapi.VectorLongToMaskTest
 */


public class VectorLongToMaskTest{

    private static final int NUM_ITER = 5000;
    private static final Random rd = Utils.getRandomInstance();

    private static VectorMask<Byte>    bmask;
    private static VectorMask<Short>   smask;
    private static VectorMask<Integer> imask;
    private static VectorMask<Long>    lmask;
    private static VectorMask<Float>   fmask;
    private static VectorMask<Double>  dmask;

    public static void testByte64(long val) {
        VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_64;
        bmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testByte128(long val) {
        VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_128;
        bmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testByte256(long val) {
        VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_256;
        bmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testByte512(long val) {
        VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_512;
        bmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testShort64(long val) {
        VectorSpecies<Short> SPECIES = ShortVector.SPECIES_64;
        smask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testShort128(long val) {
        VectorSpecies<Short> SPECIES = ShortVector.SPECIES_128;
        smask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testShort256(long val) {
        VectorSpecies<Short> SPECIES = ShortVector.SPECIES_256;
        smask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testShort512(long val) {
        VectorSpecies<Short> SPECIES = ShortVector.SPECIES_512;
        smask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testInteger64(long val) {
        VectorSpecies<Integer> SPECIES = IntVector.SPECIES_64;
        imask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testInteger128(long val) {
        VectorSpecies<Integer> SPECIES = IntVector.SPECIES_128;
        imask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testInteger256(long val) {
        VectorSpecies<Integer> SPECIES = IntVector.SPECIES_256;
        imask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testInteger512(long val) {
        VectorSpecies<Integer> SPECIES = IntVector.SPECIES_512;
        imask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testLong64(long val) {
        VectorSpecies<Long> SPECIES = LongVector.SPECIES_64;
        lmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testLong128(long val) {
        VectorSpecies<Long> SPECIES = LongVector.SPECIES_128;
        lmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testLong256(long val) {
        VectorSpecies<Long> SPECIES = LongVector.SPECIES_256;
        lmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testLong512(long val) {
        VectorSpecies<Long> SPECIES = LongVector.SPECIES_512;
        lmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testFloat64(long val) {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_64;
        fmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testFloat128(long val) {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        fmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testFloat256(long val) {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        fmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testFloat512(long val) {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_512;
        fmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testDouble64(long val) {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_64;
        dmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testDouble128(long val) {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        dmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testDouble256(long val) {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        dmask = VectorMask.fromLong(SPECIES, val);
    }

    public static void testDouble512(long val) {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_512;
        dmask = VectorMask.fromLong(SPECIES, val);
    }

    @Test
    public static void testLongToMask() {
        long [] vals = {-1L, 0, rd.nextLong(), rd.nextLong()};
        for(int i = 0; i < vals.length; i++) {
            long val = vals[i];
            for (int ctr = 0; ctr < NUM_ITER; ctr++) {
                testByte64(val);
                testByte128(val);
                testByte256(val);
                testByte512(val);
                testShort64(val);
                testShort128(val);
                testShort256(val);
                testShort512(val);
                testInteger64(val);
                testInteger128(val);
                testInteger256(val);
                testInteger512(val);
                testLong64(val);
                testLong128(val);
                testLong256(val);
                testLong512(val);
                testFloat64(val);
                testFloat128(val);
                testFloat256(val);
                testFloat512(val);
                testDouble64(val);
                testDouble128(val);
                testDouble256(val);
                testDouble512(val);
            }
        }
    }
}
