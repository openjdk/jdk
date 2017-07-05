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

import java.util.Arrays;
import static sun.java2d.marlin.MarlinUtils.logInfo;

public final class ArrayCache implements MarlinConst {

    static final int BUCKETS = 4;
    static final int MIN_ARRAY_SIZE = 4096;
    static final int MAX_ARRAY_SIZE;
    static final int MASK_CLR_1 = ~1;
    // threshold to grow arrays only by (3/2) instead of 2
    static final int THRESHOLD_ARRAY_SIZE;
    static final int[] ARRAY_SIZES = new int[BUCKETS];
    // dirty byte array sizes
    static final int MIN_DIRTY_BYTE_ARRAY_SIZE = 32 * 2048; // 32px x 2048px
    static final int MAX_DIRTY_BYTE_ARRAY_SIZE;
    static final int[] DIRTY_BYTE_ARRAY_SIZES = new int[BUCKETS];
    // large array thresholds:
    static final long THRESHOLD_LARGE_ARRAY_SIZE;
    static final long THRESHOLD_HUGE_ARRAY_SIZE;
    // stats
    private static int resizeInt = 0;
    private static int resizeDirtyInt = 0;
    private static int resizeDirtyFloat = 0;
    private static int resizeDirtyByte = 0;
    private static int oversize = 0;

    static {
        // initialize buckets for int/float arrays
        int arraySize = MIN_ARRAY_SIZE;

        for (int i = 0; i < BUCKETS; i++, arraySize <<= 2) {
            ARRAY_SIZES[i] = arraySize;

            if (doTrace) {
                logInfo("arraySize[" + i + "]: " + arraySize);
            }
        }
        MAX_ARRAY_SIZE = arraySize >> 2;

        /* initialize buckets for dirty byte arrays
         (large AA chunk = 32 x 2048 pixels) */
        arraySize = MIN_DIRTY_BYTE_ARRAY_SIZE;

        for (int i = 0; i < BUCKETS; i++, arraySize <<= 1) {
            DIRTY_BYTE_ARRAY_SIZES[i] = arraySize;

            if (doTrace) {
                logInfo("dirty arraySize[" + i + "]: " + arraySize);
            }
        }
        MAX_DIRTY_BYTE_ARRAY_SIZE = arraySize >> 1;

        // threshold to grow arrays only by (3/2) instead of 2
        THRESHOLD_ARRAY_SIZE = Math.max(2 * 1024 * 1024, MAX_ARRAY_SIZE); // 2M

        THRESHOLD_LARGE_ARRAY_SIZE = 8L * THRESHOLD_ARRAY_SIZE; // 16M
        THRESHOLD_HUGE_ARRAY_SIZE  = 8L * THRESHOLD_LARGE_ARRAY_SIZE; // 128M

        if (doStats || doMonitors) {
            logInfo("ArrayCache.BUCKETS        = " + BUCKETS);
            logInfo("ArrayCache.MIN_ARRAY_SIZE = " + MIN_ARRAY_SIZE);
            logInfo("ArrayCache.MAX_ARRAY_SIZE = " + MAX_ARRAY_SIZE);
            logInfo("ArrayCache.ARRAY_SIZES = "
                    + Arrays.toString(ARRAY_SIZES));
            logInfo("ArrayCache.MIN_DIRTY_BYTE_ARRAY_SIZE = "
                    + MIN_DIRTY_BYTE_ARRAY_SIZE);
            logInfo("ArrayCache.MAX_DIRTY_BYTE_ARRAY_SIZE = "
                    + MAX_DIRTY_BYTE_ARRAY_SIZE);
            logInfo("ArrayCache.ARRAY_SIZES = "
                    + Arrays.toString(DIRTY_BYTE_ARRAY_SIZES));
            logInfo("ArrayCache.THRESHOLD_ARRAY_SIZE = "
                    + THRESHOLD_ARRAY_SIZE);
            logInfo("ArrayCache.THRESHOLD_LARGE_ARRAY_SIZE = "
                    + THRESHOLD_LARGE_ARRAY_SIZE);
            logInfo("ArrayCache.THRESHOLD_HUGE_ARRAY_SIZE = "
                    + THRESHOLD_HUGE_ARRAY_SIZE);
        }
    }

    private ArrayCache() {
        // Utility class
    }

    static synchronized void incResizeInt() {
        resizeInt++;
    }

    static synchronized void incResizeDirtyInt() {
        resizeDirtyInt++;
    }

    static synchronized void incResizeDirtyFloat() {
        resizeDirtyFloat++;
    }

    static synchronized void incResizeDirtyByte() {
        resizeDirtyByte++;
    }

    static synchronized void incOversize() {
        oversize++;
    }

    static void dumpStats() {
        if (resizeInt != 0 || resizeDirtyInt != 0 || resizeDirtyFloat != 0
                || resizeDirtyByte != 0 || oversize != 0) {
            logInfo("ArrayCache: int resize: " + resizeInt
                    + " - dirty int resize: " + resizeDirtyInt
                    + " - dirty float resize: " + resizeDirtyFloat
                    + " - dirty byte resize: " + resizeDirtyByte
                    + " - oversize: " + oversize);
        }
    }

    // small methods used a lot (to be inlined / optimized by hotspot)

    static int getBucket(final int length) {
        for (int i = 0; i < ARRAY_SIZES.length; i++) {
            if (length <= ARRAY_SIZES[i]) {
                return i;
            }
        }
        return -1;
    }

    static int getBucketDirtyBytes(final int length) {
        for (int i = 0; i < DIRTY_BYTE_ARRAY_SIZES.length; i++) {
            if (length <= DIRTY_BYTE_ARRAY_SIZES[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Return the new array size (~ x2)
     * @param curSize current used size
     * @param needSize needed size
     * @return new array size
     */
    public static int getNewSize(final int curSize, final int needSize) {
        // check if needSize is negative or integer overflow:
        if (needSize < 0) {
            // hard overflow failure - we can't even accommodate
            // new items without overflowing
            throw new ArrayIndexOutOfBoundsException(
                          "array exceeds maximum capacity !");
        }
        assert curSize >= 0;
        final int initial = (curSize & MASK_CLR_1);
        int size;
        if (initial > THRESHOLD_ARRAY_SIZE) {
            size = initial + (initial >> 1); // x(3/2)
        } else {
            size = (initial << 1); // x2
        }
        // ensure the new size is >= needed size:
        if (size < needSize) {
            // align to 4096 (may overflow):
            size = ((needSize >> 12) + 1) << 12;
        }
        // check integer overflow:
        if (size < 0) {
            // resize to maximum capacity:
            size = Integer.MAX_VALUE;
        }
        return size;
    }

    /**
     * Return the new array size (~ x2)
     * @param curSize current used size
     * @param needSize needed size
     * @return new array size
     */
    public static long getNewLargeSize(final long curSize, final long needSize) {
        // check if needSize is negative or integer overflow:
        if ((needSize >> 31L) != 0L) {
            // hard overflow failure - we can't even accommodate
            // new items without overflowing
            throw new ArrayIndexOutOfBoundsException(
                          "array exceeds maximum capacity !");
        }
        assert curSize >= 0L;
        long size;
        if (curSize > THRESHOLD_HUGE_ARRAY_SIZE) {
            size = curSize + (curSize >> 2L); // x(5/4)
        } else  if (curSize > THRESHOLD_LARGE_ARRAY_SIZE) {
            size = curSize + (curSize >> 1L); // x(3/2)
        } else {
            size = (curSize << 1L); // x2
        }
        // ensure the new size is >= needed size:
        if (size < needSize) {
            // align to 4096:
            size = ((needSize >> 12L) + 1L) << 12L;
        }
        // check integer overflow:
        if (size > Integer.MAX_VALUE) {
            // resize to maximum capacity:
            size = Integer.MAX_VALUE;
        }
        return size;
    }
}
