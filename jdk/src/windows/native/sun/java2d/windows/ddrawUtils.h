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

#ifndef DDRAWUTILS_H
#define DDRAWUTILS_H


#include <ddraw.h>
#include <jni.h>
#include <windows.h>
#include "Win32SurfaceData.h"
#include "ddrawObject.h"

/**
 * Direct Draw utility functions
 */

#define DDINSTANCE_USABLE(ddInst) \
    ((ddInst) && (ddInst->valid) && (ddInst->accelerated))

void    DDRelease();

void    DDReleaseSurfaceMemory(DDrawSurface *lpSurface);

BOOL    DDCreatePrimary(Win32SDOps *wsdo);

void    DDFreeSyncSurface(DDrawObjectStruct *tmpDdInstance);

void    DDSync();

BOOL    DDCanCreatePrimary(HMONITOR hMon);

BOOL    DDCanBlt(Win32SDOps *wsdo);

BOOL    DDUseDDraw(Win32SDOps *wsdo);

BOOL    DeviceUseDDraw(HMONITOR hMon);

BOOL    DeviceUseD3D(HMONITOR hMon);

void    DDInvalidateDDInstance(DDrawObjectStruct *ddInst);

void    ReleaseDDInstance(DDrawObjectStruct *ddInst);

BOOL    DDEnterFullScreen(HMONITOR hMon, HWND hwnd, HWND topLevelHwnd);

BOOL    DDExitFullScreen(HMONITOR hMon, HWND hwnd);

BOOL    DDGetDisplayMode(HMONITOR hMon, DDrawDisplayMode& displayMode);

BOOL    DDSetDisplayMode(HMONITOR hMon, DDrawDisplayMode& displayMode);

BOOL    DDEnumDisplayModes(HMONITOR hMon, DDrawDisplayMode* constraint,
                           DDrawDisplayMode::Callback callback, void* context);

BOOL    DDClipCheck(Win32SDOps *wsdo, RECT *operationRect);

BOOL    DDLock(JNIEnv *env, Win32SDOps *wsdo, RECT *lockRect,
               SurfaceDataRasInfo *pRasInfo);

void    DDUnlock(JNIEnv *env, Win32SDOps *wsdo);

BOOL    DDColorFill(JNIEnv *env, jobject sData, Win32SDOps *wsdo,
                    RECT *fillRect, jint color);

BOOL    DDBlt(JNIEnv *env, Win32SDOps *wsdoSrc, Win32SDOps *wsdoDst,
              RECT *rDst, RECT *rSrc, CompositeInfo *compInfo = NULL);

void    DDSetColorKey(JNIEnv *env, Win32SDOps *wsdo, jint color);

BOOL    DDFlip(JNIEnv *env, Win32SDOps *src, Win32SDOps *dest);

BOOL    DDRestoreSurface(Win32SDOps *wsdo);

jint    DDGetAvailableMemory(HMONITOR hMon);

BOOL    DDCreateSurface(Win32SDOps *wsdo);

BOOL    DDCreateOffScreenSurface(Win32SDOps *wsdo, DDrawObjectStruct *ddInst);

BOOL    DDGetAttachedSurface(JNIEnv *env, Win32SDOps* wsdo_parent, Win32SDOps* wsdo);

void    DDDestroySurface(Win32SDOps *wsdo);

BOOL    DDCanReplaceSurfaces(HWND hwnd);

BOOL    DDSurfaceDepthsCompatible(int javaDepth, int nativeDepth);

void    PrintDirectDrawError(DWORD errNum, char *message);

void    DebugPrintDirectDrawError(DWORD errNum, char *message);

void    GetDDErrorString(DWORD errNum, char *buffer);

DDrawObjectStruct *GetDDInstanceForDevice(HMONITOR hMon);

#define CLIP2RECTS_1PARAM(r1, r2, param, comp, lim) \
    do { \
        if (r1.param comp lim) { \
            r2.param += lim - r1.param; \
            r1.param = lim; \
        } \
    } while (0)

#define CLIP2RECTS(r1, L, T, R, B, r2) \
    do { \
        CLIP2RECTS_1PARAM(r1, r2, left, <, L); \
        CLIP2RECTS_1PARAM(r1, r2, top, <, T); \
        CLIP2RECTS_1PARAM(r1, r2, right, >, R); \
        CLIP2RECTS_1PARAM(r1, r2, bottom, >, B); \
    } while(0)

#endif DDRAWUTILS_H
