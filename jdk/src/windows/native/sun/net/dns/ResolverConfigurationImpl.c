/*
 * Copyright (c) 2002, 2008, Oracle and/or its affiliates. All rights reserved.
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

#include "jni_util.h"

#define MAX_STR_LEN         256

#define STS_NO_CONFIG       0x0             /* no configuration found */
#define STS_SL_FOUND        0x1             /* search list found */
#define STS_NS_FOUND        0x2             /* name servers found */

#define IS_SL_FOUND(sts)    (sts & STS_SL_FOUND)
#define IS_NS_FOUND(sts)    (sts & STS_NS_FOUND)

/*
 * Visual C++ SP3 (as required by J2SE 1.4.0) is missing some of
 * the definitions required for the IP helper library routines that
 * were added in Windows 98 & Windows 2000.
 */
#ifndef MAX_ADAPTER_NAME_LENGTH

#define MAX_ADAPTER_ADDRESS_LENGTH      8
#define MAX_ADAPTER_DESCRIPTION_LENGTH  128
#define MAX_ADAPTER_NAME_LENGTH         256
#define MAX_HOSTNAME_LEN                128
#define MAX_DOMAIN_NAME_LEN             128
#define MAX_SCOPE_ID_LEN                256

typedef struct {
    char String[4 * 4];
} IP_ADDRESS_STRING, *PIP_ADDRESS_STRING, IP_MASK_STRING, *PIP_MASK_STRING;

typedef struct _IP_ADDR_STRING {
    struct _IP_ADDR_STRING* Next;
    IP_ADDRESS_STRING IpAddress;
    IP_MASK_STRING IpMask;
    DWORD Context;
} IP_ADDR_STRING, *PIP_ADDR_STRING;

typedef struct _IP_ADAPTER_INFO {
    struct _IP_ADAPTER_INFO* Next;
    DWORD ComboIndex;
    char AdapterName[MAX_ADAPTER_NAME_LENGTH + 4];
    char Description[MAX_ADAPTER_DESCRIPTION_LENGTH + 4];
    UINT AddressLength;
    BYTE Address[MAX_ADAPTER_ADDRESS_LENGTH];
    DWORD Index;
    UINT Type;
    UINT DhcpEnabled;
    PIP_ADDR_STRING CurrentIpAddress;
    IP_ADDR_STRING IpAddressList;
    IP_ADDR_STRING GatewayList;
    IP_ADDR_STRING DhcpServer;
    BOOL HaveWins;
    IP_ADDR_STRING PrimaryWinsServer;
    IP_ADDR_STRING SecondaryWinsServer;
    time_t LeaseObtained;
    time_t LeaseExpires;
} IP_ADAPTER_INFO, *PIP_ADAPTER_INFO;

typedef struct _FIXED_INFO {
    char HostName[MAX_HOSTNAME_LEN + 4] ;
    char DomainName[MAX_DOMAIN_NAME_LEN + 4];
    PIP_ADDR_STRING CurrentDnsServer;
    IP_ADDR_STRING DnsServerList;
    UINT NodeType;
    char ScopeId[MAX_SCOPE_ID_LEN + 4];
    UINT EnableRouting;
    UINT EnableProxy;
    UINT EnableDns;
} FIXED_INFO, *PFIXED_INFO;

#endif


/* IP helper library routine used on 98/2000/XP */
static int (PASCAL FAR *GetNetworkParams_fn)();
static int (PASCAL FAR *GetAdaptersInfo_fn)();
static int (PASCAL FAR *NotifyAddrChange_fn)();

/*
 * Routines to obtain domain name and name servers are OS specific
 */
typedef int (*LoadConfig)(char *sl, char *ns);
static LoadConfig loadconfig_fn;


/*
 * JNI ids
 */
static jfieldID searchlistID;
static jfieldID nameserversID;


/*
 * Utility routine to append s2 to s1 with a space delimiter.
 *  strappend(s1="abc", "def")  => "abc def"
 *  strappend(s1="", "def")     => "def
 */
void strappend(char *s1, char *s2) {
    int len;

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
 * Windows 95/98/ME for static TCP/IP configuration.
 *
 * Use registry approach for statically configured TCP/IP settings.
 * Registry entries described in "MS TCP/IP and Windows 95 Networking"
 * (Microsoft TechNet site).
 */
static int loadStaticConfig9x(char *sl, char *ns) {
    LONG ret;
    HANDLE hKey;
    DWORD dwLen;
    ULONG ulType;
    char result[MAX_STR_LEN];
    int sts = STS_NO_CONFIG;

    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE,
                       "SYSTEM\\CurrentControlSet\\Services\\VxD\\MSTCP",
                       0,
                       KEY_READ,
                       (PHKEY)&hKey);
    if (ret == ERROR_SUCCESS) {
        /*
         * Determine suffix list
         */
        result[0] = '\0';
        dwLen = sizeof(result);
        ret = RegQueryValueEx(hKey, "SearchList", NULL, &ulType,
                              (LPBYTE)&result, &dwLen);
        if ((ret != ERROR_SUCCESS) || (strlen(result) == 0)) {
            dwLen = sizeof(result);
            ret = RegQueryValueEx(hKey, "Domain", NULL, &ulType,
                                 (LPBYTE)&result, &dwLen);
        }
        if (ret == ERROR_SUCCESS) {
            assert(ulType == REG_SZ);
            if (strlen(result) > 0) {
                strappend(sl, result);
                sts |= STS_SL_FOUND;
            }
        }

        /*
         * Determine DNS name server(s)
         */
        result[0] = '\0';
        dwLen = sizeof(result);
        ret = RegQueryValueEx(hKey, "NameServer", NULL, &ulType,
                              (LPBYTE)&result, &dwLen);
        if (ret == ERROR_SUCCESS) {
            assert(ulType == REG_SZ);
            if (strlen(result) > 0) {
                strappend(ns, result);
                sts |= STS_NS_FOUND;
            }
        }

        RegCloseKey(hKey);
    }

    return sts;
}


/*
 * Windows 95
 *
 * Use registry approach for statically configured TCP/IP settings
 * (see loadStaticConfig9x).
 *
 * If DHCP is used we examine the DHCP vendor specific extensions. We parse
 * this based on format described in RFC 2132.
 *
 * If Dial-up Networking (DUN) is used then this TCP/IP settings cannot
 * be determined here.
 */
static int loadConfig95(char *sl, char *ns) {
    int sts;
    int index;
    LONG ret;
    HANDLE hKey;
    DWORD dwLen;
    ULONG ulType;
    char optionInfo[MAX_STR_LEN];

    /*
     * First try static configuration - if found we are done.
     */
    sts = loadStaticConfig9x(sl, ns);
    if (IS_SL_FOUND(sts) && IS_NS_FOUND(sts)) {
        return sts;
    }

    /*
     * Try DHCP. DHCP information is stored in :-
     * SYSTEM\CurrentControlSet\Services\VxD\DHCP\DhcpInfoXX
     *
     * The key is normally DhcpInfo00\OptionInfo (see Article Q255245 on
     * Microsoft site). However when multiple cards are added & removed we
     * have observed that it can be located in DhcpInfo{01,02, ...}.
     * As a hack we search all DhcpInfoXX keys until we find OptionInfo.
     */
    for (index=0; index<99; index++) {
        char key[MAX_STR_LEN];
        sprintf(key, "SYSTEM\\CurrentControlSet\\Services\\VxD\\DHCP\\DhcpInfo%02d",
                index);

        ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, key, 0, KEY_READ, (PHKEY)&hKey);
        if (ret != ERROR_SUCCESS) {
            /* end of DhcpInfoXX entries */
            break;
        }

        dwLen = sizeof(optionInfo);
        ret = RegQueryValueEx(hKey, "OptionInfo",  NULL, &ulType,
                              (LPBYTE)optionInfo, &dwLen);
        RegCloseKey(hKey);

        if (ret == ERROR_SUCCESS) {
            /* OptionInfo found */
            break;
        }
    }

    /*
     * If OptionInfo was found then we parse (as the 'options' field of
     * the DHCP packet - see RFC 2132).
     */
    if (ret == ERROR_SUCCESS) {
        unsigned int pos = 0;

        while (pos < dwLen) {
            int code, len;

            code = optionInfo[pos];
            pos++;
            if (pos >= dwLen) break;    /* bad packet */

            len = optionInfo[pos];
            pos++;

            if (pos+len > dwLen) break; /* bad packet */

            /*
             * Domain Name - see RFC 2132 section 3.17
             */
            if (!IS_SL_FOUND(sts)) {
                if (code == 0xf) {
                    char domain[MAX_STR_LEN];

                    assert(len < MAX_STR_LEN);

                    memcpy((void *)domain, (void *)&(optionInfo[pos]), (size_t)len);
                    domain[len] = '\0';

                    strappend(sl, domain);
                    sts |= STS_SL_FOUND;
                }
            }

            /*
             * DNS Option - see RFC 2132 section 3.8
             */
            if (!IS_NS_FOUND(sts)) {
                if (code == 6 && (len % 4) == 0) {
                    while (len > 0 && pos < dwLen) {
                        char addr[32];
                        sprintf(addr, "%d.%d.%d.%d",
                               (unsigned char)optionInfo[pos],
                               (unsigned char)optionInfo[pos+1],
                               (unsigned char)optionInfo[pos+2],
                               (unsigned char)optionInfo[pos+3]);
                        pos += 4;
                        len -= 4;

                        /*
                         * Append to list of name servers
                         */
                        strappend(ns, addr);
                        sts |= STS_NS_FOUND;
                    }
                }
            }

            /*
             * Onto the next options
             */
            pos += len;
        }
    }

    return sts;
}

/*
 * Windows 98/ME
 *
 * Use registry approach for statically configured TCP/IP settings
 * (see loadStaticConfig9x).
 *
 * If configuration is not static then use IP helper library routine
 * GetNetworkParams to obtain the network settings which include the
 * domain name and the DNS servers. Note that we use the registry in
 * preference to GetNetworkParams as the domain name is not populated
 * by GetNetworkParams if the configuration is static.
 */
static int loadConfig98(char *sl, char *ns) {
    FIXED_INFO *infoP;
    ULONG size;
    DWORD ret;
    int sts;

    /*
     * Use registry approach to pick up static configuation.
     */
    sts = loadStaticConfig9x(sl, ns);
    if (IS_SL_FOUND(sts) && IS_NS_FOUND(sts)) {
        return sts;
    }

    /*
     * Use IP helper library to obtain dynamic configuration (DHCP and
     * DUN).
     */
    size = sizeof(FIXED_INFO);
    infoP = (FIXED_INFO *)malloc(size);
    if (infoP) {
        ret = (*GetNetworkParams_fn)(infoP, &size);
        if (ret == ERROR_BUFFER_OVERFLOW) {
            infoP = (FIXED_INFO *)realloc(infoP, size);
            if (infoP != NULL)
                ret = (*GetNetworkParams_fn)(infoP, &size);
        }
    }
    if (infoP == NULL) {
        return sts;
    }
    if (ret == ERROR_SUCCESS) {
        /*
         * Use DomainName if search-list not specified.
         */
        if (!IS_SL_FOUND(sts)) {
            strappend(sl, infoP->DomainName);
            sts |= STS_SL_FOUND;
        }

        /*
         * Use DnsServerList if not statically configured.
         */
        if (!IS_NS_FOUND(sts)) {
            PIP_ADDR_STRING dnsP = &(infoP->DnsServerList);
            do {
                strappend(ns, (char *)&(dnsP->IpAddress));
                dnsP = dnsP->Next;
            } while (dnsP != NULL);
            sts |= STS_NS_FOUND;
        }
    }

    free(infoP);

    return sts;
}


/*
 * Windows NT
 *
 * Use registry approach based on settings described in "TCP/IP and
 * NBT Configuration Parameters for Windows" - Article Q12062 on
 * Microsoft site.
 *
 * All non-RAS TCP/IP settings are stored in HKEY_LOCAL_MACHINE in
 * the SYSTEM\CurrentControlSet\Services\Tcpip\Parameters key.
 *
 * If SearchList if not provided then return Domain or DhcpDomain.
 * If Domain is specified it overrides DhcpDomain even if DHCP is
 * enabled.
 *
 * DNS name servers based on NameServer or DhcpNameServer settings.
 * NameServer overrides DhcpNameServer even if DHCP is enabled.
 */
static int loadConfigNT(char *sl, char *ns) {
    LONG ret;
    HANDLE hKey;
    DWORD dwLen;
    ULONG ulType;
    char result[MAX_STR_LEN];
    int sts = STS_NO_CONFIG;

    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE,
                       "SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters",
                       0,
                       KEY_READ,
                       (PHKEY)&hKey);
    if (ret != ERROR_SUCCESS) {
        return sts;
    }

    /*
     * Determine search list
     */
    result[0] = '\0';
    dwLen = sizeof(result);
    ret = RegQueryValueEx(hKey, "SearchList", NULL, &ulType,
                          (LPBYTE)&result, &dwLen);
    if ((ret != ERROR_SUCCESS) || (strlen(result) == 0)) {
        dwLen = sizeof(result);
        ret = RegQueryValueEx(hKey, "Domain", NULL, &ulType,
                             (LPBYTE)&result, &dwLen);
        if ((ret != ERROR_SUCCESS) || (strlen(result) == 0)) {
            dwLen = sizeof(result);
            ret = RegQueryValueEx(hKey, "DhcpDomain", NULL, &ulType,
                                 (LPBYTE)&result, &dwLen);
        }
    }
    if (ret == ERROR_SUCCESS) {
        assert(ulType == REG_SZ);
        if (strlen(result) > 0) {
            strappend(sl, result);
            sts |= STS_SL_FOUND;
        }
    }

    /*
     * Determine DNS name server(s)
     */
    result[0] = '\0';
    dwLen = sizeof(result);
    ret = RegQueryValueEx(hKey, "NameServer", NULL, &ulType,
                          (LPBYTE)&result, &dwLen);
    if ((ret != ERROR_SUCCESS) || (strlen(result) == 0)) {
        dwLen = sizeof(result);
        ret = RegQueryValueEx(hKey, "DhcpNameServer", NULL, &ulType,
                              (LPBYTE)&result, &dwLen);
    }
    if (ret == ERROR_SUCCESS) {
        assert(ulType == REG_SZ);
        if (strlen(result) > 0) {
            strappend(ns, result);
            sts |= STS_NS_FOUND;
        }
    }

    RegCloseKey(hKey);

    return sts;
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
static int loadConfig2000(char *sl, char *ns) {
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
    ret = (*GetAdaptersInfo_fn)(adapterP, &size);
    if (ret == ERROR_BUFFER_OVERFLOW) {
        adapterP = (IP_ADAPTER_INFO *)realloc(adapterP, size);
        ret = (*GetAdaptersInfo_fn)(adapterP, &size);
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
 * Initialization :-
 *
 * 1. Based on OS version set the function pointer for OS specific load
 *    configuration routine.
 *
 * 2. On 98/2000/XP load the IP helper library.
 *
 * 3. Initialize JNI field IDs.
 *
 */
JNIEXPORT void JNICALL
Java_sun_net_dns_ResolverConfigurationImpl_init0(JNIEnv *env, jclass cls)
{
    OSVERSIONINFO ver;
    jboolean loadHelperLibrary = JNI_TRUE;

    /*
     * First we figure out which OS is running
     */
    ver.dwOSVersionInfoSize = sizeof(ver);
    GetVersionEx(&ver);

    if (ver.dwPlatformId == VER_PLATFORM_WIN32_WINDOWS) {
        if ((ver.dwMajorVersion == 4) && (ver.dwMinorVersion == 0)) {
            /*
             * Windows 95
             */
            loadHelperLibrary = JNI_FALSE;
            loadconfig_fn = loadConfig95;
        } else {
            /*
             * Windows 98/ME
             */
            loadHelperLibrary = JNI_TRUE;
            loadconfig_fn = loadConfig98;
        }
    }

    if (ver.dwPlatformId == VER_PLATFORM_WIN32_NT) {
        if (ver.dwMajorVersion <= 4) {
            /*
             * Windows NT
             */
            loadHelperLibrary = JNI_FALSE;
            loadconfig_fn = loadConfigNT;
        } else {
            /*
             * Windows 2000/XP
             */
            loadHelperLibrary = JNI_TRUE;
            loadconfig_fn = loadConfig2000;
        }
    }

    /*
     * On 98/2000/XP we load the IP Helper Library.
     */
    if (loadHelperLibrary) {
        HANDLE h = LoadLibrary("iphlpapi.dll");

        if (h != NULL) {
            GetNetworkParams_fn = (int (PASCAL FAR *)())GetProcAddress(h, "GetNetworkParams");
            GetAdaptersInfo_fn = (int (PASCAL FAR *)())GetProcAddress(h, "GetAdaptersInfo");

            NotifyAddrChange_fn = (int (PASCAL FAR *)())GetProcAddress(h, "NotifyAddrChange");
        }

        if (GetNetworkParams_fn == NULL || GetAdaptersInfo_fn == NULL) {
            JNU_ThrowByName(env, "java/lang/UnsatisfiedLinkError", "iphlpapi.dll");
            return;
        }
    }

    /*
     * Get JNI ids
     */
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

    /* call OS specific routine */
    (void)(*loadconfig_fn)(searchlist, nameservers);

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

    if (NotifyAddrChange_fn != NULL) {
        ol.hEvent = (HANDLE)0;
        rc = (*NotifyAddrChange_fn)(&h, &ol);
        if (rc == ERROR_IO_PENDING) {
            rc = GetOverlappedResult(h, &ol, &xfer, TRUE);
            if (rc != 0) {
                return 0;   /* address changed */
            }
        }
    }

    /* NotifyAddrChange not support or error */
    return -1;
}
