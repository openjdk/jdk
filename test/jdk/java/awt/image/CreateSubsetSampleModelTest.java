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
 * @bug 8378464
 * @summary test SampleModel.createSubsetSampleModel()
 */

import static java.awt.image.DataBuffer.*;
import java.awt.image.BandedSampleModel;
import java.awt.image.SampleModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.RasterFormatException;
import java.awt.image.SinglePixelPackedSampleModel;

public class CreateSubsetSampleModelTest {

    static final int[] nullbands = null;
    static final int[] zerobands = { };
    static final int[] oneband = { 0 };
    static final int[] twobands = { 0, 1 };
    static final int[] threebands = { 0, 1, 2 };
    static final int[] badbands = { 99 };

    public static void main(String[] args) {

        cons(nullbands, NullPointerException.class);
        cons(zerobands, IllegalArgumentException.class);
        cons(oneband, null);

        PixelInterleavedSampleModel psm2bands =
            new PixelInterleavedSampleModel(TYPE_BYTE, 1, 1, 1, 1, twobands);
        testSubset(psm2bands);

        ComponentSampleModel csm2bands =
            new ComponentSampleModel(TYPE_BYTE, 1, 1, 1, 1, twobands);
        testSubset(csm2bands);

        BandedSampleModel bsm2bands =
            new BandedSampleModel(TYPE_BYTE, 1, 1, 1, twobands, twobands);
        testSubset(bsm2bands);

        SinglePixelPackedSampleModel sppsm2bands =
            new SinglePixelPackedSampleModel(TYPE_BYTE, 1, 1, twobands);
        testSubset(sppsm2bands);
    }

    static void cons(int[] bands, Class eType) {
        try {
           new PixelInterleavedSampleModel(TYPE_BYTE, 1, 1, 1, 1, bands);
        } catch (Exception e) {
             if (eType == null || !(eType.isInstance(e))) {
               throw new RuntimeException("failed for " + eType + " got " + e);
           } else {
               return;
           }
        }
        if (eType != null) {
            throw new RuntimeException("No exception for " + bands);
        }
    }

    static void subset(SampleModel sm, int[] bands, Class eType) {
        try {
           sm.createSubsetSampleModel(bands);
        } catch (Exception e) {
             if (eType == null || !(eType.isInstance(e))) {
               e.printStackTrace();
               throw new RuntimeException("failed for " + eType + " got " + e);
           } else {
               return;
           }
        }
        if (eType != null) {
            throw new RuntimeException("No exception for " + bands);
        }
    }

    static void testSubset(SampleModel sm) {
        subset(sm, nullbands, NullPointerException.class);
        subset(sm, zerobands, IllegalArgumentException.class);
        subset(sm, oneband, null);
        subset(sm, twobands, null);
        subset(sm, threebands, RasterFormatException.class);
        subset(sm, badbands, ArrayIndexOutOfBoundsException.class);
    }
}
