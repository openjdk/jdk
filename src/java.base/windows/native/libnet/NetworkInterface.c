/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "net_util.h"

#include "java_net_NetworkInterface.h"
#include "java_net_InetAddress.h"

#include <wbemidl.h>

/*
 * Windows implementation of the java.net.NetworkInterface native methods.
 * This module provides the implementations of getAll, getByName, getByIndex,
 * and getByAddress.
 */

#define NDIS_IF_MAX_BUFFER_SIZE NDIS_IF_MAX_STRING_SIZE + 1
#define NO_PREFIX 255

/* various JNI ids */

jclass ni_class;            /* NetworkInterface */

jmethodID ni_ctor;          /* NetworkInterface() */

jfieldID ni_indexID;        /* NetworkInterface.index */
jfieldID ni_addrsID;        /* NetworkInterface.addrs */
jfieldID ni_bindsID;        /* NetworkInterface.bindings */
jfieldID ni_nameID;         /* NetworkInterface.name */
jfieldID ni_displayNameID;  /* NetworkInterface.displayName */
jfieldID ni_childsID;       /* NetworkInterface.childs */

jclass ni_ibcls;            /* InterfaceAddress */
jmethodID ni_ibctrID;       /* InterfaceAddress() */
jfieldID ni_ibaddressID;        /* InterfaceAddress.address */
jfieldID ni_ibbroadcastID;      /* InterfaceAddress.broadcast */
jfieldID ni_ibmaskID;           /* InterfaceAddress.maskLength */

typedef struct _netaddr {
    SOCKADDR_INET Address;
    SCOPE_ID ScopeId;
    UINT8 PrefixLength;
    struct _netaddr *Next;
} netaddr;

BOOL getAddressTables(MIB_UNICASTIPADDRESS_TABLE **uniAddrs, MIB_ANYCASTIPADDRESS_TABLE **anyAddrs) {
    ADDRESS_FAMILY addrFamily = ipv6_available() ? AF_UNSPEC : AF_INET;
    if (GetUnicastIpAddressTable(addrFamily, uniAddrs) != NO_ERROR) {
        return FALSE;
    }
    if (GetAnycastIpAddressTable(addrFamily, anyAddrs) != NO_ERROR) {
        FreeMibTable(*uniAddrs);
        return FALSE;
    }
    return TRUE;
}

void freeNetaddrs(netaddr *netaddrP) {
    netaddr *curr = netaddrP;
    while (curr != NULL) {
        netaddrP = netaddrP->Next;
        free(curr);
        curr = netaddrP;
    }
}

jobject createNetworkInterface(JNIEnv *env, MIB_IF_ROW2 *ifRow, MIB_UNICASTIPADDRESS_TABLE *uniAddrs, MIB_ANYCASTIPADDRESS_TABLE *anyAddrs) {
    WCHAR ifName[NDIS_IF_MAX_BUFFER_SIZE];
    jobject netifObj, name, displayName, inetAddr, bcastAddr, bindAddr;
    jobjectArray addrArr, bindsArr, childArr;
    netaddr *addrsHead = NULL, *addrsCurrent = NULL;
    int addrCount = 0;
    ULONG i, mask;

    // instantiate the NetworkInterface object
    netifObj = (*env)->NewObject(env, ni_class, ni_ctor);
    if (netifObj == NULL) {
        return NULL;
    }

    // set the NetworkInterface's name
    if (ConvertInterfaceLuidToNameW(&(ifRow->InterfaceLuid), (PWSTR) &ifName, NDIS_IF_MAX_BUFFER_SIZE) != ERROR_SUCCESS) {
        return NULL;
    }
    name = (*env)->NewString(env, (const jchar *) &ifName, (jsize) wcslen((const wchar_t *) &ifName));
    if (name == NULL) {
        return NULL;
    }
    (*env)->SetObjectField(env, netifObj, ni_nameID, name);
    (*env)->DeleteLocalRef(env, name);

    // set the NetworkInterface's display name
    displayName = (*env)->NewString(env, (const jchar *) ifRow->Description, (jsize) wcslen((const wchar_t *) &(ifRow->Description)));
    if (displayName == NULL) {
        return NULL;
    }
    (*env)->SetObjectField(env, netifObj, ni_displayNameID, displayName);
    (*env)->DeleteLocalRef(env, displayName);

    // set the NetworkInterface's index
    (*env)->SetIntField(env, netifObj, ni_indexID, ifRow->InterfaceIndex);

    // find addresses associated with this interface
    for (i = 0; i < uniAddrs->NumEntries; i++) {
        if (uniAddrs->Table[i].InterfaceLuid.Value == ifRow->InterfaceLuid.Value &&
                (uniAddrs->Table[i].DadState == IpDadStatePreferred || uniAddrs->Table[i].DadState == IpDadStateDeprecated)) {
            addrCount++;
            addrsCurrent = malloc(sizeof(netaddr));
            addrsCurrent->Address = uniAddrs->Table[i].Address;
            addrsCurrent->ScopeId = uniAddrs->Table[i].ScopeId;
            addrsCurrent->PrefixLength = uniAddrs->Table[i].OnLinkPrefixLength;
            addrsCurrent->Next = addrsHead;
            addrsHead = addrsCurrent;
        }
    }
    for (i = 0; i < anyAddrs->NumEntries; i++) {
        if (anyAddrs->Table[i].InterfaceLuid.Value == ifRow->InterfaceLuid.Value) {
            addrCount++;
            addrsCurrent = malloc(sizeof(netaddr));
            addrsCurrent->Address = anyAddrs->Table[i].Address;
            addrsCurrent->ScopeId = anyAddrs->Table[i].ScopeId;
            addrsCurrent->PrefixLength = NO_PREFIX;
            addrsCurrent->Next = addrsHead;
            addrsHead = addrsCurrent;
        }
    }

    // instantiate the addrs and bindings array
    addrArr = (*env)->NewObjectArray(env, addrCount, ia_class, NULL);
    if (addrArr == NULL) {
        freeNetaddrs(addrsHead);
        return NULL;
    }
    bindsArr = (*env)->NewObjectArray(env, addrCount, ni_ibcls, NULL);
    if (bindsArr == NULL) {
        freeNetaddrs(addrsHead);
        return NULL;
    }

    // populate the addrs and bindings arrays
    i = 0;
    while (addrsCurrent != NULL) {
        if (addrsCurrent->Address.si_family == AF_INET) { // IPv4
            // create and populate InetAddress object
            inetAddr = (*env)->NewObject(env, ia4_class, ia4_ctrID);
            if (inetAddr == NULL) {
                freeNetaddrs(addrsHead);
                return NULL;
            }
            setInetAddress_addr(env, inetAddr, ntohl(addrsCurrent->Address.Ipv4.sin_addr.s_addr));
            if ((*env)->ExceptionCheck(env)) {
                freeNetaddrs(addrsHead);
                return NULL;
            }

            // create and populate InterfaceAddress object
            bindAddr = (*env)->NewObject(env, ni_ibcls, ni_ibctrID);
            if (bindAddr == NULL) {
                freeNetaddrs(addrsHead);
                return NULL;
            }
            (*env)->SetObjectField(env, bindAddr, ni_ibaddressID, inetAddr);
            if (addrsCurrent->PrefixLength != NO_PREFIX) {
                (*env)->SetShortField(env, bindAddr, ni_ibmaskID, addrsCurrent->PrefixLength);
                if (ConvertLengthToIpv4Mask(addrsCurrent->PrefixLength, &mask) != NO_ERROR) {
                    freeNetaddrs(addrsHead);
                    return NULL;
                }
                bcastAddr = (*env)->NewObject(env, ia4_class, ia4_ctrID);
                if (bcastAddr == NULL) {
                    freeNetaddrs(addrsHead);
                    return NULL;
                }
                setInetAddress_addr(env, bcastAddr, ntohl(addrsCurrent->Address.Ipv4.sin_addr.s_addr | ~mask));
                if ((*env)->ExceptionCheck(env)) {
                    freeNetaddrs(addrsHead);
                    return NULL;
                }
                (*env)->SetObjectField(env, bindAddr, ni_ibbroadcastID, bcastAddr);
                (*env)->DeleteLocalRef(env, bcastAddr);
            }
        } else { // IPv6
            inetAddr = (*env)->NewObject(env, ia6_class, ia6_ctrID);
            if (inetAddr == NULL) {
                freeNetaddrs(addrsHead);
                return NULL;
            }
            if (setInet6Address_ipaddress(env, inetAddr, (jbyte *)&(addrsCurrent->Address.Ipv6.sin6_addr.s6_addr)) == JNI_FALSE) {
                freeNetaddrs(addrsHead);
                return NULL;
            }
            if (addrsCurrent->Address.Ipv6.sin6_scope_id != 0) { /* zero is default value, no need to set */
                setInet6Address_scopeid(env, inetAddr, addrsCurrent->Address.Ipv6.sin6_scope_id);
                setInet6Address_scopeifname(env, inetAddr, netifObj);
            }
            bindAddr = (*env)->NewObject(env, ni_ibcls, ni_ibctrID);
            if (bindAddr == NULL) {
                freeNetaddrs(addrsHead);
                return NULL;
            }
            (*env)->SetObjectField(env, bindAddr, ni_ibaddressID, inetAddr);
            if (addrsCurrent->PrefixLength != NO_PREFIX) {
                (*env)->SetShortField(env, bindAddr, ni_ibmaskID, addrsCurrent->PrefixLength);
            }
        }

        // add the new elements to the arrays
        (*env)->SetObjectArrayElement(env, addrArr, i, inetAddr);
        (*env)->DeleteLocalRef(env, inetAddr);
        (*env)->SetObjectArrayElement(env, bindsArr, i, bindAddr);
        (*env)->DeleteLocalRef(env, bindAddr);

        // advance to the next address
        addrsCurrent = addrsCurrent->Next;
        i++;
    }

    // free the address list since we no longer need it
    freeNetaddrs(addrsHead);

    // set the addrs and bindings arrays on the NetworkInterface
    (*env)->SetObjectField(env, netifObj, ni_addrsID, addrArr);
    (*env)->DeleteLocalRef(env, addrArr);
    (*env)->SetObjectField(env, netifObj, ni_bindsID, bindsArr);
    (*env)->DeleteLocalRef(env, bindsArr);

    // set child array on the NetworkInterface
    // Windows doesn't have virtual interfaces, so this is always empty
    childArr = (*env)->NewObjectArray(env, 0, ni_class, NULL);
    if (childArr == NULL) {
        return NULL;
    }
    (*env)->SetObjectField(env, netifObj, ni_childsID, childArr);
    (*env)->DeleteLocalRef(env, childArr);

    return netifObj;
}

jobject createNetworkInterfaceForSingleRowWithTables(JNIEnv *env, MIB_IF_ROW2 *ifRow, MIB_UNICASTIPADDRESS_TABLE *uniAddrs, MIB_ANYCASTIPADDRESS_TABLE *anyAddrs) {
    if (GetIfEntry2(ifRow) != NO_ERROR) {
        return NULL;
    }
    return createNetworkInterface(env, ifRow, uniAddrs, anyAddrs);
}

jobject createNetworkInterfaceForSingleRow(JNIEnv *env, MIB_IF_ROW2 *ifRow) {
    MIB_UNICASTIPADDRESS_TABLE *uniAddrs;
    MIB_ANYCASTIPADDRESS_TABLE *anyAddrs;
    jobject netifObj;

    if (getAddressTables(&uniAddrs, &anyAddrs) == FALSE) {
        return NULL;
    }

    netifObj = createNetworkInterfaceForSingleRowWithTables(env, ifRow, uniAddrs, anyAddrs);

    FreeMibTable(uniAddrs);
    FreeMibTable(anyAddrs);

    return netifObj;
}

/*
 * Class:     NetworkInterface
 * Method:    getByIndex0
 * Signature: (I)LNetworkInterface;
 */
JNIEXPORT jobject JNICALL Java_java_net_NetworkInterface_getByIndex0(JNIEnv *env, jclass cls, jint index) {
    MIB_IF_ROW2 ifRow = {0};

    ifRow.InterfaceIndex = index;
    return createNetworkInterfaceForSingleRow(env, &ifRow);
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    getByName0
 * Signature: (Ljava/lang/String;)Ljava/net/NetworkInterface;
 */
JNIEXPORT jobject JNICALL Java_java_net_NetworkInterface_getByName0(JNIEnv *env, jclass cls, jstring name) {
    const jchar *nameChars;
    DWORD convertResult;
    MIB_IF_ROW2 ifRow = {0};

    nameChars = (*env)->GetStringChars(env, name, NULL);
    convertResult = ConvertInterfaceNameToLuidW(nameChars, &(ifRow.InterfaceLuid));
    (*env)->ReleaseStringChars(env, name, nameChars);
    if (convertResult != ERROR_SUCCESS) {
        return NULL;
    }
    return createNetworkInterfaceForSingleRow(env, &ifRow);
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    getByInetAddress0
 * Signature: (Ljava/net/InetAddress;)Ljava/net/NetworkInterface;
 */
JNIEXPORT jobject JNICALL Java_java_net_NetworkInterface_getByInetAddress0(JNIEnv *env, jclass cls, jobject inetAddr) {
    MIB_UNICASTIPADDRESS_TABLE *uniAddrs;
    MIB_ANYCASTIPADDRESS_TABLE *anyAddrs;
    ULONG i;
    MIB_IF_ROW2 ifRow = {0};
    jobject result = NULL;

    if (getAddressTables(&uniAddrs, &anyAddrs) == FALSE) {
        return JNI_FALSE;
    }

    for (i = 0; i < uniAddrs->NumEntries; i++) {
        if (NET_SockaddrEqualsInetAddress(env, (SOCKETADDRESS*) &(uniAddrs->Table[i].Address), inetAddr) &&
                (uniAddrs->Table[i].DadState == IpDadStatePreferred || uniAddrs->Table[i].DadState == IpDadStateDeprecated)) {
            ifRow.InterfaceLuid = uniAddrs->Table[i].InterfaceLuid;
            result = createNetworkInterfaceForSingleRowWithTables(env, &ifRow, uniAddrs, anyAddrs);
            goto done;
        }
    }
    for (i = 0; i < anyAddrs->NumEntries; i++) {
        if (NET_SockaddrEqualsInetAddress(env, (SOCKETADDRESS*) &(anyAddrs->Table[i].Address), inetAddr)) {
            ifRow.InterfaceLuid = anyAddrs->Table[i].InterfaceLuid;
            result = createNetworkInterfaceForSingleRowWithTables(env, &ifRow, uniAddrs, anyAddrs);
            goto done;
        }
    }

    done:
    FreeMibTable(uniAddrs);
    FreeMibTable(anyAddrs);
    return result;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    boundInetAddress0
 * Signature: (Ljava/net/InetAddress;)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_boundInetAddress0(JNIEnv *env, jclass cls, jobject inetAddr) {
    MIB_UNICASTIPADDRESS_TABLE *uniAddrs;
    MIB_ANYCASTIPADDRESS_TABLE *anyAddrs;
    ULONG i;
    jboolean result = JNI_FALSE;

    if (getAddressTables(&uniAddrs, &anyAddrs) == FALSE) {
        return JNI_FALSE;
    }

    for (i = 0; i < uniAddrs->NumEntries; i++) {
        if (NET_SockaddrEqualsInetAddress(env, (SOCKETADDRESS*) &(uniAddrs->Table[i].Address), inetAddr) &&
                (uniAddrs->Table[i].DadState == IpDadStatePreferred || uniAddrs->Table[i].DadState == IpDadStateDeprecated)) {
            result = JNI_TRUE;
            goto done;
        }
    }
    for (i = 0; i < anyAddrs->NumEntries; i++) {
        if (NET_SockaddrEqualsInetAddress(env, (SOCKETADDRESS*) &(anyAddrs->Table[i].Address), inetAddr)) {
            result = JNI_TRUE;
            goto done;
        }
    }

    done:
    FreeMibTable(uniAddrs);
    FreeMibTable(anyAddrs);
    return result;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    getAll
 * Signature: ()[Ljava/net/NetworkInterface;
 */
JNIEXPORT jobjectArray JNICALL Java_java_net_NetworkInterface_getAll(JNIEnv *env, jclass cls) {
    MIB_IF_TABLE2 *ifTable;
    jobjectArray ifArray;
    MIB_UNICASTIPADDRESS_TABLE *uniAddrs;
    MIB_ANYCASTIPADDRESS_TABLE *anyAddrs;
    ULONG i;
    jobject ifObj;

    if (GetIfTable2(&ifTable) != NO_ERROR) {
        return NULL;
    }

    ifArray = (*env)->NewObjectArray(env, ifTable->NumEntries, cls, NULL);
    if (ifArray == NULL) {
        FreeMibTable(ifTable);
        return NULL;
    }

    if (getAddressTables(&uniAddrs, &anyAddrs) == FALSE) {
        FreeMibTable(ifTable);
        return NULL;
    }

    for (i = 0; i < ifTable->NumEntries; i++) {
        ifObj = createNetworkInterface(env, &(ifTable->Table[i]), uniAddrs, anyAddrs);
        if (ifObj == NULL) {
            FreeMibTable(ifTable);
            FreeMibTable(uniAddrs);
            FreeMibTable(anyAddrs);
            return NULL;
        }
        (*env)->SetObjectArrayElement(env, ifArray, i, ifObj);
        (*env)->DeleteLocalRef(env, ifObj);
    }

    FreeMibTable(ifTable);
    FreeMibTable(uniAddrs);
    FreeMibTable(anyAddrs);
    return ifArray;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    isUp0
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isUp0(JNIEnv *env, jclass cls, jstring name, jint index) {
    MIB_IF_ROW2 ifRow = {0};

    ifRow.InterfaceIndex = index;
    if (GetIfEntry2(&ifRow) != NO_ERROR) {
        return JNI_FALSE;
    }
    return ifRow.AdminStatus == NET_IF_ADMIN_STATUS_UP && ifRow.OperStatus == IfOperStatusUp ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    isP2P0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isP2P0(JNIEnv *env, jclass cls, jstring name, jint index) {
    MIB_IF_ROW2 ifRow = {0};

    ifRow.InterfaceIndex = index;
    if (GetIfEntry2(&ifRow) != NO_ERROR) {
        return JNI_FALSE;
    }
    return ifRow.AccessType == NET_IF_ACCESS_POINT_TO_POINT ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    isLoopback0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isLoopback0(JNIEnv *env, jclass cls, jstring name, jint index) {
    MIB_IF_ROW2 ifRow = {0};

    ifRow.InterfaceIndex = index;
    if (GetIfEntry2(&ifRow) != NO_ERROR) {
        return JNI_FALSE;
    }
    return ifRow.Type == IF_TYPE_SOFTWARE_LOOPBACK ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    getMacAddr0
 * Signature: ([bLjava/lang/String;I)[b
 */
JNIEXPORT jbyteArray JNICALL Java_java_net_NetworkInterface_getMacAddr0(JNIEnv *env, jclass class, jbyteArray addrArray, jstring name, jint index) {
    MIB_IF_ROW2 ifRow = {0};
    jbyteArray macAddr;

    ifRow.InterfaceIndex = index;
    if (GetIfEntry2(&ifRow) != NO_ERROR) {
        return NULL;
    }
    if (ifRow.PhysicalAddressLength == 0) {
        return NULL;
    }
    macAddr = (*env)->NewByteArray(env, ifRow.PhysicalAddressLength);
    if (macAddr == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, macAddr, 0, ifRow.PhysicalAddressLength, (jbyte *) ifRow.PhysicalAddress);
    return macAddr;
}

/*
 * Class:       java_net_NetworkInterface
 * Method:      getMTU0
 * Signature:   ([bLjava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_java_net_NetworkInterface_getMTU0(JNIEnv *env, jclass class, jstring name, jint index) {
    MIB_IF_ROW2 ifRow = {0};

    ifRow.InterfaceIndex = index;
    if (GetIfEntry2(&ifRow) != NO_ERROR) {
        return -1;
    }
    return ifRow.Mtu;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    supportsMulticast0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_supportsMulticast0(JNIEnv *env, jclass cls, jstring name, jint index) {
    /*
    In older versions of Windows, multicast could be disabled for individual interfaces.
    That does not appear to be supported any longer.
    Now, it seems to be a system-wide setting, but with separate settings for IPv4 vs IPv6.
    We also have the additional complication that multicast sending can be enabled while receiving is disabled.

    There are not many ways to access these settings.
    GetAdaptersAddresses has a NO_MULTICAST flag, but I have never seen it set, even when multicast is disabled.
    The flags appear to be stored in the registry, but the exact location seems to change depending on the Windows version.
    We could open a socket and try to join a multicast group. If receiving is disabled, that fails. But if enabled, it generates network traffic.
    I also tried some socket IOCTLs (e.g. SIO_ROUTING_INTERFACE_QUERY) but all of those succeeded even when multicast was disabled.
    Same for various setsockopt params (e.g. IP_MULTICAST_IF).
    So using COM to execute a WMI query was the best solution I could come up with.

    The PowerShell equivalent of this code is:
    Get-WmiObject -Query "select MldLevel from MSFT_NetIPv6Protocol" -Namespace "ROOT/StandardCimv2"
    Subsitute IPv4 if desired. The property is still called MldLevel here even though it is IGMPLevel elsewhere.

    You can also change the value using PowerShell (admin rights needed):
    Set-NetIPv4Protocol -IGMPLevel <None, SendOnly, All>
    Set-NetIPv6Protocol -MldLevel <None, SendOnly, All>
    */
    jboolean retVal = JNI_FALSE;
    IWbemLocator *locator;
    IWbemServices *services;
    IEnumWbemClassObject *results;
    IWbemClassObject *result;
    ULONG returnedCount;
    BSTR resource, language, query;
    VARIANT value;

    if (FAILED(CoInitializeEx(NULL, COINIT_MULTITHREADED))) {
        goto done0;
    }

    if (FAILED(CoInitializeSecurity(NULL, -1, NULL, NULL, RPC_C_AUTHN_LEVEL_DEFAULT, RPC_C_IMP_LEVEL_IMPERSONATE, NULL, EOAC_NONE, NULL))) {
        goto done1;
    }

    if (FAILED(CoCreateInstance(&CLSID_WbemLocator, NULL, CLSCTX_INPROC_SERVER, &IID_IWbemLocator, &locator))) {
        goto done1;
    }

    resource = SysAllocString(L"ROOT\\StandardCimv2");
    if (resource == NULL) {
        goto done2;
    }

    language = SysAllocString(L"WQL");
    if (language == NULL) {
        goto done3;
    }

    query = SysAllocString(ipv6_available() ? L"SELECT MldLevel FROM MSFT_NetIPv6Protocol" : L"SELECT MldLevel FROM MSFT_NetIPv4Protocol");
    if (query == NULL) {
        goto done4;
    }

    if (FAILED(locator->lpVtbl->ConnectServer(locator, resource, NULL, NULL, NULL, 0, NULL, NULL, &services))) {
        goto done5;
    }

    if (FAILED(services->lpVtbl->ExecQuery(services, language, query, WBEM_FLAG_BIDIRECTIONAL, NULL, &results))) {
        goto done6;
    }

    if (FAILED(results->lpVtbl->Next(results, WBEM_INFINITE, 1, &result, &returnedCount))) {
        goto done7;
    }

    if (returnedCount == 0) {
        goto done8;
    }

    if (FAILED(result->lpVtbl->Get(result, L"MldLevel", 0, &value, NULL, NULL))) {
        goto done8; // note that we must NOT call VariantClear in this case
    }

    // 0 = None; 1 = SendOnly; 2 = All
    retVal = value.uintVal == 0 ? JNI_FALSE : JNI_TRUE;

    VariantClear(&value);

    done8:
    result->lpVtbl->Release(result);

    done7:
    results->lpVtbl->Release(results);

    done6:
    services->lpVtbl->Release(services);

    done5:
    SysFreeString(query);

    done4:
    SysFreeString(language);

    done3:
    SysFreeString(resource);

    done2:
    locator->lpVtbl->Release(locator);

    done1:
    CoUninitialize();

    done0:
    return retVal;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_java_net_NetworkInterface_init(JNIEnv *env, jclass cls) {
    /*
     * Get the various JNI ids that we require
     */
    ni_class = (*env)->NewGlobalRef(env, cls);
    CHECK_NULL(ni_class);
    ni_nameID = (*env)->GetFieldID(env, ni_class, "name", "Ljava/lang/String;");
    CHECK_NULL(ni_nameID);
    ni_displayNameID = (*env)->GetFieldID(env, ni_class, "displayName", "Ljava/lang/String;");
    CHECK_NULL(ni_displayNameID);
    ni_indexID = (*env)->GetFieldID(env, ni_class, "index", "I");
    CHECK_NULL(ni_indexID);
    ni_addrsID = (*env)->GetFieldID(env, ni_class, "addrs", "[Ljava/net/InetAddress;");
    CHECK_NULL(ni_addrsID);
    ni_bindsID = (*env)->GetFieldID(env, ni_class, "bindings", "[Ljava/net/InterfaceAddress;");
    CHECK_NULL(ni_bindsID);
    ni_childsID = (*env)->GetFieldID(env, ni_class, "childs", "[Ljava/net/NetworkInterface;");
    CHECK_NULL(ni_childsID);
    ni_ctor = (*env)->GetMethodID(env, ni_class, "<init>", "()V");
    CHECK_NULL(ni_ctor);
    ni_ibcls = (*env)->FindClass(env, "java/net/InterfaceAddress");
    CHECK_NULL(ni_ibcls);
    ni_ibcls = (*env)->NewGlobalRef(env, ni_ibcls);
    CHECK_NULL(ni_ibcls);
    ni_ibctrID = (*env)->GetMethodID(env, ni_ibcls, "<init>", "()V");
    CHECK_NULL(ni_ibctrID);
    ni_ibaddressID = (*env)->GetFieldID(env, ni_ibcls, "address", "Ljava/net/InetAddress;");
    CHECK_NULL(ni_ibaddressID);
    ni_ibbroadcastID = (*env)->GetFieldID(env, ni_ibcls, "broadcast", "Ljava/net/Inet4Address;");
    CHECK_NULL(ni_ibbroadcastID);
    ni_ibmaskID = (*env)->GetFieldID(env, ni_ibcls, "maskLength", "S");
    CHECK_NULL(ni_ibmaskID);

    initInetAddressIDs(env);
}
