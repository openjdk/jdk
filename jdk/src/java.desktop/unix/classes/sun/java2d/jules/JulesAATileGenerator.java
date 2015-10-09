/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.jules;

import java.awt.*;
import java.awt.geom.*;
import java.util.concurrent.*;
import sun.java2d.pipe.*;
import sun.java2d.xr.*;

public class JulesAATileGenerator implements AATileGenerator {
    /* Threading stuff */
    static final ExecutorService rasterThreadPool =
                                          Executors.newCachedThreadPool();
    static final int CPU_CNT = Runtime.getRuntime().availableProcessors();

    static final boolean ENABLE_THREADING = false;
    static final int THREAD_MIN = 16;
    static final int THREAD_BEGIN = 16;

    IdleTileCache tileCache;
    TileWorker worker;
    boolean threaded = false;
    int rasterTileCnt;

    /* Tiling */
    static final int TILE_SIZE = 32;
    static final int TILE_SIZE_FP = 32 << 16;
    int left, right, top, bottom, width, height;
    int leftFP, topFP;
    int tileCnt, tilesX, tilesY;
    int currTilePos = 0;
    TrapezoidList traps;
    TileTrapContainer[] tiledTrapArray;
    JulesTile mainTile;

    public JulesAATileGenerator(Shape s, AffineTransform at, Region clip,
                                BasicStroke bs, boolean thin,
                                boolean normalize, int[] bbox) {
        JulesPathBuf buf = new JulesPathBuf();

        if (bs == null) {
            traps = buf.tesselateFill(s, at, clip);
        } else {
            traps = buf.tesselateStroke(s, bs, thin, false, true, at, clip);
        }

        calculateArea(bbox);
        bucketSortTraps();
        calculateTypicalAlpha();

        threaded = ENABLE_THREADING &&
                   rasterTileCnt >= THREAD_MIN && CPU_CNT >= 2;
        if (threaded) {
            tileCache = new IdleTileCache();
            worker = new TileWorker(this, THREAD_BEGIN, tileCache);
            rasterThreadPool.execute(worker);
        }

        mainTile = new JulesTile();
    }

    private static native long
        rasterizeTrapezoidsNative(long pixmanImagePtr, int[] traps,
                                  int[] trapPos, int trapCnt,
                                  byte[] buffer, int xOff, int yOff);

    private static native void freePixmanImgPtr(long pixmanImgPtr);

    private void calculateArea(int[] bbox) {
        tilesX = 0;
        tilesY = 0;
        tileCnt = 0;
        bbox[0] = 0;
        bbox[1] = 0;
        bbox[2] = 0;
        bbox[3] = 0;

        if (traps.getSize() > 0) {
            left = traps.getLeft();
            right = traps.getRight();
            top = traps.getTop();
            bottom = traps.getBottom();
            leftFP = left << 16;
            topFP = top << 16;

            bbox[0] = left;
            bbox[1] = top;
            bbox[2] = right;
            bbox[3] = bottom;

            width = right - left;
            height = bottom - top;

            if (width > 0 && height > 0) {
                tilesX = (int) Math.ceil(((double) width) / TILE_SIZE);
                tilesY = (int) Math.ceil(((double) height) / TILE_SIZE);
                tileCnt = tilesY * tilesX;
                tiledTrapArray = new TileTrapContainer[tileCnt];
            } else {
                // If there is no area touched by the traps, don't
                // render them.
                traps.setSize(0);
            }
        }
    }


    private void bucketSortTraps() {

        for (int i = 0; i < traps.getSize(); i++) {
            int top = traps.getTop(i) - XRUtils.XDoubleToFixed(this.top);
            int bottom = traps.getBottom(i) - topFP;
            int p1xLeft = traps.getP1XLeft(i) - leftFP;
            int p2xLeft = traps.getP2XLeft(i) - leftFP;
            int p1xRight = traps.getP1XRight(i) - leftFP;
            int p2xRight = traps.getP2XRight(i) - leftFP;

            int minLeft = Math.min(p1xLeft, p2xLeft);
            int maxRight = Math.max(p1xRight, p2xRight);

            maxRight = maxRight > 0 ? maxRight - 1 : maxRight;
            bottom = bottom > 0 ? bottom - 1 : bottom;

            int startTileY = top / TILE_SIZE_FP;
            int endTileY = bottom / TILE_SIZE_FP;
            int startTileX = minLeft / TILE_SIZE_FP;
            int endTileX = maxRight / TILE_SIZE_FP;

            for (int n = startTileY; n <= endTileY; n++) {

                for (int m = startTileX; m <= endTileX; m++) {
                    int trapArrayPos = n * tilesX + m;
                    TileTrapContainer trapTileList = tiledTrapArray[trapArrayPos];
                    if (trapTileList == null) {
                        trapTileList = new TileTrapContainer(new GrowableIntArray(1, 16));
                        tiledTrapArray[trapArrayPos] = trapTileList;
                    }

                    trapTileList.getTraps().addInt(i);
                }
            }
        }
    }

    public void getAlpha(byte[] tileBuffer, int offset, int rowstride) {
        JulesTile tile = null;

        if (threaded) {
            tile = worker.getPreRasterizedTile(currTilePos);
        }

        if (tile != null) {
            System.arraycopy(tile.getImgBuffer(), 0,
                             tileBuffer, 0, tileBuffer.length);
            tileCache.releaseTile(tile);
        } else {
            mainTile.setImgBuffer(tileBuffer);
            rasterizeTile(currTilePos, mainTile);
        }

        nextTile();
    }

    public void calculateTypicalAlpha() {
        rasterTileCnt = 0;

        for (int index = 0; index < tileCnt; index++) {

            TileTrapContainer trapCont = tiledTrapArray[index];
            if (trapCont != null) {
                GrowableIntArray trapList = trapCont.getTraps();

                int tileAlpha = 127;
                if (trapList == null || trapList.getSize() == 0) {
                    tileAlpha = 0;
                } else if (doTrapsCoverTile(trapList, index)) {
                    tileAlpha = 0xff;
                }

                if (tileAlpha == 127 || tileAlpha == 0xff) {
                    rasterTileCnt++;
                }

                trapCont.setTileAlpha(tileAlpha);
            }
        }
    }

    /*
     * Optimization for large fills. Foutunatly cairo does generate an y-sorted
     * list of trapezoids. This makes it quite simple to check whether a tile is
     * fully covered by traps by: - Checking whether the tile is fully covered by
     * traps vertically (trap 2 starts where trap 1 ended) - Checking whether all
     * traps cover the tile horizontally This also works, when a single tile
     * coveres the whole tile.
     */
    protected boolean doTrapsCoverTile(GrowableIntArray trapList, int tileIndex) {

        // Don't bother optimizing tiles with lots of traps, usually it won't
        // succeed anyway.
        if (trapList.getSize() > TILE_SIZE) {
            return false;
        }

        int tileStartX = getXPos(tileIndex) * TILE_SIZE_FP + leftFP;
        int tileStartY = getYPos(tileIndex) * TILE_SIZE_FP + topFP;
        int tileEndX = tileStartX + TILE_SIZE_FP;
        int tileEndY = tileStartY + TILE_SIZE_FP;

        // Check whether first tile covers the beginning of the tile vertically
        int firstTop = traps.getTop(trapList.getInt(0));
        int firstBottom = traps.getBottom(trapList.getInt(0));
        if (firstTop > tileStartY || firstBottom < tileStartY) {
            return false;
        }

        // Initialize lastBottom with top, in order to pass the checks for the
        // first iteration
        int lastBottom = firstTop;

        for (int i = 0; i < trapList.getSize(); i++) {
            int trapPos = trapList.getInt(i);
            if (traps.getP1XLeft(trapPos) > tileStartX ||
                traps.getP2XLeft(trapPos) > tileStartX ||
                traps.getP1XRight(trapPos) < tileEndX  ||
                traps.getP2XRight(trapPos) < tileEndX  ||
                 traps.getTop(trapPos) != lastBottom)
            {
                return false;
            }
            lastBottom = traps.getBottom(trapPos);
        }

        // When the last trap covered the tileEnd vertically, the tile is fully
        // covered
        return lastBottom >= tileEndY;
    }

    public int getTypicalAlpha() {
        if (tiledTrapArray[currTilePos] == null) {
            return 0;
        } else {
            return tiledTrapArray[currTilePos].getTileAlpha();
        }
    }

    public void dispose() {
        freePixmanImgPtr(mainTile.getPixmanImgPtr());

        if (threaded) {
            tileCache.disposeConsumerResources();
            worker.disposeConsumerResources();
        }
    }

    protected JulesTile rasterizeTile(int tileIndex, JulesTile tile) {
        int tileOffsetX = left + getXPos(tileIndex) * TILE_SIZE;
        int tileOffsetY = top + getYPos(tileIndex) * TILE_SIZE;
        TileTrapContainer trapCont = tiledTrapArray[tileIndex];
        GrowableIntArray trapList = trapCont.getTraps();

        if (trapCont.getTileAlpha() == 127) {
            long pixmanImgPtr =
                 rasterizeTrapezoidsNative(tile.getPixmanImgPtr(),
                                           traps.getTrapArray(),
                                           trapList.getArray(),
                                           trapList.getSize(),
                                           tile.getImgBuffer(),
                                           tileOffsetX, tileOffsetY);
            tile.setPixmanImgPtr(pixmanImgPtr);
        }

        tile.setTilePos(tileIndex);
        return tile;
    }

    protected int getXPos(int arrayPos) {
        return arrayPos % tilesX;
    }

    protected int getYPos(int arrayPos) {
        return arrayPos / tilesX;
    }

    public void nextTile() {
        currTilePos++;
    }

    public int getTileHeight() {
        return TILE_SIZE;
    }

    public int getTileWidth() {
        return TILE_SIZE;
    }

    public int getTileCount() {
        return tileCnt;
    }

    public TileTrapContainer getTrapContainer(int index) {
        return tiledTrapArray[index];
    }
}
