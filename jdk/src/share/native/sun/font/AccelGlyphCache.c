/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <malloc.h>
#include "jni.h"
#include "AccelGlyphCache.h"
#include "Trace.h"

/**
 * When the cache is full, we will try to reuse the cache cells that have
 * been used relatively less than the others (and we will save the cells that
 * have been rendered more than the threshold defined here).
 */
#define TIMES_RENDERED_THRESHOLD 5

/**
 * Creates a new GlyphCacheInfo structure, fills in the initial values, and
 * then returns a pointer to the GlyphCacheInfo record.
 *
 * Note that this method only sets up a data structure describing a
 * rectangular region of accelerated memory, containing "virtual" cells of
 * the requested size.  The cell information is added lazily to the linked
 * list describing the cache as new glyphs are added.  Platform specific
 * glyph caching code is responsible for actually creating the accelerated
 * memory surface that will contain the individual glyph images.
 */
GlyphCacheInfo *
AccelGlyphCache_Init(jint width, jint height,
                     jint cellWidth, jint cellHeight,
                     FlushFunc *func)
{
    GlyphCacheInfo *gcinfo;

    J2dTraceLn(J2D_TRACE_INFO, "AccelGlyphCache_Init");

    gcinfo = (GlyphCacheInfo *)malloc(sizeof(GlyphCacheInfo));
    if (gcinfo == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
            "AccelGlyphCache_Init: could not allocate GlyphCacheInfo");
        return NULL;
    }

    gcinfo->head = NULL;
    gcinfo->tail = NULL;
    gcinfo->width = width;
    gcinfo->height = height;
    gcinfo->cellWidth = cellWidth;
    gcinfo->cellHeight = cellHeight;
    gcinfo->isFull = JNI_FALSE;
    gcinfo->Flush = func;

    return gcinfo;
}

/**
 * Attempts to add the provided glyph to the specified cache.  If the
 * operation is successful, a pointer to the newly occupied cache cell is
 * stored in the glyph's cellInfo field; otherwise, its cellInfo field is
 * set to NULL, indicating that the glyph's original bits should be rendered
 * instead.  If the cache is full, the least-recently-used glyph is
 * invalidated and its cache cell is reassigned to the new glyph being added.
 *
 * Note that this method only ensures that a rectangular region in the
 * "virtual" glyph cache is available for the glyph image.  Platform specific
 * glyph caching code is responsible for actually caching the glyph image
 * in the associated accelerated memory surface.
 */
void
AccelGlyphCache_AddGlyph(GlyphCacheInfo *cache, GlyphInfo *glyph)
{
    CacheCellInfo *cellinfo = NULL;
    jint w = glyph->width;
    jint h = glyph->height;

    J2dTraceLn(J2D_TRACE_INFO, "AccelGlyphCache_AddGlyph");

    if ((glyph->width > cache->cellWidth) ||
        (glyph->height > cache->cellHeight))
    {
        return;
    }

    if (!cache->isFull) {
        jint x, y;

        if (cache->head == NULL) {
            x = 0;
            y = 0;
        } else {
            x = cache->tail->x + cache->cellWidth;
            y = cache->tail->y;
            if ((x + cache->cellWidth) > cache->width) {
                x = 0;
                y += cache->cellHeight;
                if ((y + cache->cellHeight) > cache->height) {
                    // no room left for a new cell; we'll go through the
                    // isFull path below
                    cache->isFull = JNI_TRUE;
                }
            }
        }

        if (!cache->isFull) {
            // create new CacheCellInfo
            cellinfo = (CacheCellInfo *)malloc(sizeof(CacheCellInfo));
            if (cellinfo == NULL) {
                glyph->cellInfo = NULL;
                J2dTraceLn(J2D_TRACE_ERROR, "could not allocate CellInfo");
                return;
            }

            cellinfo->cacheInfo = cache;
            cellinfo->glyphInfo = glyph;
            cellinfo->timesRendered = 0;
            cellinfo->x = x;
            cellinfo->y = y;
            cellinfo->tx1 = (jfloat)cellinfo->x / cache->width;
            cellinfo->ty1 = (jfloat)cellinfo->y / cache->height;
            cellinfo->tx2 = cellinfo->tx1 + ((jfloat)w / cache->width);
            cellinfo->ty2 = cellinfo->ty1 + ((jfloat)h / cache->height);

            if (cache->head == NULL) {
                // initialize the head cell
                cache->head = cellinfo;
            } else {
                // update existing tail cell
                cache->tail->next = cellinfo;
            }

            // add the new cell to the end of the list
            cache->tail = cellinfo;
            cellinfo->next = NULL;
        }
    }

    if (cache->isFull) {
        /**
         * Search through the cells, and for each cell:
         *   - reset its timesRendered counter to zero
         *   - toss it to the end of the list
         * Eventually we will find a cell that either:
         *   - is empty, or
         *   - has been used less than the threshold
         * When we find such a cell, we will:
         *   - break out of the loop
         *   - invalidate any glyph that may be residing in that cell
         *   - update the cell with the new resident glyph's information
         *
         * The goal here is to keep the glyphs rendered most often in the
         * cache, while younger glyphs hang out near the end of the list.
         * Those young glyphs that have only been used a few times will move
         * towards the head of the list and will eventually be kicked to
         * the curb.
         *
         * In the worst-case scenario, all cells will be occupied and they
         * will all have timesRendered counts above the threshold, so we will
         * end up iterating through all the cells exactly once.  Since we are
         * resetting their counters along the way, we are guaranteed to
         * eventually hit the original "head" cell, whose counter is now zero.
         * This avoids the possibility of an infinite loop.
         */

        do {
            // the head cell will be updated on each iteration
            CacheCellInfo *current = cache->head;

            if ((current->glyphInfo == NULL) ||
                (current->timesRendered < TIMES_RENDERED_THRESHOLD))
            {
                // all bow before the chosen one (we will break out of the
                // loop now that we've found an appropriate cell)
                cellinfo = current;
            }

            // move cell to the end of the list; update existing head and
            // tail pointers
            cache->head = current->next;
            cache->tail->next = current;
            cache->tail = current;
            current->next = NULL;
            current->timesRendered = 0;
        } while (cellinfo == NULL);

        if (cellinfo->glyphInfo != NULL) {
            // flush in case any pending vertices are depending on the
            // glyph that is about to be kicked out
            if (cache->Flush != NULL) {
                cache->Flush();
            }

            // if the cell is occupied, notify the base glyph that its
            // cached version is about to be kicked out
            cellinfo->glyphInfo->cellInfo = NULL;
        }

        // update cellinfo with glyph's occupied region information
        cellinfo->glyphInfo = glyph;
        cellinfo->tx2 = cellinfo->tx1 + ((jfloat)w / cache->width);
        cellinfo->ty2 = cellinfo->ty1 + ((jfloat)h / cache->height);
    }

    // update the glyph's reference to its cache cell
    glyph->cellInfo = cellinfo;
}

/**
 * Invalidates all cells in the cache.  Note that this method does not
 * attempt to compact the cache in any way; it just invalidates any cells
 * that already exist.
 */
void
AccelGlyphCache_Invalidate(GlyphCacheInfo *cache)
{
    CacheCellInfo *cellinfo;

    J2dTraceLn(J2D_TRACE_INFO, "AccelGlyphCache_Invalidate");

    if (cache == NULL) {
        return;
    }

    // flush any pending vertices that may be depending on the current
    // glyph cache layout
    if (cache->Flush != NULL) {
        cache->Flush();
    }

    cellinfo = cache->head;
    while (cellinfo != NULL) {
        if (cellinfo->glyphInfo != NULL) {
            // if the cell is occupied, notify the base glyph that its
            // cached version is about to be invalidated
            cellinfo->glyphInfo->cellInfo = NULL;
            cellinfo->glyphInfo = NULL;
        }
        cellinfo = cellinfo->next;
    }
}
