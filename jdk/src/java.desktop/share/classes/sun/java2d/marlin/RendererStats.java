/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.marlin;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import static sun.java2d.marlin.MarlinUtils.logInfo;
import sun.java2d.marlin.stats.Histogram;
import sun.java2d.marlin.stats.Monitor;
import sun.java2d.marlin.stats.StatLong;
import sun.awt.util.ThreadGroupUtils;

/**
 * This class gathers global rendering statistics for debugging purposes only
 */
public final class RendererStats implements MarlinConst {

    // singleton
    private static volatile RendererStats singleton = null;

    static RendererStats getInstance() {
        if (singleton == null) {
            singleton = new RendererStats();
        }
        return singleton;
    }

    public static void dumpStats() {
        if (singleton != null) {
            singleton.dump();
        }
    }

    /* RendererContext collection as hard references
       (only used for debugging purposes) */
    final ConcurrentLinkedQueue<RendererContext> allContexts
        = new ConcurrentLinkedQueue<RendererContext>();
    // stats
    final StatLong stat_cache_rowAA
        = new StatLong("cache.rowAA");
    final StatLong stat_cache_rowAAChunk
        = new StatLong("cache.rowAAChunk");
    final StatLong stat_cache_tiles
        = new StatLong("cache.tiles");
    final StatLong stat_rdr_poly_stack_curves
        = new StatLong("renderer.poly.stack.curves");
    final StatLong stat_rdr_poly_stack_types
        = new StatLong("renderer.poly.stack.types");
    final StatLong stat_rdr_addLine
        = new StatLong("renderer.addLine");
    final StatLong stat_rdr_addLine_skip
        = new StatLong("renderer.addLine.skip");
    final StatLong stat_rdr_curveBreak
        = new StatLong("renderer.curveBreakIntoLinesAndAdd");
    final StatLong stat_rdr_curveBreak_dec
        = new StatLong("renderer.curveBreakIntoLinesAndAdd.dec");
    final StatLong stat_rdr_curveBreak_inc
        = new StatLong("renderer.curveBreakIntoLinesAndAdd.inc");
    final StatLong stat_rdr_quadBreak
        = new StatLong("renderer.quadBreakIntoLinesAndAdd");
    final StatLong stat_rdr_quadBreak_dec
        = new StatLong("renderer.quadBreakIntoLinesAndAdd.dec");
    final StatLong stat_rdr_edges
        = new StatLong("renderer.edges");
    final StatLong stat_rdr_edges_count
        = new StatLong("renderer.edges.count");
    final StatLong stat_rdr_edges_resizes
        = new StatLong("renderer.edges.resize");
    final StatLong stat_rdr_activeEdges
        = new StatLong("renderer.activeEdges");
    final StatLong stat_rdr_activeEdges_updates
        = new StatLong("renderer.activeEdges.updates");
    final StatLong stat_rdr_activeEdges_adds
        = new StatLong("renderer.activeEdges.adds");
    final StatLong stat_rdr_activeEdges_adds_high
        = new StatLong("renderer.activeEdges.adds_high");
    final StatLong stat_rdr_crossings_updates
        = new StatLong("renderer.crossings.updates");
    final StatLong stat_rdr_crossings_sorts
        = new StatLong("renderer.crossings.sorts");
    final StatLong stat_rdr_crossings_bsearch
        = new StatLong("renderer.crossings.bsearch");
    final StatLong stat_rdr_crossings_msorts
        = new StatLong("renderer.crossings.msorts");
    // growable arrays
    final StatLong stat_array_dasher_dasher
        = new StatLong("array.dasher.dasher.d_float");
    final StatLong stat_array_dasher_firstSegmentsBuffer
        = new StatLong("array.dasher.firstSegmentsBuffer.d_float");
    final StatLong stat_array_stroker_polystack_curves
        = new StatLong("array.stroker.polystack.curves.d_float");
    final StatLong stat_array_stroker_polystack_curveTypes
        = new StatLong("array.stroker.polystack.curveTypes.d_byte");
    final StatLong stat_array_marlincache_rowAAChunk
        = new StatLong("array.marlincache.rowAAChunk.d_byte");
    final StatLong stat_array_marlincache_touchedTile
        = new StatLong("array.marlincache.touchedTile.int");
    final StatLong stat_array_renderer_alphaline
        = new StatLong("array.renderer.alphaline.int");
    final StatLong stat_array_renderer_crossings
        = new StatLong("array.renderer.crossings.int");
    final StatLong stat_array_renderer_aux_crossings
        = new StatLong("array.renderer.aux_crossings.int");
    final StatLong stat_array_renderer_edgeBuckets
        = new StatLong("array.renderer.edgeBuckets.int");
    final StatLong stat_array_renderer_edgeBucketCounts
        = new StatLong("array.renderer.edgeBucketCounts.int");
    final StatLong stat_array_renderer_edgePtrs
        = new StatLong("array.renderer.edgePtrs.int");
    final StatLong stat_array_renderer_aux_edgePtrs
        = new StatLong("array.renderer.aux_edgePtrs.int");
    // histograms
    final Histogram hist_rdr_crossings
        = new Histogram("renderer.crossings");
    final Histogram hist_rdr_crossings_ratio
        = new Histogram("renderer.crossings.ratio");
    final Histogram hist_rdr_crossings_adds
        = new Histogram("renderer.crossings.adds");
    final Histogram hist_rdr_crossings_msorts
        = new Histogram("renderer.crossings.msorts");
    final Histogram hist_rdr_crossings_msorts_adds
        = new Histogram("renderer.crossings.msorts.adds");
    final Histogram hist_tile_generator_alpha
        = new Histogram("tile_generator.alpha");
    final Histogram hist_tile_generator_encoding
        = new Histogram("tile_generator.encoding");
    final Histogram hist_tile_generator_encoding_dist
        = new Histogram("tile_generator.encoding.dist");
    final Histogram hist_tile_generator_encoding_ratio
        = new Histogram("tile_generator.encoding.ratio");
    final Histogram hist_tile_generator_encoding_runLen
        = new Histogram("tile_generator.encoding.runLen");
    // all stats
    final StatLong[] statistics = new StatLong[]{
        stat_cache_rowAA,
        stat_cache_rowAAChunk,
        stat_cache_tiles,
        stat_rdr_poly_stack_types,
        stat_rdr_poly_stack_curves,
        stat_rdr_addLine,
        stat_rdr_addLine_skip,
        stat_rdr_curveBreak,
        stat_rdr_curveBreak_dec,
        stat_rdr_curveBreak_inc,
        stat_rdr_quadBreak,
        stat_rdr_quadBreak_dec,
        stat_rdr_edges,
        stat_rdr_edges_count,
        stat_rdr_edges_resizes,
        stat_rdr_activeEdges,
        stat_rdr_activeEdges_updates,
        stat_rdr_activeEdges_adds,
        stat_rdr_activeEdges_adds_high,
        stat_rdr_crossings_updates,
        stat_rdr_crossings_sorts,
        stat_rdr_crossings_bsearch,
        stat_rdr_crossings_msorts,
        hist_rdr_crossings,
        hist_rdr_crossings_ratio,
        hist_rdr_crossings_adds,
        hist_rdr_crossings_msorts,
        hist_rdr_crossings_msorts_adds,
        hist_tile_generator_alpha,
        hist_tile_generator_encoding,
        hist_tile_generator_encoding_dist,
        hist_tile_generator_encoding_ratio,
        hist_tile_generator_encoding_runLen,
        stat_array_dasher_dasher,
        stat_array_dasher_firstSegmentsBuffer,
        stat_array_stroker_polystack_curves,
        stat_array_stroker_polystack_curveTypes,
        stat_array_marlincache_rowAAChunk,
        stat_array_marlincache_touchedTile,
        stat_array_renderer_alphaline,
        stat_array_renderer_crossings,
        stat_array_renderer_aux_crossings,
        stat_array_renderer_edgeBuckets,
        stat_array_renderer_edgeBucketCounts,
        stat_array_renderer_edgePtrs,
        stat_array_renderer_aux_edgePtrs
    };
    // monitors
    final Monitor mon_pre_getAATileGenerator
        = new Monitor("MarlinRenderingEngine.getAATileGenerator()");
    final Monitor mon_npi_currentSegment
        = new Monitor("NormalizingPathIterator.currentSegment()");
    final Monitor mon_rdr_addLine
        = new Monitor("Renderer.addLine()");
    final Monitor mon_rdr_endRendering
        = new Monitor("Renderer.endRendering()");
    final Monitor mon_rdr_endRendering_Y
        = new Monitor("Renderer._endRendering(Y)");
    final Monitor mon_rdr_copyAARow
        = new Monitor("Renderer.copyAARow()");
    final Monitor mon_pipe_renderTiles
        = new Monitor("AAShapePipe.renderTiles()");
    final Monitor mon_ptg_getAlpha
        = new Monitor("MarlinTileGenerator.getAlpha()");
    final Monitor mon_debug
        = new Monitor("DEBUG()");
    // all monitors
    final Monitor[] monitors = new Monitor[]{
        mon_pre_getAATileGenerator,
        mon_npi_currentSegment,
        mon_rdr_addLine,
        mon_rdr_endRendering,
        mon_rdr_endRendering_Y,
        mon_rdr_copyAARow,
        mon_pipe_renderTiles,
        mon_ptg_getAlpha,
        mon_debug
    };

    private RendererStats() {
        super();

        AccessController.doPrivileged(
            (PrivilegedAction<Void>) () -> {
                final Thread hook = new Thread(
                    ThreadGroupUtils.getRootThreadGroup(),
                    new Runnable() {
                        @Override
                        public void run() {
                            dump();
                        }
                    },
                    "MarlinStatsHook"
                );
                hook.setContextClassLoader(null);
                Runtime.getRuntime().addShutdownHook(hook);

                if (useDumpThread) {
                    final Timer statTimer = new Timer("RendererStats");
                    statTimer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            dump();
                        }
                    }, statDump, statDump);
                }
                return null;
            }
        );
    }

    void dump() {
        if (doStats) {
            ArrayCache.dumpStats();
        }
        final RendererContext[] all = allContexts.toArray(
                                          new RendererContext[allContexts.size()]);
        for (RendererContext rdrCtx : all) {
            logInfo("RendererContext: " + rdrCtx.name);

            if (doMonitors) {
                for (Monitor monitor : monitors) {
                    if (monitor.count != 0) {
                        logInfo(monitor.toString());
                    }
                }
                // As getAATileGenerator percents:
                final long total = mon_pre_getAATileGenerator.sum;
                if (total != 0L) {
                    for (Monitor monitor : monitors) {
                        logInfo(monitor.name + " : "
                                + ((100d * monitor.sum) / total) + " %");
                    }
                }
                if (doFlushMonitors) {
                    for (Monitor m : monitors) {
                        m.reset();
                    }
                }
            }

            if (doStats) {
                for (StatLong stat : statistics) {
                    if (stat.count != 0) {
                        logInfo(stat.toString());
                        stat.reset();
                    }
                }
                // IntArrayCaches stats:
                final RendererContext.ArrayCachesHolder holder
                    = rdrCtx.getArrayCachesHolder();

                logInfo("Array caches for thread: " + rdrCtx.name);

                for (IntArrayCache cache : holder.intArrayCaches) {
                    cache.dumpStats();
                }

                logInfo("Dirty Array caches for thread: " + rdrCtx.name);

                for (IntArrayCache cache : holder.dirtyIntArrayCaches) {
                    cache.dumpStats();
                }
                for (FloatArrayCache cache : holder.dirtyFloatArrayCaches) {
                    cache.dumpStats();
                }
                for (ByteArrayCache cache : holder.dirtyByteArrayCaches) {
                    cache.dumpStats();
                }
            }
        }
    }
}
