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
 * @summary test DataBuffer subclass getters and setters with illegal arguments.
 */

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.util.Arrays;

public class DataBufferGetSetElemTest {

    public static void main(String[] args) {
        int size = 5;

        {
            byte[] buf = new byte[size*2];
            DataBuffer db = new DataBufferByte(buf, size);
            test(db, size);
            test(db, -1);
            db = new DataBufferByte(buf, size-1, 1);
            test(db, size);
        }

        {
            short[] buf = new short[size*2];

            DataBuffer dbs = new DataBufferShort(buf, size);
            test(dbs, size);
            test(dbs, -1);
            dbs = new DataBufferShort(buf, size-1, 1);
            test(dbs, size);
            test(dbs, -2);

            DataBuffer dbu = new DataBufferUShort(buf, size);
            test(dbu, size);
            test(dbu, -1);
            dbu = new DataBufferShort(buf, size-1, 1);
            test(dbu, size);
            test(dbu, -2);
        }

        {
            int[] buf = new int[size*2];
            DataBuffer db = new DataBufferInt(buf, size);
            test(db, size);
            test(db, -1);
            db = new DataBufferInt(buf, size-1, 1);
            test(db, size);
            test(db, -2);
        }

        {
            float[] buf = new float[size*2];
            DataBuffer db = new DataBufferFloat(buf, size);
            test(db, size);
            test(db, -1);
            db = new DataBufferFloat(buf, size-1, 1);
            test(db, size);
            test(db, -2);
        }

        {
            double[] buf = new double[size*2];
            DataBuffer db = new DataBufferDouble(buf, size);
            test(db, size);
            test(db, -1);
            db = new DataBufferDouble(buf, size-1, 1);
            test(db, size);
            test(db, -2);
        }
    }

    static void test(DataBuffer db, int index) {
        boolean failed = false;
        int val = 1;

        try {
            db.getElem(index);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.setElem(index, val);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.getElem(1, index);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.setElem(1, index, val);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }

        try {
            db.getElemFloat(index);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.getElemFloat(index);
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.getElemFloat(1, index);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.setElemFloat(1, index, val);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }

        try {
            db.getElemDouble(index);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.getElemDouble(1, index);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.setElemDouble(index, val);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.setElemDouble(1, index, val);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }

        if (failed) {
           throw new RuntimeException("get/setElem test failed for " + db + " index= " + index);
        }
    }
}
