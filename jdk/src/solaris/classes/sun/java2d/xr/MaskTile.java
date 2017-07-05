/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.java2d.xr;

/**
 * Represents a single tile, used to store the rectangles covering the area
 * of the mask where the tile is located.
 *
 * @author Clemens Eisserer
 */
public class MaskTile {
    GrowableRectArray rects;
    DirtyRegion dirtyArea;

    public MaskTile()
    {
        rects = new GrowableRectArray(128);
        dirtyArea = new DirtyRegion();
    }

    public void addRect(int x, int y, int width, int height) {
        int index = rects.getNextIndex();
        rects.setX(index, x);
        rects.setY(index, y);
        rects.setWidth(index, width);
        rects.setHeight(index, height);
    }

    public void addLine(int x1, int y1, int x2, int y2) {
        /*
         * EXA is not able to accalerate diagonal lines, we try to "guide" it a
         * bit to avoid excessive migration See project documentation for an
         * detailed explanation
         */
        DirtyRegion region = new DirtyRegion();
        region.setDirtyLineRegion(x1, y1, x2, y2);
        int xDiff = region.x2 - region.x;
        int yDiff = region.y2 - region.y;

        if (xDiff == 0 || yDiff == 0) {
            addRect(region.x, region.y,
                    region.x2 - region.x + 1, region.y2 - region.y + 1);
        } else if (xDiff == 1 && yDiff == 1) {
            addRect(x1, y1, 1, 1);
            addRect(x2, y2, 1, 1);
        } else {
            lineToRects(x1, y1, x2, y2);
        }
    }

    private void lineToRects(int xstart, int ystart, int xend, int yend) {
        int x, y, t, dx, dy, incx, incy, pdx, pdy, ddx, ddy, es, el, err;

        /* Entfernung in beiden Dimensionen berechnen */
        dx = xend - xstart;
        dy = yend - ystart;

        /* Vorzeichen des Inkrements bestimmen */
        incx = dx > 0 ? 1 : (dx < 0) ? -1 : 0;
        incy = dy > 0 ? 1 : (dy < 0) ? -1 : 0;
        if (dx < 0)
            dx = -dx;
        if (dy < 0)
            dy = -dy;

        /* feststellen, welche Entfernung groesser ist */
        if (dx > dy) {
            /* x ist schnelle Richtung */
            pdx = incx;
            pdy = 0; /* pd. ist Parallelschritt */
            ddx = incx;
            ddy = incy; /* dd. ist Diagonalschritt */
            es = dy;
            el = dx; /* Fehlerschritte schnell, langsam */
        } else {
            /* y ist schnelle Richtung */
            pdx = 0;
            pdy = incy; /* pd. ist Parallelschritt */
            ddx = incx;
            ddy = incy; /* dd. ist Diagonalschritt */
            es = dx;
            el = dy; /* Fehlerschritte schnell, langsam */
        }

        /* Initialisierungen vor Schleifenbeginn */
        x = xstart;
        y = ystart;
        err = el / 2;
        addRect(x, y, 1, 1);

        /* Pixel berechnen */
        for (t = 0; t < el; ++t) /* t zaehlt die Pixel, el ist auch Anzahl */
        {
            /* Aktualisierung Fehlerterm */
            err -= es;
            if (err < 0) {
                /* Fehlerterm wieder positiv (>=0) machen */
                err += el;
                /* Schritt in langsame Richtung, Diagonalschritt */
                x += ddx;
                y += ddy;
            } else {
                /* Schritt in schnelle Richtung, Parallelschritt */
                x += pdx;
                y += pdy;
            }
            addRect(x, y, 1, 1);
            // SetPixel(x,y);
            // System.out.println(x+":"+y);
        }
    }

    public void calculateDirtyAreas()
    {
        for (int i=0; i < rects.getSize(); i++) {
            int x = rects.getX(i);
            int y = rects.getY(i);
            dirtyArea.growDirtyRegion(x, y,
                                      x + rects.getWidth(i),
                                      y + rects.getHeight(i));
        }
    }

    public void reset() {
        rects.clear();
        dirtyArea.clear();
    }

    public void translate(int x, int y) {
        if (rects.getSize() > 0) {
            dirtyArea.translate(x, y);
        }
        rects.translateRects(x, y);
    }

    public GrowableRectArray getRects() {
        return rects;
    }

    public DirtyRegion getDirtyArea() {
        return dirtyArea;
    }
}
