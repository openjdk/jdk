/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "dxInit.h"
#include "ddrawUtils.h"
#include "D3DRuntimeTest.h"
#include "Trace.h"
#include "RegistryKey.h"
#include "WindowsFlags.h"
#include "Devices.h"

/**
 * This file holds the functions that handle the initialization
 * process for DirectX.  This process includes checking the
 * Windows Registry for information about the system and each display device,
 * running any necessary functionality tests, and storing information
 * out to the registry depending on the test results.
 *
 * In general, startup tests should only have to execute once;
 * they will run the first time we initialize ourselves on a
 * particular display device.  After that, we should just be able
 * to check the registry to see what the results of those tests were
 * and enable/disable DirectX support appropriately.  Startup tests
 * may be re-run in situations where we cannot check the display
 * device information (it may fail on some OSs) or when the
 * display device we start up on is different from the devices
 * we have tested on before (eg, the user has switched video cards
 * or maybe display depths).  The user may also force the tests to be re-run
 * by using the -Dsun.java2d.accelReset flag.
 */


WCHAR                       *j2dAccelKey;       // Name of java2d root key
WCHAR                       *j2dAccelDriverKey; // Name of j2d per-device key
int                         dxAcceleration;     // dx acceleration ability
                                                // according to the Registry
HINSTANCE                   hLibDDraw = NULL;   // DDraw Library handle
extern DDrawObjectStruct    **ddInstance;
extern CriticalSection      ddInstanceLock;
extern int                  maxDDDevices;
extern int                  currNumDevices;
extern char                 *javaVersion;


BOOL CheckDDCreationCaps(DDrawObjectStruct *tmpDdInstance,
                         DxCapabilities *dxCaps)
{
    J2dTraceLn(J2D_TRACE_INFO, "CheckDDCreationCaps");
    if (dxCaps == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "CheckDDCreationCaps: null dxCaps (new monitor?)");
        return FALSE;
    }
    // If we have not yet tested this configuration, test it now
    if (dxCaps->GetDdSurfaceCreationCap() == J2D_ACCEL_UNVERIFIED) {
        // First, create a non-d3d offscreen surface
        dxCaps->SetDdSurfaceCreationCap(J2D_ACCEL_TESTING);
        DDrawSurface *lpSurface =
            tmpDdInstance->ddObject->CreateDDOffScreenSurface(1, 1,
            32, TR_OPAQUE, DDSCAPS_VIDEOMEMORY);
        if (!lpSurface) {
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "CheckDDCreationCaps: failed to create basic "\
                          "ddraw surface");
            // problems creating basic ddraw surface - log it and return FALSE
            dxCaps->SetDdSurfaceCreationCap(J2D_ACCEL_FAILURE);
            return FALSE;
        }
        // Success; log it and continue
        dxCaps->SetDdSurfaceCreationCap(J2D_ACCEL_SUCCESS);
        delete lpSurface;
    } else if (dxCaps->GetDdSurfaceCreationCap() != J2D_ACCEL_SUCCESS) {
        // we have tested and failed previously; return FALSE
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "CheckDDCreationCaps: previous surface creation "\
                      "failure detected");
        return FALSE;
    }
    return TRUE;
}
/**
 * Called from AwtWin32GraphicsEnv's initScreens() after it initializes
 * all of the display devices.  This function initializes the global
 * DirectX state as well as the per-device DirectX objects.  This process
 * includes:
 *   - Checking native/Java flags to see what the user wants to manually
 *   enable/disable
 *   - Checking the registry to see if DirectX should be globally disabled
 *   - Enumerating the display devices (this returns unique string IDs
 *   for each display device)
 *   - Checking the registry for each device to see what we have stored
 *   there for this device.
 *   - Enumerate the ddraw devices
 *   - For each ddraw device, match it up with the associated device from
 *   EnumDisplayDevices.
 *   - If no registry entries exist, then run a series of tests using
 *   ddraw and d3d, storing the results in the registry for this device ID
 *   (and possibly color depth - test results may be bpp-specific)
 *   - based on the results of the registry storage or the tests, enable
 *   and disable various ddraw/d3d capabilities as appropriate.
 */
void InitDirectX()
{

    J2dRlsTraceLn(J2D_TRACE_INFO, "InitDirectX");
    // Check registry state for all display devices
    CheckRegistry();

    // Need to prevent multiple initializations of the DX objects/primaries.
    // CriticalSection ensures that this initialization will only happen once,
    // even if multiple threads call into this function at startup time.
    static CriticalSection initLock;
    initLock.Enter();
    static bool dxInitialized = false;
    if (dxInitialized) {
        initLock.Leave();
        return;
    }
    dxInitialized = true;
    initLock.Leave();

    // Check to make sure ddraw is not disabled globally
    if (useDD) {
        if (dxAcceleration == J2D_ACCEL_UNVERIFIED) {
            RegistryKey::SetIntValue(j2dAccelKey, J2D_ACCEL_DX_NAME,
                                     J2D_ACCEL_TESTING, TRUE);
        }
        hLibDDraw = ::LoadLibrary(TEXT("ddraw.dll"));
        if (!hLibDDraw) {
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "InitDirectX: Could not load library");
            SetDDEnabledFlag(NULL, FALSE);
            if (dxAcceleration == J2D_ACCEL_UNVERIFIED) {
                RegistryKey::SetIntValue(j2dAccelKey, J2D_ACCEL_DX_NAME,
                                         J2D_ACCEL_FAILURE, TRUE);
            }
            return;
        }
        if (dxAcceleration == J2D_ACCEL_UNVERIFIED) {
            RegistryKey::SetIntValue(j2dAccelKey, J2D_ACCEL_DX_NAME,
                                     J2D_ACCEL_SUCCESS, TRUE);
        }
        maxDDDevices = 1;
        ddInstance = (DDrawObjectStruct**)safe_Malloc(maxDDDevices *
            sizeof(DDrawObjectStruct*));
        memset(ddInstance, NULL, maxDDDevices * sizeof(DDrawObjectStruct*));
        if (!DDCreateObject()) {
            J2dRlsTraceLn(J2D_TRACE_ERROR,
                          "InitDirectX: Could not create ddraw object");
            SetDDEnabledFlag(NULL, FALSE);
        }
    }

    if (checkRegistry) {
        // diagnostic purposes: iterate through all of the registry
        // settings we have just checked or set and print them out to
        // the console
        printf("Registry Settings:\n");
        RegistryKey::PrintValue(j2dAccelKey, J2D_ACCEL_DX_NAME,
                                L"  DxAcceleration");
        // Now check the registry entries for all display devices on the system
        int deviceNum = 0;
        _DISPLAY_DEVICE displayDevice;
        displayDevice.dwSize = sizeof(displayDevice);
        while (EnumDisplayDevices(NULL, deviceNum, & displayDevice, 0) &&
               deviceNum < 20) // avoid infinite loop with buggy drivers
        {
            DxCapabilities caps;
            if (displayDevice.dwFlags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP) {
                // We only care about actual display devices.  Devices without
                // this flag could be virtual devices such as NetMeeting
                Devices::InstanceAccess devices;
                AwtWin32GraphicsDevice **devArray = devices->GetRawArray();
                int numScreens = devices->GetNumDevices();
                for (int i = 0; i < numScreens; ++i) {
                    MONITOR_INFO_EXTENDED *pMonInfo =
                        (PMONITOR_INFO_EXTENDED) devArray[i]->GetMonitorInfo();
                    if (wcscmp(pMonInfo->strDevice,
                               displayDevice.strDevName) == 0) {
                        // this GraphicsDevice matches this DisplayDevice; check
                        // the bit depth and grab the appropriate values from
                        // the registry
                        int bitDepth = devArray[i]->GetBitDepth();
                        WCHAR driverKeyName[2048];
                        WCHAR fullKeyName[2048];
                        GetDeviceKeyName(&displayDevice, driverKeyName);
                        swprintf(fullKeyName, L"%s%s\\%d", j2dAccelDriverKey,
                                 driverKeyName, bitDepth);
                        printf("  Device\\Depth: %S\\%d\n",
                               driverKeyName, bitDepth);
                        caps.Initialize(fullKeyName);
                        caps.PrintCaps();
                    }
                }
            }
            deviceNum++;
        }
    }
}

/**
 * Utility function that derives a unique name for this display
 * device.  We do this by combining the "name" and "string"
 * fields from the displayDevice structure.  Note that we
 * remove '\' characters from the dev name; since we're going
 * to use this as a registry key, we do not want all those '\'
 * characters to create extra registry key levels.
 */
void GetDeviceKeyName(_DISPLAY_DEVICE *displayDevice, WCHAR *devName)
{
    WCHAR *strDevName = displayDevice->strDevName;
    int devNameIndex = 0;
    for (size_t i = 0; i < wcslen(strDevName); ++i) {
        if (strDevName[i] != L'\\') {
            devName[devNameIndex++] = strDevName[i];
        }
    }
    devName[devNameIndex++] = L' ';
    devName[devNameIndex] = L'\0';
    wcscat(devName, displayDevice->strDevString);
}


/**
 * CheckRegistry first queries the registry for whether DirectX
 * should be disabled globally.  Then it enumerates the current
 * display devices and queries the registry for each unique display
 * device, putting the resulting values in the AwtWin32GraphicsDevice
 * array for each appropriate display device.
 */
void CheckRegistry()
{
    J2dTraceLn(J2D_TRACE_INFO, "CheckRegistry");
    if (accelReset) {
        RegistryKey::DeleteKey(j2dAccelKey);
    }
    dxAcceleration = RegistryKey::GetIntValue(j2dAccelKey, J2D_ACCEL_DX_NAME);
    if (dxAcceleration == J2D_ACCEL_TESTING ||
        dxAcceleration == J2D_ACCEL_FAILURE)
    {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "CheckRegistry: previous ddraw initialization failure"\
                      " detected, ddraw is disabled");
        // Disable ddraw if previous testing either crashed or failed
        SetDDEnabledFlag(NULL, FALSE);
        // Without DirectX, there is no point to the rest of the registry checks
        // so just return
        return;
    }

    // First, get the list of current display devices
    int deviceNum = 0;  // all display devices (virtual and not)
    int numDesktopDevices = 0;  // actual display devices
    _DISPLAY_DEVICE displayDevice;
    displayDevice.dwSize = sizeof(displayDevice);
    _DISPLAY_DEVICE displayDevices[20];
    while (deviceNum < 20 && // avoid infinite loop with buggy drivers
           EnumDisplayDevices(NULL, deviceNum, &displayDevice, 0))
    {
        if (displayDevice.dwFlags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP)
        {
            // We only care about actual display devices.  Devices without
            // this flag could be virtual devices such as NetMeeting
            J2dRlsTraceLn2(J2D_TRACE_VERBOSE,
                           "CheckRegistry: Found Display Device %d: %S",
                           deviceNum, displayDevice.strDevString);
            displayDevices[numDesktopDevices] = displayDevice;
            ++numDesktopDevices;
        }
        deviceNum++;
    }
    // Workaround for platforms that do not have the EnumDisplayDevices function
    // (i.e., NT4): just set up a single device that has the display name that
    // has already been assigned to the first (and only) graphics device.
    if (deviceNum == 0) {
        Devices::InstanceAccess devices;
        MONITOR_INFO_EXTENDED *pMonInfo =
            (PMONITOR_INFO_EXTENDED) devices->GetDevice(0)->GetMonitorInfo();
        wcscpy(displayDevices[0].strDevName, pMonInfo->strDevice);
        wcscpy(displayDevices[0].strDevString, L"DefaultDriver");
        J2dRlsTraceLn(J2D_TRACE_VERBOSE,
                      "CheckRegistry: Single Default Display Device detected");
        numDesktopDevices++;
    }

    // Now, check the current display devices against the list stored
    // in the registry already.
    // First, we get the current list of devices in the registry
    WCHAR subKeyNames[20][1024];
    int numSubKeys = 0;
    {
        RegistryKey hKey(j2dAccelDriverKey, KEY_ALL_ACCESS);
        DWORD buffSize = 1024;
        DWORD ret;
        while (numSubKeys < 20 &&  // same limit as display devices above
               ((ret = hKey.EnumerateSubKeys(numSubKeys, subKeyNames[numSubKeys],
                                            &buffSize)) ==
                ERROR_SUCCESS))
        {
            ++numSubKeys;
            buffSize = 1024;
        }
    }
    // Now, compare the display devices to the registry display devices
    BOOL devicesDifferent = FALSE;
    // Check that each display device is in the registry
    // Do this by checking each physical display device to see if it
    // is also in the registry.  If it is, do the same for the rest of
    // the physical devices.  If any device is not in the registry,
    // then there is a mis-match and we break out of the loop and
    // reset the registry.
    for (int i = 0; i < numDesktopDevices; ++i) {
        // Assume the device is not in the registry until proven otherwise
        devicesDifferent = TRUE;
        WCHAR driverName[2048];
        // Key name consists of (driver string) (driver name)
        // but we must remove the "\" characters from the driver
        // name to avoid creating too many levels
        GetDeviceKeyName(&(displayDevices[i]), driverName);
        for (int j = 0; j < numDesktopDevices; ++j) {
            if (wcscmp(driverName,
                       subKeyNames[j]) == 0)
            {
                // Found a match for this device; time to move on
                devicesDifferent = FALSE;
                break;
            }
        }
        if (devicesDifferent) {
            J2dTraceLn1(J2D_TRACE_VERBOSE,
                        "CheckRegistry: Display device %S not in registry",
                        driverName);
            break;
        }
    }
    // Something was different in the runtime versus the registry; delete
    // the registry entries to force testing and writing the results to
    // the registry
    if (devicesDifferent) {
        for (int i = 0; i < numSubKeys; ++i) {
            WCHAR driverKeyName[2048];
            swprintf(driverKeyName, L"%s%s", j2dAccelDriverKey,
                     subKeyNames[i]);
            J2dTraceLn1(J2D_TRACE_VERBOSE,
                        "CheckRegistry: Deleting registry key: %S",
                        driverKeyName);
            RegistryKey::DeleteKey(driverKeyName);
        }
    }

    // Now that we have the display devices and the registry in a good
    // start state, get or initialize the dx capabilities in the registry
    // for each display device
    for (deviceNum = 0; deviceNum < numDesktopDevices; ++deviceNum) {
        Devices::InstanceAccess devices;
        AwtWin32GraphicsDevice **devArray = devices->GetRawArray();
        int numScreens = devices->GetNumDevices();
        for (int i = 0; i < numScreens; ++i) {
            MONITOR_INFO_EXTENDED *pMonInfo =
                (PMONITOR_INFO_EXTENDED)devArray[i]->GetMonitorInfo();
            if (wcscmp(pMonInfo->strDevice,
                       displayDevices[deviceNum].strDevName) == 0)
            {
                // this GraphicsDevice matches this DisplayDevice; check
                // the bit depth and grab the appropriate values from
                // the registry
                int bitDepth = devArray[i]->GetBitDepth();
                WCHAR driverKeyName[2048];
                WCHAR fullKeyName[2048];
                // Key name consists of (driver string) (driver name)
                // but we must remove the "\" characters from the driver
                // name to avoid creating too many levels
                GetDeviceKeyName(&(displayDevices[i]), driverKeyName);
                swprintf(fullKeyName, L"%s%s\\%d", j2dAccelDriverKey,
                         driverKeyName, bitDepth);
                // - query registry for key with strDevString\\depth
                devArray[i]->GetDxCaps()->Initialize(fullKeyName);
            }
        }
    }
}


BOOL DDSetupDevice(DDrawObjectStruct *tmpDdInstance, DxCapabilities *dxCaps)
{
    J2dRlsTraceLn(J2D_TRACE_INFO, "DDSetupDevice");
    BOOL surfaceBasics = CheckDDCreationCaps(tmpDdInstance, dxCaps);
    if (!surfaceBasics) {
        goto FAILURE;
    }
    // create primary surface. There is one of these per ddraw object.
    // A D3DContext will be attempted to be created during the creation
    // of the primary surface.
    tmpDdInstance->primary = tmpDdInstance->ddObject->CreateDDPrimarySurface(
        (DWORD)tmpDdInstance->backBufferCount);
    if (!tmpDdInstance->primary) {
        goto FAILURE;
    }
    J2dRlsTraceLn(J2D_TRACE_VERBOSE,
                  "DDSetupDevice: successfully created primary surface");
    if (!tmpDdInstance->capsSet) {
        DDCAPS caps;
        tmpDdInstance->ddObject->GetDDCaps(&caps);
        tmpDdInstance->canBlt = (caps.dwCaps & DDCAPS_BLT);
        BOOL canCreateOffscreen = tmpDdInstance->canBlt &&
            (caps.dwVidMemTotal > 0);
        // Only register offscreen creation ok if we can Blt and if there
        // is available video memory.  Otherwise it
        // is useless functionality.  The Barco systems apparently allow
        // offscreen creation but do not allow hardware Blt's
        if ((caps.dwCaps & DDCAPS_NOHARDWARE) || !canCreateOffscreen) {
            AwtWin32GraphicsDevice::DisableOffscreenAccelerationForDevice(
                tmpDdInstance->hMonitor);
         if (caps.dwCaps & DDCAPS_NOHARDWARE) {
                // Does not have basic functionality we need; release
                // ddraw instance and return FALSE for this device.
                J2dRlsTraceLn(J2D_TRACE_ERROR,
                              "DDSetupDevice: Disabling ddraw on "\
                              "device: no hw support");
                goto FAILURE;
            }
        }
        tmpDdInstance->capsSet = TRUE;
    }
    // Do NOT create a clipper in full-screen mode
    if (tmpDdInstance->hwndFullScreen == NULL) {
        if (!tmpDdInstance->clipper) {
            // May have already created a clipper
            tmpDdInstance->clipper = tmpDdInstance->ddObject->CreateDDClipper();
        }
        if (tmpDdInstance->clipper != NULL) {
            if (tmpDdInstance->primary->SetClipper(tmpDdInstance->clipper)
                != DD_OK)
            {
                goto FAILURE;
            }
        } else {
            goto FAILURE;
        }
    }
    J2dRlsTraceLn(J2D_TRACE_VERBOSE,
                  "DDSetupDevice: successfully setup ddraw device");
    return TRUE;

FAILURE:
    J2dRlsTraceLn(J2D_TRACE_ERROR,
                  "DDSetupDevice: Failed to setup ddraw device");
    AwtWin32GraphicsDevice::DisableOffscreenAccelerationForDevice(
        tmpDdInstance->hMonitor);
    ddInstanceLock.Enter();
    // Do not release the ddInstance structure here, just flag it
    // as having problems; other threads may currently be using a
    // reference to the structure and we cannot release it out from
    // under them.  It will be released sometime later
    // when all DD resources are released.
    tmpDdInstance->accelerated = FALSE;
    ddInstanceLock.Leave();
    return FALSE;
}

DDrawObjectStruct *CreateDevice(GUID *lpGUID, HMONITOR hMonitor)
{
    J2dRlsTraceLn2(J2D_TRACE_INFO, "CreateDevice: lpGUID=0x%x hMon=0x%x",
                   lpGUID, hMonitor);
    DDrawObjectStruct *tmpDdInstance =
        (DDrawObjectStruct*)safe_Calloc(1, sizeof(DDrawObjectStruct));
    memset(tmpDdInstance, NULL, sizeof(DDrawObjectStruct));
    tmpDdInstance->valid = TRUE;
    tmpDdInstance->accelerated = TRUE;
    tmpDdInstance->capsSet = FALSE;
    tmpDdInstance->hMonitor = hMonitor;
    tmpDdInstance->hwndFullScreen = NULL;
    tmpDdInstance->backBufferCount = 0;
    tmpDdInstance->syncSurface = NULL;
    tmpDdInstance->context = CONTEXT_NORMAL;
    // Create ddraw object
    DxCapabilities *dxCaps =
        AwtWin32GraphicsDevice::GetDxCapsForDevice(hMonitor);
    if (dxCaps->GetDdCreationCap() == J2D_ACCEL_UNVERIFIED) {
        dxCaps->SetDdCreationCap(J2D_ACCEL_TESTING);
    } else if (dxCaps->GetDdCreationCap() != J2D_ACCEL_SUCCESS) {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "CreateDevice: previous failure detected, "\
                      "no ddraw device created");
        free(tmpDdInstance);
        return NULL;
    }
    tmpDdInstance->ddObject = DDraw::CreateDDrawObject(lpGUID, hMonitor);
    if (dxCaps->GetDdCreationCap() == J2D_ACCEL_TESTING) {
        dxCaps->SetDdCreationCap(tmpDdInstance->ddObject ? J2D_ACCEL_SUCCESS :
                                                           J2D_ACCEL_FAILURE);
    }
    if (!tmpDdInstance->ddObject) {
        // REMIND: might want to shut down ddraw (useDD == FALSE?)
        // if this error occurs
        free(tmpDdInstance);
        return NULL;
    }
    if (DDSetupDevice(tmpDdInstance, dxCaps)) {
        return tmpDdInstance;
    } else {
        free(tmpDdInstance);
        return NULL;
    }
}

BOOL CALLBACK EnumDeviceCallback(GUID FAR* lpGUID, LPSTR szName, LPSTR szDevice,
                                 LPVOID lParam, HMONITOR hMonitor)
{
    J2dTraceLn(J2D_TRACE_INFO, "EnumDeviceCallback");
    if (currNumDevices == maxDDDevices) {
        maxDDDevices *= 2;
        DDrawObjectStruct **tmpDDDevices =
            (DDrawObjectStruct**)safe_Malloc(maxDDDevices *
            sizeof(DDrawObjectStruct*));
        memset(tmpDDDevices, NULL, maxDDDevices * sizeof(DDrawObjectStruct*));
        for (int i = 0; i < currNumDevices; ++i) {
            tmpDDDevices[i] = ddInstance[i];
        }
        DDrawObjectStruct **oldDDDevices = ddInstance;
        ddInstance = tmpDDDevices;
        free(oldDDDevices);
    }
    if (hMonitor != NULL) {
        DDrawObjectStruct    *tmpDdInstance;
        if (ddInstance[currNumDevices] != NULL) {
            DDFreeSyncSurface(ddInstance[currNumDevices]);
            free(ddInstance[currNumDevices]);
        }
        tmpDdInstance = CreateDevice(lpGUID, hMonitor);
        ddInstance[currNumDevices] = tmpDdInstance;
        J2dTraceLn2(J2D_TRACE_VERBOSE,
                    "EnumDeviceCallback: ddInstance[%d]=0x%x",
                    currNumDevices, tmpDdInstance);
        // Increment currNumDevices on success or failure; a null device
        // is perfectly fine; we may have an unaccelerated device
        // in the midst of our multimon configuration
        currNumDevices++;
    }
    return TRUE;
}

typedef HRESULT (WINAPI *FnDDEnumerateFunc)(LPDDENUMCALLBACK cb,
    LPVOID lpContext);

/**
 * Create the ddraw object and the global
 * ddInstance structure.  Note that we do not take the ddInstanceLock
 * here; we assume that our callers are taking that lock for us.
 */
BOOL DDCreateObject() {
    LPDIRECTDRAWENUMERATEEXA lpDDEnum;

    J2dTraceLn(J2D_TRACE_INFO, "DDCreateObject");

    currNumDevices = 0;
    // Note that we are hardcoding this call to the ANSI version and not
    // using the ANIS-or-UNICODE macro name.  This is because there is
    // apparently a problem with the UNICODE function name not being
    // implemented under the win98 MSLU.  So we just use the ANSI version
    // on all flavors of Windows instead.
    lpDDEnum = (LPDIRECTDRAWENUMERATEEXA)
        GetProcAddress(hLibDDraw, "DirectDrawEnumerateExA");
    if (lpDDEnum) {
        HRESULT ddResult = (lpDDEnum)(EnumDeviceCallback,
            NULL, DDENUM_ATTACHEDSECONDARYDEVICES);
        if (ddResult != DD_OK) {
            DebugPrintDirectDrawError(ddResult,
                 "DDCreateObject: EnumDeviceCallback failed");
        }
    }
    if (currNumDevices == 0) {
        // Either there was no ddEnumEx function or there was a problem during
        // enumeration; just create a device on the primary.
        ddInstance[currNumDevices++] = CreateDevice(NULL, NULL);
    }
    return TRUE;
}
