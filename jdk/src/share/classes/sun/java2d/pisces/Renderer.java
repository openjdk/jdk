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

import java.util.Arrays;

public class Renderer implements LineSink {

///////////////////////////////////////////////////////////////////////////////
// Scan line iterator and edge crossing data.
//////////////////////////////////////////////////////////////////////////////

    private int[] crossings;

    // This is an array of indices into the edge array. It is initialized to
    // [i * SIZEOF_STRUCT_EDGE for i in range(0, edgesSize/SIZEOF_STRUCT_EDGE)]
    // (where range(i, j) is i,i+1,...,j-1 -- just like in python).
    // The reason for keeping this is because we need the edges array sorted
    // by y0, but we don't want to move all that data around, so instead we
    // sort the indices into the edge array, and use edgeIndices to access
    // the edges array. This is meant to simulate a pointer array (hence the name)
    private int[] edgePtrs;

    // crossing bounds. The bounds are not necessarily tight (the scan line
    // at minY, for example, might have no crossings). The x bounds will
    // be accumulated as crossings are computed.
    private int minY, maxY;
    private int minX, maxX;
    private int nextY;

    // indices into the edge pointer list. They indicate the "active" sublist in
    // the edge list (the portion of the list that contains all the edges that
    // cross the next scan line).
    private int lo, hi;

    private static final int INIT_CROSSINGS_SIZE = 50;
    private void ScanLineItInitialize() {
        crossings = new int[INIT_CROSSINGS_SIZE];
        edgePtrs = new int[edgesSize / SIZEOF_STRUCT_EDGE];
        for (int i = 0; i < edgePtrs.length; i++) {
            edgePtrs[i] = i * SIZEOF_STRUCT_EDGE;
        }

        qsort(0, edgePtrs.length - 1);

        // We don't care if we clip some of the line off with ceil, since
        // no scan line crossings will be eliminated (in fact, the ceil is
        // the y of the first scan line crossing).
        nextY = minY = Math.max(boundsMinY, (int)Math.ceil(edgeMinY));
        maxY = Math.min(boundsMaxY, (int)Math.ceil(edgeMaxY));

        for (lo = 0; lo < edgePtrs.length && edges[edgePtrs[lo]+Y1] <= nextY; lo++)
            ;
        for (hi = lo; hi < edgePtrs.length && edges[edgePtrs[hi]+CURY] <= nextY; hi++)
            ; // the active list is *edgePtrs[lo] (inclusive) *edgePtrs[hi] (exclusive)
        for (int i = lo; i < hi; i++) {
            setCurY(edgePtrs[i], nextY);
        }

        // We accumulate X in the iterator because accumulating it in addEdge
        // like we do with Y does not do much good: if there's an edge
        // (0,0)->(1000,10000), and if y gets clipped to 1000, then the x
        // bound should be 100, but the accumulator from addEdge would say 1000,
        // so we'd still have to accumulate the X bounds as we add crossings.
        minX = boundsMinX;
        maxX = boundsMaxX;
    }

    private int ScanLineItCurrentY() {
        return nextY - 1;
    }

    private int ScanLineItGoToNextYAndComputeCrossings() {
        // we go through the active list and remove the ones that don't cross
        // the nextY scanline.
        int crossingIdx = 0;
        for (int i = lo; i < hi; i++) {
            if (edges[edgePtrs[i]+Y1] <= nextY) {
                edgePtrs[i] = edgePtrs[lo++];
            }
        }
        if (hi - lo > crossings.length) {
            int newSize = Math.max(hi - lo, crossings.length * 2);
            crossings = Arrays.copyOf(crossings, newSize);
        }
        // Now every edge between lo and hi crosses nextY. Compute it's
        // crossing and put it in the crossings array.
        for (int i = lo; i < hi; i++) {
            addCrossing(nextY, getCurCrossing(edgePtrs[i]), (int)edges[edgePtrs[i]+OR], crossingIdx);
            gotoNextY(edgePtrs[i]);
            crossingIdx++;
        }

        nextY++;
        // Expand active list to include new edges.
        for (; hi < edgePtrs.length && edges[edgePtrs[hi]+CURY] <= nextY; hi++) {
            setCurY(edgePtrs[hi], nextY);
        }

        Arrays.sort(crossings, 0, crossingIdx);
        return crossingIdx;
    }

    private boolean ScanLineItHasNext() {
        return nextY < maxY;
    }

    private void addCrossing(int y, int x, int or, int idx) {
        if (x < minX) {
            minX = x;
        }
        if (x > maxX) {
            maxX = x;
        }
        x <<= 1;
        crossings[idx] = ((or == 1) ? (x | 0x1) : x);
    }


    // quicksort implementation for sorting the edge indices ("pointers")
    // by increasing y0. first, last are indices into the "pointer" array
    // It sorts the pointer array from first (inclusive) to last (inclusive)
    private void qsort(int first, int last) {
        if (last > first) {
            int p = partition(first, last);
            if (first < p - 1) {
                qsort(first, p - 1);
            }
            if (p < last) {
                qsort(p, last);
            }
        }
    }

    // i, j are indices into edgePtrs.
    private int partition(int i, int j) {
        int pivotVal = edgePtrs[i];
        while (i <= j) {
            // edges[edgePtrs[i]+1] is equivalent to (*(edgePtrs[i])).y0 in C
            while (edges[edgePtrs[i]+CURY] < edges[pivotVal+CURY]) { i++; }
            while (edges[edgePtrs[j]+CURY] > edges[pivotVal+CURY]) { j--; }
            if (i <= j) {
                int tmp = edgePtrs[i];
                edgePtrs[i] = edgePtrs[j];
                edgePtrs[j] = tmp;
                i++;
                j--;
            }
        }
        return i;
    }

//============================================================================


//////////////////////////////////////////////////////////////////////////////
//  EDGE LIST
//////////////////////////////////////////////////////////////////////////////

    private static final int INIT_NUM_EDGES = 1000;
    private static final int SIZEOF_STRUCT_EDGE = 5;

    // The following array is a poor man's struct array:
    // it simulates a struct array by having
    // edges[SIZEOF_STRUCT_EDGE * i + j] be the jth field in the ith element
    // of an array of edge structs.
    private float[] edges;
    private int edgesSize; // size of the edge list.
    private static final int Y1    = 0;
    private static final int SLOPE = 1;
    private static final int OR    = 2; // the orientation. This can be -1 or 1.
                                     // -1 means up, 1 means down.
    private static final int CURY  = 3; // j = 5 corresponds to the "current Y".
                             // Each edge keeps track of the last scanline
                             // crossing it computed, and this is the y coord of
                             // that scanline.
    private static final int CURX = 4; //the x coord of the current crossing.

    // Note that while the array is declared as a float[] not all of it's
    // elements should be floats. currentY and Orientation should be ints (or int and
    // byte respectively), but they all need to be the same type. This isn't
    // really a problem because floats can represent exactly all 23 bit integers,
    // which should be more than enough.
    // Note, also, that we only need x1 for slope computation, so we don't need
    // to store it. x0, y0 don't need to be stored either. They can be put into
    // curx, cury, and it's ok if they're lost when curx and cury are changed.
    // We take this undeniably ugly and error prone approach (instead of simply
    // making an Edge class) for performance reasons. Also, it would probably be nicer
    // to have one array for each field, but that would defeat the purpose because
    // it would make poor use of the processor cache, since we tend to access
    // all the fields for one edge at a time.

    private float edgeMinY;
    private float edgeMaxY;


    private void addEdge(float x0, float y0, float x1, float y1) {
        float or = (y0 < y1) ? 1f : -1f; // orientation: 1 = UP; -1 = DOWN
        if (or == -1) {
            float tmp = y0;
            y0 = y1;
            y1 = tmp;
            tmp = x0;
            x0 = x1;
            x1 = tmp;
        }
        // skip edges that don't cross a scanline
        if (Math.ceil(y0) >= Math.ceil(y1)) {
            return;
        }

        int newSize = edgesSize + SIZEOF_STRUCT_EDGE;
        if (edges.length < newSize) {
            edges = Arrays.copyOf(edges, newSize * 2);
        }
        edges[edgesSize+CURX] = x0;
        edges[edgesSize+CURY] = y0;
        edges[edgesSize+Y1] = y1;
        edges[edgesSize+SLOPE] = (x1 - x0) / (y1 - y0);
        edges[edgesSize+OR] = or;
        // the crossing values can't be initialized meaningfully yet. This
        // will have to wait until setCurY is called
        edgesSize += SIZEOF_STRUCT_EDGE;

        // Accumulate edgeMinY and edgeMaxY
        if (y0 < edgeMinY) { edgeMinY = y0; }
        if (y1 > edgeMaxY) { edgeMaxY = y1; }
    }

    // As far as the following methods care, this edges extends to infinity.
    // They can compute the x intersect of any horizontal line.
    // precondition: idx is the index to the start of the desired edge.
    // So, if the ith edge is wanted, idx should be SIZEOF_STRUCT_EDGE * i
    private void setCurY(int idx, int y) {
        // compute the x crossing of edge at idx and horizontal line y
        // currentXCrossing = (y - y0)*slope + x0
        edges[idx + CURX] = (y - edges[idx + CURY]) * edges[idx + SLOPE] + edges[idx+CURX];
        edges[idx + CURY] = (float)y;
    }

    private void gotoNextY(int idx) {
        edges[idx + CURY] += 1f; // i.e. curY += 1
        edges[idx + CURX] += edges[idx + SLOPE]; // i.e. curXCrossing += slope
    }

    private int getCurCrossing(int idx) {
        return (int)edges[idx + CURX];
    }
//====================================================================================

    public static final int WIND_EVEN_ODD = 0;
    public static final int WIND_NON_ZERO = 1;

    // Antialiasing
    final private int SUBPIXEL_LG_POSITIONS_X;
    final private int SUBPIXEL_LG_POSITIONS_Y;
    final private int SUBPIXEL_POSITIONS_X;
    final private int SUBPIXEL_POSITIONS_Y;
    final private int SUBPIXEL_MASK_X;
    final private int SUBPIXEL_MASK_Y;
    final int MAX_AA_ALPHA;

    // Cache to store RLE-encoded coverage mask of the current primitive
    final PiscesCache cache;

    // Bounds of the drawing region, at subpixel precision.
    final private int boundsMinX, boundsMinY, boundsMaxX, boundsMaxY;

    // Pixel bounding box for current primitive
    private int pix_bboxX0, pix_bboxY0, pix_bboxX1, pix_bboxY1;

    // Current winding rule
    final private int windingRule;

    // Current drawing position, i.e., final point of last segment
    private float x0, y0;

    // Position of most recent 'moveTo' command
    private float pix_sx0, pix_sy0;

    public Renderer(int subpixelLgPositionsX, int subpixelLgPositionsY,
                    int pix_boundsX, int pix_boundsY,
                    int pix_boundsWidth, int pix_boundsHeight,
                    int windingRule,
                    PiscesCache cache) {
        this.SUBPIXEL_LG_POSITIONS_X = subpixelLgPositionsX;
        this.SUBPIXEL_LG_POSITIONS_Y = subpixelLgPositionsY;
        this.SUBPIXEL_MASK_X = (1 << (SUBPIXEL_LG_POSITIONS_X)) - 1;
        this.SUBPIXEL_MASK_Y = (1 << (SUBPIXEL_LG_POSITIONS_Y)) - 1;
        this.SUBPIXEL_POSITIONS_X = 1 << (SUBPIXEL_LG_POSITIONS_X);
        this.SUBPIXEL_POSITIONS_Y = 1 << (SUBPIXEL_LG_POSITIONS_Y);
        this.MAX_AA_ALPHA = (SUBPIXEL_POSITIONS_X * SUBPIXEL_POSITIONS_Y);

        this.edges = new float[SIZEOF_STRUCT_EDGE * INIT_NUM_EDGES];
        edgeMinY = Float.POSITIVE_INFINITY;
        edgeMaxY = Float.NEGATIVE_INFINITY;
        edgesSize = 0;

        this.windingRule = windingRule;
        this.cache = cache;

        this.boundsMinX = pix_boundsX * SUBPIXEL_POSITIONS_X;
        this.boundsMinY = pix_boundsY * SUBPIXEL_POSITIONS_Y;
        this.boundsMaxX = (pix_boundsX + pix_boundsWidth) * SUBPIXEL_POSITIONS_X;
        this.boundsMaxY = (pix_boundsY + pix_boundsHeight) * SUBPIXEL_POSITIONS_Y;

        this.pix_bboxX0 = pix_boundsX;
        this.pix_bboxY0 = pix_boundsY;
        this.pix_bboxX1 = pix_boundsX + pix_boundsWidth;
        this.pix_bboxY1 = pix_boundsY + pix_boundsHeight;
    }

    private float tosubpixx(float pix_x) {
        return pix_x * SUBPIXEL_POSITIONS_X;
    }
    private float tosubpixy(float pix_y) {
        return pix_y * SUBPIXEL_POSITIONS_Y;
    }

    public void moveTo(float pix_x0, float pix_y0) {
        close();
        this.pix_sx0 = pix_x0;
        this.pix_sy0 = pix_y0;
        this.y0 = tosubpixy(pix_y0);
        this.x0 = tosubpixx(pix_x0);
    }

    public void lineJoin() { /* do nothing */ }

    public void lineTo(float pix_x1, float pix_y1) {
        float x1 = tosubpixx(pix_x1);
        float y1 = tosubpixy(pix_y1);

        // Ignore horizontal lines
        if (y0 == y1) {
            this.x0 = x1;
            return;
        }

        addEdge(x0, y0, x1, y1);

        this.x0 = x1;
        this.y0 = y1;
    }

    public void close() {
        // lineTo expects its input in pixel coordinates.
        lineTo(pix_sx0, pix_sy0);
    }

    public void end() {
        close();
    }

    private void _endRendering() {
        // Mask to determine the relevant bit of the crossing sum
        // 0x1 if EVEN_ODD, all bits if NON_ZERO
        int mask = (windingRule == WIND_EVEN_ODD) ? 0x1 : ~0x0;

        // add 1 to better deal with the last pixel in a pixel row.
        int width = ((boundsMaxX - boundsMinX) >> SUBPIXEL_LG_POSITIONS_X) + 1;
        byte[] alpha = new byte[width+1];

        // Now we iterate through the scanlines. We must tell emitRow the coord
        // of the first non-transparent pixel, so we must keep accumulators for
        // the first and last pixels of the section of the current pixel row
        // that we will emit.
        // We also need to accumulate pix_bbox*, but the iterator does it
        // for us. We will just get the values from it once this loop is done
        int pix_maxX = Integer.MIN_VALUE;
        int pix_minX = Integer.MAX_VALUE;

        int y = boundsMinY; // needs to be declared here so we emit the last row properly.
        ScanLineItInitialize();
        for ( ; ScanLineItHasNext(); ) {
            int numCrossings = ScanLineItGoToNextYAndComputeCrossings();
            y = ScanLineItCurrentY();

            if (numCrossings > 0) {
                int lowx = crossings[0] >> 1;
                int highx = crossings[numCrossings - 1] >> 1;
                int x0 = Math.max(lowx, boundsMinX);
                int x1 = Math.min(highx, boundsMaxX);

                pix_minX = Math.min(pix_minX, x0 >> SUBPIXEL_LG_POSITIONS_X);
                pix_maxX = Math.max(pix_maxX, x1 >> SUBPIXEL_LG_POSITIONS_X);
            }

            int sum = 0;
            int prev = boundsMinX;
            for (int i = 0; i < numCrossings; i++) {
                int curxo = crossings[i];
                int curx = curxo >> 1;
                int crorientation = ((curxo & 0x1) == 0x1) ? 1 : -1;
                if ((sum & mask) != 0) {
                    int x0 = Math.max(prev, boundsMinX);
                    int x1 = Math.min(curx, boundsMaxX);
                    if (x0 < x1) {
                        x0 -= boundsMinX; // turn x0, x1 from coords to indeces
                        x1 -= boundsMinX; // in the alpha array.

                        int pix_x = x0 >> SUBPIXEL_LG_POSITIONS_X;
                        int pix_xmaxm1 = (x1 - 1) >> SUBPIXEL_LG_POSITIONS_X;

                        if (pix_x == pix_xmaxm1) {
                            // Start and end in same pixel
                            alpha[pix_x] += (x1 - x0);
                            alpha[pix_x+1] -= (x1 - x0);
                        } else {
                            int pix_xmax = x1 >> SUBPIXEL_LG_POSITIONS_X;
                            alpha[pix_x] += SUBPIXEL_POSITIONS_X - (x0 & SUBPIXEL_MASK_X);
                            alpha[pix_x+1] += (x0 & SUBPIXEL_MASK_X);
                            alpha[pix_xmax] -= SUBPIXEL_POSITIONS_X - (x1 & SUBPIXEL_MASK_X);
                            alpha[pix_xmax+1] -= (x1 & SUBPIXEL_MASK_X);
                        }
                    }
                }
                sum += crorientation;
                prev = curx;
            }

            if ((y & SUBPIXEL_MASK_Y) == SUBPIXEL_MASK_Y) {
                emitRow(alpha, y >> SUBPIXEL_LG_POSITIONS_Y, pix_minX, pix_maxX);
                pix_minX = Integer.MAX_VALUE;
                pix_maxX = Integer.MIN_VALUE;
            }
        }

        // Emit final row
        if (pix_maxX >= pix_minX) {
            emitRow(alpha, y >> SUBPIXEL_LG_POSITIONS_Y, pix_minX, pix_maxX);
        }
        pix_bboxX0 = minX >> SUBPIXEL_LG_POSITIONS_X;
        pix_bboxX1 = maxX >> SUBPIXEL_LG_POSITIONS_X;
        pix_bboxY0 = minY >> SUBPIXEL_LG_POSITIONS_Y;
        pix_bboxY1 = maxY >> SUBPIXEL_LG_POSITIONS_Y;
    }


    public void endRendering() {
        // Set up the cache to accumulate the bounding box
        if (cache != null) {
            cache.bboxX0 = Integer.MAX_VALUE;
            cache.bboxY0 = Integer.MAX_VALUE;
            cache.bboxX1 = Integer.MIN_VALUE;
            cache.bboxY1 = Integer.MIN_VALUE;
        }

        _endRendering();
    }

    public void getBoundingBox(int[] pix_bbox) {
        pix_bbox[0] = pix_bboxX0;
        pix_bbox[1] = pix_bboxY0;
        pix_bbox[2] = pix_bboxX1 - pix_bboxX0;
        pix_bbox[3] = pix_bboxY1 - pix_bboxY0;
    }

    private void emitRow(byte[] alphaRow, int pix_y, int pix_from, int pix_to) {
        // Copy rowAA data into the cache if one is present
        if (cache != null) {
            if (pix_to >= pix_from) {
                cache.startRow(pix_y, pix_from, pix_to);

                // Perform run-length encoding and store results in the cache
                int from = pix_from - (boundsMinX >> SUBPIXEL_LG_POSITIONS_X);
                int to = pix_to - (boundsMinX >> SUBPIXEL_LG_POSITIONS_X);

                int runLen = 1;
                byte startVal = alphaRow[from];
                for (int i = from + 1; i <= to; i++) {
                    byte nextVal = (byte)(startVal + alphaRow[i]);
                    if (nextVal == startVal && runLen < 255) {
                        runLen++;
                    } else {
                        cache.addRLERun(startVal, runLen);
                        runLen = 1;
                        startVal = nextVal;
                    }
                }
                cache.addRLERun(startVal, runLen);
                cache.addRLERun((byte)0, 0);
            }
        }
        java.util.Arrays.fill(alphaRow, (byte)0);
    }
}
