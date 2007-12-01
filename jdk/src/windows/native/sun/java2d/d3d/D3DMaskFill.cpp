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

#include <stdlib.h>
#include <jni.h>
#include "ddrawUtils.h"
#include "GraphicsPrimitiveMgr.h"
#include "j2d_md.h"
#include "jlong.h"

#include "sun_java2d_d3d_D3DMaskFill.h"

#include "Win32SurfaceData.h"

#include "D3DContext.h"
#include "D3DUtils.h"


extern "C" {

inline static HRESULT doMaskFill
    (JNIEnv *env, jobject self,
     Win32SDOps *wsdo, D3DContext *d3dc,
     jint x, jint y, jint w, jint h,
     jbyteArray maskArray,
     jint maskoff, jint maskscan);


JNIEXPORT void JNICALL
Java_sun_java2d_d3d_D3DMaskFill_MaskFill
    (JNIEnv *env, jobject self,
     jlong pData, jlong pCtx,
     jint x, jint y, jint w, jint h,
     jbyteArray maskArray,
     jint maskoff, jint maskscan)
{
    Win32SDOps *wsdo = (Win32SDOps *)jlong_to_ptr(pData);
    D3DContext *d3dc = (D3DContext *)jlong_to_ptr(pCtx);

    J2dTraceLn(J2D_TRACE_INFO, "D3DMaskFill_MaskFill");
    J2dTraceLn4(J2D_TRACE_VERBOSE, "  x=%-4d y=%-4d w=%-4d h=%-4d",
                x, y, w, h);
    J2dTraceLn2(J2D_TRACE_VERBOSE, "  maskoff=%-4d maskscan=%-4d",
                maskoff, maskscan);

    if (d3dc == NULL || wsdo == NULL) {
        J2dTraceLn(J2D_TRACE_WARNING,
                   "D3DMaskFill_MaskFill: context is null");
        return;
    }

    HRESULT res;
    D3D_EXEC_PRIM_LOOP(env, res, wsdo,
                  doMaskFill(env, self, wsdo, d3dc,
                             x, y, w, h,
                             maskArray, maskoff, maskscan));
}

inline static HRESULT doMaskFill
    (JNIEnv *env, jobject self,
     Win32SDOps *wsdo, D3DContext *d3dc,
     jint x, jint y, jint w, jint h,
     jbyteArray maskArray,
     jint maskoff, jint maskscan)
{
    DXSurface *maskTexture;
    static J2DLVERTEX quadVerts[4] = {
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f }
    };

    DDrawSurface *ddTargetSurface = d3dc->GetTargetSurface();
    if (ddTargetSurface == NULL) {
        return DDERR_GENERIC;
    }

    ddTargetSurface->GetExclusiveAccess();
    d3dc->GetExclusiveAccess();

    IDirect3DDevice7 *d3dDevice = d3dc->Get3DDevice();

    HRESULT res = D3D_OK;
    if (maskArray) {
        jubyte *pMask =
            (jubyte*)env->GetPrimitiveArrayCritical(maskArray, 0);
        float tx1, ty1, tx2, ty2;
        jint tw, th, x0;
        jint sx1, sy1, sx2, sy2;
        jint sx, sy, sw, sh;

        if (pMask == NULL) {
            d3dc->ReleaseExclusiveAccess();
            ddTargetSurface->ReleaseExclusiveAccess();
            return DDERR_GENERIC;
        }

        maskTexture = d3dc->GetMaskTexture();
        if (maskTexture == NULL ||
            FAILED(res = d3dc->BeginScene(STATE_MASKOP)))
        {
            env->ReleasePrimitiveArrayCritical(maskArray, pMask, JNI_ABORT);
            d3dc->ReleaseExclusiveAccess();
            ddTargetSurface->ReleaseExclusiveAccess();
            return DDERR_GENERIC;
        }

        if (FAILED(res = d3dc->SetTexture(maskTexture))) {
            d3dc->EndScene(res);
            env->ReleasePrimitiveArrayCritical(maskArray, pMask, JNI_ABORT);
            d3dc->ReleaseExclusiveAccess();
            ddTargetSurface->ReleaseExclusiveAccess();
            return res;
        }

        x0 = x;
        tx1 = 0.0f;
        ty1 = 0.0f;
        tw = D3DSD_MASK_TILE_SIZE;
        th = D3DSD_MASK_TILE_SIZE;
        sx1 = maskoff % maskscan;
        sy1 = maskoff / maskscan;
        sx2 = sx1 + w;
        sy2 = sy1 + h;

        D3DU_INIT_VERTEX_QUAD_COLOR(quadVerts, d3dc->colorPixel);
        for (sy = sy1; (sy < sy2) && SUCCEEDED(res); sy += th, y += th) {
            x = x0;
            sh = ((sy + th) > sy2) ? (sy2 - sy) : th;

            for (sx = sx1; (sx < sx2) && SUCCEEDED(res); sx += tw, x += tw) {
                sw = ((sx + tw) > sx2) ? (sx2 - sx) : tw;

                if (FAILED(d3dc->UploadImageToTexture(maskTexture,
                                                      pMask,
                                                      0, 0, sx, sy, sw, sh,
                                                      maskscan)))
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
                    // render texture tile to the destination surface
                    res = d3dDevice->DrawPrimitive(D3DPT_TRIANGLEFAN,
                                                   D3DFVF_J2DLVERTEX,
                                                   quadVerts, 4, 0);
                }

            }
        }

        d3dc->EndScene(res);

        env->ReleasePrimitiveArrayCritical(maskArray, pMask, JNI_ABORT);
    } else {
        float x1 = (float)x;
        float y1 = (float)y;
        float x2 = x1 + (float)w;
        float y2 = y1 + (float)h;
        D3DU_INIT_VERTEX_QUAD_COLOR(quadVerts, d3dc->colorPixel);
        D3DU_INIT_VERTEX_QUAD_XY(quadVerts, x1, y1, x2, y2);
        if (SUCCEEDED(res = d3dc->BeginScene(STATE_RENDEROP))) {
            if (SUCCEEDED(res = ddTargetSurface->IsLost())) {
                res = d3dDevice->DrawPrimitive(D3DPT_TRIANGLEFAN,
                                               D3DFVF_J2DLVERTEX,
                                               quadVerts, 4, 0);
            }
            d3dc->EndScene(res);
        }
    }

    d3dc->ReleaseExclusiveAccess();
    ddTargetSurface->ReleaseExclusiveAccess();

    return res;
}

}
