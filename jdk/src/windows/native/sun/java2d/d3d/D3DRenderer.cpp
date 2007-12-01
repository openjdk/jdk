/*
 * Copyright 2002-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "sun_java2d_d3d_D3DRenderer.h"
#include "sun_java2d_windows_DDRenderer.h"
#include "Win32SurfaceData.h"

#include <ddraw.h>

#include "D3DUtils.h"
#include "ddrawUtils.h"

#include "j2d_md.h"
#include "jlong.h"

/*
 * Class:     sun_java2d_d3d_D3DRenderer
 * Method:    doDrawLineD3D
 * Signature: (Lsun/java2d/SurfaceData;IIIII)Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_java2d_d3d_D3DRenderer_doDrawLineD3D
    (JNIEnv *env, jobject d3dr,
     jlong pData, jlong pCtx,
     jint x1, jint y1, jint x2, jint y2)
{
    Win32SDOps *wsdo = (Win32SDOps *)jlong_to_ptr(pData);
    D3DContext *d3dc = (D3DContext *)jlong_to_ptr(pCtx);
    static J2D_XY_C_VERTEX lineVerts[] = {
#ifdef USE_SINGLE_VERTEX_FORMAT
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
#else
        // x, y, z, color
        { 0, 0, 0, 0x0 },
        { 0, 0, 0, 0x0 },
#endif // USE_SINGLE_VERTEX_FORMAT
    };

    J2dTraceLn(J2D_TRACE_INFO, "D3DRenderer_doDrawLineD3D");
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  x1=%-4d y1=%-4d x2=%-4d y2=%-4d",
                x1, y1, x2, y2);
    HRESULT res = DDERR_GENERIC;
    if (d3dc != NULL) {
        DDrawSurface *ddTargetSurface = d3dc->GetTargetSurface();
        if (ddTargetSurface == NULL) {
            return FALSE;
        }
        ddTargetSurface->GetExclusiveAccess();
        d3dc->GetExclusiveAccess();

        IDirect3DDevice7 *d3dDevice = d3dc->Get3DDevice();
        if (d3dDevice == NULL) {
            d3dc->ReleaseExclusiveAccess();
            ddTargetSurface->ReleaseExclusiveAccess();
            return FALSE;
        }

        // +0.5f is needed to compensate for the -0.5f we
        // force when setting the transform
        lineVerts[0].x = (float)x1 + 0.5f;
        lineVerts[0].y = (float)y1 + 0.5f;
        lineVerts[0].color = d3dc->colorPixel;
        lineVerts[1].x = (float)x2 + 0.5f;
        lineVerts[1].y = (float)y2 + 0.5f;
        lineVerts[1].color = d3dc->colorPixel;

        D3DU_PRIM_LOOP_BEGIN(res, wsdo);
        if (SUCCEEDED(res = d3dc->BeginScene(STATE_RENDEROP))) {
            res = d3dDevice->DrawPrimitive(D3DPT_LINESTRIP, D3DFVF_J2D_XY_C,
                                           lineVerts, 2, 0);
            // REMIND: need to be using the results of device testing
            res = d3dDevice->DrawPrimitive(D3DPT_POINTLIST, D3DFVF_J2D_XY_C,
                                           &(lineVerts[1]), 1, 0);
            d3dc->EndScene(res);
        }
        D3DU_PRIM_LOOP_END(env, res, wsdo, "DrawPrimitive(D3DPT_LINESTRIP)");

        d3dc->ReleaseExclusiveAccess();
        ddTargetSurface->ReleaseExclusiveAccess();
    }
    return SUCCEEDED(res);
}

JNIEXPORT jboolean JNICALL
Java_sun_java2d_d3d_D3DRenderer_doDrawRectD3D
    (JNIEnv *env, jobject d3dr,
     jlong pData, jlong pCtx,
     jint x, jint y, jint w, jint h)
{
    Win32SDOps *wsdo = (Win32SDOps *)jlong_to_ptr(pData);
    D3DContext *d3dc = (D3DContext *)jlong_to_ptr(pCtx);
    float x1, y1, x2, y2;
    static J2D_XY_C_VERTEX lineVerts[] = {
#ifdef USE_SINGLE_VERTEX_FORMAT
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
#else
        // x, y, z, color
        { 0, 0, 0, 0x0 },
        { 0, 0, 0, 0x0 },
        { 0, 0, 0, 0x0 },
        { 0, 0, 0, 0x0 },
        { 0, 0, 0, 0x0 },
#endif // USE_SINGLE_VERTEX_FORMAT
    };

    J2dTraceLn(J2D_TRACE_INFO, "D3DRenderer_doDrawRectD3D");
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  x=%-4d y=%-4d w=%-4d h=%-4d",
                x, y, w, h);

    HRESULT res = DDERR_GENERIC;
    if (d3dc != NULL) {
        DDrawSurface *ddTargetSurface = d3dc->GetTargetSurface();
        if (ddTargetSurface == NULL) {
            return FALSE;
        }
        ddTargetSurface->GetExclusiveAccess();
        d3dc->GetExclusiveAccess();

        IDirect3DDevice7 *d3dDevice = d3dc->Get3DDevice();
        if (d3dDevice == NULL) {
            d3dc->ReleaseExclusiveAccess();
            ddTargetSurface->ReleaseExclusiveAccess();
            return FALSE;
        }

        // +0.5f is needed to compensate for the -0.5f we
        // force when setting the transform
        x1 = (float)x + 0.5f;
        y1 = (float)y + 0.5f;
        x2 = x1 + (float)w;
        y2 = y1 + (float)h;
        D3DU_INIT_VERTEX_PENT_XY(lineVerts, x1, y1, x2, y2);
        D3DU_INIT_VERTEX_PENT_COLOR(lineVerts, d3dc->colorPixel);

        D3DU_PRIM_LOOP_BEGIN(res, wsdo);
        if (SUCCEEDED(res = d3dc->BeginScene(STATE_RENDEROP))) {
            res = d3dDevice->DrawPrimitive(D3DPT_LINESTRIP, D3DFVF_J2D_XY_C,
                                           lineVerts, 5, 0);
            d3dc->EndScene(res);
        }
        D3DU_PRIM_LOOP_END(env, res, wsdo, "DrawPrimitive(D3DPT_LINESTRIP)");

        d3dc->ReleaseExclusiveAccess();
        ddTargetSurface->ReleaseExclusiveAccess();
    }
    return SUCCEEDED(res);
}

/*
 * Class:     sun_java2d_d3d_D3DRenderer
 * Method:    doFillRectD3D
 * Signature: (JIIII)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_java2d_d3d_D3DRenderer_doFillRectD3D
  (JNIEnv *env, jobject d3dr,
   jlong pData, jlong pCtx,
   jint x, jint y, jint w, jint h)
{
    Win32SDOps *wsdo = (Win32SDOps *)jlong_to_ptr(pData);
    D3DContext *d3dc = (D3DContext *)jlong_to_ptr(pCtx);
    HRESULT res = DDERR_GENERIC;
    float x1, y1, x2, y2;
    static J2D_XY_C_VERTEX quadVerts[] = {
#ifdef USE_SINGLE_VERTEX_FORMAT
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f }
#else
        // x, y, z, color
        { 0, 0, 0, 0x0 },
        { 0, 0, 0, 0x0 },
        { 0, 0, 0, 0x0 },
        { 0, 0, 0, 0x0 },
#endif // USE_SINGLE_VERTEX_FORMAT
    };

    J2dTraceLn(J2D_TRACE_INFO, "doFillRectD3D");
    J2dTraceLn4(J2D_TRACE_VERBOSE, "  x=%-4d y=%-4d w=%-4d h=%-4d", x, y, w, h);

    if (d3dc != NULL) {
        DDrawSurface *ddTargetSurface = d3dc->GetTargetSurface();
        if (ddTargetSurface == NULL) {
            return FALSE;
        }
        ddTargetSurface->GetExclusiveAccess();
        d3dc->GetExclusiveAccess();

        IDirect3DDevice7 *d3dDevice = d3dc->Get3DDevice();
        if (d3dDevice == NULL) {
            d3dc->ReleaseExclusiveAccess();
            ddTargetSurface->ReleaseExclusiveAccess();
            return FALSE;
        }

        x1 = (float)x;
        y1 = (float)y;
        x2 = x1 + (float)w;
        y2 = y1 + (float)h;
        D3DU_INIT_VERTEX_QUAD_COLOR(quadVerts, d3dc->colorPixel);
        D3DU_INIT_VERTEX_QUAD_XY(quadVerts, x1, y1, x2, y2);

        D3DU_PRIM_LOOP_BEGIN(res, wsdo);
        if (SUCCEEDED(res = d3dc->BeginScene(STATE_RENDEROP))) {
            res = d3dDevice->DrawPrimitive(D3DPT_TRIANGLEFAN, D3DFVF_J2D_XY_C,
                                           quadVerts, 4, 0);
            d3dc->EndScene(res);
        }
        D3DU_PRIM_LOOP_END(env, res, wsdo, "DrawPrimitive(D3DPT_TRIANGLEFAN)");

        d3dc->ReleaseExclusiveAccess();
        ddTargetSurface->ReleaseExclusiveAccess();
    }
    return SUCCEEDED(res);
}

/*
 * Class:     sun_java2d_d3d_D3DRenderer
 * Method:    doDrawPoly
 * Signature: (JII[I[IIZ)V
 */
JNIEXPORT void JNICALL Java_sun_java2d_d3d_D3DRenderer_doDrawPoly
  (JNIEnv *env, jobject d3dr,
   jlong pData, jlong pCtx,
   jint transx, jint transy,
   jintArray xcoordsArray, jintArray ycoordsArray, jint npoints,
   jboolean needToClose)
{
    Win32SDOps *wsdo = (Win32SDOps *)jlong_to_ptr(pData);
    D3DContext *d3dc = (D3DContext *)jlong_to_ptr(pCtx);
    jint *xcoords, *ycoords;
    jint i;

    J2dTraceLn(J2D_TRACE_INFO, "D3DRenderer_doDrawPoly");
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  transx=%-4d transy=%-4d "\
                "npoints=%-4d needToClose=%-4d",
                transx, transy, npoints, needToClose);

    if (d3dc == NULL) {
        J2dTraceLn(J2D_TRACE_WARNING,
                   "D3DRenderer_doDrawPoly: null device context");
        return;
    }

    if (JNU_IsNull(env, xcoordsArray) || JNU_IsNull(env, ycoordsArray)) {
        JNU_ThrowNullPointerException(env, "coordinate array");
        return;
    }
    if (env->GetArrayLength(ycoordsArray) < npoints ||
        env->GetArrayLength(xcoordsArray) < npoints)
    {
        JNU_ThrowArrayIndexOutOfBoundsException(env, "coordinate array");
        return;
    }

    xcoords = (jint *)
        env->GetPrimitiveArrayCritical(xcoordsArray, NULL);
    if (xcoords == NULL) {
        return;
    }

    ycoords = (jint *)
        env->GetPrimitiveArrayCritical(ycoordsArray, NULL);
    if (ycoords == NULL) {
        env->ReleasePrimitiveArrayCritical(xcoordsArray, xcoords, JNI_ABORT);
        return;
    }

    DDrawSurface *ddTargetSurface = d3dc->GetTargetSurface();
    if (ddTargetSurface == NULL) {
        env->ReleasePrimitiveArrayCritical(ycoordsArray, ycoords, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(xcoordsArray, xcoords, JNI_ABORT);
        return;
    }

    ddTargetSurface->GetExclusiveAccess();
    d3dc->GetExclusiveAccess();
    IDirect3DDevice7 *d3dDevice = d3dc->Get3DDevice();
    if (d3dDevice == NULL) {
        d3dc->ReleaseExclusiveAccess();
        ddTargetSurface->ReleaseExclusiveAccess();
        env->ReleasePrimitiveArrayCritical(ycoordsArray, ycoords, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(xcoordsArray, xcoords, JNI_ABORT);
        return;
    }

    int totalPoints = npoints;
    if (needToClose) {
        if (xcoords[npoints - 1] != xcoords[0] ||
            ycoords[npoints - 1] != ycoords[0])
        {
            totalPoints++;
        } else {
            needToClose = FALSE;
        }
    }
    J2D_XY_C_VERTEX *lpVerts =
        (J2D_XY_C_VERTEX *)safe_Malloc(totalPoints*sizeof(J2D_XY_C_VERTEX));
    if (!lpVerts) {
        d3dc->ReleaseExclusiveAccess();
        ddTargetSurface->ReleaseExclusiveAccess();
        env->ReleasePrimitiveArrayCritical(ycoordsArray, ycoords, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(xcoordsArray, xcoords, JNI_ABORT);
        return;
    }

    ZeroMemory(lpVerts, totalPoints*sizeof(J2D_XY_C_VERTEX));
    for (i = 0; i < npoints; i++) {
        // +0.5f is needed to compensate for the -0.5f we
        // force when setting the transform
        lpVerts[i].x = (float)(xcoords[i] + transx) + 0.5f;
        lpVerts[i].y = (float)(ycoords[i] + transy) + 0.5f;
        lpVerts[i].color =  d3dc->colorPixel;
    }
    if (needToClose) {
        lpVerts[npoints].x = (float)(xcoords[0] + transx) + 0.5f;
        lpVerts[npoints].y = (float)(ycoords[0] + transy) + 0.5f;
        lpVerts[npoints].color = d3dc->colorPixel;
    }
    env->ReleasePrimitiveArrayCritical(ycoordsArray, ycoords, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(xcoordsArray, xcoords, JNI_ABORT);

    HRESULT res;
    D3DU_PRIM_LOOP_BEGIN(res, wsdo);
    if (SUCCEEDED(res = d3dc->BeginScene(STATE_RENDEROP))) {
        res = d3dDevice->DrawPrimitive(D3DPT_LINESTRIP, D3DFVF_J2D_XY_C,
                                       lpVerts, totalPoints, 0);
        // REMIND: temp hack, need to be using the results of device testing
        if (!needToClose) {
            res = d3dDevice->DrawPrimitive(D3DPT_POINTLIST, D3DFVF_J2D_XY_C,
                                           &(lpVerts[totalPoints-1]), 1, 0);
        }
        d3dc->EndScene(res);
    }
    D3DU_PRIM_LOOP_END(env, res, wsdo, "DrawPrimitive(D3DPT_LINESTRIP)");

    free(lpVerts);

    d3dc->ReleaseExclusiveAccess();
    ddTargetSurface->ReleaseExclusiveAccess();
}

JNIEXPORT void JNICALL
Java_sun_java2d_d3d_D3DRenderer_devFillSpans
    (JNIEnv *env, jobject d3dr,
     jlong pData, jlong pCtx,
     jobject si, jlong pIterator, jint transx, jint transy)
{
    Win32SDOps *wsdo = (Win32SDOps *)jlong_to_ptr(pData);
    D3DContext *d3dc = (D3DContext *)jlong_to_ptr(pCtx);
    SpanIteratorFuncs *pFuncs = (SpanIteratorFuncs *)jlong_to_ptr(pIterator);
    void *srData;
    float x1, y1, x2, y2;
    jint spanbox[4];
    J2dTraceLn(J2D_TRACE_INFO, "D3DRenderer_devFillSpans");
    J2dTraceLn2(J2D_TRACE_VERBOSE,
                "  transx=%-4d transy=%-4d", transx, transy);

    if (JNU_IsNull(env, si)) {
        JNU_ThrowNullPointerException(env, "span iterator");
        return;
    }
    if (pFuncs == NULL) {
        JNU_ThrowNullPointerException(env, "native iterator not supplied");
        return;
    }

    if (d3dc == NULL) {
        J2dTraceLn(J2D_TRACE_WARNING,
                   "D3DRenderer_devFillSpans: context is null");
        return;
    }

    DDrawSurface *ddTargetSurface = d3dc->GetTargetSurface();
    if (ddTargetSurface == NULL) {
        return;
    }

    ddTargetSurface->GetExclusiveAccess();
    d3dc->GetExclusiveAccess();

    IDirect3DDevice7 *d3dDevice = d3dc->Get3DDevice();
    if (d3dDevice == NULL) {
        d3dc->ReleaseExclusiveAccess();
        ddTargetSurface->ReleaseExclusiveAccess();
        return;
    }

    HRESULT res = D3D_OK;

    // buffer for the span vertexes (6 vertexes per span)
    static J2DXYC_HEXA spanVx[MAX_CACHED_SPAN_VX_NUM];
    J2DXYC_HEXA *pHexa = (J2DXYC_HEXA*)spanVx;
    jint numOfCachedSpans = 0;

    if (SUCCEEDED(res = d3dc->BeginScene(STATE_RENDEROP))) {
        srData = (*pFuncs->open)(env, si);

        // REMIND: this is wrong, if something has failed, we need to
        // do a EndScene/BeginScene()
        D3DU_PRIM_LOOP_BEGIN(res, wsdo);
        while ((*pFuncs->nextSpan)(srData, spanbox)) {
            x1 = (float)(spanbox[0] + transx);
            y1 = (float)(spanbox[1] + transy);
            x2 = (float)(spanbox[2] + transx);
            y2 = (float)(spanbox[3] + transy);

            D3DU_INIT_VERTEX_COLOR_6(*pHexa, d3dc->colorPixel);
            D3DU_INIT_VERTEX_XY_6(*pHexa, x1, y1, x2, y2);
            numOfCachedSpans++;
            pHexa = (J2DXYC_HEXA*)PtrAddBytes(pHexa, sizeof(J2DXYC_HEXA));
            if (numOfCachedSpans >= MAX_CACHED_SPAN_VX_NUM) {
                if (FAILED(res = ddTargetSurface->IsLost())) {
                    numOfCachedSpans = 0;
                    break;
                }

                res = d3dDevice->DrawPrimitive(D3DPT_TRIANGLELIST,
                                               D3DFVF_J2D_XY_C,
                                               (void*)spanVx,
                                               6*numOfCachedSpans, 0);
                numOfCachedSpans = 0;
                pHexa = (J2DXYC_HEXA*)spanVx;
                if (FAILED(res)) {
                    break;
                }
            }
        }
        if (numOfCachedSpans > 0) {
            res = d3dDevice->DrawPrimitive(D3DPT_TRIANGLELIST, D3DFVF_J2D_XY_C,
                                           (void*)spanVx,
                                           6*numOfCachedSpans, 0);
        }
        D3DU_PRIM_LOOP_END(env, res, wsdo, "DrawPrimitive(D3DPT_TRIANGLEFAN)");

        (*pFuncs->close)(env, srData);

        d3dc->EndScene(res);
    }

    d3dc->ReleaseExclusiveAccess();
    ddTargetSurface->ReleaseExclusiveAccess();
}
