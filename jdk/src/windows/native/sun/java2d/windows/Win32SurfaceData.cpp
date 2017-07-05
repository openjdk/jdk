/*
 * Copyright 1999-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "sun_java2d_windows_Win32SurfaceData.h"

#include "Win32SurfaceData.h"
#include "GraphicsPrimitiveMgr.h"
#include "Region.h"
#include "Disposer.h"
#include "WindowsFlags.h"
#include "awt_Component.h"
#include "ddrawUtils.h"
#include "awt_Palette.h"
#include "awt_Win32GraphicsDevice.h"
#include "gdefs.h"
#include "D3DContext.h"
#include "Trace.h"
#include "Devices.h"

#include "jni_util.h"

static LockFunc Win32SD_Lock;
static GetRasInfoFunc Win32SD_GetRasInfo;
static UnlockFunc Win32SD_Unlock;
static DisposeFunc Win32SD_Dispose;
static SetupFunc Win32SD_Setup;
static GetDCFunc Win32SD_GetDC;
static ReleaseDCFunc Win32SD_ReleaseDC;
static InvalidateSDFunc Win32SD_InvalidateSD;

static HBRUSH   nullbrush;
static HPEN     nullpen;

static jclass xorCompClass;

static jboolean beingShutdown = JNI_FALSE;
extern BOOL isFullScreen; // TODO/REMIND : Multi-montior fullscreen support
extern HWND hwndFullScreen;
extern int gBackBufferCount;
extern BOOL ddrawParamsChanged;
extern int currNumDevices;
extern CriticalSection windowMoveLock;

extern "C"
{
GeneralDisposeFunc DisposeThreadGraphicsInfo;
jobject JNI_GetCurrentThread(JNIEnv *env);
int threadInfoIndex = TLS_OUT_OF_INDEXES;

static jclass threadClass = NULL;
static jmethodID currentThreadMethodID = NULL;

void SetupThreadGraphicsInfo(JNIEnv *env, Win32SDOps *wsdo) {
    J2dTraceLn(J2D_TRACE_INFO, "SetupThreadGraphicsInfo");

    // REMIND: handle error when creation fails
    ThreadGraphicsInfo *info =
        (ThreadGraphicsInfo*)TlsGetValue(threadInfoIndex);
    if (info == NULL) {
        info = new ThreadGraphicsInfo;
        ZeroMemory(info, sizeof(ThreadGraphicsInfo));
        TlsSetValue(threadInfoIndex, (LPVOID)info);
        J2dTraceLn2(J2D_TRACE_VERBOSE,
                    "  current batch limit for for thread 0x%x is %d",
                     GetCurrentThreadId(), ::GdiGetBatchLimit());
        J2dTraceLn(J2D_TRACE_VERBOSE, "  setting to the limit to 1");
        // Fix for bug 4374079
        ::GdiSetBatchLimit(1);

        Disposer_AddRecord(env, JNI_GetCurrentThread(env),
                           DisposeThreadGraphicsInfo,
                           ptr_to_jlong(info));
    }

    HDC oldhDC = info->hDC;
    // the hDC is NULL for offscreen surfaces - we don't store it
    // in TLS as it must be created new every time.
    if (((oldhDC == NULL) && wsdo->window != NULL) || (info->wsdo != wsdo)) {

        // Init graphics state, either because this is our first time
        // using it in this thread or because this thread is now
        // dealing with a different window than it was last time.

        // Release cached DC. We use deferred DC releasing mechanism because
        // the DC is associated with cached wsdo and component peer,
        // which may've been disposed by this time, and we have
        // no means of checking against it.
        if (oldhDC != NULL) {
            MoveDCToPassiveList(oldhDC);
            info->hDC = NULL;
        }

        if (wsdo->window != NULL){
            HDC hDC;
            // This is a window surface
            // First, init the HDC object
            AwtComponent *comp = Win32SurfaceData_GetComp(env, wsdo);
            if (comp == NULL) {
                return;
            }
            hDC = comp->GetDCFromComponent();
            if (hDC != NULL) {
                ::SelectObject(hDC, nullbrush);
                ::SelectObject(hDC, nullpen);
                ::SelectClipRgn(hDC, (HRGN) NULL);
                ::SetROP2(hDC, R2_COPYPEN);
                wsdo->device->SelectPalette(hDC);
                // Note that on NT4 we don't need to do a realize here: the
                // palette-sharing takes care of color issues for us.  But
                // on win98 if we don't realize a DC's palette, that
                // palette does not appear to have correct access to the
                // logical->system mapping.
                wsdo->device->RealizePalette(hDC);

                // Second, init the rest of the graphics state
                ::GetClientRect(wsdo->window, &info->bounds);
                // Make window-relative from client-relative
                ::OffsetRect(&info->bounds, wsdo->insets.left, wsdo->insets.top);
                //Likewise, translate GDI calls from client-relative to window-relative
                ::OffsetViewportOrgEx(hDC, -wsdo->insets.left, -wsdo->insets.top, NULL);
            }

            // Finally, set these new values in the info for this thread
            info->hDC = hDC;
        }

        // cached brush and pen are not associated with any DC, and can be
        // reused, but have to set type to 0 to indicate that no pen/brush
        // were set to the new hdc
        info->type = 0;

        if (info->clip != NULL) {
            env->DeleteWeakGlobalRef(info->clip);
        }
        info->clip = NULL;

        if (info->comp != NULL) {
            env->DeleteWeakGlobalRef(info->comp);
        }
        info->comp = NULL;

        info->xorcolor = 0;
        info->patrop = PATCOPY;

        info->wsdo = wsdo;
    }
}

/**
 * Releases native data stored in Thread local storage.
 * Called by the Disposer when the associated thread dies.
 */
void DisposeThreadGraphicsInfo(JNIEnv *env, jlong tgi) {
    J2dTraceLn(J2D_TRACE_INFO, "DisposeThreadGraphicsInfo");
    ThreadGraphicsInfo *info = (ThreadGraphicsInfo*)jlong_to_ptr(tgi);
    if (info != NULL) {
        if (info->hDC != NULL) {
            // move the DC from the active dcs list to
            // the passive dc list to be released later
            MoveDCToPassiveList(info->hDC);
        }

        if (info->clip != NULL) {
            env->DeleteWeakGlobalRef(info->clip);
        }
        if (info->comp != NULL) {
            env->DeleteWeakGlobalRef(info->comp);
        }

        if (info->brush != NULL) {
            info->brush->Release();
        }
        if (info->pen != NULL) {
            info->pen->Release();
        }

        delete info;
    }
}

/**
 * Returns current Thread object.
 */
jobject
JNI_GetCurrentThread(JNIEnv *env) {
    return env->CallStaticObjectMethod(threadClass, currentThreadMethodID);
} /* JNI_GetCurrentThread() */

/**
 * Return the data associated with this thread.
 * NOTE: This function assumes that the SetupThreadGraphicsInfo()
 * function has already been called for this situation (thread,
 * window, etc.), so we can assume that the thread info contains
 * a valid hDC.  This should usually be the case since Win32SD_Setup
 * is called as part of the GetOps() process.
 */
ThreadGraphicsInfo *GetThreadGraphicsInfo(JNIEnv *env,
                                          Win32SDOps *wsdo) {
    return (ThreadGraphicsInfo*)TlsGetValue(threadInfoIndex);
}

__inline HDC GetThreadDC(JNIEnv *env, Win32SDOps *wsdo) {
    ThreadGraphicsInfo *info =
        (ThreadGraphicsInfo *)GetThreadGraphicsInfo(env, wsdo);
    if (!info) {
        return (HDC) NULL;
    }
    return info->hDC;
}

} // extern "C"

/**
 * This source file contains support code for loops using the
 * SurfaceData interface to talk to a Win32 drawable from native
 * code.
 */

static BOOL Win32SD_CheckMonitorArea(Win32SDOps *wsdo,
                                     SurfaceDataBounds *bounds,
                                     HDC hDC)
{
    HWND hW = wsdo->window;
    BOOL retCode = TRUE;

    J2dTraceLn(J2D_TRACE_INFO, "Win32SD_CheckMonitorArea");
    int numScreens;
    {
        Devices::InstanceAccess devices;
        numScreens = devices->GetNumDevices();
    }
    if( numScreens > 1 ) {

        MONITOR_INFO *miInfo;
        RECT rSect ={0,0,0,0};
        RECT rView ={bounds->x1, bounds->y1, bounds->x2, bounds->y2};
        retCode = FALSE;

        miInfo = wsdo->device->GetMonitorInfo();

        POINT ptOrig = {0, 0};
        ::ClientToScreen(hW, &ptOrig);
        ::OffsetRect(&rView,
            (ptOrig.x), (ptOrig.y));

        ::IntersectRect(&rSect,&rView,&(miInfo->rMonitor));

        if( FALSE == ::IsRectEmpty(&rSect) ) {
            if( TRUE == ::EqualRect(&rSect,&rView) ) {
                retCode = TRUE;
            }
        }
    }
    return retCode;
}

extern "C" {

void
initThreadInfoIndex()
{
    if (threadInfoIndex == TLS_OUT_OF_INDEXES) {
        threadInfoIndex = TlsAlloc();
    }
}

/*
 * Class:     sun_java2d_windows_Win32SurfaceData
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_Win32SurfaceData_initIDs(JNIEnv *env, jclass wsd,
                                                 jclass XORComp)
{
    jclass tc;
    J2dTraceLn(J2D_TRACE_INFO, "Win32SurfaceData_initIDs");
    nullbrush = (HBRUSH) ::GetStockObject(NULL_BRUSH);
    nullpen = (HPEN) ::GetStockObject(NULL_PEN);

    initThreadInfoIndex();

    xorCompClass = (jclass)env->NewGlobalRef(XORComp);

    tc = env->FindClass("java/lang/Thread");
    DASSERT(tc != NULL);
    threadClass = (jclass)env->NewGlobalRef(tc);
    DASSERT(threadClass != NULL);
    currentThreadMethodID =
        env->GetStaticMethodID(threadClass,
                               "currentThread",  "()Ljava/lang/Thread;");
    DASSERT(currentThreadMethodID != NULL);
}

void Win32SD_RestoreSurface(JNIEnv *env, Win32SDOps *wsdo)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32SD_RestoreSurface: restoring primary");

    if (!DDRestoreSurface(wsdo)) {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "Win32SD_RestoreSurface: problems restoring primary");
    } else {
        // Force repaint of the window when we restore the surface
        ::InvalidateRect(wsdo->window, NULL, FALSE);
    }
}


/*
 * Class:     sun_java2d_windows_Win32SurfaceData
 * Method:    initOps
 * Signature: (Ljava/lang/Object;IIIIII)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_Win32SurfaceData_initOps(JNIEnv *env, jobject wsd,
                                                 jobject peer, jint depth,
                                                 jint redMask, jint greenMask,
                                                 jint blueMask, jint backBufferCount,
                                                 jint screen)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32SurfaceData_initOps");
    Win32SDOps *wsdo = (Win32SDOps *)SurfaceData_InitOps(env, wsd, sizeof(Win32SDOps));
    wsdo->sdOps.Lock = Win32SD_Lock;
    wsdo->sdOps.GetRasInfo = Win32SD_GetRasInfo;
    wsdo->sdOps.Unlock = Win32SD_Unlock;
    wsdo->sdOps.Dispose = Win32SD_Dispose;
    wsdo->sdOps.Setup = Win32SD_Setup;
    wsdo->RestoreSurface = Win32SD_RestoreSurface;
    wsdo->GetDC = Win32SD_GetDC;
    wsdo->ReleaseDC = Win32SD_ReleaseDC;
    wsdo->InvalidateSD = Win32SD_InvalidateSD;
    wsdo->invalid = JNI_FALSE;
    wsdo->lockType = WIN32SD_LOCK_UNLOCKED;
    wsdo->peer = env->NewWeakGlobalRef(peer);
    wsdo->depth = depth;
    wsdo->pixelMasks[0] = redMask;
    wsdo->pixelMasks[1] = greenMask;
    wsdo->pixelMasks[2] = blueMask;
    // Init the DIB pixelStride and pixel masks according to
    // the pixel depth. In the 8-bit case, there are no
    // masks as a palette DIB is used instead. Likewise
    // in the 24-bit case, Windows doesn't expect the masks
    switch (depth) {
        case 8:
            wsdo->pixelStride = 1;
            break;
        case 15: //555
            wsdo->pixelStride = 2;
            break;
        case 16: //565
            wsdo->pixelStride = 2;
            break;
        case 24:
            wsdo->pixelStride = 3;
            break;
        case 32: //888
            wsdo->pixelStride = 4;
            break;
    }
    // Win32SurfaceData_GetWindow will throw NullPointerException
    // if wsdo->window is NULL
    wsdo->window = Win32SurfaceData_GetWindow(env, wsdo);
    J2dTraceLn2(J2D_TRACE_VERBOSE, "  wsdo=0x%x wsdo->window=0x%x",
                wsdo, wsdo->window);

    wsdo->backBufferCount = backBufferCount;
    {
        Devices::InstanceAccess devices;
        wsdo->device = devices->GetDeviceReference(screen, FALSE);
    }
    if (wsdo->device == NULL ||
        !DDSurfaceDepthsCompatible(depth, wsdo->device->GetBitDepth()))
    {
        if (wsdo->device != NULL) {
            J2dTraceLn2(J2D_TRACE_WARNING,
                        "Win32SurfaceData_initOps: Surface depth mismatch: "\
                        "wsdo->depth=%d device depth=%d. Surface invalidated.",
                        wsdo->depth, wsdo->device->GetBitDepth());
        } else {
            J2dTraceLn1(J2D_TRACE_WARNING,
                        "Win32SurfaceData_initOps: Incorrect "\
                        "screen number (screen=%d). Surface invalidated.",
                        screen);
        }

        wsdo->invalid = JNI_TRUE;
        wsdo->lpSurface = NULL;
        wsdo->ddInstance = NULL;

    } else if (wsdo->window != NULL &&
               DDCanCreatePrimary((HMONITOR)wsdo->device->GetMonitor()))
    {
        // Create the surface on the windows event thread to avoid
        // problems with fullscreen window creation and manipulation
        if (!((BOOL)::SendMessage(wsdo->window, WM_AWT_DD_CREATE_SURFACE,
                                  (WPARAM)wsdo, NULL)))
        {
            // We only get here if the device can handle ddraw but
            // cannot create a primary.  This can happen if, for example,
            // another application has exclusive access to the device.
            // In this situation, we null-out the ddraw-related pointers
            // which ends up noop'ing ddraw operations on this surface
            // (our window is effectively not visible in this situation,
            // so noops are fine).
            wsdo->lpSurface = NULL;
            wsdo->ddInstance = NULL;
        }
    }
    else {
        wsdo->lpSurface = NULL;
        wsdo->ddInstance = NULL;
    }
    wsdo->surfaceLock = new CriticalSection();
    wsdo->transparency = TR_OPAQUE;
    wsdo->bitmap = NULL;
    wsdo->bmdc = NULL;
    wsdo->bmCopyToScreen = FALSE;
    wsdo->gdiOpPending = FALSE;
}

JNIEXPORT Win32SDOps * JNICALL
Win32SurfaceData_GetOps(JNIEnv *env, jobject sData)
{
    SurfaceDataOps *ops = SurfaceData_GetOps(env, sData);
    // REMIND: There was originally a condition check here to make sure
    // that we were really dealing with a Win32SurfaceData object, but
    // it did not allow for the existence of other win32-accelerated
    // surface data objects (e.g., Win32OffScreenSurfaceData).  I've
    // removed the check for now, but we should replace it with another,
    // more general check against Win32-related surfaces.
    return (Win32SDOps *) ops;
}

JNIEXPORT Win32SDOps * JNICALL
Win32SurfaceData_GetOpsNoSetup(JNIEnv *env, jobject sData)
{
    // use the 'no setup' version of GetOps
    SurfaceDataOps *ops = SurfaceData_GetOpsNoSetup(env, sData);
    return (Win32SDOps *) ops;
}

JNIEXPORT AwtComponent * JNICALL
Win32SurfaceData_GetComp(JNIEnv *env, Win32SDOps *wsdo)
{
    PDATA pData;
    jobject localObj = env->NewLocalRef(wsdo->peer);

    if (localObj == NULL || (pData = JNI_GET_PDATA(localObj)) == NULL) {
        J2dTraceLn1(J2D_TRACE_WARNING,
                    "Win32SurfaceData_GetComp: Null pData? pData=0x%x",
                    pData);
        if (beingShutdown == JNI_TRUE) {
            wsdo->invalid = JNI_TRUE;
            return (AwtComponent *) NULL;
        }
        try {
            AwtToolkit::GetInstance().VerifyActive();
        } catch (awt_toolkit_shutdown&) {
            beingShutdown = JNI_TRUE;
            wsdo->invalid = JNI_TRUE;
            return (AwtComponent *) NULL;
        }
        if (wsdo->invalid == JNI_TRUE) {
            SurfaceData_ThrowInvalidPipeException(env,
                "Win32SurfaceData: bounds changed");
        } else {
            JNU_ThrowNullPointerException(env, "component argument pData");
        }
        return (AwtComponent *) NULL;
    }
    return static_cast<AwtComponent*>(pData);
}

JNIEXPORT HWND JNICALL
Win32SurfaceData_GetWindow(JNIEnv *env, Win32SDOps *wsdo)
{
    HWND window = wsdo->window;

    if (window == (HWND) NULL) {
        AwtComponent *comp = Win32SurfaceData_GetComp(env, wsdo);
        if (comp == NULL) {
            J2dTraceLn(J2D_TRACE_WARNING,
                   "Win32SurfaceData_GetWindow: null component");
            return (HWND) NULL;
        }
        comp->GetInsets(&wsdo->insets);
        window = comp->GetHWnd();
        if (::IsWindow(window) == FALSE) {
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "Win32SurfaceData_GetWindow: disposed component");
            JNU_ThrowNullPointerException(env, "disposed component");
            return (HWND) NULL;
        }
        wsdo->window = window;
    }

    return window;
}

} /* extern "C" */

static jboolean Win32SD_SimpleClip(JNIEnv *env, Win32SDOps *wsdo,
                                   SurfaceDataBounds *bounds,
                                   HDC hDC)
{
    RECT rClip;

    J2dTraceLn(J2D_TRACE_INFO, "Win32SD_SimpleClip");
    if (hDC == NULL) {
        return JNI_FALSE;
    }

    int nComplexity = ::GetClipBox(hDC, &rClip);

    switch (nComplexity) {
    case COMPLEXREGION:
        {
            J2dTraceLn(J2D_TRACE_VERBOSE,
                       "  complex clipping region");
            // if complex user/system clip, more detailed testing required
            // check to see if the view itself has a complex clip.
            // ::GetClipBox is only API which returns overlapped window status
            // so we set the rView as our clip, and then see if resulting
            // clip is complex.
            // Only other way to figure this out would be to walk the
            // overlapping windows (no API to get the actual visible clip
            // list).  Then we'd still have to merge that info with the
            // clip region for the dc (if it exists).
            // REMIND: we can cache the CreateRectRgnIndirect result,
            // and only override with ::SetRectRgn

            // First, create a region handle (need existing HRGN for
            // the following call).
            HRGN rgnSave = ::CreateRectRgn(0, 0, 0, 0);
            int  clipStatus = ::GetClipRgn(hDC, rgnSave);
            if (-1 == clipStatus) {
                J2dTraceLn(J2D_TRACE_WARNING,
                           "Win32SD_SimpleClip: failed due to clip status");
                ::DeleteObject(rgnSave);
                return JNI_FALSE;
            }
            HRGN rgnBounds = ::CreateRectRgn(
                bounds->x1 - wsdo->insets.left,
                bounds->y1 - wsdo->insets.top,
                bounds->x2 - wsdo->insets.left,
                bounds->y2 - wsdo->insets.top);
            ::SelectClipRgn(hDC, rgnBounds);
            nComplexity = ::GetClipBox(hDC, &rClip);
            ::SelectClipRgn(hDC, clipStatus? rgnSave: NULL);
            ::DeleteObject(rgnSave);
            ::DeleteObject(rgnBounds);

            // Now, test the new clip box.  If it's still not a
            // SIMPLE region, then our bounds must intersect part of
            // the clipping article
            if (SIMPLEREGION != nComplexity) {
                J2dTraceLn(J2D_TRACE_WARNING,
                           "Win32SD_SimpleClip: failed due to complexity");
                return JNI_FALSE;
            }
        }
        // NOTE: No break here - we want to fall through into the
        // SIMPLE case, adjust our bounds by the new rClip rect
        // and make sure that our locking bounds are not empty.
    case SIMPLEREGION:
        J2dTraceLn(J2D_TRACE_VERBOSE, "  simple clipping region");
        // Constrain the bounds to the given clip box
        if (bounds->x1 < rClip.left) {
            bounds->x1 = rClip.left;
        }
        if (bounds->y1 < rClip.top) {
            bounds->y1 = rClip.top;
        }
        if (bounds->x2 > rClip.right) {
            bounds->x2 = rClip.right;
        }
        if (bounds->y2 > rClip.bottom) {
            bounds->y2 = rClip.bottom;
        }
        // If the bounds are 0 or negative, then the bounds have
        // been obscured by the clip box, so return FALSE
        if ((bounds->x2 <= bounds->x1) ||
            (bounds->y2 <= bounds->y1)) {
            // REMIND: We should probably do something different here
            // instead of simply returning FALSE.  Since the bounds are
            // empty we won't end up drawing anything, so why spend the
            // effort of returning false and having GDI do a LOCK_BY_DIB?
            // Perhaps we need a new lock code that will indicate that we
            // shouldn't bother drawing?
            J2dTraceLn(J2D_TRACE_WARNING,
                       "Win32SD_SimpleClip: failed due to empty bounds");
            return JNI_FALSE;
        }
        break;
    case NULLREGION:
    case ERROR:
    default:
        J2dTraceLn1(J2D_TRACE_ERROR,
                   "Win32SD_SimpleClip: failed due to incorrect complexity=%d",
                    nComplexity);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

static void Win32SD_TryLockByDD(JNIEnv *env, Win32SDOps *wsdo,
                                SurfaceDataRasInfo *pRasInfo, HDC hDC)
{
    RECT winrect;

    J2dTraceLn(J2D_TRACE_INFO, "Win32SD_LockByDD");

    ::GetWindowRect(wsdo->window, &winrect);
    ::OffsetRect(&winrect, wsdo->insets.left, wsdo->insets.top);

    /* On NT-based OS's, winddow can move even while we have the primary
     * surface locked.  Must prevent this or else we may render to the
     * wrong place on the screen. */
    windowMoveLock.Enter();

    if (!DDLock(env, wsdo, NULL, pRasInfo)) {
        windowMoveLock.Leave();
        J2dTraceLn(J2D_TRACE_WARNING,
                   "Win32SD_TryLockByDD: DDLock failed");
        return;
    }

    // If DD lock successful, update window location in wsdo
    ::GetWindowRect(wsdo->window, &winrect);
    wsdo->x = winrect.left;
    wsdo->y = winrect.top;
    if (currNumDevices > 1) {
        MONITOR_INFO *mi = wsdo->device->GetMonitorInfo();
        wsdo->x -= mi->rMonitor.left;
        wsdo->y -= mi->rMonitor.top;
    }
    if (!Win32SD_SimpleClip(env, wsdo, &pRasInfo->bounds, hDC) ||
        !Win32SD_CheckMonitorArea(wsdo, &pRasInfo->bounds, hDC))
    {
        DDUnlock(env, wsdo);
        windowMoveLock.Leave();
        J2dTraceLn(J2D_TRACE_WARNING, "Win32SD_TryLockByDD: failed because "\
                   "of clip, cursor or monitor area");
        return;
    }

    wsdo->lockType = WIN32SD_LOCK_BY_DDRAW;
    J2dTraceLn(J2D_TRACE_VERBOSE, "Win32SD_TryLockByDD: succeeded");
}

static jint Win32SD_Lock(JNIEnv *env,
                         SurfaceDataOps *ops,
                         SurfaceDataRasInfo *pRasInfo,
                         jint lockflags)
{
    Win32SDOps *wsdo = (Win32SDOps *) ops;
    int ret = SD_SUCCESS;
    HDC hDC;
    J2dTraceLn(J2D_TRACE_INFO, "Win32SD_Lock");

    /* This surfaceLock replaces an earlier implementation which used a
    monitor associated with the peer.  That implementation was prone
    to deadlock problems, so it was replaced by a lock that does not
    have dependencies outside of this thread or object.
    However, this lock doesn't necessarily do all that we want.
    For example, a user may issue a call which results in a DIB lock
    and another call which results in a DDraw Blt.  We can't guarantee
    what order these operations happen in (they are driver and
    video-card dependent), so locking around the issue of either of
    those calls won't necessarily guarantee a particular result.
    The real solution might be to move away from mixing our
    rendering API's.  That is, if we only used DDraw, then we could
    guarantee that all rendering operations would happen in a given
    order.  Similarly for GDI.  But by mixing them, we leave our
    code at the mercy of driver bugs.*/
    wsdo->surfaceLock->Enter();
    if (wsdo->invalid == JNI_TRUE) {
        J2dTraceLn(J2D_TRACE_WARNING, "Win32SD_Lock: surface is invalid");
        wsdo->surfaceLock->Leave();
        if (beingShutdown != JNI_TRUE) {
            SurfaceData_ThrowInvalidPipeException(env,
                "Win32SurfaceData: bounds changed");
        }
        return SD_FAILURE;
    }
    if (wsdo->lockType != WIN32SD_LOCK_UNLOCKED) {
        wsdo->surfaceLock->Leave();
        if (!safe_ExceptionOccurred(env)) {
            JNU_ThrowInternalError(env, "Win32 LockRasData cannot nest locks");
        }
        return SD_FAILURE;
    }

    hDC = wsdo->GetDC(env, wsdo, 0, NULL, NULL, NULL, 0);
    if (hDC == NULL) {
        wsdo->surfaceLock->Leave();
        if (beingShutdown != JNI_TRUE) {
            JNU_ThrowNullPointerException(env, "HDC for component");
        }
        return SD_FAILURE;
    }

    if (lockflags & SD_LOCK_RD_WR) {
        // Do an initial clip to the client region of the window
        RECT crect;
        ::GetClientRect(wsdo->window, &crect);

        // Translate to window coords
        crect.left += wsdo->insets.left;
        crect.top += wsdo->insets.top;
        crect.right += wsdo->insets.left;
        crect.bottom += wsdo->insets.top;

        SurfaceDataBounds *bounds = &pRasInfo->bounds;

        if (bounds->x1 < crect.left) {
            bounds->x1 = crect.left;
        }
        if (bounds->y1 < crect.top) {
            bounds->y1 = crect.top;
        }
        if (bounds->x2 > crect.right) {
            bounds->x2 = crect.right;
        }
        if (bounds->y2 > crect.bottom) {
            bounds->y2 = crect.bottom;
        }

        if (useDDLock && DDUseDDraw(wsdo)) {
            Win32SD_TryLockByDD(env, wsdo, pRasInfo, hDC);
        }
        if (wsdo->lockType == WIN32SD_LOCK_UNLOCKED) {
            J2dTraceLn(J2D_TRACE_VERBOSE,
                       "Win32SD_Lock: dd lock failed, try to lock by DIB");
            if (bounds->x2 > bounds->x1 && bounds->y2 > bounds->y1) {
                wsdo->lockType = WIN32SD_LOCK_BY_DIB;
                if (lockflags & SD_LOCK_FASTEST) {
                    ret = SD_SLOWLOCK;
                }
                J2dTraceLn(J2D_TRACE_VERBOSE, " locked by DIB");
            } else {
                wsdo->ReleaseDC(env, wsdo, hDC);
                wsdo->lockType = WIN32SD_LOCK_UNLOCKED;
                wsdo->surfaceLock->Leave();
                ret = SD_FAILURE;
                J2dTraceLn(J2D_TRACE_ERROR,
                           "Win32SD_Lock: error locking by DIB");
            }
        } else {
            J2dTraceLn(J2D_TRACE_VERBOSE, "Win32SD_Lock: locked by DDraw");
            // Surface is already locked - release DC
            wsdo->ReleaseDC(env, wsdo, hDC);
        }
    } else {
        J2dTraceLn(J2D_TRACE_VERBOSE, "Win32SD_Lock: surface wasn't locked");
        /* They didn't lock for anything - we won't give them anything */
        wsdo->ReleaseDC(env, wsdo, hDC);
        wsdo->lockType = WIN32SD_LOCK_UNLOCKED;
        wsdo->surfaceLock->Leave();
        ret = SD_FAILURE;
    }

    wsdo->lockFlags = lockflags;
    return ret;
}

static void Win32SD_GetRasInfo(JNIEnv *env,
                               SurfaceDataOps *ops,
                               SurfaceDataRasInfo *pRasInfo)
{
    Win32SDOps *wsdo = (Win32SDOps *) ops;
    jint lockflags = wsdo->lockFlags;
    J2dTraceLn(J2D_TRACE_INFO, "Win32SD_GetRasInfo");
    HDC hDC = GetThreadDC(env, wsdo);

    if (wsdo->lockType == WIN32SD_LOCK_UNLOCKED) {
        memset(pRasInfo, 0, sizeof(*pRasInfo));
        return;
    }

    if (useDDLock && DDUseDDraw(wsdo)           &&
        wsdo->lockType == WIN32SD_LOCK_BY_DIB   &&
        (lockflags & SD_LOCK_FASTEST))
    {
        Win32SD_TryLockByDD(env, wsdo, pRasInfo, hDC);
        if (wsdo->lockType == WIN32SD_LOCK_BY_DDRAW) {
            wsdo->ReleaseDC(env, wsdo, hDC);
        }
    }

    if (wsdo->lockType == WIN32SD_LOCK_BY_DDRAW) {
        // Adjust the rasBase to point to the upper left pixel of our drawing area
        pRasInfo->rasBase = (void *) ((intptr_t)pRasInfo->rasBase +
                                      (wsdo->x * pRasInfo->pixelStride) +
                                      (wsdo->y * pRasInfo->scanStride));
    } else if (wsdo->lockType == WIN32SD_LOCK_BY_DIB) {
        int x, y, w, h;
        int pixelStride = wsdo->pixelStride;
        // do not subtract insets from x,y as we take care of it in SD_GetDC
        x = pRasInfo->bounds.x1;
        y = pRasInfo->bounds.y1;
        w = pRasInfo->bounds.x2 - x;
        h = pRasInfo->bounds.y2 - y;

        struct tagBitmapheader  {
            BITMAPINFOHEADER bmiHeader;
            union {
                DWORD           dwMasks[3];
                RGBQUAD         palette[256];
            } colors;
        } bmi;

        // Need to create bitmap if we don't have one already or
        // if the existing one is not large enough for this operation
        // or if we are in 8 bpp display mode (because we need to
        // make sure that the latest palette info gets loaded into
        // the bitmap)
        // REMIND: we should find some way to dynamically force bitmap
        // recreation only when the palette changes
        if (pixelStride == 1 || !wsdo->bitmap || (w > wsdo->bmWidth) ||
            (h > wsdo->bmHeight))
        {
            if (wsdo->bitmap) {
                // delete old objects
                J2dTraceLn(J2D_TRACE_VERBOSE,
                           "Win32SD_GetRasInfo: recreating GDI bitmap");
                if (wsdo->bmdc) {   // should not be null
                    ::SelectObject(wsdo->bmdc, wsdo->oldmap);
                    ::DeleteDC(wsdo->bmdc);
                    wsdo->bmdc = 0;
                }
                ::DeleteObject(wsdo->bitmap);
                wsdo->bitmap = 0;
            }
            bmi.bmiHeader.biSize = sizeof(bmi.bmiHeader);
            bmi.bmiHeader.biWidth = w;
            bmi.bmiHeader.biHeight = -h;
            wsdo->bmWidth = w;
            wsdo->bmHeight = h;
            bmi.bmiHeader.biPlanes = 1;
            bmi.bmiHeader.biBitCount = pixelStride * 8;
            // 1,3 byte use BI_RGB, 2,4 byte use BI_BITFIELD...
            bmi.bmiHeader.biCompression =
                (pixelStride & 1)
                    ? BI_RGB
                    : BI_BITFIELDS;
            bmi.bmiHeader.biSizeImage = 0;
            bmi.bmiHeader.biXPelsPerMeter = 0;
            bmi.bmiHeader.biYPelsPerMeter = 0;
            bmi.bmiHeader.biClrUsed = 0;
            bmi.bmiHeader.biClrImportant = 0;
            if (pixelStride == 1) {
                // we can use systemEntries here because
                // RGBQUAD is xRGB and systemEntries are stored as xRGB
                memcpy(bmi.colors.palette, wsdo->device->GetSystemPaletteEntries(),
                       sizeof(bmi.colors.palette));
            } else {
                // For non-index cases, init the masks for the pixel depth
                for (int i = 0; i < 3; i++) {
                    bmi.colors.dwMasks[i] = wsdo->pixelMasks[i];
                }
            }

            // REMIND: This would be better if moved to the Lock function
            // so that errors could be dealt with.
            wsdo->bitmap = ::CreateDIBSection(hDC, (BITMAPINFO *) &bmi,
                                              DIB_RGB_COLORS, &wsdo->bmBuffer, NULL, 0);
            if (wsdo->bitmap != 0) {
                // scanStride is cached along with reuseable bitmap
                // Round up to the next DWORD boundary
                wsdo->bmScanStride = (wsdo->bmWidth * pixelStride + 3) & ~3;
                wsdo->bmdc = ::CreateCompatibleDC(hDC);
                if (wsdo->bmdc == 0) {
                    ::DeleteObject(wsdo->bitmap);
                    wsdo->bitmap = 0;
                } else {
                    wsdo->oldmap = (HBITMAP) ::SelectObject(wsdo->bmdc,
                                                            wsdo->bitmap);
                }
            }
        }
        if (wsdo->bitmap != 0) {
            if (lockflags & SD_LOCK_NEED_PIXELS) {
                int ret = ::BitBlt(wsdo->bmdc, 0, 0, w, h,
                                   hDC, x, y, SRCCOPY);
                ::GdiFlush();
            }
            wsdo->x = x;
            wsdo->y = y;
            wsdo->w = w;
            wsdo->h = h;
            pRasInfo->rasBase = (char *)wsdo->bmBuffer - (x*pixelStride +
                                y*wsdo->bmScanStride);
            pRasInfo->pixelStride = pixelStride;
            pRasInfo->pixelBitOffset = 0;
            pRasInfo->scanStride = wsdo->bmScanStride;
            if (lockflags & SD_LOCK_WRITE) {
                // If the user writes to the bitmap then we should
                // copy the bitmap to the screen during Unlock
                wsdo->bmCopyToScreen = TRUE;
            }
        } else {
            pRasInfo->rasBase = NULL;
            pRasInfo->pixelStride = 0;
            pRasInfo->pixelBitOffset = 0;
            pRasInfo->scanStride = 0;
        }
    } else {
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

static void Win32SD_Setup(JNIEnv *env,
                          SurfaceDataOps *ops)
{
    // Call SetupTGI to ensure that this thread already has a DC that is
    // compatible with this window.  This means that we won't be calling
    // ::SendMessage(GETDC) in the middle of a lock procedure, which creates
    // a potential deadlock situation.
    // Note that calling SetupTGI here means that anybody needing a DC
    // later in this rendering process need only call GetTGI, which
    // assumes that the TGI structure is valid for this thread/window.
    SetupThreadGraphicsInfo(env, (Win32SDOps*)ops);
}


static void Win32SD_Unlock(JNIEnv *env,
                           SurfaceDataOps *ops,
                           SurfaceDataRasInfo *pRasInfo)
{
    Win32SDOps *wsdo = (Win32SDOps *) ops;
    J2dTraceLn(J2D_TRACE_INFO, "Win32SD_Unlock");
    HDC hDC = GetThreadDC(env, wsdo);

    if (wsdo->lockType == WIN32SD_LOCK_UNLOCKED) {
        if (!safe_ExceptionOccurred(env)) {
            JNU_ThrowInternalError(env,
                                   "Unmatched unlock on Win32 SurfaceData");
        }
        return;
    }

    if (wsdo->lockType == WIN32SD_LOCK_BY_DDRAW) {
        DDUnlock(env, wsdo);
        windowMoveLock.Leave();
        wsdo->lockType = WIN32SD_LOCK_UNLOCKED;
    } else if (wsdo->lockType == WIN32SD_LOCK_BY_DIB) {
        if (wsdo->lockFlags & SD_LOCK_WRITE) {
            J2dTraceLn(J2D_TRACE_VERBOSE,
                       "Win32SD_Unlock: do Blt of the bitmap");
            if (wsdo->bmCopyToScreen && ::IsWindowVisible(wsdo->window)) {
                // Don't bother copying to screen if our window has gone away
                // or if the bitmap was not actually written to during this
                // Lock/Unlock procedure.
                ::BitBlt(hDC, wsdo->x, wsdo->y, wsdo->w, wsdo->h,
                    wsdo->bmdc, 0, 0, SRCCOPY);
                ::GdiFlush();
            }
            wsdo->bmCopyToScreen = FALSE;
        }
        wsdo->lockType = WIN32SD_LOCK_UNLOCKED;
        wsdo->ReleaseDC(env, wsdo, hDC);
    }
    wsdo->surfaceLock->Leave();
}

/*
 * REMIND: This mechanism is just a prototype of a way to manage a
 * small cache of DC objects.  It is incomplete in the following ways:
 *
 * - It is not thread-safe!  It needs appropriate locking and release calls
 *   (perhaps the AutoDC mechanisms from Kestrel)
 * - It does hardly any error checking (What if GetDCEx returns NULL?)
 * - It cannot handle printer DCs and their resolution
 * - It should probably "live" in the native SurfaceData object to allow
 *   alternate implementations for printing and embedding
 * - It doesn't handle XOR
 * - It caches the client bounds to determine if clipping is really needed
 *   (no way to invalidate the cached bounds and there is probably a better
 *    way to manage clip validation in any case)
 */

#define COLORFOR(c)     (PALETTERGB(((c)>>16)&0xff,((c)>>8)&0xff,((c)&0xff)))

COLORREF CheckGrayColor(Win32SDOps *wsdo, int c) {
    if (wsdo->device->GetGrayness() != GS_NOTGRAY) {
        int g = (77 *(c & 0xFF) +
                 150*((c >> 8) & 0xFF) +
                 29 *((c >> 16) & 0xFF) + 128) / 256;
        c = g | (g << 8) | (g << 16);
    }
    return COLORFOR(c);
}

static HDC Win32SD_GetDC(JNIEnv *env, Win32SDOps *wsdo,
                         jint type, jint *patrop,
                         jobject clip, jobject comp, jint color)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32SD_GetDC");

    if (wsdo->invalid == JNI_TRUE) {
        if (beingShutdown != JNI_TRUE) {
            SurfaceData_ThrowInvalidPipeException(env, "bounds changed");
        }
        return (HDC) NULL;
    }

    ThreadGraphicsInfo *info = GetThreadGraphicsInfo(env, wsdo);
    Win32SD_InitDC(env, wsdo, info, type, patrop, clip, comp, color);
    return info->hDC;
}

JNIEXPORT void JNICALL
Win32SD_InitDC(JNIEnv *env, Win32SDOps *wsdo, ThreadGraphicsInfo *info,
               jint type, jint *patrop,
               jobject clip, jobject comp, jint color)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32SD_InitDC");

    // init clip
    if (clip == NULL) {
        if (info->type & CLIP) {
            ::SelectClipRgn(info->hDC, (HRGN) NULL);
            info->type ^= CLIP;
        }
        if (info->clip != NULL) {
            env->DeleteWeakGlobalRef(info->clip);
            info->clip = NULL;
        }
    } else if (!env->IsSameObject(clip, info->clip)) {
        SurfaceDataBounds span;
        RegionData clipInfo;
        if (Region_GetInfo(env, clip, &clipInfo)) {
            // return; // REMIND: What to do here?
        }

        if (Region_IsEmpty(&clipInfo)) {
            HRGN hrgn = ::CreateRectRgn(0, 0, 0, 0);
            ::SelectClipRgn(info->hDC, hrgn);
            ::DeleteObject(hrgn);
            info->type |= CLIP;
        } else if (Region_IsRectangular(&clipInfo)) {
            if (clipInfo.bounds.x1 <= info->bounds.left &&
                clipInfo.bounds.y1 <= info->bounds.top &&
                clipInfo.bounds.x2 >= info->bounds.right &&
                clipInfo.bounds.y2 >= info->bounds.bottom)
            {
                if (info->type & CLIP) {
                    ::SelectClipRgn(info->hDC, (HRGN) NULL);
                    info->type ^= CLIP;
                }
            } else {
                // Make the window-relative rect a client-relative
                // one for Windows
                HRGN hrgn =
                    ::CreateRectRgn(clipInfo.bounds.x1 - wsdo->insets.left,
                                    clipInfo.bounds.y1 - wsdo->insets.top,
                                    clipInfo.bounds.x2 - wsdo->insets.left,
                                    clipInfo.bounds.y2 - wsdo->insets.top);
                ::SelectClipRgn(info->hDC, hrgn);
                ::DeleteObject(hrgn);
                info->type |= CLIP;
            }
        } else {
            int leftInset = wsdo->insets.left;
            int topInset = wsdo->insets.top;
            Region_StartIteration(env, &clipInfo);
            jint numrects = Region_CountIterationRects(&clipInfo);
            DWORD nCount = sizeof(RGNDATAHEADER) + numrects * sizeof(RECT);
            RGNDATA *lpRgnData = (RGNDATA *) safe_Malloc(nCount);
            lpRgnData->rdh.dwSize = sizeof(RGNDATAHEADER);
            lpRgnData->rdh.iType = RDH_RECTANGLES;
            lpRgnData->rdh.nCount = numrects;
            lpRgnData->rdh.nRgnSize = 0;
            lpRgnData->rdh.rcBound.left = clipInfo.bounds.x1 - leftInset;
            lpRgnData->rdh.rcBound.top = clipInfo.bounds.y1 - topInset;
            lpRgnData->rdh.rcBound.right = clipInfo.bounds.x2 - leftInset;
            lpRgnData->rdh.rcBound.bottom = clipInfo.bounds.y2 - topInset;
            RECT *pRect = (RECT *) &(((RGNDATA *)lpRgnData)->Buffer);
            while (Region_NextIteration(&clipInfo, &span)) {
                pRect->left = span.x1 - leftInset;
                pRect->top = span.y1 - topInset;
                pRect->right = span.x2 - leftInset;
                pRect->bottom = span.y2 - topInset;
                pRect++;
            }
            Region_EndIteration(env, &clipInfo);
            HRGN hrgn = ::ExtCreateRegion(NULL, nCount, lpRgnData);
            free(lpRgnData);
            ::SelectClipRgn(info->hDC, hrgn);
            ::DeleteObject(hrgn);
            info->type |= CLIP;
        }
        if (info->clip != NULL) {
            env->DeleteWeakGlobalRef(info->clip);
        }
        info->clip = env->NewWeakGlobalRef(clip);
    }

    // init composite
    if ((comp == NULL) || !env->IsInstanceOf(comp, xorCompClass)) {
        if (info->comp != NULL) {
            env->DeleteWeakGlobalRef(info->comp);
            info->comp = NULL;
            info->patrop = PATCOPY;
            ::SetROP2(info->hDC, R2_COPYPEN);
        }
    } else {
        if (!env->IsSameObject(comp, info->comp)) {
            info->xorcolor = GrPrim_CompGetXorColor(env, comp);
            if (info->comp != NULL) {
                env->DeleteWeakGlobalRef(info->comp);
            }
            info->comp = env->NewWeakGlobalRef(comp);
            info->patrop = PATINVERT;
            ::SetROP2(info->hDC, R2_XORPEN);
        }
        color ^= info->xorcolor;
    }

    if (patrop != NULL) {
        *patrop = info->patrop;
    }

    // init brush and pen
    if (type & BRUSH) {
        if (info->brushclr != color || (info->brush == NULL)) {
            if (info->type & BRUSH) {
                ::SelectObject(info->hDC, nullbrush);
                info->type ^= BRUSH;
            }
            if (info->brush != NULL) {
                info->brush->Release();
            }
            info->brush = AwtBrush::Get(CheckGrayColor(wsdo, color));
            info->brushclr = color;
        }
        if ((info->type & BRUSH) == 0) {
            ::SelectObject(info->hDC, info->brush->GetHandle());
            info->type ^= BRUSH;
        }
    } else if (type & NOBRUSH) {
        if (info->type & BRUSH) {
            ::SelectObject(info->hDC, nullbrush);
            info->type ^= BRUSH;
        }
    }
    if (type & PEN) {
        if (info->penclr != color || (info->pen == NULL)) {
            if (info->type & PEN) {
                ::SelectObject(info->hDC, nullpen);
                info->type ^= PEN;
            }
            if (info->pen != NULL) {
                info->pen->Release();
            }
            info->pen = AwtPen::Get(CheckGrayColor(wsdo, color));
            info->penclr = color;
        }
        if ((info->type & PEN) == 0) {
            ::SelectObject(info->hDC, info->pen->GetHandle());
            info->type ^= PEN;
        }
    } else if (type & NOPEN) {
        if (info->type & PEN) {
            ::SelectObject(info->hDC, nullpen);
            info->type ^= PEN;
        }
    }
}

static void Win32SD_ReleaseDC(JNIEnv *env, Win32SDOps *wsdo, HDC hDC)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32SD_ReleaseDC");
    // Don't actually do anything here: every thread holds its own
    // wsdo-specific DC until the thread goes away or the wsdo
    // is disposed.

    // we don't track gdi operations for on-screen surfaces
    // by setting wsdo->gdiOpPending to TRUE here
    // because we do not need an extra sync before DD-locking
    // the on-screen surface (sync is a dd-lock)
}


static void Win32SD_InvalidateSD(JNIEnv *env, Win32SDOps *wsdo)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32SD_InvalidateSD");
    J2dTraceLn2(J2D_TRACE_VERBOSE, "  wsdo=0x%x wsdo->window=0x%x",
                wsdo, wsdo->window);

    wsdo->invalid = JNI_TRUE;
}



/*
 * Method:    Win32SD_Dispose
 */
static void
Win32SD_Dispose(JNIEnv *env, SurfaceDataOps *ops)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32SD_Dispose");
    // ops is assumed non-null as it is checked in SurfaceData_DisposeOps
    Win32SDOps *wsdo = (Win32SDOps*)ops;
    if (wsdo->bitmap) {
        // delete old objects
        J2dTraceLn(J2D_TRACE_VERBOSE, "  disposing the GDI bitmap");
        if (wsdo->bmdc) {   // should not be null
            ::SelectObject(wsdo->bmdc, wsdo->oldmap);
            ::DeleteDC(wsdo->bmdc);
            wsdo->bmdc = 0;
        }
        ::DeleteObject(wsdo->bitmap);
        wsdo->bitmap = 0;
    }
    DDDestroySurface(wsdo);
    env->DeleteWeakGlobalRef(wsdo->peer);
    if (wsdo->device != NULL) {
        wsdo->device->Release();
        wsdo->device = NULL;
    }
    delete wsdo->surfaceLock;
}


/*
 * Class:     sun_java2d_windows_Win32SurfaceData
 * Method:    invalidateSD
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_Win32SurfaceData_invalidateSD(JNIEnv *env, jobject wsd)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32SurfaceData_invalidateSD");
    Win32SDOps *wsdo = Win32SurfaceData_GetOpsNoSetup(env, wsd);
    if (wsdo != NULL) {
        wsdo->InvalidateSD(env, wsdo);
    }
}

/*
 * Class:     sun_java2d_windows_Win32SurfaceData
 * Method:    restoreSurface
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_Win32SurfaceData_restoreSurface(JNIEnv *env,
                                                        jobject sData)
{
    J2dTraceLn(J2D_TRACE_INFO,
               "Win32SurfaceData_restoreSurface: restoring primary");
    Win32SDOps *wsdo = Win32SurfaceData_GetOpsNoSetup(env, sData);

    // Attempt to restore and lock the surface (to make sure the restore worked)
    if (DDRestoreSurface(wsdo) && DDLock(env, wsdo, NULL, NULL)) {
        DDUnlock(env, wsdo);
    } else {
        // Failure - throw exception
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "Win32SurfaceData_restoreSurface: "\
                      "problems restoring primary");
        SurfaceData_ThrowInvalidPipeException(env, "RestoreSurface failure");
    }
}

/*
 * Class:     sun_java2d_windows_Win32SurfaceData
 * Method:    flip
 * Signature: (Lsun/awt/windows/Win32SurfaceData;)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_Win32SurfaceData_flip(JNIEnv *env,
                                              jobject sData, jobject dData)
{
    J2dTraceLn(J2D_TRACE_INFO, "Win32SurfaceData_flip");
    if (dData == NULL) {
        J2dTraceLn(J2D_TRACE_WARNING,
                   "Win32SurfaceData_flip: dData is null");
        SurfaceData_ThrowInvalidPipeException(env,
            "Could not flip offscreen Surface");
        return;
    }
    Win32SDOps *wsdo_s = Win32SurfaceData_GetOpsNoSetup(env, sData);
    Win32SDOps *wsdo_d = Win32SurfaceData_GetOpsNoSetup(env, dData);
    if (!DDFlip(env, wsdo_s, wsdo_d)) {
        J2dTraceLn(J2D_TRACE_WARNING, "Win32SurfaceData_flip: flipping error");
        SurfaceData_ThrowInvalidPipeException(env,
            "Could not flip offscreen Surface");
    }
}
