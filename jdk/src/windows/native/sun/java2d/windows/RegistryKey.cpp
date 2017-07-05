/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * This class encapsulates simple interaction with the Windows Registry.
 * Use of the class should generally follow one of two forms:
 * (1) Need to get set just one value:
 *     int val = RegistryKey::GetIntValue(keyName, valueName);
 * This function creates a temporary registry key object, reads the value
 * from it, and closes the key.
 * (2) Need to get/set several values:
 *   {
 *     RegistryKey key(keyName, permissions);
 *     int val = key.GetIntValue(valueName);
 *     // other key operations
 *   }
 * Upon going out of scope, the RegistryKey object is automatically disposed,
 * which closes the key.  This is important: if you instead create an instance
 * like this:
 *     RegistryKey *key = new RegistryKey(keyName, permissions);
 * then you need to remember to delete that object, else you will leave a
 * registry key open, which could cause various problems such as leaks
 * and synchronization.
 *
 * One important item implemented in this class is the ability to force
 * a flush during a registry set operation.  This was implemented because
 * the primary usage for the registry at this time is in storing results
 * of testing; if we happen to crash (the application or system) during the
 * tests, we want to ensure that that information was recorded.  If we
 * rely on the default lazy behavior of the registry, then we have no way of
 * knowing whether our last settings into the registry were recorded before
 * the process died.
 */

#include <windows.h>
#include <stdio.h>
#include "Trace.h"
#include "WindowsFlags.h"
#include "RegistryKey.h"

/**
 * Constructs a registry key object.  permissions can be any of the
 * allowable values for keys, but are generally KEY_WRITE or
 * KEY_QUERY_VALUE.  If the key does not yet exist in the registry, it
 * will be created here.
 * Note that we use HKEY_CURRENT_USER as the registry hierarchy; this is
 * because we want any user (restricted or administrator) to be able to
 * read and write these test results; storing the results in a more central
 * location (e.g., HKEY_LOCAL_MACHINE) would prevent usage by users without
 * permission to read and write in that registry hierarchy.
 */
RegistryKey::RegistryKey(WCHAR *keyName, REGSAM permissions)
{
    hKey = NULL; // default value
    if (disableRegistry) {
        return;
    }
    DWORD disposition;
    DWORD ret = RegCreateKeyEx(HKEY_CURRENT_USER, keyName, 0, 0,
                               REG_OPTION_NON_VOLATILE, permissions,
                               NULL, &hKey, &disposition);
    if (ret != ERROR_SUCCESS) {
        PrintRegistryError(ret, "RegCreateKeyEx");
    }
}

/**
 * Destruction of the registry key object; this closes the key if
 * if was opened.
 */
RegistryKey::~RegistryKey()
{
    if (hKey) {
        RegCloseKey(hKey);
    }
}

DWORD RegistryKey::EnumerateSubKeys(DWORD index, WCHAR *subKeyName,
                                    DWORD *buffSize)
{
    if (disableRegistry) {
        // truncate the enumeration
        return ERROR_NO_MORE_ITEMS;
    }
    FILETIME lastWriteTime;
    return RegEnumKeyEx(hKey, index, subKeyName, buffSize, NULL, NULL, NULL,
                        &lastWriteTime);
}

/**
 * Retrieves the value of the given parameter from the registry.
 * If no such value exists in the registry, it returns the default
 * value of J2D_ACCEL_UNVERIFIED.
 */
int RegistryKey::GetIntValue(WCHAR *valueName)
{
    DWORD valueLength = 4;
    int regValue = J2D_ACCEL_UNVERIFIED;
    if (!disableRegistry) {
        RegQueryValueEx(hKey, valueName, NULL, NULL, (LPBYTE) & regValue,
                        & valueLength);
    }
    // QueryValue could fail if value does not exist, but in this
    // case regValue still equals the UNVERIFIED state, so no need to
    // catch failure.
    return regValue;
}

/**
 * Static method which opens a registry key with the given keyName and
 * calls GetIntValue(valueName) on that key.
 */
int RegistryKey::GetIntValue(WCHAR *keyName, WCHAR *valueName)
{
    RegistryKey key(keyName, KEY_QUERY_VALUE);
    return key.GetIntValue(valueName);
}

/**
 * Sets the specified value in the given key, returning TRUE for
 * success and FALSE for failure (errors are not expected in
 * this function and indicate some unknown problem with registry
 * interaction).  The flush parameter indicates that we should force
 * the registry to record this value after setting it (as opposed
 * to allowing the registry to write the value lazily).
 */
BOOL RegistryKey::SetIntValue(WCHAR *valueName, int regValue, BOOL flush)
{
    if (disableRegistry) {
        return TRUE;
    }
    if (!hKey) {
        PrintRegistryError(0, "Null hKey in SetIntValue");
        return FALSE;
    }
    DWORD valueLength = 4;
    DWORD ret = RegSetValueEx(hKey, valueName, 0, REG_DWORD, (LPBYTE)&regValue,
                        valueLength);
    if (ret != ERROR_SUCCESS) {
        PrintRegistryError(ret, "RegSetValueEx");
        return FALSE;
    }
    if (flush) {
        ret = RegFlushKey(hKey);
        if (ret != ERROR_SUCCESS) {
            PrintRegistryError(ret, "RegFlushKey");
            return FALSE;
        }
    }
    return TRUE;
}

/**
 * Static method which opens a registry key with the given keyName and
 * calls SetIntValue(valueName, regValue, flush) on that key.
 */
BOOL RegistryKey::SetIntValue(WCHAR *keyName, WCHAR *valueName,
                              int regValue, BOOL flush)
{
    RegistryKey key(keyName, KEY_WRITE);
    return key.SetIntValue(valueName, regValue, flush);
}

/**
 * Deletes the key with the given key name.  This is useful when using
 * the -Dsun.java2d.accelReset flag, which resets the registry values
 * to force the startup tests to be rerun and re-recorded.
 *
 */
void RegistryKey::DeleteKey(WCHAR *keyName)
{
    if (disableRegistry) {
        return;
    }
    // We should be able to do this with the ShDeleteKey() function, but
    // that is apparently not available on the ia64 sdk, so we revert back
    // to recursively deleting all subkeys until we can delete the key in
    // question
    DWORD buffSize = 1024;
    WCHAR subKeyName[1024];
    int subKeyIndex = 0;
    FILETIME lastWriteTime;
    HKEY hKey;
    DWORD ret = RegOpenKeyEx(HKEY_CURRENT_USER, keyName, 0, KEY_ALL_ACCESS,
                             &hKey);
    if (ret != ERROR_SUCCESS) {
        PrintRegistryError(ret, "DeleteKey, during RegOpenKeyEx");
    }
    while ((ret = RegEnumKeyEx(hKey, subKeyIndex, subKeyName, &buffSize,
                               NULL, NULL, NULL, &lastWriteTime)) ==
           ERROR_SUCCESS)
    {
        WCHAR subKeyBuffer[1024];
        swprintf(subKeyBuffer, L"%s\\%s", keyName, subKeyName);
        DeleteKey(subKeyBuffer);
        ++subKeyIndex;
        buffSize = 1024;
    }
    ret = RegCloseKey(hKey);
    if (ret != ERROR_SUCCESS) {
        PrintRegistryError(ret, "DeleteKey, during RegCloseKey");
    }
    ret = RegDeleteKey(HKEY_CURRENT_USER, keyName);
    if (ret != ERROR_SUCCESS) {
        PrintRegistryError(ret, "DeleteKey, during RegDeleteKey");
    }
}

void RegistryKey::PrintValue(WCHAR *keyName, WCHAR *valueName,
                             WCHAR *msg)
{
    int value = GetIntValue(keyName, valueName);
    switch (value) {
    case J2D_ACCEL_UNVERIFIED:
        printf("%S: %s\n", msg, "UNVERIFIED");
        break;
    case J2D_ACCEL_TESTING:
        printf("%S: %s\n", msg, "TESTING (may indicate crash during test)");
        break;
    case J2D_ACCEL_FAILURE:
        printf("%S: %s\n", msg, "FAILURE");
        break;
    case J2D_ACCEL_SUCCESS:
        printf("%S: %s\n", msg, "SUCCESS");
        break;
    default:
        printf("No registry value for key, value %S, %S\n",
                keyName, valueName);
        break;
    }
}

/**
 * Debugging utility: prints information about errors received
 * during interaction with the registry.
 */
void RegistryKey::PrintRegistryError(LONG errNum, char *message)
{
    WCHAR errString[255];
    int numChars =  FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM, NULL, errNum, 0,
        errString, 255, NULL);
    if (numChars == 0) {
        J2dTraceLn1(J2D_TRACE_ERROR, "problem with formatmessage, err = %d\n",
                    GetLastError());
    }
    J2dTraceLn3(J2D_TRACE_ERROR, "problem with %s, errNum, string = %d, %S\n",
                message, errNum, errString);
}
