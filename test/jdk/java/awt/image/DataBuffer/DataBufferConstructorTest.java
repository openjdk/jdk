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

/*
 * @test
 * @bug 8377568
 * @summary test DataBuffer subclass constructors behaviour with illegal arguments.
 */

import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.util.Arrays;

public class DataBufferConstructorTest {

    static void testByteConstructor(int size,
                                    Class expectedExceptionType) {
        try {
             new DataBufferByte(size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testByteConstructor(int size, int numBanks,
                                    Class expectedExceptionType) {
        try {
             new DataBufferByte(size, numBanks);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testByteConstructor(byte[] dataArray, int size,
                                    Class expectedExceptionType) {
        try {
             new DataBufferByte(dataArray, size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testByteConstructor(byte[] dataArray, int size, int offset,
                                    Class expectedExceptionType) {
        try {
             new DataBufferByte(dataArray, size, offset);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testByteConstructor(byte[][] dataArray, int size,
                                    Class expectedExceptionType) {
        try {
             new DataBufferByte(dataArray, size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }
    static void testByteConstructor(byte[][] dataArray, int size, int[] offsets,
                                    Class expectedExceptionType) {
        try {
             new DataBufferByte(dataArray, size, offsets);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testShortConstructor(int size,
                                    boolean signed, Class expectedExceptionType) {
        try {
             if (signed) {
                new DataBufferShort(size);
             } else {
                new DataBufferUShort(size);
             }
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testShortConstructor(int size, int numBanks,
                                    boolean signed, Class expectedExceptionType) {
        try {
             if (signed) {
                new DataBufferShort(size, numBanks);
             } else {
                new DataBufferUShort(size, numBanks);
             }
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testShortConstructor(short[] dataArray, int size,
                                    boolean signed, Class expectedExceptionType) {
        try {
             if (signed) {
                new DataBufferShort(dataArray, size);
             } else {
                new DataBufferUShort(dataArray, size);
             }
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testShortConstructor(short[] dataArray, int size, int offset,
                                    boolean signed, Class expectedExceptionType) {
        try {
             if (signed) {
                 new DataBufferShort(dataArray, size, offset);
             } else {
                 new DataBufferUShort(dataArray, size, offset);
             }
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testShortConstructor(short[][] dataArray, int size,
                                    boolean signed, Class expectedExceptionType) {
        try {
             new DataBufferShort(dataArray, size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }
    static void testShortConstructor(short[][] dataArray, int size, int[] offsets,
                                    boolean signed, Class expectedExceptionType) {
        try {
             new DataBufferShort(dataArray, size, offsets);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testIntConstructor(int size,
                                    Class expectedExceptionType) {
        try {
             new DataBufferInt(size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testIntConstructor(int size, int numBanks,
                                    Class expectedExceptionType) {
        try {
             new DataBufferInt(size, numBanks);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testIntConstructor(int[] dataArray, int size,
                                    Class expectedExceptionType) {
        try {
             new DataBufferInt(dataArray, size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testIntConstructor(int[] dataArray, int size, int offset,
                                    Class expectedExceptionType) {
        try {
             new DataBufferInt(dataArray, size, offset);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testIntConstructor(int[][] dataArray, int size,
                                    Class expectedExceptionType) {
        try {
             new DataBufferInt(dataArray, size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }
    static void testIntConstructor(int[][] dataArray, int size, int[] offsets,
                                    Class expectedExceptionType) {
        try {
             new DataBufferInt(dataArray, size, offsets);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testFloatConstructor(int size,
                                    Class expectedExceptionType) {
        try {
             new DataBufferFloat(size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testFloatConstructor(int size, int numBanks,
                                    Class expectedExceptionType) {
        try {
             new DataBufferFloat(size, numBanks);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testFloatConstructor(float[] dataArray, int size,
                                    Class expectedExceptionType) {
        try {
             new DataBufferFloat(dataArray, size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testFloatConstructor(float[] dataArray, int size, int offset,
                                    Class expectedExceptionType) {
        try {
             new DataBufferFloat(dataArray, size, offset);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testFloatConstructor(float[][] dataArray, int size,
                                    Class expectedExceptionType) {
        try {
             new DataBufferFloat(dataArray, size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testFloatConstructor(float[][] dataArray, int size, int[] offsets,
                                    Class expectedExceptionType) {
        try {
             new DataBufferFloat(dataArray, size, offsets);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testDoubleConstructor(int size,
                                    Class expectedExceptionType) {
        try {
             new DataBufferDouble(size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testDoubleConstructor(int size, int numBanks,
                                    Class expectedExceptionType) {
        try {
             new DataBufferDouble(size, numBanks);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testDoubleConstructor(double[] dataArray, int size,
                                    Class expectedExceptionType) {
        try {
             new DataBufferDouble(dataArray, size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testDoubleConstructor(double[] dataArray, int size, int offset,
                                    Class expectedExceptionType) {
        try {
             new DataBufferDouble(dataArray, size, offset);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testDoubleConstructor(double[][] dataArray, int size,
                                    Class expectedExceptionType) {
        try {
             new DataBufferDouble(dataArray, size);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static void testDoubleConstructor(double[][] dataArray, int size, int[] offsets,
                                    Class expectedExceptionType) {
        try {
             new DataBufferDouble(dataArray, size, offsets);
             failed = true;
             System.err.println("No expected exception");
             Thread.dumpStack();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) {
                failed = true;
                System.err.println("Unexpected exception type " + e);
                Thread.dumpStack();
            }
        }
    }

    static boolean failed = false;

    static final int[] nullOffsets = null;
    static final int[] zeroOffsetArray = { 0 } ;
    static final int[] oneOffsetArray = { 1 } ;
    static final int[] twoOffsetArray = { 0, 0 } ;

    public static void main(String[] args) {

        final byte[] nullByteArray = null;
        final byte[] zeroByteArray = { } ;
        final byte[] oneByteArray = { 0 } ;
        final byte[] oneKByteArray = new byte[1000];
        final byte[][] nullByteArrays = null;
        final byte[][] nullByteSubArrays = { nullByteArray } ;
        final byte[][] zeroByteSubArrays = { zeroByteArray } ;
        final byte[][] oneByteSubArrays = { oneByteArray } ;

        // DataBufferByte(int size)
        testByteConstructor(-1, IllegalArgumentException.class);
        testByteConstructor(0, IllegalArgumentException.class);

        // DataBufferByte(int size, numBanks)
        testByteConstructor(0, 1, IllegalArgumentException.class);
        testByteConstructor(-1, 0, IllegalArgumentException.class);
        testByteConstructor(-1, 1, IllegalArgumentException.class);

        // DataBufferByte(byte[] dataArray, int size)
        testByteConstructor(nullByteArray, 0, NullPointerException.class);
        testByteConstructor(zeroByteArray, 0, IllegalArgumentException.class);
        testByteConstructor(oneByteArray, -1, IllegalArgumentException.class);
        testByteConstructor(oneByteArray, 2, IllegalArgumentException.class);

        // DataBufferByte(byte[] dataArray, int size, int offset)
        testByteConstructor(nullByteArray, 0, 1, NullPointerException.class);
        testByteConstructor(zeroByteArray, 0, 0, IllegalArgumentException.class);
        testByteConstructor(oneByteArray, -1, 1, IllegalArgumentException.class);
        testByteConstructor(oneByteArray, 2, 1, IllegalArgumentException.class);
        testByteConstructor(oneKByteArray, 1000, Integer.MAX_VALUE - 50, IllegalArgumentException.class);

        // DataBufferByte(byte[][] dataArray, int size)
        testByteConstructor(nullByteArrays, 0, NullPointerException.class);
        testByteConstructor(oneByteSubArrays, 0, IllegalArgumentException.class);
        testByteConstructor(oneByteSubArrays, -1, IllegalArgumentException.class);
        testByteConstructor(oneByteSubArrays, 2, IllegalArgumentException.class);

        // DataBufferByte(byte[][] dataArray, int size, int[] offsets)
        testByteConstructor(nullByteArrays, 0, zeroOffsetArray, NullPointerException.class);
        testByteConstructor(zeroByteSubArrays, 0, zeroOffsetArray, IllegalArgumentException.class);
        testByteConstructor(oneByteSubArrays, 0, zeroOffsetArray, IllegalArgumentException.class);
        testByteConstructor(oneByteSubArrays, -1, oneOffsetArray, IllegalArgumentException.class);
        testByteConstructor(oneByteSubArrays, 2, oneOffsetArray, IllegalArgumentException.class);
        testByteConstructor(oneByteSubArrays, 1, oneOffsetArray, IllegalArgumentException.class);
        testByteConstructor(oneByteSubArrays, 1, twoOffsetArray, ArrayIndexOutOfBoundsException.class);


        final short[] nullShortArray = null;
        final short[] zeroShortArray = { } ;
        final short[] oneShortArray = { 0 } ;
        final byte[] oneKShortArray = new byte[1000];
        final short[][] nullShortArrays = null;
        final short[][] nullShortSubArrays = { nullShortArray } ;
        final short[][] zeroShortSubArrays = { zeroShortArray } ;
        final short[][] oneShortSubArrays = { oneShortArray } ;

        // test DataBufferShort and DataBufferUShort
        for (boolean signed : Arrays.asList(true, false)) {

            // DataBufferShort(int size);
            testShortConstructor(-1, signed, IllegalArgumentException.class);
            testShortConstructor(0, signed, IllegalArgumentException.class);

            // DataBufferShort(int size, int numBanks)
            testShortConstructor(0, 1, signed, IllegalArgumentException.class);
            testShortConstructor(-1, 0, signed, IllegalArgumentException.class);
            testShortConstructor(-1, 1, signed, IllegalArgumentException.class);

            // DataBufferShort(short[] dataArray, int size)
            testShortConstructor(nullShortArray, 0, signed, NullPointerException.class);
            testShortConstructor(zeroShortArray, 0, signed, IllegalArgumentException.class);
            testShortConstructor(oneShortArray, -1, signed, IllegalArgumentException.class);
            testShortConstructor(oneShortArray, 2, signed, IllegalArgumentException.class);

            // DataBufferShort(short[] dataArray, int size, int offset)
            testShortConstructor(nullShortArray, 0, 1, signed, NullPointerException.class);
            testShortConstructor(zeroShortArray, 0, 0, signed, IllegalArgumentException.class);
            testShortConstructor(oneShortArray, -1, 1, signed, IllegalArgumentException.class);
            testShortConstructor(oneShortArray, 2, 1, signed, IllegalArgumentException.class);

            // DataBufferShort(short[][] dataArray, int size)
            testShortConstructor(nullShortArrays, 0, signed, NullPointerException.class);
            testShortConstructor(oneShortSubArrays, 0, signed, IllegalArgumentException.class);
            testShortConstructor(oneShortSubArrays, -1, signed, IllegalArgumentException.class);
            testShortConstructor(oneShortSubArrays, 2, signed, IllegalArgumentException.class);

            // DataBufferShort(short[][] dataArray, int size, int[] offsets)
            testShortConstructor(nullShortArrays, 0, zeroOffsetArray, signed, NullPointerException.class);
            testShortConstructor(zeroShortSubArrays, 0, zeroOffsetArray, signed, IllegalArgumentException.class);
            testShortConstructor(oneShortSubArrays, 0, zeroOffsetArray, signed, IllegalArgumentException.class);
            testShortConstructor(oneShortSubArrays, -1, oneOffsetArray, signed, IllegalArgumentException.class);
            testShortConstructor(oneShortSubArrays, 2, oneOffsetArray, signed, IllegalArgumentException.class);
            testShortConstructor(oneShortSubArrays, 1, oneOffsetArray, signed, IllegalArgumentException.class);
            testShortConstructor(oneShortSubArrays, 1, twoOffsetArray, signed, ArrayIndexOutOfBoundsException.class);
        }

        final int[] nullIntArray = null;
        final int[] zeroIntArray = { };
        final int[] oneIntArray = { 0 } ;
        final int[] oneKIntArray = new int[1000];
        final int[][] nullIntArrays = null;
        final int[][] nullIntSubArrays = { nullIntArray } ;
        final int[][] zeroIntSubArrays = { zeroIntArray } ;
        final int[][] oneIntSubArrays = { oneIntArray } ;

        // DataBufferInt(int size)
        testIntConstructor(-1, IllegalArgumentException.class);
        testIntConstructor(0, IllegalArgumentException.class);

        // DataBufferInt(int size, numBanks)
        testIntConstructor(0, 1, IllegalArgumentException.class);
        testIntConstructor(-1, 0, IllegalArgumentException.class);
        testIntConstructor(-1, 1, IllegalArgumentException.class);

        // DataBufferInt(byte[] dataArray, int size)
        testIntConstructor(nullIntArray, 0, NullPointerException.class);
        testIntConstructor(zeroIntArray, 0, IllegalArgumentException.class);
        testIntConstructor(oneIntArray, -1, IllegalArgumentException.class);
        testIntConstructor(oneIntArray, 2, IllegalArgumentException.class);

        // DataBufferInt(byte[] dataArray, int size, int offset)
        testIntConstructor(nullIntArray, 0, 1, NullPointerException.class);
        testIntConstructor(zeroIntArray, 0, 0, IllegalArgumentException.class);
        testIntConstructor(oneIntArray, -1, 1, IllegalArgumentException.class);
        testIntConstructor(oneIntArray, 2, 1, IllegalArgumentException.class);
        testIntConstructor(oneKIntArray, 1000, Integer.MAX_VALUE - 50, IllegalArgumentException.class);

        // DataBufferInt(byte[][] dataArray, int size)
        testIntConstructor(nullIntArrays, 0, NullPointerException.class);
        testIntConstructor(zeroIntSubArrays, 0, IllegalArgumentException.class);
        testIntConstructor(oneIntSubArrays, -1, IllegalArgumentException.class);
        testIntConstructor(oneIntSubArrays, 2, IllegalArgumentException.class);

        // DataBufferInt(byte[][] dataArray, int size, int[] offsets)
        testIntConstructor(nullIntArrays, 0, zeroOffsetArray, NullPointerException.class);
        testIntConstructor(zeroIntSubArrays, 0, zeroOffsetArray, IllegalArgumentException.class);
        testIntConstructor(oneIntSubArrays, 0, zeroOffsetArray, IllegalArgumentException.class);
        testIntConstructor(oneIntSubArrays, -1, oneOffsetArray, IllegalArgumentException.class);
        testIntConstructor(oneIntSubArrays, 2, oneOffsetArray, IllegalArgumentException.class);
        testIntConstructor(oneIntSubArrays, 1, oneOffsetArray, IllegalArgumentException.class);
        testIntConstructor(oneIntSubArrays, 1, twoOffsetArray, ArrayIndexOutOfBoundsException.class);

        final float[] nullFloatArray = null;
        final float[] zeroFloatArray = { } ;
        final float[] oneFloatArray = { 0 } ;
        final float[] oneKFloatArray = new float[1000];
        final float[][] nullFloatArrays = null;
        final float[][] nullFloatSubArrays = { nullFloatArray } ;
        final float[][] zeroFloatSubArrays = { zeroFloatArray } ;
        final float[][] oneFloatSubArrays = { oneFloatArray } ;

        // DataBufferFloat(int size)
        testFloatConstructor(-1, IllegalArgumentException.class);
        testFloatConstructor(0, IllegalArgumentException.class);

        // DataBufferFloat(int size, numBanks)
        testFloatConstructor(0, 1, IllegalArgumentException.class);
        testFloatConstructor(-1, 0, IllegalArgumentException.class);
        testFloatConstructor(-1, 1, IllegalArgumentException.class);

        // DataBufferFloat(byte[] dataArray, int size)
        testFloatConstructor(nullFloatArray, 0, NullPointerException.class);
        testFloatConstructor(zeroFloatArray, 0, IllegalArgumentException.class);
        testFloatConstructor(oneFloatArray, -1, IllegalArgumentException.class);
        testFloatConstructor(oneFloatArray, 2, IllegalArgumentException.class);

        // DataBufferFloat(byte[] dataArray, int size, int offset)
        testFloatConstructor(nullFloatArray, 0, 1, NullPointerException.class);
        testFloatConstructor(zeroFloatArray, 0, 0, IllegalArgumentException.class);
        testFloatConstructor(oneFloatArray, -1, 1, IllegalArgumentException.class);
        testFloatConstructor(oneFloatArray, 2, 1, IllegalArgumentException.class);
        testFloatConstructor(oneKFloatArray, 1000, Integer.MAX_VALUE - 50, IllegalArgumentException.class);

        // DataBufferFloat(byte[][] dataArray, int size)
        testFloatConstructor(nullFloatArrays, 0, NullPointerException.class);
        testFloatConstructor(zeroFloatSubArrays, 0, IllegalArgumentException.class);
        testFloatConstructor(oneFloatSubArrays, -1, IllegalArgumentException.class);
        testFloatConstructor(oneFloatSubArrays, 2, IllegalArgumentException.class);

        // DataBufferFloat(byte[][] dataArray, int size, int[] offsets)
        testFloatConstructor(nullFloatArrays, 0, zeroOffsetArray, NullPointerException.class);
        testFloatConstructor(zeroFloatSubArrays, 0, zeroOffsetArray, IllegalArgumentException.class);
        testFloatConstructor(oneFloatSubArrays, 0, zeroOffsetArray, IllegalArgumentException.class);
        testFloatConstructor(oneFloatSubArrays, -1, oneOffsetArray, IllegalArgumentException.class);
        testFloatConstructor(oneFloatSubArrays, 2, oneOffsetArray, IllegalArgumentException.class);
        testFloatConstructor(oneFloatSubArrays, 1, oneOffsetArray, IllegalArgumentException.class);
        testFloatConstructor(oneFloatSubArrays, 1, twoOffsetArray, ArrayIndexOutOfBoundsException.class);

        final double[] nullDoubleArray = null;
        final double[] zeroDoubleArray = { };
        final double[] oneDoubleArray = { 0 } ;
        final double[] oneKDoubleArray = new double[1000];
        final double[][] nullDoubleArrays = null;
        final double[][] nullDoubleSubArrays = { nullDoubleArray } ;
        final double[][] zeroDoubleSubArrays = { zeroDoubleArray } ;
        final double[][] oneDoubleSubArrays = { oneDoubleArray } ;

        // DataBufferDouble(int size)
        testDoubleConstructor(-1, IllegalArgumentException.class);
        testDoubleConstructor(0, IllegalArgumentException.class);

        // DataBufferDouble(int size, numBanks)
        testDoubleConstructor(0, 1, IllegalArgumentException.class);
        testDoubleConstructor(-1, 0, IllegalArgumentException.class);
        testDoubleConstructor(-1, 1, IllegalArgumentException.class);

        // DataBufferDouble(byte[] dataArray, int size)
        testDoubleConstructor(nullDoubleArray, 0, NullPointerException.class);
        testDoubleConstructor(zeroDoubleArray, 0, IllegalArgumentException.class);
        testDoubleConstructor(oneDoubleArray, -1, IllegalArgumentException.class);
        testDoubleConstructor(oneDoubleArray, 2, IllegalArgumentException.class);

        // DataBufferDouble(byte[] dataArray, int size, int offset)
        testDoubleConstructor(nullDoubleArray, 0, 1, NullPointerException.class);
        testDoubleConstructor(zeroDoubleArray, 0, 0, IllegalArgumentException.class);
        testDoubleConstructor(oneDoubleArray, -1, 1, IllegalArgumentException.class);
        testDoubleConstructor(oneDoubleArray, 2, 1, IllegalArgumentException.class);

        // DataBufferDouble(byte[][] dataArray, int size)
        testDoubleConstructor(nullDoubleArrays, 0, NullPointerException.class);
        testDoubleConstructor(zeroDoubleSubArrays, 0, IllegalArgumentException.class);
        testDoubleConstructor(oneDoubleSubArrays, -1, IllegalArgumentException.class);
        testDoubleConstructor(oneDoubleSubArrays, 2, IllegalArgumentException.class);
        testDoubleConstructor(oneKDoubleArray, 1000, Integer.MAX_VALUE - 50, IllegalArgumentException.class);

        // DataBufferDouble(byte[][] dataArray, int size, int[] offsets)
        testDoubleConstructor(nullDoubleArrays, 0, zeroOffsetArray, NullPointerException.class);
        testDoubleConstructor(zeroDoubleSubArrays, 0, zeroOffsetArray, IllegalArgumentException.class);
        testDoubleConstructor(oneDoubleSubArrays, 0, zeroOffsetArray, IllegalArgumentException.class);
        testDoubleConstructor(oneDoubleSubArrays, -1, oneOffsetArray, IllegalArgumentException.class);
        testDoubleConstructor(oneDoubleSubArrays, 2, oneOffsetArray, IllegalArgumentException.class);
        testDoubleConstructor(oneDoubleSubArrays, 1, oneOffsetArray, IllegalArgumentException.class);
        testDoubleConstructor(oneDoubleSubArrays, 1, twoOffsetArray, ArrayIndexOutOfBoundsException.class);

        if (failed) {
            throw new RuntimeException("One or more cases failed.");
        }
   }
}
