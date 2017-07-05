/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include "jvm.h"
#include "TimeZone_md.h"

#define VALUE_UNKNOWN           0
#define VALUE_KEY               1
#define VALUE_MAPID             2
#define VALUE_GMTOFFSET         3

#define MAX_ZONE_CHAR           256
#define MAX_MAPID_LENGTH        32

#define NT_TZ_KEY               "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Time Zones"
#define WIN_TZ_KEY              "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Time Zones"
#define WIN_CURRENT_TZ_KEY      "System\\CurrentControlSet\\Control\\TimeZoneInformation"

typedef struct _TziValue {
    LONG        bias;
    LONG        stdBias;
    LONG        dstBias;
    SYSTEMTIME  stdDate;
    SYSTEMTIME  dstDate;
} TziValue;

/*
 * Registry key names
 */
static void *keyNames[] = {
    (void *) L"StandardName",
    (void *) "StandardName",
    (void *) L"Std",
    (void *) "Std"
};

/*
 * Indices to keyNames[]
 */
#define STANDARD_NAME           0
#define STD_NAME                2

static int isNT = FALSE;        /* TRUE if it is NT. */

/*
 * Calls RegQueryValueEx() to get the value for the specified key. If
 * the platform is NT, 2000 or XP, it calls the Unicode
 * version. Otherwise, it calls the ANSI version and converts the
 * value to Unicode. In this case, it assumes that the current ANSI
 * Code Page is the same as the native platform code page (e.g., Code
 * Page 932 for the Japanese Windows systems.
 *
 * `keyIndex' is an index value to the keyNames in Unicode
 * (WCHAR). `keyIndex' + 1 points to its ANSI value.
 *
 * Returns the status value. ERROR_SUCCESS if succeeded, a
 * non-ERROR_SUCCESS value otherwise.
 */
static LONG
getValueInRegistry(HKEY hKey,
                   int keyIndex,
                   LPDWORD typePtr,
                   LPBYTE buf,
                   LPDWORD bufLengthPtr)
{
    LONG ret;
    DWORD bufLength = *bufLengthPtr;
    char val[MAX_ZONE_CHAR];
    DWORD valSize;
    int len;

    *typePtr = 0;
    if (isNT) {
        ret = RegQueryValueExW(hKey, (WCHAR *) keyNames[keyIndex], NULL,
                               typePtr, buf, bufLengthPtr);
        if (ret == ERROR_SUCCESS && *typePtr == REG_SZ) {
            return ret;
        }
    }

    valSize = sizeof(val);
    ret = RegQueryValueExA(hKey, (char *) keyNames[keyIndex + 1], NULL,
                           typePtr, val, &valSize);
    if (ret != ERROR_SUCCESS) {
        return ret;
    }
    if (*typePtr != REG_SZ) {
        return ERROR_BADKEY;
    }

    len = MultiByteToWideChar(CP_ACP, MB_ERR_INVALID_CHARS,
                              (LPCSTR) val, -1,
                              (LPWSTR) buf, bufLength/sizeof(WCHAR));
    if (len <= 0) {
        return ERROR_BADKEY;
    }
    return ERROR_SUCCESS;
}

/*
 * Produces custom name "GMT+hh:mm" from the given bias in buffer.
 */
static void customZoneName(LONG bias, char *buffer) {
    LONG gmtOffset;
    int sign;

    if (bias > 0) {
        gmtOffset = bias;
        sign = -1;
    } else {
        gmtOffset = -bias;
        sign = 1;
    }
    if (gmtOffset != 0) {
        sprintf(buffer, "GMT%c%02d:%02d",
                ((sign >= 0) ? '+' : '-'),
                gmtOffset / 60,
                gmtOffset % 60);
    } else {
        strcpy(buffer, "GMT");
    }
}

/*
 * Gets the current time zone entry in the "Time Zones" registry.
 */
static int getWinTimeZone(char *winZoneName, char *winMapID)
{
    TIME_ZONE_INFORMATION tzi;
    OSVERSIONINFO ver;
    int onlyMapID;
    HANDLE hKey = NULL, hSubKey = NULL;
    LONG ret;
    DWORD nSubKeys, i;
    ULONG valueType;
    TCHAR subKeyName[MAX_ZONE_CHAR];
    TCHAR szValue[MAX_ZONE_CHAR];
    WCHAR stdNameInReg[MAX_ZONE_CHAR];
    TziValue tempTzi;
    WCHAR *stdNamePtr = tzi.StandardName;
    DWORD valueSize;
    DWORD timeType;

    /*
     * Get the current time zone setting of the platform.
     */
    timeType = GetTimeZoneInformation(&tzi);
    if (timeType == TIME_ZONE_ID_INVALID) {
        goto err;
    }

    /*
     * Determine if this is an NT system.
     */
    ver.dwOSVersionInfoSize = sizeof(ver);
    GetVersionEx(&ver);
    isNT = ver.dwPlatformId == VER_PLATFORM_WIN32_NT;

    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, WIN_CURRENT_TZ_KEY, 0,
                       KEY_READ, (PHKEY)&hKey);
    if (ret == ERROR_SUCCESS) {
        DWORD val;
        DWORD bufSize;

        /*
         * Determine if auto-daylight time adjustment is turned off.
         */
        valueType = 0;
        bufSize = sizeof(val);
        ret = RegQueryValueExA(hKey, "DisableAutoDaylightTimeSet",
                               NULL, &valueType, (LPBYTE) &val, &bufSize);
        /*
         * Vista uses the different key name.
         */
        if (ret != ERROR_SUCCESS) {
          bufSize = sizeof(val);
            ret = RegQueryValueExA(hKey, "DynamicDaylightTimeDisabled",
                                   NULL, &valueType, (LPBYTE) &val, &bufSize);
        }
        if (ret == ERROR_SUCCESS) {
            if (val == 1) {
                (void) RegCloseKey(hKey);
                customZoneName(tzi.Bias, winZoneName);
                return VALUE_GMTOFFSET;
            }
        }

        /*
         * Vista has the key for the current "Time Zones" entry.
         */
        if (isNT && ver.dwMajorVersion >= 6) {
            valueType = 0;
            bufSize = MAX_ZONE_CHAR;
            ret = RegQueryValueExA(hKey, "TimeZoneKeyName", NULL,
                                   &valueType, (LPBYTE) winZoneName, &bufSize);
            if (ret != ERROR_SUCCESS) {
                goto err;
            }
            (void) RegCloseKey(hKey);
            return VALUE_KEY;
        }

        /*
         * Win32 problem: If the length of the standard time name is equal
         * to (or probably longer than) 32 in the registry,
         * GetTimeZoneInformation() on NT returns a null string as its
         * standard time name. We need to work around this problem by
         * getting the same information from the TimeZoneInformation
         * registry. The function on Win98 seems to return its key name.
         * We can't do anything in that case.
         */
        if (tzi.StandardName[0] == 0) {
            bufSize = sizeof(stdNameInReg);
            ret = getValueInRegistry(hKey, STANDARD_NAME, &valueType,
                                     (LPBYTE) stdNameInReg, &bufSize);
            if (ret != ERROR_SUCCESS) {
                goto err;
            }
            stdNamePtr = stdNameInReg;
        }
        (void) RegCloseKey(hKey);
    }

    /*
     * Open the "Time Zones" registry.
     */
    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, NT_TZ_KEY, 0, KEY_READ, (PHKEY)&hKey);
    if (ret != ERROR_SUCCESS) {
        ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, WIN_TZ_KEY, 0, KEY_READ, (PHKEY)&hKey);
        /*
         * If both failed, then give up.
         */
        if (ret != ERROR_SUCCESS) {
            return VALUE_UNKNOWN;
        }
    }

    /*
     * Get the number of subkeys of the "Time Zones" registry for
     * enumeration.
     */
    ret = RegQueryInfoKey(hKey, NULL, NULL, NULL, &nSubKeys,
                          NULL, NULL, NULL, NULL, NULL, NULL, NULL);
    if (ret != ERROR_SUCCESS) {
        goto err;
    }

    /*
     * Compare to the "Std" value of each subkey and find the entry that
     * matches the current control panel setting.
     */
    onlyMapID = 0;
    for (i = 0; i < nSubKeys; ++i) {
        DWORD size = sizeof(subKeyName);
        ret = RegEnumKeyEx(hKey, i, subKeyName, &size, NULL, NULL, NULL, NULL);
        if (ret != ERROR_SUCCESS) {
            goto err;
        }
        ret = RegOpenKeyEx(hKey, subKeyName, 0, KEY_READ, (PHKEY)&hSubKey);
        if (ret != ERROR_SUCCESS) {
            goto err;
        }

        size = sizeof(szValue);
        ret = getValueInRegistry(hSubKey, STD_NAME, &valueType,
                                 szValue, &size);
        if (ret != ERROR_SUCCESS) {
            /*
             * NT 4.0 SP3 fails here since it doesn't have the "Std"
             * entry in the Time Zones registry.
             */
            RegCloseKey(hSubKey);
            onlyMapID = 1;
            ret = RegOpenKeyExW(hKey, stdNamePtr, 0, KEY_READ, (PHKEY)&hSubKey);
            if (ret != ERROR_SUCCESS) {
                goto err;
            }
            break;
        }

        if (wcscmp((WCHAR *)szValue, stdNamePtr) == 0) {
            /*
             * Some localized Win32 platforms use a same name to
             * different time zones. So, we can't rely only on the name
             * here. We need to check GMT offsets and transition dates
             * to make sure it's the registry of the current time
             * zone.
             */
            DWORD tziValueSize = sizeof(tempTzi);
            ret = RegQueryValueEx(hSubKey, "TZI", NULL, &valueType,
                                  (unsigned char *) &tempTzi, &tziValueSize);
            if (ret == ERROR_SUCCESS) {
                if ((tzi.Bias != tempTzi.bias) ||
                    (memcmp((const void *) &tzi.StandardDate,
                            (const void *) &tempTzi.stdDate,
                            sizeof(SYSTEMTIME)) != 0)) {
                        goto out;
                }

                if (tzi.DaylightBias != 0) {
                    if ((tzi.DaylightBias != tempTzi.dstBias) ||
                        (memcmp((const void *) &tzi.DaylightDate,
                                (const void *) &tempTzi.dstDate,
                                sizeof(SYSTEMTIME)) != 0)) {
                        goto out;
                    }
                }
            }

            /*
             * found matched record, terminate search
             */
            strcpy(winZoneName, subKeyName);
            break;
        }
    out:
        (void) RegCloseKey(hSubKey);
    }

    /*
     * Get the "MapID" value of the registry to be able to eliminate
     * duplicated key names later.
     */
    valueSize = MAX_MAPID_LENGTH;
    ret = RegQueryValueExA(hSubKey, "MapID", NULL, &valueType, winMapID, &valueSize);
    (void) RegCloseKey(hSubKey);
    (void) RegCloseKey(hKey);

    if (ret != ERROR_SUCCESS) {
        /*
         * Vista doesn't have mapID. VALUE_UNKNOWN should be returned
         * only for Windows NT.
         */
        if (onlyMapID == 1) {
            return VALUE_UNKNOWN;
        }
    }

    return VALUE_KEY;

 err:
    if (hKey != NULL) {
        (void) RegCloseKey(hKey);
    }
    return VALUE_UNKNOWN;
}

/*
 * The mapping table file name.
 */
#define MAPPINGS_FILE "\\lib\\tzmappings"

/*
 * Index values for the mapping table.
 */
#define TZ_WIN_NAME     0
#define TZ_MAPID        1
#define TZ_REGION       2
#define TZ_JAVA_NAME    3

#define TZ_NITEMS       4       /* number of items (fields) */

/*
 * Looks up the mapping table (tzmappings) and returns a Java time
 * zone ID (e.g., "America/Los_Angeles") if found. Otherwise, NULL is
 * returned.
 *
 * value_type is one of the following values:
 *      VALUE_KEY for exact key matching
 *      VALUE_MAPID for MapID and country-based mapping (this is
 *      required for the old Windows, such as NT 4.0 SP3).
 */
static char *matchJavaTZ(const char *java_home_dir, int value_type, char *tzName,
                         char *mapID, const char *country)
{
    int line;
    int IDmatched = 0;
    FILE *fp;
    char *javaTZName = NULL;
    char *items[TZ_NITEMS];
    char mapFileName[_MAX_PATH + 1];
    char lineBuffer[MAX_ZONE_CHAR * 4];
    char bestMatch[MAX_ZONE_CHAR];
    int noMapID = *mapID == '\0';       /* no mapID on Vista */

    bestMatch[0] = '\0';

    strcpy(mapFileName, java_home_dir);
    strcat(mapFileName, MAPPINGS_FILE);

    if ((fp = fopen(mapFileName, "r")) == NULL) {
        jio_fprintf(stderr, "can't open %s.\n", mapFileName);
        return NULL;
    }

    line = 0;
    while (fgets(lineBuffer, sizeof(lineBuffer), fp) != NULL) {
        char *start, *idx, *endp;
        int itemIndex = 0;

        line++;
        start = idx = lineBuffer;
        endp = &lineBuffer[sizeof(lineBuffer)];

        /*
         * Ignore comment and blank lines.
         */
        if (*idx == '#' || *idx == '\n') {
            continue;
        }

        for (itemIndex = 0; itemIndex < TZ_NITEMS; itemIndex++) {
            items[itemIndex] = start;
            while (*idx && *idx != ':') {
                if (++idx >= endp) {
                    goto illegal_format;
                }
            }
            if (*idx == '\0') {
                goto illegal_format;
            }
            *idx++ = '\0';
            start = idx;
        }

        if (*idx != '\n') {
            goto illegal_format;
        }

        if (noMapID || strcmp(mapID, items[TZ_MAPID]) == 0) {
            /*
             * When there's no mapID, we need to scan items until the
             * exact match is found or the end of data is detected.
             */
            if (!noMapID) {
                IDmatched = 1;
            }
            if (strcmp(items[TZ_WIN_NAME], tzName) == 0) {
                /*
                 * Found the time zone in the mapping table.
                 */
                javaTZName = _strdup(items[TZ_JAVA_NAME]);
                break;
            }
            /*
             * Try to find the most likely time zone.
             */
            if (*items[TZ_REGION] == '\0') {
                strncpy(bestMatch, items[TZ_JAVA_NAME], MAX_ZONE_CHAR);
            } else if (country != NULL && strcmp(items[TZ_REGION], country) == 0) {
                if (value_type == VALUE_MAPID) {
                    javaTZName = _strdup(items[TZ_JAVA_NAME]);
                    break;
                }
                strncpy(bestMatch, items[TZ_JAVA_NAME], MAX_ZONE_CHAR);
            }
        } else {
            if (IDmatched == 1) {
                /*
                 * No need to look up the mapping table further.
                 */
                break;
            }
        }
    }
    fclose(fp);

    if (javaTZName == NULL && bestMatch[0] != '\0') {
        javaTZName = _strdup(bestMatch);
    }
    return javaTZName;

 illegal_format:
    (void) fclose(fp);
    jio_fprintf(stderr, "tzmappings: Illegal format at line %d.\n", line);
    return NULL;
}

/*
 * Detects the platform time zone which maps to a Java time zone ID.
 */
char *findJavaTZ_md(const char *java_home_dir, const char *country)
{
    char winZoneName[MAX_ZONE_CHAR];
    char winMapID[MAX_MAPID_LENGTH];
    char *std_timezone = NULL;
    int  result;

    winMapID[0] = 0;
    result = getWinTimeZone(winZoneName, winMapID);

    if (result != VALUE_UNKNOWN) {
        if (result == VALUE_GMTOFFSET) {
            std_timezone = _strdup(winZoneName);
        } else {
            std_timezone = matchJavaTZ(java_home_dir, result,
                                       winZoneName, winMapID, country);
        }
    }

    return std_timezone;
}

/**
 * Returns a GMT-offset-based time zone ID. On Win32, it always return
 * NULL since the fall back is performed in getWinTimeZone().
 */
char *
getGMTOffsetID()
{
    return NULL;
}
