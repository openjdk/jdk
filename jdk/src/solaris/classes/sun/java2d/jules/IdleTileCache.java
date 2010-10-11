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

public class IdleTileCache {
    final static int IDLE_TILE_SYNC_GRANULARITY = 16;
    final static ArrayList<JulesTile> idleBuffers = new ArrayList<JulesTile>();

    ArrayList<JulesTile> idleTileWorkerCacheList = new ArrayList<JulesTile>();
    ArrayList<JulesTile> idleTileConsumerCacheList =
              new ArrayList<JulesTile>(IDLE_TILE_SYNC_GRANULARITY);

    /**
     * Return a cached Tile, if possible from cache.
     * Allowed caller: Rasterizer/Producer-Thread
     *
     * @param: maxCache - Specify the maximum amount of tiles needed
     */
    public JulesTile getIdleTileWorker(int maxCache) {
        /* Try to fetch idle tiles from the global cache list */
        if (idleTileWorkerCacheList.size() == 0) {
            idleTileWorkerCacheList.ensureCapacity(maxCache);

            synchronized (idleBuffers) {
                for (int i = 0; i < maxCache && idleBuffers.size() > 0; i++) {
                    idleTileWorkerCacheList.add(
                            idleBuffers.remove(idleBuffers.size() - 1));
                }
            }
        }

        if (idleTileWorkerCacheList.size() > 0) {
            return idleTileWorkerCacheList.remove(idleTileWorkerCacheList.size() - 1);
        }

        return new JulesTile();
    }

    /**
     * Release tile and allow it to be re-used by another thread. Allowed
     *  Allowed caller: MaskBlit/Consumer-Thread
     */
    public void releaseTile(JulesTile tile) {
        if (tile != null && tile.hasBuffer()) {
            idleTileConsumerCacheList.add(tile);

            if (idleTileConsumerCacheList.size() > IDLE_TILE_SYNC_GRANULARITY) {
                synchronized (idleBuffers) {
                    idleBuffers.addAll(idleTileConsumerCacheList);
                }
                idleTileConsumerCacheList.clear();
            }
        }
    }

    /**
     * Releases thread-local tiles cached for use by the rasterizing thread.
     * Allowed caller: Rasterizer/Producer-Thread
     */
    public void disposeRasterizerResources() {
        releaseTiles(idleTileWorkerCacheList);
    }

    /**
     * Releases thread-local tiles cached for performance reasons. Allowed
     * Allowed caller: MaskBlit/Consumer-Thread
     */
    public void disposeConsumerResources() {
        releaseTiles(idleTileConsumerCacheList);
    }

    /**
     * Release a list of tiles and allow it to be re-used by another thread.
     * Thread safe.
     */
    public void releaseTiles(List<JulesTile> tileList) {
        if (tileList.size() > 0) {
            synchronized (idleBuffers) {
                idleBuffers.addAll(tileList);
            }
            tileList.clear();
        }
    }
}
