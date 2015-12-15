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

/**
 * Marlin constant holder using System properties
 */
interface MarlinConst {
    // enable Logs (logger or stdout)
    static final boolean enableLogs = false;
    // enable Logger
    static final boolean useLogger = enableLogs && MarlinProperties.isUseLogger();

    // log new RendererContext
    static final boolean logCreateContext = enableLogs
        && MarlinProperties.isLogCreateContext();
    // log misc.Unsafe alloc/realloc/free
    static final boolean logUnsafeMalloc = enableLogs
        && MarlinProperties.isLogUnsafeMalloc();

    // do statistics
    static final boolean doStats = enableLogs && MarlinProperties.isDoStats();
    // do monitors
    // disabled to reduce byte-code size a bit...
    static final boolean doMonitors = enableLogs && false; // MarlinProperties.isDoMonitors();
    // do checks
    static final boolean doChecks = false; // MarlinProperties.isDoChecks();

    // do AA range checks: disable when algorithm / code is stable
    static final boolean DO_AA_RANGE_CHECK = false;

    // enable logs
    static final boolean doLogWidenArray = enableLogs && false;
    // enable oversize logs
    static final boolean doLogOverSize = enableLogs && false;
    // enable traces
    static final boolean doTrace = enableLogs && false;
    // do flush monitors
    static final boolean doFlushMonitors = true;
    // use one polling thread to dump statistics/monitors
    static final boolean useDumpThread = false;
    // thread dump interval (ms)
    static final long statDump = 5000L;

    // do clean dirty array
    static final boolean doCleanDirty = false;

    // flag to use line simplifier
    static final boolean useSimplifier = MarlinProperties.isUseSimplifier();

    // flag to enable logs related bounds checks
    static final boolean doLogBounds = enableLogs && false;

    // Initial Array sizing (initial context capacity) ~ 512K

    // 2048 pixel (width x height) for initial capacity
    static final int INITIAL_PIXEL_DIM
        = MarlinProperties.getInitialImageSize();

    // typical array sizes: only odd numbers allowed below
    static final int INITIAL_ARRAY        = 256;
    static final int INITIAL_SMALL_ARRAY  = 1024;
    static final int INITIAL_MEDIUM_ARRAY = 4096;
    static final int INITIAL_LARGE_ARRAY  = 8192;
    static final int INITIAL_ARRAY_16K    = 16384;
    static final int INITIAL_ARRAY_32K    = 32768;
    // alpha row dimension
    static final int INITIAL_AA_ARRAY     = INITIAL_PIXEL_DIM;

    // initial edges (24 bytes) = 24K [ints] = 96K
    static final int INITIAL_EDGES_CAPACITY = 4096 * 24; // 6 ints per edges

    // zero value as byte
    static final byte BYTE_0 = (byte) 0;

    // subpixels expressed as log2
    public static final int SUBPIXEL_LG_POSITIONS_X
        = MarlinProperties.getSubPixel_Log2_X();
    public static final int SUBPIXEL_LG_POSITIONS_Y
        = MarlinProperties.getSubPixel_Log2_Y();

    // number of subpixels
    public static final int SUBPIXEL_POSITIONS_X = 1 << (SUBPIXEL_LG_POSITIONS_X);
    public static final int SUBPIXEL_POSITIONS_Y = 1 << (SUBPIXEL_LG_POSITIONS_Y);

    public static final float NORM_SUBPIXELS
        = (float)Math.sqrt(( SUBPIXEL_POSITIONS_X * SUBPIXEL_POSITIONS_X
                           + SUBPIXEL_POSITIONS_Y * SUBPIXEL_POSITIONS_Y)/2.0);

    public static final int MAX_AA_ALPHA
        = SUBPIXEL_POSITIONS_X * SUBPIXEL_POSITIONS_Y;

    public static final int TILE_SIZE_LG = MarlinProperties.getTileSize_Log2();
    public static final int TILE_SIZE = 1 << TILE_SIZE_LG; // 32 by default

    public static final int BLOCK_SIZE_LG = MarlinProperties.getBlockSize_Log2();
    public static final int BLOCK_SIZE    = 1 << BLOCK_SIZE_LG;
}
