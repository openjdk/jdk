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
            DataBufferByte dbb = new DataBufferByte(buf, size);
            try {
                dbb.getData(1);
                throw new RuntimeException("No exception for invalid data bank index");
            } catch (ArrayIndexOutOfBoundsException e) {
            }
            test(dbb, size);
            test(dbb, -1);
            dbb = new DataBufferByte(buf, size-1, 1);
            test(dbb, size);
            dbb = new DataBufferByte(buf, size, size);
            testpass(dbb);
        }

        {
            short[] buf = new short[size*2];
            DataBufferShort dbs = new DataBufferShort(buf, size);
            try {
                dbs.getData(1);
                throw new RuntimeException("No exception for invalid data bank index");
            } catch (ArrayIndexOutOfBoundsException e) {
            }
            test(dbs, size);
            test(dbs, -1);
            dbs = new DataBufferShort(buf, size-1, 1);
            test(dbs, size);
            test(dbs, -2);
            dbs = new DataBufferShort(buf, size, size);
            testpass(dbs);

            DataBufferUShort dbu = new DataBufferUShort(buf, size);
            try {
                dbu.getData(1);
                throw new RuntimeException("No exception for invalid data bank index");
            } catch (ArrayIndexOutOfBoundsException e) {
            }
            test(dbu, size);
            test(dbu, -1);
            dbu = new DataBufferUShort(buf, size-1, 1);
            test(dbu, size);
            test(dbu, -2);
            dbu = new DataBufferUShort(buf, size, size);
            testpass(dbu);
        }

        {
            int[] buf = new int[size*2];
            DataBufferInt dbi = new DataBufferInt(buf, size);
            try {
                dbi.getData(1);
                throw new RuntimeException("No exception for invalid data bank index");
            } catch (ArrayIndexOutOfBoundsException e) {
            }
            test(dbi, size);
            test(dbi, -1);
            dbi = new DataBufferInt(buf, size-1, 1);
            test(dbi, size);
            test(dbi, -2);
            dbi = new DataBufferInt(buf, size, size);
            testpass(dbi);
        }

        {
            float[] buf = new float[size*2];
            DataBufferFloat dbf = new DataBufferFloat(buf, size);
            try {
                dbf.getData(1);
                throw new RuntimeException("No exception for invalid data bank index");
            } catch (ArrayIndexOutOfBoundsException e) {
            }
            test(dbf, size);
            test(dbf, -1);
            dbf = new DataBufferFloat(buf, size-1, 1);
            test(dbf, size);
            test(dbf, -2);
            dbf = new DataBufferFloat(buf, size, size);
            testpass(dbf);
        }

        {
            double[] buf = new double[size*2];
            DataBufferDouble dbd = new DataBufferDouble(buf, size);
            try {
                dbd.getData(1);
                throw new RuntimeException("No exception for invalid data bank index");
            } catch (ArrayIndexOutOfBoundsException e) {
            }
            test(dbd, size);
            test(dbd, -1);
            dbd = new DataBufferDouble(buf, size-1, 1);
            test(dbd, size);
            test(dbd, -2);
            dbd = new DataBufferDouble(buf, size, size);
            testpass(dbd);
        }
    }

    static void testpass(DataBuffer db) {
        int i = db.getSize() - 1;

        db.getElem(i);
        db.setElem(i, 0);
        db.getElem(0, i);
        db.setElem(0, i, 0);

        db.getElemFloat(i);
        db.setElemFloat(i, 0);
        db.getElemFloat(0, i);
        db.setElemFloat(0, i, 0);

        db.getElemDouble(i);
        db.setElemDouble(i, 0);
        db.getElemDouble(0, i);
        db.setElemDouble(0, i, 0);
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
            db.getElem(0, index);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.setElem(0, index, val);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.getElem(1, 0);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.setElem(1, 0, val);
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
            db.setElemFloat(index, val);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.getElemFloat(0, index);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.setElemFloat(0, index, val);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.getElemFloat(1, 0);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.setElemFloat(1, 0, val);
            failed = true;
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
            db.setElemDouble(index, val);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.getElemDouble(0, index);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.setElemDouble(0, index, val);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.getElemDouble(1, 0);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.setElemDouble(1, 0, val);
            failed = true;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            db.getElemDouble(1, index);
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
