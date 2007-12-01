/*
 * Copyright 1996-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#define _JNI_IMPLEMENTATION_
#include "awt_DrawingSurface.h"
#include "WindowsFlags.h"
#include "awt_Component.h"

jclass jawtVImgClass;
jclass jawtComponentClass;
jclass jawtW32ossdClass;
jfieldID jawtPDataID;
jfieldID jawtSDataID;
jfieldID jawtSMgrID;


/* DSI */

jint JAWTDrawingSurfaceInfo::Init(JAWTDrawingSurface* parent)
{
    TRY;

    JNIEnv* env = parent->env;
    jobject target = parent->target;
    if (JNU_IsNull(env, target)) {
        DTRACE_PRINTLN("NULL target");
        return JAWT_LOCK_ERROR;
    }
    HWND newHwnd = AwtComponent::GetHWnd(env, target);
    if (!::IsWindow(newHwnd)) {
        DTRACE_PRINTLN("Bad HWND");
        return JAWT_LOCK_ERROR;
    }
    jint retval = 0;
    platformInfo = this;
    ds = parent;
    bounds.x = env->GetIntField(target, AwtComponent::xID);
    bounds.y = env->GetIntField(target, AwtComponent::yID);
    bounds.width = env->GetIntField(target, AwtComponent::widthID);
    bounds.height = env->GetIntField(target, AwtComponent::heightID);
    if (hwnd != newHwnd) {
        if (hwnd != NULL) {
            ::ReleaseDC(hwnd, hdc);
            retval = JAWT_LOCK_SURFACE_CHANGED;
        }
        hwnd = newHwnd;
        hdc = ::GetDCEx(hwnd, NULL, DCX_CACHE|DCX_CLIPCHILDREN|DCX_CLIPSIBLINGS);
    }
    clipSize = 1;
    clip = &bounds;
    int screen = AwtWin32GraphicsDevice::DeviceIndexForWindow(hwnd);
    hpalette = AwtWin32GraphicsDevice::GetPalette(screen);

    return retval;

    CATCH_BAD_ALLOC_RET(JAWT_LOCK_ERROR);
}

jint JAWTOffscreenDrawingSurfaceInfo::Init(JAWTOffscreenDrawingSurface* parent)
{
    TRY;

    JNIEnv* env = parent->env;
    jobject target = parent->target;
    if (JNU_IsNull(env, target)) {
        DTRACE_PRINTLN("NULL target");
        return JAWT_LOCK_ERROR;
    }
    Win32SDOps * ops =
        (Win32SDOps *)((void*)env->GetLongField(target, jawtPDataID));
    if (ops == NULL) {
        DTRACE_PRINTLN("NULL ops");
        return JAWT_LOCK_ERROR;
    }
    ddrawSurface = ops->lpSurface;
    if (ddrawSurface == NULL) {
        DTRACE_PRINTLN("NULL lpSurface");
        return JAWT_LOCK_ERROR;
    }
    DXSurface *dxSurface = ddrawSurface->GetDXSurface();
    if (dxSurface == NULL) {
        DTRACE_PRINTLN("NULL dxSurface");
        return JAWT_LOCK_ERROR;
    }
    platformInfo = this;
    ds = parent;
    return 0;

    CATCH_BAD_ALLOC_RET(JAWT_LOCK_ERROR);
}

/* Drawing Surface */

JAWTDrawingSurface::JAWTDrawingSurface(JNIEnv* pEnv, jobject rTarget)
{
    TRY_NO_VERIFY;

    env = pEnv;
    target = env->NewGlobalRef(rTarget);
    Lock = LockSurface;
    GetDrawingSurfaceInfo = GetDSI;
    FreeDrawingSurfaceInfo = FreeDSI;
    Unlock = UnlockSurface;
    info.hwnd = NULL;
    info.hdc = NULL;
    info.hpalette = NULL;

    CATCH_BAD_ALLOC;
}

JAWTDrawingSurface::~JAWTDrawingSurface()
{
    TRY_NO_VERIFY;

    env->DeleteGlobalRef(target);

    CATCH_BAD_ALLOC;
}

JAWT_DrawingSurfaceInfo* JNICALL JAWTDrawingSurface::GetDSI
    (JAWT_DrawingSurface* ds)
{
    TRY;

    if (ds == NULL) {
        DTRACE_PRINTLN("Drawing Surface is NULL");
        return NULL;
    }
    JAWTDrawingSurface* pds = static_cast<JAWTDrawingSurface*>(ds);
    return &(pds->info);

    CATCH_BAD_ALLOC_RET(NULL);
}

void JNICALL JAWTDrawingSurface::FreeDSI
    (JAWT_DrawingSurfaceInfo* dsi)
{
    TRY_NO_VERIFY;

    DASSERTMSG(dsi != NULL, "Drawing Surface Info is NULL\n");

    JAWTDrawingSurfaceInfo* jdsi = static_cast<JAWTDrawingSurfaceInfo*>(dsi);

    ::ReleaseDC(jdsi->hwnd, jdsi->hdc);

    CATCH_BAD_ALLOC;
}

jint JNICALL JAWTDrawingSurface::LockSurface
    (JAWT_DrawingSurface* ds)
{
    TRY;

    if (ds == NULL) {
        DTRACE_PRINTLN("Drawing Surface is NULL");
        return JAWT_LOCK_ERROR;
    }
    JAWTDrawingSurface* pds = static_cast<JAWTDrawingSurface*>(ds);
    jint val = pds->info.Init(pds);
    if ((val & JAWT_LOCK_ERROR) != 0) {
        return val;
    }
    val = AwtComponent::GetDrawState(pds->info.hwnd);
    AwtComponent::SetDrawState(pds->info.hwnd, 0);
    return val;

    CATCH_BAD_ALLOC_RET(JAWT_LOCK_ERROR);
}

void JNICALL JAWTDrawingSurface::UnlockSurface
    (JAWT_DrawingSurface* ds)
{
    TRY_NO_VERIFY;

    if (ds == NULL) {
        DTRACE_PRINTLN("Drawing Surface is NULL");
        return;
    }
    JAWTDrawingSurface* pds = static_cast<JAWTDrawingSurface*>(ds);

    CATCH_BAD_ALLOC;
}

JAWTOffscreenDrawingSurface::JAWTOffscreenDrawingSurface(JNIEnv* pEnv,
                                                         jobject rTarget)
{
    TRY_NO_VERIFY;
    env = pEnv;
    target = env->NewGlobalRef(rTarget);
    Lock = LockSurface;
    GetDrawingSurfaceInfo = GetDSI;
    FreeDrawingSurfaceInfo = FreeDSI;
    Unlock = UnlockSurface;
    info.dxSurface = NULL;
    info.dx7Surface = NULL;

    CATCH_BAD_ALLOC;
}

JAWTOffscreenDrawingSurface::~JAWTOffscreenDrawingSurface()
{
    env->DeleteGlobalRef(target);
}

JAWT_DrawingSurfaceInfo* JNICALL JAWTOffscreenDrawingSurface::GetDSI
    (JAWT_DrawingSurface* ds)
{
    TRY;

    if (ds == NULL) {
        DTRACE_PRINTLN("Drawing Surface is NULL");
        return NULL;
    }
    JAWTOffscreenDrawingSurface* pds =
        static_cast<JAWTOffscreenDrawingSurface*>(ds);
    return &(pds->info);

    CATCH_BAD_ALLOC_RET(NULL);
}

void JNICALL JAWTOffscreenDrawingSurface::FreeDSI
    (JAWT_DrawingSurfaceInfo* dsi)
{
}

jint JNICALL JAWTOffscreenDrawingSurface::LockSurface
    (JAWT_DrawingSurface* ds)
{
    TRY;

    if (ds == NULL) {
        DTRACE_PRINTLN("Drawing Surface is NULL");
        return JAWT_LOCK_ERROR;
    }
    JAWTOffscreenDrawingSurface* pds =
        static_cast<JAWTOffscreenDrawingSurface*>(ds);
    jint val = pds->info.Init(pds);
    if ((val & JAWT_LOCK_ERROR) != 0) {
            return val;
    }
    DDrawSurface *ddrawSurface = pds->info.ddrawSurface;
    if (ddrawSurface == NULL) {
        return JAWT_LOCK_ERROR;
    }
    ddrawSurface->GetExclusiveAccess();
    DXSurface *dxSurface = ddrawSurface->GetDXSurface();
    if (!dxSurface) {
        return JAWT_LOCK_ERROR;
    }
    switch (dxSurface->GetVersionID()) {
    case VERSION_DX7:
        {
            pds->info.dx7Surface = dxSurface->GetDDSurface();
            break;
        }
    default:
        // Leave info values at default and return error
        DTRACE_PRINTLN1("unknown jawt offscreen version: %d\n",
                        dxSurface->GetVersionID());
        return JAWT_LOCK_ERROR;
    }
    return 0;

    CATCH_BAD_ALLOC_RET(JAWT_LOCK_ERROR);
}

void JNICALL JAWTOffscreenDrawingSurface::UnlockSurface
    (JAWT_DrawingSurface* ds)
{
    TRY_NO_VERIFY;

    if (ds == NULL) {
        DTRACE_PRINTLN("Drawing Surface is NULL");
        return;
    }
    JAWTOffscreenDrawingSurface* pds =
        static_cast<JAWTOffscreenDrawingSurface*>(ds);
    pds->info.ddrawSurface->ReleaseExclusiveAccess();

    CATCH_BAD_ALLOC;
}

/* C exports */

extern "C" JNIEXPORT JAWT_DrawingSurface* JNICALL DSGetDrawingSurface
    (JNIEnv* env, jobject target)
{
    TRY;

    // See if the target component is a java.awt.Component
    if (env->IsInstanceOf(target, jawtComponentClass)) {
        return new JAWTDrawingSurface(env, target);
    }
    // Sharing native offscreen surfaces is disabled by default in
    // this release.  Sharing is enabled via the -Dsun.java2d.offscreenSharing
    // flag.
    if (g_offscreenSharing && env->IsInstanceOf(target, jawtVImgClass)) {
        jobject sMgr, sData;
        sMgr = env->GetObjectField(target, jawtSMgrID);
        if (!sMgr) {
            return NULL;
        }
        sData = env->GetObjectField(sMgr, jawtSDataID);
        if (!sData || !(env->IsInstanceOf(sData, jawtW32ossdClass))) {
            return NULL;
        }
        return new JAWTOffscreenDrawingSurface(env, sData);
    } else {
        if (!g_offscreenSharing) {
            DTRACE_PRINTLN(
                "GetDrawingSurface target must be a Component");
        } else {
            DTRACE_PRINTLN(
                "GetDrawingSurface target must be a Component or VolatileImage");
        }
        return NULL;
    }


    CATCH_BAD_ALLOC_RET(NULL);
}

extern "C" JNIEXPORT void JNICALL DSFreeDrawingSurface
    (JAWT_DrawingSurface* ds)
{
    TRY_NO_VERIFY;

    if (ds == NULL) {
        DTRACE_PRINTLN("Drawing Surface is NULL");
    }
    delete static_cast<JAWTDrawingSurface*>(ds);

    CATCH_BAD_ALLOC;
}

extern "C" JNIEXPORT void JNICALL DSLockAWT(JNIEnv* env)
{
    // Do nothing on Windows
}

extern "C" JNIEXPORT void JNICALL DSUnlockAWT(JNIEnv* env)
{
    // Do nothing on Windows
}
