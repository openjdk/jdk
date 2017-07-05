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

#ifndef DDRAWOBJECT_H
#define DDRAWOBJECT_H

#include <ddraw.h>
#include <d3d.h>
#include <jni.h>
#include <windows.h>
#include "Win32SurfaceData.h"
#include "GraphicsPrimitiveMgr.h"

#ifdef DEBUG
  #define DX_FUNC(func) do { \
          HRESULT ddr = (func); \
          if (FAILED(ddr)) \
              DebugPrintDirectDrawError(ddr, #func); \
  } while (0)
#else
  #define DX_FUNC(func) do { func; } while (0)
#endif

/**
 * Class for display modes
 */
class DDrawDisplayMode {

public:
    typedef void (*Callback)(DDrawDisplayMode&, void*);

public:
    DDrawDisplayMode();
    DDrawDisplayMode(DDrawDisplayMode& rhs);
    DDrawDisplayMode(jint w, jint h, jint b, jint r);
    virtual ~DDrawDisplayMode();

    jint width;
    jint height;
    jint bitDepth;
    jint refreshRate;

};

class DDrawSurface;
class DDrawClipper;
class DXSurface;

class D3DContext;


class DXObject {
private:
    IDirectDraw7 *ddObject;
    HMONITOR hMonitor;
    static long WINAPI EnumCallback(LPDDSURFACEDESC2 pDDSD, void* pContext);

public:
    DXObject(IDirectDraw7 *ddObject, HMONITOR hMonitor) {
        this->ddObject = ddObject;
        this->hMonitor = hMonitor;
    }
    virtual ~DXObject();
    HRESULT GetCaps(LPDDCAPS halCaps, LPDDCAPS helCaps) {
        return ddObject->GetCaps(halCaps, helCaps);
    }
    HMONITOR GetHMonitor() { return hMonitor; }
    HRESULT GetAvailableVidMem(DWORD caps, DWORD *total,
                                       DWORD *free);
    HRESULT CreateSurface(DWORD dwFlags,
                          DWORD ddsCaps, DWORD ddsCaps2,
                          LPDDPIXELFORMAT lpPf,
                          int width, int height,
                          DXSurface **lpDDSurface,
                          int numBackBuffers);

    HRESULT CreateSurface(DWORD dwFlags, DWORD ddsCaps,
                          LPDDPIXELFORMAT lpPf,
                          int width, int height,
                          DXSurface **lpDDSurface,
                          int numBackBuffers = 0)
    {
        return CreateSurface(dwFlags, ddsCaps, 0, lpPf, width, height,
                             lpDDSurface, numBackBuffers);
    }
    HRESULT CreateSurface(DWORD dwFlags, DWORD ddsCaps,
                          DXSurface **lpDDSurface)
    {
        return CreateSurface(dwFlags, ddsCaps, NULL, 0, 0, lpDDSurface, 0);
    }
    HRESULT CreateSurface(DWORD dwFlags, DWORD ddsCaps,
                          DXSurface **lpDDSurface,
                          int numBackBuffers)
    {
        return CreateSurface(dwFlags, ddsCaps, NULL, 0, 0, lpDDSurface,
                             numBackBuffers);
    }

    HRESULT CreateClipper(DWORD dwFlags,
                          LPDIRECTDRAWCLIPPER FAR *lplpDDClipper)
    {
        return ddObject->CreateClipper(dwFlags, lplpDDClipper, NULL);
    }
    HRESULT GetDisplayMode(DDrawDisplayMode &dm);
    HRESULT SetDisplayMode(DWORD width, DWORD height, DWORD depth,
                           DWORD refreshRate)
    {
        return ddObject->SetDisplayMode(width, height, depth, refreshRate, 0);
    }
    HRESULT EnumDisplayModes(DDrawDisplayMode *dm,
                             DDrawDisplayMode::Callback callback,
                             void *context);
    HRESULT RestoreDisplayMode() {
        return ddObject->RestoreDisplayMode();
    }
    HRESULT SetCooperativeLevel(HWND hWnd, DWORD dwFlags) {
        return ddObject->SetCooperativeLevel(hWnd,
                                             (dwFlags | DDSCL_FPUPRESERVE));
    }
    HRESULT CreateD3DObject(IDirect3D7 **d3dObject);
    /**
     * Structure for enumerating display modes; used to invoke the callback
     */
    class EnumDisplayModesParam {
    public:
        EnumDisplayModesParam(DDrawDisplayMode::Callback cb, void* ct);
        virtual ~EnumDisplayModesParam();
        DDrawDisplayMode::Callback callback;
        void* context;
    };
};

typedef HRESULT (WINAPI *FnDDCreateFunc)(GUID FAR * lpGUID,
    LPDIRECTDRAW FAR * lplpDD, IUnknown FAR * pUnkOuter);
typedef HRESULT (WINAPI *FnDDCreateExFunc)(GUID FAR * lpGUID,
    LPVOID * lplpDD, REFIID refIID, IUnknown FAR * pUnkOuter);
/**
 * Class for the direct draw object
 */
class DDraw  {

private:

public:
    DDraw(DXObject *dxObject);
    virtual ~DDraw();

    static DDraw *CreateDDrawObject(GUID *lpGUID, HMONITOR hMonitor);

    BOOL GetDDCaps(LPDDCAPS caps);
    HRESULT GetDDAvailableVidMem(DWORD *free);
    DDrawSurface* CreateDDOffScreenSurface(DWORD width, DWORD height,
                                           DWORD depth,
                                           jint transparency,
                                           DWORD surfaceTypeCaps);
    DDrawSurface* CreateDDPrimarySurface(DWORD backBufferCount);
    void InitD3DContext();
    void ReleaseD3DContext();
    void DisableD3D() { deviceUseD3D = FALSE; }
    BOOL IsD3DEnabled() { return deviceUseD3D; }
    D3DContext * GetD3dContext() { return d3dContext; };

    DDrawClipper* CreateDDClipper();

    BOOL GetDDDisplayMode(DDrawDisplayMode& dm);
    HRESULT SetDDDisplayMode(DDrawDisplayMode& dm);
    BOOL EnumDDDisplayModes(DDrawDisplayMode* constraint,
        DDrawDisplayMode::Callback callback, void* context);
    BOOL RestoreDDDisplayMode();

    HRESULT SetCooperativeLevel(HWND hwnd, DWORD dwFlags);

private:
    DXObject                *dxObject;
    DDrawSurface            *lpPrimary;
    D3DContext              *d3dContext;
    BOOL                    deviceUseD3D;
};


#define VERSION_DX7     0x00000007

/**
 * DXSurface class implementating DX 7 interfaces
 * (IDirectDrawSurface7)
 */
class DXSurface {
public:
    IDirectDrawSurface7 *lpSurface;
    DDSURFACEDESC2 ddsd;
    DXSurface* depthBuffer;

public:
    DXSurface() {
        versionID = VERSION_DX7; depthBuffer = NULL; clipperSet = FALSE;
    }

    DXSurface(IDirectDrawSurface7 *lpSurface);

    IDirectDrawSurface7 *GetDDSurface() { return lpSurface; }
    HRESULT Blt(RECT *destRect, DXSurface *lpSurfaceSrc,
                RECT *srcRect, DWORD dwFlags, LPDDBLTFX ddBltFx)
    {
        return lpSurface->Blt(destRect,
                              lpSurfaceSrc ?
                                  lpSurfaceSrc->GetDDSurface() :
                                  NULL,
                              srcRect, dwFlags, ddBltFx);
    }
    HRESULT Lock(RECT *lockRect, SurfaceDataRasInfo *pRasInfo,
                         DWORD dwFlags, HANDLE hEvent);
    HRESULT Unlock(RECT *unlockRect) {
        return lpSurface->Unlock(unlockRect);
    }
    HRESULT Flip(DWORD dwFlags) {
        return lpSurface->Flip(NULL, dwFlags);
    }
    HRESULT IsLost() {
        HRESULT res = D3D_OK;
        if (depthBuffer != NULL) {
            res = depthBuffer->IsLost();
        }
        return FAILED(res) ? res : lpSurface->IsLost();
    }
    HRESULT Restore();
    HRESULT GetDC(HDC *hDC) {
        return lpSurface->GetDC(hDC);
    }
    HRESULT ReleaseDC(HDC hDC) {
        return lpSurface->ReleaseDC(hDC);
    }
    ULONG   Release() {
        if (depthBuffer != NULL) {
            depthBuffer->Release();
            delete depthBuffer;
            depthBuffer = NULL;
        }
        return lpSurface->Release();
    }
    HRESULT SetClipper(DDrawClipper *pClipper);
    HRESULT SetColorKey(DWORD dwFlags, LPDDCOLORKEY lpDDColorKey) {
        return lpSurface->SetColorKey(dwFlags, lpDDColorKey);
    }
    HRESULT GetColorKey(DWORD dwFlags, LPDDCOLORKEY lpDDColorKey) {
        return lpSurface->GetColorKey(dwFlags, lpDDColorKey);
    }
    HRESULT GetAttachedSurface(DWORD dwCaps, DXSurface **bbSurface);
    int     GetSurfaceDepth();
    HRESULT AttachDepthBuffer(DXObject* dxObject,
                              BOOL bAccelerated,
                              DDPIXELFORMAT* pddpf);

    DWORD   GetWidth() { return width; }
    DWORD   GetHeight() { return height; }
    DWORD   GetVersionID() { return versionID; }

private:
    DWORD   width, height;
protected:
    DWORD versionID;
    BOOL clipperSet;
};


/**
 * Class for direct draw surfaces
 */
class DDrawSurface {

    friend class DDraw;
    friend class DDrawPrimarySurface;
    friend class DDrawBackBufferSurface;

protected:
    DDrawSurface();

public:
    virtual ~DDrawSurface();
    DDrawSurface(DDraw *ddObject, DXSurface *dxSurface);

public:
    virtual void    SetNewSurface(DXSurface *dxSurface);
    virtual HRESULT ReleaseSurface();
    virtual HRESULT SetClipper(DDrawClipper* pClipper);
    virtual HRESULT SetColorKey(DWORD dwFlags, LPDDCOLORKEY lpDDColorKey);
    virtual HRESULT GetColorKey(DWORD dwFlags, LPDDCOLORKEY lpDDColorKey);
    virtual HRESULT Lock(LPRECT lockRect = NULL, SurfaceDataRasInfo *pRasInfo = NULL,
                         DWORD dwFlags = DDLOCK_WAIT, HANDLE hEvent = NULL);
    virtual HRESULT Unlock(LPRECT lockRect = NULL);
    virtual HRESULT Blt(LPRECT destRect, DDrawSurface* pSrc,
                        LPRECT srcRect = NULL, DWORD dwFlags = DDBLT_WAIT,
                        LPDDBLTFX lpDDBltFx = NULL);
    virtual HRESULT Flip(DDrawSurface* pDest, DWORD dwFlags = DDFLIP_WAIT);
    virtual HRESULT IsLost();
    /**
     * Restores the surface or the depth buffer if the surface
     * represents an attached backbuffer surface. In the latter case
     * the surface itself will be restored implicitly with the primary.
     */
    virtual HRESULT Restore();
    virtual HRESULT GetDC(HDC *hDC);
    virtual HRESULT ReleaseDC(HDC hDC);
    void    GetExclusiveAccess() { CRITICAL_SECTION_ENTER(*surfaceLock); };
    void    ReleaseExclusiveAccess() { CRITICAL_SECTION_LEAVE(*surfaceLock); };
    virtual DDrawSurface* GetDDAttachedSurface(DWORD caps = 0)
        { return NULL; };
    virtual DXSurface *GetDXSurface() { return dxSurface; }
    void    FlushD3DContext(BOOL bForce = FALSE);
    int     GetSurfaceDepth();

protected:
    DDraw *ddObject;
    DXSurface   *dxSurface;
    CriticalSection *surfaceLock;
};

class BackBufferHolder;

/**
 * Class for direct draw primary surface
 */
class DDrawPrimarySurface : DDrawSurface {

    friend class DDraw;

protected:
    BackBufferHolder *bbHolder;

protected:
    DDrawPrimarySurface(DDraw *ddObject, DXSurface *dxSurface);
    DDrawPrimarySurface();

public:
    virtual ~DDrawPrimarySurface();
    virtual HRESULT ReleaseSurface();
    virtual void    SetNewSurface(DXSurface *dxSurface);
    virtual DDrawSurface* GetDDAttachedSurface(DWORD caps = 0);
    virtual HRESULT Restore();
};

/**
 * Class for direct draw back buffer surface
 */
class DDrawBackBufferSurface : DDrawSurface {

    friend class DDraw;
    friend class DDrawPrimarySurface;

protected:
    DDrawPrimarySurface *lpPrimary;
    BackBufferHolder *bbHolder;

protected:
    DDrawBackBufferSurface(DDraw *ddObject, BackBufferHolder *holder);
    DDrawBackBufferSurface();

public:
    virtual ~DDrawBackBufferSurface();
    virtual HRESULT ReleaseSurface();
};

/**
 * Linked list holding all references to DDrawBackBufferSurface
 * objects that share a single ddraw surface.  This class
 * is used by BackBufferHolder.
 */
class BackBufferList {
public:
    DDrawBackBufferSurface *backBuffer;
    BackBufferList *next;
};

/**
 * Class for storing the shared ddraw/d3d back buffer objects
 * and a list of all objects that use those shared surfaces.
 */
class BackBufferHolder {

public:
    BackBufferHolder(DXSurface *dxSurface);
    BackBufferHolder();
    ~BackBufferHolder();

    virtual void Add(DDrawBackBufferSurface *surf);
    virtual void Remove(DDrawBackBufferSurface *surf);
    DXSurface *GetBackBufferSurface() { return backBuffer; };
    HRESULT RestoreDepthBuffer();

protected:
    BackBufferList *bbList;             // linked list of objects that
                                        // share the ddraw/d3d surfaces
    DXSurface *backBuffer;
    CriticalSection bbLock;             // synchronize accesses to list
};


#ifdef DEBUG
void StackTrace();
// Critical Section debugging class
class DDCriticalSection : public CriticalSection {
private:
    DDrawSurface* lpSurface;
    int count;

public:
    DDCriticalSection(DDrawSurface* surface) : lpSurface(surface), count(0) {
    }
    void Enter() {
        ++count;
        //J2dTraceLn2(J2D_TRACE_VERBOSE,
        //            "DDCriticalSection::Enter for surface 0x%x count %d\n",
        //             lpSurface, count);
        CriticalSection::Enter();
    }
    void Leave() {
        //J2dTraceLn2(J2D_TRACE_VERBOSE,
        //            "DDCriticalSection::Leave for surface 0x%x count %d\n",
        //            lpSurface, count);
        if (count == 0) {
            //J2dTraceLn1(J2D_TRACE_VERBOSE,
            //            "DDCriticalSection::Leave Invalid "\
            //            "decrement in DDCriticalSection "\
            //            "for surface 0x%x\n",
            //            lpSurface);
            StackTrace();
        }
        CriticalSection::Leave();
        count--;
    }
};
#else
#define DDCriticalSection(x) CriticalSection()
#define StackTrace()
#endif

/**
 * Class for direct draw clippers
 */
class DDrawClipper {

    friend class DDraw;

private:
    DDrawClipper(LPDIRECTDRAWCLIPPER clipper);

public:
    virtual ~DDrawClipper();

public:
    HRESULT SetHWnd(DWORD dwFlags, HWND hwnd);
    HRESULT GetClipList(LPRECT lpRect, LPRGNDATA rgnData, LPDWORD rgnSize);
    LPDIRECTDRAWCLIPPER GetClipper();

private:
    LPDIRECTDRAWCLIPPER lpClipper;
};


#endif DDRAWOBJECT_H
