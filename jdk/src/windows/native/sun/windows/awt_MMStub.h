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
#ifndef _INC_MMSTUB
#define _INC_MMSTUB

#ifndef     _WINDOWS_
#include    "windows.h"
#endif

#ifndef _AWT_H_
#include "awt.h"
#endif

#if !defined(_WIN32_WINNT) || (_WIN32_WINNT < 0x0500)

/*  Cdecl for C++               */
#ifdef __cplusplus
extern "C" {
#endif

/*  Constants                   */
#define SM_XVSCREEN                         76
#define SM_YVSCREEN                         77
#define SM_CXVSCREEN                        78
#define SM_CYVSCREEN                        79
#define SM_CMONITORS                        80
#define SM_SAMEDSPLFORMAT                   81

#define MONITOR_DEFAULT_TO_NULL             0x00000000
#define MONITOR_DEFAULT_TO_PRIMARY          0x00000001
#define MONITOR_DEFAULT_TO_NEAR             0x00000002



#define MONITOR_INFO_FLAG_PRIMARY           0x00000001

#define DISPLAY_DEVICE_ATTACHED_TO_DESKTOP  0x00000001
#define DISPLAY_DEVICE_MULTY_DRIVER         0x00000002
#define DISPLAY_DEVICE_PRIMARY_DEVICE       0x00000004
#define DISPLAY_DEVICE_MIRRORING_DRIVER     0x00000008


#define DISPLAY_DEVICE_VGA                  0x00000010

#define ENUM_CURRENT_SETTINGS               ((DWORD)-1)
#define ENUM_REGISTRY_SETTINGS              ((DWORD)-2)

#define PRIMARY_MONITOR                     ((MHND)0x42)


#define DEV_NAME_LEN                        32
#define DEV_STR_LEN                         128


//  Datatypes
typedef HANDLE                              MHND;
typedef BOOL (CALLBACK* MON_ENUM_CALLBACK_PROC)(MHND,HDC,LPRECT,LPARAM);

typedef struct  tagMONITOR_INFO
{
    DWORD       dwSize;
    RECT        rMonitor;
    RECT        rWork;
    DWORD       dwFlags;
} MONITOR_INFO, *PMONITOR_INFO;

typedef struct tagMONITOR_INFO_EXTENDED
{
   DWORD       dwSize;
    RECT        rMonitor;
    RECT        rWork;
    DWORD       dwFlags;
    TCHAR       strDevice[DEV_NAME_LEN];
} MONITOR_INFO_EXTENDED, *PMONITOR_INFO_EXTENDED;

typedef struct tagDISPLAY_DEVICE
{
    DWORD       dwSize;
    WCHAR        strDevName[DEV_NAME_LEN];
    WCHAR        strDevString[DEV_STR_LEN];
    DWORD       dwFlags;
    WCHAR       deviceID[128];
    WCHAR       deviceKey[128];
} _DISPLAY_DEVICE, *P_DISPLAY_DEVICE;

/*  Basic API's  */
BOOL WINAPI                     _enumDisplayMonitors(HDC,LPCRECT,MON_ENUM_CALLBACK_PROC,LPARAM);
BOOL WINAPI                     _enumDisplayDevices (LPVOID,int,P_DISPLAY_DEVICE,DWORD);
BOOL WINAPI                     _getMonitorInfo     (MHND,PMONITOR_INFO);
MHND WINAPI                     _monitorFromPoint   (POINT,UINT);
MHND WINAPI                      _monitorFromWindow  (HWND,UINT);
MHND WINAPI                     _monitorFromRect    (LPCRECT,UINT);
int WINAPI                      _getSystemMetrics   (int);

/*  Additional API's */
int WINAPI                      _countMonitors      (void);
int WINAPI                      _collectMonitors    (MHND*,int);
BOOL WINAPI                     _monitorBounds      (MHND,RECT*);
HDC WINAPI                      _makeDCFromMonitor  (MHND);
HWND WINAPI                     _createWindowOM     (MHND,LPCTSTR,LPCTSTR,DWORD,int,int,int,
                                                     int,HWND,HMENU,HANDLE,LPVOID);

#ifdef __cplusplus
}
#endif  /* __cplusplus */

#endif  /* !defined(_WIN32_WINNT) || (_WIN32_WINNT < 0x0500) */

#endif  /* _INC_MMSTUB */
