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
#include "jlong.h"
#include <sun_java2d_d3d_D3DBlitLoops.h>
#include "ddrawUtils.h"
#include "GraphicsPrimitiveMgr.h"
#include "Region.h"

#include "D3DUtils.h"
#include "D3DContext.h"
#include "D3DSurfaceData.h"

extern CriticalSection windowMoveLock;

extern "C" {

JNIEXPORT void JNICALL
Java_sun_java2d_d3d_D3DBlitLoops_doTransform
    (JNIEnv *env, jclass d3dbl,
     jlong pSrcData, jlong pDstData,
     jlong pCtx,
     jint hint,
     jint sx1, jint sy1, jint sx2, jint sy2,
     jfloat dx1, jfloat dy1, jfloat dx2, jfloat dy2)
{
    static J2DLVERTEX quadVerts[4] = {
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0x0, 0.0f, 0.0f }
    };

    J2dTraceLn(J2D_TRACE_INFO, "D3DBlitLoops_doTransform");
    J2dTraceLn4(J2D_TRACE_VERBOSE, "  sx1=%-4d sy1=%-4d sx2=%-4d sy2=%-4d ",
                sx1, sy1, sx2, sy2);
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  dx1=%4f dy1=%4f dx2=%4f dy2=%4f", dx1, dy1, dx2, dy2);

    if (sx2 <= sx1 || sy2 <= sy1 || dx2 <= dx1 || dy2 <= dy1) {
        J2dTraceLn(J2D_TRACE_WARNING,
                   "D3DBlitLoops_doTransform: invalid dimensions");
        return;
    }

    D3DContext *d3dc = (D3DContext *)jlong_to_ptr(pCtx);
    if (d3dc == NULL) {
        J2dTraceLn(J2D_TRACE_WARNING,
                   "D3DBlitLoops_doTransform: null device context");
        return;
    }
    Win32SDOps *srcOps = (Win32SDOps *)jlong_to_ptr(pSrcData);
    Win32SDOps *dstOps = (Win32SDOps *)jlong_to_ptr(pDstData);

    if (!srcOps->ddInstance || !dstOps->ddInstance) {
        // Some situations can cause us to fail on primary
        // creation, resulting in null lpSurface and null ddInstance
        // for a Win32Surface object.. Just noop this call in that case.
        return;
    }

    DDrawSurface *ddTargetSurface = d3dc->GetTargetSurface();
    DDrawSurface *ddSrcSurface = srcOps->lpSurface;
    if (ddTargetSurface == NULL || ddSrcSurface == NULL) {
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

    float tw = (float)ddSrcSurface->GetDXSurface()->GetWidth();
    float th = (float)ddSrcSurface->GetDXSurface()->GetHeight();
    float tx1 = ((float)sx1) / tw;
    float ty1 = ((float)sy1) / th;
    float tx2 = ((float)sx2) / tw;
    float ty2 = ((float)sy2) / th;

    D3DU_INIT_VERTEX_QUAD(quadVerts, dx1, dy1, dx2, dy2,
                          d3dc->blitPolygonPixel,
                          tx1, ty1, tx2, ty2);

    if (hint == D3DSD_XFORM_BILINEAR) {
        d3dDevice->SetTextureStageState(0, D3DTSS_MAGFILTER, D3DTFG_LINEAR);
        d3dDevice->SetTextureStageState(0, D3DTSS_MINFILTER, D3DTFG_LINEAR);
    } else if (hint == D3DSD_XFORM_NEAREST_NEIGHBOR) {
        d3dDevice->SetTextureStageState(0, D3DTSS_MAGFILTER, D3DTFG_POINT);
        d3dDevice->SetTextureStageState(0, D3DTSS_MINFILTER, D3DTFG_POINT);
    }

    HRESULT res;
    D3DU_PRIM2_LOOP_BEGIN(res, srcOps, dstOps);
    if (SUCCEEDED(res = d3dc->BeginScene(STATE_BLITOP))) {
        DXSurface *dxSurface = ddSrcSurface->GetDXSurface();
        if (SUCCEEDED(res = d3dc->SetTexture(dxSurface)))
        {
            res = d3dDevice->DrawPrimitive(D3DPT_TRIANGLEFAN, D3DFVF_J2DLVERTEX,
                                           quadVerts, 4, 0);
        }
        d3dc->EndScene(res);
    }
    D3DU_PRIM2_LOOP_END(env, res, srcOps, dstOps,
                        "DrawPrimitive(D3DPT_TRIANGLEFAN)");

    d3dc->ReleaseExclusiveAccess();
    ddTargetSurface->ReleaseExclusiveAccess();
}

}
