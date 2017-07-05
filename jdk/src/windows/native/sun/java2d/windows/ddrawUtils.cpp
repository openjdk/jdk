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

#define INITGUID
#include "Trace.h"
#include "ddrawUtils.h"
#include "ddrawObject.h"
#include "awt_Multimon.h"
#include "awt_MMStub.h"
#include "dxInit.h"
#include "WindowsFlags.h"
#include "D3DContext.h"

//
// Globals
//
DDrawObjectStruct           **ddInstance;
int                         maxDDDevices = 0;
int                         currNumDevices = 0;
CriticalSection             ddInstanceLock;
extern BOOL                 isAppActive;
extern HINSTANCE            hLibDDraw; // DDraw Library handle
extern jfieldID             ddSurfacePuntedID;

extern "C" void Win32OSSD_DisableDD(JNIEnv *env, Win32SDOps *wsdo);

//
// Constants
//
#define MAX_BUSY_ATTEMPTS 50    // Arbitrary number of times to attempt
                                // an operation that returns a busy error

//
// Macros
//

/**
 * This macro is just a shortcut for the various places in the
 * code where we want to call a ddraw function and print any error
 * if the result is not equal to DD_OK.  The errorString passed
 * in is for debugging/tracing purposes only.
 */
#define DD_FUNC(func, errorString) { \
    HRESULT ddResult = func; \
    if (ddResult != DD_OK) { \
        DebugPrintDirectDrawError(ddResult, errorString); \
    } \
}
/**
 * Same as above, only return FALSE when done (to be used only in
 * functions that should return FALSE on a ddraw failure).
 */
#define DD_FUNC_RETURN(func, errorString) { \
    HRESULT ddResult = func; \
    if (ddResult != DD_OK) { \
        DebugPrintDirectDrawError(ddResult, errorString); \
        return FALSE; \
    } \
}

//
// INLINE functions
//

// Attaches the clipper object of a given surface to
// the primary.  Note that this action only happens if the
// surface is onscreen (clipping only makes sense for onscreen windows)
INLINE void AttachClipper(Win32SDOps *wsdo) {
    if (wsdo->window && wsdo->ddInstance->hwndFullScreen == NULL) {
        J2dTraceLn(J2D_TRACE_VERBOSE, "AttachClipper");
        HRESULT ddResult;
        ddResult = wsdo->ddInstance->clipper->SetHWnd(0, wsdo->window);
        if (ddResult != DD_OK) {
            DebugPrintDirectDrawError(ddResult, "AttachClipper");
        }
    }
}

//
// Functions
//

/**
 * Returns the ddInstance associated with a particular HMONITOR
 */
DDrawObjectStruct *GetDDInstanceForDevice(HMONITOR hMon)
{
    J2dTraceLn(J2D_TRACE_VERBOSE, "GetDDInstanceForDevice");
    DDrawObjectStruct *tmpDdInstance = NULL;
    ddInstanceLock.Enter();
    if (currNumDevices == 1) {
        // Non multimon situation
        if (ddInstance[0])
        {
            tmpDdInstance = ddInstance[0];
        }
    } else {
        for (int i = 0; i < currNumDevices; ++i) {
            if (ddInstance[i]
                && hMon == ddInstance[i]->hMonitor)
            {
                tmpDdInstance = ddInstance[i];
                break;
            }
        }
    }
    if (tmpDdInstance != NULL && !tmpDdInstance->accelerated) {
        // Some failure situations (see DDSetupDevice in dxInit.cpp) can cause
        // a ddInstance object to become invalid.  If this happens, we should
        // not be using this ddInstance object at all, so return NULL.
        tmpDdInstance = NULL;
    }
    ddInstanceLock.Leave();
    return tmpDdInstance;
}

/**
 * Can return FALSE if there was some problem during ddraw
 * initialization for this screen, or if this screen does not
 * support some of the capabilities necessary for running ddraw
 * correctly.
 */
BOOL DDCanCreatePrimary(HMONITOR hMon) {
    DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);
    return (useDD && ddInstance && tmpDdInstance);
}

/**
 * Can return FALSE if the device that the surfaceData object
 * resides on cannot support accelerated Blt's.  Some devices
 * can perform basic ddraw Lock/Unlock commands but cannot
 * handle the ddraw Blt command.
 */
BOOL DDCanBlt(Win32SDOps *wsdo) {
    return (useDD && wsdo->ddInstance && wsdo->ddInstance->canBlt);
}

/**
 * Can return FALSE if either ddraw is not enabled at all (problems
 * during initialization) or the device associated with the hMon object
 * cannot support the basic required capabilities (in
 * which case the ddInstance for that device will be set to NULL).
 */
BOOL DeviceUseDDraw(HMONITOR hMon) {
    DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);
    return (useDD && tmpDdInstance && tmpDdInstance->ddObject);
}

/**
 * Can return FALSE if either ddraw is not enabled at all (problems
 * during initialization) or the device associated with the hMon object
 * cannot support the basic required capabilities (in
 * which case the ddInstance for that device will be set to NULL).
 */
BOOL DeviceUseD3D(HMONITOR hMon) {
    DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);
    return (useDD && tmpDdInstance && tmpDdInstance->ddObject &&
            tmpDdInstance->ddObject->IsD3DEnabled());
}

/**
 * Can return FALSE if either ddraw is not enabled at all (problems
 * during initialization) or the device that the surfaceData object
 * resides on cannot support the basic required capabilities (in
 * which case the ddInstance for that device will be set to NULL).
 */
BOOL DDUseDDraw(Win32SDOps *wsdo) {
    return (useDD && wsdo->ddInstance && wsdo->ddInstance->valid);
}


/**
 * Release the resources consumed by ddraw.  This will be called
 * by the DllMain function when it receives a PROCESS_DETACH method,
 * meaning that the application is done with awt.  We need to release
 * these ddraw resources because of potential memory leaks, but
 * more importantly, because if we don't release a primary surface
 * that has been locked and not unlocked, then we may cause
 * ddraw to be corrupted on this system until reboot.
 * IMPORTANT: because we do not use any locks around this release,
 * we assume that this function is called only during the
 * PROCESS_DETACH procedure described above.  Any other situation
 * could cause unpredictable results.
 */
void DDRelease()
{
    J2dTraceLn(J2D_TRACE_INFO, "DDRelease");

    // Note that we do not lock the ddInstanceLock CriticalSection.
    // Normally we should do that in this kind of situation (to ensure
    // that the ddInstance used in all release calls is the same on).
    // But in this case we do not want the release of a locked surface
    // to be hampered by some bad CriticalSection deadlock, so we
    // will just release ddInstance anyway.
    // Anyway, if users of this function call it properly (as
    // documented above), then there should be no problem.
    try {
        if (hLibDDraw) {
            ::FreeLibrary(hLibDDraw);
            hLibDDraw = NULL;
        }
        hLibDDraw = NULL;
        if (ddInstance) {
            for (int i = 0; i < currNumDevices; ++i) {
                ReleaseDDInstance(ddInstance[i]);
            }
            free(ddInstance);
        }
    } catch (...) {
        // Handle all exceptions by simply returning.
        // There are some cases where the OS may have already
        // released our objects for us (e.g., NT4) and we have
        // no way of knowing, but the above call into Release will
        // cause an exception to be thrown by dereferencing
        // already-released ddraw objects
    }
}


/**
 * Create the primary surface.  Note that we do not take the ddInstanceLock
 * here; we assume that our callers are taking that lock for us.
 */
BOOL DDCreatePrimary(Win32SDOps *wsdo) {
    J2dTraceLn(J2D_TRACE_INFO, "DDCreatePrimary");
    BOOL ret = TRUE;

    if (wsdo != NULL && wsdo->device != NULL) {
        HMONITOR hMon;
        hMon = (HMONITOR)wsdo->device->GetMonitor();
        DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);
        // Check if we need to recreate the primary for this device.
        // If we are in full-screen mode, we do not need to change
        // the primary unless the number of back buffers has changed.
        if (tmpDdInstance == NULL) {
            return FALSE;
        }
        if (tmpDdInstance->hwndFullScreen == NULL ||
            tmpDdInstance->context != CONTEXT_NORMAL)
        {
            ret = DDSetupDevice(tmpDdInstance,
                AwtWin32GraphicsDevice::GetDxCapsForDevice(hMon));
            tmpDdInstance->context = CONTEXT_NORMAL;
        }
        if (ret) {
            tmpDdInstance->valid = TRUE;
        }
        return ret;
    }
    return ret;
}

/**
 * Returns a DDrawSurface which should be used for performing DDraw sync.
 *
 * On systems other than Windows Vista, a primary surface is returned.
 *
 * On Windows Vista, a 1x1 scratch offscreen surface will be created and
 * maintained, because locking the primary surface will cause DWM to be
 * disabled for the run of the application.
 *
 * Note: this method must be called while ddInstance lock is held.
 * Note: a "DDINSTANCE_USABLE" non-null argument is assumed.
 */
// 4978973: Only lock one pixel to flush; avoids GDI flicker artifacts
// and only fill one pixel in the sync surface
static RECT tinyRect = { 0, 0, 1, 1 };
static DDrawSurface* DDGetSyncSurface(DDrawObjectStruct *tmpDdInstance)
{
    static BOOL bIsVista = IS_WINVISTA;
    static DDBLTFX ddBltFx;
    DDrawSurface *lpSyncSurface;

    if (bIsVista) {
        lpSyncSurface = tmpDdInstance->syncSurface;

        if (lpSyncSurface != NULL) {
            // return the existing surface if it wasn't lost or it was
            // restored succesfully
            if (SUCCEEDED(lpSyncSurface->IsLost()) ||
                SUCCEEDED(lpSyncSurface->Restore()))
            {
                // we need to render to the sync surface so that DDraw
                // will flush the pipeline when we lock it in DDSync
                lpSyncSurface->Blt(&tinyRect, NULL, NULL,
                                   DDBLT_COLORFILL | DDBLT_WAIT,
                                   &ddBltFx);
                return lpSyncSurface;
            }
            J2dTraceLn(J2D_TRACE_WARNING, "DDGetSyncSurface: failed to restore"
                       " sync surface, recreating");
            delete lpSyncSurface;
        }

        // this doesn't need to be done every time in the fast path
        ddBltFx.dwSize = sizeof(ddBltFx);
        ddBltFx.dwFillColor = 0xffffffff;

        lpSyncSurface =
            tmpDdInstance->ddObject->
                CreateDDOffScreenSurface(1, 1, 24/*ignored*/,
                                         TR_OPAQUE,
                                         DDSCAPS_VIDEOMEMORY);
        tmpDdInstance->syncSurface = lpSyncSurface;
    } else {
        lpSyncSurface = tmpDdInstance->primary;
    }

    return lpSyncSurface;
}

void
DDFreeSyncSurface(DDrawObjectStruct *tmpDdInstance)
{
    J2dTraceLn(J2D_TRACE_INFO, "DDFreeSyncSurface");
    if (tmpDdInstance != NULL && tmpDdInstance->syncSurface != NULL) {
        delete tmpDdInstance->syncSurface;
        tmpDdInstance->syncSurface = NULL;
    }
}

/**
 * Synchronize graphics pipeline by calling Lock/Unlock on primary
 * surface
 */
void DDSync()
{
    int attempts = 0;
    HRESULT ddResult;
    DDrawSurface *lpSyncSurface = NULL;

    J2dTraceLn(J2D_TRACE_INFO, "DDSync");
    // REMIND: need to handle errors here
    ddInstanceLock.Enter();
    for (int i = 0; i < currNumDevices; ++i) {
        DDrawObjectStruct *tmpDdInstance = ddInstance[i];

        if (!DDINSTANCE_USABLE(tmpDdInstance)) {
            continue;
        }
        lpSyncSurface = DDGetSyncSurface(tmpDdInstance);
        if (lpSyncSurface == NULL) {
            J2dRlsTraceLn1(J2D_TRACE_ERROR,
                           "DDSync: no sync surface for device %d", i);
            continue;
        }
        // Spin while busy up to some finite number of times
        do {
            ddResult = lpSyncSurface->Lock(&tinyRect, NULL,
                                           DDLOCK_WAIT, NULL);
        } while ((ddResult == DDERR_SURFACEBUSY) &&
                 (++attempts < MAX_BUSY_ATTEMPTS));
        if (ddResult == DD_OK) {
            ddResult = lpSyncSurface->Unlock(&tinyRect);
        }
    }
    ddInstanceLock.Leave();
    J2dTraceLn(J2D_TRACE_VERBOSE, "DDSync done");
}


/**
 * Simple clip check against the window of the given surface data.
 * If the clip list is complex or if the clip list intersects
 * the visible region of the window then return FALSE, meaning
 * that the clipping is sufficiently complex that the caller
 * may want to find an alternative means (other than ddraw) of
 * performing an operation.
 */
BOOL DDClipCheck(Win32SDOps *wsdo, RECT *operationRect)
{
    static struct {
        RGNDATAHEADER   rdh;
        RECT            rects[1];
    } rgnData;
    unsigned long rgnSize = sizeof(rgnData);
    HRESULT ddResult;

    J2dTraceLn(J2D_TRACE_VERBOSE, "DDClipCheck");

    if (!wsdo->window) {
        // Offscreen surfaces need no clipping
        return TRUE;
    }

    // If ddResult not OK, could be because of a complex clipping region
    // (Our rgnData structure only has space for a simple rectangle region).
    // Thus, we return FALSE and attach the clipper object.
    DDrawObjectStruct *tmpDdInstance = wsdo->ddInstance;
    if (!DDINSTANCE_USABLE(tmpDdInstance)) {
        return FALSE;
    }
    if (wsdo->window == tmpDdInstance->hwndFullScreen) {
        // Fullscreen surfaces need no clipping
        return TRUE;
    }
    DD_FUNC(tmpDdInstance->clipper->SetHWnd(0, wsdo->window),
        "DDClipCheck: SetHWnd");
    ddResult = tmpDdInstance->clipper->GetClipList(NULL, (RGNDATA*)&rgnData,
        &rgnSize);
    if (ddResult == DDERR_REGIONTOOSMALL) {
        // Complex clipping region
        // REMIND: could be more clever here and actually check operationRect
        // against all rectangles in clipList, but this works for now.
        return FALSE;
    }
    // Check intersection of clip region with operationRect.  If clip region
    // smaller, then we have a simple clip case.
    // If no operationRect, then check against entire window bounds.
    if (operationRect) {
        if (operationRect->left   < rgnData.rects[0].left ||
            operationRect->top    < rgnData.rects[0].top  ||
            operationRect->right  > rgnData.rects[0].right ||
            operationRect->bottom > rgnData.rects[0].bottom)
        {
            return FALSE;
        }
    } else {
        RECT winrect;
        ::GetWindowRect(wsdo->window, &winrect);
        if (winrect.left   < rgnData.rects[0].left ||
            winrect.top    < rgnData.rects[0].top  ||
            winrect.right  > rgnData.rects[0].right ||
            winrect.bottom > rgnData.rects[0].bottom)
        {
            return FALSE;
        }
    }
    return TRUE;
}


/**
 * Lock the surface.
 */
BOOL DDLock(JNIEnv *env, Win32SDOps *wsdo, RECT *lockRect,
            SurfaceDataRasInfo *pRasInfo)
{
    J2dTraceLn1(J2D_TRACE_INFO, "DDLock: wsdo->lpSurface=0x%x", wsdo->lpSurface);

    int attempts = 0;

    if (wsdo->gdiOpPending) {
        // This sync is really for flushing any pending GDI
        // operations. On ATI boards GdiFlush() doesn't do the trick,
        // only locking the primary works.
        DDSync();
        wsdo->gdiOpPending = FALSE;
    }
    while (attempts++ < MAX_BUSY_ATTEMPTS) {
        if (!wsdo->ddInstance->valid) {
            // If dd object became invalid, don't bother calling Lock
            // Note: This check should not be necessary because we should
            // do the right thing in any case - catch the error, try to
            // restore the surface, fai, etc.  But there seem to be problems
            // with ddraw that sometimes cause it to hang in the Restore and
            // Lock calls. Better to avoid the situation as much as we can and
            // bail out early.
            J2dTraceLn(J2D_TRACE_ERROR,
                       "DDLock: wsdo->ddInstance invalid");
            return FALSE;
        }
        HRESULT ddResult = wsdo->lpSurface->Lock(lockRect, pRasInfo,
            DDLOCK_WAIT, NULL);
        // Spin on the busy-type errors, else return having failed or succeeded
        switch (ddResult) {
        case DD_OK:
            return TRUE;
        case DDERR_WASSTILLDRAWING:
        case DDERR_SURFACEBUSY:
            J2dTraceLn(J2D_TRACE_WARNING, "DDLock: surface busy...");
            break;
        case DDERR_SURFACELOST:
            J2dTraceLn(J2D_TRACE_WARNING, "DDLock: surface lost");
            wsdo->RestoreSurface(env, wsdo);
            return FALSE;
        case DDERR_GENERIC:
            J2dRlsTraceLn(J2D_TRACE_ERROR, "DDLock: unexpected error");
            if (wsdo->window == NULL) {
                Win32OSSD_DisableDD(env, wsdo);
            }
            return FALSE;
        default:
            DebugPrintDirectDrawError(ddResult, "DDLock");
            return FALSE;
        }
    }
    // If we get here, then there was an error in the function and we
    // should return false
    return FALSE;
}


/**
 * Unlock the surface
 */
void DDUnlock(JNIEnv *env, Win32SDOps *wsdo)
{
    J2dTraceLn1(J2D_TRACE_INFO, "DDUnlock: wsdo->lpSurface=0x%x", wsdo->lpSurface);

    HRESULT ddResult = wsdo->lpSurface->Unlock(NULL);
    // Spin on the busy-type errors, else return having failed or succeeded
    switch (ddResult) {
    case DD_OK:
        return;
    case DDERR_NOTLOCKED:
        J2dTraceLn(J2D_TRACE_ERROR, "DDUnlock: Surface not locked");
        return;
    case DDERR_SURFACELOST:
        J2dTraceLn(J2D_TRACE_WARNING, "DDUnlock: Surface lost");
        wsdo->RestoreSurface(env, wsdo);
        return;
    default:
        DebugPrintDirectDrawError(ddResult, "DDUnlock");
        return;
    }
}

/**
 * Fill given surface with given color in given RECT bounds
 */
BOOL DDColorFill(JNIEnv *env, jobject sData, Win32SDOps *wsdo,
                 RECT *fillRect, jint color)
{
    DDBLTFX ddBltFx;
    HRESULT ddResult;
    int attempts = 0;

    J2dTraceLn(J2D_TRACE_VERBOSE, "DDColorFill");
    J2dTraceLn5(J2D_TRACE_VERBOSE,
                "  color=0x%x l=%-4d t=%-4d r=%-4d b=%-4d",
                color, fillRect->left, fillRect->top, fillRect->right,
                fillRect->bottom);
    ddBltFx.dwSize = sizeof(ddBltFx);
    ddBltFx.dwFillColor = color;
    AttachClipper(wsdo);
    while (attempts++ < MAX_BUSY_ATTEMPTS) {
        ddResult = wsdo->lpSurface->Blt(fillRect, NULL, NULL,
                                        DDBLT_COLORFILL | DDBLT_WAIT,
                                        &ddBltFx);
        // Spin on the busy-type errors, else return having failed or succeeded
        switch (ddResult) {
        case DD_OK:
            return TRUE;
        case DDERR_INVALIDRECT:
            J2dTraceLn4(J2D_TRACE_ERROR,
                        "DDColorFill: Invalid rect for colorfill "\
                        "l=%-4d t=%-4d r=%-4d b=%-4d",
                        fillRect->left, fillRect->top,
                        fillRect->right, fillRect->bottom);
            return FALSE;
        case DDERR_SURFACEBUSY:
            J2dTraceLn(J2D_TRACE_WARNING, "DDColorFill: surface busy");
            break;
        case DDERR_SURFACELOST:
            J2dTraceLn(J2D_TRACE_WARNING, "DDColorfill: surface lost");
            wsdo->RestoreSurface(env, wsdo);
            return FALSE;
        default:
            DebugPrintDirectDrawError(ddResult, "DDColorFill");
        }
    }
    J2dTraceLn(J2D_TRACE_VERBOSE, "DDColorFill done");
    return FALSE;
}

void ManageOffscreenSurfaceBlt(JNIEnv *env, Win32SDOps *wsdo)
{
    J2dTraceLn(J2D_TRACE_INFO, "ManageOffscreenSurfaceBlt");
    wsdo->surfacePuntData.pixelsReadSinceBlt = 0;
    if (wsdo->surfacePuntData.numBltsSinceRead >=
        wsdo->surfacePuntData.numBltsThreshold)
    {
        if (wsdo->surfacePuntData.usingDDSystem) {
            if (wsdo->surfacePuntData.lpSurfaceVram->Blt(NULL,
                    wsdo->surfacePuntData.lpSurfaceSystem,
                    NULL, DDBLT_WAIT, NULL) == DD_OK)
            {
                J2dTraceLn2(J2D_TRACE_VERBOSE,
                            "  Unpunting sys to VRAM: 0x%x -> 0x%x",
                            wsdo->surfacePuntData.lpSurfaceVram,
                            wsdo->surfacePuntData.lpSurfaceSystem);
                wsdo->lpSurface = wsdo->surfacePuntData.lpSurfaceVram;
                wsdo->surfacePuntData.usingDDSystem = FALSE;
                // Now: double our threshhold to prevent thrashing; we
                // don't want to keep punting and un-punting our surface
                wsdo->surfacePuntData.numBltsThreshold *= 2;
                // Notify the Java level that this surface has
                // been unpunted so that future copies to this surface
                // from accelerated src surfaces will do the right thing.
                jobject sdObject = env->NewLocalRef(wsdo->sdOps.sdObject);
                if (sdObject) {
                    // Only bother with this optimization if the
                    // reference is still valid
                    env->SetBooleanField(sdObject, ddSurfacePuntedID, JNI_FALSE);
                    env->DeleteLocalRef(sdObject);
                }
            }
        }
    } else {
        wsdo->surfacePuntData.numBltsSinceRead++;
    }
}

/**
 * Copy data from src to dst using src and dst rectangles
 */
BOOL DDBlt(JNIEnv *env, Win32SDOps *wsdoSrc, Win32SDOps *wsdoDst,
               RECT *rDst, RECT *rSrc, CompositeInfo *compInfo)
{
    int attempts = 0;
    DWORD bltFlags = DDBLT_WAIT;

    J2dTraceLn(J2D_TRACE_INFO, "DDBlt");
    J2dTraceLn4(J2D_TRACE_INFO, "  src rect: l=%-4d t=%-4d r=%-4d b=%-4d",
                    rSrc->left, rSrc->top, rSrc->right, rSrc->bottom);
    J2dTraceLn4(J2D_TRACE_INFO, "  dst rect: l=%-4d t=%-4d r=%-4d b=%-4d",
                    rDst->left, rDst->top, rDst->right, rDst->bottom);

    // Note: the primary can only have one clipper attached to it at
    // any time.  This seems weird to set it to src then dst, but this
    // works because either: both are the same window (devCopyArea),
    // neither are windows (both offscreen), or only one is a window
    // (Blt).  We can't get here from a windowA -> windowB copy operation.
    AttachClipper(wsdoSrc);
    AttachClipper(wsdoDst);

    // Administrate system-surface punt mechanism for offscreen images
    if (!wsdoSrc->window && !wsdoSrc->surfacePuntData.disablePunts) {
        ManageOffscreenSurfaceBlt(env, wsdoSrc);
    }
    if (wsdoSrc->transparency == TR_BITMASK) {
        bltFlags |= DDBLT_KEYSRC;
    }
    while (attempts++ < MAX_BUSY_ATTEMPTS) {
        HRESULT ddResult =
            wsdoDst->lpSurface->Blt(rDst, wsdoSrc->lpSurface,
                                    rSrc, bltFlags, NULL);

        // Spin on the busy-type errors or return having failed or succeeded
        switch (ddResult) {
        case DD_OK:
            return TRUE;
        case DDERR_SURFACEBUSY:
            J2dTraceLn(J2D_TRACE_WARNING, "DDBlt: surface busy");
            break;
        case DDERR_SURFACELOST:
            /**
             * Only restore the Dst if it is truly lost; "restoring" an
             * offscreen surface simply sets a flag and throws an exception,
             * thus guaranteeing that the Src restore below will not happen.
             * So if the Src stays Lost and we keep trying to restore an un-Lost
             * Dst, then we will never actually do the restore on the Src.
             */
            if (wsdoDst->lpSurface->IsLost() != DD_OK) {
                J2dTraceLn(J2D_TRACE_WARNING, "DDBlt: dst surface lost");
                wsdoDst->RestoreSurface(env, wsdoDst);
            }
            if (wsdoSrc->lpSurface->IsLost() != DD_OK) {
                J2dTraceLn(J2D_TRACE_WARNING, "DDBlt: src surface lost");
                wsdoSrc->RestoreSurface(env, wsdoSrc);
            }
            return FALSE;
        default:
            DebugPrintDirectDrawError(ddResult, "DDBlt");
            return FALSE;
        }
    }
    return FALSE;
}

/**
 * Set the color key information for this surface.  During a
 * blit operation, pixels of the specified color will not be
 * drawn (resulting in transparent areas of the image).  Note
 * that the "transparency" field in the Win32SDOps structure must
 * be set to TR_BITMASK for the color key information to have an effect.
 */
void DDSetColorKey(JNIEnv *env, Win32SDOps *wsdo, jint color)
{
    J2dTraceLn(J2D_TRACE_VERBOSE, "DDSetColorKey");
    DDCOLORKEY    ddck;
    HRESULT       ddResult;

    ddck.dwColorSpaceLowValue  = color;
    ddck.dwColorSpaceHighValue = color;

    ddResult = wsdo->lpSurface->SetColorKey(DDCKEY_SRCBLT, &ddck);

    if (ddResult != DD_OK) {
        DebugPrintDirectDrawError(ddResult, "DDSetColorKey");
    }
}


/**
 * Swaps the surface memory of the front and back buffers.
 * Flips memory from the source surface to the destination surface.
 */
BOOL DDFlip(JNIEnv *env, Win32SDOps *src, Win32SDOps *dest)
{
    J2dTraceLn(J2D_TRACE_INFO, "DDFlip");
    int attempts = 0;
    while (attempts++ < MAX_BUSY_ATTEMPTS) {
        HRESULT ddResult = src->lpSurface->Flip(dest->lpSurface);
        // Spin on the busy-type errors or return having failed or succeeded
        switch (ddResult) {
        case DD_OK:
            return TRUE;
        case DDERR_SURFACEBUSY:
            J2dTraceLn(J2D_TRACE_WARNING, "DDFlip: surface busy");
            break;
        case DDERR_SURFACELOST:
            if (dest->lpSurface->IsLost() != DD_OK) {
                J2dTraceLn(J2D_TRACE_WARNING, "DDFlip: dst surface lost");
                dest->RestoreSurface(env, dest);
            }
            if (src->lpSurface->IsLost() != DD_OK) {
                J2dTraceLn(J2D_TRACE_WARNING, "DDFlip: src surface lost");
                src->RestoreSurface(env, src);
            }
            return FALSE;
        default:
            DebugPrintDirectDrawError(ddResult, "DDFlip");
            return FALSE;
        }
    }
    return FALSE;
}


/**
 * Mark the given ddInstance structure as invalid.  This flag
 * can then be used to detect rendering with an invalid ddraw
 * object later (to avoid further ddraw errors) or to detect
 * when it is time to create a new ddraw object.  Recreation
 * happens when we are asked to create a new surface but the
 * current ddInstance global structure is invalid.
 */
void DDInvalidateDDInstance(DDrawObjectStruct *ddInst) {
    J2dTraceLn(J2D_TRACE_INFO, "DDInvalidateDDInstance");
    if (useDD) {
        if (ddInst != NULL) {
            // Invalidate given instance of ddInstance
            ddInst->valid = FALSE;
        } else {
            // Invalidate global ddInstance.  This occurs at the start
            // of a display-change event.
            for (int i = 0; i < currNumDevices; ++i) {
                if (ddInstance[i] && ddInstance[i]->hwndFullScreen == NULL) {
                    ddInstance[i]->valid = FALSE;
                }
            }
        }
    }
}

/**
 * Utility routine: release all elements of given ddInst structure
 * and free the memory consumed by ddInst.  Note that this may be
 * called during a failed DDCreateDDObject, so any null fields were
 * not yet initialized and should not be released.
 */
void ReleaseDDInstance(DDrawObjectStruct *ddInst)
{
    J2dTraceLn(J2D_TRACE_INFO, "ReleaseDDInstance");
    if (ddInst) {
        if (ddInst->primary) {
            delete ddInst->primary;
            ddInst->primary = NULL;
        }
        if (ddInst->clipper) {
            delete ddInst->clipper;
            ddInst->clipper = NULL;
        }
        if (ddInst->ddObject) {
            delete ddInst->ddObject;
            ddInst->ddObject = NULL;
        }
        free(ddInst);
    }
}

/**
 * Enters full-screen exclusive mode, setting the hwnd as the screen
 */
BOOL DDEnterFullScreen(HMONITOR hMon, HWND hwnd, HWND topLevelHwnd)
{
    HRESULT ddResult = DD_OK;
    // Sleep so that programatically full-screen cannot be entered
    // and left multiple times quickly enough to crash the driver
    static DWORD prevTime = 0;
    DWORD currTime = ::GetTickCount();
    DWORD timeDiff = (currTime - prevTime);
    if (timeDiff < 500) {
        ::Sleep(500 - timeDiff);
    }
    prevTime = currTime;

    DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);
    tmpDdInstance->ddObject->SetCooperativeLevel(topLevelHwnd,
        DDSCL_FULLSCREEN | DDSCL_EXCLUSIVE);
    if (ddResult != DD_OK) {
        DebugPrintDirectDrawError(ddResult, "DDEnterFullScreen");
        return FALSE;
    }
    if (tmpDdInstance->primary) {
        // No clipping necessary in fullscreen mode.  Elsewhere,
        // we avoid setting the clip list for the fullscreen window,
        // so we should also null-out the clipper object for the
        // primary surface in that case.  Bug 4737785.
        tmpDdInstance->primary->SetClipper(NULL);
    }
    tmpDdInstance->hwndFullScreen = hwnd;
    tmpDdInstance->context = CONTEXT_ENTER_FULL_SCREEN;

    return TRUE;
}


/**
 * Exits full-screen exclusive mode
 */
BOOL DDExitFullScreen(HMONITOR hMon, HWND hwnd)
{
    // Sleep so that programatically full-screen cannot be entered
    // and left multiple times quickly enough to crash the driver
    static DWORD prevTime = 0;
    DWORD currTime = ::GetTickCount();
    DWORD timeDiff = (currTime - prevTime);
    if (timeDiff < 500) {
        ::Sleep(500 - timeDiff);
    }
    prevTime = currTime;

    J2dTraceLn(J2D_TRACE_INFO, "DDExitFullScreen");
    DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);
    tmpDdInstance->context = CONTEXT_EXIT_FULL_SCREEN;
    if (!tmpDdInstance || !tmpDdInstance->ddObject ||
        !tmpDdInstance->ddObject->RestoreDDDisplayMode()) {
        return FALSE;
    }
    J2dTraceLn1(J2D_TRACE_VERBOSE,
                "DDExitFullScreen: Restoring cooperative level hwnd=0x%x",
                hwnd);
    HRESULT ddResult =
        tmpDdInstance->ddObject->SetCooperativeLevel(NULL, DDSCL_NORMAL);
    if (ddResult != DD_OK) {
        DebugPrintDirectDrawError(ddResult, "DDExitFullScreen");
        return FALSE;
    }
    if (tmpDdInstance->clipper == NULL) {
        // May not have created clipper if we were in FS mode during
        // primary creation
        tmpDdInstance->clipper = tmpDdInstance->ddObject->CreateDDClipper();
    }
    if (tmpDdInstance->clipper != NULL) {
        tmpDdInstance->primary->SetClipper(tmpDdInstance->clipper);
    }
    J2dTraceLn(J2D_TRACE_VERBOSE,
               "DDExitFullScreen: Restored cooperative level");
    tmpDdInstance->hwndFullScreen = NULL;
    tmpDdInstance->context = CONTEXT_NORMAL;
    return TRUE;
}

/**
 * Gets the current display mode; sets the values in displayMode
 */
BOOL DDGetDisplayMode(HMONITOR hMon, DDrawDisplayMode& displayMode)
{
    DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);
    if (tmpDdInstance && tmpDdInstance->ddObject) {
        return tmpDdInstance->ddObject->GetDDDisplayMode(displayMode);
    } else {
        return FALSE;
    }
}

/**
 * Sets the display mode to the supplied mode
 */
BOOL DDSetDisplayMode(HMONITOR hMon, DDrawDisplayMode& displayMode)
{
    DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);
    if (tmpDdInstance) {
        tmpDdInstance->context = CONTEXT_DISPLAY_CHANGE;
    }
    if (tmpDdInstance && tmpDdInstance->ddObject) {
        int attempts = 0;
        while (attempts++ < MAX_BUSY_ATTEMPTS) {
            HRESULT ddResult = tmpDdInstance->ddObject->SetDDDisplayMode(
                displayMode);
            // Spin on the busy-type errors or return having failed or succeeded
            switch (ddResult) {
                case DD_OK:
                    return TRUE;
                case DDERR_SURFACEBUSY:
                    J2dTraceLn(J2D_TRACE_WARNING,
                               "DDSetDisplayMode: surface busy");
                    break;
                default:
                    DebugPrintDirectDrawError(ddResult, "DDSetDisplayMode");
                    return FALSE;
            }
        }
        return FALSE;
    } else {
        return FALSE;
    }
}

/**
 * Enumerates all display modes, calling the supplied callback for each
 * display mode returned by the system
 */
BOOL DDEnumDisplayModes(HMONITOR hMon, DDrawDisplayMode* constraint,
    DDrawDisplayMode::Callback callback, void* context)
{
    DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);
    if (tmpDdInstance && tmpDdInstance->ddObject) {
        return tmpDdInstance->ddObject->EnumDDDisplayModes(
            constraint, callback, context);
    } else {
        return FALSE;
    }
}

/**
 * Attempts to restore surface.  This will only succeed if the system is
 * in a state that allows the surface to be restored.  If a restore
 * results in a DDERR_WRONGMODE, then the surface must be recreated
 * entirely; we do this by invalidating the surfaceData and recreating
 * it from scratch (at the Java level).
 */
BOOL DDRestoreSurface(Win32SDOps *wsdo)
{
    J2dTraceLn1(J2D_TRACE_INFO, "DDRestoreSurface, wsdo->lpSurface=0x%x",
                wsdo->lpSurface);

    DDrawObjectStruct *tmpDdInstance = wsdo->ddInstance;
    if (tmpDdInstance == NULL || !tmpDdInstance->accelerated) {
        return FALSE;
    }
    // Don't try to restore an inactive primary in full-screen mode
    if (!isAppActive && wsdo->window &&
        wsdo->window == tmpDdInstance->hwndFullScreen) {
        return FALSE;
    }
    if (wsdo->lpSurface->IsLost() == DD_OK) {
        J2dTraceLn(J2D_TRACE_VERBOSE, "DDRestoreSurface:  surface memory ok");
    }
    else {
        J2dTraceLn(J2D_TRACE_WARNING,
                   "DDRestoreSurface: surface memory lost, trying to restore");
        HRESULT ddResult;
        ddResult = wsdo->lpSurface->Restore();
        if (ddResult == DDERR_WRONGMODE) {
            // Strange full-screen bug; return false to avoid a hang.
            // Note that we should never get this error in full-screen mode.
            if (wsdo->window == tmpDdInstance->hwndFullScreen) {
                return FALSE;
            }
            // Wrong mode: display depth has been changed.
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "DDRestoreSurface failure: DDERR_WRONGMODE");
            if (wsdo->window) {
                /**
                 * If this is a window surface, invalidate this
                 * object's ddInstance and return the approriate error.  The
                 * surfaceData will later be invalidated, disposed, and
                 * re-created with the new and correct depth information.
                 * Only invalidate for windows because offscreen surfaces
                 * have other means of being re-created and do not necessarily
                 * mean that the ddInstance object is invalid for other surfaces
                 */
                DDInvalidateDDInstance(wsdo->ddInstance);
            }
            return FALSE;
        } else if (ddResult != DD_OK) {
            DebugPrintDirectDrawError(ddResult, "DDRestoreSurface");
            return FALSE;
        }
    }
    if (!tmpDdInstance->valid) {
        tmpDdInstance->valid = TRUE;
    }
    return TRUE;
}

jint DDGetAvailableMemory(HMONITOR hMon)
{
    J2dTraceLn(J2D_TRACE_INFO, "DDGetAvailableMemory");
    DWORD dwFree;
    DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);
    if (!useDD || !tmpDdInstance || !tmpDdInstance->valid) {
        return 0;
    }

    HRESULT ddResult = tmpDdInstance->ddObject->GetDDAvailableVidMem(&dwFree);
    if (ddResult != DD_OK) {
        DebugPrintDirectDrawError(ddResult, "GetAvailableMemory");
    }

    return (jint)dwFree;
}


/**
 * Creates either an offscreen or onscreen ddraw surface, depending
 * on the value of wsdo->window.  Handles the common
 * framework of surface creation, such as ddInstance management,
 * and passes off the functionality of actual surface creation to
 * other functions.  Successful creation results in a return value
 * of TRUE.
 */
BOOL DDCreateSurface(Win32SDOps *wsdo)
{
    J2dTraceLn(J2D_TRACE_INFO, "DDCreateSurface");
    HMONITOR hMon;
    hMon = (HMONITOR)wsdo->device->GetMonitor();
    DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);

    wsdo->ddInstance = NULL; // default value in case of error
    wsdo->lpSurface = NULL; // default value in case of error

    if (wsdo->window) {
        if (tmpDdInstance &&
            tmpDdInstance->backBufferCount != wsdo->backBufferCount &&
            tmpDdInstance->hwndFullScreen == wsdo->window)
        {
            tmpDdInstance->context = CONTEXT_CHANGE_BUFFER_COUNT;
            tmpDdInstance->backBufferCount = wsdo->backBufferCount;
        }
        if (!tmpDdInstance || !tmpDdInstance->valid ||
            tmpDdInstance->context != CONTEXT_NORMAL
            ) {
            // Only recreate dd object on primary create.  Given our current
            // model of displayChange event propagation, we can only guarantee
            // that the system has been properly prepared for a recreate when
            // we recreate a primary surface.  The offscreen surfaces may
            // be recreated at any time.
            // Recreating ddraw at offscreen surface creation time has caused
            // rendering artifacts as well as unexplainable hangs in ddraw
            // calls.
            ddInstanceLock.Enter();
            BOOL success = DDCreatePrimary(wsdo);
            ddInstanceLock.Leave();
            if (!success) {
                return FALSE;
            }
            tmpDdInstance = GetDDInstanceForDevice(hMon);
        }
        // restore the primary if it's lost
        if (tmpDdInstance->primary != NULL &&
            FAILED(tmpDdInstance->primary->IsLost()) &&
            FAILED(tmpDdInstance->primary->Restore()))
        {
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "DDCreateSurface: failed to restore primary surface");
            return FALSE;
        }
        // non-null window means onscreen surface.  Primary already
        // exists, just need to cache a pointer to it in this wsdo
        wsdo->lpSurface = tmpDdInstance->primary;
    } else {
        if (!tmpDdInstance || !tmpDdInstance->valid) {
            // Don't recreate the ddraw object here (see note above), but
            // do fail this creation.  We will come back here eventually
            // after an onscreen surface has been created (and the new
            // ddraw object to go along with it).
            return FALSE;
        }
        if (!DDCreateOffScreenSurface(wsdo, tmpDdInstance)) {
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "DDCreateSurface: Failed creating offscreen surface");
            return FALSE;
        }
    }
    wsdo->ddInstance = tmpDdInstance;
    J2dTraceLn2(J2D_TRACE_VERBOSE,
                "DDCreateSurface: succeeded ddInst=0x%x wsdo->lpSurface=0x%x",
                tmpDdInstance, wsdo->lpSurface);
    return TRUE;
}

/**
 * Utility function to make sure that native and java-level
 * surface depths are matched.  They can be mismatched when display-depths
 * change, either between the creation of the Java surfaceData structure
 * and the native ddraw surface, or later when a surface is automatically
 * adjusted to be the new display depth (even if it was created in a different
 * depth to begin with)
 */
BOOL DDSurfaceDepthsCompatible(int javaDepth, int nativeDepth)
{
    if (nativeDepth != javaDepth) {
        switch (nativeDepth) {
        case 0: // Error condition: something is wrong with the surface
        case 8:
        case 24:
            // Java and native surface depths should match exactly for
            // these cases
            return FALSE;
            break;
        case 16:
            // Java surfaceData should be 15 or 16 bits
            if (javaDepth < 15 || javaDepth > 16) {
                return FALSE;
            }
            break;
        case 32:
            // Could have this native depth for either 24- or 32-bit
            // Java surfaceData
            if (javaDepth != 24 && javaDepth != 32) {
                return FALSE;
            }
            break;
        default:
            // should not get here, but if we do something is odd, so
            // just register a failure
            return FALSE;
        }
    }
    return TRUE;
}


/**
 * Creates offscreen surface.  Examines the display mode information
 * for the current ddraw object and uses that to create this new
 * surface.
 */
BOOL DDCreateOffScreenSurface(Win32SDOps *wsdo,
                              DDrawObjectStruct *ddInst)
{
    J2dTraceLn(J2D_TRACE_INFO, "DDCreateOffScreenSurface");

    wsdo->lpSurface =
        ddInst->ddObject->CreateDDOffScreenSurface(wsdo->w, wsdo->h,
        wsdo->depth, wsdo->transparency, DDSCAPS_VIDEOMEMORY);
    if (!ddInst->primary || (ddInst->primary->IsLost() != DD_OK)) {
        if (wsdo->lpSurface) {
            delete wsdo->lpSurface;
            wsdo->lpSurface = NULL;
        }
        if (ddInst->primary) {
            // attempt to restore primary
            ddInst->primary->Restore();
            if (ddInst->primary->IsLost() == DD_OK) {
                // Primary restored: create the offscreen surface again
                wsdo->lpSurface =
                    ddInst->ddObject->CreateDDOffScreenSurface(wsdo->w, wsdo->h,
                        wsdo->depth, wsdo->transparency,
                        DDSCAPS_VIDEOMEMORY);
                if (ddInst->primary->IsLost() != DD_OK) {
                    // doubtful, but possible that it is lost again
                    // If so, delete the surface and get out of here
                    if (wsdo->lpSurface) {
                        delete wsdo->lpSurface;
                        wsdo->lpSurface = NULL;
                    }
                }
            }
        }
    }
    if (wsdo->lpSurface != NULL && (wsdo->transparency != TR_TRANSLUCENT)) {
        /**
         * 4941350: Double-check that the depth of the surface just created
         * is compatible with the depth requested.  Note that we ignore texture
         * (translucent) surfaces as those depths may differ between Java and
         * native representations.
         */
        int surfaceDepth = wsdo->lpSurface->GetSurfaceDepth();
        if (!DDSurfaceDepthsCompatible(wsdo->depth, surfaceDepth)) {
            J2dTraceLn2(J2D_TRACE_WARNING,
                        "DDCreateOffScreenSurface: Surface depth mismatch: "\
                        "intended=%d actual=%d",
                        wsdo->depth, surfaceDepth);
            DDReleaseSurfaceMemory(wsdo->lpSurface);
            wsdo->lpSurface = NULL;
        }
    }
    return (wsdo->lpSurface != NULL);
}


/**
 * Gets an attached surface, such as a back buffer, from a parent
 * surface.  Sets the lpSurface member of the wsdo supplied to
 * the attached surface.
 */
BOOL DDGetAttachedSurface(JNIEnv *env, Win32SDOps* wsdo_parent,
    Win32SDOps* wsdo)
{
    J2dTraceLn(J2D_TRACE_INFO, "DDGetAttachedSurface");
    HMONITOR hMon = (HMONITOR)wsdo_parent->device->GetMonitor();
    DDrawObjectStruct *tmpDdInstance = GetDDInstanceForDevice(hMon);

    wsdo->ddInstance = NULL; // default value in case of error
    wsdo->lpSurface = NULL; // default value in case of error

    if (!tmpDdInstance || !tmpDdInstance->valid ||
        wsdo_parent->lpSurface == NULL)
    {
        J2dTraceLn2(J2D_TRACE_WARNING,
                    "DDGetAttachedSurface: unable to get attached "\
                    "surface for wsdo=0%x wsdo_parent=0%x", wsdo, wsdo_parent);
        return FALSE;
    }
    DDrawSurface* pNew = wsdo_parent->lpSurface->GetDDAttachedSurface();
    if (pNew == NULL) {
        return FALSE;
    }
    wsdo->lpSurface = pNew;
    wsdo->ddInstance = tmpDdInstance;
    J2dTraceLn1(J2D_TRACE_VERBOSE,
                "DDGetAttachedSurface: succeeded wsdo->lpSurface=0x%x",
                wsdo->lpSurface);
    return TRUE;
}


/**
 * Destroys resources associated with a surface
 */
void DDDestroySurface(Win32SDOps *wsdo)
{
    J2dTraceLn1(J2D_TRACE_INFO, "DDDestroySurface: wsdo->lpSurface=0x%x",
                wsdo->lpSurface);

    if (!wsdo->lpSurface) {
        // null surface means it was never created; simply return
        return;
    }
    if (!wsdo->window) {
        // offscreen surface
        delete wsdo->lpSurface;
        wsdo->lpSurface = NULL;
    }
    J2dTraceLn1(J2D_TRACE_VERBOSE,
                "DDDestroySurface: ddInstance->refCount=%d",
                wsdo->ddInstance->refCount);
}

/**
 * Releases ddraw resources associated with a surface.  Note that
 * the DDrawSurface object is still valid, but the underlying
 * DirectDraw surface is released.
 */
void DDReleaseSurfaceMemory(DDrawSurface *lpSurface)
{
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDReleaseSurfaceMemory: lpSurface=0x%x", lpSurface);

    if (!lpSurface) {
        // null surface means it was never created; simply return
        return;
    }
    HRESULT ddResult = lpSurface->ReleaseSurface();
}

/*
 * This function returns whether or not surfaces should be replaced
 * in response to a WM_DISPLAYCHANGE message.  If we are a full-screen
 * application that has lost its surfaces, we do not want to replace
 * our surfaces in response to a WM_DISPLAYCHANGE.
 */
BOOL DDCanReplaceSurfaces(HWND hwnd)
{
    J2dTraceLn1(J2D_TRACE_VERBOSE, "DDCanReplaceSurfaces: hwnd=0x%x", hwnd);
    DDrawObjectStruct *tmpDdInstance = NULL;
    ddInstanceLock.Enter();
    for (int i = 0; i < currNumDevices; i++) {
        tmpDdInstance = ddInstance[i];
        if (DDINSTANCE_USABLE(tmpDdInstance)) {
            J2dTraceLn2(J2D_TRACE_VERBOSE,
                        "  ddInstance[%d]->hwndFullScreen=0x%x",
                        i, tmpDdInstance->hwndFullScreen);
            if (tmpDdInstance->hwndFullScreen != NULL &&
                tmpDdInstance->context == CONTEXT_NORMAL &&
                (tmpDdInstance->hwndFullScreen == hwnd || hwnd == NULL)) {
                ddInstanceLock.Leave();
                return FALSE;
            }
        }
    }
    ddInstanceLock.Leave();
    return TRUE;
}

/*
 * This function prints the DirectDraw error associated with
 * the given errNum
 */
void PrintDirectDrawError(DWORD errNum, char *message)
{
    char buffer[255];

    GetDDErrorString(errNum, buffer);
    printf("%s:: %s\n", message, buffer);
}


/*
 * This function prints the DirectDraw error associated with
 * the given errNum
 */
void DebugPrintDirectDrawError(DWORD errNum, char *message)
{
    char buffer[255];

    GetDDErrorString(errNum, buffer);
    J2dRlsTraceLn2(J2D_TRACE_ERROR, "%s: %s", message, buffer);
}


/*
 * This function prints the error string into the given buffer
 */
void GetDDErrorString(DWORD errNum, char *buffer)
{
    switch (errNum) {
    case DDERR_ALREADYINITIALIZED:
        sprintf(buffer, "DirectDraw Error: DDERR_ALREADYINITIALIZED");
        break;
    case DDERR_CANNOTATTACHSURFACE:
        sprintf(buffer, "DirectDraw Error: DDERR_CANNOTATTACHSURFACE");
        break;
    case DDERR_CANNOTDETACHSURFACE:
        sprintf(buffer, "DirectDraw Error: DDERR_CANNOTDETACHSURFACE");
        break;
    case DDERR_CURRENTLYNOTAVAIL:
        sprintf(buffer, "DirectDraw Error: DDERR_CURRENTLYNOTAVAIL");
        break;
    case DDERR_EXCEPTION:
        sprintf(buffer, "DirectDraw Error: DDERR_EXCEPTION");
        break;
    case DDERR_GENERIC:
        sprintf(buffer, "DirectDraw Error: DDERR_GENERIC");
        break;
    case DDERR_HEIGHTALIGN:
        sprintf(buffer, "DirectDraw Error: DDERR_HEIGHTALIGN");
        break;
    case DDERR_INCOMPATIBLEPRIMARY:
        sprintf(buffer, "DirectDraw Error: DDERR_INCOMPATIBLEPRIMARY");
        break;
    case DDERR_INVALIDCAPS:
        sprintf(buffer, "DirectDraw Error: DDERR_INVALIDCAPS");
        break;
    case DDERR_INVALIDCLIPLIST:
        sprintf(buffer, "DirectDraw Error: DDERR_INVALIDCLIPLIST");
        break;
    case DDERR_INVALIDMODE:
        sprintf(buffer, "DirectDraw Error: DDERR_INVALIDMODE");
        break;
    case DDERR_INVALIDOBJECT:
        sprintf(buffer, "DirectDraw Error: DDERR_INVALIDOBJECT");
        break;
    case DDERR_INVALIDPARAMS:
        sprintf(buffer, "DirectDraw Error: DDERR_INVALIDPARAMS");
        break;
    case DDERR_INVALIDPIXELFORMAT:
        sprintf(buffer, "DirectDraw Error: DDERR_INVALIDPIXELFORMAT");
        break;
    case DDERR_INVALIDRECT:
        sprintf(buffer, "DirectDraw Error: DDERR_INVALIDRECT");
        break;
    case DDERR_LOCKEDSURFACES:
        sprintf(buffer, "DirectDraw Error: DDERR_LOCKEDSURFACES");
        break;
    case DDERR_NO3D:
        sprintf(buffer, "DirectDraw Error: DDERR_NO3D");
        break;
    case DDERR_NOALPHAHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOALPHAHW");
        break;
    case DDERR_NOCLIPLIST:
        sprintf(buffer, "DirectDraw Error: DDERR_NOCLIPLIST");
        break;
    case DDERR_NOCOLORCONVHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOCOLORCONVHW");
        break;
    case DDERR_NOCOOPERATIVELEVELSET:
        sprintf(buffer, "DirectDraw Error: DDERR_NOCOOPERATIVELEVELSET");
        break;
    case DDERR_NOCOLORKEY:
        sprintf(buffer, "DirectDraw Error: DDERR_NOCOLORKEY");
        break;
    case DDERR_NOCOLORKEYHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOCOLORKEYHW");
        break;
    case DDERR_NODIRECTDRAWSUPPORT:
        sprintf(buffer, "DirectDraw Error: DDERR_NODIRECTDRAWSUPPORT");
        break;
    case DDERR_NOEXCLUSIVEMODE:
        sprintf(buffer, "DirectDraw Error: DDERR_NOEXCLUSIVEMODE");
        break;
    case DDERR_NOFLIPHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOFLIPHW");
        break;
    case DDERR_NOGDI:
        sprintf(buffer, "DirectDraw Error: DDERR_NOGDI");
        break;
    case DDERR_NOMIRRORHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOMIRRORHW");
        break;
    case DDERR_NOTFOUND:
        sprintf(buffer, "DirectDraw Error: DDERR_NOTFOUND");
        break;
    case DDERR_NOOVERLAYHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOOVERLAYHW");
        break;
    case DDERR_NORASTEROPHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NORASTEROPHW");
        break;
    case DDERR_NOROTATIONHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOROTATIONHW");
        break;
    case DDERR_NOSTRETCHHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOSTRETCHHW");
        break;
    case DDERR_NOT4BITCOLOR:
        sprintf(buffer, "DirectDraw Error: DDERR_NOT4BITCOLOR");
        break;
    case DDERR_NOT4BITCOLORINDEX:
        sprintf(buffer, "DirectDraw Error: DDERR_NOT4BITCOLORINDEX");
        break;
    case DDERR_NOT8BITCOLOR:
        sprintf(buffer, "DirectDraw Error: DDERR_NOT8BITCOLOR");
        break;
    case DDERR_NOTEXTUREHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOTEXTUREHW");
        break;
    case DDERR_NOVSYNCHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOVSYNCHW");
        break;
    case DDERR_NOZBUFFERHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOZBUFFERHW");
        break;
    case DDERR_NOZOVERLAYHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOZOVERLAYHW");
        break;
    case DDERR_OUTOFCAPS:
        sprintf(buffer, "DirectDraw Error: DDERR_OUTOFCAPS");
        break;
    case DDERR_OUTOFMEMORY:
        sprintf(buffer, "DirectDraw Error: DDERR_OUTOFMEMORY");
        break;
    case DDERR_OUTOFVIDEOMEMORY:
        sprintf(buffer, "DirectDraw Error: DDERR_OUTOFVIDEOMEMORY");
        break;
    case DDERR_OVERLAYCANTCLIP:
        sprintf(buffer, "DirectDraw Error: DDERR_OVERLAYCANTCLIP");
        break;
    case DDERR_OVERLAYCOLORKEYONLYONEACTIVE:
        sprintf(buffer, "DirectDraw Error: DDERR_OVERLAYCOLORKEYONLYONEACTIVE");
        break;
    case DDERR_PALETTEBUSY:
        sprintf(buffer, "DirectDraw Error: DDERR_PALETTEBUSY");
        break;
    case DDERR_COLORKEYNOTSET:
        sprintf(buffer, "DirectDraw Error: DDERR_COLORKEYNOTSET");
        break;
    case DDERR_SURFACEALREADYATTACHED:
        sprintf(buffer, "DirectDraw Error: DDERR_SURFACEALREADYATTACHED");
        break;
    case DDERR_SURFACEALREADYDEPENDENT:
        sprintf(buffer, "DirectDraw Error: DDERR_SURFACEALREADYDEPENDENT");
        break;
    case DDERR_SURFACEBUSY:
        sprintf(buffer, "DirectDraw Error: DDERR_SURFACEBUSY");
        break;
    case DDERR_CANTLOCKSURFACE:
        sprintf(buffer, "DirectDraw Error: DDERR_CANTLOCKSURFACE");
        break;
    case DDERR_SURFACEISOBSCURED:
        sprintf(buffer, "DirectDraw Error: DDERR_SURFACEISOBSCURED");
        break;
    case DDERR_SURFACELOST:
        sprintf(buffer, "DirectDraw Error: DDERR_SURFACELOST");
        break;
    case DDERR_SURFACENOTATTACHED:
        sprintf(buffer, "DirectDraw Error: DDERR_SURFACENOTATTACHED");
        break;
    case DDERR_TOOBIGHEIGHT:
        sprintf(buffer, "DirectDraw Error: DDERR_TOOBIGHEIGHT");
        break;
    case DDERR_TOOBIGSIZE:
        sprintf(buffer, "DirectDraw Error: DDERR_TOOBIGSIZE");
        break;
    case DDERR_TOOBIGWIDTH:
        sprintf(buffer, "DirectDraw Error: DDERR_TOOBIGWIDTH");
        break;
    case DDERR_UNSUPPORTED:
        sprintf(buffer, "DirectDraw Error: DDERR_UNSUPPORTED");
        break;
    case DDERR_UNSUPPORTEDFORMAT:
        sprintf(buffer, "DirectDraw Error: DDERR_UNSUPPORTEDFORMAT");
        break;
    case DDERR_UNSUPPORTEDMASK:
        sprintf(buffer, "DirectDraw Error: DDERR_UNSUPPORTEDMASK");
        break;
    case DDERR_VERTICALBLANKINPROGRESS:
        sprintf(buffer, "DirectDraw Error: DDERR_VERTICALBLANKINPROGRESS");
        break;
    case DDERR_WASSTILLDRAWING:
        sprintf(buffer, "DirectDraw Error: DDERR_WASSTILLDRAWING");
        break;
    case DDERR_XALIGN:
        sprintf(buffer, "DirectDraw Error: DDERR_XALIGN");
        break;
    case DDERR_INVALIDDIRECTDRAWGUID:
        sprintf(buffer, "DirectDraw Error: DDERR_INVALIDDIRECTDRAWGUID");
        break;
    case DDERR_DIRECTDRAWALREADYCREATED:
        sprintf(buffer, "DirectDraw Error: DDERR_DIRECTDRAWALREADYCREATED");
        break;
    case DDERR_NODIRECTDRAWHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NODIRECTDRAWHW");
        break;
    case DDERR_PRIMARYSURFACEALREADYEXISTS:
        sprintf(buffer, "DirectDraw Error: DDERR_PRIMARYSURFACEALREADYEXISTS");
        break;
    case DDERR_NOEMULATION:
        sprintf(buffer, "DirectDraw Error: DDERR_NOEMULATION");
        break;
    case DDERR_REGIONTOOSMALL:
        sprintf(buffer, "DirectDraw Error: DDERR_REGIONTOOSMALL");
        break;
    case DDERR_CLIPPERISUSINGHWND:
        sprintf(buffer, "DirectDraw Error: DDERR_CLIPPERISUSINGHWND");
        break;
    case DDERR_NOCLIPPERATTACHED:
        sprintf(buffer, "DirectDraw Error: DDERR_NOCLIPPERATTACHED");
        break;
    case DDERR_NOHWND:
        sprintf(buffer, "DirectDraw Error: DDERR_NOHWND");
        break;
    case DDERR_HWNDSUBCLASSED:
        sprintf(buffer, "DirectDraw Error: DDERR_HWNDSUBCLASSED");
        break;
    case DDERR_HWNDALREADYSET:
        sprintf(buffer, "DirectDraw Error: DDERR_HWNDALREADYSET");
        break;
    case DDERR_NOPALETTEATTACHED:
        sprintf(buffer, "DirectDraw Error: DDERR_NOPALETTEATTACHED");
        break;
    case DDERR_NOPALETTEHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOPALETTEHW");
        break;
    case DDERR_BLTFASTCANTCLIP:
        sprintf(buffer, "DirectDraw Error: DDERR_BLTFASTCANTCLIP");
        break;
    case DDERR_NOBLTHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOBLTHW");
        break;
    case DDERR_NODDROPSHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NODDROPSHW");
        break;
    case DDERR_OVERLAYNOTVISIBLE:
        sprintf(buffer, "DirectDraw Error: DDERR_OVERLAYNOTVISIBLE");
        break;
    case DDERR_NOOVERLAYDEST:
        sprintf(buffer, "DirectDraw Error: DDERR_NOOVERLAYDEST");
        break;
    case DDERR_INVALIDPOSITION:
        sprintf(buffer, "DirectDraw Error: DDERR_INVALIDPOSITION");
        break;
    case DDERR_NOTAOVERLAYSURFACE:
        sprintf(buffer, "DirectDraw Error: DDERR_NOTAOVERLAYSURFACE");
        break;
    case DDERR_EXCLUSIVEMODEALREADYSET:
        sprintf(buffer, "DirectDraw Error: DDERR_EXCLUSIVEMODEALREADYSET");
        break;
    case DDERR_NOTFLIPPABLE:
        sprintf(buffer, "DirectDraw Error: DDERR_NOTFLIPPABLE");
        break;
    case DDERR_CANTDUPLICATE:
        sprintf(buffer, "DirectDraw Error: DDERR_CANTDUPLICATE");
        break;
    case DDERR_NOTLOCKED:
        sprintf(buffer, "DirectDraw Error: DDERR_NOTLOCKED");
        break;
    case DDERR_CANTCREATEDC:
        sprintf(buffer, "DirectDraw Error: DDERR_CANTCREATEDC");
        break;
    case DDERR_NODC:
        sprintf(buffer, "DirectDraw Error: DDERR_NODC");
        break;
    case DDERR_WRONGMODE:
        sprintf(buffer, "DirectDraw Error: DDERR_WRONGMODE");
        break;
    case DDERR_IMPLICITLYCREATED:
        sprintf(buffer, "DirectDraw Error: DDERR_IMPLICITLYCREATED");
        break;
    case DDERR_NOTPALETTIZED:
        sprintf(buffer, "DirectDraw Error: DDERR_NOTPALETTIZED");
        break;
    case DDERR_UNSUPPORTEDMODE:
        sprintf(buffer, "DirectDraw Error: DDERR_UNSUPPORTEDMODE");
        break;
    case DDERR_NOMIPMAPHW:
        sprintf(buffer, "DirectDraw Error: DDERR_NOMIPMAPHW");
        break;
    case DDERR_INVALIDSURFACETYPE:
        sprintf(buffer, "DirectDraw Error: DDERR_INVALIDSURFACETYPE");
        break;
    case DDERR_DCALREADYCREATED:
        sprintf(buffer, "DirectDraw Error: DDERR_DCALREADYCREATED");
        break;
    case DDERR_CANTPAGELOCK:
        sprintf(buffer, "DirectDraw Error: DDERR_CANTPAGELOCK");
        break;
    case DDERR_CANTPAGEUNLOCK:
        sprintf(buffer, "DirectDraw Error: DDERR_CANTPAGEUNLOCK");
        break;
    case DDERR_NOTPAGELOCKED:
        sprintf(buffer, "DirectDraw Error: DDERR_NOTPAGELOCKED");
        break;
    case D3DERR_INVALID_DEVICE:
        sprintf(buffer, "Direct3D Error: D3DERR_INVALID_DEVICE");
        break;
    case D3DERR_INITFAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_INITFAILED");
        break;
    case D3DERR_DEVICEAGGREGATED:
        sprintf(buffer, "Direct3D Error: D3DERR_DEVICEAGGREGATED");
        break;
    case D3DERR_EXECUTE_CREATE_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_EXECUTE_CREATE_FAILED");
        break;
    case D3DERR_EXECUTE_DESTROY_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_EXECUTE_DESTROY_FAILED");
        break;
    case D3DERR_EXECUTE_LOCK_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_EXECUTE_LOCK_FAILED");
        break;
    case D3DERR_EXECUTE_UNLOCK_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_EXECUTE_UNLOCK_FAILED");
        break;
    case D3DERR_EXECUTE_LOCKED:
        sprintf(buffer, "Direct3D Error: D3DERR_EXECUTE_LOCKED");
        break;
    case D3DERR_EXECUTE_NOT_LOCKED:
        sprintf(buffer, "Direct3D Error: D3DERR_EXECUTE_NOT_LOCKED");
        break;
    case D3DERR_EXECUTE_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_EXECUTE_FAILED");
        break;
    case D3DERR_EXECUTE_CLIPPED_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_EXECUTE_CLIPPED_FAILED");
        break;
    case D3DERR_TEXTURE_NO_SUPPORT:
        sprintf(buffer, "Direct3D Error: D3DERR_TEXTURE_NO_SUPPORT");
        break;
    case D3DERR_TEXTURE_CREATE_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_TEXTURE_CREATE_FAILED");
        break;
    case D3DERR_TEXTURE_DESTROY_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_TEXTURE_DESTROY_FAILED");
        break;
    case D3DERR_TEXTURE_LOCK_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_TEXTURE_LOCK_FAILED");
        break;
    case D3DERR_TEXTURE_UNLOCK_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_TEXTURE_UNLOCK_FAILED");
        break;
    case D3DERR_TEXTURE_LOAD_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_TEXTURE_LOAD_FAILED");
        break;
    case D3DERR_TEXTURE_SWAP_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_TEXTURE_SWAP_FAILED");
        break;
    case D3DERR_TEXTURE_LOCKED:
        sprintf(buffer, "Direct3D Error: D3DERR_TEXTURE_LOCKED");
        break;
    case D3DERR_TEXTURE_NOT_LOCKED:
        sprintf(buffer, "Direct3D Error: D3DERR_TEXTURE_NOT_LOCKED");
        break;
    case D3DERR_TEXTURE_GETSURF_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_TEXTURE_GETSURF_FAILED");
        break;
    case D3DERR_MATRIX_CREATE_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_MATRIX_CREATE_FAILED");
        break;
    case D3DERR_MATRIX_DESTROY_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_MATRIX_DESTROY_FAILED");
        break;
    case D3DERR_MATRIX_SETDATA_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_MATRIX_SETDATA_FAILED");
        break;
    case D3DERR_MATRIX_GETDATA_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_MATRIX_GETDATA_FAILED");
        break;
    case D3DERR_SETVIEWPORTDATA_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_SETVIEWPORTDATA_FAILED");
        break;
    case D3DERR_INVALIDCURRENTVIEWPORT:
        sprintf(buffer, "Direct3D Error: D3DERR_INVALIDCURRENTVIEWPORT");
        break;
    case D3DERR_INVALIDPRIMITIVETYPE:
        sprintf(buffer, "Direct3D Error: D3DERR_INVALIDPRIMITIVETYPE");
        break;
    case D3DERR_INVALIDVERTEXTYPE:
        sprintf(buffer, "Direct3D Error: D3DERR_INVALIDVERTEXTYPE");
        break;
    case D3DERR_TEXTURE_BADSIZE:
        sprintf(buffer, "Direct3D Error: D3DERR_TEXTURE_BADSIZE");
        break;
    case D3DERR_INVALIDRAMPTEXTURE:
        sprintf(buffer, "Direct3D Error: D3DERR_INVALIDRAMPTEXTURE");
        break;
    case D3DERR_MATERIAL_CREATE_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_MATERIAL_CREATE_FAILED");
        break;
    case D3DERR_MATERIAL_DESTROY_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_MATERIAL_DESTROY_FAILED");
        break;
    case D3DERR_MATERIAL_SETDATA_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_MATERIAL_SETDATA_FAILED");
        break;
    case D3DERR_MATERIAL_GETDATA_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_MATERIAL_GETDATA_FAILED");
        break;
    case D3DERR_INVALIDPALETTE:
        sprintf(buffer, "Direct3D Error: D3DERR_INVALIDPALETTE");
        break;
    case D3DERR_ZBUFF_NEEDS_SYSTEMMEMORY:
        sprintf(buffer, "Direct3D Error: D3DERR_ZBUFF_NEEDS_SYSTEMMEMORY");
        break;
    case D3DERR_ZBUFF_NEEDS_VIDEOMEMORY:
        sprintf(buffer, "Direct3D Error: D3DERR_ZBUFF_NEEDS_VIDEOMEMORY");
        break;
    case D3DERR_SURFACENOTINVIDMEM:
        sprintf(buffer, "Direct3D Error: D3DERR_SURFACENOTINVIDMEM");
        break;
    case D3DERR_LIGHT_SET_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_LIGHT_SET_FAILED");
        break;
    case D3DERR_LIGHTHASVIEWPORT:
        sprintf(buffer, "Direct3D Error: D3DERR_LIGHTHASVIEWPORT");
        break;
    case D3DERR_LIGHTNOTINTHISVIEWPORT:
        sprintf(buffer, "Direct3D Error: D3DERR_LIGHTNOTINTHISVIEWPORT");
        break;
    case D3DERR_SCENE_IN_SCENE:
        sprintf(buffer, "Direct3D Error: D3DERR_SCENE_IN_SCENE");
        break;
    case D3DERR_SCENE_NOT_IN_SCENE:
        sprintf(buffer, "Direct3D Error: D3DERR_SCENE_NOT_IN_SCENE");
        break;
    case D3DERR_SCENE_BEGIN_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_SCENE_BEGIN_FAILED");
        break;
    case D3DERR_SCENE_END_FAILED:
        sprintf(buffer, "Direct3D Error: D3DERR_SCENE_END_FAILED");
        break;
    case D3DERR_INBEGIN:
        sprintf(buffer, "Direct3D Error: D3DERR_INBEGIN");
        break;
    case D3DERR_NOTINBEGIN:
        sprintf(buffer, "Direct3D Error: D3DERR_NOTINBEGIN");
        break;
    case D3DERR_NOVIEWPORTS:
        sprintf(buffer, "Direct3D Error: D3DERR_NOVIEWPORTS");
        break;
    case D3DERR_VIEWPORTDATANOTSET:
        sprintf(buffer, "Direct3D Error: D3DERR_VIEWPORTDATANOTSET");
        break;
    case D3DERR_VIEWPORTHASNODEVICE:
        sprintf(buffer, "Direct3D Error: D3DERR_VIEWPORTHASNODEVICE");
        break;
    case D3DERR_NOCURRENTVIEWPORT:
        sprintf(buffer, "Direct3D Error: D3DERR_NOCURRENTVIEWPORT");
        break;
    default:
        sprintf(buffer, "DirectX Error Unknown 0x%x", errNum);
        break;
    }

}
