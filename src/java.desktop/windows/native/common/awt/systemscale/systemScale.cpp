/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include "systemScale.h"
#include <d2d1.h>
#include <jdk_util.h>
#ifndef MDT_EFFECTIVE_DPI
#define MDT_EFFECTIVE_DPI 0
#endif

void GetScreenDpi(HMONITOR hmon, float *dpiX, float *dpiY)
{
    unsigned x = 0;
    unsigned y = 0;

    // for debug purposes
    static float scale = -2.0f;
    if (scale == -2) {
        scale = -1;
        char *uiScale = getenv("J2D_UISCALE");
        if (uiScale != NULL) {
            scale = (float)strtod(uiScale, NULL);
            if (errno == ERANGE || scale <= 0) {
                scale = -1;
            }
        }
    }

    if (scale > 0) {
        *dpiX = *dpiY = scale;
        return;
    }

    typedef HRESULT(WINAPI D2D1CreateFactoryFunc)
                   (D2D1_FACTORY_TYPE, REFIID,
                    CONST D2D1_FACTORY_OPTIONS*, ID2D1Factory**);
    typedef HRESULT(WINAPI GetDpiForMonitorFunc)(HMONITOR, int, UINT*, UINT*);
    static HMODULE hLibSHCoreDll = NULL;
    static GetDpiForMonitorFunc *lpGetDpiForMonitor = NULL;

    if (hLibSHCoreDll == NULL) {
        hLibSHCoreDll = JDK_LoadSystemLibrary("shcore.dll");
        if (hLibSHCoreDll != NULL) {
            lpGetDpiForMonitor = (GetDpiForMonitorFunc*)GetProcAddress(
                hLibSHCoreDll, "GetDpiForMonitor");
        }
    }

    if (lpGetDpiForMonitor != NULL) {
        HRESULT hResult = lpGetDpiForMonitor(hmon,
                                             MDT_EFFECTIVE_DPI, &x, &y);
        if (hResult == S_OK) {
            *dpiX = static_cast<float>(x);
            *dpiY = static_cast<float>(y);
        }
    } else {
        static HMODULE d2dDll = NULL;
        static BOOL loadAttempted = FALSE;
        static D2D1CreateFactoryFunc *lpD2D1CreateFactory = NULL;
        if (!loadAttempted && d2dDll == NULL) {
            loadAttempted = TRUE;
            d2dDll = JDK_LoadSystemLibrary("d2d1.dll");
        }
        if (d2dDll != NULL && lpD2D1CreateFactory == NULL) {
            lpD2D1CreateFactory = (D2D1CreateFactoryFunc*)GetProcAddress(
                    d2dDll, "D2D1CreateFactory");
        }
        if (lpD2D1CreateFactory != NULL) {
            ID2D1Factory* m_pDirect2dFactory;
            HRESULT res = lpD2D1CreateFactory
                          (D2D1_FACTORY_TYPE_SINGLE_THREADED,
                           __uuidof(ID2D1Factory), NULL,
                           &m_pDirect2dFactory);
            if (res == S_OK) {
#pragma warning(push)
#pragma warning(disable : 4996) // GetDesktopDpi is deprecated.
                m_pDirect2dFactory->GetDesktopDpi(dpiX, dpiY);
#pragma warning(pop)
                m_pDirect2dFactory->Release();
            }
        }
    }
    return;
}

HMONITOR WINAPI getPrimaryMonitor()
{
    const POINT point = { 0, 0 };
    return MonitorFromPoint(point, MONITOR_DEFAULTTOPRIMARY);
}
