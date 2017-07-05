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

#include "sun_java2d_windows_WinBackBufferSurfaceData.h"

#include "Win32SurfaceData.h"

#include "awt_Component.h"
#include "Trace.h"
#include "ddrawUtils.h"

#include "jni_util.h"

extern "C" jboolean initOSSD_WSDO(JNIEnv* env, Win32SDOps* wsdo, jint width,
                                  jint height, jint screen, jint transparency);

extern "C" void disposeOSSD_WSDO(JNIEnv* env, Win32SDOps* wsdo);

DisposeFunc Win32BBSD_Dispose;

/*
 * Class:     sun_java2d_windows_WinBackBufferSurfaceData
 * Method:    initSurface
 * Signature: (IIILsun/awt/windows/WinBackBufferSurfaceData;)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_WinBackBufferSurfaceData_initSurface(JNIEnv *env,
    jobject sData, jint depth, jint width, jint height, jint screen,
    jobject parentData)
{
    Win32SDOps *wsdo = (Win32SDOps *)SurfaceData_GetOps(env, sData);

    J2dTraceLn(J2D_TRACE_INFO, "Win32BBSD_initSurface");
    /* Set the correct dispose method */
    wsdo->sdOps.Dispose = Win32BBSD_Dispose;
    jboolean status =
        initOSSD_WSDO(env, wsdo, width, height, screen, JNI_FALSE);
    if (status == JNI_FALSE || parentData == NULL) {
        SurfaceData_ThrowInvalidPipeException(env,
            "Error initalizing back-buffer surface");
        return;
    }
    Win32SDOps *wsdo_parent = (Win32SDOps*)SurfaceData_GetOps(env, parentData);
    if (!DDGetAttachedSurface(env, wsdo_parent, wsdo)) {
        SurfaceData_ThrowInvalidPipeException(env,
            "Can't create attached surface");
    }
    J2dTraceLn1(J2D_TRACE_VERBOSE,
                "Win32BackBufferSurfaceData_initSurface: "\
                "completed wsdo->lpSurface=0x%x", wsdo->lpSurface);
}

/*
 * Class:     sun_java2d_windows_WinBackBufferSurfaceData
 * Method:    restoreSurface
 * Signature: (Lsun/awt/windows/WinBackBufferSurfaceData;)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_WinBackBufferSurfaceData_restoreSurface(JNIEnv *env,
    jobject sData, jobject parentData)
{
    // Noop: back buffer restoration implicit in primary restore
}

/*
 * Method:    Win32BBSD_Dispose
 */
void
Win32BBSD_Dispose(JNIEnv *env, SurfaceDataOps *ops)
{
    // ops is assumed non-null as it is checked in SurfaceData_DisposeOps
    Win32SDOps *wsdo = (Win32SDOps*)ops;
    J2dTraceLn(J2D_TRACE_INFO, "Win32BBSD_Dispose");
    if (wsdo->lpSurface != NULL && !wsdo->surfaceLost) {
        delete wsdo->lpSurface;
        wsdo->lpSurface = NULL;
    }
    disposeOSSD_WSDO(env, wsdo);
}
