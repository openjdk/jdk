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

 /**
  * ddrawObject.cpp
  *
  * This file holds classes used to access DirectDraw functionality.
  * There are two main classes here used by the outside world:
  * DDraw and DDrawSurface.  DDraw holds the actual DirectDraw
  * device object, responsible for creating surfaces and doing other
  * device-wide operations.  DDraw also holds a pointer to a D3DContext,
  * which has the d3dObject and shared d3d drawing device for the
  * display device (see d3dObject.cpp).  DDrawSurface holds an individual
  * surface, such as the primary or an offscreen surface.
  * DDrawSurface also holds a pointer to the device-wide d3dContext
  * because some operations on the surface may actually be 3D methods
  * that need to be forwarded to the 3d drawing device.
  * The DirectDraw object and surfaces are wrapped by DXObject
  * and DXSurface classes in order to be able to generically handle
  * DDraw method calls without the caller having to worry about which
  * version of DirectX we are currently running with.
  * A picture might help to explain the hierarchy of objects herein:
  *
  *                  DDraw (one per display device)
  *                   field: DXObject *dxObject
  *                   field: DXSurface *lpPrimary
  *                   field: D3DContext *d3dContext
  *
  *
  *                  DXObject (one per display device)
  *                   field: IDirectDraw7 (Actual DirectX objects)
  *
  *
  *                  DDrawSurface (one per offscreen or onscreen surface)
  *                   field: DXSurface (for ddraw operations)
  *
  *                  DXSurface (wrapper for DirectDraw operations)
  *                   field: IDirectDrawSurface7 (DirectX object)
  *
  * The wrapper classes work by using the same method calls as the
  * actual DirectX calls and simply forwarding those calls into the
  * the appropriate DirectX object that they contain.  The reason for
  * the indirection is that the subclasses can thus call into the
  * appropriate interface without the caller having to do that
  * explicitly.  So instead of something like:
  *         if (usingDX7) {
  *             dx7Surface->Lock();
  *         } else if (usingDXN) {
  *             dxNSurface->Lock();
  *         }
  * the caller can simply call:
  *         dxSurface->Lock();
  * and let the magic of subclassing handle the details of which interface
  * to call, depending on which interface was loaded (and thus which
  * subclass was instantiated).
  * The main difference between actual DirectX method calls and the
  * method calls of these wrapper classes is that we avoid using any
  * structures or parameters that are different between the versions
  * of DirectX that we currently support (DX7).  For example,
  * Lock takes DDSURFACEDESC2 structure for DX7.
  * For these methods, we pick an appropriate higher-level data
  * structure that can be cast and queried as appropriate at the
  * subclass level (in the Lock example, we pass a
  * SurfaceDataRasInfo structure, holds the data from the
  * call that we need.
  *
  * Note that the current implementation of the d3d and ddraw pipelines
  * relies heavily on DX7, so some of the abstraction concepts aren't
  * applicable. They may become more relevant once we get back to
  * version-independent implementation.
  */

#include "ddrawUtils.h"
#include "ddrawObject.h"
#include "WindowsFlags.h"
#include "java_awt_DisplayMode.h"

#include "D3DContext.h"

extern HINSTANCE                    hLibDDraw; // DDraw Library handle


#ifdef DEBUG
void StackTrace() {
    JNIEnv* env;
    jvm->AttachCurrentThread((void**)&env, NULL);
    jclass threadClass = env->FindClass("java/lang/Thread");
    jmethodID dumpStackMID = env->GetStaticMethodID(threadClass, "dumpStack", "()V");
    env->CallStaticVoidMethod(threadClass, dumpStackMID);
}
#endif

/**
 * Class DDrawDisplayMode
 */
DDrawDisplayMode::DDrawDisplayMode() :
        width(0), height(0), bitDepth(0), refreshRate(0) {}
DDrawDisplayMode::DDrawDisplayMode(DDrawDisplayMode& rhs) :
        width(rhs.width), height(rhs.height), bitDepth(rhs.bitDepth),
        refreshRate(rhs.refreshRate) {}
DDrawDisplayMode::DDrawDisplayMode(jint w, jint h, jint b, jint r) :
    width(w), height(h), bitDepth(b), refreshRate(r) {}

DDrawDisplayMode::~DDrawDisplayMode() {}


/**
 * Class DDraw::EnumDisplayModesParam
 */
DXObject::EnumDisplayModesParam::EnumDisplayModesParam(
    DDrawDisplayMode::Callback cb, void* ct) : callback(cb), context(ct) {}

DXObject::EnumDisplayModesParam::~EnumDisplayModesParam() {}

/**
 * DXObject
 * These classes handle operations specific to the DX7 interfaces
 */

DXObject::~DXObject()
{
    J2dTraceLn1(J2D_TRACE_INFO, "~DXObject: ddObject = 0x%x", ddObject);
    ddObject->Release();
    ddObject = NULL;
}

HRESULT DXObject::GetAvailableVidMem(DWORD caps, DWORD *total,
                                     DWORD *free)
{
    DDSCAPS2 ddsCaps;
    memset(&ddsCaps, 0, sizeof(ddsCaps));
    ddsCaps.dwCaps = caps;
    return ddObject->GetAvailableVidMem(&ddsCaps, total, free);
}

HRESULT DXObject::CreateSurface(DWORD dwFlags,
                                DWORD ddsCaps,
                                DWORD ddsCaps2,
                                LPDDPIXELFORMAT lpPf,
                                int width, int height,
                                DXSurface **lpDDSurface,
                                int numBackBuffers)
{
    IDirectDrawSurface7 *lpSurface;
    HRESULT ddResult;
    DDSURFACEDESC2 ddsd;
    memset(&ddsd, 0, sizeof(ddsd));
    ddsd.dwSize = sizeof(ddsd);
    ddsd.dwFlags = dwFlags;
    ddsd.ddsCaps.dwCaps = ddsCaps;
    ddsd.ddsCaps.dwCaps2 = ddsCaps2;
    ddsd.dwWidth = width;
    ddsd.dwHeight = height;
    ddsd.dwBackBufferCount = numBackBuffers;
    if (lpPf) {
        memcpy(&ddsd.ddpfPixelFormat, lpPf, sizeof(DDPIXELFORMAT));
    }
    ddResult = ddObject->CreateSurface(&ddsd, &lpSurface, NULL);
    if (ddResult != DD_OK) {
        DebugPrintDirectDrawError(ddResult, "DXObject::CreateSurface");
        return ddResult;
    }
    *lpDDSurface = new DXSurface(lpSurface);
    J2dTraceLn3(J2D_TRACE_INFO,
                "DXObject::CreateSurface: w=%-4d h=%-4d dxSurface=0x%x",
                width, height, *lpDDSurface);
    return DD_OK;
}

HRESULT DXObject::GetDisplayMode(DDrawDisplayMode &dm)
{
    HRESULT ddResult;
    DDSURFACEDESC2 ddsd;
    memset(&ddsd, 0, sizeof(ddsd));
    ddsd.dwSize = sizeof(ddsd);
    ddResult = ddObject->GetDisplayMode(&ddsd);
    dm.width = ddsd.dwWidth;
    dm.height = ddsd.dwHeight;
    dm.bitDepth = ddsd.ddpfPixelFormat.dwRGBBitCount;
    dm.refreshRate = ddsd.dwRefreshRate;
    return ddResult;
}

HRESULT DXObject::EnumDisplayModes(DDrawDisplayMode *dm,
                                   DDrawDisplayMode::Callback callback,
                                   void *context)
{
    DDSURFACEDESC2 ddsd;
    memset(&ddsd, 0, sizeof(ddsd));
    ddsd.dwSize = sizeof(ddsd);
    LPDDSURFACEDESC2 pDDSD;
    if (dm == NULL) {
        pDDSD = NULL;
    } else {
        ddsd.dwFlags = DDSD_WIDTH | DDSD_HEIGHT;
        ddsd.dwWidth = dm->width;
        ddsd.dwHeight = dm->height;
        ddsd.dwFlags |= DDSD_PIXELFORMAT;
        ddsd.ddpfPixelFormat.dwFlags = DDPF_RGB;
        ddsd.ddpfPixelFormat.dwSize = sizeof(DDPIXELFORMAT);
        // dm->bitDepth could be BIT_DEPTH_MULTI or some other invalid value,
        // we rely on DirectDraw to reject such mode
        ddsd.ddpfPixelFormat.dwRGBBitCount = dm->bitDepth;
        if (dm->refreshRate != java_awt_DisplayMode_REFRESH_RATE_UNKNOWN) {
            ddsd.dwFlags |= DDSD_REFRESHRATE;
            ddsd.dwRefreshRate = dm->refreshRate;
        }
        pDDSD = &ddsd;
    }

    EnumDisplayModesParam param(callback, context);

    HRESULT ddResult;
    ddResult = ddObject->EnumDisplayModes(DDEDM_REFRESHRATES, pDDSD,
                                          &param, EnumCallback);
    return ddResult;
}

HRESULT DXObject::CreateD3DObject(IDirect3D7 **d3dObject)
{
    HRESULT ddResult = ddObject->QueryInterface(IID_IDirect3D7,
                                                (void**)d3dObject);
    if (FAILED(ddResult)) {
        DebugPrintDirectDrawError(ddResult,
                                  "DXObject::CreateD3DObject: "\
                                  "query Direct3D7 interface failed");
    }
    return ddResult;
}

/**
 * Class DDraw
 */
DDraw::DDraw(DXObject *dxObject) {
    J2dTraceLn(J2D_TRACE_INFO, "DDraw::DDraw");
    lpPrimary = NULL;
    d3dContext = NULL;
    deviceUseD3D = useD3D;
    this->dxObject = dxObject;
}

DDraw::~DDraw() {
    J2dTraceLn(J2D_TRACE_INFO, "DDraw::~DDraw");
    if (dxObject) {
        delete dxObject;
    }
    if (d3dContext) {
        delete d3dContext;
    }
}

DDraw *DDraw::CreateDDrawObject(GUID *lpGUID, HMONITOR hMonitor) {

    J2dTraceLn(J2D_TRACE_INFO, "DDraw::CreateDDrawObject");
    HRESULT ddResult;
    DXObject *newDXObject;

    // First, try to create a DX7 object
    FnDDCreateExFunc ddCreateEx = NULL;

    if (getenv("NO_J2D_DX7") == NULL) {
        ddCreateEx = (FnDDCreateExFunc)
        ::GetProcAddress(hLibDDraw, "DirectDrawCreateEx");
    }

    if (ddCreateEx) {

        J2dTraceLn(J2D_TRACE_VERBOSE, "  Using DX7");
        // Success - we are going to use the DX7 interfaces
        // create ddraw object
        IDirectDraw7    *ddObject;

        ddResult = (*ddCreateEx)(lpGUID, (void**)&ddObject, IID_IDirectDraw7, NULL);
        if (ddResult != DD_OK) {
            DebugPrintDirectDrawError(ddResult,
                                      "DDraw::CreateDDrawObject: "\
                                      "DirectDrawCreateEx failed");
            return NULL;
        }
        ddResult = ddObject->SetCooperativeLevel(NULL,
                                                 (DDSCL_NORMAL |
                                                  DDSCL_FPUPRESERVE));
        if (ddResult != DD_OK) {
            DebugPrintDirectDrawError(ddResult,
                                      "DDraw::CreateDDrawObject: Error "\
                                      "setting cooperative level");
            return NULL;
        }
        newDXObject = new DXObject(ddObject, hMonitor);

    } else {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "DDraw::CreateDDrawObject: No DX7+, ddraw is disabled");
        return NULL;
    }

    return new DDraw(newDXObject);
}

BOOL DDraw::GetDDCaps(LPDDCAPS caps) {
    HRESULT ddResult;

    memset(caps, 0, sizeof(*caps));
    caps->dwSize = sizeof(*caps);
    ddResult = dxObject->GetCaps(caps, NULL);
    if (ddResult != DD_OK) {
        DebugPrintDirectDrawError(ddResult,
                                  "DDraw::GetDDCaps: dxObject->GetCaps failed");
        return FALSE;
    }
    return TRUE;
}

HRESULT DDraw::GetDDAvailableVidMem(DWORD *freeMem)
{
    DDrawDisplayMode dm;
    HRESULT ddResult;

    ddResult = dxObject->GetAvailableVidMem((DDSCAPS_VIDEOMEMORY |
                                             DDSCAPS_OFFSCREENPLAIN),
                                            NULL, freeMem);
    if (*freeMem == 0 || ddResult != DD_OK) {
        // Need to check it out ourselves: allocate as much as we can
        // and return that amount
        DDSURFACEDESC ddsd;
        ZeroMemory (&ddsd, sizeof(ddsd));
        ddsd.dwSize = sizeof( ddsd );
        HRESULT ddr = dxObject->GetDisplayMode(dm);
        if (ddr != DD_OK)
            DebugPrintDirectDrawError(ddr,
                "DDraw::GetDDAvailableVidMem: GetDisplayMode failed");
        int bytesPerPixel = dm.bitDepth;
        static int maxSurfaces = 20;
        DXSurface **lpDDSOffscreenVram = (DXSurface**)
            safe_Malloc(maxSurfaces*sizeof(DXSurface*));
        DWORD dwFlags = (DWORD)(DDSD_CAPS | DDSD_HEIGHT | DDSD_WIDTH);
        DWORD ddsCaps = (DWORD)(DDSCAPS_VIDEOMEMORY | DDSCAPS_OFFSCREENPLAIN);
        int size = 1024;
        int numVramSurfaces = 0;
        int bitsAllocated = 0;
        BOOL done = FALSE;
        while (!done) {
            HRESULT hResult =
                dxObject->CreateSurface(dwFlags, ddsCaps, NULL, size, size,
                                        &lpDDSOffscreenVram[numVramSurfaces]);
            if (hResult != DD_OK) {
                if (size > 1) {
                    size >>= 1;
                } else {
                    done = TRUE;
                }
            } else {
                *freeMem += size * size * bytesPerPixel;
                numVramSurfaces++;
                if (numVramSurfaces == maxSurfaces) {
                    // Need to reallocate surface holder array
                    int newMaxSurfaces = 2 * maxSurfaces;
                    DXSurface **newSurfaceArray = (DXSurface**)
                        safe_Malloc(maxSurfaces*sizeof(DXSurface*));
                    for (int i= 0; i < maxSurfaces; ++i) {
                        newSurfaceArray[i] = lpDDSOffscreenVram[i];
                    }
                    free(lpDDSOffscreenVram);
                    maxSurfaces = newMaxSurfaces;
                    lpDDSOffscreenVram = newSurfaceArray;
                }
            }
        }
        // Now release all the surfaces we allocated
        for (int i = 0; i < numVramSurfaces; ++i) {
            delete lpDDSOffscreenVram[i];
        }
        free(lpDDSOffscreenVram);
    }
    return ddResult;
}


DDrawSurface* DDraw::CreateDDOffScreenSurface(DWORD width, DWORD height,
                                              DWORD depth,
                                              jint transparency,
                                              DWORD surfaceTypeCaps)
{
    HRESULT ddResult;
    J2dTraceLn(J2D_TRACE_INFO, "DDraw::CreateDDOffScreenSurface");

    DXSurface *dxSurface;
    DWORD dwFlags, ddsCaps;

    // Create the offscreen surface
    dwFlags = DDSD_CAPS | DDSD_HEIGHT | DDSD_WIDTH;

    switch (transparency) {
    case TR_BITMASK:
        J2dTraceLn(J2D_TRACE_VERBOSE, "  bitmask surface");
        dwFlags |= DDSD_CKSRCBLT;
        /*FALLTHROUGH*/
    case TR_OPAQUE:
        ddsCaps = DDSCAPS_OFFSCREENPLAIN | surfaceTypeCaps;
    }
    J2dTraceLn1(J2D_TRACE_VERBOSE, "  creating %s surface",
                (transparency == TR_BITMASK ? "bitmask" : "opaque"));

    DDrawSurface* ret = NULL;
    if (dxObject) {
        ddResult = dxObject->CreateSurface(dwFlags, ddsCaps,
                                           NULL /*texture pixel format*/,
                                           width, height, &dxSurface);
        if (ddResult == DD_OK) {
            ret = new DDrawSurface(this, dxSurface);
        } else {
            DebugPrintDirectDrawError(ddResult,
                                      "DDraw::CreateDDOffScreenSurface: "\
                                      "dxObject->CreateSurface failed");
        }
    }

    return ret;
}

DDrawSurface* DDraw::CreateDDPrimarySurface(DWORD backBufferCount)
{
    HRESULT ddResult;
    DXSurface *dxSurface;
    DWORD dwFlags, ddsCaps;
    LPDIRECTDRAWSURFACE lpSurface(NULL);
    DDSURFACEDESC ddsd;
    memset(&ddsd, 0, sizeof(ddsd));
    ddsd.dwSize = sizeof(DDSURFACEDESC);

    J2dRlsTraceLn1(J2D_TRACE_INFO,
                   "DDraw::CreateDDPrimarySurface: back-buffers=%d",
                   backBufferCount);
    // create primary surface. There is one of these per ddraw object
    dwFlags = DDSD_CAPS;
    ddsCaps = DDSCAPS_PRIMARYSURFACE;
    if (backBufferCount > 0) {
        dwFlags |= DDSD_BACKBUFFERCOUNT;
        ddsCaps |= (DDSCAPS_FLIP | DDSCAPS_COMPLEX);

        // this is required to be able to use d3d for rendering to
        // a backbuffer
        if (deviceUseD3D) {
            ddsCaps |= DDSCAPS_3DDEVICE;
        }
    }
    DDrawSurface* ret;
    if (lpPrimary) {

        lpPrimary->GetExclusiveAccess();
        // REMIND: it looks like we need to release
        // d3d resources associated with this
        // surface prior to releasing the dd surfaces;
        ReleaseD3DContext();

        ddResult = lpPrimary->ReleaseSurface();
        if (ddResult != DD_OK) {
            DebugPrintDirectDrawError(ddResult,
                "DDraw::CreateDDPrimarySurface: failed releasing old primary");
        }
        lpPrimary->dxSurface = NULL;
    }
    if (dxObject) {
        ddResult = dxObject->CreateSurface(dwFlags, ddsCaps, &dxSurface,
                                           backBufferCount);
    } else {
        if (lpPrimary) {
            lpPrimary->ReleaseExclusiveAccess();
        }
        return NULL;
    }
    if (ddResult != DD_OK) {
        DebugPrintDirectDrawError(ddResult,
                                  "DDraw::CreateDDPrimarySurface: "\
                                  "CreateSurface failed");
        if (lpPrimary) {
            lpPrimary->ReleaseExclusiveAccess();
        }
        return NULL;
    }

    if (lpPrimary) {
        lpPrimary->SetNewSurface(dxSurface);
        lpPrimary->ReleaseExclusiveAccess();
    } else {
        lpPrimary = new DDrawPrimarySurface(this, dxSurface);
    }

    // The D3D context will be initialized when it's requested
    // by the D3DContext java class (see D3DContext.cpp:initNativeContext).

    ret = lpPrimary;
    J2dTraceLn1(J2D_TRACE_VERBOSE,
                "DDraw::CreateDDPrimarySurface new primary=0x%x", ret);
    return ret;
}

void
DDraw::InitD3DContext()
{
    J2dTraceLn(J2D_TRACE_INFO, "DDraw::InitD3DContext");
    // note: the first time the context initialization fails,
    // deviceUseD3D is set to FALSE, and we never attempt to
    // initialize it again later.
    // For example, if the app switches to a display mode where
    // d3d is not supported, we disable d3d, but it stays disabled
    // even when we switch back to a supported mode
    if (deviceUseD3D) {
        if (d3dContext == NULL) {
            d3dContext = D3DContext::CreateD3DContext(this, dxObject);
        } else {
            d3dContext->CreateD3DDevice();
        }
    }
}

void
DDraw::ReleaseD3DContext()
{
    J2dTraceLn(J2D_TRACE_INFO, "DDraw::ReleaseD3DContext");
    if (d3dContext != NULL) {
        d3dContext->Release3DDevice();
    }
}

DDrawClipper* DDraw::CreateDDClipper() {
    HRESULT ddResult;

    LPDIRECTDRAWCLIPPER pClipper;

    J2dTraceLn(J2D_TRACE_INFO, "DDraw::CreateDDClipper");
    ddResult = dxObject->CreateClipper(0, &pClipper);

    if (ddResult != DD_OK) {
        DebugPrintDirectDrawError(ddResult, "DDraw::CreateDDClipper");
        return NULL;
    }

    return new DDrawClipper(pClipper);
}

BOOL DDraw::GetDDDisplayMode(DDrawDisplayMode& dm) {
    HRESULT ddResult;

    ddResult = dxObject->GetDisplayMode(dm);

    if (ddResult != DD_OK) {
        DebugPrintDirectDrawError(ddResult, "GetDDDisplayMode");
        return FALSE;
    }

    return TRUE;
}

HRESULT DDraw::SetDDDisplayMode(DDrawDisplayMode& dm) {

    J2dTraceLn4(J2D_TRACE_INFO, "DDraw::SetDisplayMode %dx%dx%d, %d",
                dm.width, dm.height, dm.bitDepth, dm.refreshRate);

    HRESULT ddResult;
    // Sleep so that someone can't programatically set the display mode
    // multiple times very quickly and accidentally crash the driver
    static DWORD prevTime = 0;
    DWORD currTime = ::GetTickCount();
    DWORD timeDiff = (currTime - prevTime);
    if (timeDiff < 500) {
        ::Sleep(500 - timeDiff);
    }
    prevTime = currTime;

    ddResult = dxObject->SetDisplayMode(dm.width, dm.height, dm.bitDepth,
                                        dm.refreshRate);

    return ddResult;
}

/**
 * Private callback used by EnumDisplayModes
 */
long WINAPI DXObject::EnumCallback(LPDDSURFACEDESC2 pDDSD,
    void* pContext)
{
    EnumDisplayModesParam* pParam =
        (EnumDisplayModesParam*)pContext;
    DDrawDisplayMode::Callback callback = pParam->callback;
    void* context = pParam->context;

    DDrawDisplayMode displayMode(pDDSD->dwWidth, pDDSD->dwHeight,
        pDDSD->ddpfPixelFormat.dwRGBBitCount, pDDSD->dwRefreshRate);
    (*callback)(displayMode, context);
    return DDENUMRET_OK;
}

BOOL DDraw::EnumDDDisplayModes(DDrawDisplayMode* constraint,
    DDrawDisplayMode::Callback callback, void* context)
{
    HRESULT ddResult;

    ddResult = dxObject->EnumDisplayModes(constraint, callback, context);

    if (ddResult != DD_OK) {
        DebugPrintDirectDrawError(ddResult, "DDraw::EnumDisplayModes");
        return FALSE;
    }

    return TRUE;
}

BOOL DDraw::RestoreDDDisplayMode() {
    HRESULT ddResult;

    J2dTraceLn(J2D_TRACE_INFO, "DDraw::RestoreDDDisplayMode");
    ddResult = dxObject->RestoreDisplayMode();

    if (ddResult != DD_OK) {
        DebugPrintDirectDrawError(ddResult, "DDraw::RestoreDDDisplayMode");
        return FALSE;
    }
    return TRUE;
}

HRESULT DDraw::SetCooperativeLevel(HWND hwnd, DWORD dwFlags)
{
    HRESULT ddResult;
    J2dTraceLn(J2D_TRACE_INFO, "DDraw::SetCooperativeLevel");
    ddResult = dxObject->SetCooperativeLevel(hwnd, dwFlags);
    /* On some hardware (Radeon 7500 and GeForce2), attempting
     * to use the d3d device created prior to running FS|EX
     * may cause a system crash.  A workaround is to restore
     * the primary surface and recreate the
     * 3d device on the restored surface.
     */
    if ((ddResult == DD_OK) && lpPrimary && d3dContext) {

        lpPrimary->GetExclusiveAccess();
        if (lpPrimary->IsLost() != DD_OK) {
            // Only bother with workaround if the primary has been lost
            // Note that this call may fail with DDERR_WRONGMODE if
            // the surface was created in a different mode, but we
            // do not want to propagate this (non-fatal) error.
            HRESULT res = lpPrimary->Restore();
            if (FAILED(res)) {
                DebugPrintDirectDrawError(res,
                                          "DDraw::SetCooperativeLevel:"
                                          " lpPrimary->Restore() failed");
            }
        }
        lpPrimary->ReleaseExclusiveAccess();
    }
    return ddResult;
}


DXSurface::DXSurface(IDirectDrawSurface7 *lpSurface)
{
    J2dTraceLn(J2D_TRACE_INFO, "DXSurface::DXSurface");
    this->lpSurface = lpSurface;
    this->versionID = VERSION_DX7;
    this->depthBuffer = NULL;
    memset(&ddsd, 0, sizeof(ddsd));
    ddsd.dwSize = sizeof(ddsd);
    lpSurface->GetSurfaceDesc(&ddsd);
    width = ddsd.dwWidth;
    height = ddsd.dwHeight;
}

HRESULT DXSurface::Restore() {
    J2dTraceLn(J2D_TRACE_INFO, "DXSurface::Restore");
    HRESULT resDepth = D3D_OK, resSurface;
    if (depthBuffer != NULL) {
        J2dTraceLn(J2D_TRACE_VERBOSE, "  restoring depth buffer");
        resDepth = depthBuffer->Restore();
    }
    // If this is an attached backbuffer surface, we should not
    // restore it explicitly, as it will be restored implicitly with the
    // primary surface's restoration. But we did need to restore the
    // depth buffer, because it won't be restored with the primary.
    if ((ddsd.ddsCaps.dwCaps & DDSCAPS_BACKBUFFER) != 0) {
        return resDepth;
    }
    resSurface = lpSurface->Restore();
    return FAILED(resDepth) ? resDepth : resSurface;
}
HRESULT DXSurface::Lock(RECT *lockRect, SurfaceDataRasInfo *pRasInfo,
                         DWORD dwFlags, HANDLE hEvent)
{
    J2dTraceLn(J2D_TRACE_INFO, "DXSurface::Lock");
    HRESULT retValue = lpSurface->Lock(lockRect, &ddsd, dwFlags, hEvent);
    if (SUCCEEDED(retValue) && pRasInfo) {
        // Someone might call Lock() just to synchronize, in which case
        // they don't care about the result and pass in NULL for pRasInfo
        pRasInfo->pixelStride = ddsd.ddpfPixelFormat.dwRGBBitCount / 8;
        pRasInfo->pixelBitOffset = ddsd.ddpfPixelFormat.dwRGBBitCount & 7;
        pRasInfo->scanStride = ddsd.lPitch;
        pRasInfo->rasBase = (void *) ddsd.lpSurface;
    }
    return retValue;
}

HRESULT DXSurface::AttachDepthBuffer(DXObject *dxObject,
                                     BOOL bAccelerated,
                                     DDPIXELFORMAT *pddpf)
{
    HRESULT res;

    J2dTraceLn(J2D_TRACE_INFO, "DXSurface::AttachDepthBuffer");
    J2dTraceLn1(J2D_TRACE_VERBOSE, "  bAccelerated=%d", bAccelerated);
    // we already have a depth buffer
    if (depthBuffer != NULL) {
        J2dTraceLn(J2D_TRACE_VERBOSE,
                   "  depth buffer already created");
        // we don't want to restore the depth buffer
        // here if it was lost, it will be restored when
        // the surface is restored.
        if (FAILED(res = depthBuffer->IsLost())) {
            J2dTraceLn(J2D_TRACE_WARNING,
                       "DXSurface::AttachDepthBuffer: depth buffer is lost");
        }
        return res;
    }

    DWORD flags = DDSD_WIDTH|DDSD_HEIGHT|DDSD_CAPS|DDSD_PIXELFORMAT;
    DWORD caps = DDSCAPS_ZBUFFER;
    if (bAccelerated) {
        caps |= DDSCAPS_VIDEOMEMORY;
    } else {
        caps |= DDSCAPS_SYSTEMMEMORY;
    }
    if (SUCCEEDED(res =
                  dxObject->CreateSurface(flags, caps, pddpf,
                                          GetWidth(), GetHeight(),
                                          (DXSurface **)&depthBuffer, 0)))
    {
        if (FAILED(res =
                   lpSurface->AddAttachedSurface(depthBuffer->GetDDSurface())))
        {
            DebugPrintDirectDrawError(res, "DXSurface::AttachDepthBuffer: "\
                                      "failed to attach depth buffer");
            depthBuffer->Release();
            delete depthBuffer;
            depthBuffer = NULL;
        }
        return res;
    }
    DebugPrintDirectDrawError(res, "DXSurface::AttachDepthBuffer: "\
                              "depth buffer creation failed");
    return res;
}

HRESULT DXSurface::GetAttachedSurface(DWORD dwCaps, DXSurface **bbSurface)
{
    J2dTraceLn(J2D_TRACE_INFO, "DXSurface::GetAttachedSurface");
    IDirectDrawSurface7 *lpDDSBack;
    HRESULT retValue;
    DDSCAPS2 ddsCaps;
    memset(&ddsCaps, 0, sizeof(ddsCaps));
    ddsCaps.dwCaps = dwCaps;

    retValue = lpSurface->GetAttachedSurface(&ddsCaps, &lpDDSBack);
    if (retValue == DD_OK) {
        *bbSurface = new DXSurface(lpDDSBack);
    }
    return retValue;
}

HRESULT DXSurface::SetClipper(DDrawClipper *pClipper)
{
    J2dTraceLn(J2D_TRACE_INFO, "DXSurface::SetClipper");
    // A NULL pClipper is valid; it means we want no clipper
    // on this surface
    IDirectDrawClipper *actualClipper = pClipper ?
                                        pClipper->GetClipper() :
                                        NULL;
    // Calling SetClipper(NULL) on a surface that currently does
    // not have a clipper can cause a crash on some devices
    // (e.g., Matrox G400), so only call SetClipper(NULL) if
    // there is currently a non-NULL clipper set on this surface.
    if (actualClipper || clipperSet) {
        clipperSet = (actualClipper != NULL);
        return lpSurface->SetClipper(actualClipper);
    }
    return DD_OK;
}

int DXSurface::GetSurfaceDepth()
{
    if (FAILED(lpSurface->GetSurfaceDesc(&ddsd))) {
        // Failure: return 0 as an error indication
        return 0;
    }
    return ddsd.ddpfPixelFormat.dwRGBBitCount;
}

/**
 * Class DDrawSurface
 *
 * This class handles all operations on DirectDraw surfaces.
 * Mostly, it is a wrapper for the standard ddraw operations on
 * surfaces, but it also provides some additional functionality.
 * There is a surfaceLock CriticalSection associated with every
 * DDrawSurface which is used to make each instance MT-safe.
 * In general, ddraw itself is MT-safe, but we need to ensure
 * that accesses to our internal variables are MT-safe as well.
 * For example, we may need to recreate the primary surface
 * (during a display-mode-set operation) or release a ddraw
 * surface (due to a call to GraphicsDevice.flush()). The surfaceLock
 * enables us to do these operations without putting other threads
 * in danger of dereferencing garbage memory.
 *
 * If a surface has been released but other threads are still
 * using it, most methods simply return DD_OK and the calling
 * thread can go about its business without worrying about the
 * failure.  Some methods (Lock and GetDC) return an error
 * code so that the caller does not base further operations on
 * an unsuccessful lock call.
 */

DDrawSurface::DDrawSurface()
{
    surfaceLock = new DDCriticalSection(this);
    //d3dObject = NULL;
}

DDrawSurface::DDrawSurface(DDraw *ddObject, DXSurface *dxSurface)
{
    surfaceLock = new DDCriticalSection(this);
    CRITICAL_SECTION_ENTER(*surfaceLock);
    this->ddObject = ddObject;
    this->dxSurface = dxSurface;
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDrawSurface::DDrawSurface: dxSurface=0x%x", dxSurface);
    CRITICAL_SECTION_LEAVE(*surfaceLock);
}

DDrawSurface::~DDrawSurface()
{
    ReleaseSurface();
    delete surfaceLock;
}

/**
 * This function can only be called when the caller has exclusive
 * access to the DDrawSurface object.  This is done because some
 * surfaces (e.g., the primary surface) must be released before a
 * new one can be created and the surfaceLock must be held during
 * the entire process so that no other thread can access the
 * lpSurface before the process is complete.
 */
void DDrawSurface::SetNewSurface(DXSurface *dxSurface)
{
    this->dxSurface = dxSurface;
}

HRESULT DDrawSurface::ReleaseSurface() {
    CRITICAL_SECTION_ENTER(*surfaceLock);
    if (!dxSurface) {
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        return DD_OK;
    }
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDrawSurface::ReleaseSurface: dxSurface=0x%x", dxSurface);
    FlushD3DContext();
    HRESULT retValue = dxSurface->Release();
    dxSurface = NULL;
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    return retValue;
}

HRESULT DDrawSurface::SetClipper(DDrawClipper* pClipper) {
    CRITICAL_SECTION_ENTER(*surfaceLock);
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDrawSurface::SetClipper: dxSurface=0x%x", dxSurface);
    if (!dxSurface) {
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        return DD_OK;
    }
    HRESULT retValue = dxSurface->SetClipper(pClipper);
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    return retValue;
}

HRESULT DDrawSurface::SetColorKey(DWORD dwFlags, LPDDCOLORKEY lpDDColorKey) {
    CRITICAL_SECTION_ENTER(*surfaceLock);
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDrawSurface::SetColorKey: dxSurface=0x%x", dxSurface);
    if (!dxSurface) {
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        return DD_OK;
    }
    HRESULT retValue = dxSurface->SetColorKey(dwFlags, lpDDColorKey);
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    return retValue;
}

HRESULT DDrawSurface::GetColorKey(DWORD dwFlags, LPDDCOLORKEY lpDDColorKey) {
    CRITICAL_SECTION_ENTER(*surfaceLock);
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDrawSurface::GetColorKey: dxSurface=0x%x", dxSurface);
    if (!dxSurface) {
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        return DDERR_NOCOLORKEY;
    }
    HRESULT retValue = dxSurface->GetColorKey(dwFlags, lpDDColorKey);
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    return retValue;
}

/**
 * NOTE: This function takes the surfaceLock critical section, but
 * does not release that lock. The
 * Unlock method for this surface MUST be called before anything
 * else can happen on the surface.  This is necessary to prevent the
 * surface from being released or recreated while it is being used.
 * See also Unlock(), GetDC(), and ReleaseDC().
 */
HRESULT DDrawSurface::Lock(LPRECT lockRect, SurfaceDataRasInfo *pRasInfo,
    DWORD dwFlags, HANDLE hEvent) {
    CRITICAL_SECTION_ENTER(*surfaceLock);
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDrawSurface::Lock: dxSurface=0x%x", dxSurface);
    if (!dxSurface) {
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        // Return error here so that caller does not assume
        // lock worked and perform operations on garbage data
        // based on that assumption
        return DDERR_INVALIDOBJECT;
    }

    FlushD3DContext();
    HRESULT retValue = dxSurface->Lock(lockRect, pRasInfo, dwFlags, hEvent);
    if (retValue != DD_OK) {
        // Failure should release CriticalSection: either the lock will
        // be attempted again (e.g., DDERR_SURFACEBUSY) or the lock
        // failed and DDUnlock will not be called.
        CRITICAL_SECTION_LEAVE(*surfaceLock);
    }

    return retValue;
}

HRESULT DDrawSurface::Unlock(LPRECT lockRect) {
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDrawSurface::Unlock: dxSurface=0x%x", dxSurface);
    if (!dxSurface) {
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        return DD_OK;
    }
    HRESULT retValue = dxSurface->Unlock(lockRect);
    if (retValue != DD_OK && lockRect) {
        // Strange and undocumented bug using pre-DX7 interface;
        // for some reason unlocking the same rectangle as we
        // locked returns a DDERR_NOTLOCKED error, but unlocking
        // NULL (the entire surface) seems to work instead.  It is
        // as if Lock(&rect) actually performs Lock(NULL) implicitly,
        // thus causing Unlock(&rect) to fail but Unlock(NULL) to
        // succeed.  Trap this error specifically and try the workaround
        // of attempting to unlock the whole surface instead.
        retValue = dxSurface->Unlock(NULL);
    }
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    return retValue;
}

HRESULT DDrawSurface::Blt(LPRECT destRect, DDrawSurface* pSrc,
    LPRECT srcRect, DWORD dwFlags, LPDDBLTFX lpDDBltFx) {
    LPDIRECTDRAWSURFACE lpSrc = NULL;
    DXSurface *dxSrcSurface = NULL;
    CRITICAL_SECTION_ENTER(*surfaceLock);
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDrawSurface::Blt: dxSurface=0x%x", dxSurface);
    if (!dxSurface) {
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        return DD_OK;
    }
    if (pSrc) {
        pSrc->GetExclusiveAccess();
        dxSrcSurface = pSrc->dxSurface;
        if (!dxSrcSurface || (dxSrcSurface->IsLost() != DD_OK)) {
            // If no src surface, then surface must have been released
            // by some other thread.  If src is lost, then we should not
            // attempt this operation (causes a crash on some framebuffers).
            // Return SURFACELOST error in IsLost() case to force surface
            // restoration as necessary.
            HRESULT retError;
            if (!dxSrcSurface) {
                retError = DD_OK;
            } else {
                retError = DDERR_SURFACELOST;
            }
            pSrc->ReleaseExclusiveAccess();
            CRITICAL_SECTION_LEAVE(*surfaceLock);
            return retError;
        }
        pSrc->FlushD3DContext();
    }
    FlushD3DContext();
    HRESULT retValue = dxSurface->Blt(destRect, dxSrcSurface, srcRect, dwFlags,
                                      lpDDBltFx);
    if (pSrc) {
        pSrc->ReleaseExclusiveAccess();
    }
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    return retValue;
}

void DDrawSurface::FlushD3DContext(BOOL bForce) {
    D3DContext *d3dContext = ddObject->GetD3dContext();
    if (d3dContext) {
        d3dContext->FlushD3DQueueForTarget(bForce ? NULL : this);
    }
}

HRESULT DDrawSurface::Flip(DDrawSurface* pDest, DWORD dwFlags) {
    J2dTraceLn2(J2D_TRACE_INFO, "DDrawSurface::Flip this=0x%x pDest=0x%x",
                this, pDest);
    CRITICAL_SECTION_ENTER(*surfaceLock);
    if (!dxSurface) {
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        return DD_OK;
    }
    pDest->GetExclusiveAccess();
    if (!pDest->dxSurface) {
        pDest->ReleaseExclusiveAccess();
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        return DD_OK;
    }
    // Flush the queue unconditionally
    FlushD3DContext(TRUE);
    HRESULT retValue = dxSurface->Flip(dwFlags);
    pDest->ReleaseExclusiveAccess();
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    return retValue;
}

HRESULT DDrawSurface::IsLost() {
    CRITICAL_SECTION_ENTER(*surfaceLock);
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDrawSurface::IsLost: dxSurface=0x%x", dxSurface);
    if (!dxSurface) {
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        return DD_OK;
    }
    HRESULT retValue = dxSurface->IsLost();
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    return retValue;
}

HRESULT DDrawSurface::Restore() {
    CRITICAL_SECTION_ENTER(*surfaceLock);
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDrawSurface::Restore: dxSurface=0x%x", dxSurface);
    if (!dxSurface) {
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        return DD_OK;
    }
    FlushD3DContext();
    HRESULT retValue = dxSurface->Restore();
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    return retValue;
}

/**
 * Returns the bit depth of the ddraw surface
 */
int DDrawSurface::GetSurfaceDepth() {
    int retValue = 0; // default value; 0 indicates some problem getting depth
    CRITICAL_SECTION_ENTER(*surfaceLock);
    if (dxSurface) {
        retValue = dxSurface->GetSurfaceDepth();
    }
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    return retValue;
}

/**
 * As in Lock(), above, we grab the surfaceLock in this function,
 * but do not release it until ReleaseDC() is called.  This is because
 * these functions must be called as a pair (they take a lock on
 * the surface inside the ddraw runtime) and the surface should not
 * be released or recreated while the DC is held.  The caveat is that
 * a failure in this method causes us to release the surfaceLock here
 * because we will not (and should not) call ReleaseDC if we are returning
 * an error from GetDC.
 */
HRESULT DDrawSurface::GetDC(HDC *pHDC) {
    *pHDC = (HDC)NULL;
    CRITICAL_SECTION_ENTER(*surfaceLock);
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDrawSurface::GetDC: dxSurface=0x%x", dxSurface);
    if (!dxSurface) {
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        return DDERR_GENERIC;
    }
    FlushD3DContext();
    HRESULT ddResult = dxSurface->GetDC(pHDC);
    if (ddResult != DD_OK) {
        DebugPrintDirectDrawError(ddResult, "DDrawSurface::GetDC");
        if (*pHDC != (HDC)NULL) {
            // Probably cannot reach here; we got an error
            // but we also got a valid hDC.  Release it and
            // return NULL.  Note that releasing the DC also
            // releases the surfaceLock so we do not duplicate
            // that here
            ReleaseDC(*pHDC);
            *pHDC = (HDC)NULL;
        } else {
            CRITICAL_SECTION_LEAVE(*surfaceLock);
        }
    }
    return ddResult;
}

HRESULT DDrawSurface::ReleaseDC(HDC hDC) {
    J2dTraceLn1(J2D_TRACE_INFO,
                "DDrawSurface::ReleaseDC: dxSurface=0x%x", dxSurface);
    if (!hDC) {
        // We should not get here, but just in case we need to trap this
        // situation and simply noop.  Note that we do not release the
        // surfaceLock because we already released it when we failed to
        // get the HDC in the first place in GetDC
        J2dRlsTraceLn(J2D_TRACE_ERROR, "DDrawSurface::ReleaseDC: Null "\
                      "HDC received in ReleaseDC");
        return DD_OK;
    }
    if (!dxSurface) {
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        return DD_OK;
    }
    HRESULT retValue = dxSurface->ReleaseDC(hDC);
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    return retValue;
}

/**
 * Class DDrawPrimarySurface
 * This sublcass of DDrawSurface handles primary-specific
 * functionality.  In particular, the primary can have a
 * back buffer associated with it; DDrawPrimarySurface holds
 * the reference to that shared resource.
 */
DDrawPrimarySurface::DDrawPrimarySurface() : DDrawSurface()
{
    bbHolder = NULL;
}

DDrawPrimarySurface::DDrawPrimarySurface(DDraw *ddObject,
                                         DXSurface *dxSurface) :
    DDrawSurface(ddObject, dxSurface)
{
    bbHolder = NULL;
}

DDrawPrimarySurface::~DDrawPrimarySurface() {
}

HRESULT DDrawPrimarySurface::ReleaseSurface() {
    J2dTraceLn(J2D_TRACE_INFO, "DDrawPrimarySurface::ReleaseSurface");
    if (bbHolder) {
        delete bbHolder;
        bbHolder = NULL;
    }
    return DDrawSurface::ReleaseSurface();
}

void DDrawPrimarySurface::SetNewSurface(DXSurface *dxSurface)
{
    J2dTraceLn(J2D_TRACE_INFO, "DDrawPrimarySurface::SetNewSurface");
    if (bbHolder) {
        delete bbHolder;
        bbHolder = NULL;
    }
    DDrawSurface::SetNewSurface(dxSurface);
}

DDrawSurface* DDrawPrimarySurface::GetDDAttachedSurface(DWORD caps) {
    J2dTraceLn(J2D_TRACE_INFO, "DDrawPrimarySurface::GetDDAttachedSurface");
    if (!bbHolder) {
        HRESULT ddResult;
        DWORD dwCaps;
        if (caps == 0) {
            dwCaps = DDSCAPS_BACKBUFFER;
        } else {
            dwCaps = caps;
        }

        DXSurface *dxSurfaceBB;

        CRITICAL_SECTION_ENTER(*surfaceLock);
        if (!dxSurface) {
            CRITICAL_SECTION_LEAVE(*surfaceLock);
            return NULL;
        }
        ddResult = dxSurface->GetAttachedSurface(dwCaps, &dxSurfaceBB);
        CRITICAL_SECTION_LEAVE(*surfaceLock);
        if (ddResult != DD_OK) {
            DebugPrintDirectDrawError(ddResult,
                "DDrawPrimarySurface::GetDDAttachedSurface failed");
            return NULL;
        }
        bbHolder = new BackBufferHolder(dxSurfaceBB);
    }
    return new DDrawBackBufferSurface(ddObject, bbHolder);
}

/**
 * Primary restoration is different from non-primary because
 * of the d3dContext object.  There is a bug (4754180) on some
 * configurations (including Radeon and GeForce2) where using
 * the d3dDevice associated with a primary that is either lost
 * or has been restored can crash the system.  The solution is
 * to force a primary restoration at the appropriate time and
 * to recreate the d3d device associated with that primary.
 */
HRESULT DDrawPrimarySurface::Restore() {
    J2dTraceLn(J2D_TRACE_INFO, "DDrawPrimarySurface::Restore");
    AwtToolkit::GetInstance().SendMessage(WM_AWT_D3D_RELEASE_DEVICE,
                                          (WPARAM)ddObject, NULL);

    J2dTraceLn(J2D_TRACE_VERBOSE, "  Restoring primary surface");
    HRESULT res = DDrawSurface::Restore();
    if (SUCCEEDED(res) && bbHolder != NULL) {
        res = bbHolder->RestoreDepthBuffer();
    }
    return res;
}


/**
 * Class DDrawBackBufferSurface
 * This subclass handles functionality that is specific to
 * the back buffer surface.  The back buffer is a different
 * type of surface than a typical ddraw surface; it is
 * created by the primary surface and restored/released
 * implicitly by similar operations on the primary.
 * There is only one back buffer per primary, so each
 * DDrawBackBufferSurface object which refers to that object
 * shares the reference to it.  In order to appropriately
 * share this resource (both avoid creating too many objects
 * and avoid leaking those that we create), we use the
 * BackBufferHolder structure to contain the single ddraw
 * surface and register ourselves with that object.  This
 * allows us to have multi-threaded access to the back buffer
 * because if it was somehow deleted by another thread while we
 * are still using it, then the reference to our lpSurface will
 * be nulled-out for us and we will noop operations on that
 * surface (instead of crashing due to dereferencing a released
 * resource).
 */
DDrawBackBufferSurface::DDrawBackBufferSurface() : DDrawSurface() {
}

DDrawBackBufferSurface::DDrawBackBufferSurface(DDraw *ddObject,
                                               BackBufferHolder *holder) :
    DDrawSurface(ddObject, holder->GetBackBufferSurface())
{
    J2dTraceLn(J2D_TRACE_INFO,
               "DDrawBackBufferSurface::DDrawBackBufferSurface");
    CRITICAL_SECTION_ENTER(*surfaceLock);
    // Register ourselves with the back buffer container.
    // This means that we will be updated by that container
    // if the back buffer goes away.
    bbHolder = holder;
    bbHolder->Add(this);
    CRITICAL_SECTION_LEAVE(*surfaceLock);
}

/**
 * This destructor removes us from the list of back buffers
 * that hold pointers to the one true back buffer.  It also
 * nulls-out references to the ddraw and d3d objects to make
 * sure that our parent class does not attempt to release
 * those objects.
 */
DDrawBackBufferSurface::~DDrawBackBufferSurface() {
    J2dTraceLn(J2D_TRACE_INFO,
               "DDrawBackBufferSurface::~DDrawBackBufferSurface");
    CRITICAL_SECTION_ENTER(*surfaceLock);
    if (bbHolder) {
        // Tell the back buffer container that we are no
        // longer alive; otherwise it will try to update
        // us when the back buffer dies.
        bbHolder->Remove(this);
    }
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    // Note: our parent class destructor also calls ReleaseSurface,
    // but a function called within a destructor calls only the local
    // version, not the overridden version.  So we first call our
    // overridden ReleaseSurface to make sure our variables are nulled-out
    // before calling the superclass which will attempt to release
    // non-null objects.
    ReleaseSurface();
}

/**
 * Note that in this subclass' version of ReleaseSurface
 * we merely null-out the references to our objects.
 * They are shared resources and will be deleted elsewhere.
 */
HRESULT DDrawBackBufferSurface::ReleaseSurface() {
    J2dTraceLn(J2D_TRACE_INFO, "DDrawBackBufferSurface::ReleaseSurface");
    CRITICAL_SECTION_ENTER(*surfaceLock);
    bbHolder = NULL;
    dxSurface = NULL;
    CRITICAL_SECTION_LEAVE(*surfaceLock);
    return DD_OK;
}

/**
 * Class BackBufferHolder
 * This class holds the real ddraw/d3d back buffer surfaces.
 * It also contains a list of everyone that is currently
 * sharing those resources.  When the back buffer goes away
 * due to the primary being released or deleted), then
 * we tell everyone on the list that the back buffer is
 * gone (by nulling out their references to that object)
 * and thus avoid dereferencing a released resource.
 */
BackBufferHolder::BackBufferHolder(DXSurface *backBuffer)
{
    this->backBuffer = backBuffer;
    bbList = NULL;
}

/**
 * The back buffer is going away; iterate through our
 * list and tell all of those objects that information.
 * Then go ahead and actually release the back buffer's
 * resources.
 */
BackBufferHolder::~BackBufferHolder()
{
    bbLock.Enter();
    BackBufferList *bbListPtr = bbList;
    while (bbListPtr) {
        bbListPtr->backBuffer->ReleaseSurface();
        BackBufferList *bbTmp = bbListPtr;
        bbListPtr = bbListPtr->next;
        delete bbTmp;
    }
    // Note: don't release the ddraw surface; this is
    // done implicitly through releasing the primary
    //if (backBuffer3D) {
        //backBuffer3D->Release();
    //}
    bbLock.Leave();
}

/**
 * Add a new client to the list of objects sharing the
 * back buffer.
 */
void BackBufferHolder::Add(DDrawBackBufferSurface *surf)
{
    bbLock.Enter();
    BackBufferList *bbTmp = new BackBufferList;
    bbTmp->next = bbList;
    bbTmp->backBuffer = surf;
    bbList = bbTmp;
    bbLock.Leave();
}

/**
 * Remove a client from the sharing list.  This happens when
 * a client is deleted; we need to remove it from the list
 * so that we do not later go to update a defunct client
 * in the ~BackBufferHolder() destructor.
 */
void BackBufferHolder::Remove(DDrawBackBufferSurface *surf)
{
    bbLock.Enter();
    BackBufferList *bbListPtr = bbList;
    BackBufferList *bbListPtrPrev = NULL;
    while (bbListPtr) {
        if (bbListPtr->backBuffer == surf) {
            BackBufferList *bbTmp = bbListPtr;
            if (!bbListPtrPrev) {
                bbList = bbListPtr->next;
            } else {
                bbListPtrPrev->next = bbTmp->next;
            }
            delete bbTmp;
            break;
        }
        bbListPtrPrev = bbListPtr;
        bbListPtr = bbListPtr->next;
    }
    bbLock.Leave();
}

HRESULT BackBufferHolder::RestoreDepthBuffer() {
    J2dTraceLn(J2D_TRACE_INFO,
               "BackBufferHolder::RestoreDepthBuffer");
    if (backBuffer != NULL) {
        // this restores the depth-buffer attached
        // to the back-buffer. The back-buffer itself is restored
        // when the primary surface is restored, but the depth buffer
        // needs to be restored manually.
        return backBuffer->Restore();
    } else {
        return D3D_OK;
    }
}

/**
 * Class DDrawClipper
 */
DDrawClipper::DDrawClipper(LPDIRECTDRAWCLIPPER clipper) : lpClipper(clipper) {}

DDrawClipper::~DDrawClipper()
{
    if (lpClipper) {
        lpClipper->Release();
    }
}

HRESULT DDrawClipper::SetHWnd(DWORD dwFlags, HWND hwnd)
{
    return lpClipper->SetHWnd(dwFlags, hwnd);
}

HRESULT DDrawClipper::GetClipList(LPRECT rect, LPRGNDATA rgnData,
                                  LPDWORD rgnSize)
{
    return lpClipper->GetClipList(rect, rgnData, rgnSize);
}

LPDIRECTDRAWCLIPPER DDrawClipper::GetClipper()
{
    return lpClipper;
}
