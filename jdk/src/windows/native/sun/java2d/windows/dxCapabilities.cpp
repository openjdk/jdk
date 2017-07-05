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

#include <windows.h>
#include <stdio.h>
#include <malloc.h>
#include "dxCapabilities.h"

/**
 * DxCapabilities encapsulates the DirectX capabilities of a display
 * device.  Typically, we run tests at startup on each display device
 * at the current display depth.  We record the results of those tests
 * in a capabilities object and also record those results in the registry.
 * The next time we run on this display device, we check whether we have
 * already recorded results for this device/depth in the registry and simply
 * use those values instead of re-running the tests.  The results of the
 * tests determine which ddraw/d3d capabilities we enable/disable at runtime.
 */

void DxCapabilities::Initialize(WCHAR *keyName)
{
    this->keyName = (WCHAR*)malloc((wcslen(keyName) + 1) * sizeof(WCHAR));
    wcscpy(this->keyName, keyName);
    RegistryKey regKey(keyName, KEY_READ);
    ddCreation = regKey.GetIntValue(DD_CREATION);
    ddSurfaceCreation = regKey.GetIntValue(DD_SURFACE_CREATION);

    d3dCapsValidity = regKey.GetIntValue(D3D_CAPS_VALIDITY);
    d3dDeviceCaps = regKey.GetIntValue(D3D_DEVICE_CAPS);
}

WCHAR *StringForValue(int value)
{
    switch (value) {
    case J2D_ACCEL_UNVERIFIED:
        return L"UNVERIFIED";
        break;
    case J2D_ACCEL_TESTING:
        return L"TESTING (may indicate crash during test)";
        break;
    case J2D_ACCEL_FAILURE:
        return L"FAILURE";
        break;
    case J2D_ACCEL_SUCCESS:
        return L"SUCCESS";
        break;
    default:
        return L"UNKNOWN";
        break;
    }
}

/**
 * PrintCaps is here for debugging purposes only
 */
void DxCapabilities::PrintCaps() {
    printf("    %S: %S\n", DD_CREATION, StringForValue(ddCreation));
    printf("    %S: %S\n", DD_SURFACE_CREATION, StringForValue(ddSurfaceCreation));
    printf("    %S: %S\n", D3D_CAPS_VALIDITY, StringForValue(d3dCapsValidity));
    printf("    %S: 0x%X\n", D3D_DEVICE_CAPS, d3dDeviceCaps);
}

void DxCapabilities::SetDdCreationCap(int value) {
    ddCreation = value;
    SetCap(DD_CREATION, value);
}

void DxCapabilities::SetDdSurfaceCreationCap(int value) {
    ddSurfaceCreation = value;
    SetCap(DD_SURFACE_CREATION, value);
}

void DxCapabilities::SetD3dCapsValidity(int value) {
    d3dCapsValidity = value;
    SetCap(D3D_CAPS_VALIDITY, value);
}

void DxCapabilities::SetD3dDeviceCaps(int value) {
    d3dDeviceCaps = value;
    SetCap(D3D_DEVICE_CAPS, value);
}

void DxCapabilities::SetCap(WCHAR *capName, int value) {
    RegistryKey regKey(keyName, KEY_WRITE);
    regKey.SetIntValue(capName, value, TRUE);
}
