/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "NetworkInterface.h"

#include "java_net_InetAddress.h"
#include "java_net_NetworkInterface.h"

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

/*
 * Gets the unicast and anycast IP address tables.
 * If an error occurs while fetching a table,
 * any tables already fetched are freed and an exception is set.
 * It is the caller's responsibility to free the tables when they are no longer needed.
 */
static BOOL getAddressTables(
        JNIEnv *env, MIB_UNICASTIPADDRESS_TABLE **uniAddrs,
        MIB_ANYCASTIPADDRESS_TABLE **anyAddrs) {
    ULONG apiRetVal;
    ADDRESS_FAMILY addrFamily = ipv6_available() ? AF_UNSPEC : AF_INET;

    apiRetVal = GetUnicastIpAddressTable(addrFamily, uniAddrs);
    if (apiRetVal != NO_ERROR) {
        SetLastError(apiRetVal);
        NET_ThrowByNameWithLastError(
                env, JNU_JAVANETPKG "SocketException", "GetUnicastIpAddressTable");
        return FALSE;
    }
    apiRetVal = GetAnycastIpAddressTable(addrFamily, anyAddrs);
    if (apiRetVal != NO_ERROR) {
        FreeMibTable(*uniAddrs);
        SetLastError(apiRetVal);
        NET_ThrowByNameWithLastError(
                env, JNU_JAVANETPKG "SocketException", "GetAnycastIpAddressTable");
        return FALSE;
    }
    return TRUE;
}

/*
 * Frees a linked list of netaddr structs.
 */
static void freeNetaddrs(netaddr *netaddrP) {
    netaddr *curr = netaddrP;
    while (curr != NULL) {
        netaddrP = netaddrP->Next;
        free(curr);
        curr = netaddrP;
    }
}

/*
 * Builds and returns a java.net.NetworkInterface object from the given MIB_IF_ROW2.
 * Unlike createNetworkInterfaceForSingleRowWithTables,
 * this expects that the row is already populated, either by GetIfEntry2 or GetIfTable2.
 * If anything goes wrong, an exception will be set,
 * but the address tables are not freed.
 * Freeing the address tables is always the caller's responsibility.
 */
static jobject createNetworkInterface(
        JNIEnv *env, MIB_IF_ROW2 *ifRow, MIB_UNICASTIPADDRESS_TABLE *uniAddrs,
        MIB_ANYCASTIPADDRESS_TABLE *anyAddrs) {
    WCHAR ifName[NDIS_IF_MAX_BUFFER_SIZE];
    jobject netifObj, name, displayName, inetAddr, bcastAddr, bindAddr;
    jobjectArray addrArr, bindsArr, childArr;
    netaddr *addrsHead = NULL, *addrsCurrent = NULL;
    int addrCount = 0;
    ULONG apiRetVal, i, mask;

    // instantiate the NetworkInterface object
    netifObj = (*env)->NewObject(env, ni_class, ni_ctor);
    if (netifObj == NULL) {
        return NULL;
    }

    // set the NetworkInterface's name
    apiRetVal = ConvertInterfaceLuidToNameW(
            &(ifRow->InterfaceLuid), ifName, NDIS_IF_MAX_BUFFER_SIZE);
    if (apiRetVal != ERROR_SUCCESS) {
        SetLastError(apiRetVal);
        NET_ThrowByNameWithLastError(
                env, JNU_JAVANETPKG "SocketException", "ConvertInterfaceLuidToNameW");
        return NULL;
    }
    name = (*env)->NewString(env, ifName, (jsize) wcslen(ifName));
    if (name == NULL) {
        return NULL;
    }
    (*env)->SetObjectField(env, netifObj, ni_nameID, name);
    (*env)->DeleteLocalRef(env, name);

    // set the NetworkInterface's display name
    displayName = (*env)->NewString(
            env, ifRow->Description, (jsize) wcslen(ifRow->Description));
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
                (uniAddrs->Table[i].DadState == IpDadStatePreferred ||
                        uniAddrs->Table[i].DadState == IpDadStateDeprecated)) {
            addrCount++;
            addrsCurrent = malloc(sizeof(netaddr));
            if (addrsCurrent == NULL) {
                freeNetaddrs(addrsHead);
                JNU_ThrowOutOfMemoryError(env, "native heap");
                return NULL;
            }
            addrsCurrent->Address = uniAddrs->Table[i].Address;
            addrsCurrent->PrefixLength = uniAddrs->Table[i].OnLinkPrefixLength;
            addrsCurrent->Next = addrsHead;
            addrsHead = addrsCurrent;
        }
    }
    for (i = 0; i < anyAddrs->NumEntries; i++) {
        if (anyAddrs->Table[i].InterfaceLuid.Value == ifRow->InterfaceLuid.Value) {
            addrCount++;
            addrsCurrent = malloc(sizeof(netaddr));
            if (addrsCurrent == NULL) {
                freeNetaddrs(addrsHead);
                JNU_ThrowOutOfMemoryError(env, "native heap");
                return NULL;
            }
            addrsCurrent->Address = anyAddrs->Table[i].Address;
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
            setInetAddress_addr(
                    env, inetAddr, ntohl(addrsCurrent->Address.Ipv4.sin_addr.s_addr));
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
                (*env)->SetShortField(
                        env, bindAddr, ni_ibmaskID, addrsCurrent->PrefixLength);
                apiRetVal = ConvertLengthToIpv4Mask(addrsCurrent->PrefixLength, &mask);
                if (apiRetVal != NO_ERROR) {
                    freeNetaddrs(addrsHead);
                    SetLastError(apiRetVal);
                    NET_ThrowByNameWithLastError(
                            env, JNU_JAVANETPKG "SocketException",
                            "ConvertLengthToIpv4Mask");
                    return NULL;
                }
                bcastAddr = (*env)->NewObject(env, ia4_class, ia4_ctrID);
                if (bcastAddr == NULL) {
                    freeNetaddrs(addrsHead);
                    return NULL;
                }
                setInetAddress_addr(
                        env, bcastAddr,
                        ntohl(addrsCurrent->Address.Ipv4.sin_addr.s_addr | ~mask));
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
            if (setInet6Address_ipaddress(
                    env, inetAddr,
                    (jbyte *)&(addrsCurrent->Address.Ipv6.sin6_addr.s6_addr))
                    == JNI_FALSE) {
                freeNetaddrs(addrsHead);
                return NULL;
            }
            /* zero is default value, no need to set */
            if (addrsCurrent->Address.Ipv6.sin6_scope_id != 0) {
                setInet6Address_scopeid(
                        env, inetAddr, addrsCurrent->Address.Ipv6.sin6_scope_id);
                setInet6Address_scopeifname(env, inetAddr, netifObj);
            }
            bindAddr = (*env)->NewObject(env, ni_ibcls, ni_ibctrID);
            if (bindAddr == NULL) {
                freeNetaddrs(addrsHead);
                return NULL;
            }
            (*env)->SetObjectField(env, bindAddr, ni_ibaddressID, inetAddr);
            if (addrsCurrent->PrefixLength != NO_PREFIX) {
                (*env)->SetShortField(
                        env, bindAddr, ni_ibmaskID, addrsCurrent->PrefixLength);
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

/*
 * Builds and returns a java.net.NetworkInterface object from the given MIB_IF_ROW2.
 * This expects that the row is not yet populated, but an index has been set,
 * so the row is ready to be populated by GetIfEntry2.
 * If anything goes wrong, an exception will be set,
 * but the address tables are not freed.
 * Freeing the address tables is always the caller's responsibility.
 */
static jobject createNetworkInterfaceForSingleRowWithTables(
        JNIEnv *env, MIB_IF_ROW2 *ifRow,
        MIB_UNICASTIPADDRESS_TABLE *uniAddrs, MIB_ANYCASTIPADDRESS_TABLE *anyAddrs) {
    ULONG apiRetVal;

    apiRetVal = GetIfEntry2(ifRow);
    if (apiRetVal != NO_ERROR) {
        if (apiRetVal != ERROR_FILE_NOT_FOUND) {
            SetLastError(apiRetVal);
            NET_ThrowByNameWithLastError(
                    env, JNU_JAVANETPKG "SocketException", "GetIfEntry2");
        }
        return NULL;
    }
    return createNetworkInterface(env, ifRow, uniAddrs, anyAddrs);
}

/*
 * Builds and returns a java.net.NetworkInterface object from the given MIB_IF_ROW2.
 * This expects that the row is not yet populated, but an index has been set,
 * so the row is ready to be populated by GetIfEntry2.
 * Unlike createNetworkInterfaceForSingleRowWithTables, this will get the address
 * tables at the beginning and free them at the end.
 * If anything goes wrong, an exception will be set.
 */
static jobject createNetworkInterfaceForSingleRow(
        JNIEnv *env, MIB_IF_ROW2 *ifRow) {
    MIB_UNICASTIPADDRESS_TABLE *uniAddrs;
    MIB_ANYCASTIPADDRESS_TABLE *anyAddrs;
    jobject netifObj;

    if (getAddressTables(env, &uniAddrs, &anyAddrs) == FALSE) {
        return NULL;
    }

    netifObj = createNetworkInterfaceForSingleRowWithTables(
            env, ifRow, uniAddrs, anyAddrs);

    FreeMibTable(uniAddrs);
    FreeMibTable(anyAddrs);

    return netifObj;
}

/*
 * Class:     NetworkInterface
 * Method:    getByIndex0
 * Signature: (I)LNetworkInterface;
 */
JNIEXPORT jobject JNICALL Java_java_net_NetworkInterface_getByIndex0(
        JNIEnv *env, jclass cls, jint index) {
    MIB_IF_ROW2 ifRow = {0};

    if (index == 0) {
        // 0 is never a valid index, and would make GetIfEntry2 think nothing is set
        return NULL;
    }

    ifRow.InterfaceIndex = index;
    return createNetworkInterfaceForSingleRow(env, &ifRow);
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    getByName0
 * Signature: (Ljava/lang/String;)Ljava/net/NetworkInterface;
 */
JNIEXPORT jobject JNICALL Java_java_net_NetworkInterface_getByName0(
        JNIEnv *env, jclass cls, jstring name) {
    const jchar *nameChars;
    ULONG apiRetVal;
    MIB_IF_ROW2 ifRow = {0};

    nameChars = (*env)->GetStringChars(env, name, NULL);
    apiRetVal = ConvertInterfaceNameToLuidW(nameChars, &(ifRow.InterfaceLuid));
    (*env)->ReleaseStringChars(env, name, nameChars);
    if (apiRetVal != ERROR_SUCCESS) {
        if (apiRetVal != ERROR_INVALID_NAME) {
            SetLastError(apiRetVal);
            NET_ThrowByNameWithLastError(
                    env, JNU_JAVANETPKG "SocketException",
                    "ConvertInterfaceNameToLuidW");
        }
        return NULL;
    }
    return createNetworkInterfaceForSingleRow(env, &ifRow);
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    getByInetAddress0
 * Signature: (Ljava/net/InetAddress;)Ljava/net/NetworkInterface;
 */
JNIEXPORT jobject JNICALL Java_java_net_NetworkInterface_getByInetAddress0(
        JNIEnv *env, jclass cls, jobject inetAddr) {
    MIB_UNICASTIPADDRESS_TABLE *uniAddrs;
    MIB_ANYCASTIPADDRESS_TABLE *anyAddrs;
    ULONG i;
    MIB_IF_ROW2 ifRow = {0};
    jobject result = NULL;

    if (getAddressTables(env, &uniAddrs, &anyAddrs) == FALSE) {
        return NULL;
    }

    for (i = 0; i < uniAddrs->NumEntries; i++) {
        if (NET_SockaddrEqualsInetAddress(
                env, (SOCKETADDRESS*) &(uniAddrs->Table[i].Address), inetAddr) &&
                (uniAddrs->Table[i].DadState == IpDadStatePreferred ||
                        uniAddrs->Table[i].DadState == IpDadStateDeprecated)) {
            ifRow.InterfaceLuid = uniAddrs->Table[i].InterfaceLuid;
            result = createNetworkInterfaceForSingleRowWithTables(
                    env, &ifRow, uniAddrs, anyAddrs);
            goto done;
        }
    }
    for (i = 0; i < anyAddrs->NumEntries; i++) {
        if (NET_SockaddrEqualsInetAddress(
                env, (SOCKETADDRESS*) &(anyAddrs->Table[i].Address), inetAddr)) {
            ifRow.InterfaceLuid = anyAddrs->Table[i].InterfaceLuid;
            result = createNetworkInterfaceForSingleRowWithTables(
                    env, &ifRow, uniAddrs, anyAddrs);
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
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_boundInetAddress0(
        JNIEnv *env, jclass cls, jobject inetAddr) {
    MIB_UNICASTIPADDRESS_TABLE *uniAddrs;
    MIB_ANYCASTIPADDRESS_TABLE *anyAddrs;
    ULONG i;
    jboolean result = JNI_FALSE;

    if (getAddressTables(env, &uniAddrs, &anyAddrs) == FALSE) {
        return JNI_FALSE;
    }

    for (i = 0; i < uniAddrs->NumEntries; i++) {
        if (NET_SockaddrEqualsInetAddress(
                env, (SOCKETADDRESS*) &(uniAddrs->Table[i].Address), inetAddr) &&
                (uniAddrs->Table[i].DadState == IpDadStatePreferred ||
                        uniAddrs->Table[i].DadState == IpDadStateDeprecated)) {
            result = JNI_TRUE;
            goto done;
        }
    }
    for (i = 0; i < anyAddrs->NumEntries; i++) {
        if (NET_SockaddrEqualsInetAddress(
                env, (SOCKETADDRESS*) &(anyAddrs->Table[i].Address), inetAddr)) {
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
JNIEXPORT jobjectArray JNICALL Java_java_net_NetworkInterface_getAll(
        JNIEnv *env, jclass cls) {
    MIB_IF_TABLE2 *ifTable;
    jobjectArray ifArray;
    MIB_UNICASTIPADDRESS_TABLE *uniAddrs;
    MIB_ANYCASTIPADDRESS_TABLE *anyAddrs;
    ULONG apiRetVal, i;
    jobject ifObj;

    apiRetVal = GetIfTable2(&ifTable);
    if (apiRetVal != NO_ERROR) {
        SetLastError(apiRetVal);
        NET_ThrowByNameWithLastError(
                env, JNU_JAVANETPKG "SocketException", "GetIfTable2");
        return NULL;
    }

    ifArray = (*env)->NewObjectArray(env, ifTable->NumEntries, cls, NULL);
    if (ifArray == NULL) {
        FreeMibTable(ifTable);
        return NULL;
    }

    if (getAddressTables(env, &uniAddrs, &anyAddrs) == FALSE) {
        FreeMibTable(ifTable);
        return NULL;
    }

    for (i = 0; i < ifTable->NumEntries; i++) {
        ifObj = createNetworkInterface(
                env, &(ifTable->Table[i]), uniAddrs, anyAddrs);
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
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isUp0(
        JNIEnv *env, jclass cls, jstring name, jint index) {
    MIB_IF_ROW2 ifRow = {0};
    ULONG apiRetVal;

    ifRow.InterfaceIndex = index;
    apiRetVal = GetIfEntry2(&ifRow);
    if (apiRetVal != NO_ERROR) {
        SetLastError(apiRetVal);
        NET_ThrowByNameWithLastError(
                env, JNU_JAVANETPKG "SocketException", "GetIfEntry2");
        return JNI_FALSE;
    }
    return ifRow.AdminStatus == NET_IF_ADMIN_STATUS_UP &&
            ifRow.OperStatus == IfOperStatusUp
            ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    isP2P0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isP2P0(
        JNIEnv *env, jclass cls, jstring name, jint index) {
    MIB_IF_ROW2 ifRow = {0};
    ULONG apiRetVal;

    ifRow.InterfaceIndex = index;
    apiRetVal = GetIfEntry2(&ifRow);
    if (apiRetVal != NO_ERROR) {
        SetLastError(apiRetVal);
        NET_ThrowByNameWithLastError(
                env, JNU_JAVANETPKG "SocketException", "GetIfEntry2");
        return JNI_FALSE;
    }
    return ifRow.AccessType == NET_IF_ACCESS_POINT_TO_POINT ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    isLoopback0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isLoopback0(
        JNIEnv *env, jclass cls, jstring name, jint index) {
    MIB_IF_ROW2 ifRow = {0};
    ULONG apiRetVal;

    ifRow.InterfaceIndex = index;
    apiRetVal = GetIfEntry2(&ifRow);
    if (apiRetVal != NO_ERROR) {
        SetLastError(apiRetVal);
        NET_ThrowByNameWithLastError(
                env, JNU_JAVANETPKG "SocketException", "GetIfEntry2");
        return JNI_FALSE;
    }
    return ifRow.Type == IF_TYPE_SOFTWARE_LOOPBACK ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    getMacAddr0
 * Signature: ([bLjava/lang/String;I)[b
 */
JNIEXPORT jbyteArray JNICALL Java_java_net_NetworkInterface_getMacAddr0(
        JNIEnv *env, jclass class, jbyteArray addrArray, jstring name, jint index) {
    MIB_IF_ROW2 ifRow = {0};
    ULONG apiRetVal;
    jbyteArray macAddr;

    ifRow.InterfaceIndex = index;
    apiRetVal = GetIfEntry2(&ifRow);
    if (apiRetVal != NO_ERROR) {
        SetLastError(apiRetVal);
        NET_ThrowByNameWithLastError(
                env, JNU_JAVANETPKG "SocketException", "GetIfEntry2");
        return NULL;
    }
    if (ifRow.PhysicalAddressLength == 0) {
        return NULL;
    }
    macAddr = (*env)->NewByteArray(env, ifRow.PhysicalAddressLength);
    if (macAddr == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(
            env, macAddr, 0, ifRow.PhysicalAddressLength,
            (jbyte *) ifRow.PhysicalAddress);
    return macAddr;
}

/*
 * Class:       java_net_NetworkInterface
 * Method:      getMTU0
 * Signature:   ([bLjava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_java_net_NetworkInterface_getMTU0(
        JNIEnv *env, jclass class, jstring name, jint index) {
    MIB_IF_ROW2 ifRow = {0};
    ULONG apiRetVal;

    ifRow.InterfaceIndex = index;
    apiRetVal = GetIfEntry2(&ifRow);
    if (apiRetVal != NO_ERROR) {
        SetLastError(apiRetVal);
        NET_ThrowByNameWithLastError(
                env, JNU_JAVANETPKG "SocketException", "GetIfEntry2");
        return -1;
    }
    return ifRow.Mtu;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    supportsMulticast0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_supportsMulticast0(
        JNIEnv *env, jclass cls, jstring name, jint index) {
    // we assume that multicast is enabled, because there are no reliable APIs to tell us
    return JNI_TRUE;
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
