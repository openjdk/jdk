/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "sun_java2d_windows_Win32OffScreenSurfaceData.h"

#include "Win32SurfaceData.h"

#include "Trace.h"
#include "Region.h"
#include "awt_Component.h"
#include "debug_trace.h"
#include "ddrawUtils.h"
#include "awt_Win32GraphicsDevice.h"
#include "D3DContext.h"

#include "jni_util.h"

/**
 * This source file contains support code for loops using the
 * SurfaceData interface to talk to a Win32 drawable from native
 * code.
 */

JNIEXPORT void JNICALL
Win32OSSD_InitDC(JNIEnv *env, Win32SDOps *wsdo, HDC hdc,
                 jint type, jint *patrop,
                 jobject clip, jobject comp, jint color);
jfieldID ddSurfacePuntedID;
jmethodID markSurfaceLostMID;
static HBRUSH   nullbrush;
static HPEN     nullpen;

extern BOOL ddVramForced;

LockFunc Win32OSSD_Lock;
GetRasInfoFunc Win32OSSD_GetRasInfo;
UnlockFunc Win32OSSD_Unlock;
DisposeFunc Win32OSSD_Dispose;
GetDCFunc Win32OSSD_GetDC;
ReleaseDCFunc Win32OSSD_ReleaseDC;
InvalidateSDFunc Win32OSSD_InvalidateSD;
RestoreSurfaceFunc Win32OSSD_RestoreSurface;

extern "C" {

/*
 * Class:     sun_java2d_windows_Win32OffScreenSurfaceData
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_Win32OffScreenSurfaceData_initIDs(JNIEnv *env,
                                                          jclass wsd)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32OffScreenSurfaceData_initIDs");
    ddSurfacePuntedID = env->GetFieldID(wsd, "ddSurfacePunted", "Z");
    markSurfaceLostMID = env->GetMethodID(wsd, "markSurfaceLost", "()V");
    nullbrush = (HBRUSH) ::GetStockObject(NULL_BRUSH);
    nullpen = (HPEN) ::GetStockObject(NULL_PEN);
}

void Win32OSSD_DisableDD(JNIEnv *env, Win32SDOps *wsdo)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32OSSD_DisableDD");

    wsdo->RestoreSurface(env, wsdo);
    jobject sdObject = env->NewLocalRef(wsdo->sdOps.sdObject);
    if (sdObject != NULL) {
        J2dRlsTraceLn1(J2D_TRACE_ERROR,
                       "Win32OSSD_DisableDD: disabling DirectDraw"\
                       " for surface 0x%x", wsdo);
        JNU_CallMethodByName(env, NULL, sdObject, "disableDD", "()V");
        env->DeleteLocalRef(sdObject);
    }
}

void disposeOSSD_WSDO(JNIEnv* env, Win32SDOps* wsdo)
{
    J2dTraceLn(J2D_TRACE_INFO, "disposeOSSD_WSDO");
    if (wsdo->device != NULL) {
        wsdo->device->Release();
        wsdo->device = NULL;
    }
    delete wsdo->surfaceLock;
}

jboolean initOSSD_WSDO(JNIEnv* env, Win32SDOps* wsdo, jint width, jint height,
                       jint screen, jint transparency)
{
    J2dTraceLn1(J2D_TRACE_INFO, "initOSSD_WSDO screen=%d", screen);

    {
        Devices::InstanceAccess devices;
        wsdo->device = devices->GetDeviceReference(screen, FALSE);
    }
    if (wsdo->device == NULL) {
        J2dTraceLn1(J2D_TRACE_WARNING,
                    "initOSSD_WSDO: Incorrect "\
                    "screen number (screen=%d)", screen);
        wsdo->invalid = TRUE;
        return JNI_FALSE;
    }

    wsdo->transparency = transparency;
    wsdo->w = width;
    wsdo->h = height;
    wsdo->surfacePuntData.disablePunts = TRUE;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_sun_java2d_windows_Win32OffScreenSurfaceData_initSurface
    (JNIEnv *env,
     jobject sData,
     jint depth,
     jint width, jint height,
     jint screen,
     jboolean isVolatile,
     jint transparency)
{
    Win32SDOps *wsdo = (Win32SDOps *)SurfaceData_GetOps(env, sData);

    J2dTraceLn(J2D_TRACE_INFO, "Win32OSSD_initSurface");
    jboolean status =
        initOSSD_WSDO(env, wsdo, width, height, screen, transparency);

    if (status == JNI_FALSE || !DDCreateSurface(wsdo)) {
        J2dRlsTraceLn1(J2D_TRACE_ERROR,
                       "Win32OffScreenSurfaceData_initSurface: Error creating "\
                       "offscreen surface (transparency=%d), throwing IPE",
                       transparency);
        SurfaceData_ThrowInvalidPipeException(env,
                                              "Can't create offscreen surf");
        return;
    } else {
        wsdo->surfacePuntData.lpSurfaceVram = wsdo->lpSurface;
    }
    // 8 is somewhat arbitrary; we want the threshhold to represent a
    // significant portion of the surface area in order to avoid
    // punting for occasional, small reads
    wsdo->surfacePuntData.pixelsReadThreshold = width * height / 8;
    /**
     * Only enable our punt-to-sysmem-surface scheme for surfaces that are:
     *   - non-transparent (we really only intended this workaround for
     *     back buffers, which are usually opaque)
     *   - volatile (non-volatile images should not even get into the punt
     *     situation since they should not be a rendering destination, but
     *     we check this just to make sure)
     * And only do so if the user did not specify that punting be disabled
     */
    wsdo->surfacePuntData.disablePunts = (transparency != TR_OPAQUE) ||
                                         !isVolatile                 ||
                                         ddVramForced;
}

/*
 * Class:     sun_java2d_windows_Win32OffScreenSurfaceData
 * Method:    restoreSurface
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_Win32OffScreenSurfaceData_restoreSurface(JNIEnv *env,
                                                                 jobject sData)
{
    J2dTraceLn(J2D_TRACE_INFO,
               "Win32OSSD_restoreSurface: restoring offscreen");
    Win32SDOps *wsdo = (Win32SDOps *)SurfaceData_GetOps(env, sData);

    // Might have gotten here by some default action.  Make sure that the
    // surface is marked as lost before bothering to try to restore it.
    if (!wsdo->surfaceLost) {
        return;
    }

    // Attempt to restore and lock the surface (to make sure the restore worked)
    if (DDRestoreSurface(wsdo) && DDLock(env, wsdo, NULL, NULL)) {
        DDUnlock(env, wsdo);
        wsdo->surfaceLost = FALSE;
    } else {
        // Failure - throw exception
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "Win32OSSD_restoreSurface: problems"\
                      " restoring, throwing IPE");
        SurfaceData_ThrowInvalidPipeException(env, "RestoreSurface failure");
    }
}


/*
 * Class:     sun_java2d_windows_Win32OffScreenSurfaceData
 * Method:    initOps
 * Signature: (Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_Win32OffScreenSurfaceData_initOps(JNIEnv *env,
                                                          jobject wsd,
                                                          jint depth,
                                                          jint transparency)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32OffScreenSurfaceData_initOps");
    Win32SDOps *wsdo =
        (Win32SDOps *)SurfaceData_InitOps(env, wsd, sizeof(Win32SDOps));
    wsdo->sdOps.Lock = Win32OSSD_Lock;
    wsdo->sdOps.GetRasInfo = Win32OSSD_GetRasInfo;
    wsdo->sdOps.Unlock = Win32OSSD_Unlock;
    wsdo->sdOps.Dispose = Win32OSSD_Dispose;
    wsdo->RestoreSurface = Win32OSSD_RestoreSurface;
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
        case 32: //888
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

JNIEXPORT Win32SDOps * JNICALL
Win32OffScreenSurfaceData_GetOps(JNIEnv *env, jobject sData)
{
    J2dTraceLn(J2D_TRACE_VERBOSE, "Win32OffScreenSurfaceData_GetOps");
    SurfaceDataOps *ops = SurfaceData_GetOps(env, sData);
    if (ops == NULL) {
        JNU_ThrowNullPointerException(env, "SurfaceData native ops");
    } else if (ops->Lock != Win32OSSD_Lock) {
        SurfaceData_ThrowInvalidPipeException(env, "not a Win32 SurfaceData");
        ops = NULL;
    }
    return (Win32SDOps *) ops;
}

} /* extern "C" */

void Win32OSSD_RestoreSurface(JNIEnv *env, Win32SDOps *wsdo)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32OSSD_RestoreSurface");
    wsdo->surfaceLost = TRUE;
    jobject sdObject = env->NewLocalRef(wsdo->sdOps.sdObject);
    if (sdObject != NULL) {
        // markSurfaceLost will end up throwing an InvalidPipeException
        // if this surface belongs to a managed image.
        env->CallVoidMethod(sdObject, markSurfaceLostMID);
        env->DeleteLocalRef(sdObject);
    }
}

void Win32OSSD_LockByDD(JNIEnv *env, Win32SDOps *wsdo, jint lockflags,
                        SurfaceDataRasInfo *pRasInfo)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32OSSD_LockByDD");

    if ((lockflags & SD_LOCK_READ) &&
        !wsdo->surfacePuntData.disablePunts)
    {
        wsdo->surfacePuntData.numBltsSinceRead = 0;
        if (!wsdo->surfacePuntData.usingDDSystem) {
            int w = pRasInfo->bounds.x2 - pRasInfo->bounds.x1;
            int h = pRasInfo->bounds.y2 - pRasInfo->bounds.y1;
            wsdo->surfacePuntData.pixelsReadSinceBlt += w * h;
            // Note that basing this decision on the bounds is somewhat
            // incorrect because locks of type FASTEST will simply send
            // in bounds that equal the area of the entire surface.
            // To do this correctly, we would need to return
            // SLOWLOCK and recalculate the punt data in GetRasInfo()
            if (wsdo->surfacePuntData.pixelsReadSinceBlt >
                wsdo->surfacePuntData.pixelsReadThreshold)
            {
                // Create the system surface if it doesn't exist
                if (!wsdo->surfacePuntData.lpSurfaceSystem) {
                    wsdo->surfacePuntData.lpSurfaceSystem =
                        wsdo->ddInstance->ddObject->CreateDDOffScreenSurface(
                        wsdo->w, wsdo->h, wsdo->depth,
                        wsdo->transparency, DDSCAPS_SYSTEMMEMORY);
                    if (wsdo->surfacePuntData.lpSurfaceSystem) {
                        // 4941350: Double-check that the surface we created
                        // matches the depth expected.
                        int sysmemDepth =
                            wsdo->surfacePuntData.lpSurfaceSystem->GetSurfaceDepth();
                        if (!DDSurfaceDepthsCompatible(wsdo->depth, sysmemDepth)) {
                            // There is clearly a problem here; release
                            // the punting surface
                            J2dTraceLn2(J2D_TRACE_WARNING,
                                        "Win32OSSD_LockByDD: Punting error: "\
                                        "wsdo->depth=%d memory surface depth=%d",
                                        wsdo->depth, sysmemDepth);
                            DDReleaseSurfaceMemory(wsdo->surfacePuntData.
                                lpSurfaceSystem);
                            wsdo->surfacePuntData.lpSurfaceSystem = NULL;
                        } else {
                            DDCOLORKEY ddck;
                            HRESULT ddResult =
                                wsdo->surfacePuntData.lpSurfaceVram->GetColorKey(
                                DDCKEY_SRCBLT, &ddck);
                            if (ddResult == DD_OK) {
                                // Vram surface has colorkey - use same colorkey on sys
                                ddResult =
                                    wsdo->surfacePuntData.lpSurfaceSystem->SetColorKey(
                                    DDCKEY_SRCBLT, &ddck);
                            }
                        }
                    }
                }
                // Assuming no errors in system creation, copy contents
                if (wsdo->surfacePuntData.lpSurfaceSystem) {
                    if (wsdo->surfacePuntData.lpSurfaceSystem->Blt(NULL,
                            wsdo->surfacePuntData.lpSurfaceVram, NULL,
                            DDBLT_WAIT, NULL) == DD_OK)
                    {
                        J2dTraceLn2(J2D_TRACE_INFO,
                                    "Win32OSSD_LockByDD: punting VRAM to sys: "\
                                    "0x%x -> 0x%x",
                                    wsdo->surfacePuntData.lpSurfaceVram,
                                    wsdo->surfacePuntData.lpSurfaceSystem);
                        wsdo->lpSurface = wsdo->surfacePuntData.lpSurfaceSystem;
                        wsdo->surfacePuntData.usingDDSystem = TRUE;
                        // Notify the Java level that this surface has
                        // been punted to avoid performance penalties from
                        // copying from VRAM cached versions of other images
                        // when we should use system memory versions instead.
                        jobject sdObject =
                            env->NewLocalRef(wsdo->sdOps.sdObject);
                        if (sdObject) {
                            // Only bother with this optimization if the
                            // reference is still valid
                            env->SetBooleanField(sdObject, ddSurfacePuntedID,
                                                 JNI_TRUE);
                            env->DeleteLocalRef(sdObject);
                        }
                    }
                }
            }
        }
    }

    if (!DDLock(env, wsdo, NULL, pRasInfo))
        return;

    wsdo->lockType = WIN32SD_LOCK_BY_DDRAW;
}


jint Win32OSSD_Lock(JNIEnv *env,
                    SurfaceDataOps *ops,
                    SurfaceDataRasInfo *pRasInfo,
                    jint lockflags)
{
    Win32SDOps *wsdo = (Win32SDOps *) ops;
    J2dTraceLn1(J2D_TRACE_INFO, "Win32OSSD_Lock: lockflags=0x%x",
                lockflags);
    wsdo->surfaceLock->Enter();
    if (wsdo->invalid) {
        wsdo->surfaceLock->Leave();
        SurfaceData_ThrowInvalidPipeException(env, "invalid sd");
        return SD_FAILURE;
    }

    if (wsdo->lockType != WIN32SD_LOCK_UNLOCKED) {
        wsdo->surfaceLock->Leave();
        JNU_ThrowInternalError(env, "Win32OSSD_Lock cannot nest locks");
        return SD_FAILURE;
    }

    if (lockflags & SD_LOCK_RD_WR) {
        if (pRasInfo->bounds.x1 < 0) pRasInfo->bounds.x1 = 0;
        if (pRasInfo->bounds.y1 < 0) pRasInfo->bounds.y1 = 0;
        if (pRasInfo->bounds.x2 > wsdo->w) pRasInfo->bounds.x2 = wsdo->w;
        if (pRasInfo->bounds.y2 > wsdo->h) pRasInfo->bounds.y2 = wsdo->h;
        if (DDUseDDraw(wsdo)) {
            Win32OSSD_LockByDD(env, wsdo, lockflags, pRasInfo);
        }
        if (wsdo->lockType == WIN32SD_LOCK_UNLOCKED) {
            wsdo->lockFlags = lockflags;
            wsdo->surfaceLock->Leave();
            return SD_FAILURE;
        }
    } else {
        // They didn't ask for a lock, so they don't get one
        wsdo->lockType = WIN32SD_LOCK_BY_NULL;
    }
    wsdo->lockFlags = lockflags;
    J2dTraceLn2(J2D_TRACE_VERBOSE, "Win32OSSD_Lock: flags=0x%x type=%d",
                wsdo->lockFlags, wsdo->lockType);
    return 0;
}

void Win32OSSD_GetRasInfo(JNIEnv *env,
                          SurfaceDataOps *ops,
                          SurfaceDataRasInfo *pRasInfo)
{
    Win32SDOps *wsdo = (Win32SDOps *) ops;
    jint lockflags = wsdo->lockFlags;

    J2dTraceLn(J2D_TRACE_INFO, "Win32OSSD_GetRasInfo");

    if (wsdo->lockType == WIN32SD_LOCK_UNLOCKED) {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "Win32OSSD_GetRasInfo: lockType=UNLOCKED");
        memset(pRasInfo, 0, sizeof(*pRasInfo));
        return;
    }

    if (wsdo->lockType != WIN32SD_LOCK_BY_DDRAW) {
        /* They didn't lock for anything - we won't give them anything */
        pRasInfo->rasBase = NULL;
        pRasInfo->pixelStride = 0;
        pRasInfo->pixelBitOffset = 0;
        pRasInfo->scanStride = 0;
    }
    if (wsdo->lockFlags & SD_LOCK_LUT) {
        pRasInfo->lutBase =
            (long *) wsdo->device->GetSystemPaletteEntries();
        pRasInfo->lutSize = 256;
    } else {
        pRasInfo->lutBase = NULL;
        pRasInfo->lutSize = 0;
    }
    if (wsdo->lockFlags & SD_LOCK_INVCOLOR) {
        pRasInfo->invColorTable = wsdo->device->GetSystemInverseLUT();
        ColorData *cData = wsdo->device->GetColorData();
        pRasInfo->redErrTable = cData->img_oda_red;
        pRasInfo->grnErrTable = cData->img_oda_green;
        pRasInfo->bluErrTable = cData->img_oda_blue;
    } else {
        pRasInfo->invColorTable = NULL;
        pRasInfo->redErrTable = NULL;
        pRasInfo->grnErrTable = NULL;
        pRasInfo->bluErrTable = NULL;
    }
    if (wsdo->lockFlags & SD_LOCK_INVGRAY) {
        pRasInfo->invGrayTable =
            wsdo->device->GetColorData()->pGrayInverseLutData;
    } else {
        pRasInfo->invGrayTable = NULL;
    }
}

void Win32OSSD_Unlock(JNIEnv *env,
                      SurfaceDataOps *ops,
                      SurfaceDataRasInfo *pRasInfo)
{
    Win32SDOps *wsdo = (Win32SDOps *) ops;

    J2dTraceLn(J2D_TRACE_INFO, "Win32OSSD_Unlock");

    if (wsdo->lockType == WIN32SD_LOCK_UNLOCKED) {
        JNU_ThrowInternalError(env, "Unmatched unlock on Win32OS SurfaceData");
        return;
    }

    if (wsdo->lockType == WIN32SD_LOCK_BY_DDRAW) {
        DDUnlock(env, wsdo);
    }
    wsdo->lockType = WIN32SD_LOCK_UNLOCKED;
    wsdo->surfaceLock->Leave();
}

static void
GetClipFromRegion(JNIEnv *env, jobject clip, RECT &r)
{
    SurfaceDataBounds bounds;
    Region_GetBounds(env, clip, &bounds);
    r.left = bounds.x1;
    r.top = bounds.y1;
    r.right = bounds.x2;
    r.bottom = bounds.y2;
}

/*
 * REMIND: This mechanism is just a prototype of a way to manage a
 * small cache of DC objects.  It is incomplete in the following ways:
 *
 * - It is not thread-safe!  It needs appropriate locking and release calls
 *   (perhaps the AutoDC mechanisms from Kestrel)
 * - It does hardly any error checking (What if GetDCEx returns NULL?)
 * - It cannot handle printer DCs, their resolution, or Embedded DCs
 * - It always selects a clip region, even if the clip is the window bounds
 * - There is very little error checking (null DC returned from GetDCEx, etc)
 * - It should probably "live" in the native SurfaceData object to allow
 *   alternate implementations for printing and embedding
 * - It doesn't handle XOR
 * - It caches the client bounds to determine if clipping is really needed
 *   (no way to invalidate the cached bounds and there is probably a better
 *    way to manage clip validation in any case)
 */

extern COLORREF CheckGrayColor(Win32SDOps *wsdo, int c);
HDC Win32OSSD_GetDC(JNIEnv *env, Win32SDOps *wsdo,
                    jint type, jint *patrop,
                    jobject clip, jobject comp, jint color)
{
    // REMIND: Should lock around all accesses to "last<mumble>"
    J2dTraceLn1(J2D_TRACE_INFO, "Win32OSSD_GetDC: color=0x%x", color);

    if (wsdo->invalid) {
        SurfaceData_ThrowInvalidPipeException(env, "invalid sd");
        return (HDC) NULL;
    }

    HDC hdc;
    HRESULT res = wsdo->lpSurface->GetDC(&hdc);
    if (res != DD_OK) {
        if (res == DDERR_CANTCREATEDC) {
            // this may be a manifestations of an unrecoverable error caused by
            // address space exaustion when heap size is too large
            Win32OSSD_DisableDD(env, wsdo);
        }
        // Note: DDrawSurface::GetDC() releases its surfaceLock
        // when it returns an error here, so do not call ReleaseDC()
        // to force the release of surfaceLock
        SurfaceData_ThrowInvalidPipeException(env, "invalid sd");
        return (HDC) NULL;
    }

    // Initialize the DC
    Win32OSSD_InitDC(env, wsdo, hdc, type, patrop, clip, comp, color);
    return hdc;
}

JNIEXPORT void JNICALL
Win32OSSD_InitDC(JNIEnv *env, Win32SDOps *wsdo, HDC hdc,
                 jint type, jint *patrop,
                 jobject clip, jobject comp, jint color)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32OSSD_InitDC");
    // Initialize DC.  Assume nothing about the DC since ddraw DC's are
    // created from scratch every time

    // Since we can't get here in XOR mode (ISCOPY only), we will ignore the
    // comp and force the patrop to PATCOPY if necessary.
    if (patrop != NULL) {
        *patrop = PATCOPY;
    }

    if (clip == NULL) {
        ::SelectClipRgn(hdc, (HRGN) NULL);
    } else {
        RECT r;
        GetClipFromRegion(env, clip, r);
        // Only bother setting clip if it's smaller than our window
        if ((r.left > 0) || (r.top > 0) ||
            (r.right < wsdo->w) || (r.bottom < wsdo->h)) {
            J2dTraceLn4(J2D_TRACE_VERBOSE,
                        "Win32OSSD_InitDC: clipRect "\
                        "l=%-4d t=%-4d r=%-4d b=%-4d",
                        r.left, r.top, r.right, r.bottom);
            //Make the window-relative rect a client-relative one for Windows
            ::OffsetRect(&r, -wsdo->insets.left, -wsdo->insets.top);
            if (r.left > r.right) r.left = r.right;
            if (r.top > r.bottom) r.top = r.bottom;
            HRGN hrgn = ::CreateRectRgnIndirect(&r);
            ::SelectClipRgn(hdc, hrgn);
            ::DeleteObject(hrgn);
        }
    }
    if (type & BRUSH) {
        if (wsdo->brushclr != color || (wsdo->brush == NULL)) {
            if (wsdo->brush != NULL) {
                wsdo->brush->Release();
            }
            wsdo->brush = AwtBrush::Get(CheckGrayColor(wsdo, color));
            wsdo->brushclr = color;
        }
        // always select a new brush - the DC is new every time
        ::SelectObject(hdc, wsdo->brush->GetHandle());
    } else if (type & NOBRUSH) {
        ::SelectObject(hdc, nullbrush);
    }
    if (type & PEN) {
        if (wsdo->penclr != color || (wsdo->pen == NULL)) {
            if (wsdo->pen != NULL) {
                wsdo->pen->Release();
            }
            wsdo->pen = AwtPen::Get(CheckGrayColor(wsdo, color));
            wsdo->penclr = color;
        }
        // always select a new pen - the DC is new every time
        ::SelectObject(hdc, wsdo->pen->GetHandle());
    } else if (type & NOPEN) {
        ::SelectObject(hdc, nullpen);
    }
}

void Win32OSSD_ReleaseDC(JNIEnv *env, Win32SDOps *wsdo, HDC hdc)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32OSSD_ReleaseDC");
    wsdo->lpSurface->ReleaseDC(hdc);
    wsdo->gdiOpPending = TRUE;
}

void Win32OSSD_InvalidateSD(JNIEnv *env, Win32SDOps *wsdo)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32OSSD_InvalidateSD");
    wsdo->invalid = JNI_TRUE;
}

/*
 * Class:     sun_java2d_windows_Win32OffScreenSurfaceData
 * Method:    invalidateSD
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_Win32OffScreenSurfaceData_nativeInvalidate(JNIEnv *env,
                                                                   jobject wsd)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32OffScreenSurfaceData_nativeInvalidate");
    Win32SDOps *wsdo = (Win32SDOps *)SurfaceData_GetOps(env, wsd);
    if (wsdo != NULL) {
        wsdo->InvalidateSD(env, wsdo);
    }
}


/*
 * Method:    Win32OSSD_Dispose
 */
void
Win32OSSD_Dispose(JNIEnv *env, SurfaceDataOps *ops)
{
    Win32SDOps *wsdo = (Win32SDOps*)ops;
    J2dTraceLn2(J2D_TRACE_VERBOSE, "Win32OSSD_Dispose vram=0%x sysm=0%x",
                wsdo->surfacePuntData.lpSurfaceVram,
                wsdo->surfacePuntData.lpSurfaceSystem);
    // REMIND: Need to delete a lot of other things here as well, starting
    // with the offscreen surface

    // ops is assumed non-null as it is checked in SurfaceData_DisposeOps
    if (wsdo->surfacePuntData.lpSurfaceVram) {
        delete wsdo->surfacePuntData.lpSurfaceVram;
    }
    if (wsdo->surfacePuntData.lpSurfaceSystem) {
        delete wsdo->surfacePuntData.lpSurfaceSystem;
    }
    if (wsdo->brush != NULL) {
        wsdo->brush->Release();
    }
    if (wsdo->pen != NULL) {
        wsdo->pen->Release();
    }
    wsdo->lpSurface = NULL;
    disposeOSSD_WSDO(env, wsdo);
}

JNIEXPORT void JNICALL
Java_sun_java2d_windows_Win32OffScreenSurfaceData_setTransparentPixel(JNIEnv *env,
                                                                      jobject wsd,
                                                                      jint pixel)
{
    Win32SDOps *wsdo = (Win32SDOps *)SurfaceData_GetOps(env, wsd);
    DDSetColorKey(env, wsdo, pixel);
}

/*
 * Class:     sun_java2d_windows_Win32OffScreenSurfaceData
 * Method:    flush
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_Win32OffScreenSurfaceData_flush(JNIEnv *env,
                                                        jobject wsd)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32OffScreenSurfaceData_flush");
    Win32SDOps *wsdo = (Win32SDOps *)SurfaceData_GetOps(env, wsd);
    if (wsdo != NULL) {
        // Note that wsdo may be null if there was some error during
        // construction, such as a surface depth we could not handle
        DDReleaseSurfaceMemory(wsdo->surfacePuntData.lpSurfaceSystem);
        DDReleaseSurfaceMemory(wsdo->surfacePuntData.lpSurfaceVram);
    }
}
