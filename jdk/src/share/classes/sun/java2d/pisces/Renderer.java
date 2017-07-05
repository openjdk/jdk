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
import java.util.Iterator;

import sun.awt.geom.PathConsumer2D;

public class Renderer implements PathConsumer2D {

    private class ScanlineIterator {

        private int[] crossings;

        // crossing bounds. The bounds are not necessarily tight (the scan line
        // at minY, for example, might have no crossings). The x bounds will
        // be accumulated as crossings are computed.
        private int minY, maxY;
        private int nextY;

        // indices into the segment pointer lists. They indicate the "active"
        // sublist in the segment lists (the portion of the list that contains
        // all the segments that cross the next scan line).
        private int elo, ehi;
        private final int[] edgePtrs;
        private int qlo, qhi;
        private final int[] quadPtrs;
        private int clo, chi;
        private final int[] curvePtrs;

        private static final int INIT_CROSSINGS_SIZE = 10;

        private ScanlineIterator() {
            crossings = new int[INIT_CROSSINGS_SIZE];

            edgePtrs = new int[numEdges];
            Helpers.fillWithIdxes(edgePtrs, SIZEOF_EDGE);
            qsort(edges, edgePtrs, YMIN, 0, numEdges - 1);

            quadPtrs = new int[numQuads];
            Helpers.fillWithIdxes(quadPtrs, SIZEOF_QUAD);
            qsort(quads, quadPtrs, YMIN, 0, numQuads - 1);

            curvePtrs = new int[numCurves];
            Helpers.fillWithIdxes(curvePtrs, SIZEOF_CURVE);
            qsort(curves, curvePtrs, YMIN, 0, numCurves - 1);

            // We don't care if we clip some of the line off with ceil, since
            // no scan line crossings will be eliminated (in fact, the ceil is
            // the y of the first scan line crossing).
            nextY = minY = Math.max(boundsMinY, (int)Math.ceil(edgeMinY));
            maxY = Math.min(boundsMaxY, (int)Math.ceil(edgeMaxY));

            for (elo = 0; elo < numEdges && edges[edgePtrs[elo]+YMAX] <= minY; elo++)
                ;
            // the active list is *edgePtrs[lo] (inclusive) *edgePtrs[hi] (exclusive)
            for (ehi = elo; ehi < numEdges && edges[edgePtrs[ehi]+YMIN] <= minY; ehi++)
                edgeSetCurY(edgePtrs[ehi], minY);// TODO: make minY a float to avoid casts

            for (qlo = 0; qlo < numQuads && quads[quadPtrs[qlo]+YMAX] <= minY; qlo++)
                ;
            for (qhi = qlo; qhi < numQuads && quads[quadPtrs[qhi]+YMIN] <= minY; qhi++)
                quadSetCurY(quadPtrs[qhi], minY);

            for (clo = 0; clo < numCurves && curves[curvePtrs[clo]+YMAX] <= minY; clo++)
                ;
            for (chi = clo; chi < numCurves && curves[curvePtrs[chi]+YMIN] <= minY; chi++)
                curveSetCurY(curvePtrs[chi], minY);
        }

        private int next() {
            // we go through the active lists and remove segments that don't cross
            // the nextY scanline.
            int crossingIdx = 0;
            for (int i = elo; i < ehi; i++) {
                if (edges[edgePtrs[i]+YMAX] <= nextY) {
                    edgePtrs[i] = edgePtrs[elo++];
                }
            }
            for (int i = qlo; i < qhi; i++) {
                if (quads[quadPtrs[i]+YMAX] <= nextY) {
                    quadPtrs[i] = quadPtrs[qlo++];
                }
            }
            for (int i = clo; i < chi; i++) {
                if (curves[curvePtrs[i]+YMAX] <= nextY) {
                    curvePtrs[i] = curvePtrs[clo++];
                }
            }

            crossings = Helpers.widenArray(crossings, 0, ehi-elo+qhi-qlo+chi-clo);

            // Now every edge between lo and hi crosses nextY. Compute it's
            // crossing and put it in the crossings array.
            for (int i = elo; i < ehi; i++) {
                int ptr = edgePtrs[i];
                addCrossing(nextY, (int)edges[ptr+CURX], edges[ptr+OR], crossingIdx);
                edgeGoToNextY(ptr);
                crossingIdx++;
            }
            for (int i = qlo; i < qhi; i++) {
                int ptr = quadPtrs[i];
                addCrossing(nextY, (int)quads[ptr+CURX], quads[ptr+OR], crossingIdx);
                quadGoToNextY(ptr);
                crossingIdx++;
            }
            for (int i = clo; i < chi; i++) {
                int ptr = curvePtrs[i];
                addCrossing(nextY, (int)curves[ptr+CURX], curves[ptr+OR], crossingIdx);
                curveGoToNextY(ptr);
                crossingIdx++;
            }

            nextY++;
            // Expand active lists to include new edges.
            for (; ehi < numEdges && edges[edgePtrs[ehi]+YMIN] <= nextY; ehi++) {
                edgeSetCurY(edgePtrs[ehi], nextY);
            }
            for (; qhi < numQuads && quads[quadPtrs[qhi]+YMIN] <= nextY; qhi++) {
                quadSetCurY(quadPtrs[qhi], nextY);
            }
            for (; chi < numCurves && curves[curvePtrs[chi]+YMIN] <= nextY; chi++) {
                curveSetCurY(curvePtrs[chi], nextY);
            }
            Arrays.sort(crossings, 0, crossingIdx);
            return crossingIdx;
        }

        private boolean hasNext() {
            return nextY < maxY;
        }

        private int curY() {
            return nextY - 1;
        }

        private void addCrossing(int y, int x, float or, int idx) {
            x <<= 1;
            crossings[idx] = ((or > 0) ? (x | 0x1) : x);
        }
    }
    // quicksort implementation for sorting the edge indices ("pointers")
    // by increasing y0. first, last are indices into the "pointer" array
    // It sorts the pointer array from first (inclusive) to last (inclusive)
    private static void qsort(final float[] data, final int[] ptrs,
                              final int fieldForCmp, int first, int last)
    {
        if (last > first) {
            int p = partition(data, ptrs, fieldForCmp, first, last);
            if (first < p - 1) {
                qsort(data, ptrs, fieldForCmp, first, p - 1);
            }
            if (p < last) {
                qsort(data, ptrs, fieldForCmp, p, last);
            }
        }
    }

    // i, j are indices into edgePtrs.
    private static int partition(final float[] data, final int[] ptrs,
                                 final int fieldForCmp, int i, int j)
    {
        int pivotValFieldForCmp = ptrs[i]+fieldForCmp;
        while (i <= j) {
            // edges[edgePtrs[i]+1] is equivalent to (*(edgePtrs[i])).y0 in C
            while (data[ptrs[i]+fieldForCmp] < data[pivotValFieldForCmp])
                i++;
            while (data[ptrs[j]+fieldForCmp] > data[pivotValFieldForCmp])
                j--;
            if (i <= j) {
                int tmp = ptrs[i];
                ptrs[i] = ptrs[j];
                ptrs[j] = tmp;
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
// TODO(maybe): very tempting to use fixed point here. A lot of opportunities
// for shifts and just removing certain operations altogether.
// TODO: it might be worth it to make an EdgeList class. It would probably
// clean things up a bit and not impact performance much.

    // common to all types of input path segments.
    private static final int YMIN = 0;
    private static final int YMAX = 1;
    private static final int CURX = 2;
    // this and OR are meant to be indeces into "int" fields, but arrays must
    // be homogenous, so every field is a float. However floats can represent
    // exactly up to 26 bit ints, so we're ok.
    private static final int CURY = 3;
    private static final int OR   = 4;

    // for straight lines only:
    private static final int SLOPE = 5;

    // for quads and cubics:
    private static final int X0 = 5;
    private static final int Y0 = 6;
    private static final int XL = 7;
    private static final int COUNT = 8;
    private static final int CURSLOPE = 9;
    private static final int DX = 10;
    private static final int DY = 11;
    private static final int DDX = 12;
    private static final int DDY = 13;

    // for cubics only
    private static final int DDDX = 14;
    private static final int DDDY = 15;

    private float edgeMinY = Float.POSITIVE_INFINITY;
    private float edgeMaxY = Float.NEGATIVE_INFINITY;
    private float edgeMinX = Float.POSITIVE_INFINITY;
    private float edgeMaxX = Float.NEGATIVE_INFINITY;

    private static final int SIZEOF_EDGE = 6;
    private float[] edges = null;
    private int numEdges;
    // these are static because we need them to be usable from ScanlineIterator
    private void edgeSetCurY(final int idx, int y) {
        edges[idx+CURX] += (y - edges[idx+CURY]) * edges[idx+SLOPE];
        edges[idx+CURY] = y;
    }
    private void edgeGoToNextY(final int idx) {
        edges[idx+CURY] += 1;
        edges[idx+CURX] += edges[idx+SLOPE];
    }


    private static final int SIZEOF_QUAD = 14;
    private float[] quads = null;
    private int numQuads;
    // This function should be called exactly once, to set the first scanline
    // of the curve. Before it is called, the curve should think its first
    // scanline is CEIL(YMIN).
    private void quadSetCurY(final int idx, final int y) {
        assert y < quads[idx+YMAX];
        assert (quads[idx+CURY] > y);
        assert (quads[idx+CURY] == Math.ceil(quads[idx+CURY]));

        while (quads[idx+CURY] < ((float)y)) {
            quadGoToNextY(idx);
        }
    }
    private void quadGoToNextY(final int idx) {
        quads[idx+CURY] += 1;
        // this will get overriden if the while executes.
        quads[idx+CURX] += quads[idx+CURSLOPE];
        int count = (int)quads[idx+COUNT];
        // this loop should never execute more than once because our
        // curve is monotonic in Y. Still we put it in because you can
        // never be too sure when dealing with floating point.
        while(quads[idx+CURY] >= quads[idx+Y0] && count > 0) {
            float x0 = quads[idx+X0], y0 = quads[idx+Y0];
            count = executeQuadAFDIteration(idx);
            float x1 = quads[idx+X0], y1 = quads[idx+Y0];
            // our quads are monotonic, so this shouldn't happen, but
            // it is conceivable that for very flat quads with different
            // y values at their endpoints AFD might give us a horizontal
            // segment.
            if (y1 == y0) {
                continue;
            }
            quads[idx+CURSLOPE] = (x1 - x0) / (y1 - y0);
            quads[idx+CURX] = x0 + (quads[idx+CURY] - y0) * quads[idx+CURSLOPE];
        }
    }


    private static final int SIZEOF_CURVE = 16;
    private float[] curves = null;
    private int numCurves;
    private void curveSetCurY(final int idx, final int y) {
        assert y < curves[idx+YMAX];
        assert (curves[idx+CURY] > y);
        assert (curves[idx+CURY] == Math.ceil(curves[idx+CURY]));

        while (curves[idx+CURY] < ((float)y)) {
            curveGoToNextY(idx);
        }
    }
    private void curveGoToNextY(final int idx) {
        curves[idx+CURY] += 1;
        // this will get overriden if the while executes.
        curves[idx+CURX] += curves[idx+CURSLOPE];
        int count = (int)curves[idx+COUNT];
        // this loop should never execute more than once because our
        // curve is monotonic in Y. Still we put it in because you can
        // never be too sure when dealing with floating point.
        while(curves[idx+CURY] >= curves[idx+Y0] && count > 0) {
            float x0 = curves[idx+X0], y0 = curves[idx+Y0];
            count = executeCurveAFDIteration(idx);
            float x1 = curves[idx+X0], y1 = curves[idx+Y0];
            // our curves are monotonic, so this shouldn't happen, but
            // it is conceivable that for very flat curves with different
            // y values at their endpoints AFD might give us a horizontal
            // segment.
            if (y1 == y0) {
                continue;
            }
            curves[idx+CURSLOPE] = (x1 - x0) / (y1 - y0);
            curves[idx+CURX] = x0 + (curves[idx+CURY] - y0) * curves[idx+CURSLOPE];
        }
    }


    private static final float DEC_BND = 20f;
    private static final float INC_BND = 8f;
    // Flattens using adaptive forward differencing. This only carries out
    // one iteration of the AFD loop. All it does is update AFD variables (i.e.
    // X0, Y0, D*[X|Y], COUNT; not variables used for computing scanline crossings).
    private int executeQuadAFDIteration(int idx) {
        int count = (int)quads[idx+COUNT];
        float ddx = quads[idx+DDX];
        float ddy = quads[idx+DDY];
        float dx = quads[idx+DX];
        float dy = quads[idx+DY];

        while (Math.abs(ddx) > DEC_BND || Math.abs(ddy) > DEC_BND) {
            ddx = ddx / 4;
            ddy = ddy / 4;
            dx = (dx - ddx) / 2;
            dy = (dy - ddy) / 2;
            count <<= 1;
        }
        // can only do this on even "count" values, because we must divide count by 2
        while (count % 2 == 0 && Math.abs(dx) <= INC_BND && Math.abs(dy) <= INC_BND) {
            dx = 2 * dx + ddx;
            dy = 2 * dy + ddy;
            ddx = 4 * ddx;
            ddy = 4 * ddy;
            count >>= 1;
        }
        count--;
        if (count > 0) {
            quads[idx+X0] += dx;
            dx += ddx;
            quads[idx+Y0] += dy;
            dy += ddy;
        } else {
            quads[idx+X0] = quads[idx+XL];
            quads[idx+Y0] = quads[idx+YMAX];
        }
        quads[idx+COUNT] = count;
        quads[idx+DDX] = ddx;
        quads[idx+DDY] = ddy;
        quads[idx+DX] = dx;
        quads[idx+DY] = dy;
        return count;
    }
    private int executeCurveAFDIteration(int idx) {
        int count = (int)curves[idx+COUNT];
        float ddx = curves[idx+DDX];
        float ddy = curves[idx+DDY];
        float dx = curves[idx+DX];
        float dy = curves[idx+DY];
        float dddx = curves[idx+DDDX];
        float dddy = curves[idx+DDDY];

        while (Math.abs(ddx) > DEC_BND || Math.abs(ddy) > DEC_BND) {
            dddx /= 8;
            dddy /= 8;
            ddx = ddx/4 - dddx;
            ddy = ddy/4 - dddy;
            dx = (dx - ddx) / 2;
            dy = (dy - ddy) / 2;
            count <<= 1;
        }
        // can only do this on even "count" values, because we must divide count by 2
        while (count % 2 == 0 && Math.abs(dx) <= INC_BND && Math.abs(dy) <= INC_BND) {
            dx = 2 * dx + ddx;
            dy = 2 * dy + ddy;
            ddx = 4 * (ddx + dddx);
            ddy = 4 * (ddy + dddy);
            dddx = 8 * dddx;
            dddy = 8 * dddy;
            count >>= 1;
        }
        count--;
        if (count > 0) {
            curves[idx+X0] += dx;
            dx += ddx;
            ddx += dddx;
            curves[idx+Y0] += dy;
            dy += ddy;
            ddy += dddy;
        } else {
            curves[idx+X0] = curves[idx+XL];
            curves[idx+Y0] = curves[idx+YMAX];
        }
        curves[idx+COUNT] = count;
        curves[idx+DDDX] = dddx;
        curves[idx+DDDY] = dddy;
        curves[idx+DDX] = ddx;
        curves[idx+DDY] = ddy;
        curves[idx+DX] = dx;
        curves[idx+DY] = dy;
        return count;
    }


    private void initLine(final int idx, float[] pts, int or) {
        edges[idx+SLOPE] = (pts[2] - pts[0]) / (pts[3] - pts[1]);
        edges[idx+CURX] = pts[0] + (edges[idx+CURY] - pts[1]) * edges[idx+SLOPE];
    }

    private void initQuad(final int idx, float[] points, int or) {
        final int countlg = 3;
        final int count = 1 << countlg;

        // the dx and dy refer to forward differencing variables, not the last
        // coefficients of the "points" polynomial
        final float ddx, ddy, dx, dy;
        c.set(points, 6);

        ddx = c.dbx / (1 << (2 * countlg));
        ddy = c.dby / (1 << (2 * countlg));
        dx = c.bx / (1 << (2 * countlg)) + c.cx / (1 << countlg);
        dy = c.by / (1 << (2 * countlg)) + c.cy / (1 << countlg);

        quads[idx+DDX] = ddx;
        quads[idx+DDY] = ddy;
        quads[idx+DX] = dx;
        quads[idx+DY] = dy;
        quads[idx+COUNT] = count;
        quads[idx+XL] = points[4];
        quads[idx+X0] = points[0];
        quads[idx+Y0] = points[1];
        executeQuadAFDIteration(idx);
        float x1 = quads[idx+X0], y1 = quads[idx+Y0];
        quads[idx+CURSLOPE] = (x1 - points[0]) / (y1 - points[1]);
        quads[idx+CURX] = points[0] + (quads[idx+CURY] - points[1])*quads[idx+CURSLOPE];
    }

    private void initCurve(final int idx, float[] points, int or) {
        final int countlg = 3;
        final int count = 1 << countlg;

        // the dx and dy refer to forward differencing variables, not the last
        // coefficients of the "points" polynomial
        final float dddx, dddy, ddx, ddy, dx, dy;
        c.set(points, 8);
        dddx = 2f * c.dax / (1 << (3 * countlg));
        dddy = 2f * c.day / (1 << (3 * countlg));

        ddx = dddx + c.dbx / (1 << (2 * countlg));
        ddy = dddy + c.dby / (1 << (2 * countlg));
        dx = c.ax / (1 << (3 * countlg)) + c.bx / (1 << (2 * countlg)) + c.cx / (1 << countlg);
        dy = c.ay / (1 << (3 * countlg)) + c.by / (1 << (2 * countlg)) + c.cy / (1 << countlg);

        curves[idx+DDDX] = dddx;
        curves[idx+DDDY] = dddy;
        curves[idx+DDX] = ddx;
        curves[idx+DDY] = ddy;
        curves[idx+DX] = dx;
        curves[idx+DY] = dy;
        curves[idx+COUNT] = count;
        curves[idx+XL] = points[6];
        curves[idx+X0] = points[0];
        curves[idx+Y0] = points[1];
        executeCurveAFDIteration(idx);
        float x1 = curves[idx+X0], y1 = curves[idx+Y0];
        curves[idx+CURSLOPE] = (x1 - points[0]) / (y1 - points[1]);
        curves[idx+CURX] = points[0] + (curves[idx+CURY] - points[1])*curves[idx+CURSLOPE];
    }

    private void addPathSegment(float[] pts, final int type, final int or) {
        int idx;
        float[] addTo;
        switch (type) {
        case 4:
            idx = numEdges * SIZEOF_EDGE;
            addTo = edges = Helpers.widenArray(edges, numEdges*SIZEOF_EDGE, SIZEOF_EDGE);
            numEdges++;
            break;
        case 6:
            idx = numQuads * SIZEOF_QUAD;
            addTo = quads = Helpers.widenArray(quads, numQuads*SIZEOF_QUAD, SIZEOF_QUAD);
            numQuads++;
            break;
        case 8:
            idx = numCurves * SIZEOF_CURVE;
            addTo = curves = Helpers.widenArray(curves, numCurves*SIZEOF_CURVE, SIZEOF_CURVE);
            numCurves++;
            break;
        default:
            throw new InternalError();
        }
        // set the common fields, except CURX, for which we must know the kind
        // of curve. NOTE: this must be done before the type specific fields
        // are initialized, because those depend on the common ones.
        addTo[idx+YMIN] = pts[1];
        addTo[idx+YMAX] = pts[type-1];
        addTo[idx+OR] = or;
        addTo[idx+CURY] = (float)Math.ceil(pts[1]);
        switch (type) {
        case 4:
            initLine(idx, pts, or);
            break;
        case 6:
            initQuad(idx, pts, or);
            break;
        case 8:
            initCurve(idx, pts, or);
            break;
        default:
            throw new InternalError();
        }
    }

    // precondition: the curve in pts must be monotonic and increasing in y.
    private void somethingTo(float[] pts, final int type, final int or) {
        // NOTE: it's very important that we check for or >= 0 below (as
        // opposed to or == 1, or or > 0, or anything else). That's
        // because if we check for or==1, when the curve being added
        // is a horizontal line, or will be 0 so or==1 will be false and
        // x0 and y0 will be updated to pts[0] and pts[1] instead of pts[type-2]
        // and pts[type-1], which is the correct thing to do.
        this.x0 = or >= 0 ? pts[type - 2] : pts[0];
        this.y0 = or >= 0 ? pts[type - 1] : pts[1];

        float minY = pts[1], maxY = pts[type - 1];
        if (Math.ceil(minY) >= Math.ceil(maxY) ||
            Math.ceil(minY) >= boundsMaxY || maxY < boundsMinY)
        {
            return;
        }

        if (minY < edgeMinY) { edgeMinY = minY; }
        if (maxY > edgeMaxY) { edgeMaxY = maxY; }

        int minXidx = (pts[0] < pts[type-2] ? 0 : type - 2);
        float minX = pts[minXidx];
        float maxX = pts[type - 2 - minXidx];
        if (minX < edgeMinX) { edgeMinX = minX; }
        if (maxX > edgeMaxX) { edgeMaxX = maxX; }
        addPathSegment(pts, type, or);
    }

// END EDGE LIST
//////////////////////////////////////////////////////////////////////////////


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
    PiscesCache cache;

    // Bounds of the drawing region, at subpixel precision.
    private final int boundsMinX, boundsMinY, boundsMaxX, boundsMaxY;

    // Current winding rule
    private final int windingRule;

    // Current drawing position, i.e., final point of last segment
    private float x0, y0;

    // Position of most recent 'moveTo' command
    private float pix_sx0, pix_sy0;

    public Renderer(int subpixelLgPositionsX, int subpixelLgPositionsY,
                    int pix_boundsX, int pix_boundsY,
                    int pix_boundsWidth, int pix_boundsHeight,
                    int windingRule)
    {
        this.SUBPIXEL_LG_POSITIONS_X = subpixelLgPositionsX;
        this.SUBPIXEL_LG_POSITIONS_Y = subpixelLgPositionsY;
        this.SUBPIXEL_MASK_X = (1 << (SUBPIXEL_LG_POSITIONS_X)) - 1;
        this.SUBPIXEL_MASK_Y = (1 << (SUBPIXEL_LG_POSITIONS_Y)) - 1;
        this.SUBPIXEL_POSITIONS_X = 1 << (SUBPIXEL_LG_POSITIONS_X);
        this.SUBPIXEL_POSITIONS_Y = 1 << (SUBPIXEL_LG_POSITIONS_Y);
        this.MAX_AA_ALPHA = (SUBPIXEL_POSITIONS_X * SUBPIXEL_POSITIONS_Y);

        this.windingRule = windingRule;

        this.boundsMinX = pix_boundsX * SUBPIXEL_POSITIONS_X;
        this.boundsMinY = pix_boundsY * SUBPIXEL_POSITIONS_Y;
        this.boundsMaxX = (pix_boundsX + pix_boundsWidth) * SUBPIXEL_POSITIONS_X;
        this.boundsMaxY = (pix_boundsY + pix_boundsHeight) * SUBPIXEL_POSITIONS_Y;
    }

    private float tosubpixx(float pix_x) {
        return pix_x * SUBPIXEL_POSITIONS_X;
    }
    private float tosubpixy(float pix_y) {
        return pix_y * SUBPIXEL_POSITIONS_Y;
    }

    public void moveTo(float pix_x0, float pix_y0) {
        closePath();
        this.pix_sx0 = pix_x0;
        this.pix_sy0 = pix_y0;
        this.y0 = tosubpixy(pix_y0);
        this.x0 = tosubpixx(pix_x0);
    }

    public void lineJoin() { /* do nothing */ }

    private final float[][] pts = new float[2][8];
    private final float[] ts = new float[4];

    private static void invertPolyPoints(float[] pts, int off, int type) {
        for (int i = off, j = off + type - 2; i < j; i += 2, j -= 2) {
            float tmp = pts[i];
            pts[i] = pts[j];
            pts[j] = tmp;
            tmp = pts[i+1];
            pts[i+1] = pts[j+1];
            pts[j+1] = tmp;
        }
    }

    // return orientation before making the curve upright.
    private static int makeMonotonicCurveUpright(float[] pts, int off, int type) {
        float y0 = pts[off + 1];
        float y1 = pts[off + type - 1];
        if (y0 > y1) {
            invertPolyPoints(pts, off, type);
            return -1;
        } else if (y0 < y1) {
            return 1;
        }
        return 0;
    }

    public void lineTo(float pix_x1, float pix_y1) {
        pts[0][0] = x0; pts[0][1] = y0;
        pts[0][2] = tosubpixx(pix_x1); pts[0][3] = tosubpixy(pix_y1);
        int or = makeMonotonicCurveUpright(pts[0], 0, 4);
        somethingTo(pts[0], 4, or);
    }

    Curve c = new Curve();
    private void curveOrQuadTo(int type) {
        c.set(pts[0], type);
        int numTs = c.dxRoots(ts, 0);
        numTs += c.dyRoots(ts, numTs);
        numTs = Helpers.filterOutNotInAB(ts, 0, numTs, 0, 1);
        Helpers.isort(ts, 0, numTs);

        Iterator<float[]> it = Curve.breakPtsAtTs(pts, type, ts, numTs);
        while(it.hasNext()) {
            float[] curCurve = it.next();
            int or = makeMonotonicCurveUpright(curCurve, 0, type);
            somethingTo(curCurve, type, or);
        }
    }

    @Override public void curveTo(float x1, float y1,
                                  float x2, float y2,
                                  float x3, float y3)
    {
        pts[0][0] = x0; pts[0][1] = y0;
        pts[0][2] = tosubpixx(x1); pts[0][3] = tosubpixy(y1);
        pts[0][4] = tosubpixx(x2); pts[0][5] = tosubpixy(y2);
        pts[0][6] = tosubpixx(x3); pts[0][7] = tosubpixy(y3);
        curveOrQuadTo(8);
    }

    @Override public void quadTo(float x1, float y1, float x2, float y2) {
        pts[0][0] = x0; pts[0][1] = y0;
        pts[0][2] = tosubpixx(x1); pts[0][3] = tosubpixy(y1);
        pts[0][4] = tosubpixx(x2); pts[0][5] = tosubpixy(y2);
        curveOrQuadTo(6);
    }

    public void closePath() {
        // lineTo expects its input in pixel coordinates.
        lineTo(pix_sx0, pix_sy0);
    }

    public void pathDone() {
        closePath();
    }


    @Override
    public long getNativeConsumer() {
        throw new InternalError("Renderer does not use a native consumer.");
    }

    private void _endRendering(final int pix_bboxx0, final int pix_bboxy0,
                               final int pix_bboxx1, final int pix_bboxy1)
    {
        // Mask to determine the relevant bit of the crossing sum
        // 0x1 if EVEN_ODD, all bits if NON_ZERO
        int mask = (windingRule == WIND_EVEN_ODD) ? 0x1 : ~0x0;

        // add 1 to better deal with the last pixel in a pixel row.
        int width = pix_bboxx1 - pix_bboxx0 + 1;
        int[] alpha = new int[width+1];

        int bboxx0 = pix_bboxx0 << SUBPIXEL_LG_POSITIONS_X;
        int bboxx1 = pix_bboxx1 << SUBPIXEL_LG_POSITIONS_X;

        // Now we iterate through the scanlines. We must tell emitRow the coord
        // of the first non-transparent pixel, so we must keep accumulators for
        // the first and last pixels of the section of the current pixel row
        // that we will emit.
        // We also need to accumulate pix_bbox*, but the iterator does it
        // for us. We will just get the values from it once this loop is done
        int pix_maxX = Integer.MIN_VALUE;
        int pix_minX = Integer.MAX_VALUE;

        int y = boundsMinY; // needs to be declared here so we emit the last row properly.
        ScanlineIterator it = this.new ScanlineIterator();
        for ( ; it.hasNext(); ) {
            int numCrossings = it.next();
            int[] crossings = it.crossings;
            y = it.curY();

            if (numCrossings > 0) {
                int lowx = crossings[0] >> 1;
                int highx = crossings[numCrossings - 1] >> 1;
                int x0 = Math.max(lowx, bboxx0);
                int x1 = Math.min(highx, bboxx1);

                pix_minX = Math.min(pix_minX, x0 >> SUBPIXEL_LG_POSITIONS_X);
                pix_maxX = Math.max(pix_maxX, x1 >> SUBPIXEL_LG_POSITIONS_X);
            }

            int sum = 0;
            int prev = bboxx0;
            for (int i = 0; i < numCrossings; i++) {
                int curxo = crossings[i];
                int curx = curxo >> 1;
                int crorientation = ((curxo & 0x1) == 0x1) ? 1 : -1;
                if ((sum & mask) != 0) {
                    int x0 = Math.max(prev, bboxx0);
                    int x1 = Math.min(curx, bboxx1);
                    if (x0 < x1) {
                        x0 -= bboxx0; // turn x0, x1 from coords to indeces
                        x1 -= bboxx0; // in the alpha array.

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

            // even if this last row had no crossings, alpha will be zeroed
            // from the last emitRow call. But this doesn't matter because
            // maxX < minX, so no row will be emitted to the cache.
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
    }

    public void endRendering() {
        final int bminx = boundsMinX >> SUBPIXEL_LG_POSITIONS_X;
        final int bmaxx = boundsMaxX >> SUBPIXEL_LG_POSITIONS_X;
        final int bminy = boundsMinY >> SUBPIXEL_LG_POSITIONS_Y;
        final int bmaxy = boundsMaxY >> SUBPIXEL_LG_POSITIONS_Y;
        final int eminx = ((int)Math.floor(edgeMinX)) >> SUBPIXEL_LG_POSITIONS_X;
        final int emaxx = ((int)Math.ceil(edgeMaxX)) >> SUBPIXEL_LG_POSITIONS_X;
        final int eminy = ((int)Math.floor(edgeMinY)) >> SUBPIXEL_LG_POSITIONS_Y;
        final int emaxy = ((int)Math.ceil(edgeMaxY)) >> SUBPIXEL_LG_POSITIONS_Y;

        final int minX = Math.max(bminx, eminx);
        final int maxX = Math.min(bmaxx, emaxx);
        final int minY = Math.max(bminy, eminy);
        final int maxY = Math.min(bmaxy, emaxy);
        if (minX > maxX || minY > maxY) {
            this.cache = new PiscesCache(bminx, bminy, bmaxx, bmaxy);
            return;
        }

        this.cache = new PiscesCache(minX, minY, maxX, maxY);
        _endRendering(minX, minY, maxX, maxY);
    }

    public PiscesCache getCache() {
        if (cache == null) {
            throw new InternalError("cache not yet initialized");
        }
        return cache;
    }

    private void emitRow(int[] alphaRow, int pix_y, int pix_from, int pix_to) {
        // Copy rowAA data into the cache if one is present
        if (cache != null) {
            if (pix_to >= pix_from) {
                cache.startRow(pix_y, pix_from);

                // Perform run-length encoding and store results in the cache
                int from = pix_from - cache.bboxX0;
                int to = pix_to - cache.bboxX0;

                int runLen = 1;
                int startVal = alphaRow[from];
                for (int i = from + 1; i <= to; i++) {
                    int nextVal = startVal + alphaRow[i];
                    if (nextVal == startVal) {
                        runLen++;
                    } else {
                        cache.addRLERun(startVal, runLen);
                        runLen = 1;
                        startVal = nextVal;
                    }
                }
                cache.addRLERun(startVal, runLen);
            }
        }
        java.util.Arrays.fill(alphaRow, 0);
    }
}
