/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.java2d.pisces;

/**
 * An object used to cache pre-rendered complex paths.
 *
 * @see PiscesRenderer#render
 */
public final class PiscesCache {

    int bboxX0, bboxY0, bboxX1, bboxY1;

    byte[] rowAARLE;
    int alphaRLELength;

    int[] rowOffsetsRLE;
    int[] minTouched;
    int alphaRows;

    private PiscesCache() {}

    public static PiscesCache createInstance() {
        return new PiscesCache();
    }

    private static final float ROWAA_RLE_FACTOR = 1.5f;
    private static final float TOUCHED_FACTOR = 1.5f;
    private static final int MIN_TOUCHED_LEN = 64;

    private void reallocRowAARLE(int newLength) {
        if (rowAARLE == null) {
            rowAARLE = new byte[newLength];
        } else if (rowAARLE.length < newLength) {
            int len = Math.max(newLength,
                               (int)(rowAARLE.length*ROWAA_RLE_FACTOR));
            byte[] newRowAARLE = new byte[len];
            System.arraycopy(rowAARLE, 0, newRowAARLE, 0, rowAARLE.length);
            rowAARLE = newRowAARLE;
        }
    }

    private void reallocRowInfo(int newHeight) {
        if (minTouched == null) {
            int len = Math.max(newHeight, MIN_TOUCHED_LEN);
            minTouched = new int[len];
            rowOffsetsRLE = new int[len];
        } else if (minTouched.length < newHeight) {
            int len = Math.max(newHeight,
                               (int)(minTouched.length*TOUCHED_FACTOR));
            int[] newMinTouched = new int[len];
            int[] newRowOffsetsRLE = new int[len];
            System.arraycopy(minTouched, 0, newMinTouched, 0,
                             alphaRows);
            System.arraycopy(rowOffsetsRLE, 0, newRowOffsetsRLE, 0,
                             alphaRows);
            minTouched = newMinTouched;
            rowOffsetsRLE = newRowOffsetsRLE;
        }
    }

    void addRLERun(byte val, int runLen) {
        reallocRowAARLE(alphaRLELength + 2);
        rowAARLE[alphaRLELength++] = val;
        rowAARLE[alphaRLELength++] = (byte)runLen;
    }

    void startRow(int y, int x0, int x1) {
        if (alphaRows == 0) {
            bboxY0 = y;
            bboxY1 = y+1;
            bboxX0 = x0;
            bboxX1 = x1+1;
        } else {
            if (bboxX0 > x0) bboxX0 = x0;
            if (bboxX1 < x1 + 1) bboxX1 = x1 + 1;
            while (bboxY1++ < y) {
                reallocRowInfo(alphaRows+1);
                minTouched[alphaRows] = 0;
                // Assuming last 2 entries in rowAARLE are 0,0
                rowOffsetsRLE[alphaRows] = alphaRLELength-2;
                alphaRows++;
            }
        }
        reallocRowInfo(alphaRows+1);
        minTouched[alphaRows] = x0;
        rowOffsetsRLE[alphaRows] = alphaRLELength;
        alphaRows++;
    }

    public synchronized void dispose() {
        rowAARLE = null;
        alphaRLELength = 0;

        minTouched = null;
        rowOffsetsRLE = null;
        alphaRows = 0;

        bboxX0 = bboxY0 = bboxX1 = bboxY1 = 0;
    }

    public void print(java.io.PrintStream out) {
        synchronized (out) {
        out.println("bbox = ["+
                    bboxX0+", "+bboxY0+" => "+
                    bboxX1+", "+bboxY1+"]");

        out.println("alphRLELength = "+alphaRLELength);

        for (int y = bboxY0; y < bboxY1; y++) {
            int i = y-bboxY0;
            out.println("row["+i+"] == {"+
                        "minX = "+minTouched[i]+
                        ", off = "+rowOffsetsRLE[i]+"}");
        }

        for (int i = 0; i < alphaRLELength; i += 2) {
            out.println("rle["+i+"] = "+
                        (rowAARLE[i+1]&0xff)+" of "+(rowAARLE[i]&0xff));
        }
    }
    }
}
