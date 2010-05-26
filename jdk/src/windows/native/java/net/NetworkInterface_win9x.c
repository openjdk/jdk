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

#include <windows.h>
#include <winsock2.h>
#include <assert.h>

#include "jni_util.h"

#include "NetworkInterface.h"

/*
 * Windows 9x specific routines to enumerate network interfaces and the
 * IP addresses bound to those interfaces.
 *
 * Windows 95 does not include IP helper library support by default.
 * Additionally Windows 98 can have its IP helper library support
 * trashed by certain IE installations. For these environments we
 * combine information from the registry with the list of IP addresses
 * obtained via SIO_GET_INTERFACE_LIST.
 */

/*
 * Header files are missing these
 */
#if !defined(SIO_GET_INTERFACE_LIST)
#define SIO_GET_INTERFACE_LIST  _IOR('t', 127, u_long)

struct in_addr6 {
                u_char  s6_addr[16];
};

struct sockaddr_in6 {
                short   sin6_family;
                u_short sin6_port;
                u_long  sin6_flowinfo;
                struct in_addr6 sin6_addr;
};

typedef union sockaddr_gen{
                struct sockaddr Address;
                struct sockaddr_in  AddressIn;
                struct sockaddr_in6 AddressIn6;
} sockaddr_gen;

typedef struct _INTERFACE_INFO
{
        u_long          iiFlags;
        sockaddr_gen    iiAddress;
        sockaddr_gen    iiBroadcastAddress;
        sockaddr_gen    iiNetmask;
} INTERFACE_INFO;

#define IFF_UP              0x00000001
#endif


#define MAX_STR_LEN         256


/*
 * A network adapter (similiar to the netif structure except contains
 * Windows 9x specific fields).
 */
typedef struct _adapter {
    char *name;
    char *displayName;
    int index;
    char *reg_key;
    int is_wan_driver;
    netaddr *addrs;
    struct _adapter *next;
} adapter;


/*
 * Cached adapter list.
 */
static CRITICAL_SECTION cacheLock;
static adapter *cachedAdapterList;

/*
 * Initialize cache
 */
void init_win9x() {
    InitializeCriticalSection(&cacheLock);
}


/*
 * Free adapter list and any addresses bound to the adpater.
 */
static void free_adapters(adapter *adapterP) {
    adapter *curr = adapterP;
    while (curr != NULL) {
        if (curr->name != NULL)
            free(curr->name);

        if (curr->displayName != NULL)
            free(curr->displayName);

        if (curr->reg_key != NULL)
            free(curr->reg_key);

        if (curr->addrs != NULL)
            free_netaddr(curr->addrs);

        adapterP = adapterP->next;
        free(curr);
        curr = adapterP;
    }
}


/*
 * Returns the SIO_GET_INTERFACE_LIST output
 */
static int getInterfaceList(JNIEnv *env, INTERFACE_INFO *infoP, DWORD dwSize) {
    SOCKET sock;
    DWORD ret;

    /* create a socket and do the ioctl */
    sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock == INVALID_SOCKET) {
        JNU_ThrowByName(env, "java/lang/Error", "socket failed");
        return -1;
    }
    ret = WSAIoctl(sock, SIO_GET_INTERFACE_LIST, NULL, 0,
                   infoP, dwSize, &dwSize, NULL, NULL);
    closesocket(sock);
    if (ret == SOCKET_ERROR) {
        JNU_ThrowByName(env, "java/lang/Error", "WSAIoctl failed");
        return -1;
    }
    return dwSize;
}


/*
 * Gross, ugly, and crude way of guessing if this is a WAN (dial-up) driver.
 * Returns 1 if it's the normal PPCMAC VxD, otherwise 0.
 */
static int isWanDriver(char *driver) {
    LONG ret;
    HKEY hKey;
    DWORD dwLen;
    ULONG ulType;
    char key[MAX_STR_LEN];
    char vxd[MAX_STR_LEN];

    sprintf(key, "System\\CurrentControlSet\\Services\\Class\\%s", driver);
    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, key, 0, KEY_READ, (PHKEY)&hKey);
    if (ret != ERROR_SUCCESS) {
        return 0;
    }
    dwLen = sizeof(vxd);
    ret = RegQueryValueEx(hKey, "DeviceVxDs",  NULL, &ulType,
                         (LPBYTE)vxd, &dwLen);
    RegCloseKey(hKey);
    if (ret != ERROR_SUCCESS) {
        return 0;
    }
    return (strcmp(vxd, "pppmac.vxd") == 0);
}

/*
 * Windows 9x routine to get the network adapters using the registry.
 * We enumerate HKEY_LOCAL_MACHINE\Enum and iterate through the tree
 * looking for devices of class "Net". As these devices may not have a
 * unique name we assign them a generated name.
 *
 * Returns a list of adapters without IP addresses (addrs member is NULL).
 */
static int getAdapters(JNIEnv *env, adapter **adapterPP)
{
    LONG ret;
    HKEY enumKey;
    DWORD dwLen;
    DWORD dwEnumKeys;
    DWORD enumIndex;
    ULONG ulType;
    int adapterCount = 0;
    adapter *adapterP = NULL;
    adapter *curr;

    /*
     * Start at HKEY_LOCAL_MACHINE\Enum
     */
    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, "Enum", 0, KEY_READ, (PHKEY)&enumKey);
    if (ret != ERROR_SUCCESS) {
        return -1;
    }
    ret = RegQueryInfoKey(enumKey, NULL, NULL, NULL, &dwEnumKeys,
                          NULL, NULL, NULL, NULL, NULL, NULL, NULL);
    if (ret != ERROR_SUCCESS) {
        RegCloseKey(enumKey);
        return -1;
    }

    /*
     * Iterate through the sub-keys (PCI, Root, ...)
     */
    for(enumIndex = 0; enumIndex<dwEnumKeys; enumIndex++) {
        TCHAR deviceType[MAX_STR_LEN];
        HKEY deviceKey;
        DWORD deviceIndex;
        DWORD dwDeviceKeys;

        dwLen = sizeof(deviceType);
        ret = RegEnumKeyEx(enumKey, enumIndex, deviceType, &dwLen, NULL, NULL, NULL, NULL);
        if (ret != ERROR_SUCCESS) {
            /* ignore this tree */
            continue;
        }

        ret = RegOpenKeyEx(enumKey, deviceType, 0, KEY_READ, (PHKEY)&deviceKey);
        if (ret != ERROR_SUCCESS) {
            /* ignore this tree */
            continue;
        }
        ret = RegQueryInfoKey(deviceKey, NULL, NULL, NULL, &dwDeviceKeys,
                              NULL, NULL, NULL, NULL, NULL, NULL, NULL);
        if (ret != ERROR_SUCCESS) {
            /* ignore this tree */
            RegCloseKey(deviceKey);
            continue;
        }

        /*
         * Iterate through each of the sub-keys under PCI, Root, ...
         */
        for (deviceIndex=0; deviceIndex<dwDeviceKeys; deviceIndex++) {
            TCHAR name[MAX_STR_LEN];
            HKEY nameKey;
            DWORD nameIndex;
            DWORD dwNameKeys;

            dwLen = sizeof(name);
            ret = RegEnumKeyEx(deviceKey, deviceIndex, name, &dwLen, NULL, NULL, NULL, NULL);

            if (ret != ERROR_SUCCESS) {
                /* ignore this sub-tree */
                continue;
            }

            ret = RegOpenKeyEx(deviceKey, name, 0, KEY_READ, (PHKEY)&nameKey);
            if (ret != ERROR_SUCCESS) {
                /* ignore this sub-tree */
                continue;
            }
            ret = RegQueryInfoKey(nameKey, NULL, NULL, NULL, &dwNameKeys,
                                  NULL, NULL, NULL, NULL, NULL, NULL, NULL);
            if (ret != ERROR_SUCCESS) {
                RegCloseKey(nameKey);
                /* ignore this sub-tree */
                continue;
            }

            /*
             * Finally iterate through the Enum\Root\Net level keys
             */
            for (nameIndex=0; nameIndex<dwNameKeys; nameIndex++) {
                TCHAR dev[MAX_STR_LEN];
                TCHAR cls[MAX_STR_LEN];
                HKEY clsKey;

                dwLen = sizeof(dev);
                ret = RegEnumKeyEx(nameKey, nameIndex, dev, &dwLen, NULL, NULL, NULL, NULL);
                if (ret != ERROR_SUCCESS) {
                    continue;
                }

                ret = RegOpenKeyEx(nameKey, dev, 0, KEY_READ, (PHKEY)&clsKey);
                if (ret == ERROR_SUCCESS) {
                    dwLen = sizeof(cls);
                    ret = RegQueryValueEx(clsKey, "Class",  NULL, &ulType,
                                          (LPBYTE)cls, &dwLen);

                    if (ret == ERROR_SUCCESS) {
                        if (strcmp(cls, "Net") == 0) {
                            TCHAR deviceDesc[MAX_STR_LEN];

                            dwLen = sizeof(deviceDesc);
                            ret = RegQueryValueEx(clsKey, "DeviceDesc",  NULL, &ulType,
                                                  (LPBYTE)deviceDesc, &dwLen);

                            if (ret == ERROR_SUCCESS) {
                                char key_name[MAX_STR_LEN];
                                char ps_name[8];
                                char driver[MAX_STR_LEN];
                                int wan_device;

                                /*
                                 * Generate a pseudo device name
                                 */
                                sprintf(ps_name, "net%d", adapterCount);

                                /*
                                 * Try to determine if this a WAN adapter. This is
                                 * useful when we try to eliminate WAN adapters from
                                 * the interface list when probing for DHCP info
                                 */
                                dwLen = sizeof(driver);
                                ret = RegQueryValueEx(clsKey, "Driver",  NULL,
                                                      &ulType, (LPBYTE)driver, &dwLen);
                                if (ret == ERROR_SUCCESS) {
                                    wan_device = isWanDriver(driver);
                                } else {
                                    wan_device = 0;
                                }

                                /*
                                 * We have found a Net device. In order to get the
                                 * static IP addresses we must note the key.
                                 */
                                sprintf(key_name, "Enum\\%s\\%s\\%s", deviceType, name, dev);

                                /*
                                 * Create the net adapter
                                 */
                                curr = (adapter *)calloc(1, sizeof(adapter));
                                if (curr != NULL) {
                                    curr->is_wan_driver = wan_device;
                                    curr->name = (char *)malloc(strlen(ps_name) + 1);
                                    if (curr->name) {
                                        curr->displayName = (char *)malloc(strlen(deviceDesc) + 1);
                                        if (curr->displayName) {
                                            curr->reg_key = (char *)malloc(strlen(key_name)+1);
                                            if (curr->reg_key == NULL) {
                                                free(curr->displayName);
                                                free(curr->name);
                                                free(curr);
                                                curr = NULL;
                                            }
                                        } else {
                                            free(curr->name);
                                            free(curr);
                                            curr = NULL;
                                        }
                                    } else {
                                        free(curr);
                                        curr = NULL;
                                    }
                                }

                                /* At OutOfMemory occurred */
                                if (curr == NULL) {
                                    JNU_ThrowOutOfMemoryError(env, "heap allocation failure");
                                    free_adapters(adapterP);
                                    RegCloseKey(clsKey);
                                    RegCloseKey(nameKey);
                                    RegCloseKey(deviceKey);
                                    RegCloseKey(enumKey);
                                    return -1;
                                }

                                /* index starts at 1 (not 0) */
                                curr->index = ++adapterCount;

                                strcpy(curr->name, ps_name);
                                strcpy(curr->displayName, deviceDesc);
                                strcpy(curr->reg_key, key_name);

                                /*
                                 * Put the adapter at the end of the list.
                                 */
                                if (adapterP == NULL) {
                                    adapterP = curr;
                                } else {
                                    adapter *tail = adapterP;
                                    while (tail->next != NULL) {
                                        tail = tail->next;
                                    }
                                    tail->next = curr;
                                }
                            }
                        }
                    }
                }
                RegCloseKey(clsKey);
            }
            RegCloseKey(nameKey);
        }
        RegCloseKey(deviceKey);
    }
    RegCloseKey(enumKey);

    /*
     * Insert an entry for the loopback interface
     */
    curr = (adapter *)calloc(1, sizeof(adapter));
    if (curr == NULL) {
        JNU_ThrowOutOfMemoryError(env, "heap allocation failure");
        free_adapters(adapterP);
        return -1;
    }
    curr->index = ++adapterCount;
    curr->name = _strdup("lo");
    curr->displayName = _strdup("TCP Loopback interface");
    curr->next = adapterP;
    *adapterPP = curr;

    return adapterCount;
}

/*
 * Windows 9x routine to obtain any static addresses for a specified
 * TCP/IP binding.
 *
 * We first open Enum\Network\${binding} and check that the driver
 * is TCP/IP. If so we pick up the driver and check for any IP addresses
 * in System\\CurrentControlSet\\Services\\Class\\${driver}
 *
 * Returns 0 if found, otherwise -1.
 */
static int getStaticAddressEntry(char *binding, char *addresses) {
    LONG ret;
    HKEY hKey;
    char name[255];
    char desc[255];
    char driver[255];
    char ipaddr[255];
    DWORD dwName;
    ULONG ulType;

    /* assume nothing will be returned */
    strcpy(addresses, "");

    /*
     * Open the binding and check that it's TCP/IP
     */
    sprintf(name, "Enum\\Network\\%s", binding);
    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, name, 0, KEY_READ, (PHKEY)&hKey);
    if (ret != ERROR_SUCCESS) {
        return -1;
    }
    dwName = sizeof(desc);
    ret = RegQueryValueEx(hKey, "DeviceDesc",  NULL, &ulType,
                         (LPBYTE)desc, &dwName);
    if (ret != ERROR_SUCCESS) {
        RegCloseKey(hKey);
        return -1;
    }
    if (strcmp(desc, "TCP/IP") != 0) {
        /* ignore non-TCP/IP bindings */
        RegCloseKey(hKey);
        return -1;
    }

    /*
     * Get the driver for this TCP/IP binding
     */
    dwName = sizeof(driver);
    ret = RegQueryValueEx(hKey, "Driver",  NULL, &ulType,
                          (LPBYTE)driver, &dwName);
    RegCloseKey(hKey);
    if (ret != ERROR_SUCCESS) {
        return -1;
    }

    /*
     * Finally check if there is an IPAddress value for this driver.
     */
    sprintf(name, "System\\CurrentControlSet\\Services\\Class\\%s", driver);
    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, name, 0, KEY_READ, (PHKEY)&hKey);
    if (ret != ERROR_SUCCESS) {
        return -1;
    }
    dwName = sizeof(ipaddr);
    ret = RegQueryValueEx(hKey, "IPAddress",  NULL, &ulType,
                          (LPBYTE)ipaddr, &dwName);
    RegCloseKey(hKey);
    if (ret != ERROR_SUCCESS) {
        return -1;
    }

    /* Return the address(es) */
    strcpy( addresses, ipaddr );
    return 0;
}

/*
 * Windows 9x routine to enumerate the static IP addresses on a
 * particular interface using the registry.
 *
 * Returns a count of the number of addresses found.
 */
static int getStaticAddresses(JNIEnv *env, char *reg_key, netaddr **netaddrPP)
{
    LONG ret;
    HKEY enumKey, bindingKey;
    DWORD dwLen;
    ULONG ulType;
    char addresses[MAX_STR_LEN];
    unsigned long addr;     /* IPv4 address */
    unsigned char byte;
    netaddr *netaddrP, *curr;
    int i, addrCount;

    /*
     * Open the HKEY_LOCAL_MACHINE\Enum\%s\%s\%s key
     */
    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, reg_key, 0, KEY_READ,
                       (PHKEY)&enumKey);
    if (ret != ERROR_SUCCESS) {
        /* interface has been removed */
        *netaddrPP = NULL;
        return 0;
    }

    /*
     * Iterate through each of the bindings to find any TCP/IP bindings
     * and any static address assoicated with the binding.
     */
    strcpy(addresses, "");
    addrCount = 0;
    netaddrP = NULL;

    ret = RegOpenKeyEx(enumKey, "Bindings", 0, KEY_READ, (PHKEY)&bindingKey);
    if (ret == ERROR_SUCCESS) {
        DWORD dwBindingKeys;
        DWORD dwBindingIndex;

        ret = RegQueryInfoKey(bindingKey, NULL, NULL, NULL, NULL, NULL, NULL, &dwBindingKeys,
                              NULL, NULL, NULL, NULL);
        if (ret == ERROR_SUCCESS) {
            TCHAR binding[MAX_STR_LEN];

            dwBindingIndex=0;
            while (dwBindingIndex<dwBindingKeys) {
                dwLen = sizeof(binding);
                ret = RegEnumValue(bindingKey, dwBindingIndex, binding, &dwLen,
                                   NULL, &ulType, NULL, NULL);
                if (ret == ERROR_SUCCESS) {
                    if (getStaticAddressEntry(binding, addresses) == 0) {
                        /*
                         * On Windows 9x IP addresses are strings. Multi-homed hosts have
                         * the IP addresses seperated by commas.
                         */
                        addr = 0;
                        byte = 0;
                        i = 0;
                        while ((DWORD)i < strlen(addresses)+1) {
                            /* eof or seperator */
                            if (addresses[i] == ',' || addresses[i] == 0) {
                                if (addr != 0) {
                                    addr = (addr << 8) | byte;

                                    curr = (netaddr *)malloc(sizeof(netaddr));
                                    if (curr == NULL) {
                                        JNU_ThrowOutOfMemoryError(env, "heap allocation failure");
                                        free_netaddr(netaddrP);
                                        RegCloseKey(enumKey);
                                        return -1;
                                    }
                                    curr->addr.him4.sin_family = AF_INET;
                                    curr->addr.him4.sin_addr.s_addr = htonl(addr);
                                    curr->next = netaddrP;

                                    netaddrP = curr;
                                    addrCount++;

                                    /* reset the address for the next iteration */
                                    addr = 0;
                                }
                                byte = 0;
                            } else {
                                if (addresses[i] == '.') {
                                    addr = (addr << 8) | byte;
                                    byte = 0;
                                } else {
                                    byte = (byte * 10) + (addresses[i] - '0');
                                }
                            }
                            i++;
                        }
                    }
                }
                if (addrCount > 0) {
                    break;
                }
                dwBindingIndex++;
            }
        }
        RegCloseKey(bindingKey);
    }

    /* close the registry */
    RegCloseKey(enumKey);


    /* return the list */
    *netaddrPP = netaddrP;
    return addrCount;
}

/*
 * Windows 9x routine to probe the registry for a DHCP allocated address.
 * This routine is only useful if we know that only one interface has its
 * address allocated using DHCP. Returns 0.0.0.0 if none or multiple
 * addresses found.0
 */
static DWORD getDHCPAddress()
{
    LONG ret;
    HKEY hKey;
    DWORD dwLen;
    ULONG ulType;
    char key[MAX_STR_LEN];
    int index;
    DWORD dhcp_addr = 0;

    index = 0;
    while (index < 99) {
        DWORD addr;

        sprintf(key, "SYSTEM\\CurrentControlSet\\Services\\VxD\\DHCP\\DhcpInfo%02d", index);

        ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, key, 0, KEY_READ, (PHKEY)&hKey);
        if (ret != ERROR_SUCCESS) {
            return dhcp_addr;
        }

        /*
         * On Windows 9x the DHCP address is in the DhcpIPAddress key. We
         * are assuming here that this is Windows Socket 2. If Windows
         * Sockets is the original 1.1 release then this doesn't work because
         * the IP address if in the DhcpInfo key (a blob with the first 4
         * bytes set to the IP address).
         */
        dwLen = sizeof(addr);
        ret = RegQueryValueEx(hKey, "DhcpIPAddress",  NULL, &ulType,
                              (LPBYTE)&addr, &dwLen);
        RegCloseKey(hKey);

        if (ret == ERROR_SUCCESS) {
            if (addr) {
                /* more than 1 DHCP address in registry */
                if (dhcp_addr) {
                    return 0;
                }
                dhcp_addr = htonl(addr);
            }
        }
        index++;
    }

    /* if we get here it means we've examined 100 registry entries */
    return 0;
}


/*
 * Attempt to allocate the remaining addresses on addrList to the adpaters
 * on adapterList. Returns the number of address remaining.
 */
int allocateRemaining(adapter *adapterList, int address_count, netaddr *addrList) {
    adapter *adapterP = adapterList;
    adapter *nobindingsP = NULL;

    /*
     * If all addresses have been assigned there's nothing to do.
     */
    if (address_count == 0) {
        return 0;
    }

    /*
     * Determine if there is only one adapter without an address
     */
    while (adapterP != NULL) {
        if (adapterP->addrs == NULL) {
            if (nobindingsP == NULL) {
                nobindingsP = adapterP;
            } else {
                nobindingsP = NULL;
                break;
            }
        }
        adapterP = adapterP->next;
    }

    /*
     * Found (only one)
     */
    if (nobindingsP) {
        nobindingsP->addrs = addrList;
        address_count = 0;
    }

    return address_count;
}


/*
 * 1. Network adapters are enumerated by traversing through the
 *    HKEY_LOCAL_MACHINE\Enum tree and picking out class "Net" devices.
 *
 * 2. Address enumeration starts with the list of IP addresses returned
 *    by SIO_GET_INTERFACE_LIST and then we "allocate" the addresses to
 *    the network adapters enumerated in step 1. Allocation works as
 *    follows :-
 *
 *    i.   Loopback address is assigned to the loopback interface. If there
 *         is one network adapter then all other addresses must be bound
 *         to that adapter.
 *
 *    ii.  Enumerate all static IP addresses using the registry. This allows
 *         us to allocate all static IP address to the corresponding adapter.
 *
 *    iii. After step ii. if there is one network adapter that has not been
 *         allocated an IP address then we know that the remaining IP addresses
 *         must be bound to this adapter.
 *
 *    iv.  If we get to this step it means we are dealing with a complex
 *         configuration whereby multiple network adapters have their address
 *         configured dynamically (eg: NIC using DHCP plus modem using PPP).
 *         We employ a gross hack based on a crude determination done in step 1.
 *         If there is a DHCP address configured and if one remaining
 *         network adapter that is not a WAN adapter then the DHCP address
 *         must be bound to it.
 */
static adapter *loadConfig(JNIEnv *env) {
    adapter *adapterList;
    int adapter_count;
    INTERFACE_INFO interfaceInfo[8];
    DWORD dwSize;
    int address_count, i;
    netaddr *addrList;

    /*
     * Enumerate the network adapters
     */
    adapter_count = getAdapters(env, &adapterList);
    if (adapter_count < 0) {
        return NULL;
    }
    /* minimum of loopback interface */
    assert(adapter_count >= 1);

    /*
     * Enumerate all IP addresses as known to winsock
     */
    dwSize = getInterfaceList(env, interfaceInfo, sizeof(interfaceInfo));
    if (dwSize < 0) {
        free_adapters(adapterList);
        return NULL;
    }
    address_count = dwSize/sizeof(INTERFACE_INFO);

    /* minimum of loopback address */
    assert(address_count >= 1);

    /*
     * Create an address list (addrList) from the INTERFACE_INFO
     * structure.
     */
    addrList = NULL;
    for (i=0; i<address_count; i++) {
        netaddr *addrP = (netaddr *)calloc(1, sizeof(netaddr));
        if (addrP == NULL) {
            JNU_ThrowOutOfMemoryError(env, "heap allocation failure");
            free_netaddr(addrList);
            free(adapterList);
            return NULL;
        }

        addrP->addr.him4.sin_family = AF_INET;
        addrP->addr.him4.sin_addr.s_addr =
            ((SOCKADDR_IN *)&(interfaceInfo[i].iiAddress))->sin_addr.S_un.S_addr;

        addrP->next = addrList;
        addrList = addrP;
    }


    /*
     * First we assign the loopback address to the lo adapter.
     * If lo is the only adapter then we are done.
     */
    {
        adapter *loopbackAdapter;
        netaddr *addrP, *prevP;

        /* find the loopback adapter */
        loopbackAdapter = adapterList;
        while (strcmp(loopbackAdapter->name, "lo") != 0) {
            loopbackAdapter = loopbackAdapter->next;
        }
        assert(loopbackAdapter != NULL);

        /* find the loopback address and move it to the loopback adapter */
        addrP = addrList;
        prevP = NULL;
        while (addrP != NULL) {
            if (addrP->addr.him4.sin_addr.s_addr == htonl(0x7f000001)) {
                loopbackAdapter->addrs = addrP;
                if (prevP == NULL) {
                    addrList = addrP->next;
                } else {
                    prevP->next = addrP->next;
                }
                loopbackAdapter->addrs->next = NULL;
                address_count--;
                break;
            }
            prevP = addrP;
            addrP = addrP->next;
        }
    }


    /*
     * Special case. If there's only one network adapter then all remaining
     * IP addresses must be bound to that adapter.
     */
    address_count = allocateRemaining(adapterList, address_count, addrList);
    if (address_count == 0) {
        return adapterList;
    }

    /*
     * Locate any static IP addresses defined in the registry. Validate the
     * addresses against the SIO_GET_INTERFACE_LIST (as registry may have
     * stale settings). If valid we move the addresses from addrList to
     * the adapter.
     */
    {
        adapter *adapterP;

        adapterP = adapterList;
        while (adapterP != NULL) {
            int cnt;
            netaddr *static_addrP;

            /*
             * Skip loopback
             */
            if (strcmp(adapterP->name, "lo") == 0) {
                adapterP = adapterP->next;
                continue;
            }

            /*
             * Get the static addresses for this adapter.
             */
            cnt = getStaticAddresses(env, adapterP->reg_key, &static_addrP);
            if (cnt < 0) {
                free_netaddr(addrList);
                free(adapterList);
                return NULL;
            }

            /*
             * Validate against the SIO_GET_INTERFACE_LIST.
             * (avoids stale registry settings).
             */
            while (static_addrP != NULL) {
                netaddr *addrP = addrList;
                netaddr *prev = NULL;

                while (addrP != NULL) {
                    if (addrP->addr.him4.sin_addr.s_addr == static_addrP->addr.him4.sin_addr.s_addr)
                        break;

                    prev = addrP;
                    addrP = addrP->next;
                }

                /*
                 * if addrP is not NULL it means we have a match
                 * (ie: address from the registry is valid).
                 */
                if (addrP != NULL) {
                    /* remove from addrList */
                    if (prev == NULL) {
                        addrList = addrP->next;
                    } else {
                        prev->next = addrP->next;
                    }
                    address_count--;

                    /* add to adapter list */
                    addrP->next = adapterP->addrs;
                    adapterP->addrs = addrP;
                }

                /*
                 * On the next static address.
                 */
                static_addrP = static_addrP->next;
            }

            /* not needed */
            free_netaddr(static_addrP);

            adapterP = adapterP->next;
        }
    }


    /*
     * Static addresses are now assigned so try again to allocate the
     * remaining addresses. This will succeed if there is one adapter
     * with a dynamically assigned address (DHCP or PPP).
     */
    address_count = allocateRemaining(adapterList, address_count, addrList);
    if (address_count == 0) {
        return adapterList;
    }

    /*
     * Next we see if there is a DHCP address in the registry. If there is
     * an address (and it's valid) then we know it must be bound to a LAN
     * adapter. Additionally, when we enumerate the network adapters
     * we made a crude determination on if an adapter is dial-up. Thus if
     * we know there is one remaining LAN adapter without an IP address
     * then the DHCP address must be bound to it.
     */
    {
        long dhcp_addr = getDHCPAddress(); /* returns in network order */
        if (dhcp_addr) {
            netaddr *addrP, *prevP;

            /*
             * Check that the DHCP address is valid
             */
            addrP = addrList;
            prevP = NULL;
            while (addrP != NULL) {
                if (addrP->addr.him4.sin_addr.s_addr == dhcp_addr) {
                    break;
                }
                prevP = addrP;
                addrP = addrP->next;
            }

            /*
             * Address is valid - now check how many non-WAN adapters
             * don't have addresses yet.
             */
            if (addrP != NULL) {
                adapter *adapterP = adapterList;
                adapter *nobindingsP = NULL;

                while (adapterP != NULL) {
                    if (adapterP->addrs == NULL && !adapterP->is_wan_driver) {
                        if (nobindingsP == NULL) {
                            nobindingsP = adapterP;
                        } else {
                            /* already found one */
                            nobindingsP = NULL;
                            break;
                        }
                    }
                    adapterP = adapterP->next;
                }

                /*
                 * One non-WAN adapter remaining
                 */
                if (nobindingsP != NULL) {
                    nobindingsP->addrs = addrP;

                    /* remove from addrList */
                    if (prevP == NULL) {
                        addrList = addrP->next;
                    } else {
                        prevP->next = addrP->next;
                    }
                    addrP->next = NULL;
                    address_count--;
                }
            }
        }
    }

    /*
     * Finally we do one final attempt to re-assign any remaining
     * addresses. This catches the case of 2 adapters that have their
     * addresses dynamically assigned (specifically NIC with DHCP, and
     * Modem using RAS/PPP).
     */
    address_count = allocateRemaining(adapterList, address_count, addrList);
    if (address_count == 0) {
        return adapterList;
    }

    /*
     * Free any unallocated addresses
     */
    if (address_count > 0) {
        free_netaddr(addrList);
    }

    /*
     * Return the adapter List.
     */
    return adapterList;

}


/*
 * Enumerate network interfaces. If successful returns the number of
 * network interfaces and netifPP returning a list of netif structures.
 * Returns -1 with exception thrown if error.
 */
int enumInterfaces_win9x(JNIEnv *env, netif **netifPP) {
    adapter *adapters, *adapterP;
    int cnt = 0;
    netif *netifP = NULL;

    /* enumerate network configuration */
    adapters = loadConfig(env);
    if (adapters == NULL) {
        return -1;
    }

    /*
     * loadConfig returns an adapter list - we need to create a corresponding
     * list of netif structures.
     */
    adapterP = adapters;
    while (adapterP != NULL) {
        netif *ifs = (netif *)calloc(1, sizeof(netif));

        if (ifs == NULL) {
            JNU_ThrowOutOfMemoryError(env, "heap allocation failure");
            free_adapters(adapters);
            free_netif(netifP);
            return -1;
        }

        ifs->name = _strdup(adapterP->name);
        ifs->displayName = _strdup(adapterP->displayName);
        ifs->dwIndex = adapterP->index;
        ifs->index = adapterP->index;
        ifs->next = netifP;
        netifP = ifs;

        if (ifs->name == NULL || ifs->displayName == NULL) {
            JNU_ThrowOutOfMemoryError(env, "heap allocation failure");
            free_adapters(adapters);
            free_netif(netifP);
            return -1;
        }

        cnt++;
        adapterP = adapterP->next;
    }

    /*
     * Put the adapter list in the cache
     */
    EnterCriticalSection(&cacheLock);
    {
        if (cachedAdapterList != NULL) {
            free_adapters(cachedAdapterList);
        }
        cachedAdapterList = adapters;
    }
    LeaveCriticalSection(&cacheLock);

    /*
     * Return the netif list
     */
    *netifPP = netifP;
    return cnt;
}

/*
 * Enumerate the addresses for the specified network interface. If successful
 * returns the number of addresses bound to the interface and sets netaddrPP
 * to be a list of netaddr structures. Returns -1 if error.
 */
int enumAddresses_win9x(JNIEnv *env, netif *netifP, netaddr **netaddrPP) {

    EnterCriticalSection(&cacheLock);
    {
        adapter *adapterP = cachedAdapterList;
        while (adapterP != NULL) {
            if (strcmp(adapterP->name, netifP->name) == 0) {

                netaddr *newlist = NULL;
                netaddr *curr = adapterP->addrs;
                int cnt = 0;

                while (curr != NULL) {
                    /*
                     * Clone the netaddr and add it to newlist.
                     */
                    netaddr *tmp = (netaddr *)calloc(1, sizeof(netaddr));
                    if (tmp == NULL) {
                        LeaveCriticalSection(&cacheLock);
                        JNU_ThrowOutOfMemoryError(env, "heap allocation failure");
                        free_netaddr(newlist);
                        return -1;
                    }
                    tmp->addr = curr->addr;
                    tmp->next = newlist;
                    newlist = tmp;

                    cnt++;
                    curr = curr->next;
                }

                *netaddrPP = newlist;
                LeaveCriticalSection(&cacheLock);
                return cnt;
            }
            adapterP = adapterP->next;
        }
    }
    LeaveCriticalSection(&cacheLock);

    *netaddrPP = NULL;
    return 0;
}
