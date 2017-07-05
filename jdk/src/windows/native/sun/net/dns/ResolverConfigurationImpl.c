/*
 * Copyright (c) 2002, 2011, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <windows.h>
#include <stdio.h>
#include <stddef.h>
#include <iprtrmib.h>
#include <time.h>
#include <assert.h>
#include <iphlpapi.h>

#include "jni_util.h"

#define MAX_STR_LEN         256

#define STS_NO_CONFIG       0x0             /* no configuration found */
#define STS_SL_FOUND        0x1             /* search list found */
#define STS_NS_FOUND        0x2             /* name servers found */

#define IS_SL_FOUND(sts)    (sts & STS_SL_FOUND)
#define IS_NS_FOUND(sts)    (sts & STS_NS_FOUND)

/* JNI ids */
static jfieldID searchlistID;
static jfieldID nameserversID;

/*
 * Utility routine to append s2 to s1 with a space delimiter.
 *  strappend(s1="abc", "def")  => "abc def"
 *  strappend(s1="", "def")     => "def
 */
void strappend(char *s1, char *s2) {
    size_t len;

    if (s2[0] == '\0')                      /* nothing to append */
        return;

    len = strlen(s1)+1;
    if (s1[0] != 0)                         /* needs space character */
        len++;
    if (len + strlen(s2) > MAX_STR_LEN)     /* insufficient space */
        return;

    if (s1[0] != 0) {
        strcat(s1, " ");
    }
    strcat(s1, s2);
}

/*
 * Windows 2000/XP
 *
 * Use registry approach based on settings described in Appendix C
 * of "Microsoft Windows 2000 TCP/IP Implementation Details".
 *
 * DNS suffix list is obtained from SearchList registry setting. If
 * this is not specified we compile suffix list based on the
 * per-connection domain suffix.
 *
 * DNS name servers and domain settings are on a per-connection
 * basic. We therefore enumerate the network adapters to get the
 * names of each adapter and then query the corresponding registry
 * settings to obtain NameServer/DhcpNameServer and Domain/DhcpDomain.
 */
static int loadConfig(char *sl, char *ns) {
    IP_ADAPTER_INFO *adapterP;
    ULONG size;
    DWORD ret;
    DWORD dwLen;
    ULONG ulType;
    char result[MAX_STR_LEN];
    HANDLE hKey;
    int gotSearchList = 0;

    /*
     * First see if there is a global suffix list specified.
     */
    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE,
                       "SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters",
                       0,
                       KEY_READ,
                       (PHKEY)&hKey);
    if (ret == ERROR_SUCCESS) {
        dwLen = sizeof(result);
        ret = RegQueryValueEx(hKey, "SearchList", NULL, &ulType,
                             (LPBYTE)&result, &dwLen);
        if (ret == ERROR_SUCCESS) {
            assert(ulType == REG_SZ);
            if (strlen(result) > 0) {
                strappend(sl, result);
                gotSearchList = 1;
            }
        }
        RegCloseKey(hKey);
    }

    /*
     * Ask the IP Helper library to enumerate the adapters
     */
    size = sizeof(IP_ADAPTER_INFO);
    adapterP = (IP_ADAPTER_INFO *)malloc(size);
    ret = GetAdaptersInfo(adapterP, &size);
    if (ret == ERROR_BUFFER_OVERFLOW) {
        adapterP = (IP_ADAPTER_INFO *)realloc(adapterP, size);
        ret = GetAdaptersInfo(adapterP, &size);
    }

    /*
     * Iterate through the list of adapters as registry settings are
     * keyed on the adapter name (GUID).
     */
    if (ret == ERROR_SUCCESS) {
        IP_ADAPTER_INFO *curr = adapterP;
        while (curr != NULL) {
            char key[MAX_STR_LEN];

            sprintf(key,
                "SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces\\%s",
                curr->AdapterName);

            ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE,
                               key,
                               0,
                               KEY_READ,
                               (PHKEY)&hKey);
            if (ret == ERROR_SUCCESS) {
                DWORD enableDhcp = 0;

                /*
                 * Is DHCP enabled on this interface
                 */
                dwLen = sizeof(enableDhcp);
                ret = RegQueryValueEx(hKey, "EnableDhcp", NULL, &ulType,
                                     (LPBYTE)&enableDhcp, &dwLen);

                /*
                 * If we don't have the suffix list when get the Domain
                 * or DhcpDomain. If DHCP is enabled then Domain overides
                 * DhcpDomain
                 */
                if (!gotSearchList) {
                    result[0] = '\0';
                    dwLen = sizeof(result);
                    ret = RegQueryValueEx(hKey, "Domain", NULL, &ulType,
                                         (LPBYTE)&result, &dwLen);
                    if (((ret != ERROR_SUCCESS) || (strlen(result) == 0)) &&
                        enableDhcp) {
                        dwLen = sizeof(result);
                        ret = RegQueryValueEx(hKey, "DhcpDomain", NULL, &ulType,
                                              (LPBYTE)&result, &dwLen);
                    }
                    if (ret == ERROR_SUCCESS) {
                        assert(ulType == REG_SZ);
                        strappend(sl, result);
                    }
                }

                /*
                 * Get DNS servers based on NameServer or DhcpNameServer
                 * registry setting. If NameServer is set then it overrides
                 * DhcpNameServer (even if DHCP is enabled).
                 */
                result[0] = '\0';
                dwLen = sizeof(result);
                ret = RegQueryValueEx(hKey, "NameServer", NULL, &ulType,
                                     (LPBYTE)&result, &dwLen);
                if (((ret != ERROR_SUCCESS) || (strlen(result) == 0)) &&
                    enableDhcp) {
                    dwLen = sizeof(result);
                    ret = RegQueryValueEx(hKey, "DhcpNameServer", NULL, &ulType,
                                          (LPBYTE)&result, &dwLen);
                }
                if (ret == ERROR_SUCCESS) {
                    assert(ulType == REG_SZ);
                    strappend(ns, result);
                }

                /*
                 * Finished with this registry key
                 */
                RegCloseKey(hKey);
            }

            /*
             * Onto the next adapeter
             */
            curr = curr->Next;
        }
    }

    /*
     * Free the adpater structure
     */
    if (adapterP) {
        free(adapterP);
    }

    return STS_SL_FOUND & STS_NS_FOUND;
}


/*
 * Initialize JNI field IDs.
 */
JNIEXPORT void JNICALL
Java_sun_net_dns_ResolverConfigurationImpl_init0(JNIEnv *env, jclass cls)
{
    searchlistID = (*env)->GetStaticFieldID(env, cls, "os_searchlist",
                                      "Ljava/lang/String;");
    nameserversID = (*env)->GetStaticFieldID(env, cls, "os_nameservers",
                                      "Ljava/lang/String;");
}

/*
 * Class:     sun_net_dns_ResolverConfgurationImpl
 * Method:    loadConfig0
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_net_dns_ResolverConfigurationImpl_loadDNSconfig0(JNIEnv *env, jclass cls)
{
    char searchlist[MAX_STR_LEN];
    char nameservers[MAX_STR_LEN];
    jstring obj;

    searchlist[0] = '\0';
    nameservers[0] = '\0';

    loadConfig(searchlist, nameservers);

    /*
     * Populate static fields in sun.net.DefaultResolverConfiguration
     */
    obj = (*env)->NewStringUTF(env, searchlist);
    (*env)->SetStaticObjectField(env, cls, searchlistID, obj);

    obj = (*env)->NewStringUTF(env, nameservers);
    (*env)->SetStaticObjectField(env, cls, nameserversID, obj);
}


/*
 * Class:     sun_net_dns_ResolverConfgurationImpl
 * Method:    notifyAddrChange0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_sun_net_dns_ResolverConfigurationImpl_notifyAddrChange0(JNIEnv *env, jclass cls)
{
    OVERLAPPED ol;
    HANDLE h;
    DWORD rc, xfer;

    ol.hEvent = (HANDLE)0;
    rc = NotifyAddrChange(&h, &ol);
    if (rc == ERROR_IO_PENDING) {
        rc = GetOverlappedResult(h, &ol, &xfer, TRUE);
        if (rc != 0) {
            return 0;   /* address changed */
        }
    }

    /* error */
    return -1;
}
