/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef DXINIT_H
#define DXINIT_H

#include "Win32SurfaceData.h"
#include "Trace.h"
#include "awt_MMStub.h"
#include "dxCapabilities.h"

// Registry definitions: these values are used to determine whether
// acceleration components are untested, working, or broken, depending
// on the results of testing
#define J2D_ACCEL_KEY_ROOT L"Software\\JavaSoft\\Java2D\\"
#define J2D_ACCEL_DRIVER_SUBKEY L"Drivers\\"
#define J2D_ACCEL_DX_NAME L"DXAcceleration"

void    InitDirectX();

void    GetDeviceKeyName(_DISPLAY_DEVICE *displayDevice, WCHAR *devName);

void    CheckFlags();

void    CheckRegistry();

BOOL    DDSetupDevice(DDrawObjectStruct *tmpDdInstance, DxCapabilities *dxCaps);

DDrawObjectStruct *CreateDevice(GUID *lpGUID, HMONITOR hMonitor);

BOOL CALLBACK EnumDeviceCallback(GUID FAR* lpGUID, LPSTR szName, LPSTR szDevice,
                                 LPVOID lParam, HMONITOR hMonitor);

BOOL    DDCreateObject();

#endif DXINIT_H
