/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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

#define MAX_ZONE_CHAR           256
#define MAX_REGION_LENGTH       4

#define WIN_CURRENT_TZ_KEY      "System\\CurrentControlSet\\Control\\TimeZoneInformation"

/*
 * Produces custom name "GMT+hh:mm" from the given bias in buffer.
 */
static void customZoneName(LONG bias, char *buffer, size_t bufSize) {
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
        snprintf(buffer, bufSize, "GMT%c%02d:%02d",
                ((sign >= 0) ? '+' : '-'),
                gmtOffset / 60,
                gmtOffset % 60);
    } else {
        strcpy(buffer, "GMT");
    }
}

/*
 * The mapping table file name.
 */
#define MAPPINGS_FILE "\\lib\\tzmappings"

/*
 * Index values for the mapping table.
 */
#define TZ_WIN_NAME     0
#define TZ_REGION       1
#define TZ_JAVA_NAME    2

#define TZ_NITEMS       3       /* number of items (fields) */

/*
 * Looks up the mapping table (tzmappings) and returns a Java time
 * zone ID (e.g., "America/Los_Angeles") if found. Otherwise, NULL is
 * returned.
 */
static char *matchJavaTZ(const char *java_home_dir, char *tzName)
{
    int line;
    FILE *fp;
    char *javaTZName = NULL;
    char *items[TZ_NITEMS];
    char *mapFileName;
    char lineBuffer[MAX_ZONE_CHAR * 4];
    int offset = 0;
    const char* errorMessage = "unknown error";
    char region[MAX_REGION_LENGTH];

    // Get the user's location
    if (GetGeoInfo(GetUserGeoID(GEOCLASS_NATION),
            GEO_ISO2, region, MAX_REGION_LENGTH, 0) == 0) {
        // If GetGeoInfo fails, fallback to LCID's country
        LCID lcid = GetUserDefaultLCID();
        if (GetLocaleInfo(lcid,
                          LOCALE_SISO3166CTRYNAME, region, MAX_REGION_LENGTH) == 0 &&
            GetLocaleInfo(lcid,
                          LOCALE_SISO3166CTRYNAME2, region, MAX_REGION_LENGTH) == 0) {
            region[0] = '\0';
        }
    }

    mapFileName = malloc(strlen(java_home_dir) + strlen(MAPPINGS_FILE) + 1);
    if (mapFileName == NULL) {
        return NULL;
    }
    strcpy(mapFileName, java_home_dir);
    strcat(mapFileName, MAPPINGS_FILE);

    if (fopen_s(&fp, mapFileName, "rt") != 0) {
        jio_fprintf(stderr, "can't open %s.\n", mapFileName);
        free((void *) mapFileName);
        return NULL;
    }
    free((void *) mapFileName);

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
                    errorMessage = "premature end of line";
                    offset = (int)(idx - lineBuffer);
                    goto illegal_format;
                }
            }
            if (*idx == '\0') {
                errorMessage = "illegal null character found";
                offset = (int)(idx - lineBuffer);
                goto illegal_format;
            }
            *idx++ = '\0';
            start = idx;
        }

        if (*idx != '\n') {
            errorMessage = "illegal non-newline character found";
            offset = (int)(idx - lineBuffer);
            goto illegal_format;
        }

        /*
         * We need to scan items until the
         * exact match is found or the end of data is detected.
         */
        if (strcmp(items[TZ_WIN_NAME], tzName) == 0) {
            /*
             * Found the time zone in the mapping table.
             * Check the region code and select the appropriate entry
             */
            if (strcmp(items[TZ_REGION], region) == 0 ||
                strcmp(items[TZ_REGION], "001") == 0) {
                javaTZName = _strdup(items[TZ_JAVA_NAME]);
                break;
            }
        }
    }
    fclose(fp);

    return javaTZName;

 illegal_format:
    (void) fclose(fp);
    jio_fprintf(stderr, "Illegal format in tzmappings file: %s at line %d, offset %d.\n",
                errorMessage, line, offset);
    return NULL;
}

/*
 * Detects the platform time zone which maps to a Java time zone ID.
 */
char *findJavaTZ_md(const char *java_home_dir)
{
    char winZoneName[MAX_ZONE_CHAR];
    char *std_timezone = NULL;

    DYNAMIC_TIME_ZONE_INFORMATION dtzi;
    DWORD timeType;

    /*
     * Get the dynamic time zone information so that time zone redirection
     * can be supported. (see JDK-7044727)
     */
    timeType = GetDynamicTimeZoneInformation(&dtzi);
    if (timeType == TIME_ZONE_ID_INVALID) {
        return NULL;
    }

    /*
     * If DynamicDaylightTime is enabled, map TimeZoneKeyName to a Java TZ.
     * If that succeeds, return the Java TZ name.
     */
    if (dtzi.DynamicDaylightTimeDisabled == 0 && dtzi.TimeZoneKeyName[0] != 0) {
        wcstombs(winZoneName, dtzi.TimeZoneKeyName, MAX_ZONE_CHAR);
        std_timezone = matchJavaTZ(java_home_dir, winZoneName);
        if (std_timezone != NULL) {
            return std_timezone;
        }
    }

    /*
     * If DynamicDaylightTime is disabled or TimeZoneKeyName is unknown,
     * return a custom time zone name based on the GMT offset.
     */
    customZoneName(dtzi.Bias, winZoneName, MAX_ZONE_CHAR);
    return _strdup(winZoneName);
}

/**
 * Returns a GMT-offset-based time zone ID.
 */
char *
getGMTOffsetID()
{
    LONG bias = 0;
    LONG ret;
    HANDLE hKey = NULL;
    char zonename[32];

    // Obtain the current GMT offset value of ActiveTimeBias.
    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, WIN_CURRENT_TZ_KEY, 0,
                       KEY_READ, (PHKEY)&hKey);
    if (ret == ERROR_SUCCESS) {
        DWORD val;
        DWORD bufSize = sizeof(val);
        ret = RegGetValueA(hKey, NULL, "ActiveTimeBias",
                           RRF_RT_REG_DWORD, NULL, (LPBYTE) &val, &bufSize);
        if (ret == ERROR_SUCCESS) {
            bias = (LONG) val;
        }
        (void) RegCloseKey(hKey);
    }

    // If we can't get the ActiveTimeBias value, use Bias of TimeZoneInformation.
    // Note: Bias doesn't reflect current daylight saving.
    if (ret != ERROR_SUCCESS) {
        TIME_ZONE_INFORMATION tzi;
        if (GetTimeZoneInformation(&tzi) != TIME_ZONE_ID_INVALID) {
            bias = tzi.Bias;
        }
    }

    customZoneName(bias, zonename, sizeof(zonename));
    return _strdup(zonename);
}
