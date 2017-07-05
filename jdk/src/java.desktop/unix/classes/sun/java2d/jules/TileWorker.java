/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

public class TileWorker implements Runnable {
    static final int RASTERIZED_TILE_SYNC_GRANULARITY = 8;
    final ArrayList<JulesTile> rasterizedTileConsumerCache =
         new ArrayList<JulesTile>();
    final LinkedList<JulesTile> rasterizedBuffers = new LinkedList<JulesTile>();

    IdleTileCache tileCache;
    JulesAATileGenerator tileGenerator;
    int workerStartIndex;
    volatile int consumerPos = 0;

    /* Threading statistics */
    int mainThreadCnt = 0;
    int workerCnt = 0;
    int doubled = 0;

    public TileWorker(JulesAATileGenerator tileGenerator, int workerStartIndex, IdleTileCache tileCache) {
        this.tileGenerator = tileGenerator;
        this.workerStartIndex = workerStartIndex;
        this.tileCache = tileCache;
    }

    public void run() {
        ArrayList<JulesTile> tiles = new ArrayList<JulesTile>(16);

        for (int i = workerStartIndex; i < tileGenerator.getTileCount(); i++) {
            TileTrapContainer tile = tileGenerator.getTrapContainer(i);

            if (tile != null && tile.getTileAlpha() == 127) {
                JulesTile rasterizedTile =
                      tileGenerator.rasterizeTile(i,
                           tileCache.getIdleTileWorker(
                               tileGenerator.getTileCount() - i - 1));
                tiles.add(rasterizedTile);

                if (tiles.size() > RASTERIZED_TILE_SYNC_GRANULARITY) {
                    addRasterizedTiles(tiles);
                    tiles.clear();
                }
            }

            i = Math.max(i, consumerPos + RASTERIZED_TILE_SYNC_GRANULARITY / 2);
        }
        addRasterizedTiles(tiles);

        tileCache.disposeRasterizerResources();
    }

    /**
     * Returns a rasterized tile for the specified tilePos,
     * or null if it isn't available.
     * Allowed caller: MaskBlit/Consumer-Thread
     */
    public JulesTile getPreRasterizedTile(int tilePos) {
        JulesTile tile = null;

        if (rasterizedTileConsumerCache.size() == 0 &&
            tilePos >= workerStartIndex)
        {
            synchronized (rasterizedBuffers) {
                rasterizedTileConsumerCache.addAll(rasterizedBuffers);
                rasterizedBuffers.clear();
            }
        }

        while (tile == null && rasterizedTileConsumerCache.size() > 0) {
            JulesTile t = rasterizedTileConsumerCache.get(0);

            if (t.getTilePos() > tilePos) {
                break;
            }

            if (t.getTilePos() < tilePos) {
                tileCache.releaseTile(t);
                doubled++;
            }

            if (t.getTilePos() <= tilePos) {
                rasterizedTileConsumerCache.remove(0);
            }

            if (t.getTilePos() == tilePos) {
                tile = t;
            }
        }

        if (tile == null) {
            mainThreadCnt++;

            // If there are no tiles left, tell the producer the current
            // position. This avoids producing tiles twice.
            consumerPos = tilePos;
        } else {
            workerCnt++;
        }

        return tile;
    }

    private void addRasterizedTiles(ArrayList<JulesTile> tiles) {
        synchronized (rasterizedBuffers) {
            rasterizedBuffers.addAll(tiles);
        }
    }

    /**
     * Releases cached tiles.
     * Allowed caller: MaskBlit/Consumer-Thread
     */
    public void disposeConsumerResources() {
        synchronized (rasterizedBuffers) {
            tileCache.releaseTiles(rasterizedBuffers);
        }

        tileCache.releaseTiles(rasterizedTileConsumerCache);
    }
}
