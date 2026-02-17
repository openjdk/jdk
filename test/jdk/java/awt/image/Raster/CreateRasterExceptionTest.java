/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8255800 8369129 8376297
 * @summary verify Raster + SampleModel creation vs spec.
 */

import java.awt.Point;
import java.awt.image.BandedSampleModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferFloat;
import java.awt.image.Raster;
import java.awt.image.RasterFormatException;

public class CreateRasterExceptionTest {

    static int[] bankIndices = new int[] { 0, 0};
    static int[] negBankIndices = new int[] { -1, 0};
    static int[] bandOffsets = new int[] { 0, 0};
    static int[] bandOffsets2 = new int[] { 0, 0, 0, 0};
    static int[] bandMasks1 = new int[] { 0x0ff };
    static int[] zeroBandOffsets = new int[] {};
    static DataBuffer dBuffer = new DataBufferByte(15);
    static DataBuffer dBuffer1 = new DataBufferByte(1);

    static void noException() {
         Thread.dumpStack();
         throw new RuntimeException("No expected exception");
    }

    /**
      * If running on a JDK of the targetVersion or later, throw
      * a RuntimeException because the exception argument
      * should not have occured. However it is expected on
      * prior versions because that was the previous behaviour.
      * @param targetVersion to check
      * @param t the thrown exception to print
      */
    static void checkIsOldVersion(int targetVersion, Throwable t) {
        String version = System.getProperty("java.version");
        version = version.split("\\D")[0];
        int v = Integer.parseInt(version);
        if (v >= targetVersion) {
            t.printStackTrace();
            throw new RuntimeException(
                           "Unexpected exception for version " + v);
        }
    }

    /* Except a version starting with "17" or higher */
    static void checkIsOldVersion(Throwable t) {
        checkIsOldVersion(17, t);
    }

    public static void main(String[] args) {
         componentSampleModelTests1();
         componentSampleModelTests2();
         bandedSampleModelTests1();
         bandedSampleModelTests2();
         bandedRasterTests1();
         bandedRasterTests2();
         bandedRasterTests3();
         interleavedRasterTests1();
         interleavedRasterTests2();
         interleavedRasterTests3();
         packedRasterTests1();
         packedRasterTests2();
         packedRasterTests3();
         packedRasterTests4();
         System.out.println();
         System.out.println(" ** Test Passed **");
    }


    /*   public ComponentSampleModel(int dataType,
     *                          int w, int h,
     *                          int pixelStride,
     *                          int scanlineStride,
     *                          int[] bandOffsets);
     */
    static void componentSampleModelTests1() {

        System.out.println();
        System.out.println("** componentSampleModelTests1");

        try {
            /* @throws IllegalArgumentException if {@code w} and
             * {@code h} are not both greater than 0
             */
            new ComponentSampleModel(DataBuffer.TYPE_INT, -5, 1, 3, 15,
                                     bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                         "Got expected exception for negative width");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the product of
             * {@code w} and {@code h} is greater than
             * {@code Integer.MAX_VALUE}
             */
            new ComponentSampleModel(DataBuffer.TYPE_INT,
                   Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2,
                   3, 15, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                        "Got expected exception for exceeding max int");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException if {@code pixelStride}
              * is less than 0
              */
            new ComponentSampleModel(DataBuffer.TYPE_INT, 5, 1, -3, 15,
                                     bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                "Got expected exception for negative pixel stride");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException if
              * {@code scanlineStride} is less than 0
              */
            new ComponentSampleModel(DataBuffer.TYPE_INT, 5, 1, 3, -15,
                                     bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                "Got expected exception for negative scanline stride");
            System.out.println(t);
        }

        try {
            /* @throws NullPointerException if {@code bandOffsets} is
             * {@code null}
             */
            new ComponentSampleModel(DataBuffer.TYPE_INT, 5, 1, 3, 15,
                                     bankIndices, null);
            noException();
        } catch (NullPointerException t) {
            System.out.println(
                        "Got expected exception for null bandOffsets");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if
             * {@code bandOffsets.length}is 0
             */
            new ComponentSampleModel(DataBuffer.TYPE_INT, 5, 1, 3, 15,
                                     bankIndices, zeroBandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                        "Got expected exception for 0 bandOffsets");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code dataType} is
             * not one of the supported data types for this sample model
             */
            new ComponentSampleModel(-1234, 5, 1, 3, 15,
                                     bankIndices, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                      "Got expected exception for bad databuffer type");
            System.out.println(t);
        }
    }

    /* public ComponentSampleModel(int dataType,
     *                          int w, int h,
     *                          int pixelStride,
     *                          int scanlineStride,
     *                          int[] bankIndices,
     *                          int[] bandOffsets);
     */
    static void componentSampleModelTests2() {

        System.out.println();
        System.out.println("** componentSampleModelTests2");

        try {
            /* @throws IllegalArgumentException if {@code w}
             * and {@code h} are not both greater than 0
             */
            new ComponentSampleModel(DataBuffer.TYPE_INT, -5, 1, 3, 15,
                                     bankIndices, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                     "Got expected exception for negative width");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the product of
             * {@code w} and {@code h} is greater than
             * {@code Integer.MAX_VALUE}
             */
            new ComponentSampleModel(DataBuffer.TYPE_INT,
                   Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2,
                   3, 15, bankIndices, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                     "Got expected exception for exceeding max int");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException if {@code pixelStride}
              * is less than 0
              */
            new ComponentSampleModel(DataBuffer.TYPE_INT, 5, 1, -3, 15,
                                     bankIndices, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                "Got expected exception for negative pixel stride");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException if
              * {@code scanlineStride} is less than 0
              */
            new ComponentSampleModel(DataBuffer.TYPE_INT, 5, 1, 3, -15,
                                     bankIndices, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                "Got expected exception for negative scanline stride");
            System.out.println(t);
        }

        try {
         /*
          * @throws NullPointerException if {@code bankIndices}
          *  is {@code null}
          */
             new ComponentSampleModel(DataBuffer.TYPE_INT, 5, 1, 3, 15,
                                      null, bandOffsets);
            noException();
        } catch (NullPointerException t) {
            System.out.println(
                   "Got expected exception for null bankIndices");
            System.out.println(t);
        }

        try {
            /* @throws NullPointerException if {@code bandOffsets} is
             * {@code null}
             */
            new ComponentSampleModel(DataBuffer.TYPE_INT, 5, 1, 3, 15,
                                     bankIndices, null);
            noException();
        } catch (NullPointerException t) {
            System.out.println(
                   "Got expected exception for null bandOffsets");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if
             * {@code bandOffsets.length} is 0
             */
            new ComponentSampleModel(DataBuffer.TYPE_INT, 5, 1, 3, 15,
                                     bankIndices, zeroBandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for 0 bandOffsets");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the length of
             * {@code bankIndices} does not equal the length of
             * {@code bandOffsets}
             */
            new ComponentSampleModel(DataBuffer.TYPE_INT, 5, 1, 3, 15,
                                     bankIndices, bandOffsets2);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for " +
                "bandOffsets.length != bankIndices.length");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the length of
             * {@code bankIndices} does not equal the length of
             * {@code bandOffsets}
             */
            new ComponentSampleModel(DataBuffer.TYPE_INT, 5, 1, 3, 15,
                                     negBankIndices, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for " +
                "negative bank Index");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code dataType} is
             * not one of the supported data types for this sample model
             */
            new ComponentSampleModel(-1234, 5, 1, 3, 15,
                                     bankIndices, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad databuffer type");
            System.out.println(t);
        }
    }

    /* public BandedSampleModel(int dataType, int w, int h,
     * int numBands);
     */
    static void bandedSampleModelTests1() {

        System.out.println();
        System.out.println("** bandedSampleModelTests1");

        try {
            /* @throws IllegalArgumentException if {@code w} and
             * {@code h} are not both greater than 0
             */
            new BandedSampleModel(DataBuffer.TYPE_INT, -5, 1, 1);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for negative width");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the product of
             * {@code w} and {@code h} is greater than
             * {@code Integer.MAX_VALUE}
             */
            new BandedSampleModel(DataBuffer.TYPE_INT,
                   Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2, 1);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for exceeding max int");
            System.out.println(t);
        }

        /* Testing this both with 0 and negative (next test) */
        try {
            /* @throws IllegalArgumentException if {@code numBands}
             *  is <= 0
             */
            new BandedSampleModel(DataBuffer.TYPE_INT, 5, 1, 0);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for 0 numBands");
            System.out.println(t);
        }

        /* Before JDK 17, a negative value for num bands would throw
         * NegativeArraySizeException, but a zero value would throw
         * IllegalArgumentException so allow NegativeArraySizeException
         * on < 17 here to make it easier to run this test on both
         * versions and verify all behaviours.
         */
        try {
            /* @throws IllegalArgumentException if {@code numBands}
             *  is <= 0
             */
            new BandedSampleModel(DataBuffer.TYPE_INT, 5, 1, -1);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for < 0 numBands");
            System.out.println(t);
        } catch (NegativeArraySizeException t) {
            checkIsOldVersion(t);
            System.out.println(
                   "Got expected exception for < 0 numBands");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code dataType}
             * is not one of the supported data types for this
             * sample model
             */
            new BandedSampleModel(-1234, 5, 1, 3);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad databuffer type");
            System.out.println(t);
        }
    }

    /*
     * public BandedSampleModel(int dataType,
     *                          int w, int h,
     *                          int scanlineStride,
     *                          int[] bankIndices,
     *                          int[] bandOffsets);
     */
    static void bandedSampleModelTests2() {

        System.out.println();
        System.out.println("** bandedSampleModelTests2");

        try {
            /* @throws IllegalArgumentException if {@code w} and
             * {@code h} are not both greater than 0
             */
            new BandedSampleModel(DataBuffer.TYPE_INT, -5, 1, 15,
                                     bankIndices, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                      "Got expected exception for negative width");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the product of
             * {@code w} and {@code h} is greater than
             * {@code Integer.MAX_VALUE}
             */
            new BandedSampleModel(DataBuffer.TYPE_INT,
                   Integer.MAX_VALUE / 8, Integer.MAX_VALUE / 8,
                   Integer.MAX_VALUE, bankIndices, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for exceeding max int");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException if
              * {@code scanlineStride} is less than 0
              */
            new BandedSampleModel(DataBuffer.TYPE_INT, 5, 1, -15,
                                     bankIndices, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                "Got expected exception for negative scanline stride");
            System.out.println(t);
        }

        try {
         /*
          * @throws NullPointerException if {@code bankIndices}
          * is {@code null}
          */
             new BandedSampleModel(DataBuffer.TYPE_INT, 5, 1, 15,
                                      null, bandOffsets);
            noException();
        } catch (NullPointerException t) {
            System.out.println(
                   "Got expected exception for null bankIndices");
            System.out.println(t);
        }

        try {
            /* @throws NullPointerException if {@code bandOffsets} is
             * {@code null}
             */
            new BandedSampleModel(DataBuffer.TYPE_INT, 5, 1, 15,
                                     bankIndices, null);
            noException();
        } catch (NullPointerException t) {
            System.out.println(
                   "Got expected exception for null bandOffsets");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if
             * {@code bandOffsets.length} is 0
             */
            new BandedSampleModel(DataBuffer.TYPE_INT, 5, 1, 15,
                                     bankIndices, zeroBandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for 0 bandOffsets");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the length of
             * {@code bankIndices} does not equal the length of
             * {@code bandOffsets}
             */
            new BandedSampleModel(DataBuffer.TYPE_INT, 5, 1, 15,
                                     bankIndices, bandOffsets2);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for " +
                "bandOffsets.length != bankIndices.length");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the length of
             * {@code bankIndices} does not equal the length of
             * {@code bandOffsets}
             */
            new BandedSampleModel(DataBuffer.TYPE_INT, 5, 1, 15,
                                     negBankIndices, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for " +
                "negative bank Index");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code dataType} is
             * not one of the supported data types for this sample model
             */
            new BandedSampleModel(-1234, 5, 1, 15,
                                     bankIndices, bandOffsets);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad databuffer type");
            System.out.println(t);
        }
    }

      /* createBandedRaster(int dataType, int w, int h,
       *                    int bands, Point location);
       */
    static void bandedRasterTests1() {

        System.out.println();
        System.out.println("** bandedRasterTests1");

        Point p = new Point();

        try {
            /* @throws IllegalArgumentException if {@code w} and
             * {@code h} are not both greater than 0
             */
            /* Old API had @throws RasterFormatException if w or h < 0
             * Old JDK never actually does. And it is worse.
             * If one or the other is zero, we get IAE.
             * If one is positive, the other negative
             * we get NegativeArraySizeException.
             * If BOTH are negative, we are back to IAE.
             * This needs to be consistent.
             */
            Raster.createBandedRaster(DataBuffer.TYPE_INT,
                                      1,  -1, 3, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for width <= 0");
            System.out.println(t);
        } catch (NegativeArraySizeException t) {
            checkIsOldVersion(t);
            System.out.println("Got expected exception for width <= 0");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the product of
             * {@code w} and {@code h} is greater than
             * {@code Integer.MAX_VALUE}
             */
            Raster.createBandedRaster(DataBuffer.TYPE_INT,
                    Integer.MAX_VALUE/8, Integer.MAX_VALUE/8, 3, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        } catch (NegativeArraySizeException t) {
            checkIsOldVersion(t);
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if computing either
             * {@code location.x + w} or
             * {@code location.y + h} results in integer overflow
             */
            Point pt = new Point(5, 1);
            Raster.createBandedRaster(DataBuffer.TYPE_INT,
                    Integer.MAX_VALUE-2, 1, 1, pt);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        } catch (NegativeArraySizeException | OutOfMemoryError t) {
            checkIsOldVersion(t);
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
            /* @throws ArrayIndexOutOfBoundsException if {@code bands}
             *         is less than 1
             */
            Raster.createBandedRaster(DataBuffer.TYPE_INT,
                                      1, 1, 0, null);
            noException();
        } catch (ArrayIndexOutOfBoundsException t) {
            System.out.println("Got expected exception for zero bands");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code dataType} is
             * not one of the supported data types for this sample model
             */
            Raster.createBandedRaster(DataBuffer.TYPE_FLOAT,
                                       5, 1, 3, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad databuffer type");
            System.out.println(t);
        }
    }

    /*
     *  createBandedRaster(int dataType,
     *                     int w, int h,
     *                     int scanlineStride,
     *                     int[] bankIndices,
     *                     int[] bandOffsets,
     *                     Point location)
     */
    static void bandedRasterTests2() {

        System.out.println();
        System.out.println("** bandedRasterTests2");

        Point p = new Point();

        try {
            /* @throws IllegalArgumentException if {@code w} and
             * {@code h} are not both greater than 0
             */
            /* Old API had @throws RasterFormatException if w or h < 0
             * Old JDK never actually does. And it is worse.
             * If one or the other * is zero, we get IAE.
             * If one is positive, the other negative
             * we get NegativeArraySizeException.
             * If BOTH are negative, we are back to IAE.
             * This needs to be consistent.
             */
            Raster.createBandedRaster(DataBuffer.TYPE_INT, 1, -1, 3,
                                      bankIndices, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for width <= 0");
            System.out.println(t);
        } catch (NegativeArraySizeException t) {
            checkIsOldVersion(t);
            System.out.println("Got expected exception for width <= 0");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the product of
             * {@code w} and {@code h} is greater than
             * {@code Integer.MAX_VALUE}
             */
            Raster.createBandedRaster(DataBuffer.TYPE_INT,
                    Integer.MAX_VALUE/8, Integer.MAX_VALUE/8,
                    Integer.MAX_VALUE,
                    bankIndices, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if computing either
             * {@code location.x + w} or
             * {@code location.y + h} results in integer overflow
             */
            Point pt = new Point(5, 1);
            Raster.createBandedRaster(DataBuffer.TYPE_INT,
                    Integer.MAX_VALUE-2, 1,
                    Integer.MAX_VALUE-2, bankIndices, bandOffsets, pt);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        } catch (NegativeArraySizeException t) {
            checkIsOldVersion(t);
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException if
              * {@code scanlineStride} is less than 0
              */
            Raster.createBandedRaster(DataBuffer.TYPE_INT, 10, 10, -1000,
                                      bankIndices, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                "Got expected exception for negative scanline stride");
            System.out.println(t);
        }

        try {
            /* @throws ArrayIndexOutOfBoundsException if
             * {@code bankIndices} is null
             */
            Raster.createBandedRaster(DataBuffer.TYPE_INT, 1, 1, 1,
                                      null, bandOffsets, null);
            noException();
        } catch (ArrayIndexOutOfBoundsException t) {
            System.out.println(
                   "Got expected exception for null bankIndices");
            System.out.println(t);
        }

        try {
            /* @throws NullPointerException if {@code bandOffsets}
             * is null
             */
            Raster.createBandedRaster(DataBuffer.TYPE_INT, 1, 1, 1,
                                      bankIndices, null, null);
            noException();
        } catch (NullPointerException t) {
            System.out.println(
                   "Got expected exception for null bandoffsets");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the lengths of {@code bankIndices}
             *         and {@code bandOffsets} are different.
             */
            Raster.createBandedRaster(DataBuffer.TYPE_INT, 1, 1, 1,
                                      bankIndices, bandOffsets2, null);
            noException();
        } catch (ArrayIndexOutOfBoundsException t) {
          checkIsOldVersion(26, t);
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for different array lengths");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code dataType} is
             * not one of the supported data types for this sample model
             */
            Raster.createBandedRaster(DataBuffer.TYPE_FLOAT, 5, 1, 3,
                                      bankIndices, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad databuffer type");
            System.out.println(t);
        }
    }

    /*
     *  createBandedRaster(DataBuffer dataBuffer,
     *                     int w, int h,
     *                     int scanlineStride,
     *                     int[] bankIndices,
     *                     int[] bandOffsets,
     *                     Point location)
     */
    static void bandedRasterTests3() {

        System.out.println();
        System.out.println("** bandedRasterTests3");

        Point p = new Point();

        try {
            /* @throws IllegalArgumentException if {@code w} and
             * {@code h} are not both greater than 0
             */
            Raster.createBandedRaster(dBuffer, 1, -1, 3,
                                      bankIndices, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for width <= 0");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the product of
             * {@code w} and {@code h} is greater than
             * {@code Integer.MAX_VALUE}
             */
            Raster.createBandedRaster(dBuffer,
                    Integer.MAX_VALUE/8, Integer.MAX_VALUE/8,
                    Integer.MAX_VALUE, bankIndices, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        } catch (NegativeArraySizeException t) {
        }

        try {
            /* @throws RasterFormatException if computing either
             * {@code location.x + w} or
             * {@code location.y + h} results in integer overflow
             */
            Point pt = new Point(5, 1);
            Raster.createBandedRaster(dBuffer,
                    Integer.MAX_VALUE-2, 1, Integer.MAX_VALUE,
                    bankIndices, bandOffsets, pt);
            noException();
        } catch (RasterFormatException t) {
            System.out.println(
                   "Got expected raster exception for overflow");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException if
              * {@code scanlineStride} is less than 0
              */
            Raster.createBandedRaster(dBuffer, 1, 1, -3,
                                      bankIndices, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                "Got expected exception for negative scanline stride");
            System.out.println(t);
        }

        try {
            /* @throws NullPointerException if {@code bankIndices}
             *         is null
             */
            Raster.createBandedRaster(dBuffer, 1, 1, 0,
                                      null, bandOffsets, null);
            noException();
        } catch (NullPointerException t) {
            System.out.println(
                   "Got expected exception for null bankIndices");
            System.out.println(t);
        }

        try {
            /* @throws NullPointerException if {@code dataBuffer}
             * is null
             */
            Raster.createBandedRaster(null, 1, 1, 3,
                                      bankIndices, bandOffsets, null);
            noException();
        } catch (NullPointerException t) {
            System.out.println(
                   "Got expected exception for null dataBuffer");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the length of
             * {@code bankIndices} does not  equal the length of
             *  {@code bandOffsets}
             */
            Raster.createBandedRaster(dBuffer, 1, 1, 3,
                                      bankIndices, bandOffsets2, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for different arrlen");
            System.out.println(t);
        }

        try {
            /* @throws NullPointerException if {@code bandOffsets}
             * is null
             */
            Raster.createBandedRaster(dBuffer, 1, 1, 0,
                                      bankIndices, null, null);
            noException();
        } catch (NullPointerException t) {
            System.out.println(
                   "Got expected exception for null bandoffsets");
            System.out.println(t);
        }

        try {
            /* @throws ArrayIndexOutOfBoundsException if any element of {@code bankIndices}
             *         is greater or equal to the number of bands in {@code dataBuffer}
             */
            int[] indices = new int[] { 0, 1, 2 };
            int[] offsets = new int[] { 0, 0, 0 };
            Raster.createBandedRaster(dBuffer, 1, 1, 1,
                                      indices, offsets, null);
            noException();
        } catch (ArrayIndexOutOfBoundsException t) {
            System.out.println(
                   "Got expected exception for bad bank index");
            System.out.println(t);
        }

        try {
            /*
             * @throws IllegalArgumentException if {@code dataType}
             * is not one of the supported data types, which are
             * {@code DataBuffer.TYPE_BYTE},
             * {@code DataBuffer.TYPE_USHORT}
             * or {@code DataBuffer.TYPE_INT},
             */
            DataBufferFloat dbFloat = new DataBufferFloat(20);
            Raster.createBandedRaster(dbFloat, 1, 1, 3,
                                      bankIndices, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad dataBuffer");
            System.out.println(t);
        }
    }

    /* createInterleavedRaster(int dataType, int w, int h,
     *                        int bands, Point location);
     */
    static void interleavedRasterTests1() {

        System.out.println();
        System.out.println("** interleavedRasterTests1");

        Point p = new Point();

        try {
            /* @throws IllegalArgumentException if {@code w}
             * and {@code h} are not both greater than 0
             */
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                                            1, -1, 3, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for width <= 0");
            System.out.println(t);
        } catch (NegativeArraySizeException t) {
            checkIsOldVersion(t);
            System.out.println("Got expected exception for width <= 0");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the product of
             * {@code w} and {@code h} is greater than
             * {@code Integer.MAX_VALUE}
             */
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                    Integer.MAX_VALUE/8, Integer.MAX_VALUE/8, 1, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        } catch (NegativeArraySizeException t) {
            checkIsOldVersion(t);
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
            /* @throws RasterFormatException if computing either
             * {@code location.x + w} or
             * {@code location.y + h} results in integer overflow
             */
            Point pt = new Point(5, 1);
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                    Integer.MAX_VALUE-2, 1, 1, pt);
            noException();
        } catch (RasterFormatException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code bands}
             *         is less than 1
             */
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                                            1, 1, 0, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception zero bands");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code dataType} is
             * not one of the supported data types for this sample model
             */
             Raster.createInterleavedRaster(DataBuffer.TYPE_INT,
                                                5, 1, 3, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad databuffer type");
            System.out.println(t);
        }
    }

     /* createInterleavedRaster(int dataType,
      *                         int w, int h,
      *                         int scanlineStride,
      *                         int pixelStride,
      *                         int[] bandOffsets,
      *                         Point location)
      */
    static void interleavedRasterTests2() {

        System.out.println();
        System.out.println("** interleavedRasterTests2 ");

        Point p = new Point();

        try {
            /* @throws IllegalArgumentException if {@code w}
             * and {@code h} are not both greater than 0
             */
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                                       1, -1, 3, 1, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for width <= 0");
            System.out.println(t);
        } catch (NegativeArraySizeException t) {
            checkIsOldVersion(t);
            System.out.println("Got expected exception for width <= 0");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the product of
             * {@code w} and {@code h} is greater than
             * {@code Integer.MAX_VALUE}
             */
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                    Integer.MAX_VALUE/8, Integer.MAX_VALUE/8,
                    Integer.MAX_VALUE/2 , 1,
                    bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        } catch (NegativeArraySizeException t) {
            checkIsOldVersion(t);
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the data size
             * needs to store all lines is greater than
             * {@code Integer.MAX_VALUE}
             */
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                    1000, 1000,
                    Integer.MAX_VALUE/2 , 1,
                    bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        } catch (NegativeArraySizeException t) {
            checkIsOldVersion(26, t);
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
            /* @throws RasterFormatException if computing either
             * {@code location.x + w} or
             * {@code location.y + h} results in integer overflow
             */
            Point pt = new Point(5, 1);
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                    Integer.MAX_VALUE-2, 1,
                    Integer.MAX_VALUE, 1, bandOffsets, pt);
            noException();
        } catch (RasterFormatException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException if
              * {@code scanlineStride} is less than or equal to 0
              */
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                                      1, 1, 0, 1, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                "Got expected exception for negative scanline stride");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException if {@code pixelStride}
              * is less than or equal to 0
              */
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                                      1, 1, 3, 0, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for pixelStride < 0");
            System.out.println(t);
        } catch (RasterFormatException t) {
            checkIsOldVersion(26, t);
        } catch (NegativeArraySizeException t) {
            checkIsOldVersion(t);
            System.out.println(
                   "Got expected exception for pixelStride < 0");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if (w * pixelStride)
             * is greater than scanlineStride
             */
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                                  1, 1, 0, 1, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for incorrect stride");
            System.out.println(t);
        }

        try {
            /* @throws NullPointerException if {@code bandOffsets}
             * is null
             */
            Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                                  1, 1, 1, 1, null, null);
            noException();
        } catch (NullPointerException t) {
            System.out.println(
                   "Got expected exception for null bandoffsets");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code dataType} is
             * not one of the supported data types for this sample model
             */
            Raster.createInterleavedRaster(DataBuffer.TYPE_INT,
                                        5, 1, 3, 1, bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad databuffer type");
            System.out.println(t);
        }
    }

     /* createInterleavedRaster(DataBuffer dBuffer,
      *                         int w, int h,
      *                         int scanlineStride,
      *                         int pixelStride,
      *                         int[] bandOffsets,
      *                         Point location)
      */
    static void interleavedRasterTests3() {

        System.out.println();
        System.out.println("** interleavedRasterTests3 ");

        Point p = new Point();

        try {
            /* @throws IllegalArgumentException if {@code w}
             * and {@code h} are not both greater than 0
             */
            Raster.createInterleavedRaster(dBuffer, 1, -1, 3, 1,
                                      bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for width <= 0");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the product of
             * {@code w} and {@code h} is greater than
             * {@code Integer.MAX_VALUE}
             */
            Raster.createInterleavedRaster(dBuffer,
                    Integer.MAX_VALUE/8, Integer.MAX_VALUE/8,
                     Integer.MAX_VALUE/4, 1,
                    bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
            /* @throws RasterFormatException if computing either
             * {@code location.x + w} or
             * {@code location.y + h} results in integer overflow
             */
            Point pt = new Point(5, 1);
            Raster.createInterleavedRaster(dBuffer,
                    Integer.MAX_VALUE-2, 1,
                    Integer.MAX_VALUE, 1, bandOffsets, pt);
            noException();
        } catch (RasterFormatException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException
              * if {@code scanlineStride} is less than 0
              */
            Raster.createInterleavedRaster(dBuffer, 5, 1, -15, 1,
                                      bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                "Got expected exception for negative scanline stride");
            System.out.println(t);
        }

        try {
             /* @throws RasterFormatException if {@code dataBuffer} is too small.
              */
            Raster.createInterleavedRaster(dBuffer1, 5, 1, 15, 1,
                                      bandOffsets, null);
            noException();
        } catch (RasterFormatException t) {
            System.out.println(
                "Got expected exception for databuffer too small");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException if {@code pixelStride}
              * is less than 0
              */
            Raster.createInterleavedRaster(dBuffer, 5, 1, 15, -1,
                                      bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for pixelStride < 0");
            System.out.println(t);
        } catch (NegativeArraySizeException t) {
            checkIsOldVersion(t);
            System.out.println(
                   "Got expected exception for pixelStride < 0");
            System.out.println(t);
        }

        try {
            /* @throws NullPointerException if {@code bandOffsets}
             * is null
             */
            Raster.createInterleavedRaster(dBuffer, 5, 1, 15, 1,
                                      null, null);
            noException();
        } catch (NullPointerException t) {
            System.out.println(
                   "Got expected exception for null bandoffsets");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code dataBuffer}
             * is not one of the supported data types
             */
            DataBufferFloat dbFloat = new DataBufferFloat(20);
            Raster.createInterleavedRaster(dbFloat, 5, 1, 15, 1,
                                      bandOffsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad databuffer type");
            System.out.println(t);
        }

        try {
            /* @throws RasterFormatException if {@code dataBuffer}
             * has more than one bank.
             */
            DataBufferByte dbb = new DataBufferByte(100, 2);
            Raster.createInterleavedRaster(dbb, 5, 1, 15, 1,
                                      bandOffsets, null);
            noException();
        } catch (RasterFormatException t) {
            System.out.println(
                   "Got expected exception for bad databuffer banks");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException if any element of {@code bandOffsets} is greater
             *  than {@code pixelStride} or the {@code scanlineStride}
             */
            int[] offsets = new int[] { 0, 1, 2};
            Raster.createInterleavedRaster(dBuffer,
                                  1, 1, 1, 1, offsets, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for element too large");
            System.out.println(t);
        }

    }

    /*  createPackedRaster(int dataType,
     *                     int w, int h,
     *                     int[] bandMasks,
     *                     Point location)
     *
     */
     static void packedRasterTests1() {

        System.out.println();
        System.out.println("** packedRasterTests1");

         try {
             /* @throws IllegalArgumentException if {@code w} and {@code h}
              *         are not both greater than 0
              */
             Raster.createPackedRaster(DataBuffer.TYPE_BYTE, 0, 0, bandMasks1, null);
            noException();
         } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for zero w / h");
            System.out.println(t);
         }

         try {
             /* @throws IllegalArgumentException if {@code w} * {@code h}
              *         is greater than {@code Integer.MAX_VALUE}
              */
             Raster.createPackedRaster(DataBuffer.TYPE_BYTE,
                                       Integer.MAX_VALUE/10, Integer.MAX_VALUE/10,
                                       bandMasks1, null);
            noException();
         } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for overflow");
            System.out.println(t);
         }

        try {
            /* @throws RasterFormatException if computing either
             * {@code location.x + w} or
             * {@code location.y + h} results in integer overflow
             */
            Point pt = new Point(5, 1);
            Raster.createPackedRaster(DataBuffer.TYPE_BYTE,
                                      Integer.MAX_VALUE-2, 1,
                                      bandMasks1, pt);
            noException();
        } catch (RasterFormatException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code dataType}
             * is not one of the supported data types
             */
            Raster.createPackedRaster(1000, 1, 1, bandMasks1, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad databuffer type");
            System.out.println(t);
        }
     }

    /*  createPackedRaster(int dataType,
     *                     int w, int h,
     *                     int bands,
     *                     int bitsPerBand,
     *                     Point location)
     */
     static void packedRasterTests2() {

        System.out.println();
        System.out.println("** packedRasterTests2");

         try {
             /* @throws IllegalArgumentException if {@code w} and {@code h}
              *         are not both greater than 0
              */
             Raster.createPackedRaster(DataBuffer.TYPE_BYTE, 0, 0, 1, 8, null);
             noException();
         } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for zero w / h");
            System.out.println(t);
         }

         try {
             /* @throws IllegalArgumentException if {@code w} * {@code h}
              *         is greater than {@code Integer.MAX_VALUE}
              */
             Raster.createPackedRaster(DataBuffer.TYPE_BYTE,
                                       Integer.MAX_VALUE/10, Integer.MAX_VALUE/10,
                                       1, 8, null);
             noException();
         } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for overflow");
            System.out.println(t);
         }

        try {
            /* @throws RasterFormatException if computing either
             * {@code location.x + w} or
             * {@code location.y + h} results in integer overflow
             */
            Point pt = new Point(5, 1);
            Raster.createPackedRaster(DataBuffer.TYPE_BYTE,
                                      Integer.MAX_VALUE-2, 1,
                                      1, 8, pt);
            noException();
        } catch (RasterFormatException t) {
            System.out.println("Got expected exception for overflow");
            System.out.println(t);
        }

        try {
             /* @throws IllegalArgumentException if {@code bitsPerBand} or
              *         {@code bands} is not greater than zero
              */
            Raster.createPackedRaster(DataBuffer.TYPE_BYTE, 1, 1,
                                       0, 8, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for 0 bands");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code bitsPerBand} or
             *         {@code bands} is not greater than zero
             */
            Raster.createPackedRaster(DataBuffer.TYPE_BYTE, 1, 1,
                                       8, 0, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for 0 bitsPerBand");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if the product of
             *         {@code bitsPerBand} and {@code bands} is
             *         greater than the number of bits held by
             *         {@code dataType}
             */
            Raster.createPackedRaster(DataBuffer.TYPE_BYTE, 1, 1,
                                       2, 8, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bands per sample");
            System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code dataType}
             * is not one of the supported data types
             */
            Raster.createPackedRaster(1000, 1, 1, 1, 8, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad databuffer type");
            System.out.println(t);
        }
    }

    /*  createPackedRaster(DataBuffer dataBuffer,
     *                     int w, int h,
     *                     int scanlineStride,
     *                     int[] bandMasks,
     *                     Point location)
     */
    static void packedRasterTests3() {

        System.out.println();
        System.out.println("** packedRasterTests3");

        try {
             /* @throws IllegalArgumentException if {@code w} and {@code h}
              *         are not both greater than 0
              */
            Raster.createPackedRaster(dBuffer, 0, 1, 1, bandMasks1, null);
           noException();
        } catch (IllegalArgumentException t) {
           System.out.println(
                  "Got expected exception for zero w / h");
           System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code w} * {@code h}
             *         is greater than {@code Integer.MAX_VALUE}
             */
            Raster.createPackedRaster(dBuffer,
                                      Integer.MAX_VALUE/10, Integer.MAX_VALUE/10,
                                      1, bandMasks1, null);
           noException();
        } catch (IllegalArgumentException t) {
           System.out.println(
                  "Got expected exception for overflow");
           System.out.println(t);
        }

       try {
           /* @throws RasterFormatException if computing either
            * {@code location.x + w} or
            * {@code location.y + h} results in integer overflow
            */
           Point pt = new Point(5, 1);
           Raster.createPackedRaster(dBuffer,
                                     Integer.MAX_VALUE-2, 1,
                                     1, bandMasks1, pt);
           noException();
       } catch (RasterFormatException t) {
           System.out.println("Got expected exception for overflow");
           System.out.println(t);
       }

       try {
            /* @throws NullPointerException if databuffer is null.
             */
            Raster.createPackedRaster(null,
                                      1, 1,
                                      1, bandMasks1, null);
           noException();
       } catch (NullPointerException t) {
          System.out.println(
                  "Got expected exception for null data buffer");
           System.out.println(t);
       }

       try {
            /* @throws RasterFormatException if {@code dataBuffer}
             * has more than one bank.
             */
            DataBufferByte dbuffer2 = new DataBufferByte(20, 2);
            Raster.createPackedRaster(dbuffer2, 1, 1, 1, bandMasks1, null);
            noException();
        } catch (RasterFormatException t) {
            System.out.println(
                   "Got expected exception for bad databuffer banks");
            System.out.println(t);
        }
       try {
            /* @throws IllegalArgumentException if {@code dataBuffer}
             * is not one of the supported data types
             */
            DataBufferFloat dbFloat = new DataBufferFloat(20);
            Raster.createPackedRaster(dbFloat, 1, 1, 1, bandMasks1, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad databuffer type");
            System.out.println(t);
        }
   }

    /* createPackedRaster(DataBuffer dataBuffer,
     *               int w, int h,
     *               int bitsPerPixel,
     *               Point location)
     */
    static void packedRasterTests4() {

        System.out.println();
        System.out.println("** packedRasterTests4");

        try {
             /* @throws IllegalArgumentException if {@code w} and {@code h}
              *         are not both greater than 0
              */
            Raster.createPackedRaster(dBuffer, 0, 1, 8, null);
           noException();
        } catch (IllegalArgumentException t) {
           System.out.println(
                  "Got expected exception for zero w / h");
           System.out.println(t);
        }

        try {
            /* @throws IllegalArgumentException if {@code w} * {@code h}
             *         is greater than {@code Integer.MAX_VALUE}
             */
            Raster.createPackedRaster(dBuffer,
                                      Integer.MAX_VALUE/10, Integer.MAX_VALUE/10,
                                      8, null);
           noException();
        } catch (IllegalArgumentException t) {
           System.out.println(
                  "Got expected exception for overflow");
           System.out.println(t);
        }

       try {
           /* @throws RasterFormatException if computing either
            * {@code location.x + w} or
            * {@code location.y + h} results in integer overflow
            */
           Point pt = new Point(5, 1);
           Raster.createPackedRaster(dBuffer,
                                     Integer.MAX_VALUE-2, 1,
                                     8, pt);
           noException();
       } catch (RasterFormatException t) {
           System.out.println("Got expected exception for overflow");
           System.out.println(t);
       }

       try {
            /* @throws IllegalArgumentException if {@code dataBuffer}
             * is not one of the supported data types
             */
            DataBufferFloat dbFloat = new DataBufferFloat(20);
            Raster.createPackedRaster(dbFloat, 1, 1, 8, null);
            noException();
        } catch (IllegalArgumentException t) {
            System.out.println(
                   "Got expected exception for bad databuffer type");
            System.out.println(t);
        }

        try {
            /* @throws RasterFormatException if {@code dataBuffer}
             * has more than one bank.
             */
            DataBufferByte dbb = new DataBufferByte(100, 2);
            Raster.createPackedRaster(dbb, 1, 1, 8, null);
            noException();
        } catch (RasterFormatException t) {
            System.out.println(
                   "Got expected exception for bad databuffer banks");
            System.out.println(t);
        }

        try {
            /* @throws NullPointerException if databuffer is null.
             */
           Raster.createPackedRaster(null, 1, 1, 8, null);
           noException();
       } catch (NullPointerException t) {
          System.out.println(
                  "Got expected exception for null data buffer");
           System.out.println(t);
       }

       int[] badbpp = { 0, 6, 16 };
       for (int bpp : badbpp) {
           try {
               /* @throws RasterFormatException if {@code bitsPixel} is less than 1 or
                * not a power of 2 or exceeds the {@code dataBuffer} element size.
                */
               System.out.println("Test bpp=" + bpp);
               Raster.createPackedRaster(dBuffer, 1, 1, bpp, null);
               noException();
           } catch (RasterFormatException t) {
              System.out.println(
                      "Got expected exception for bitsPerPixel");
               System.out.println(t);
           } catch (ArithmeticException t) {
               checkIsOldVersion(26, t);
               if (bpp != 0) {
                   throw new RuntimeException("Unexpected arithmetic exception");
               }
               System.out.println("Got expected arithmetic exception");
               System.out.println(t);
        }
       }
    }
}
