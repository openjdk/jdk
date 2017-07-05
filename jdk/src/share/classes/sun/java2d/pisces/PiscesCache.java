/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

/**
 * An object used to cache pre-rendered complex paths.
 *
 * @see PiscesRenderer#render
 */
final class PiscesCache {

    final int bboxX0, bboxY0, bboxX1, bboxY1;

    // rowAARLE[i] holds the encoding of the pixel row with y = bboxY0+i.
    // The format of each of the inner arrays is: rowAARLE[i][0,1] = (x0, n)
    // where x0 is the first x in row i with nonzero alpha, and n is the
    // number of RLE entries in this row. rowAARLE[i][j,j+1] for j>1 is
    // (val,runlen)
    final int[][] rowAARLE;

    // RLE encodings are added in increasing y rows and then in increasing
    // x inside those rows. Therefore, at any one time there is a well
    // defined position (x,y) where a run length is about to be added (or
    // the row terminated). x0,y0 is this (x,y)-(bboxX0,bboxY0). They
    // are used to get indices into the current tile.
    private int x0 = Integer.MIN_VALUE, y0 = Integer.MIN_VALUE;

    // touchedTile[i][j] is the sum of all the alphas in the tile with
    // y=i*TILE_SIZE+bboxY0 and x=j*TILE_SIZE+bboxX0.
    private final int[][] touchedTile;

    static final int TILE_SIZE_LG = 5;
    static final int TILE_SIZE = 1 << TILE_SIZE_LG; // 32
    private static final int INIT_ROW_SIZE = 8; // enough for 3 run lengths

    PiscesCache(int minx, int miny, int maxx, int maxy) {
        assert maxy >= miny && maxx >= minx;
        bboxX0 = minx;
        bboxY0 = miny;
        bboxX1 = maxx + 1;
        bboxY1 = maxy + 1;
        // we could just leave the inner arrays as null and allocate them
        // lazily (which would be beneficial for shapes with gaps), but we
        // assume there won't be too many of those so we allocate everything
        // up front (which is better for other cases)
        rowAARLE = new int[bboxY1 - bboxY0 + 1][INIT_ROW_SIZE];
        x0 = 0;
        y0 = -1; // -1 makes the first assert in startRow succeed
        // the ceiling of (maxy - miny + 1) / TILE_SIZE;
        int nyTiles = (maxy - miny + TILE_SIZE) >> TILE_SIZE_LG;
        int nxTiles = (maxx - minx + TILE_SIZE) >> TILE_SIZE_LG;

        touchedTile = new int[nyTiles][nxTiles];
    }

    void addRLERun(int val, int runLen) {
        if (runLen > 0) {
            addTupleToRow(y0, val, runLen);
            if (val != 0) {
                // the x and y of the current row, minus bboxX0, bboxY0
                int tx = x0 >> TILE_SIZE_LG;
                int ty = y0 >> TILE_SIZE_LG;
                int tx1 = (x0 + runLen - 1) >> TILE_SIZE_LG;
                // while we forbid rows from starting before bboxx0, our users
                // can still store rows that go beyond bboxx1 (although this
                // shouldn't happen), so it's a good idea to check that i
                // is not going out of bounds in touchedTile[ty]
                if (tx1 >= touchedTile[ty].length) {
                    tx1 = touchedTile[ty].length - 1;
                }
                if (tx <= tx1) {
                    int nextTileXCoord = (tx + 1) << TILE_SIZE_LG;
                    if (nextTileXCoord > x0+runLen) {
                        touchedTile[ty][tx] += val * runLen;
                    } else {
                        touchedTile[ty][tx] += val * (nextTileXCoord - x0);
                    }
                    tx++;
                }
                // don't go all the way to tx1 - we need to handle the last
                // tile as a special case (just like we did with the first
                for (; tx < tx1; tx++) {
//                    try {
                    touchedTile[ty][tx] += (val << TILE_SIZE_LG);
//                    } catch (RuntimeException e) {
//                        System.out.println("x0, y0: " + x0 + ", " + y0);
//                        System.out.printf("tx, ty, tx1: %d, %d, %d %n", tx, ty, tx1);
//                        System.out.printf("bboxX/Y0/1: %d, %d, %d, %d %n",
//                                bboxX0, bboxY0, bboxX1, bboxY1);
//                        throw e;
//                    }
                }
                // they will be equal unless x0>>TILE_SIZE_LG == tx1
                if (tx == tx1) {
                    int lastXCoord = Math.min(x0 + runLen, (tx + 1) << TILE_SIZE_LG);
                    int txXCoord = tx << TILE_SIZE_LG;
                    touchedTile[ty][tx] += val * (lastXCoord - txXCoord);
                }
            }
            x0 += runLen;
        }
    }

    void startRow(int y, int x) {
        // rows are supposed to be added by increasing y.
        assert y - bboxY0 > y0;
        assert y <= bboxY1; // perhaps this should be < instead of <=

        y0 = y - bboxY0;
        // this should be a new, uninitialized row.
        assert rowAARLE[y0][1] == 0;

        x0 = x - bboxX0;
        assert x0 >= 0 : "Input must not be to the left of bbox bounds";

        // the way addTupleToRow is implemented it would work for this but it's
        // not a good idea to use it because it is meant for adding
        // RLE tuples, not the first tuple (which is special).
        rowAARLE[y0][0] = x;
        rowAARLE[y0][1] = 2;
    }

    int alphaSumInTile(int x, int y) {
        x -= bboxX0;
        y -= bboxY0;
        return touchedTile[y>>TILE_SIZE_LG][x>>TILE_SIZE_LG];
    }

    int minTouched(int rowidx) {
        return rowAARLE[rowidx][0];
    }

    int rowLength(int rowidx) {
        return rowAARLE[rowidx][1];
    }

    private void addTupleToRow(int row, int a, int b) {
        int end = rowAARLE[row][1];
        rowAARLE[row] = Helpers.widenArray(rowAARLE[row], end, 2);
        rowAARLE[row][end++] = a;
        rowAARLE[row][end++] = b;
        rowAARLE[row][1] = end;
    }

    @Override
    public String toString() {
        String ret = "bbox = ["+
                      bboxX0+", "+bboxY0+" => "+
                      bboxX1+", "+bboxY1+"]\n";
        for (int[] row : rowAARLE) {
            if (row != null) {
                ret += ("minTouchedX=" + row[0] +
                        "\tRLE Entries: " + Arrays.toString(
                                Arrays.copyOfRange(row, 2, row[1])) + "\n");
            } else {
                ret += "[]\n";
            }
        }
        return ret;
    }
}
