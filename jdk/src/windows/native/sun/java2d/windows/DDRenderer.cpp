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

#include "sun_java2d_windows_DDRenderer.h"
#include "Win32SurfaceData.h"

#include <ddraw.h>
#include "ddrawUtils.h"
#include "Trace.h"

/*
 * Class:     sun_java2d_windows_DDRenderer
 * Method:    doDrawLineDD
 * Signature: (Lsun/java2d/SurfaceData;IIIII)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_DDRenderer_doDrawLineDD
    (JNIEnv *env, jobject wr,
     jobject sData,
     jint color,
     jint x1, jint y1, jint x2, jint y2)
{
    Win32SDOps      *wsdo = Win32SurfaceData_GetOpsNoSetup(env, sData);
    RECT            fillRect;

    J2dTraceLn(J2D_TRACE_INFO, "DDRenderer_doDrawLineDD");

    // Assume x1 <= x2 and y1 <= y2 (that's the way the
    // Java code is written)
    fillRect.left = x1;
    fillRect.top = y1;
    fillRect.right = x2+1;
    fillRect.bottom = y2+1;
    DDColorFill(env, sData, wsdo, &fillRect, color);
}


/*
 * Class:     sun_java2d_windows_DDRenderer
 * Method:    doFillRect
 * Signature: (Lsun/java2d/SurfaceData;IIIII)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_DDRenderer_doFillRectDD
    (JNIEnv *env, jobject wr,
     jobject sData,
     jint color,
     jint left, jint top, jint right, jint bottom)
{
    Win32SDOps      *wsdo = Win32SurfaceData_GetOpsNoSetup(env, sData);
    RECT            fillRect;

    J2dTraceLn(J2D_TRACE_INFO, "DDRenderer_doFillRectDD");

    fillRect.left = left;
    fillRect.top = top;
    fillRect.right = right;
    fillRect.bottom = bottom;
    DDColorFill(env, sData, wsdo, &fillRect, color);
}


/*
 * Class:     sun_java2d_windows_DDRenderer
 * Method:    doDrawRectDD
 * Signature: (Lsun/java2d/SurfaceData;IIIII)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_DDRenderer_doDrawRectDD
    (JNIEnv *env, jobject wr,
     jobject sData,
     jint color,
     jint x, jint y, jint w, jint h)
{
    Win32SDOps      *wsdo = Win32SurfaceData_GetOpsNoSetup(env, sData);
    RECT            fillRect;

    J2dTraceLn(J2D_TRACE_INFO, "DDRenderer_doDrawRectDD");

    if (w == 0 || h == 0) {
        fillRect.left = x;
        fillRect.top = y;
        fillRect.right = w + 1;
        fillRect.bottom = h + 1;
        DDColorFill(env, sData, wsdo, &fillRect, color);
    }
    else {
        fillRect.left = x;
        fillRect.top = y;
        fillRect.right = x + w + 1;
        fillRect.bottom = y + 1;
        if (!DDColorFill(env, sData, wsdo, &fillRect, color))
            return;
        fillRect.top = y + 1;
        fillRect.right = x + 1;
        fillRect.bottom = y + h + 1;
        if (!DDColorFill(env, sData, wsdo, &fillRect, color))
            return;
        fillRect.left = x + 1;
        fillRect.top = y + h;
        fillRect.right = x + w + 1;
        fillRect.bottom = y + h + 1;
        if (!DDColorFill(env, sData, wsdo, &fillRect, color))
            return;
        fillRect.left = x + w;
        fillRect.top = y + 1;
        fillRect.bottom = y + h;
        if (!DDColorFill(env, sData, wsdo, &fillRect, color))
            return;
    }
}

/*
 * Class:     sun_java2d_windows_DDRenderer
 * Method:    devCopyArea
 * Signature: (Lsun/awt/windows/SurfaceData;IIIIII)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_DDRenderer_devCopyArea
    (JNIEnv *env, jobject wr,
     jobject sData,
     jint srcx, jint srcy,
     jint dx, jint dy,
     jint width, jint height)
{
    Win32SDOps *wsdo = Win32SurfaceData_GetOpsNoSetup(env, sData);
    J2dTraceLn(J2D_TRACE_INFO, "DDRenderer_devCopyArea");
    J2dTrace4(J2D_TRACE_VERBOSE, "  sx=%-4d sy=%-4d dx=%-4d dy=%-4d",
                srcx, srcy, dx, dy);
    J2dTraceLn2(J2D_TRACE_VERBOSE, "  w=%-4d h=%-4d", width, height);
    if (wsdo == NULL) {
        return;
    }

    RECT rSrc = {srcx, srcy, srcx + width, srcy + height};
    if (DDCanBlt(wsdo)) {
        RECT rDst = rSrc;
        ::OffsetRect(&rDst, dx, dy);

        DDBlt(env,wsdo, wsdo, &rDst, &rSrc);
        return;
    }
    HDC hDC = wsdo->GetDC(env, wsdo, 0, NULL, NULL, NULL, 0);
    if (hDC == NULL) {
        return;
    }

    VERIFY(::ScrollDC(hDC, dx, dy, &rSrc, NULL, NULL, NULL));
    wsdo->ReleaseDC(env, wsdo, hDC);
}
