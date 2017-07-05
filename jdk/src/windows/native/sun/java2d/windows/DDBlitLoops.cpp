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

#include <stdlib.h>
#include <jni.h>
#include <sun_java2d_windows_DDBlitLoops.h>
#include <sun_java2d_windows_DDScaleLoops.h>
#include "ddrawUtils.h"
#include "GraphicsPrimitiveMgr.h"
#include "Region.h"
#include "Trace.h"

extern int currNumDevices;
extern CriticalSection windowMoveLock;

extern "C" {

/**
 * Return TRUE if rCheck is contained within rContainer
 */
INLINE BOOL RectInRect(RECT *rCheck, RECT *rContainer)
{
    // Assumption: left <= right, top <= bottom
    if (rCheck->left >= rContainer->left &&
        rCheck->right <= rContainer->right &&
        rCheck->top >= rContainer->top &&
        rCheck->bottom <= rContainer->bottom)
    {
        return TRUE;
    } else {
        return FALSE;
    }
}

/**
 * Returns whether the given rectangle (in screen-relative
 * coords) is within the rectangle of the given device.
 * NOTE: A side-effect of this function is offsetting the
 * rectangle by the left/top of the monitor rectangle.
 */
INLINE BOOL RectInDevice(RECT *rect, AwtWin32GraphicsDevice *device)
{
    MONITOR_INFO *mi = device->GetMonitorInfo();
    ::OffsetRect(rect, mi->rMonitor.left, mi->rMonitor.top);
    if (!RectInRect(rect, &mi->rMonitor)) {
        return TRUE;
    }
    return FALSE;
}

/**
 * Need to handle Blt to other devices iff:
 *    - there are >1 devices on the system
 *    - at least one of src/dest is an onscreen window
 *    - the onscreen window overlaps with
 *    a monitor which is not the monitor associated with the window
 */
void MultimonBlt(JNIEnv *env, Win32SDOps *wsdoSrc, Win32SDOps *wsdoDst,
                 jobject clip,
                 jint srcx, jint srcy,
                 jint dstx, jint dsty,
                 RECT *rSrc, RECT *rDst)
{
    J2dTraceLn(J2D_TRACE_INFO, "MultimonBlt");
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  srcx=%-4d srcy=%-4d dstx=%-4d dsty=%-4d",
                srcx, srcy, dstx, dsty);
    if (rSrc == NULL) {
        J2dTraceLn(J2D_TRACE_ERROR, "MultimonBlt: null rSrc");
        return;
    }
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  rSrc: l=%-4d t=%-4d r=%-4d b=%-4d",
                rSrc->left, rSrc->top, rSrc->right, rSrc->bottom);
    if (rDst == NULL) {
        J2dTraceLn(J2D_TRACE_ERROR, "MultimonBlt: null rDst");
        return;
    }
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  rDst: l=%-4d t=%-4d r=%-4d b=%-4d",
                rDst->left, rDst->top, rDst->right, rDst->bottom);
    int currentDevice = -1;
    RECT rectToIntersect;

    if (!(wsdoSrc->window || wsdoDst->window))
    {
        // Neither surface is onscreen: nothing to do
        return;
    }
    BOOL doGdiBlt = FALSE;
    if (wsdoSrc->window) {
        doGdiBlt = RectInDevice(rSrc, wsdoSrc->device);
        if (doGdiBlt) {
            currentDevice = wsdoSrc->device->GetDeviceIndex();
            rectToIntersect = *rSrc;
        }
    } else if (wsdoDst->window) {
        doGdiBlt = RectInDevice(rDst, wsdoDst->device);
        if (doGdiBlt) {
            currentDevice = wsdoDst->device->GetDeviceIndex();
            rectToIntersect = *rDst;
        }
    }
    if (doGdiBlt) {
        // Need to invoke Setup functions to setup HDCs because we used
        // the NoSetup versions of GetOps for performance reasons
        SurfaceData_InvokeSetup(env, (SurfaceDataOps*)wsdoSrc);
        SurfaceData_InvokeSetup(env, (SurfaceDataOps*)wsdoDst);
        HDC hdcSrc = wsdoSrc->GetDC(env, wsdoSrc, 0, NULL, NULL, NULL, 0);
        if (!hdcSrc) {
            J2dTraceLn(J2D_TRACE_WARNING,
                       "MultimonBlt: Null src HDC in MultimonBlt");
            return;
        }
        HDC hdcDst = wsdoDst->GetDC(env, wsdoDst, 0, NULL, clip, NULL, 0);
        if (!hdcDst) {
            J2dTraceLn(J2D_TRACE_WARNING,
                       "MultimonBlt: Null dst HDC in MultimonBlt");
            wsdoSrc->ReleaseDC(env, wsdoSrc, hdcSrc);
            return;
        }
        for (int i = 0; i < currNumDevices; ++i) {
            // Assumption: can't end up here for copies between two
            // different windows; it must be a copy between offscreen
            // surfaces or a window and an offscreen surface.  We've
            // already handled the Blt to window on the window's native
            // GraphicsDevice, so skip that device now.
            if (i == currentDevice) {
                continue;
            }
            MONITOR_INFO *mi = AwtWin32GraphicsDevice::GetMonitorInfo(i);
            RECT rIntersect;
            ::IntersectRect(&rIntersect, &rectToIntersect, &mi->rMonitor);
            if (!::IsRectEmpty(&rIntersect)) {
                int newSrcX = srcx + (rIntersect.left - rectToIntersect.left);
                int newSrcY = srcy + (rIntersect.top - rectToIntersect.top);
                int newDstX = dstx + (rIntersect.left - rectToIntersect.left);
                int newDstY = dsty + (rIntersect.top - rectToIntersect.top);
                int newW = rIntersect.right - rIntersect.left;
                int newH = rIntersect.bottom - rIntersect.top;
                ::BitBlt(hdcDst, newDstX, newDstY, newW, newH, hdcSrc,
                    newSrcX, newSrcY, SRCCOPY);
            }
        }
        wsdoSrc->ReleaseDC(env, wsdoSrc, hdcSrc);
        wsdoDst->ReleaseDC(env, wsdoDst, hdcDst);
    }
}

JNIEXPORT void JNICALL
Java_sun_java2d_windows_DDBlitLoops_Blit
    (JNIEnv *env, jobject joSelf,
     jobject srcData, jobject dstData,
     jobject composite, jobject clip,
     jint srcx, jint srcy,
     jint dstx, jint dsty,
     jint width, jint height)
{
    J2dTraceLn(J2D_TRACE_INFO, "DDBlitLoops_Blit");
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  srcx=%-4d srcy=%-4d dstx=%-4d dsty=%-4d",
                srcx, srcy, dstx, dsty);
    J2dTraceLn2(J2D_TRACE_VERBOSE, "  width=%-4d height=%-4d", width, height);
    POINT ptDst = {0, 0};
    POINT ptSrc = {0, 0};
    Win32SDOps *wsdoSrc = Win32SurfaceData_GetOpsNoSetup(env, srcData);
    Win32SDOps *wsdoDst = Win32SurfaceData_GetOpsNoSetup(env, dstData);
    RegionData clipInfo;

    if (!wsdoSrc->ddInstance || !wsdoDst->ddInstance) {
        // Some situations can cause us to fail on primary
        // creation, resulting in null lpSurface and null ddInstance
        // for a Win32Surface object.. Just noop this call in that case.
        return;
    }

    if (wsdoSrc->invalid || wsdoDst->invalid) {
        SurfaceData_ThrowInvalidPipeException(env,
            "DDBlitLoops_Blit: invalid surface data");
        return;
    }

    RECT rSrc = {srcx, srcy, srcx + width, srcy + height};
    RECT rDst = {dstx, dsty, dstx + width, dsty + height};
    if (Region_GetInfo(env, clip, &clipInfo)) {
        return;
    }

    /* If dst and/or src are offscreen surfaces, need to make sure
       that Blt is within the boundaries of those surfaces.  If not,
       clip the surface in question and also clip the other
       surface by the same amount.
     */
    if (!wsdoDst->window) {
        CLIP2RECTS(rDst, 0, 0, wsdoDst->w, wsdoDst->h, rSrc);
    }
    CLIP2RECTS(rDst,
               clipInfo.bounds.x1, clipInfo.bounds.y1,
               clipInfo.bounds.x2, clipInfo.bounds.y2,
               rSrc);
    if (!wsdoSrc->window) {
        CLIP2RECTS(rSrc, 0, 0, wsdoSrc->w, wsdoSrc->h, rDst);
    }
    Region_IntersectBoundsXYXY(&clipInfo,
                               rDst.left, rDst.top,
                               rDst.right, rDst.bottom);
    if (Region_IsEmpty(&clipInfo)) {
        return;
    }
    if (wsdoDst->window || wsdoSrc->window) {
        if ((wsdoDst->window && !::IsWindowVisible(wsdoDst->window)) ||
            (wsdoSrc->window && !::IsWindowVisible(wsdoSrc->window)))
        {
            return;
        }
        // The windowMoveLock CriticalSection ensures that a window cannot
        // move while we are in the middle of copying pixels into it.  See
        // the WM_WINDOWPOSCHANGING code in awt_Component.cpp for more
        // information.
        windowMoveLock.Enter();
        if (wsdoDst->window) {
            ::ClientToScreen(wsdoDst->window, &ptDst);
            MONITOR_INFO *mi = wsdoDst->device->GetMonitorInfo();
            ptDst.x -= wsdoDst->insets.left;
            ptDst.y -= wsdoDst->insets.top;
            ptDst.x -= mi->rMonitor.left;
            ptDst.y -= mi->rMonitor.top;
            ::OffsetRect(&rDst, ptDst.x, ptDst.y);
        }
        if (wsdoSrc->window) {
            MONITOR_INFO *mi = wsdoDst->device->GetMonitorInfo();
            ::ClientToScreen(wsdoSrc->window, &ptSrc);
            ptSrc.x -= wsdoSrc->insets.left;
            ptSrc.y -= wsdoSrc->insets.top;
            ptSrc.x -= mi->rMonitor.left;
            ptSrc.y -= mi->rMonitor.top;
            ::OffsetRect(&rSrc, ptSrc.x, ptSrc.y);
        }
    }
    if (Region_IsRectangular(&clipInfo)) {
        DDBlt(env, wsdoSrc, wsdoDst, &rDst, &rSrc);
    } else {
        SurfaceDataBounds span;
        RECT rSrcSpan, rDstSpan;
        ptSrc.x += srcx - dstx;
        ptSrc.y += srcy - dsty;
        Region_StartIteration(env, &clipInfo);
        while (Region_NextIteration(&clipInfo, &span)) {
            ::SetRect(&rDstSpan, span.x1, span.y1, span.x2, span.y2);
            ::CopyRect(&rSrcSpan, &rDstSpan);
            ::OffsetRect(&rDstSpan, ptDst.x, ptDst.y);
            ::OffsetRect(&rSrcSpan, ptSrc.x, ptSrc.y);
            DDBlt(env, wsdoSrc, wsdoDst, &rDstSpan, &rSrcSpan);
        }
        Region_EndIteration(env, &clipInfo);
    }
    if (wsdoDst->window || wsdoSrc->window) {
        windowMoveLock.Leave();
    }

    if (currNumDevices > 1) {
        // Also need to handle Blit in multimon case, where part of the
        // source or dest lies on a different device
        MultimonBlt(env, wsdoSrc, wsdoDst, clip, srcx, srcy, dstx, dsty,
                    &rSrc, &rDst);
    }
}


JNIEXPORT void JNICALL
Java_sun_java2d_windows_DDScaleLoops_Scale
    (JNIEnv *env, jobject joSelf,
     jobject srcData, jobject dstData,
     jobject composite,
     jint srcx, jint srcy,
     jint dstx, jint dsty,
     jint srcWidth, jint srcHeight,
     jint dstWidth, jint dstHeight)
{
    J2dTraceLn(J2D_TRACE_INFO, "DDScaleLoops_Scale");
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  srcx=%-4d srcy=%-4d dstx=%-4d dsty=%-4d",
                srcx, srcy, dstx, dsty);
    J2dTraceLn4(J2D_TRACE_VERBOSE,
                "  srcWidth=%-4d srcHeight=%-4d dstWidth=%-4d dstHeight=%-4d",
                srcWidth, srcHeight, dstWidth, dstHeight);
    POINT ptDst = {0, 0};
    POINT ptSrc = {0, 0};
    Win32SDOps *wsdoSrc = Win32SurfaceData_GetOpsNoSetup(env, srcData);
    Win32SDOps *wsdoDst = Win32SurfaceData_GetOpsNoSetup(env, dstData);

    if (!wsdoSrc->ddInstance || !wsdoDst->ddInstance) {
        // Some situations can cause us to fail on primary
        // creation, resulting in null lpSurface and null ddInstance
        // for a Win32Surface object.. Just noop this call in that case.
        return;
    }

    if (wsdoSrc->invalid || wsdoDst->invalid) {
        SurfaceData_ThrowInvalidPipeException(env,
            "DDBlitLoops_Scale: invalid surface data");
        return;
    }

    RECT rSrc = {srcx, srcy, srcx + srcWidth, srcy + srcHeight};
    RECT rDst = {dstx, dsty, dstx + dstWidth, dsty + dstHeight};

    /* If dst and/or src are offscreen surfaces, need to make sure
       that Blt is within the boundaries of those surfaces.  If not,
       clip the surface in question and also rescale the other
       surface according to the new scaling rectangle.
     */
    if (!wsdoDst->window &&
        (dstx < 0 || dsty < 0 ||
         rDst.right > wsdoDst->w || rDst.bottom > wsdoDst->h))
    {
        RECT newRDst;
        newRDst.left = max(0, rDst.left);
        newRDst.top = max(0, rDst.top);
        newRDst.right = min(wsdoDst->w, rDst.right);
        newRDst.bottom = min(wsdoDst->h, rDst.bottom);
        double srcDstScaleW = (double)srcWidth/(double)dstWidth;
        double srcDstScaleH = (double)srcHeight/(double)dstHeight;
        rSrc.left += (int)(srcDstScaleW * (newRDst.left - rDst.left));
        rSrc.top += (int)(srcDstScaleH * (newRDst.top - rDst.top));
        rSrc.right += (int)(srcDstScaleW * (newRDst.right - rDst.right));
        rSrc.bottom += (int)(srcDstScaleH * (newRDst.bottom - rDst.bottom));
        rDst = newRDst;
    }
    if (!wsdoSrc->window &&
        (srcx < 0 || srcy < 0 ||
         rSrc.right > wsdoSrc->w || rSrc.bottom > wsdoSrc->h))
    {
        RECT newRSrc;
        newRSrc.left = max(0, rSrc.left);
        newRSrc.top = max(0, rSrc.top);
        newRSrc.right = min(wsdoSrc->w, rSrc.right);
        newRSrc.bottom = min(wsdoSrc->h, rSrc.bottom);
        double dstSrcScaleW = (double)dstWidth/(double)srcWidth;
        double dstSrcScaleH = (double)dstHeight/(double)srcHeight;
        rDst.left += (int)(dstSrcScaleW * (newRSrc.left - rSrc.left));
        rDst.top += (int)(dstSrcScaleH * (newRSrc.top - rSrc.top));
        rDst.right += (int)(dstSrcScaleW * (newRSrc.right - rSrc.right));
        rDst.bottom += (int)(dstSrcScaleH * (newRSrc.bottom - rSrc.bottom));
        rSrc = newRSrc;
    }
    if (wsdoDst->window || wsdoSrc->window) {
        if ((wsdoDst->window && !::IsWindowVisible(wsdoDst->window)) ||
            (wsdoSrc->window && !::IsWindowVisible(wsdoSrc->window)))
        {
            return;
        }
        // The windowMoveLock CriticalSection ensures that a window cannot
        // move while we are in the middle of copying pixels into it.  See
        // the WM_WINDOWPOSCHANGING code in awt_Component.cpp for more
        // information.
        windowMoveLock.Enter();
        if (wsdoDst->window) {
            ::ClientToScreen(wsdoDst->window, &ptDst);
            MONITOR_INFO *mi = wsdoDst->device->GetMonitorInfo();
            ptDst.x -= wsdoDst->insets.left;
            ptDst.y -= wsdoDst->insets.top;
            ptDst.x -= mi->rMonitor.left;
            ptDst.y -= mi->rMonitor.top;
            ::OffsetRect(&rDst, ptDst.x, ptDst.y);
        }
        if (wsdoSrc->window) {
            MONITOR_INFO *mi = wsdoDst->device->GetMonitorInfo();
            ::ClientToScreen(wsdoSrc->window, &ptSrc);
            ptSrc.x -= wsdoSrc->insets.left;
            ptSrc.y -= wsdoSrc->insets.top;
            ptSrc.x -= mi->rMonitor.left;
            ptSrc.y -= mi->rMonitor.top;
            ::OffsetRect(&rSrc, ptSrc.x, ptSrc.y);
        }
    }
    DDBlt(env, wsdoSrc, wsdoDst, &rDst, &rSrc);
    if (wsdoDst->window || wsdoSrc->window) {
        windowMoveLock.Leave();
    }

    if (currNumDevices > 1) {
        // Also need to handle Blit in multimon case, where part of the
        // source or dest lies on a different device
        MultimonBlt(env, wsdoSrc, wsdoDst, NULL, srcx, srcy, dstx, dsty,
                    &rSrc, &rDst);
    }
}


}
