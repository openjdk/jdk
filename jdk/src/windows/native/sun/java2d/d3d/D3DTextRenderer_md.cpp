/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "fontscalerdefs.h"
#include "AccelGlyphCache.h"
#include "j2d_md.h"
#include "jlong.h"

#include "ddrawUtils.h"
#include "D3DContext.h"
#include "D3DUtils.h"
#include "Win32SurfaceData.h"

extern "C" {

#define MAX_STATIC_QUADS_NUM 40
static int indicesInited = 0;
static short vertexIndices[MAX_STATIC_QUADS_NUM*6];
static J2DLV_QUAD vertexQuads[MAX_STATIC_QUADS_NUM];

/**
 * Initializes the array of index vertices used for rendering
 * glyphs using cached texture.
 */
static void
InitIndexArray()
{
    int ii, vi;
    memset(vertexQuads, 0, sizeof(vertexQuads));
    for (ii = 0, vi = 0; ii < MAX_STATIC_QUADS_NUM*6; ii += 6, vi += 4) {
        vertexIndices[ii + 0] = vi + 0;
        vertexIndices[ii + 1] = vi + 1;
        vertexIndices[ii + 2] = vi + 2;
        vertexIndices[ii + 3] = vi + 0;
        vertexIndices[ii + 4] = vi + 2;
        vertexIndices[ii + 5] = vi + 3;
    }
}

/**
 * Renders each glyph directly from the glyph texture cache.
 */
static HRESULT
D3DDrawGlyphList_UseCache(JNIEnv *env, Win32SDOps *wsdo,
                          D3DContext *d3dc,
                          ImageRef *glyphs, jint totalGlyphs)
{
    int glyphCounter;
    HRESULT res = DDERR_GENERIC;
    DXSurface *glyphCacheTexture;
    J2dTraceLn(J2D_TRACE_INFO, "D3DDrawGlyphList_UseCache");
    int color = d3dc->colorPixel;
    int quadCounter = 0;

    DDrawSurface *ddTargetSurface = d3dc->GetTargetSurface();
    if (ddTargetSurface == NULL) {
        return DDERR_GENERIC;
    }
    ddTargetSurface->GetExclusiveAccess();
    d3dc->GetExclusiveAccess();

    glyphCacheTexture = d3dc->GetGlyphCacheTexture();
    IDirect3DDevice7 *d3dDevice = d3dc->Get3DDevice();
    if (d3dDevice == NULL ||
        FAILED(res = d3dc->BeginScene(STATE_MASKOP)))
    {
        d3dc->ReleaseExclusiveAccess();
        ddTargetSurface->ReleaseExclusiveAccess();
        return res;
    }

    if (FAILED(res = d3dc->SetTexture(glyphCacheTexture))) {
        d3dc->EndScene(res);
        d3dc->ReleaseExclusiveAccess();
        ddTargetSurface->ReleaseExclusiveAccess();
        return res;
    }

    if (!indicesInited) {
        InitIndexArray();
        indicesInited = 1;
    }

    for (glyphCounter = 0; (glyphCounter < totalGlyphs) && SUCCEEDED(res);
         glyphCounter++)
    {
        // render glyph cached in texture object
        const jubyte *pixels = (const jubyte *)glyphs[glyphCounter].pixels;
        GlyphInfo *ginfo = (GlyphInfo *)glyphs[glyphCounter].glyphInfo;
        CacheCellInfo *cell;
        float x1, y1, x2, y2;
        float tx1, ty1, tx2, ty2;
        J2DLV_QUAD *quad;

        // it the glyph is an empty space, skip it
        if (!pixels) {
            continue;
        }

        if (ginfo->cellInfo == NULL ||
            // REMIND: this is a temp fix to allow a glyph be cached
            // in caches for different devices.
            // REMIND: check if this is even a problem: we're using
            // managed textures, they may be automatically accelerated
            // on a different device.
            // If the glyph is cached on a different device, cache
            // it on this context's device.
            // This may result in thrashing if the same glyphs
            // get rendered on different devices.
            // Note: this is not thread-safe: we may change the coordinates
            // while another thread is using this cell.
            // A proper fix would allow a glyph to be cached in multiple
            // caches at the same time.
            d3dc->GetGlyphCache() != ginfo->cellInfo->cacheInfo)
        {
            // attempt to add glyph to accelerated glyph cache
            if (FAILED(d3dc->GlyphCacheAdd(env, ginfo)) ||
                ginfo->cellInfo == NULL)
            {
                continue;
            }
        }

        cell = ginfo->cellInfo;
        cell->timesRendered++;

        x1 = (float)glyphs[glyphCounter].x;
        y1 = (float)glyphs[glyphCounter].y;
        x2 = x1 + (float)glyphs[glyphCounter].width;
        y2 = y1 + (float)glyphs[glyphCounter].height;
        tx1 = cell->tx1;
        ty1 = cell->ty1;
        tx2 = cell->tx2;
        ty2 = cell->ty2;
        quad = &vertexQuads[quadCounter++];

        D3DU_INIT_VERTEX_QUAD(*quad, x1, y1, x2, y2, color     ,
                              tx1, ty1, tx2, ty2);

        if (quadCounter == MAX_STATIC_QUADS_NUM &&
            SUCCEEDED(res = ddTargetSurface->IsLost()))
        {
            res = d3dDevice->DrawIndexedPrimitive(D3DPT_TRIANGLELIST,
                                                  D3DFVF_J2DLVERTEX,
                                                  vertexQuads,
                                                  4*quadCounter,
                                                  (LPWORD)vertexIndices,
                                                  6*quadCounter, 0);
            quadCounter = 0;
        }
    }

    if (quadCounter > 0 && SUCCEEDED(res)) {
        res = d3dDevice->DrawIndexedPrimitive(D3DPT_TRIANGLELIST,
                                              D3DFVF_J2DLVERTEX,
                                              vertexQuads,
                                              4*quadCounter,
                                              (LPWORD)vertexIndices,
                                              6*quadCounter, 0);
    }

    d3dc->EndScene(res);

    d3dc->ReleaseExclusiveAccess();
    ddTargetSurface->ReleaseExclusiveAccess();

    return res;
}

static HRESULT
D3DDrawGlyphList_NoCache(JNIEnv *env, Win32SDOps *wsdo,
                         D3DContext *d3dc,
                         ImageRef *glyphs, jint totalGlyphs)
{
    int glyphCounter;
    float tx1, ty1, tx2, ty2;
    jint tw, th;
    DXSurface *maskTexture;
    static J2DLVERTEX quadVerts[4] = {
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f }
    };

    J2dTraceLn(J2D_TRACE_INFO, "D3DDrawGlyphList_NoCache");

    DDrawSurface *ddTargetSurface = d3dc->GetTargetSurface();
    if (ddTargetSurface == NULL) {
        return DDERR_GENERIC;
    }
    ddTargetSurface->GetExclusiveAccess();
    d3dc->GetExclusiveAccess();

    HRESULT res = DDERR_GENERIC;

    IDirect3DDevice7 *d3dDevice = d3dc->Get3DDevice();
    if (d3dDevice == NULL) {
        d3dc->ReleaseExclusiveAccess();
        ddTargetSurface->ReleaseExclusiveAccess();
        return res;
    }

    maskTexture = d3dc->GetMaskTexture();
    if (maskTexture == NULL ||
        FAILED(res = d3dc->BeginScene(STATE_MASKOP)))
    {
        d3dc->ReleaseExclusiveAccess();
        ddTargetSurface->ReleaseExclusiveAccess();
        return DDERR_GENERIC;
    }

    if (FAILED(res = d3dc->SetTexture(maskTexture))) {
        d3dc->EndScene(res);
        d3dc->ReleaseExclusiveAccess();
        ddTargetSurface->ReleaseExclusiveAccess();
        return res;
    }

    tx1 = 0.0f;
    ty1 = 0.0f;
    tw = D3DSD_MASK_TILE_SIZE;
    th = D3DSD_MASK_TILE_SIZE;

    D3DU_INIT_VERTEX_QUAD_COLOR(quadVerts, d3dc->colorPixel);
    for (glyphCounter = 0; (glyphCounter < totalGlyphs) && SUCCEEDED(res);
         glyphCounter++)
    {
        // render system memory glyph image
        jint sx, sy, sw, sh;
        jint x, y, w, h, x0;
        const jubyte *pixels = (const jubyte *)glyphs[glyphCounter].pixels;

        if (!pixels) {
            continue;
        }

        x = glyphs[glyphCounter].x;
        y = glyphs[glyphCounter].y;
        w = glyphs[glyphCounter].width;
        h = glyphs[glyphCounter].height;
        x0 = x;

        for (sy = 0; sy < h; sy += th, y += th) {
            x = x0;
            sh = ((sy + th) > h) ? (h - sy) : th;

            for (sx = 0; sx < w; sx += tw, x += tw) {
                sw = ((sx + tw) > w) ? (w - sx) : tw;

                if (FAILED(d3dc->UploadImageToTexture(maskTexture,
                                                      (jubyte*)pixels,
                                                      0, 0, sx, sy,
                                                      sw, sh, w)))
                {
                    continue;
                }

                // update the lower right texture coordinates
                tx2 = ((float)sw) / tw;
                ty2 = ((float)sh) / th;

                D3DU_INIT_VERTEX_QUAD_XYUV(quadVerts,
                                           (float)x, (float)y,
                                           (float)(x+sw), (float)(y+sh),
                                           tx1, ty1, tx2, ty2);
                if (SUCCEEDED(res = ddTargetSurface->IsLost())) {
                    res = d3dDevice->DrawPrimitive(D3DPT_TRIANGLEFAN,
                                                   D3DFVF_J2DLVERTEX,
                                                   quadVerts, 4, 0);
                }
            }
        }
    }

    d3dc->EndScene(res);

    d3dc->ReleaseExclusiveAccess();
    ddTargetSurface->ReleaseExclusiveAccess();

    return res;
}


JNIEXPORT void JNICALL
D3DDrawGlyphList(JNIEnv *env, jobject d3dtr,
                 jlong pData, jlong pCtx,
                 ImageRef *glyphs, jint totalGlyphs,
                 jboolean useCache)
{
    Win32SDOps *wsdo = (Win32SDOps *)jlong_to_ptr(pData);
    D3DContext *d3dc = (D3DContext *)jlong_to_ptr(pCtx);

    HRESULT res;
    // Note: uncomment to control glyph caching via env. variable.
    // useCache = useCache && !getenv("J2D_D3D_NOGLYPHCACHING");

    if (d3dc == NULL) {
        return;
    }

    if (useCache && SUCCEEDED(res = d3dc->InitGlyphCache())) {
        D3D_EXEC_PRIM_LOOP(env, res, wsdo,
                      D3DDrawGlyphList_UseCache(env, wsdo, d3dc, glyphs,
                                                totalGlyphs));
        return;
    }

    D3D_EXEC_PRIM_LOOP(env, res, wsdo,
                  D3DDrawGlyphList_NoCache(env, wsdo, d3dc, glyphs,
                                           totalGlyphs));
}

}
