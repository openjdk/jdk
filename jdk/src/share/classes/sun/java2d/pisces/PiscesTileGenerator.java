/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.java2d.pisces;

import sun.java2d.pipe.AATileGenerator;

public class PiscesTileGenerator implements AATileGenerator {
    public static final int TILE_SIZE = 32;

    PiscesCache cache;
    int x, y;
    int maxalpha;
    byte alphaMap[];

    public PiscesTileGenerator(PiscesCache cache, int maxalpha) {
        this.cache = cache;
        this.x = cache.bboxX0;
        this.y = cache.bboxY0;
        this.alphaMap = getAlphaMap(maxalpha);
        this.maxalpha = maxalpha;
    }

    static int prevMaxAlpha;
    static byte prevAlphaMap[];

    public synchronized static byte[] getAlphaMap(int maxalpha) {
        if (maxalpha != prevMaxAlpha) {
            prevAlphaMap = new byte[maxalpha+300];
            int halfmaxalpha = maxalpha>>2;
            for (int i = 0; i <= maxalpha; i++) {
                prevAlphaMap[i] = (byte) ((i * 255 + halfmaxalpha) / maxalpha);
            }
            for (int i = maxalpha; i < prevAlphaMap.length; i++) {
                prevAlphaMap[i] = (byte) 255;
            }
            prevMaxAlpha = maxalpha;
        }
        return prevAlphaMap;
    }

    public void getBbox(int bbox[]) {
        bbox[0] = cache.bboxX0;
        bbox[1] = cache.bboxY0;
        bbox[2] = cache.bboxX1;
        bbox[3] = cache.bboxY1;
        //System.out.println("bbox["+bbox[0]+", "+bbox[1]+" => "+bbox[2]+", "+bbox[3]+"]");
    }

    /**
     * Gets the width of the tiles that the generator batches output into.
     * @return the width of the standard alpha tile
     */
    public int getTileWidth() {
        return TILE_SIZE;
    }

    /**
     * Gets the height of the tiles that the generator batches output into.
     * @return the height of the standard alpha tile
     */
    public int getTileHeight() {
        return TILE_SIZE;
    }

    /**
     * Gets the typical alpha value that will characterize the current
     * tile.
     * The answer may be 0x00 to indicate that the current tile has
     * no coverage in any of its pixels, or it may be 0xff to indicate
     * that the current tile is completely covered by the path, or any
     * other value to indicate non-trivial coverage cases.
     * @return 0x00 for no coverage, 0xff for total coverage, or any other
     *         value for partial coverage of the tile
     */
    public int getTypicalAlpha() {
        if (true) return 0x80;
        // Decode run-length encoded alpha mask data
        // The data for row j begins at cache.rowOffsetsRLE[j]
        // and is encoded as a set of 2-byte pairs (val, runLen)
        // terminated by a (0, 0) pair.

        int x0 = this.x;
        int x1 = x0 + TILE_SIZE;
        int y0 = this.y;
        int y1 = y0 + TILE_SIZE;
        if (x1 > cache.bboxX1) x1 = cache.bboxX1;
        if (y1 > cache.bboxY1) y1 = cache.bboxY1;
        y0 -= cache.bboxY0;
        y1 -= cache.bboxY0;

        int ret = -1;
        for (int cy = y0; cy < y1; cy++) {
            int pos = cache.rowOffsetsRLE[cy];
            int cx = cache.minTouched[cy];

            if (cx > x0) {
                if (ret > 0) return 0x80;
                ret = 0x00;
            }
            while (cx < x1) {
                int runLen = cache.rowAARLE[pos + 1] & 0xff;
                if (runLen == 0) {
                    if (ret > 0) return 0x80;
                    ret = 0x00;
                    break;
                }
                cx += runLen;
                if (cx > x0) {
                    int val = cache.rowAARLE[pos] & 0xff;
                    if (ret != val) {
                        if (ret < 0) {
                            if (val != 0x00 && val != maxalpha) return 0x80;
                            ret = val;
                        } else {
                            return 0x80;
                        }
                    }
                }
                pos += 2;
            }
        }
        return ret;
    }

    /**
     * Skips the current tile and moves on to the next tile.
     * Either this method, or the getAlpha() method should be called
     * once per tile, but not both.
     */
    public void nextTile() {
        if ((x += TILE_SIZE) >= cache.bboxX1) {
            x = cache.bboxX0;
            y += TILE_SIZE;
        }
    }

    /**
     * Gets the alpha coverage values for the current tile.
     * Either this method, or the nextTile() method should be called
     * once per tile, but not both.
     */
    public void getAlpha(byte tile[], int offset, int rowstride) {
        // Decode run-length encoded alpha mask data
        // The data for row j begins at cache.rowOffsetsRLE[j]
        // and is encoded as a set of 2-byte pairs (val, runLen)
        // terminated by a (0, 0) pair.

        int x0 = this.x;
        int x1 = x0 + TILE_SIZE;
        int y0 = this.y;
        int y1 = y0 + TILE_SIZE;
        if (x1 > cache.bboxX1) x1 = cache.bboxX1;
        if (y1 > cache.bboxY1) y1 = cache.bboxY1;
        y0 -= cache.bboxY0;
        y1 -= cache.bboxY0;

        int idx = offset;
        for (int cy = y0; cy < y1; cy++) {
            int pos = cache.rowOffsetsRLE[cy];
            int cx = cache.minTouched[cy];
            if (cx > x1) cx = x1;

            if (cx > x0) {
                //System.out.println("L["+(cx-x0)+"]");
                for (int i = x0; i < cx; i++) {
                    tile[idx++] = 0x00;
                }
            }
            while (cx < x1) {
                byte val;
                int runLen = 0;
                try {
                    val = alphaMap[cache.rowAARLE[pos] & 0xff];
                    runLen = cache.rowAARLE[pos + 1] & 0xff;
                } catch (RuntimeException e0) {
                    System.out.println("maxalpha = "+maxalpha);
                    System.out.println("tile["+x0+", "+y0+
                                       " => "+x1+", "+y1+"]");
                    System.out.println("cx = "+cx+", cy = "+cy);
                    System.out.println("idx = "+idx+", pos = "+pos);
                    System.out.println("len = "+runLen);
                    cache.print(System.out);
                    e0.printStackTrace();
                    System.exit(1);
                    return;
                }
                if (runLen == 0) {
                    break;
                }
                int rx0 = cx;
                cx += runLen;
                int rx1 = cx;
                if (rx0 < x0) rx0 = x0;
                if (rx1 > x1) rx1 = x1;
                runLen = rx1 - rx0;
                //System.out.println("M["+runLen+"]");
                while (--runLen >= 0) {
                    try {
                        tile[idx++] = val;
                    } catch (RuntimeException e) {
                        System.out.println("maxalpha = "+maxalpha);
                        System.out.println("tile["+x0+", "+y0+
                                           " => "+x1+", "+y1+"]");
                        System.out.println("cx = "+cx+", cy = "+cy);
                        System.out.println("idx = "+idx+", pos = "+pos);
                        System.out.println("rx0 = "+rx0+", rx1 = "+rx1);
                        System.out.println("len = "+runLen);
                        cache.print(System.out);
                        e.printStackTrace();
                        System.exit(1);
                        return;
                    }
                }
                pos += 2;
            }
            if (cx < x0) { cx = x0; }
            while (cx < x1) {
                tile[idx++] = 0x00;
                cx++;
            }
            /*
            for (int i = idx - (x1-x0); i < idx; i++) {
                System.out.print(hex(tile[i], 2));
            }
            System.out.println();
            */
            idx += (rowstride - (x1-x0));
        }
        nextTile();
    }

    static String hex(int v, int d) {
        String s = Integer.toHexString(v);
        while (s.length() < d) {
            s = "0"+s;
        }
        return s.substring(0, d);
    }

    /**
     * Disposes this tile generator.
     * No further calls will be made on this instance.
     */
    public void dispose() {}
}
