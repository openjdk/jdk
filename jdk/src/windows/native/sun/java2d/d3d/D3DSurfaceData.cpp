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

#include "D3DSurfaceData.h"
#include "D3DContext.h"
#include "jlong.h"
#include "jni_util.h"
#include "Trace.h"
#include "ddrawUtils.h"
#include "Devices.h"

#include "Win32SurfaceData.h"
#include "sun_java2d_d3d_D3DBackBufferSurfaceData.h"

extern LockFunc Win32OSSD_Lock;
extern GetRasInfoFunc Win32OSSD_GetRasInfo;
extern UnlockFunc Win32OSSD_Unlock;
extern DisposeFunc Win32OSSD_Dispose;
extern GetDCFunc Win32OSSD_GetDC;
extern ReleaseDCFunc Win32OSSD_ReleaseDC;
extern InvalidateSDFunc Win32OSSD_InvalidateSD;
extern RestoreSurfaceFunc Win32OSSD_RestoreSurface;
extern DisposeFunc Win32BBSD_Dispose;

extern "C" {

RestoreSurfaceFunc D3DSD_RestoreSurface;

/*
 * D3D-surface specific restore function.
 * We need to make sure the D3DContext is notified if the
 * surface is lost (only if this surface is the current target,
 * otherwise it's possible that it'll get restored (along with its
 * depth buffer), and the context will still think that the clipping
 * that's set for this surface is valid.
 * Consider this scenario:
 * do {
 *     vi.validate(gc); // validated, vi's surface is restored, clipping is lost
 *     // render stuff using d3d, clipping is reset
 *     // -> surface loss event happens
 *     // do a DD blit of the VI to the screen
 *     // at this point the VI surface will be marked lost
 *     // and will be restored in validate() next time around,
 *     // losing the clipping w/o notifying the D3D context
 * } while (vi.surfaceLost());
 */
void D3DSD_RestoreSurface(JNIEnv *env, Win32SDOps *wsdo) {
    J2dTraceLn(J2D_TRACE_INFO, "D3DSD_RestoreSurface");
    D3DSDOps *d3dsdo = (D3DSDOps *)wsdo;
    // This is needed only for non-textures, since textures can't
    // lose their surfaces, as they're managed.
    if (!(d3dsdo->d3dType & D3D_TEXTURE_SURFACE) && wsdo->lpSurface != NULL)
    {
        if (wsdo->ddInstance != NULL && wsdo->ddInstance->ddObject != NULL) {
            D3DContext *d3dContext =
                wsdo->ddInstance->ddObject->GetD3dContext();
            if (d3dContext != NULL) {
                d3dContext->InvalidateIfTarget(env, wsdo->lpSurface);
            }
        }
    }
    Win32OSSD_RestoreSurface(env, wsdo);
}

/*
 * Class:     sun_java2d_d3d_D3DSurfaceData
 * Method:    initOps
 * Signature: (Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_d3d_D3DSurfaceData_initOps(JNIEnv *env,
                                           jobject wsd,
                                           jint depth,
                                           jint transparency)
{
    J2dTraceLn(J2D_TRACE_INFO, "D3DSurfaceData_initOps");
    Win32SDOps *wsdo = (Win32SDOps *)SurfaceData_InitOps(env, wsd,
                                                         sizeof(D3DSDOps));
    wsdo->sdOps.Lock = Win32OSSD_Lock;
    wsdo->sdOps.GetRasInfo = Win32OSSD_GetRasInfo;
    wsdo->sdOps.Unlock = Win32OSSD_Unlock;
    wsdo->sdOps.Dispose = Win32OSSD_Dispose;
    wsdo->RestoreSurface = D3DSD_RestoreSurface;
    wsdo->GetDC = Win32OSSD_GetDC;
    wsdo->ReleaseDC = Win32OSSD_ReleaseDC;
    wsdo->InvalidateSD = Win32OSSD_InvalidateSD;
    wsdo->invalid = JNI_FALSE;
    wsdo->lockType = WIN32SD_LOCK_UNLOCKED;
    wsdo->window = NULL;
    wsdo->backBufferCount = 0;
    wsdo->depth = depth;
    switch (depth) {
        case 8:
            wsdo->pixelStride = 1;
            break;
        case 15: //555
            wsdo->pixelStride = 2;
            wsdo->pixelMasks[0] = 0x1f << 10;
            wsdo->pixelMasks[1] = 0x1f << 5;
            wsdo->pixelMasks[2] = 0x1f;
            break;
        case 16: //565
            wsdo->pixelStride = 2;
            wsdo->pixelMasks[0] = 0x1f << 11;
            wsdo->pixelMasks[1] = 0x3f << 5;
            wsdo->pixelMasks[2] = 0x1f;
            break;
        case 24:
            wsdo->pixelStride = 3;
            break;
        case 32: //x888
            wsdo->pixelStride = 4;
            wsdo->pixelMasks[0] = 0xff0000;
            wsdo->pixelMasks[1] = 0x00ff00;
            wsdo->pixelMasks[2] = 0x0000ff;
            break;
    }
    wsdo->surfaceLock = new CriticalSection();
    wsdo->surfaceLost = FALSE;
    wsdo->transparency = transparency;
    wsdo->surfacePuntData.usingDDSystem = FALSE;
    wsdo->surfacePuntData.lpSurfaceSystem = NULL;
    wsdo->surfacePuntData.lpSurfaceVram = NULL;
    wsdo->surfacePuntData.numBltsSinceRead = 0;
    wsdo->surfacePuntData.pixelsReadSinceBlt = 0;
    wsdo->surfacePuntData.numBltsThreshold = 2;
    wsdo->gdiOpPending = FALSE;
}

jboolean init_D3DSDO(JNIEnv* env, Win32SDOps* wsdo, jint width, jint height,
                     jint d3dSurfaceType, jint screen)
{
    // default in case of an error
    wsdo->lpSurface = NULL;
    wsdo->ddInstance = NULL;

    {
        Devices::InstanceAccess devices;
        wsdo->device = devices->GetDeviceReference(screen, FALSE);
    }
    if (wsdo->device == NULL) {
        J2dTraceLn1(J2D_TRACE_WARNING,
                    "init_D3DSDO: Incorrect "\
                    "screen number (screen=%d)", screen);
        wsdo->invalid = TRUE;
        return JNI_FALSE;
    }
    wsdo->w = width;
    wsdo->h = height;
    wsdo->surfacePuntData.disablePunts = TRUE;
    return JNI_TRUE;
}

/*
 * Class:     sun_java2d_d3d_D3DSurfaceData
 * Method:    initOffScreenSurface
 * Signature: (JJJIIII)I
 */
JNIEXPORT jint JNICALL
Java_sun_java2d_d3d_D3DSurfaceData_initOffScreenSurface
    (JNIEnv *env, jobject sData,
     jlong pCtx,
     jlong pData, jlong parentPdata,
     jint width, jint height,
     jint d3dSurfaceType, jint screen)
{
    Win32SDOps *wsdo = (Win32SDOps *)jlong_to_ptr(pData);
    D3DContext *pd3dc = (D3DContext *)jlong_to_ptr(pCtx);

    J2dTraceLn(J2D_TRACE_INFO, "D3DSurfaceData_initOffScreenSurface");
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  width=%-4d height=%-4d type=%-3d scr=%-3d",
                width, height, d3dSurfaceType, screen);

    // REMIND: ideally this should be done in initOps
    if (d3dSurfaceType == D3D_ATTACHED_SURFACE) {
        wsdo->sdOps.Dispose = Win32BBSD_Dispose;
    }

    if (init_D3DSDO(env, wsdo, width, height,
                    d3dSurfaceType, screen) == JNI_FALSE)
    {
        SurfaceData_ThrowInvalidPipeException(env,
            "Can't create offscreen surface");
        return PF_INVALID;
    }

    HMONITOR hMon = (HMONITOR)wsdo->device->GetMonitor();
    DDrawObjectStruct *ddInstance = GetDDInstanceForDevice(hMon);
    if (!ddInstance || !ddInstance->valid || !pd3dc) {
        return PF_INVALID;
    }

    if (d3dSurfaceType == D3D_ATTACHED_SURFACE) {
        // REMIND: still using the old path. ideally the creation of attached
        // surface shoudld be done in the same way as other types of surfaces,
        // that is, in D3DContext::CreateSurface, but we really don't use
        // anything from D3DContext to get an attached surface, so this
        // was left here.

        Win32SDOps *wsdo_parent = (Win32SDOps *)jlong_to_ptr(parentPdata);
        // we're being explicit here: requesting backbuffer, and render target
        DDrawSurface* pNew = wsdo_parent->lpSurface == NULL ?
            NULL :
            wsdo_parent->lpSurface->
                GetDDAttachedSurface(DDSCAPS_BACKBUFFER|DDSCAPS_3DDEVICE);
        if (pNew == NULL ||
            FAILED(pd3dc->AttachDepthBuffer(pNew->GetDXSurface())))
        {
            J2dRlsTraceLn1(J2D_TRACE_ERROR,
                           "D3DSD_initSurface: GetAttachedSurface for parent"\
                           " wsdo_parent->lpSurface=0x%x failed",
                           wsdo_parent->lpSurface);
            if (pNew != NULL) {
                delete pNew;
            }
            SurfaceData_ThrowInvalidPipeException(env,
                "Can't create attached offscreen surface");
            return PF_INVALID;
        }

        wsdo->lpSurface = pNew;
        wsdo->ddInstance = ddInstance;
        J2dTraceLn2(J2D_TRACE_VERBOSE,
                    "D3DSD_initSurface: created attached surface: "\
                    "wsdo->lpSurface=0x%x for parent "\
                    "wsdo_parent->lpSurface=0x%x",
                    wsdo->lpSurface, wsdo_parent->lpSurface);
        // we don't care about pixel format for non-texture surfaces
        return PF_INVALID;
    }

    DXSurface *dxSurface = NULL;
    jint pf = PF_INVALID;
    HRESULT res;
    if (SUCCEEDED(res = pd3dc->CreateSurface(env, wsdo->w, wsdo->h,
                                             wsdo->depth, wsdo->transparency,
                                             d3dSurfaceType,
                                             &dxSurface, &pf)))
    {
        // REMIND: put all the error-handling stuff here from
        // DDCreateOffScreenSurface
        wsdo->lpSurface = new DDrawSurface(ddInstance->ddObject, dxSurface);
        wsdo->surfacePuntData.lpSurfaceVram = wsdo->lpSurface;
        wsdo->ddInstance = ddInstance;
        // the dimensions of the surface may be adjusted in case of
        // textures
        wsdo->w = dxSurface->GetWidth();
        wsdo->h = dxSurface->GetHeight();
        J2dTraceLn1(J2D_TRACE_VERBOSE,
                    "D3DSurfaceData_initSurface: created surface: "\
                    "wsdo->lpSurface=0x%x", wsdo->lpSurface);
    } else {
        DebugPrintDirectDrawError(res,
                                  "D3DSurfaceData_initSurface: "\
                                  "CreateSurface failed");
        // REMIND: should use some other way to signal that
        // surface creation was unsuccessful
        SurfaceData_ThrowInvalidPipeException(env,
                                              "Can't create offscreen surf");
    }
    return pf;
}

/*
 * Class:     sun_java2d_d3d_D3DBackBufferSurfaceData
 * Method:    restoreDepthBuffer
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_d3d_D3DBackBufferSurfaceData_restoreDepthBuffer(JNIEnv *env,
                                                                jobject sData)
{
    Win32SDOps *wsdo = Win32SurfaceData_GetOpsNoSetup(env, sData);
    J2dTraceLn1(J2D_TRACE_INFO,
                "D3DBBSD_restoreDepthBuffer: wsdo=0x%x", wsdo);

    if (wsdo != NULL) {
        if (!DDRestoreSurface(wsdo)) {
            // Failure - throw exception
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "D3DBBSD_restoreDepthBuffer: failed to "\
                          "restore depth buffer");

            SurfaceData_ThrowInvalidPipeException(env,
                                                  "RestoreDepthBuffer failure");
        }
    }
}

}
