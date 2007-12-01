/*
 * Copyright 1999-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

#include    "awt_MMStub.h"

//---------------------------------------------------------------------------
//  Basic API
//---------------------------------------------------------------------------

int      (WINAPI* g_pfnGetSystemMetrics)        (int);
MHND     (WINAPI* g_pfnMonitorFromWindow)       (HWND,BOOL);
MHND     (WINAPI* g_pfnMonitorFromRect)         (LPCRECT,BOOL);
MHND     (WINAPI* g_pfnMonitorFromPoint)        (POINT,BOOL);
BOOL     (WINAPI* g_pfnGetMonitorInfo)          (MHND,PMONITOR_INFO);
BOOL     (WINAPI* g_pfnEnumDisplayMonitors)     (HDC,LPCRECT,MON_ENUM_CALLBACK_PROC,LPARAM);
BOOL     (WINAPI* g_pfnEnumDisplayDevices)      (LPVOID,int,P_DISPLAY_DEVICE,DWORD);

BOOL __initMultipleMonitorStubs(void);
BOOL __initMultipleMonitorStubs(void)
{
    static BOOL fInitDone;
    HMODULE     hUser32;
    HMODULE     hUnicows = UnicowsLoader::GetModuleHandle();
    BOOL        retCode = FALSE;

    if (fInitDone)
    {
      retCode = g_pfnGetMonitorInfo != NULL;
        goto _RET_;
    }

    if ((hUser32 = GetModuleHandle(TEXT("USER32"))) &&
        (*(FARPROC*)&g_pfnGetSystemMetrics    = GetProcAddress(hUser32,"GetSystemMetrics")) &&
        (*(FARPROC*)&g_pfnMonitorFromWindow   = GetProcAddress(hUser32,"MonitorFromWindow")) &&
        (*(FARPROC*)&g_pfnMonitorFromRect     = GetProcAddress(hUser32,"MonitorFromRect")) &&
        (*(FARPROC*)&g_pfnMonitorFromPoint    = GetProcAddress(hUser32,"MonitorFromPoint")) &&
        (*(FARPROC*)&g_pfnEnumDisplayMonitors = GetProcAddress(hUser32,"EnumDisplayMonitors")) &&
        (*(FARPROC*)&g_pfnGetMonitorInfo      = GetProcAddress(IS_WIN95 ? hUnicows : hUser32,"GetMonitorInfoW")) &&
        (*(FARPROC*)&g_pfnEnumDisplayDevices  = GetProcAddress(IS_WIN95 ? hUnicows : hUser32,"EnumDisplayDevicesW")) &&
        (GetSystemMetrics(SM_CXVSCREEN) >= GetSystemMetrics(SM_CXSCREEN)) &&
        (GetSystemMetrics(SM_CYVSCREEN) >= GetSystemMetrics(SM_CYSCREEN)) )
    {
        fInitDone = TRUE;
        retCode = TRUE;
        goto _RET_;
    }
    g_pfnGetSystemMetrics    = NULL;
    g_pfnMonitorFromWindow   = NULL;
    g_pfnMonitorFromRect     = NULL;
    g_pfnMonitorFromPoint    = NULL;
    g_pfnGetMonitorInfo      = NULL;
    g_pfnEnumDisplayMonitors = NULL;
    g_pfnEnumDisplayDevices  = NULL;

    fInitDone = TRUE;
    retCode = FALSE;

_RET_:
    return retCode;
}

int WINAPI _getSystemMetrics(int nCode)
{
   int     retCode;
    if( __initMultipleMonitorStubs() )
    {
        retCode = g_pfnGetSystemMetrics(nCode);
        goto _RET_;
    }

    switch( nCode )
    {
        case SM_CMONITORS:
        case SM_SAMEDSPLFORMAT:
            return 1;

        case SM_XVSCREEN:
        case SM_YVSCREEN:
            return 0;

        case SM_CXVSCREEN:
            nCode = SM_CXSCREEN;
            break;

        case SM_CYVSCREEN:
            nCode = SM_CYSCREEN;
            break;
    }

    retCode = GetSystemMetrics(nCode);
_RET_:
    return retCode;
}


MHND WINAPI _monitorFromRect(LPCRECT prScreen, UINT nFlags)
{
    MHND    retCode = NULL;
    if( __initMultipleMonitorStubs() )
    {
        retCode = g_pfnMonitorFromRect(prScreen, nFlags);
        goto _RET_;
    }

    if( (prScreen->right < 0) || (prScreen->bottom < 0) )
    {
        goto _RET_;
    }
    {
        POINT   pP = {0,0};

        pP.x = prScreen->left;
        pP.y = prScreen->top;

        retCode = _monitorFromPoint(pP,nFlags);
    }

_RET_:
    return retCode;
}

MHND WINAPI _monitorFromWindow(HWND hwProbe, UINT nFlags)
{
    RECT    rR;
    MHND    retCode = NULL;

    if( __initMultipleMonitorStubs() )
    {
        retCode = g_pfnMonitorFromWindow(hwProbe, nFlags);
        goto _RET_;
    }

    if( nFlags & (MONITOR_DEFAULT_TO_PRIMARY | MONITOR_DEFAULT_TO_NEAR) )
    {
        retCode = PRIMARY_MONITOR;
        goto _RET_;
    }

    if( GetWindowRect(hwProbe, &rR) )
    {
        retCode = _monitorFromRect(&rR, nFlags);
        goto _RET_;
    }

_RET_:
    return retCode;
}

MHND WINAPI _monitorFromPoint(POINT ptProbe, UINT nFlags)
{
    MHND    retCode = NULL;
    if( __initMultipleMonitorStubs() )
    {
        retCode = g_pfnMonitorFromPoint(ptProbe,nFlags);
        goto _RET_;
    }

    if( nFlags & (MONITOR_DEFAULT_TO_PRIMARY | MONITOR_DEFAULT_TO_NEAR) )
    {
        goto _ASSIGN_;
    }

    if( (ptProbe.x <= 0) || (ptProbe.x > GetSystemMetrics(SM_CXSCREEN)) )
    {
        goto _RET_;
    }

    if( (ptProbe.y <= 0) || (ptProbe.y < GetSystemMetrics(SM_CYSCREEN)) )
    {
        goto _RET_;
    }
_ASSIGN_:
    retCode = PRIMARY_MONITOR;

_RET_:
    return retCode;
}

BOOL WINAPI _getMonitorInfo(MHND mhMon, PMONITOR_INFO pmMonInfo)
{
    RECT    rArea;
    BOOL    retCode = FALSE;

    if( __initMultipleMonitorStubs() )
    {
        retCode = g_pfnGetMonitorInfo(mhMon, pmMonInfo);
        goto _RET_;
    }

    if( mhMon != PRIMARY_MONITOR )
    {
        goto _RET_;
    }

    if( NULL == pmMonInfo )
    {
        goto _RET_;
    }

    if( FALSE == SystemParametersInfo(SPI_GETWORKAREA,0,&rArea,0) )
    {
        goto _RET_;
    }

    if( pmMonInfo->dwSize >= sizeof(MONITOR_INFO) )
    {
        pmMonInfo->rMonitor.left = 0;
        pmMonInfo->rMonitor.top  = 0;
        pmMonInfo->rMonitor.right  = GetSystemMetrics(SM_CXSCREEN);
        pmMonInfo->rMonitor.bottom = GetSystemMetrics(SM_CYSCREEN);
        pmMonInfo->rWork    = rArea;
        pmMonInfo->dwFlags  = MONITOR_INFO_FLAG_PRIMARY;

        if( pmMonInfo->dwSize >= sizeof(MONITOR_INFO_EXTENDED))
        {
            lstrcpy(((PMONITOR_INFO_EXTENDED)pmMonInfo)->strDevice,
            TEXT("DISPLAY") );
        }

        retCode = TRUE;
    }

_RET_:
    return retCode;
}

BOOL WINAPI _enumDisplayMonitors(
                                    HDC hDC,LPCRECT lrcSect,
                                    MON_ENUM_CALLBACK_PROC lpfnEnumProc,
                                    LPARAM lData
                                )
{
    BOOL    retCode     = FALSE;
    RECT    rToPass     = {0,0,0,0};
    RECT    rBorder     = {0,0,0,0};

    if( __initMultipleMonitorStubs() )
    {
        retCode = g_pfnEnumDisplayMonitors  (
                                                hDC, lrcSect,
                                                lpfnEnumProc,lData
                                            );
        goto _RET_;
    }

    if( !lpfnEnumProc )
    {
        goto _RET_;
    }

    rBorder.left   = 0;
    rBorder.top    = 0;
    rBorder.right  = GetSystemMetrics(SM_CXSCREEN);
    rBorder.bottom = GetSystemMetrics(SM_CYSCREEN);

    if( hDC )
    {
        RECT rSect = {0,0,0,0};
        HWND hWnd  = NULL;

        if( NULL == (hWnd = WindowFromDC(hDC)) )
        {
            goto _RET_;
        }

        switch( GetClipBox(hDC,&rSect) )
        {
            case NULLREGION:
                 goto _ASSIGN_;
            case ERROR:
                 goto _RET_;
            default:
                MapWindowPoints(NULL, hWnd, (LPPOINT)&rBorder, 2);
                if( TRUE == IntersectRect(&rToPass,&rSect,&rBorder) )
                {
                   break;
                }
        }

        rBorder = rToPass;
    }

    if( (NULL == lrcSect) || (TRUE == IntersectRect(&rToPass,lrcSect,&rBorder)) )
    {
        lpfnEnumProc(PRIMARY_MONITOR,hDC,&rToPass,lData);
    }
_ASSIGN_:
    retCode = TRUE;
_RET_:
    return retCode;
}

BOOL WINAPI _enumDisplayDevices (
                                    LPVOID lpReserved, int iDeviceNum,
                                    _DISPLAY_DEVICE * pDisplayDevice, DWORD dwFlags
                                )
{
    BOOL retCode = FALSE;
    if( __initMultipleMonitorStubs() )
    {
        retCode = g_pfnEnumDisplayDevices(lpReserved,iDeviceNum,pDisplayDevice,dwFlags);
    }

    return retCode;
}


//---------------------------------------------------------------------------
// Extended API.
//---------------------------------------------------------------------------
//  Globais
int         g_nMonitorCounter;
int         g_nMonitorLimit;
MHND*       g_hmpMonitors;
//  Callbacks
BOOL WINAPI clb_fCountMonitors(MHND,HDC,LPRECT,LPARAM);
BOOL WINAPI clb_fCountMonitors(MHND hMon,HDC hDC,LPRECT rRect,LPARAM lP)
{
    g_nMonitorCounter ++;
    return TRUE;
}
BOOL WINAPI clb_fCollectMonitors(MHND,HDC,LPRECT,LPARAM);
BOOL WINAPI clb_fCollectMonitors(MHND hMon,HDC hDC,LPRECT rRect,LPARAM lP)
{

    if( (g_nMonitorCounter < g_nMonitorLimit) && (NULL != g_hmpMonitors) )
    {
        g_hmpMonitors[g_nMonitorCounter] = hMon;
        g_nMonitorCounter ++;
    }

    return TRUE;
}
//  Tools
void __normaRectPos(RECT*,RECT,RECT);
HWND __createWindow0(MHND,LPCTSTR,LPCTSTR,DWORD,int,int,int,int,HWND,HMENU,HANDLE,LPVOID);
HWND __createWindow1(MHND,LPCTSTR,LPCTSTR,DWORD,int,int,int,int,HWND,HMENU,HANDLE,LPVOID);
void __normaRectPos(RECT* rDest,RECT rSrc,RECT rNorma)
{
    int nDX = rSrc.right - rSrc.left;
    int nDY = rSrc.bottom - rSrc.top;

    rDest->left  = rSrc.left + rNorma.left;
    rDest->top   = rSrc.top + rNorma.top;

    rDest->right     = rDest->left + nDX;
    rDest->bottom    = rDest->top + nDY;
}
HWND __createWindow0(   MHND hmMonitor,LPCTSTR lpClassName,LPCTSTR lpWindowName,
                        DWORD dwStyle,int x,int y,int nWidth,
                        int nHeight,HWND hWndParent,HMENU hMenu,
                        HANDLE hInstance,LPVOID lpParam )
{
    HWND    retCode = NULL;

    if( (NULL != hmMonitor) && (NULL != lpClassName) &&
        (NULL != lpWindowName) && (NULL != hInstance) )
    {
        RECT    rRW     = {0,0,0,0};
        RECT    rRM     = {0,0,0,0};
        RECT    rSect   = {0,0,0,0};

        SetRect(&rRW,x,y,x+nWidth,y+nHeight);

        if( TRUE == _monitorBounds(hmMonitor,&rRM) )
        {
            __normaRectPos(&rRW,rRW,rRM);

            IntersectRect(&rSect,&rRM,&rRW);

            if( TRUE == EqualRect(&rSect,&rRW) )
            {
                x = rSect.left;
                y = rSect.top;
                nWidth = rSect.right - rSect.left;
                nHeight = rSect.bottom - rSect.top;
                retCode = CreateWindow(
                                            lpClassName,lpWindowName,
                                            dwStyle,x,y,nWidth,
                                            nHeight,hWndParent,hMenu,
                                            (HINSTANCE)hInstance,lpParam
                                        );
            } else  {
                    //  A coisa indefinida. Nao tenho sabdoria o que
                    //  fazer aqui mesmo
                    //  E necessario perguntar Jeannette
                    }
        }
    }

    return retCode;
}
HWND __createWindow1(   MHND hmMonitor,LPCTSTR lpClassName,LPCTSTR lpWindowName,
                        DWORD dwStyle,int x,int y,int nWidth,
                        int nHeight,HWND hWndParent,HMENU hMenu,
                        HANDLE hInstance,LPVOID lpParam )
{
    HWND    retCode = NULL;

    if( (NULL != hmMonitor) && (NULL != lpClassName) &&
        (NULL != lpWindowName) && (NULL != hInstance) )
    {
        RECT    rRM     = {0,0,0,0};

        if( TRUE == _monitorBounds(hmMonitor,&rRM) )
        {
            HWND    wW          = NULL;
            BOOL    wasVisible  = (0 != (dwStyle & WS_VISIBLE));

            if( TRUE == wasVisible )
            {
                dwStyle &= ~WS_VISIBLE;
            }

            if( NULL != (wW = CreateWindow(
                                                lpClassName,lpWindowName,
                                                dwStyle,x,y,nWidth,
                                                nHeight,hWndParent,hMenu,
                                                (HINSTANCE)hInstance,lpParam
                                            )) )
            {
                RECT    rRW     = {0,0,0,0};
                RECT    rSect   = {0,0,0,0};

                GetWindowRect(wW,&rRW);

                __normaRectPos(&rRW,rRW,rRM);

                IntersectRect(&rSect,&rRM,&rRW);

                if( TRUE == EqualRect(&rSect,&rRW) )
                {
                    x = rSect.left;
                    y = rSect.top;
                    nWidth = rSect.right - rSect.left;
                    nHeight = rSect.bottom - rSect.top;

                    MoveWindow(wW,x,y,nWidth,nHeight,FALSE);

                    if( TRUE == wasVisible )
                    {
                        UpdateWindow(wW);
                        ShowWindow(wW,SW_SHOW);
                    }

                    retCode = wW;
                } else  {
                        //  A coisa indefinida. Nao sei o que
                        //  fazer aqui. E necessario perguntar Jeannette
                            DestroyWindow(wW);
                        }
            }
        }
    }

    return retCode;
}

//  Implementations
int WINAPI _countMonitors(void)
{
    g_nMonitorCounter = 0;

    _enumDisplayMonitors(NULL,NULL,clb_fCountMonitors,0L);

    return g_nMonitorCounter;

}
int WINAPI _collectMonitors(MHND* hmpMonitors,int nNum)
{
    int     retCode = 0;

    if( NULL != hmpMonitors )
    {
        g_nMonitorCounter   = 0;
        g_nMonitorLimit     = nNum;
        g_hmpMonitors       = hmpMonitors;

        _enumDisplayMonitors(NULL,NULL,clb_fCollectMonitors,0L);

        retCode             = g_nMonitorCounter;

        g_nMonitorCounter   = 0;
        g_nMonitorLimit     = 0;
        g_hmpMonitors       = NULL;

    }
    return retCode;
}
BOOL WINAPI _monitorBounds(MHND hmMonitor,RECT* rpBounds)
{
    BOOL retCode = FALSE;

    if( (NULL != hmMonitor) && (NULL != rpBounds)  )
    {
        MONITOR_INFO miInfo;

        memset((void*)(&miInfo),0,sizeof(MONITOR_INFO));
        miInfo.dwSize = sizeof(MONITOR_INFO);

        if( TRUE == (retCode = _getMonitorInfo(hmMonitor,&(miInfo))) )
        {
            (*rpBounds) = miInfo.rMonitor;
        }
    }
    return retCode;
}

HDC WINAPI _makeDCFromMonitor(MHND hmMonitor) {
    HDC retCode = NULL;

    if( NULL != hmMonitor ) {

        MONITOR_INFO_EXTENDED mieInfo;

        memset((void*)(&mieInfo),0,sizeof(MONITOR_INFO_EXTENDED));
        mieInfo.dwSize = sizeof(MONITOR_INFO_EXTENDED);

        if( TRUE == _getMonitorInfo(hmMonitor,(PMONITOR_INFO)(&mieInfo)) ) {
            HDC hDC = CreateDC(mieInfo.strDevice,NULL,NULL,NULL);

            if( NULL != hDC ) {
                retCode = hDC;
            }
        }
    }
    return retCode;
}

HWND WINAPI _createWindowOM( MHND hmMonitor,LPCTSTR lpClassName,LPCTSTR lpWindowName,
                    DWORD dwStyle,int x,int y,int nWidth,
                    int nHeight,HWND hWndParent,HMENU hMenu,
                    HANDLE hInstance,LPVOID lpParam )
{
    if( (CW_USEDEFAULT == x) || (CW_USEDEFAULT == y) ||
        (CW_USEDEFAULT == nWidth) || (CW_USEDEFAULT == nHeight) )
    {
        return __createWindow1   (
                                    hmMonitor,lpClassName,lpWindowName,
                                    dwStyle,x,y,nWidth,
                                    nHeight,hWndParent,hMenu,
                                    hInstance,lpParam
                                );
    }
    return __createWindow0   (
                                hmMonitor,lpClassName,lpWindowName,
                                dwStyle,x,y,nWidth,
                                nHeight,hWndParent,hMenu,
                                hInstance,lpParam
                            );
}
