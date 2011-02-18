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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import sun.java2d.pipe.AATileGenerator;

final class PiscesTileGenerator implements AATileGenerator {
    public static final int TILE_SIZE = PiscesCache.TILE_SIZE;

    // perhaps we should be using weak references here, but right now
    // that's not necessary. The way the renderer is, this map will
    // never contain more than one element - the one with key 64, since
    // we only do 8x8 supersampling.
    private static final Map<Integer, byte[]> alphaMapsCache = new
                   ConcurrentHashMap<Integer, byte[]>();

    PiscesCache cache;
    int x, y;
    final int maxalpha;
    private final int maxTileAlphaSum;

    // The alpha map used by this object (taken out of our map cache) to convert
    // pixel coverage counts gotten from PiscesCache (which are in the range
    // [0, maxalpha]) into alpha values, which are in [0,256).
    byte alphaMap[];

    public PiscesTileGenerator(Renderer r, int maxalpha) {
        this.cache = r.getCache();
        this.x = cache.bboxX0;
        this.y = cache.bboxY0;
        this.alphaMap = getAlphaMap(maxalpha);
        this.maxalpha = maxalpha;
        this.maxTileAlphaSum = TILE_SIZE*TILE_SIZE*maxalpha;
    }

    private static byte[] buildAlphaMap(int maxalpha) {
        byte[] alMap = new byte[maxalpha+1];
        int halfmaxalpha = maxalpha>>2;
        for (int i = 0; i <= maxalpha; i++) {
            alMap[i] = (byte) ((i * 255 + halfmaxalpha) / maxalpha);
        }
        return alMap;
    }

    public static byte[] getAlphaMap(int maxalpha) {
        if (!alphaMapsCache.containsKey(maxalpha)) {
            alphaMapsCache.put(maxalpha, buildAlphaMap(maxalpha));
        }
        return alphaMapsCache.get(maxalpha);
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
        int al = cache.alphaSumInTile(x, y);
        // Note: if we have a filled rectangle that doesn't end on a tile
        // border, we could still return 0xff, even though al!=maxTileAlphaSum
        // This is because if we return 0xff, our users will fill a rectangle
        // starting at x,y that has width = Math.min(TILE_SIZE, bboxX1-x),
        // and height min(TILE_SIZE,bboxY1-y), which is what should happen.
        // However, to support this, we would have to use 2 Math.min's
        // and 2 multiplications per tile, instead of just 2 multiplications
        // to compute maxTileAlphaSum. The savings offered would probably
        // not be worth it, considering how rare this case is.
        // Note: I have not tested this, so in the future if it is determined
        // that it is worth it, it should be implemented. Perhaps this method's
        // interface should be changed to take arguments the width and height
        // of the current tile. This would eliminate the 2 Math.min calls that
        // would be needed here, since our caller needs to compute these 2
        // values anyway.
        return (al == 0x00 ? 0x00 :
            (al == maxTileAlphaSum ? 0xff : 0x80));
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
            int[] row = cache.rowAARLE[cy];
            assert row != null;
            int cx = cache.minTouched(cy);
            if (cx > x1) cx = x1;

            for (int i = x0; i < cx; i++) {
                tile[idx++] = 0x00;
            }

            int pos = 2;
            while (cx < x1 && pos < row[1]) {
                byte val;
                int runLen = 0;
                assert row[1] > 2;
                try {
                    val = alphaMap[row[pos]];
                    runLen = row[pos + 1];
                    assert runLen > 0;
                } catch (RuntimeException e0) {
                    System.out.println("maxalpha = "+maxalpha);
                    System.out.println("tile["+x0+", "+y0+
                                       " => "+x1+", "+y1+"]");
                    System.out.println("cx = "+cx+", cy = "+cy);
                    System.out.println("idx = "+idx+", pos = "+pos);
                    System.out.println("len = "+runLen);
                    System.out.print(cache.toString());
                    e0.printStackTrace();
                    System.exit(1);
                    return;
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
                        System.out.print(cache.toString());
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