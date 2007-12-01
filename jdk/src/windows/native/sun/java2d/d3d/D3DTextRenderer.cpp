/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "sun_java2d_d3d_D3DTextRenderer.h"
#include "SurfaceData.h"
#include "Region.h"
#include "glyphblitting.h"

extern "C" {

#ifndef D3D_GCACHE_WIDTH
  #define D3D_GCACHE_WIDTH 512
  #define D3D_GCACHE_HEIGHT 512
  #define D3D_GCACHE_CELL_WIDTH 16
  #define D3D_GCACHE_CELL_HEIGHT 16
#endif

/**
 * This method is almost exactly the same as the RefineBounds() method
 * defined in DrawGlyphList.c.  The goal is to determine whether the given
 * GlyphBlitVector intersects with the given bounding box.  If any part of
 * the GBV intersects with the bounding box, this method returns true;
 * otherwise false is returned.  The only step that differs in this method
 * from RefineBounds() is that we check to see whether all the glyphs in
 * the GBV will fit in the glyph cache.  If any glyph is too big for a
 * glyph cache cell, we return FALSE in the useCache out parameter; otherwise
 * useCache is TRUE, indicating that the caller can be assured that all
 * the glyphs can be stored in the accelerated glyph cache.
 */
jboolean
D3DRefineBounds(GlyphBlitVector *gbv, SurfaceDataBounds *bounds,
                jboolean *useCache)
{
    int index;
    jint dx1, dy1, dx2, dy2;
    ImageRef glyphImage;
    int num = gbv->numGlyphs;
    SurfaceDataBounds glyphs;
    jboolean tryCache = JNI_TRUE;

    glyphs.x1 = glyphs.y1 = 0x7fffffff;
    glyphs.x2 = glyphs.y2 = 0x80000000;
    for (index = 0; index < num; index++) {
        glyphImage = gbv->glyphs[index];
        dx1 = (jint) glyphImage.x;
        dy1 = (jint) glyphImage.y;
        dx2 = dx1 + glyphImage.width;
        dy2 = dy1 + glyphImage.height;
        if (glyphs.x1 > dx1) glyphs.x1 = dx1;
        if (glyphs.y1 > dy1) glyphs.y1 = dy1;
        if (glyphs.x2 < dx2) glyphs.x2 = dx2;
        if (glyphs.y2 < dy2) glyphs.y2 = dy2;

        if (tryCache &&
            ((glyphImage.width > D3D_GCACHE_CELL_WIDTH) ||
             (glyphImage.height > D3D_GCACHE_CELL_HEIGHT)))
        {
            tryCache = JNI_FALSE;
        }
    }

    *useCache = tryCache;

    SurfaceData_IntersectBounds(bounds, &glyphs);
    return (bounds->x1 < bounds->x2 && bounds->y1 < bounds->y2);
}

extern JNIEXPORT void JNICALL
    D3DDrawGlyphList(JNIEnv *env, jobject d3dtr,
                     jlong pData, jlong pCtx,
                     ImageRef *glyphs, jint totalGlyphs,
                     jboolean useCache);

/*
 * Class:     sun_java2d_d3d_D3DTextRenderer
 * Method:    doDrawGlyphList
 * Signature: (JLsun/java2d/pipe/Region;Lsun/font/GlyphList;)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_d3d_D3DTextRenderer_doDrawGlyphList
    (JNIEnv *env, jobject d3dtr,
     jlong pData,
     jlong pCtx, jobject clip, jobject glyphlist)
{
    GlyphBlitVector* gbv;
    SurfaceDataBounds bounds;
    jboolean useCache;

    if ((pData == 0) || (pCtx == 0)) {
        return;
    }

    Region_GetBounds(env, clip, &bounds);

    if ((gbv = setupBlitVector(env, glyphlist)) == NULL) {
        return;
    }

    if (!D3DRefineBounds(gbv, &bounds, &useCache)) {
        free(gbv);
        return;
    }

    D3DDrawGlyphList(env, d3dtr,
                     pData, pCtx,
                     gbv->glyphs, gbv->numGlyphs, useCache);
    free(gbv);
}

}
