/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include <stdio.h>
#include <stddef.h>
#include <time.h>
#include <assert.h>

#include "net_util.h"
#include "jni_util.h"

#define MAX_STR_LEN         1024
#define BUFF_SIZE           15360
#define MAX_TRIES           3

#define STS_NO_CONFIG       0x0             /* no configuration found */
#define STS_SL_FOUND        0x1             /* search list found */
#define STS_NS_FOUND        0x2             /* name servers found */
#define STS_ERROR           -1              /* error return  lodConfig failed memory allccation failure*/

#define IS_SL_FOUND(sts)    (sts & STS_SL_FOUND)
#define IS_NS_FOUND(sts)    (sts & STS_NS_FOUND)

/* JNI ids */
static jfieldID searchlistID;
static jfieldID nameserversID;

/*
 * return an array of IP_ADAPTER_ADDRESSES containing one element
 * for each adapter on the system. Returned in *adapters.
 * Buffer is malloc'd and must be freed (unless error returned)
 */
static int getAdapters (JNIEnv *env, int flags, IP_ADAPTER_ADDRESSES **adapters) {
    DWORD ret;
    IP_ADAPTER_ADDRESSES *adapterInfo;
    ULONG len;
    int try;

    *adapters = NULL;

    adapterInfo = (IP_ADAPTER_ADDRESSES *) malloc(BUFF_SIZE);
    if (adapterInfo == NULL) {
        JNU_ThrowByName(env, "java/lang/OutOfMemoryError",
            "Native heap allocation failure");
        return -1;
    }

    len = BUFF_SIZE;
    ret = GetAdaptersAddresses(AF_UNSPEC, flags, NULL, adapterInfo, &len);

    for (try = 0; ret == ERROR_BUFFER_OVERFLOW && try < MAX_TRIES; ++try) {
        IP_ADAPTER_ADDRESSES * newAdapterInfo = NULL;
        if (len < (ULONG_MAX - BUFF_SIZE)) {
            len += BUFF_SIZE;
        }
        newAdapterInfo =
            (IP_ADAPTER_ADDRESSES *) realloc (adapterInfo, len);
        if (newAdapterInfo == NULL) {
            free(adapterInfo);
            JNU_ThrowByName(env, "java/lang/OutOfMemoryError",
                "Native heap allocation failure");
            return -1;
        }

        adapterInfo = newAdapterInfo;

        ret = GetAdaptersAddresses(AF_UNSPEC, flags, NULL, adapterInfo, &len);
    }

    if (ret != ERROR_SUCCESS) {
        free (adapterInfo);
        switch (ret) {
            case ERROR_INVALID_PARAMETER:
                JNU_ThrowInternalError(env,
                    "IP Helper Library GetAdaptersAddresses function failed: "
                    "invalid parameter");
                break;
            case ERROR_NOT_ENOUGH_MEMORY:
                JNU_ThrowOutOfMemoryError(env,
                    "IP Helper Library GetAdaptersAddresses function failed: "
                    "not enough memory");
                break;
            case ERROR_NO_DATA:
                // not an error
                *adapters = NULL;
                return ERROR_SUCCESS;
            default:
                SetLastError(ret);
                JNU_ThrowByNameWithMessageAndLastError(env,
                    JNU_JAVANETPKG "SocketException",
                    "IP Helper Library GetAdaptersAddresses function failed");
                break;
        }

        return -1;
    }
    *adapters = adapterInfo;
    return ERROR_SUCCESS;
}

/*
 * Utility routine to append s2 to s1 with a comma delimiter.
 *  strappend(s1="abc", "def")  => "abc,def"
 *  strappend(s1="", "def")     => "def
 */
void strappend(char *s1, char *s2) {
    size_t len;

    if (s2[0] == '\0')                      /* nothing to append */
        return;

    len = strlen(s1)+1;
    if (s1[0] != 0)                         /* needs comma character */
        len++;
    if (len + strlen(s2) > MAX_STR_LEN)     /* insufficient space */
        return;

    if (s1[0] != 0) {
        strcat(s1, ",");
    }
    strcat(s1, s2);
}

/*
 * Use DNS server addresses returned by GetAdaptersAddresses for currently
 * active interfaces.
 */
static int loadConfig(JNIEnv *env, char *sl, char *ns) {
    IP_ADAPTER_ADDRESSES *adapters, *adapter;
    IP_ADAPTER_DNS_SERVER_ADDRESS *dnsServer;
    WCHAR *suffix;
    DWORD ret, flags;
    DWORD dwLen;
    ULONG ulType;
    char result[MAX_STR_LEN];
    HANDLE hKey;
    SOCKADDR *sockAddr;
    struct sockaddr_in6 *sockAddrIpv6;

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
            }
        }
        RegCloseKey(hKey);
    }


    // We only need DNS server addresses so skip everything else.
    flags = GAA_FLAG_SKIP_UNICAST;
    flags |= GAA_FLAG_SKIP_ANYCAST;
    flags |= GAA_FLAG_SKIP_MULTICAST;
    flags |= GAA_FLAG_SKIP_FRIENDLY_NAME;
    flags |= GAA_FLAG_INCLUDE_ALL_INTERFACES;
    ret = getAdapters(env, flags, &adapters);

    if (ret != ERROR_SUCCESS) {
        return STS_ERROR;
    }

    adapter = adapters;
    while (adapter != NULL) {
        // Only load config from enabled adapters.
        if (adapter->OperStatus == IfOperStatusUp) {
            dnsServer = adapter->FirstDnsServerAddress;
            while (dnsServer != NULL) {
                sockAddr = dnsServer->Address.lpSockaddr;
                if (sockAddr->sa_family == AF_INET6) {
                    sockAddrIpv6 = (struct sockaddr_in6 *)sockAddr;
                    if (sockAddrIpv6->sin6_scope_id != 0) {
                        // An address with a scope is either link-local or
                        // site-local, which aren't valid for DNS queries so
                        // we can skip them.
                        dnsServer = dnsServer->Next;
                        continue;
                    }
                }

                dwLen = sizeof(result);
                ret = WSAAddressToStringA(sockAddr,
                          dnsServer->Address.iSockaddrLength, NULL,
                          result, &dwLen);
                if (ret == 0) {
                    strappend(ns, result);
                }

                dnsServer = dnsServer->Next;
            }

            // Add connection-specific search domains in addition to global one.
            suffix = adapter->DnsSuffix;
            if (suffix != NULL) {
                ret = WideCharToMultiByte(CP_UTF8, 0, suffix, -1,
                    result, sizeof(result), NULL, NULL);
                if (ret != 0) {
                    strappend(sl, result);
                }
            }
        }

        adapter = adapter->Next;
    }

    free(adapters);

    return STS_SL_FOUND & STS_NS_FOUND;
}


/*
 * Initialize JNI field IDs and classes.
 */
JNIEXPORT void JNICALL
Java_sun_net_dns_ResolverConfigurationImpl_init0(JNIEnv *env, jclass cls)
{
    searchlistID = (*env)->GetStaticFieldID(env, cls, "os_searchlist",
                                      "Ljava/lang/String;");
    CHECK_NULL(searchlistID);
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

    if (loadConfig(env, searchlist, nameservers) != STS_ERROR) {

        /*
         * Populate static fields in sun.net.DefaultResolverConfiguration
         */
        obj = (*env)->NewStringUTF(env, searchlist);
        CHECK_NULL(obj);
        (*env)->SetStaticObjectField(env, cls, searchlistID, obj);

        obj = (*env)->NewStringUTF(env, nameservers);
        CHECK_NULL(obj);
        (*env)->SetStaticObjectField(env, cls, nameserversID, obj);
    }
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
