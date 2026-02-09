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

import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.SinglePixelPackedSampleModel;

/*
 * @test
 * @bug  8376297
 * @summary  Test SampleModel.getSampleSize(int)
 */

public class GetSampleSizeTest {

    public static void main(String[] args) {

        final int width = 10;
        final int height = 10;
        int[] bandOffsets = {0, 0};
        int[] bitMask = {0x00ff0000, 0x0000ff00, 0xff, 0x0};

        {
            ComponentSampleModel csm =
                new ComponentSampleModel(DataBuffer.TYPE_BYTE,
                                         width, height, 1, width, bandOffsets);
            int numBands = csm.getNumBands();
            System.out.println("CSM numBands = " + numBands);
            if (numBands != 2) {
                throw new RuntimeException("Unexpected numBands");
            }
            System.out.println("CSM sample size = " + csm.getSampleSize(numBands));
        }

        {
            MultiPixelPackedSampleModel mppsm =
                new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, width, height, 4);
            int numBands = mppsm.getNumBands();
            System.out.println("MPPSM numBands = " + numBands);
            if (numBands != 1) {
                 throw new RuntimeException("Unexpected numBands");
            }
            System.out.println("MPPSM sample size = " + mppsm.getSampleSize(numBands));
        }

        {
            SinglePixelPackedSampleModel sppsm  =
                new SinglePixelPackedSampleModel(DataBuffer.TYPE_BYTE, width, height, bitMask);
            int numBands = sppsm.getNumBands();
            System.out.println("SPPSM numBands = " + numBands);
            if (numBands != 4) {
                throw new RuntimeException("Unexpected numBands");
            }
            try {
                System.out.println("SPPSM sample size = " + sppsm.getSampleSize(numBands));
                throw new RuntimeException("No expected AIOBE");
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("Got expected AIOBE for SPPSM");
            }
        }

   }
}

