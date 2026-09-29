/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6453640
 * @summary Verify BandedSampleModel.createCompatibleSampleModel
 *          and createSubsetSampleModel behaviour
 * @run main BSMCreateCompatibleSMTest
 */

import java.awt.image.BandedSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.RasterFormatException;

public class BSMCreateCompatibleSMTest {

    public static void main(String[] args) {

        // These should all be OK
        BandedSampleModel bsm = new BandedSampleModel(DataBuffer.TYPE_BYTE, 1, 1, 1);
        bsm.createCompatibleSampleModel(20_000, 20_000);
        int[] bands = { 0 } ;
        bsm.createSubsetSampleModel(bands);

        // These should all throw an exception
        try {
            bsm.createCompatibleSampleModel(-1, 1);
            throw new RuntimeException("No exception for illegal w");
        } catch (IllegalArgumentException e) {
            System.out.println(e);
        }

        try {
            bsm.createCompatibleSampleModel(1, 0);
            throw new RuntimeException("No exception for illegal h");
        } catch (IllegalArgumentException e) {
            System.out.println(e);
        }

        try {
            bsm.createCompatibleSampleModel(-1, -1);
            throw new RuntimeException("No exception for illegal w+h");
        } catch (IllegalArgumentException e) {
            System.out.println(e);
        }

        try {
            bsm.createCompatibleSampleModel(50_000, 50_000);
            throw new RuntimeException("No exception for too large dims");
        } catch (IllegalArgumentException e) {
            System.out.println(e);
        }

        try {
            int[] bands0 = { } ;
            bsm.createSubsetSampleModel(bands0);
            throw new RuntimeException("No exception for empty bands[]");
        } catch (IllegalArgumentException e) {
            System.out.println(e);
        }

        try {
            int[] bands1 = { 1 } ;
            bsm.createSubsetSampleModel(bands1);
            throw new RuntimeException("No exception for out of bounds band");
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(e);
        }

        try {
            int[] bands2 = { 0, 0 } ;
            bsm.createSubsetSampleModel(bands2);
            throw new RuntimeException("No exception for too many bands");
        } catch (RasterFormatException e) {
            System.out.println(e);
        }
   }

}
