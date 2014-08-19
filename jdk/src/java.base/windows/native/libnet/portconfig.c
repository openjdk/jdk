/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "jni.h"
#include "net_util.h"
#include "sun_net_PortConfig.h"

#ifdef __cplusplus
extern "C" {
#endif

struct portrange {
    int lower;
    int higher;
};

static int getPortRange(struct portrange *range)
{
    OSVERSIONINFO ver;
    ver.dwOSVersionInfoSize = sizeof(ver);
    GetVersionEx(&ver);

    /* Check for major version 5 or less = Windows XP/2003 or older */
    if (ver.dwMajorVersion <= 5) {
        LONG ret;
        HKEY hKey;
        range->lower = 1024;
        range->higher = 4999;

        /* check registry to see if upper limit was raised */
        ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE,
                   "SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters",
                   0, KEY_READ, (PHKEY)&hKey
        );
        if (ret == ERROR_SUCCESS) {
            DWORD maxuserport;
            ULONG ulType;
            DWORD dwLen = sizeof(maxuserport);
            ret = RegQueryValueEx(hKey, "MaxUserPort",  NULL, &ulType,
                             (LPBYTE)&maxuserport, &dwLen);
            RegCloseKey(hKey);
            if (ret == ERROR_SUCCESS) {
                range->higher = maxuserport;
            }
        }
    } else {
        /* There doesn't seem to be an API to access this. "MaxUserPort"
          * is affected, but is not sufficient to determine.
         * so we just use the defaults, which are less likely to change
          */
        range->lower = 49152;
        range->higher = 65535;
    }
    return 0;
}

/*
 * Class:     sun_net_PortConfig
 * Method:    getLower0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_net_PortConfig_getLower0
  (JNIEnv *env, jclass clazz)
{
    struct portrange range;
    getPortRange(&range);
    return range.lower;
}

/*
 * Class:     sun_net_PortConfig
 * Method:    getUpper0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_net_PortConfig_getUpper0
  (JNIEnv *env, jclass clazz)
{
    struct portrange range;
    getPortRange(&range);
    return range.higher;
}
#ifdef __cplusplus
}
#endif
