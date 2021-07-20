/*
 * Copyright (c) 2021, Huawei Technologies Co. Ltd. All rights reserved.
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

/*
 * @test
 * @bug 8269866
 * @summary AArch64: unsupport missing rules for vector conversion.
 * @modules jdk.incubator.vector
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions compiler.vectorapi.TestVectorCast
 */

package compiler.vectorapi;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorSpecies;
import java.util.Arrays;

public class TestVectorCast {
    static final VectorSpecies<Byte>    SPECIESb = ByteVector.SPECIES_PREFERRED;
    static final VectorSpecies<Short>   SPECIESs = ShortVector.SPECIES_PREFERRED;
    static final VectorSpecies<Integer> SPECIESi = IntVector.SPECIES_PREFERRED;
    static final VectorSpecies<Long>    SPECIESl = LongVector.SPECIES_PREFERRED;
    static final VectorSpecies<Float>   SPECIESf = FloatVector.SPECIES_PREFERRED;
    static final VectorSpecies<Double>  SPECIESd = DoubleVector.SPECIES_PREFERRED;

    static final int INVOC_COUNT = 100000;
    static final int size = 64;

    static byte[] ab = {71, 78, 65, 85, 72, 71, 78, 65, 87, 32, 46, 101, 114,
                        117, 116, 117, 102, 32, 101, 104, 116, 32, 110, 105, 32, 115,
                        115, 101, 110, 105, 112, 112, 97, 104, 32, 103, 110, 105, 114,
                        101, 118, 101, 115, 114, 101, 112, 32, 102, 111, 32, 101, 99,
                        114, 117, 111, 115, 32, 101, 104, 116, 32, 101, 98, 32};
    static short[] as = {108, 108, 105, 119, 32, 101, 118, 111, 108, 32, 114, 117, 79, 46,
                         115, 115, 101, 110, 105, 112, 112, 97, 104, 110, 117, 32, 114,
                         117, 111, 121, 32, 108, 108, 97, 32, 101, 118, 97, 115, 32,
                         111, 116, 32, 101, 112, 111, 104, 32, 73, 46, 116, 104, 103,
                         105, 114, 98, 32, 101, 98, 32, 100, 108, 117, 111};
    static int[] ai = {104, 115, 32, 103, 110, 105, 104, 116, 121, 114, 101, 118, 101, 32, 44,
                       101, 114, 117, 116, 117, 102, 32, 101, 104, 116, 32, 110, 73,
                       46, 110, 101, 100, 114, 117, 98, 32, 97, 32, 101, 109, 111,
                       99, 101, 98, 32, 116, 105, 32, 116, 101, 108, 32, 116, 111,
                       110, 32, 100, 108, 117, 111, 104, 115, 32, 101};
    static long[] al = {119, 32, 100, 110, 97, 32, 44, 114, 101, 118, 111, 32, 115, 105, 32, 116,
                        115, 97, 112, 32, 101, 104, 84, 32, 46, 101, 118, 111, 108,
                        32, 116, 115, 101, 98, 32, 101, 104, 116, 32, 117, 111, 121,
                        32, 101, 118, 105, 103, 32, 111, 116, 32, 116, 110, 97, 119,
                        32, 73, 44, 121, 116, 101, 101, 119, 83};
    static float[] af = {32, 103, 110, 97, 117, 72, 32, 71, 78, 65, 87, 32, 46,
                         101, 121, 66, 45, 101, 121, 66, 32, 46, 73, 32, 109, 97,
                         32, 111, 115, 32, 44, 107, 99, 97, 98, 32, 101, 109, 111,
                         99, 32, 73, 32, 110, 101, 104, 119, 32, 114, 101, 116, 116,
                         101, 98, 32, 101, 98, 32, 108, 108, 105, 119, 32, 117};
    static double[] ad = {111, 89, 32, 46, 121, 110, 97, 112, 109, 111, 99, 32, 114, 97,
                          101, 100, 32, 44, 101, 121, 98, 100, 111, 111, 71, 32, 46,
                          121, 110, 97, 112, 109, 111, 99, 32, 101, 104, 116, 32, 116,
                          97, 32, 121, 97, 100, 32, 116, 115, 97, 108, 32, 121, 109,
                          32, 115, 105, 32, 121, 97, 100, 111, 84, 32, 32};
    public static short[] testVectorCastB2S(byte[] input, short[] output) {
        short[] tmp = output.clone();
        ByteVector av = ByteVector.fromArray(SPECIESb, input, 0);
        ShortVector bv = (ShortVector) av.castShape(SPECIESs, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static int[] testVectorCastB2I(byte[] input, int[] output) {
        int[] tmp = output.clone();
        ByteVector av = ByteVector.fromArray(SPECIESb, input, 0);
        IntVector bv = (IntVector) av.castShape(SPECIESi, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static long[] testVectorCastB2L(byte[] input, long[] output) {
        long[] tmp = output.clone();
        ByteVector av = ByteVector.fromArray(SPECIESb, input, 0);
        LongVector bv = (LongVector) av.castShape(SPECIESl, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static float[] testVectorCastB2F(byte[] input, float[] output) {
        float[] tmp = output.clone();
        ByteVector av = ByteVector.fromArray(SPECIESb, input, 0);
        FloatVector bv = (FloatVector) av.castShape(SPECIESf, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static double[] testVectorCastB2D(byte[] input, double[] output) {
        double[] tmp = output.clone();
        ByteVector av = ByteVector.fromArray(SPECIESb, input, 0);
        DoubleVector bv = (DoubleVector) av.castShape(SPECIESd, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static byte[] testVectorCastS2B(short[] input, byte[] output) {
        byte[] tmp = output.clone();
        ShortVector av = ShortVector.fromArray(SPECIESs, input, 0);
        ByteVector bv = (ByteVector) av.castShape(SPECIESb, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static int[] testVectorCastS2I(short[] input, int[] output) {
        int[] tmp = output.clone();
        ShortVector av = ShortVector.fromArray(SPECIESs, input, 0);
        IntVector bv = (IntVector) av.castShape(SPECIESi, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static long[] testVectorCastS2L(short[] input, long[] output) {
        long[] tmp = output.clone();
        ShortVector av = ShortVector.fromArray(SPECIESs, input, 0);
        LongVector bv = (LongVector) av.castShape(SPECIESl, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static float[] testVectorCastS2F(short[] input, float[] output) {
        float[] tmp = output.clone();
        ShortVector av = ShortVector.fromArray(SPECIESs, input, 0);
        FloatVector bv = (FloatVector) av.castShape(SPECIESf, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static double[] testVectorCastS2D(short[] input, double[] output) {
        double[] tmp = output.clone();
        ShortVector av = ShortVector.fromArray(SPECIESs, input, 0);
        DoubleVector bv = (DoubleVector) av.castShape(SPECIESd, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static byte[] testVectorCastI2B(int[] input, byte[] output) {
        byte[] tmp = output.clone();
        IntVector av = IntVector.fromArray(SPECIESi, input, 0);
        ByteVector bv = (ByteVector) av.castShape(SPECIESb, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static short[] testVectorCastI2S(int[] input, short[] output) {
        short[] tmp = output.clone();
        IntVector av = IntVector.fromArray(SPECIESi, input, 0);
        ShortVector bv = (ShortVector) av.castShape(SPECIESs, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static long[] testVectorCastI2L(int[] input, long[] output) {
        long[] tmp = output.clone();
        IntVector av = IntVector.fromArray(SPECIESi, input, 0);
        LongVector bv = (LongVector) av.castShape(SPECIESl, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static float[] testVectorCastI2F(int[] input, float[] output) {
        float[] tmp = output.clone();
        IntVector av = IntVector.fromArray(SPECIESi, input, 0);
        FloatVector bv = (FloatVector) av.castShape(SPECIESf, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static double[] testVectorCastI2D(int[] input, double[] output) {
        double[] tmp = output.clone();
        IntVector av = IntVector.fromArray(SPECIESi, input, 0);
        DoubleVector bv = (DoubleVector) av.castShape(SPECIESd, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static byte[] testVectorCastL2B(long[] input, byte[] output) {
        byte[] tmp = output.clone();
        LongVector av = LongVector.fromArray(SPECIESl, input, 0);
        ByteVector bv = (ByteVector) av.castShape(SPECIESb, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static short[] testVectorCastL2S(long[] input, short[] output) {
        short[] tmp = output.clone();
        LongVector av = LongVector.fromArray(SPECIESl, input, 0);
        ShortVector bv = (ShortVector) av.castShape(SPECIESs, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static int[] testVectorCastL2I(long[] input, int[] output) {
        int[] tmp = output.clone();
        LongVector av = LongVector.fromArray(SPECIESl, input, 0);
        IntVector bv = (IntVector) av.castShape(SPECIESi, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static float[] testVectorCastL2F(long[] input, float[] output) {
        float[] tmp = output.clone();
        LongVector av = LongVector.fromArray(SPECIESl, input, 0);
        FloatVector bv = (FloatVector) av.castShape(SPECIESf, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static double[] testVectorCastL2D(long[] input, double[] output) {
        double[] tmp = output.clone();
        LongVector av = LongVector.fromArray(SPECIESl, input, 0);
        DoubleVector bv = (DoubleVector) av.castShape(SPECIESd, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static byte[] testVectorCastF2B(float[] input, byte[] output) {
        byte[] tmp = output.clone();
        FloatVector av = FloatVector.fromArray(SPECIESf, input, 0);
        ByteVector bv = (ByteVector) av.castShape(SPECIESb, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static short[] testVectorCastF2S(float[] input, short[] output) {
        short[] tmp = output.clone();
        FloatVector av = FloatVector.fromArray(SPECIESf, input, 0);
        ShortVector bv = (ShortVector) av.castShape(SPECIESs, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static int[] testVectorCastF2I(float[] input, int[] output) {
        int[] tmp = output.clone();
        FloatVector av = FloatVector.fromArray(SPECIESf, input, 0);
        IntVector bv = (IntVector) av.castShape(SPECIESi, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static long[] testVectorCastF2L(float[] input, long[] output) {
        long[] tmp = output.clone();
        FloatVector av = FloatVector.fromArray(SPECIESf, input, 0);
        LongVector bv = (LongVector) av.castShape(SPECIESl, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static double[] testVectorCastF2D(float[] input, double[] output) {
        double[] tmp = output.clone();
        FloatVector av = FloatVector.fromArray(SPECIESf, input, 0);
        DoubleVector bv = (DoubleVector) av.castShape(SPECIESd, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static byte[] testVectorCastD2B(double[] input, byte[] output) {
        byte[] tmp = output.clone();
        DoubleVector av = DoubleVector.fromArray(SPECIESd, input, 0);
        ByteVector bv = (ByteVector) av.castShape(SPECIESb, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static short[] testVectorCastD2S(double[] input, short[] output) {
        short[] tmp = output.clone();
        DoubleVector av = DoubleVector.fromArray(SPECIESd, input, 0);
        ShortVector bv = (ShortVector) av.castShape(SPECIESs, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static int[] testVectorCastD2I(double[] input, int[] output) {
        int[] tmp = output.clone();
        DoubleVector av = DoubleVector.fromArray(SPECIESd, input, 0);
        IntVector bv = (IntVector) av.castShape(SPECIESi, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static long[] testVectorCastD2L(double[] input, long[] output) {
        long[] tmp = output.clone();
        DoubleVector av = DoubleVector.fromArray(SPECIESd, input, 0);
        LongVector bv = (LongVector) av.castShape(SPECIESl, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static float[] testVectorCastD2F(double[] input, float[] output) {
        float[] tmp = output.clone();
        DoubleVector av = DoubleVector.fromArray(SPECIESd, input, 0);
        FloatVector bv = (FloatVector) av.castShape(SPECIESf, 0);
        bv.intoArray(tmp, 0);
        return tmp;
    }

    public static void main(String[] args) {
        short[]  b2s = new short[1];
        int[]    b2i = new int[1];
        long[]   b2l = new long[1];
        float[]  b2f = new float[1];
        double[] b2d = new double[1];

        byte[]   s2b = new byte[1];
        int[]    s2i = new int[1];
        long[]   s2l = new long[1];
        float[]  s2f = new float[1];
        double[] s2d = new double[1];

        byte[]   i2b = new byte[1];
        short[]  i2s = new short[1];
        long[]   i2l = new long[1];
        float[]  i2f = new float[1];
        double[] i2d = new double[1];

        byte[]   l2b = new byte[1];
        short[]  l2s = new short[1];
        int[]    l2i = new int[1];
        float[]  l2f = new float[1];
        double[] l2d = new double[1];

        byte[]   f2b = new byte[1];
        short[]  f2s = new short[1];
        int[]    f2i = new int[1];
        long[]   f2l = new long[1];
        double[] f2d = new double[1];

        byte[]   d2b = new byte[1];
        short[]  d2s = new short[1];
        int[]    d2i = new int[1];
        long[]   d2l = new long[1];
        float[]  d2f = new float[1];

        for (int i = 0; i < INVOC_COUNT; i++) {
            b2s = testVectorCastB2S(ab, as);
            b2i = testVectorCastB2I(ab, ai);
            b2l = testVectorCastB2L(ab, al);
            b2f = testVectorCastB2F(ab, af);
            b2d = testVectorCastB2D(ab, ad);

            s2b = testVectorCastS2B(as, ab);
            s2i = testVectorCastS2I(as, ai);
            s2l = testVectorCastS2L(as, al);
            s2f = testVectorCastS2F(as, af);
            s2d = testVectorCastS2D(as, ad);

            i2b = testVectorCastI2B(ai, ab);
            i2s = testVectorCastI2S(ai, as);
            i2l = testVectorCastI2L(ai, al);
            i2f = testVectorCastI2F(ai, af);
            i2d = testVectorCastI2D(ai, ad);

            l2b = testVectorCastL2B(al, ab);
            l2s = testVectorCastL2S(al, as);
            l2i = testVectorCastL2I(al, ai);
            l2f = testVectorCastL2F(al, af);
            l2d = testVectorCastL2D(al, ad);

            f2b = testVectorCastF2B(af, ab);
            f2s = testVectorCastF2S(af, as);
            f2i = testVectorCastF2I(af, ai);
            f2l = testVectorCastF2L(af, al);
            f2d = testVectorCastF2D(af, ad);

            d2b = testVectorCastD2B(ad, ab);
            d2s = testVectorCastD2S(ad, as);
            d2i = testVectorCastD2I(ad, ai);
            d2l = testVectorCastD2L(ad, al);
            d2f = testVectorCastD2F(ad, af);
        }

        System.out.println(Arrays.toString(b2s));
        System.out.println(Arrays.toString(b2i));
        System.out.println(Arrays.toString(b2l));
        System.out.println(Arrays.toString(b2f));
        System.out.println(Arrays.toString(b2d));

        System.out.println(Arrays.toString(s2b));
        System.out.println(Arrays.toString(s2i));
        System.out.println(Arrays.toString(s2l));
        System.out.println(Arrays.toString(s2f));
        System.out.println(Arrays.toString(s2d));

        System.out.println(Arrays.toString(i2b));
        System.out.println(Arrays.toString(i2s));
        System.out.println(Arrays.toString(i2l));
        System.out.println(Arrays.toString(i2f));
        System.out.println(Arrays.toString(i2d));

        System.out.println(Arrays.toString(l2b));
        System.out.println(Arrays.toString(l2s));
        System.out.println(Arrays.toString(l2i));
        System.out.println(Arrays.toString(l2f));
        System.out.println(Arrays.toString(l2d));

        System.out.println(Arrays.toString(f2b));
        System.out.println(Arrays.toString(f2s));
        System.out.println(Arrays.toString(f2i));
        System.out.println(Arrays.toString(f2l));
        System.out.println(Arrays.toString(f2d));

        System.out.println(Arrays.toString(d2b));
        System.out.println(Arrays.toString(d2s));
        System.out.println(Arrays.toString(d2i));
        System.out.println(Arrays.toString(d2l));
        System.out.println(Arrays.toString(d2f));
    }
}
