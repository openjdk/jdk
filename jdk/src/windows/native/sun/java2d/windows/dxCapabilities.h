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

#ifndef DX_CAPABILITIES_H
#define DX_CAPABILITIES_H

#include "RegistryKey.h"

#define DD_CREATION            L"ddCreation"
#define DD_SURFACE_CREATION    L"ddSurfaceCreation"
#define D3D_CAPS_VALIDITY      L"d3dCapsValidity"
#define D3D_DEVICE_CAPS        L"d3dDeviceCaps"

class DxCapabilities {
private:
    WCHAR *keyName;
    int ddCreation;
    int ddSurfaceCreation;
    int d3dCapsValidity;
    int d3dDeviceCaps;

public:
        DxCapabilities() { keyName = NULL; }
        ~DxCapabilities() { if (keyName) free(keyName); }
    void Initialize(WCHAR *keyName);

    int GetDdCreationCap() { return ddCreation; }
    int GetDdSurfaceCreationCap() { return ddSurfaceCreation; }
    int GetD3dCapsValidity() { return d3dCapsValidity; }
    int GetD3dDeviceCaps() { return d3dDeviceCaps; }

    WCHAR *GetDeviceName() { return keyName; }

    void SetDdCreationCap(int value);
    void SetDdSurfaceCreationCap(int value);
    void SetD3dCapsValidity(int value);
    void SetD3dDeviceCaps(int value);

    void PrintCaps();

private:
    void SetCap(WCHAR *capName, int value);
};

#endif DX_CAPABILITIES_H
