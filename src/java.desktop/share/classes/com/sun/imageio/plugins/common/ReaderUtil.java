/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.imageio.plugins.common;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import javax.imageio.stream.ImageInputStream;

/**
 * This class contains utility methods that may be useful to ImageReader
 * plugins.  Ideally these methods would be in the ImageReader base class
 * so that all subclasses could benefit from them, but that would be an
 * addition to the existing API, and it is not yet clear whether these methods
 * are universally useful, so for now we will leave them here.
 */
public class ReaderUtil {

    // Helper for computeUpdatedPixels method
    private static void computeUpdatedPixels(int sourceOffset,
                                             int sourceExtent,
                                             int destinationOffset,
                                             int dstMin,
                                             int dstMax,
                                             int sourceSubsampling,
                                             int passStart,
                                             int passExtent,
                                             int passPeriod,
                                             int[] vals,
                                             int offset)
    {
        // We need to satisfy the congruences:
        // dst = destinationOffset + (src - sourceOffset)/sourceSubsampling
        //
        // src - passStart == 0 (mod passPeriod)
        // src - sourceOffset == 0 (mod sourceSubsampling)
        //
        // subject to the inequalities:
        //
        // src >= passStart
        // src < passStart + passExtent
        // src >= sourceOffset
        // src < sourceOffset + sourceExtent
        // dst >= dstMin
        // dst <= dstmax
        //
        // where
        //
        // dst = destinationOffset + (src - sourceOffset)/sourceSubsampling
        //
        // For now we use a brute-force approach although we could
        // attempt to analyze the congruences.  If passPeriod and
        // sourceSubsampling are relatively prime, the period will be
        // their product.  If they share a common factor, either the
        // period will be equal to the larger value, or the sequences
        // will be completely disjoint, depending on the relationship
        // between passStart and sourceOffset.  Since we only have to do this
        // twice per image (once each for X and Y), it seems cheap enough
        // to do it the straightforward way.

        boolean gotPixel = false;
        int firstDst = -1;
        int secondDst = -1;
        int lastDst = -1;

        for (int i = 0; i < passExtent; i++) {
            int src = passStart + i*passPeriod;
            if (src < sourceOffset) {
                continue;
            }
            if ((src - sourceOffset) % sourceSubsampling != 0) {
                continue;
            }
            if (src >= sourceOffset + sourceExtent) {
                break;
            }

            int dst = destinationOffset +
                (src - sourceOffset)/sourceSubsampling;
            if (dst < dstMin) {
                continue;
            }
            if (dst > dstMax) {
                break;
            }

            if (!gotPixel) {
                firstDst = dst; // Record smallest valid pixel
                gotPixel = true;
            } else if (secondDst == -1) {
                secondDst = dst; // Record second smallest valid pixel
            }
            lastDst = dst; // Record largest valid pixel
        }

        vals[offset] = firstDst;

        // If we never saw a valid pixel, set width to 0
        if (!gotPixel) {
            vals[offset + 2] = 0;
        } else {
            vals[offset + 2] = lastDst - firstDst + 1;
        }

        // The period is given by the difference of any two adjacent pixels
        vals[offset + 4] = Math.max(secondDst - firstDst, 1);
    }

    /**
     * A utility method that computes the exact set of destination
     * pixels that will be written during a particular decoding pass.
     * The intent is to simplify the work done by readers in combining
     * the source region, source subsampling, and destination offset
     * information obtained from the {@code ImageReadParam} with
     * the offsets and periods of a progressive or interlaced decoding
     * pass.
     *
     * @param sourceRegion a {@code Rectangle} containing the
     * source region being read, offset by the source subsampling
     * offsets, and clipped against the source bounds, as returned by
     * the {@code getSourceRegion} method.
     * @param destinationOffset a {@code Point} containing the
     * coordinates of the upper-left pixel to be written in the
     * destination.
     * @param dstMinX the smallest X coordinate (inclusive) of the
     * destination {@code Raster}.
     * @param dstMinY the smallest Y coordinate (inclusive) of the
     * destination {@code Raster}.
     * @param dstMaxX the largest X coordinate (inclusive) of the destination
     * {@code Raster}.
     * @param dstMaxY the largest Y coordinate (inclusive) of the destination
     * {@code Raster}.
     * @param sourceXSubsampling the X subsampling factor.
     * @param sourceYSubsampling the Y subsampling factor.
     * @param passXStart the smallest source X coordinate (inclusive)
     * of the current progressive pass.
     * @param passYStart the smallest source Y coordinate (inclusive)
     * of the current progressive pass.
     * @param passWidth the width in pixels of the current progressive
     * pass.
     * @param passHeight the height in pixels of the current progressive
     * pass.
     * @param passPeriodX the X period (horizontal spacing between
     * pixels) of the current progressive pass.
     * @param passPeriodY the Y period (vertical spacing between
     * pixels) of the current progressive pass.
     *
     * @return an array of 6 {@code int}s containing the
     * destination min X, min Y, width, height, X period and Y period
     * of the region that will be updated.
     */
    public static int[] computeUpdatedPixels(Rectangle sourceRegion,
                                             Point destinationOffset,
                                             int dstMinX,
                                             int dstMinY,
                                             int dstMaxX,
                                             int dstMaxY,
                                             int sourceXSubsampling,
                                             int sourceYSubsampling,
                                             int passXStart,
                                             int passYStart,
                                             int passWidth,
                                             int passHeight,
                                             int passPeriodX,
                                             int passPeriodY)
    {
        int[] vals = new int[6];
        computeUpdatedPixels(sourceRegion.x, sourceRegion.width,
                             destinationOffset.x,
                             dstMinX, dstMaxX, sourceXSubsampling,
                             passXStart, passWidth, passPeriodX,
                             vals, 0);
        computeUpdatedPixels(sourceRegion.y, sourceRegion.height,
                             destinationOffset.y,
                             dstMinY, dstMaxY, sourceYSubsampling,
                             passYStart, passHeight, passPeriodY,
                             vals, 1);
        return vals;
    }

    public static int readMultiByteInteger(ImageInputStream iis)
        throws IOException
    {
        int value = iis.readByte();
        int result = value & 0x7f;
        while((value & 0x80) == 0x80) {
            result <<= 7;
            value = iis.readByte();
            result |= (value & 0x7f);
        }
        return result;
    }

    /**
     * An utility method to allocate and initialize a byte array
     * step by step with pre-defined limit, instead of allocating
     * a large array up-front based on the length derived from
     * an image header.
     *
     * @param iis a {@code ImageInputStream} to decode data and store
     * it in byte array.
     * @param length the size of data to decode
     *
     * @return array of size length when decode succeeds
     *
     * @throws IOException if decoding of stream fails
     */
    public static byte[] staggeredReadByteStream(ImageInputStream iis,
        int length) throws IOException {
        final int UNIT_SIZE = 1024000;
        byte[] decodedData;
        if (length < UNIT_SIZE) {
            decodedData = new byte[length];
            iis.readFully(decodedData, 0, length);
        } else {
            int bytesToRead = length;
            int bytesRead = 0;
            List<byte[]> bufs = new ArrayList<>();
            while (bytesToRead != 0) {
                int sz = Math.min(bytesToRead, UNIT_SIZE);
                byte[] unit = new byte[sz];
                iis.readFully(unit, 0, sz);
                bufs.add(unit);
                bytesRead += sz;
                bytesToRead -= sz;
            }
            decodedData = new byte[bytesRead];
            int copiedBytes = 0;
            for (byte[] ba : bufs) {
                System.arraycopy(ba, 0, decodedData, copiedBytes, ba.length);
                copiedBytes += ba.length;
            }
        }
        return decodedData;
    }

    /**
     * Tries to read {@code b.length} bytes from the stream,
     * and stores them into {@code b} starting at index 0.
     * If the end of the stream is reached, a {@code false}
     * will be returned.
     *
     * @param  iis  the stream to read.
     * @param  b    an array where to store the {@code byte}s.
     * @return {@code true} on success, or {@code false} on EOF.
     */
    public static boolean tryReadFully(ImageInputStream iis, byte[] b)
        throws IOException
    {
        int offset = 0;
        do {
            int n = iis.read(b, offset, b.length - offset);
            if (n < 0) {
                return false;       // EOF
            }
            offset += n;
        } while (offset < b.length);
        return true;
    }
}
