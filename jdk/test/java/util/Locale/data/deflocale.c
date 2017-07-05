/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
/*
 *
 *
 * A simple tool to output all the installed locales on a Windows machine, and
 * corresponding Java default locale/file.encoding using PrintDefaultLocale
 *
 * WARNING:  This tool directly modifies the locale info in the Windows registry.
 * It may not work with the Windows versions after Windows XP SP2.  Also,
 * if the test did not complete or was manually killed, you will need to reset
 * the user default locale in the Control Panel manually.
 *
 * Usage: "deflocale.exe <java launcher> PrintDefaultLocale
 *
 * How to compile: "cl deflocale.c advapi32.lib"
 */
#include <windows.h>
#include <stdio.h>
#include <memory.h>

char* launcher;
char szBuffer[MAX_PATH];
LCID LCIDArray[1024];
int numLCIDs = 0;

void testLCID(int anLCID) {
    HKEY hk;

    printf("\n");
    printf("OS Locale (lcid: %x): ", anLCID);
    GetLocaleInfo(anLCID, LOCALE_SENGLANGUAGE, szBuffer, MAX_PATH);
    printf("%s (", szBuffer);
    GetLocaleInfo(anLCID, LOCALE_SENGCOUNTRY, szBuffer, MAX_PATH);
    printf("%s) - ", szBuffer);
    GetLocaleInfo(anLCID, LOCALE_IDEFAULTANSICODEPAGE, szBuffer, MAX_PATH);
    printf("%s\n", szBuffer);
    fflush(0);

    if (RegOpenKeyEx(HKEY_CURRENT_USER, "Control Panel\\International", 0, KEY_READ | KEY_WRITE, &hk) == ERROR_SUCCESS) {
        BYTE original[16];
        BYTE test[16];
        DWORD cb = 16;
        STARTUPINFO si;
        PROCESS_INFORMATION pi;

        RegQueryValueEx(hk, "Locale", 0, 0, original, &cb);
        sprintf(test, "%08x", anLCID);
        RegSetValueEx(hk, "Locale", 0, REG_SZ, test, cb);

        ZeroMemory(&si, sizeof(si));
        si.cb = sizeof(si);
        ZeroMemory(&pi, sizeof(pi));
        if (CreateProcess(NULL, launcher, NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi)==0) {
            printf("CreateProcess failed with the error code: %x\n", GetLastError());
        }

        WaitForSingleObject( pi.hProcess, INFINITE );

        RegSetValueEx(hk, "Locale", 0, REG_SZ, original, cb);
        RegCloseKey(hk);
    }
}

BOOL CALLBACK EnumLocaleProc(LPTSTR lpLocaleStr) {
    sscanf(lpLocaleStr, "%08x", &LCIDArray[numLCIDs]);
    numLCIDs ++;

    return TRUE;
}

int sortLCIDs(LCID * pLCID1, LCID * pLCID2) {
    if (*pLCID1 < *pLCID2) return (-1);
    if (*pLCID1 == *pLCID2) return 0;
    if (*pLCID1 > *pLCID2) return 1;
}

int main(int argc, char** argv) {
    OSVERSIONINFO osvi;
    LPTSTR commandline = GetCommandLine();
    int i;

    osvi.dwOSVersionInfoSize = sizeof(osvi);
    GetVersionEx(&osvi);
    printf("# OSVersionInfo\n");
    printf("# MajorVersion: %d\n", osvi.dwMajorVersion);
    printf("# MinorVersion: %d\n", osvi.dwMinorVersion);
    printf("# BuildNumber: %d\n", osvi.dwBuildNumber);
    printf("# CSDVersion: %s\n", osvi.szCSDVersion);
    printf("\n");
    fflush(0);

    launcher = strchr(commandline, ' ')+1;
    while (*launcher == ' ') {
        launcher++;
    }

    // Enumerate locales
    EnumSystemLocales(EnumLocaleProc, LCID_INSTALLED);

    // Sort LCIDs
    qsort(LCIDArray, numLCIDs, sizeof(LCID), (void *)sortLCIDs);

    // Execute enumeration of Java default locales
    for (i = 0; i < numLCIDs; i ++) {
        testLCID(LCIDArray[i]);
    }
}
