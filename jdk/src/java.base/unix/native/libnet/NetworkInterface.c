/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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


#include <errno.h>
#include <strings.h>
#if defined(_ALLBSD_SOURCE) && defined(__OpenBSD__)
#include <sys/types.h>
#endif
#include <netinet/in.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <net/if.h>
#include <net/if_arp.h>

#ifdef __solaris__
#include <sys/dlpi.h>
#include <fcntl.h>
#include <stropts.h>
#include <sys/sockio.h>
#endif

#ifdef __linux__
#include <sys/ioctl.h>
#include <bits/ioctls.h>
#include <sys/utsname.h>
#include <stdio.h>
#endif

#if defined(_AIX)
#include <sys/ioctl.h>
#include <netinet/in6_var.h>
#include <sys/ndd_var.h>
#include <sys/kinfo.h>
#endif

#ifdef __linux__
#define _PATH_PROCNET_IFINET6           "/proc/net/if_inet6"
#endif

#if defined(_ALLBSD_SOURCE)
#include <sys/param.h>
#include <sys/ioctl.h>
#include <sys/sockio.h>
#if defined(__APPLE__)
#include <net/ethernet.h>
#include <net/if_var.h>
#include <net/if_dl.h>
#include <netinet/in_var.h>
#include <ifaddrs.h>
#endif
#endif

#include "jvm.h"
#include "jni_util.h"
#include "net_util.h"

typedef struct _netaddr  {
    struct sockaddr *addr;
    struct sockaddr *brdcast;
    short mask;
    int family; /* to make searches simple */
    struct _netaddr *next;
} netaddr;

typedef struct _netif {
    char *name;
    int index;
    char virtual;
    netaddr *addr;
    struct _netif *childs;
    struct _netif *next;
} netif;

/************************************************************************
 * NetworkInterface
 */

#include "java_net_NetworkInterface.h"

/************************************************************************
 * NetworkInterface
 */
jclass ni_class;
jfieldID ni_nameID;
jfieldID ni_indexID;
jfieldID ni_descID;
jfieldID ni_addrsID;
jfieldID ni_bindsID;
jfieldID ni_virutalID;
jfieldID ni_childsID;
jfieldID ni_parentID;
jfieldID ni_defaultIndexID;
jmethodID ni_ctrID;

static jclass ni_ibcls;
static jmethodID ni_ibctrID;
static jfieldID ni_ibaddressID;
static jfieldID ni_ib4broadcastID;
static jfieldID ni_ib4maskID;

/** Private methods declarations **/
static jobject createNetworkInterface(JNIEnv *env, netif *ifs);
static int     getFlags0(JNIEnv *env, jstring  ifname);

static netif  *enumInterfaces(JNIEnv *env);
static netif  *enumIPv4Interfaces(JNIEnv *env, int sock, netif *ifs);

#ifdef AF_INET6
static netif  *enumIPv6Interfaces(JNIEnv *env, int sock, netif *ifs);
#endif

static netif  *addif(JNIEnv *env, int sock, const char * if_name, netif *ifs, struct sockaddr* ifr_addrP, int family, short prefix);
static void    freeif(netif *ifs);

static int     openSocket(JNIEnv *env, int proto);
static int     openSocketWithFallback(JNIEnv *env, const char *ifname);


static struct  sockaddr *getBroadcast(JNIEnv *env, int sock, const char *name, struct sockaddr *brdcast_store);
static short   getSubnet(JNIEnv *env, int sock, const char *ifname);
static int     getIndex(int sock, const char *ifname);

static int     getFlags(int sock, const char *ifname, int *flags);
static int     getMacAddress(JNIEnv *env, int sock,  const char* ifname, const struct in_addr* addr, unsigned char *buf);
static int     getMTU(JNIEnv *env, int sock, const char *ifname);



#ifdef __solaris__
static netif *enumIPvXInterfaces(JNIEnv *env, int sock, netif *ifs, int family);
static int    getMacFromDevice(JNIEnv *env, const char* ifname, unsigned char* retbuf);

#ifndef SIOCGLIFHWADDR
#define SIOCGLIFHWADDR  _IOWR('i', 192, struct lifreq)
#endif

#endif

/******************* Java entry points *****************************/

/*
 * Class:     java_net_NetworkInterface
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_NetworkInterface_init(JNIEnv *env, jclass cls) {
    ni_class = (*env)->FindClass(env,"java/net/NetworkInterface");
    CHECK_NULL(ni_class);
    ni_class = (*env)->NewGlobalRef(env, ni_class);
    CHECK_NULL(ni_class);
    ni_nameID = (*env)->GetFieldID(env, ni_class,"name", "Ljava/lang/String;");
    CHECK_NULL(ni_nameID);
    ni_indexID = (*env)->GetFieldID(env, ni_class, "index", "I");
    CHECK_NULL(ni_indexID);
    ni_addrsID = (*env)->GetFieldID(env, ni_class, "addrs", "[Ljava/net/InetAddress;");
    CHECK_NULL(ni_addrsID);
    ni_bindsID = (*env)->GetFieldID(env, ni_class, "bindings", "[Ljava/net/InterfaceAddress;");
    CHECK_NULL(ni_bindsID);
    ni_descID = (*env)->GetFieldID(env, ni_class, "displayName", "Ljava/lang/String;");
    CHECK_NULL(ni_descID);
    ni_virutalID = (*env)->GetFieldID(env, ni_class, "virtual", "Z");
    CHECK_NULL(ni_virutalID);
    ni_childsID = (*env)->GetFieldID(env, ni_class, "childs", "[Ljava/net/NetworkInterface;");
    CHECK_NULL(ni_childsID);
    ni_parentID = (*env)->GetFieldID(env, ni_class, "parent", "Ljava/net/NetworkInterface;");
    CHECK_NULL(ni_parentID);
    ni_ctrID = (*env)->GetMethodID(env, ni_class, "<init>", "()V");
    CHECK_NULL(ni_ctrID);
    ni_ibcls = (*env)->FindClass(env, "java/net/InterfaceAddress");
    CHECK_NULL(ni_ibcls);
    ni_ibcls = (*env)->NewGlobalRef(env, ni_ibcls);
    CHECK_NULL(ni_ibcls);
    ni_ibctrID = (*env)->GetMethodID(env, ni_ibcls, "<init>", "()V");
    CHECK_NULL(ni_ibctrID);
    ni_ibaddressID = (*env)->GetFieldID(env, ni_ibcls, "address", "Ljava/net/InetAddress;");
    CHECK_NULL(ni_ibaddressID);
    ni_ib4broadcastID = (*env)->GetFieldID(env, ni_ibcls, "broadcast", "Ljava/net/Inet4Address;");
    CHECK_NULL(ni_ib4broadcastID);
    ni_ib4maskID = (*env)->GetFieldID(env, ni_ibcls, "maskLength", "S");
    CHECK_NULL(ni_ib4maskID);
    ni_defaultIndexID = (*env)->GetStaticFieldID(env, ni_class, "defaultIndex", "I");
    CHECK_NULL(ni_defaultIndexID);

    initInetAddressIDs(env);
}


/*
 * Class:     java_net_NetworkInterface
 * Method:    getByName0
 * Signature: (Ljava/lang/String;)Ljava/net/NetworkInterface;
 */
JNIEXPORT jobject JNICALL Java_java_net_NetworkInterface_getByName0
    (JNIEnv *env, jclass cls, jstring name) {

    netif *ifs, *curr;
    jboolean isCopy;
    const char* name_utf;
    jobject obj = NULL;

    ifs = enumInterfaces(env);
    if (ifs == NULL) {
        return NULL;
    }

    name_utf = (*env)->GetStringUTFChars(env, name, &isCopy);
    if (name_utf == NULL) {
       if (!(*env)->ExceptionCheck(env))
           JNU_ThrowOutOfMemoryError(env, NULL);
       return NULL;
    }
    /*
     * Search the list of interface based on name
     */
    curr = ifs;
    while (curr != NULL) {
        if (strcmp(name_utf, curr->name) == 0) {
            break;
        }
        curr = curr->next;
    }

    /* if found create a NetworkInterface */
    if (curr != NULL) {;
        obj = createNetworkInterface(env, curr);
    }

    /* release the UTF string and interface list */
    (*env)->ReleaseStringUTFChars(env, name, name_utf);
    freeif(ifs);

    return obj;
}


/*
 * Class:     java_net_NetworkInterface
 * Method:    getByIndex0
 * Signature: (Ljava/lang/String;)Ljava/net/NetworkInterface;
 */
JNIEXPORT jobject JNICALL Java_java_net_NetworkInterface_getByIndex0
    (JNIEnv *env, jclass cls, jint index) {

    netif *ifs, *curr;
    jobject obj = NULL;

    if (index <= 0) {
        return NULL;
    }
    ifs = enumInterfaces(env);
    if (ifs == NULL) {
        return NULL;
    }

    /*
     * Search the list of interface based on index
     */
    curr = ifs;
    while (curr != NULL) {
        if (index == curr->index) {
            break;
        }
        curr = curr->next;
    }

    /* if found create a NetworkInterface */
    if (curr != NULL) {;
        obj = createNetworkInterface(env, curr);
    }

    freeif(ifs);
    return obj;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    getByInetAddress0
 * Signature: (Ljava/net/InetAddress;)Ljava/net/NetworkInterface;
 */
JNIEXPORT jobject JNICALL Java_java_net_NetworkInterface_getByInetAddress0
    (JNIEnv *env, jclass cls, jobject iaObj) {

    netif *ifs, *curr;

#ifdef AF_INET6
    int family = (getInetAddress_family(env, iaObj) == IPv4) ? AF_INET : AF_INET6;
#else
    int family =  AF_INET;
#endif

    jobject obj = NULL;
    jboolean match = JNI_FALSE;

    ifs = enumInterfaces(env);
    if (ifs == NULL) {
        return NULL;
    }

    curr = ifs;
    while (curr != NULL) {
        netaddr *addrP = curr->addr;

        /*
         * Iterate through each address on the interface
         */
        while (addrP != NULL) {

            if (family == addrP->family) {
                if (family == AF_INET) {
                    int address1 = htonl(((struct sockaddr_in*)addrP->addr)->sin_addr.s_addr);
                    int address2 = getInetAddress_addr(env, iaObj);

                    if (address1 == address2) {
                        match = JNI_TRUE;
                        break;
                    }
                }

#ifdef AF_INET6
                if (family == AF_INET6) {
                    jbyte *bytes = (jbyte *)&(((struct sockaddr_in6*)addrP->addr)->sin6_addr);
                    jbyte caddr[16];
                    int i;
                    getInet6Address_ipaddress(env, iaObj, (char *)caddr);
                    i = 0;
                    while (i < 16) {
                        if (caddr[i] != bytes[i]) {
                            break;
                        }
                        i++;
                    }
                    if (i >= 16) {
                        match = JNI_TRUE;
                        break;
                    }
                }
#endif

            }

            if (match) {
                break;
            }
            addrP = addrP->next;
        }

        if (match) {
            break;
        }
        curr = curr->next;
    }

    /* if found create a NetworkInterface */
    if (match) {;
        obj = createNetworkInterface(env, curr);
    }

    freeif(ifs);
    return obj;
}


/*
 * Class:     java_net_NetworkInterface
 * Method:    getAll
 * Signature: ()[Ljava/net/NetworkInterface;
 */
JNIEXPORT jobjectArray JNICALL Java_java_net_NetworkInterface_getAll
    (JNIEnv *env, jclass cls) {

    netif *ifs, *curr;
    jobjectArray netIFArr;
    jint arr_index, ifCount;

    ifs = enumInterfaces(env);
    if (ifs == NULL) {
        return NULL;
    }

    /* count the interface */
    ifCount = 0;
    curr = ifs;
    while (curr != NULL) {
        ifCount++;
        curr = curr->next;
    }

    /* allocate a NetworkInterface array */
    netIFArr = (*env)->NewObjectArray(env, ifCount, cls, NULL);
    if (netIFArr == NULL) {
        freeif(ifs);
        return NULL;
    }

    /*
     * Iterate through the interfaces, create a NetworkInterface instance
     * for each array element and populate the object.
     */
    curr = ifs;
    arr_index = 0;
    while (curr != NULL) {
        jobject netifObj;

        netifObj = createNetworkInterface(env, curr);
        if (netifObj == NULL) {
            freeif(ifs);
            return NULL;
        }

        /* put the NetworkInterface into the array */
        (*env)->SetObjectArrayElement(env, netIFArr, arr_index++, netifObj);

        curr = curr->next;
    }

    freeif(ifs);
    return netIFArr;
}


/*
 * Class:     java_net_NetworkInterface
 * Method:    isUp0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isUp0(JNIEnv *env, jclass cls, jstring name, jint index) {
    int ret = getFlags0(env, name);
    return ((ret & IFF_UP) && (ret & IFF_RUNNING)) ? JNI_TRUE :  JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    isP2P0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isP2P0(JNIEnv *env, jclass cls, jstring name, jint index) {
    int ret = getFlags0(env, name);
    return (ret & IFF_POINTOPOINT) ? JNI_TRUE :  JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    isLoopback0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isLoopback0(JNIEnv *env, jclass cls, jstring name, jint index) {
    int ret = getFlags0(env, name);
    return (ret & IFF_LOOPBACK) ? JNI_TRUE :  JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    supportsMulticast0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_supportsMulticast0(JNIEnv *env, jclass cls, jstring name, jint index) {
    int ret = getFlags0(env, name);
    return (ret & IFF_MULTICAST) ? JNI_TRUE :  JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    getMacAddr0
 * Signature: ([bLjava/lang/String;I)[b
 */
JNIEXPORT jbyteArray JNICALL Java_java_net_NetworkInterface_getMacAddr0(JNIEnv *env, jclass class, jbyteArray addrArray, jstring name, jint index) {
    jint addr;
    jbyte caddr[4];
    struct in_addr iaddr;
    jbyteArray ret = NULL;
    unsigned char mac[16];
    int len;
    int sock;
    jboolean isCopy;
    const char* name_utf;

    name_utf = (*env)->GetStringUTFChars(env, name, &isCopy);
    if (name_utf == NULL) {
       if (!(*env)->ExceptionCheck(env))
           JNU_ThrowOutOfMemoryError(env, NULL);
       return NULL;
    }
    if ((sock =openSocketWithFallback(env, name_utf)) < 0) {
       (*env)->ReleaseStringUTFChars(env, name, name_utf);
       return JNI_FALSE;
    }


    if (!IS_NULL(addrArray)) {
       (*env)->GetByteArrayRegion(env, addrArray, 0, 4, caddr);
       addr = ((caddr[0]<<24) & 0xff000000);
       addr |= ((caddr[1] <<16) & 0xff0000);
       addr |= ((caddr[2] <<8) & 0xff00);
       addr |= (caddr[3] & 0xff);
       iaddr.s_addr = htonl(addr);
       len = getMacAddress(env, sock, name_utf, &iaddr, mac);
    } else {
       len = getMacAddress(env, sock, name_utf,NULL, mac);
    }
    if (len > 0) {
       ret = (*env)->NewByteArray(env, len);
       if (IS_NULL(ret)) {
          /* we may have memory to free at the end of this */
          goto fexit;
       }
       (*env)->SetByteArrayRegion(env, ret, 0, len, (jbyte *) (mac));
    }
 fexit:
   /* release the UTF string and interface list */
   (*env)->ReleaseStringUTFChars(env, name, name_utf);

   close(sock);
   return ret;
}

/*
 * Class:       java_net_NetworkInterface
 * Method:      getMTU0
 * Signature:   ([bLjava/lang/String;I)I
 */

JNIEXPORT jint JNICALL Java_java_net_NetworkInterface_getMTU0(JNIEnv *env, jclass class, jstring name, jint index) {
    jboolean isCopy;
    int ret = -1;
    int sock;
    const char* name_utf = NULL;

    if (name != NULL) {
        name_utf = (*env)->GetStringUTFChars(env, name, &isCopy);
    } else {
        JNU_ThrowNullPointerException(env, "network interface name is NULL");
        return ret;
    }
    if (name_utf == NULL) {
       if (!(*env)->ExceptionCheck(env))
           JNU_ThrowOutOfMemoryError(env, NULL);
       return ret;
    }

    if ((sock =openSocketWithFallback(env, name_utf)) < 0) {
       (*env)->ReleaseStringUTFChars(env, name, name_utf);
       return JNI_FALSE;
    }

    ret = getMTU(env, sock, name_utf);

    (*env)->ReleaseStringUTFChars(env, name, name_utf);

    close(sock);
    return ret;
}

/*** Private methods definitions ****/

static int getFlags0(JNIEnv *env, jstring name) {
    jboolean isCopy;
    int ret, sock;
    const char* name_utf;
    int flags = 0;

    if (name != NULL) {
        name_utf = (*env)->GetStringUTFChars(env, name, &isCopy);
    } else {
        JNU_ThrowNullPointerException(env, "network interface name is NULL");
        return -1;
    }

    if (name_utf == NULL) {
       if (!(*env)->ExceptionCheck(env))
           JNU_ThrowOutOfMemoryError(env, NULL);
       return -1;
    }
    if ((sock = openSocketWithFallback(env, name_utf)) < 0) {
        (*env)->ReleaseStringUTFChars(env, name, name_utf);
        return -1;
    }

    ret = getFlags(sock, name_utf, &flags);

    close(sock);
    (*env)->ReleaseStringUTFChars(env, name, name_utf);

    if (ret < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL  SIOCGLIFFLAGS failed");
        return -1;
    }

    return flags;
}




/*
 * Create a NetworkInterface object, populate the name and index, and
 * populate the InetAddress array based on the IP addresses for this
 * interface.
 */
jobject createNetworkInterface(JNIEnv *env, netif *ifs) {
    jobject netifObj;
    jobject name;
    jobjectArray addrArr;
    jobjectArray bindArr;
    jobjectArray childArr;
    netaddr *addrs;
    jint addr_index, addr_count, bind_index;
    jint child_count, child_index;
    netaddr *addrP;
    netif *childP;
    jobject tmp;

    /*
     * Create a NetworkInterface object and populate it
     */
    netifObj = (*env)->NewObject(env, ni_class, ni_ctrID);
    CHECK_NULL_RETURN(netifObj, NULL);
    name = (*env)->NewStringUTF(env, ifs->name);
    CHECK_NULL_RETURN(name, NULL);
    (*env)->SetObjectField(env, netifObj, ni_nameID, name);
    (*env)->SetObjectField(env, netifObj, ni_descID, name);
    (*env)->SetIntField(env, netifObj, ni_indexID, ifs->index);
    (*env)->SetBooleanField(env, netifObj, ni_virutalID, ifs->virtual ? JNI_TRUE : JNI_FALSE);

    /*
     * Count the number of address on this interface
     */
    addr_count = 0;
    addrP = ifs->addr;
    while (addrP != NULL) {
        addr_count++;
        addrP = addrP->next;
    }

    /*
     * Create the array of InetAddresses
     */
    addrArr = (*env)->NewObjectArray(env, addr_count,  ia_class, NULL);
    if (addrArr == NULL) {
        return NULL;
    }

    bindArr = (*env)->NewObjectArray(env, addr_count, ni_ibcls, NULL);
    if (bindArr == NULL) {
       return NULL;
    }
    addrP = ifs->addr;
    addr_index = 0;
    bind_index = 0;
    while (addrP != NULL) {
        jobject iaObj = NULL;
        jobject ibObj = NULL;

        if (addrP->family == AF_INET) {
            iaObj = (*env)->NewObject(env, ia4_class, ia4_ctrID);
            if (iaObj) {
                 setInetAddress_addr(env, iaObj, htonl(((struct sockaddr_in*)addrP->addr)->sin_addr.s_addr));
            } else {
                return NULL;
            }
            ibObj = (*env)->NewObject(env, ni_ibcls, ni_ibctrID);
            if (ibObj) {
                 (*env)->SetObjectField(env, ibObj, ni_ibaddressID, iaObj);
                 if (addrP->brdcast) {
                    jobject ia2Obj = NULL;
                    ia2Obj = (*env)->NewObject(env, ia4_class, ia4_ctrID);
                    if (ia2Obj) {
                       setInetAddress_addr(env, ia2Obj, htonl(((struct sockaddr_in*)addrP->brdcast)->sin_addr.s_addr));
                       (*env)->SetObjectField(env, ibObj, ni_ib4broadcastID, ia2Obj);
                    } else {
                        return NULL;
                    }
                 }
                 (*env)->SetShortField(env, ibObj, ni_ib4maskID, addrP->mask);
                 (*env)->SetObjectArrayElement(env, bindArr, bind_index++, ibObj);
            } else {
                return NULL;
            }
        }

#ifdef AF_INET6
        if (addrP->family == AF_INET6) {
            int scope=0;
            iaObj = (*env)->NewObject(env, ia6_class, ia6_ctrID);
            if (iaObj) {
                jboolean ret = setInet6Address_ipaddress(env, iaObj, (char *)&(((struct sockaddr_in6*)addrP->addr)->sin6_addr));
                if (ret == JNI_FALSE) {
                    return NULL;
                }

                scope = ((struct sockaddr_in6*)addrP->addr)->sin6_scope_id;

                if (scope != 0) { /* zero is default value, no need to set */
                    setInet6Address_scopeid(env, iaObj, scope);
                    setInet6Address_scopeifname(env, iaObj, netifObj);
                }
            } else {
                return NULL;
            }
            ibObj = (*env)->NewObject(env, ni_ibcls, ni_ibctrID);
            if (ibObj) {
                (*env)->SetObjectField(env, ibObj, ni_ibaddressID, iaObj);
                (*env)->SetShortField(env, ibObj, ni_ib4maskID, addrP->mask);
                (*env)->SetObjectArrayElement(env, bindArr, bind_index++, ibObj);
            } else {
                return NULL;
            }
        }
#endif

        (*env)->SetObjectArrayElement(env, addrArr, addr_index++, iaObj);
        addrP = addrP->next;
    }

    /*
     * See if there is any virtual interface attached to this one.
     */
    child_count = 0;
    childP = ifs->childs;
    while (childP) {
        child_count++;
        childP = childP->next;
    }

    childArr = (*env)->NewObjectArray(env, child_count, ni_class, NULL);
    if (childArr == NULL) {
        return NULL;
    }

    /*
     * Create the NetworkInterface instances for the sub-interfaces as
     * well.
     */
    child_index = 0;
    childP = ifs->childs;
    while(childP) {
      tmp = createNetworkInterface(env, childP);
      if (tmp == NULL) {
         return NULL;
      }
      (*env)->SetObjectField(env, tmp, ni_parentID, netifObj);
      (*env)->SetObjectArrayElement(env, childArr, child_index++, tmp);
      childP = childP->next;
    }
    (*env)->SetObjectField(env, netifObj, ni_addrsID, addrArr);
    (*env)->SetObjectField(env, netifObj, ni_bindsID, bindArr);
    (*env)->SetObjectField(env, netifObj, ni_childsID, childArr);

    /* return the NetworkInterface */
    return netifObj;
}

/*
 * Enumerates all interfaces
 */
static netif *enumInterfaces(JNIEnv *env) {
    netif *ifs;
    int sock;

    /*
     * Enumerate IPv4 addresses
     */

    sock = openSocket(env, AF_INET);
    if (sock < 0 && (*env)->ExceptionOccurred(env)) {
        return NULL;
    }

    ifs = enumIPv4Interfaces(env, sock, NULL);
    close(sock);

    if (ifs == NULL && (*env)->ExceptionOccurred(env)) {
        return NULL;
    }

    /* return partial list if an exception occurs in the middle of process ???*/

    /*
     * If IPv6 is available then enumerate IPv6 addresses.
     */
#ifdef AF_INET6

        /* User can disable ipv6 explicitly by -Djava.net.preferIPv4Stack=true,
         * so we have to call ipv6_available()
         */
        if (ipv6_available()) {

           sock =  openSocket(env, AF_INET6);
           if (sock < 0 && (*env)->ExceptionOccurred(env)) {
               freeif(ifs);
               return NULL;
           }

           ifs = enumIPv6Interfaces(env, sock, ifs);
           close(sock);

           if ((*env)->ExceptionOccurred(env)) {
              freeif(ifs);
              return NULL;
           }

       }
#endif

    return ifs;
}

#define CHECKED_MALLOC3(_pointer,_type,_size) \
       do{ \
        _pointer = (_type)malloc( _size ); \
        if (_pointer == NULL) { \
            JNU_ThrowOutOfMemoryError(env, "Native heap allocation failed"); \
            return ifs; /* return untouched list */ \
        } \
       } while(0)


/*
 * Free an interface list (including any attached addresses)
 */
void freeif(netif *ifs) {
    netif *currif = ifs;
    netif *child = NULL;

    while (currif != NULL) {
        netaddr *addrP = currif->addr;
        while (addrP != NULL) {
            netaddr *next = addrP->next;
            free(addrP);
            addrP = next;
         }

            /*
            * Don't forget to free the sub-interfaces.
            */
          if (currif->childs != NULL) {
                freeif(currif->childs);
          }

          ifs = currif->next;
          free(currif);
          currif = ifs;
    }
}

netif *addif(JNIEnv *env, int sock, const char * if_name,
             netif *ifs, struct sockaddr* ifr_addrP, int family,
             short prefix)
{
    netif *currif = ifs, *parent;
    netaddr *addrP;

#ifdef LIFNAMSIZ
    int ifnam_size = LIFNAMSIZ;
    char name[LIFNAMSIZ], vname[LIFNAMSIZ];
#else
    int ifnam_size = IFNAMSIZ;
    char name[IFNAMSIZ], vname[IFNAMSIZ];
#endif

    char  *name_colonP;
    int mask;
    int isVirtual = 0;
    int addr_size;
    int flags = 0;

    /*
     * If the interface name is a logical interface then we
     * remove the unit number so that we have the physical
     * interface (eg: hme0:1 -> hme0). NetworkInterface
     * currently doesn't have any concept of physical vs.
     * logical interfaces.
     */
    strncpy(name, if_name, ifnam_size);
    name[ifnam_size - 1] = '\0';
    *vname = 0;

    /*
     * Create and populate the netaddr node. If allocation fails
     * return an un-updated list.
     */
    /*Allocate for addr and brdcast at once*/

#ifdef AF_INET6
    addr_size = (family == AF_INET) ? sizeof(struct sockaddr_in) : sizeof(struct sockaddr_in6);
#else
    addr_size = sizeof(struct sockaddr_in);
#endif

    CHECKED_MALLOC3(addrP, netaddr *, sizeof(netaddr)+2*addr_size);
    addrP->addr = (struct sockaddr *)( (char *) addrP+sizeof(netaddr) );
    memcpy(addrP->addr, ifr_addrP, addr_size);

    addrP->family = family;
    addrP->brdcast = NULL;
    addrP->mask = prefix;
    addrP->next = 0;
    if (family == AF_INET) {
       // Deal with broadcast addr & subnet mask
       struct sockaddr * brdcast_to = (struct sockaddr *) ((char *) addrP + sizeof(netaddr) + addr_size);
       addrP->brdcast = getBroadcast(env, sock, name,  brdcast_to );
       if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
           return ifs;
       }
       if ((mask = getSubnet(env, sock, name)) != -1) {
           addrP->mask = mask;
       } else if((*env)->ExceptionCheck(env)) {
           return ifs;
       }
     }

    /**
     * Deal with virtual interface with colon notation e.g. eth0:1
     */
    name_colonP = strchr(name, ':');
    if (name_colonP != NULL) {
      /**
       * This is a virtual interface. If we are able to access the parent
       * we need to create a new entry if it doesn't exist yet *and* update
       * the 'parent' interface with the new records.
       */
        *name_colonP = 0;
        if (getFlags(sock, name, &flags) < 0 || flags < 0) {
            // failed to access parent interface do not create parent.
            // We are a virtual interface with no parent.
            isVirtual = 1;
            *name_colonP = ':';
        }
        else{
           // Got access to parent, so create it if necessary.
           // Save original name to vname and truncate name by ':'
            memcpy(vname, name, sizeof(vname) );
            vname[name_colonP - name] = ':';
        }
    }

    /*
     * Check if this is a "new" interface. Use the interface
     * name for matching because index isn't supported on
     * Solaris 2.6 & 7.
     */
    while (currif != NULL) {
        if (strcmp(name, currif->name) == 0) {
            break;
        }
        currif = currif->next;
    }

    /*
     * If "new" then create an netif structure and
     * insert it onto the list.
     */
    if (currif == NULL) {
         CHECKED_MALLOC3(currif, netif *, sizeof(netif) + ifnam_size);
         currif->name = (char *) currif+sizeof(netif);
         strncpy(currif->name, name, ifnam_size);
         currif->name[ifnam_size - 1] = '\0';
         currif->index = getIndex(sock, name);
         currif->addr = NULL;
         currif->childs = NULL;
         currif->virtual = isVirtual;
         currif->next = ifs;
         ifs = currif;
    }

    /*
     * Finally insert the address on the interface
     */
    addrP->next = currif->addr;
    currif->addr = addrP;

    parent = currif;

    /**
     * Let's deal with the virtual interface now.
     */
    if (vname[0]) {
        netaddr *tmpaddr;

        currif = parent->childs;

        while (currif != NULL) {
            if (strcmp(vname, currif->name) == 0) {
                break;
            }
            currif = currif->next;
        }

        if (currif == NULL) {
            CHECKED_MALLOC3(currif, netif *, sizeof(netif) + ifnam_size);
            currif->name = (char *) currif + sizeof(netif);
            strncpy(currif->name, vname, ifnam_size);
            currif->name[ifnam_size - 1] = '\0';
            currif->index = getIndex(sock, vname);
            currif->addr = NULL;
           /* Need to duplicate the addr entry? */
            currif->virtual = 1;
            currif->childs = NULL;
            currif->next = parent->childs;
            parent->childs = currif;
        }

        CHECKED_MALLOC3(tmpaddr, netaddr *, sizeof(netaddr)+2*addr_size);
        memcpy(tmpaddr, addrP, sizeof(netaddr));
        if (addrP->addr != NULL) {
            tmpaddr->addr = (struct sockaddr *) ( (char*)tmpaddr + sizeof(netaddr) ) ;
            memcpy(tmpaddr->addr, addrP->addr, addr_size);
        }

        if (addrP->brdcast != NULL) {
            tmpaddr->brdcast = (struct sockaddr *) ((char *) tmpaddr + sizeof(netaddr)+addr_size);
            memcpy(tmpaddr->brdcast, addrP->brdcast, addr_size);
        }

        tmpaddr->next = currif->addr;
        currif->addr = tmpaddr;
    }

    return ifs;
}

/* Open socket for further ioct calls
 * proto is AF_INET/AF_INET6
 */
static int  openSocket(JNIEnv *env, int proto){
    int sock;

    if ((sock = socket(proto, SOCK_DGRAM, 0)) < 0) {
        /*
         * If EPROTONOSUPPORT is returned it means we don't have
         * support  for this proto so don't throw an exception.
         */
        if (errno != EPROTONOSUPPORT) {
            NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "Socket creation failed");
        }
        return -1;
    }

    return sock;
}


/** Linux, AIX **/
#if defined(__linux__) || defined(_AIX)
/* Open socket for further ioct calls, try v4 socket first and
 * if it falls return v6 socket
 */

#ifdef AF_INET6
// unused arg ifname and struct if2
static int openSocketWithFallback(JNIEnv *env, const char *ifname){
    int sock;
    struct ifreq if2;

     if ((sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
         if (errno == EPROTONOSUPPORT){
              if ( (sock = socket(AF_INET6, SOCK_DGRAM, 0)) < 0 ){
                 NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "IPV6 Socket creation failed");
                 return -1;
              }
         }
         else{ // errno is not NOSUPPORT
             NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "IPV4 Socket creation failed");
             return -1;
         }
   }

     /* Linux starting from 2.6.? kernel allows ioctl call with either IPv4 or IPv6 socket regardless of type
        of address of an interface */

       return sock;
}

#else
static int openSocketWithFallback(JNIEnv *env, const char *ifname){
    return openSocket(env,AF_INET);
}
#endif

static netif *enumIPv4Interfaces(JNIEnv *env, int sock, netif *ifs) {
    struct ifconf ifc;
    struct ifreq *ifreqP;
    char *buf = NULL;
    int numifs;
    unsigned i;
    int siocgifconfRequest = SIOCGIFCONF;


#if defined(__linux__)
    /* need to do a dummy SIOCGIFCONF to determine the buffer size.
     * SIOCGIFCOUNT doesn't work
     */
    ifc.ifc_buf = NULL;
    if (ioctl(sock, SIOCGIFCONF, (char *)&ifc) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "ioctl SIOCGIFCONF failed");
        return ifs;
    }
#elif defined(_AIX)
    ifc.ifc_buf = NULL;
    if (ioctl(sock, SIOCGSIZIFCONF, &(ifc.ifc_len)) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "ioctl SIOCGSIZIFCONF failed");
        return ifs;
    }
#endif /* __linux__ */

    CHECKED_MALLOC3(buf,char *, ifc.ifc_len);

    ifc.ifc_buf = buf;
#if defined(_AIX)
    siocgifconfRequest = CSIOCGIFCONF;
#endif
    if (ioctl(sock, siocgifconfRequest, (char *)&ifc) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "ioctl SIOCGIFCONF failed");
        (void) free(buf);
        return ifs;
    }

    /*
     * Iterate through each interface
     */
    ifreqP = ifc.ifc_req;
    for (i=0; i<ifc.ifc_len/sizeof (struct ifreq); i++, ifreqP++) {
#if defined(_AIX)
        if (ifreqP->ifr_addr.sa_family != AF_INET) continue;
#endif
        /*
         * Add to the list
         */
        ifs = addif(env, sock, ifreqP->ifr_name, ifs, (struct sockaddr *) & (ifreqP->ifr_addr), AF_INET, 0);

        /*
         * If an exception occurred then free the list
         */
        if ((*env)->ExceptionOccurred(env)) {
            free(buf);
            freeif(ifs);
            return NULL;
        }
    }

    /*
     * Free socket and buffer
     */
    free(buf);
    return ifs;
}


/*
 * Enumerates and returns all IPv6 interfaces on Linux
 */

#if defined(AF_INET6) && defined(__linux__)
static netif *enumIPv6Interfaces(JNIEnv *env, int sock, netif *ifs) {
    FILE *f;
    char addr6[40], devname[21];
    char addr6p[8][5];
    int plen, scope, dad_status, if_idx;
    uint8_t ipv6addr[16];

    if ((f = fopen(_PATH_PROCNET_IFINET6, "r")) != NULL) {
        while (fscanf(f, "%4s%4s%4s%4s%4s%4s%4s%4s %08x %02x %02x %02x %20s\n",
                         addr6p[0], addr6p[1], addr6p[2], addr6p[3], addr6p[4], addr6p[5], addr6p[6], addr6p[7],
                         &if_idx, &plen, &scope, &dad_status, devname) != EOF) {

            struct netif *ifs_ptr = NULL;
            struct netif *last_ptr = NULL;
            struct sockaddr_in6 addr;

            sprintf(addr6, "%s:%s:%s:%s:%s:%s:%s:%s",
                           addr6p[0], addr6p[1], addr6p[2], addr6p[3], addr6p[4], addr6p[5], addr6p[6], addr6p[7]);
            inet_pton(AF_INET6, addr6, ipv6addr);

            memset(&addr, 0, sizeof(struct sockaddr_in6));
            memcpy((void*)addr.sin6_addr.s6_addr, (const void*)ipv6addr, 16);

            addr.sin6_scope_id = if_idx;

            ifs = addif(env, sock, devname, ifs, (struct sockaddr *)&addr, AF_INET6, plen);


            /*
             * If an exception occurred then return the list as is.
             */
            if ((*env)->ExceptionOccurred(env)) {
                fclose(f);
                return ifs;
            }
       }
       fclose(f);
    }
    return ifs;
}
#endif


/*
 * Enumerates and returns all IPv6 interfaces on AIX
 */

#if defined(AF_INET6) && defined(_AIX)
static netif *enumIPv6Interfaces(JNIEnv *env, int sock, netif *ifs) {
    struct ifconf ifc;
    struct ifreq *ifreqP;
    char *buf;
    int numifs;
    unsigned i;
    unsigned bufsize;
    char *cp, *cplimit;

    /* use SIOCGSIZIFCONF to get size for  SIOCGIFCONF */

    ifc.ifc_buf = NULL;
    if (ioctl(sock, SIOCGSIZIFCONF, &(ifc.ifc_len)) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                        "ioctl SIOCGSIZIFCONF failed");
        return ifs;
    }
    bufsize = ifc.ifc_len;

    buf = (char *)malloc(bufsize);
    if (!buf) {
        JNU_ThrowOutOfMemoryError(env, "Network interface native buffer allocation failed");
        return ifs;
    }
    ifc.ifc_len = bufsize;
    ifc.ifc_buf = buf;
    if (ioctl(sock, SIOCGIFCONF, (char *)&ifc) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                       "ioctl CSIOCGIFCONF failed");
        free(buf);
        return ifs;
    }

    /*
     * Iterate through each interface
     */
    ifreqP = ifc.ifc_req;
    cp = (char *)ifc.ifc_req;
    cplimit = cp + ifc.ifc_len;

    for ( ; cp < cplimit; cp += (sizeof(ifreqP->ifr_name) + MAX((ifreqP->ifr_addr).sa_len, sizeof(ifreqP->ifr_addr)))) {
        ifreqP = (struct ifreq *)cp;
        struct ifreq if2;

        memset((char *)&if2, 0, sizeof(if2));
        strcpy(if2.ifr_name, ifreqP->ifr_name);

        /*
         * Skip interface that aren't UP
         */
        if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2) >= 0) {
            if (!(if2.ifr_flags & IFF_UP)) {
                continue;
            }
        }

        if (ifreqP->ifr_addr.sa_family != AF_INET6)
            continue;

        if (ioctl(sock, SIOCGIFSITE6, (char *)&if2) >= 0) {
            struct sockaddr_in6 *s6= (struct sockaddr_in6 *)&(ifreqP->ifr_addr);
            s6->sin6_scope_id = if2.ifr_site6;
        }

        /*
         * Add to the list
         */
        ifs = addif(env, sock, ifreqP->ifr_name, ifs,
                    (struct sockaddr *)&(ifreqP->ifr_addr),
                    AF_INET6, 0);

        /*
         * If an exception occurred then free the list
         */
        if ((*env)->ExceptionOccurred(env)) {
            free(buf);
            freeif(ifs);
            return NULL;
        }
    }

    /*
     * Free socket and buffer
     */
    free(buf);
    return ifs;
}
#endif


static int getIndex(int sock, const char *name){
     /*
      * Try to get the interface index
      */
#if defined(_AIX)
    return if_nametoindex(name);
#else
    struct ifreq if2;
    strcpy(if2.ifr_name, name);

    if (ioctl(sock, SIOCGIFINDEX, (char *)&if2) < 0) {
        return -1;
    }

    return if2.ifr_ifindex;
#endif
}

/**
 * Returns the IPv4 broadcast address of a named interface, if it exists.
 * Returns 0 if it doesn't have one.
 */
static struct sockaddr *getBroadcast(JNIEnv *env, int sock, const char *ifname, struct sockaddr *brdcast_store) {
  struct sockaddr *ret = NULL;
  struct ifreq if2;

  memset((char *) &if2, 0, sizeof(if2));
  strcpy(if2.ifr_name, ifname);

  /* Let's make sure the interface does have a broadcast address */
  if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2)  < 0) {
      NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL  SIOCGIFFLAGS failed");
      return ret;
  }

  if (if2.ifr_flags & IFF_BROADCAST) {
      /* It does, let's retrieve it*/
      if (ioctl(sock, SIOCGIFBRDADDR, (char *)&if2) < 0) {
          NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFBRDADDR failed");
          return ret;
      }

      ret = brdcast_store;
      memcpy(ret, &if2.ifr_broadaddr, sizeof(struct sockaddr));
  }

  return ret;
}

/**
 * Returns the IPv4 subnet prefix length (aka subnet mask) for the named
 * interface, if it has one, otherwise return -1.
 */
static short getSubnet(JNIEnv *env, int sock, const char *ifname) {
    unsigned int mask;
    short ret;
    struct ifreq if2;

    memset((char *) &if2, 0, sizeof(if2));
    strcpy(if2.ifr_name, ifname);

    if (ioctl(sock, SIOCGIFNETMASK, (char *)&if2) < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFNETMASK failed");
        return -1;
    }

    mask = ntohl(((struct sockaddr_in*)&(if2.ifr_addr))->sin_addr.s_addr);
    ret = 0;
    while (mask) {
       mask <<= 1;
       ret++;
    }

    return ret;
}

/**
 * Get the Hardware address (usually MAC address) for the named interface.
 * return puts the data in buf, and returns the length, in byte, of the
 * MAC address. Returns -1 if there is no hardware address on that interface.
 */
static int getMacAddress(JNIEnv *env, int sock, const char* ifname, const struct in_addr* addr, unsigned char *buf) {
#if defined (_AIX)
    int size;
    struct kinfo_ndd *nddp;
    void *end;

    size = getkerninfo(KINFO_NDD, 0, 0, 0);
    if (size == 0) {
        return -1;
    }

    if (size < 0) {
        perror("getkerninfo 1");
        return -1;
    }

    nddp = (struct kinfo_ndd *)malloc(size);

    if (!nddp) {
        JNU_ThrowOutOfMemoryError(env, "Network interface getMacAddress native buffer allocation failed");
        return -1;
    }

    if (getkerninfo(KINFO_NDD, nddp, &size, 0) < 0) {
        perror("getkerninfo 2");
        return -1;
    }

    end = (void *)nddp + size;
    while ((void *)nddp < end) {
        if (!strcmp(nddp->ndd_alias, ifname) ||
                !strcmp(nddp->ndd_name, ifname)) {
            bcopy(nddp->ndd_addr, buf, 6);
            return 6;
        } else {
            nddp++;
        }
    }

    return -1;

#elif defined(__linux__)
    static struct ifreq ifr;
    int i;

    strcpy(ifr.ifr_name, ifname);
    if (ioctl(sock, SIOCGIFHWADDR, &ifr) < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFHWADDR failed");
        return -1;
    }

    memcpy(buf, &ifr.ifr_hwaddr.sa_data, IFHWADDRLEN);

   /*
    * All bytes to 0 means no hardware address.
    */

    for (i = 0; i < IFHWADDRLEN; i++) {
        if (buf[i] != 0)
            return IFHWADDRLEN;
    }

    return -1;
#endif
}

static int getMTU(JNIEnv *env, int sock,  const char *ifname) {
    struct ifreq if2;
    memset((char *) &if2, 0, sizeof(if2));

    if (ifname != NULL) {
        strcpy(if2.ifr_name, ifname);
    } else {
        JNU_ThrowNullPointerException(env, "network interface name is NULL");
        return -1;
    }

    if (ioctl(sock, SIOCGIFMTU, (char *)&if2) < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFMTU failed");
        return -1;
    }

    return  if2.ifr_mtu;
}

static int getFlags(int sock, const char *ifname, int *flags) {
  struct ifreq if2;

  memset((char *) &if2, 0, sizeof(if2));
  strcpy(if2.ifr_name, ifname);

  if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2) < 0){
      return -1;
  }

  if (sizeof(if2.ifr_flags) == sizeof(short)) {
      *flags = (if2.ifr_flags & 0xffff);
  } else {
      *flags = if2.ifr_flags;
  }
  return 0;
}

#endif

/** Solaris **/
#ifdef __solaris__
/* Open socket for further ioct calls, try v4 socket first and
 * if it falls return v6 socket
 */

#ifdef AF_INET6
static int openSocketWithFallback(JNIEnv *env, const char *ifname){
    int sock, alreadyV6 = 0;
    struct lifreq if2;

     if ((sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
         if (errno == EPROTONOSUPPORT){
              if ( (sock = socket(AF_INET6, SOCK_DGRAM, 0)) < 0 ){
                 NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "IPV6 Socket creation failed");
                 return -1;
              }

              alreadyV6=1;
         }
         else{ // errno is not NOSUPPORT
             NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "IPV4 Socket creation failed");
             return -1;
         }
   }

     /**
      * Solaris requires that we have an IPv6 socket to query an
      * interface without an IPv4 address - check it here.
      * POSIX 1 require the kernel to return ENOTTY if the call is
      * inappropriate for a device e.g. the NETMASK for a device having IPv6
      * only address but not all devices follow the standard so
      * fall back on any error. It's not an ecologically friendly gesture
      * but more reliable.
      */

    if (! alreadyV6 ){
        memset((char *) &if2, 0, sizeof(if2));
        strcpy(if2.lifr_name, ifname);
        if (ioctl(sock, SIOCGLIFNETMASK, (char *)&if2) < 0) {
                close(sock);
                if ( (sock = socket(AF_INET6, SOCK_DGRAM, 0)) < 0 ){
                      NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "IPV6 Socket creation failed");
                      return -1;
                }
        }
    }

    return sock;
}

#else
static int openSocketWithFallback(JNIEnv *env, const char *ifname){
    return openSocket(env,AF_INET);
}
#endif

/*
 * Enumerates and returns all IPv4 interfaces
 * (linux verision)
 */

static netif *enumIPv4Interfaces(JNIEnv *env, int sock, netif *ifs) {
     return enumIPvXInterfaces(env,sock, ifs, AF_INET);
}

#ifdef AF_INET6
static netif *enumIPv6Interfaces(JNIEnv *env, int sock, netif *ifs) {
    return enumIPvXInterfaces(env,sock, ifs, AF_INET6);
}
#endif

/*
   Enumerates and returns all interfaces on Solaris
   use the same code for IPv4 and IPv6
 */
static netif *enumIPvXInterfaces(JNIEnv *env, int sock, netif *ifs, int family) {
    struct lifconf ifc;
    struct lifreq *ifr;
    int n;
    char *buf;
    struct lifnum numifs;
    unsigned bufsize;

    /*
     * Get the interface count
     */
    numifs.lifn_family = family;
    numifs.lifn_flags = 0;
    if (ioctl(sock, SIOCGLIFNUM, (char *)&numifs) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "ioctl SIOCGLIFNUM failed");
        return ifs;
    }

    /*
     *  Enumerate the interface configurations
     */
    bufsize = numifs.lifn_count * sizeof (struct lifreq);
    CHECKED_MALLOC3(buf, char *, bufsize);

    ifc.lifc_family = family;
    ifc.lifc_flags = 0;
    ifc.lifc_len = bufsize;
    ifc.lifc_buf = buf;
    if (ioctl(sock, SIOCGLIFCONF, (char *)&ifc) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "ioctl SIOCGLIFCONF failed");
        free(buf);
        return ifs;
    }

    /*
     * Iterate through each interface
     */
    ifr = ifc.lifc_req;
    for (n=0; n<numifs.lifn_count; n++, ifr++) {
        int index = -1;
        struct lifreq if2;

        /*
        * Ignore either IPv4 or IPv6 addresses
        */
        if (ifr->lifr_addr.ss_family != family) {
            continue;
        }

#ifdef AF_INET6
        if (ifr->lifr_addr.ss_family == AF_INET6) {
            struct sockaddr_in6 *s6= (struct sockaddr_in6 *)&(ifr->lifr_addr);
            s6->sin6_scope_id = getIndex(sock, ifr->lifr_name);
        }
#endif

        /* add to the list */
        ifs = addif(env, sock,ifr->lifr_name, ifs, (struct sockaddr *)&(ifr->lifr_addr),family, (short) ifr->lifr_addrlen);

        /*
        * If an exception occurred we return immediately
        */
        if ((*env)->ExceptionOccurred(env)) {
            free(buf);
            return ifs;
        }

   }

    free(buf);
    return ifs;
}

static int getIndex(int sock, const char *name){
   /*
    * Try to get the interface index
    * (Not supported on Solaris 2.6 or 7)
    */
    struct lifreq if2;
    strcpy(if2.lifr_name, name);

    if (ioctl(sock, SIOCGLIFINDEX, (char *)&if2) < 0) {
        return -1;
    }

    return if2.lifr_index;
}

/**
 * Returns the IPv4 broadcast address of a named interface, if it exists.
 * Returns 0 if it doesn't have one.
 */
static struct sockaddr *getBroadcast(JNIEnv *env, int sock, const char *ifname, struct sockaddr *brdcast_store) {
    struct sockaddr *ret = NULL;
    struct lifreq if2;

    memset((char *) &if2, 0, sizeof(if2));
    strcpy(if2.lifr_name, ifname);

    /* Let's make sure the interface does have a broadcast address */
    if (ioctl(sock, SIOCGLIFFLAGS, (char *)&if2)  < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL  SIOCGLIFFLAGS failed");
        return ret;
    }

    if (if2.lifr_flags & IFF_BROADCAST) {
        /* It does, let's retrieve it*/
        if (ioctl(sock, SIOCGLIFBRDADDR, (char *)&if2) < 0) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGLIFBRDADDR failed");
            return ret;
        }

        ret = brdcast_store;
        memcpy(ret, &if2.lifr_broadaddr, sizeof(struct sockaddr));
    }

    return ret;
}

/**
 * Returns the IPv4 subnet prefix length (aka subnet mask) for the named
 * interface, if it has one, otherwise return -1.
 */
static short getSubnet(JNIEnv *env, int sock, const char *ifname) {
    unsigned int mask;
    short ret;
    struct lifreq if2;

    memset((char *) &if2, 0, sizeof(if2));
    strcpy(if2.lifr_name, ifname);

    if (ioctl(sock, SIOCGLIFNETMASK, (char *)&if2) < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGLIFNETMASK failed");
        return -1;
    }

    mask = ntohl(((struct sockaddr_in*)&(if2.lifr_addr))->sin_addr.s_addr);
    ret = 0;

    while (mask) {
       mask <<= 1;
       ret++;
    }

    return ret;
}



#define DEV_PREFIX  "/dev/"

/**
 * Solaris specific DLPI code to get hardware address from a device.
 * Unfortunately, at least up to Solaris X, you have to have special
 * privileges (i.e. be root).
 */
static int getMacFromDevice(JNIEnv *env, const char* ifname, unsigned char* retbuf) {
    char style1dev[MAXPATHLEN];
    int fd;
    dl_phys_addr_req_t dlpareq;
    dl_phys_addr_ack_t *dlpaack;
    struct strbuf msg;
    char buf[128];
    int flags = 0;

   /**
    * Device is in /dev
    * e.g.: /dev/bge0
    */
    strcpy(style1dev, DEV_PREFIX);
    strcat(style1dev, ifname);
    if ((fd = open(style1dev, O_RDWR)) < 0) {
        /*
         * Can't open it. We probably are missing the privilege.
         * We'll have to try something else
         */
         return 0;
    }

    dlpareq.dl_primitive = DL_PHYS_ADDR_REQ;
    dlpareq.dl_addr_type = DL_CURR_PHYS_ADDR;

    msg.buf = (char *)&dlpareq;
    msg.len = DL_PHYS_ADDR_REQ_SIZE;

    if (putmsg(fd, &msg, NULL, 0) < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "putmsg failed");
        return -1;
    }

    dlpaack = (dl_phys_addr_ack_t *)buf;

    msg.buf = (char *)buf;
    msg.len = 0;
    msg.maxlen = sizeof (buf);
    if (getmsg(fd, &msg, NULL, &flags) < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "getmsg failed");
        return -1;
    }

    if (msg.len < DL_PHYS_ADDR_ACK_SIZE || dlpaack->dl_primitive != DL_PHYS_ADDR_ACK) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Couldn't obtain phys addr\n");
        return -1;
    }

    memcpy(retbuf, &buf[dlpaack->dl_addr_offset], dlpaack->dl_addr_length);
    return dlpaack->dl_addr_length;
}

/**
 * Get the Hardware address (usually MAC address) for the named interface.
 * return puts the data in buf, and returns the length, in byte, of the
 * MAC address. Returns -1 if there is no hardware address on that interface.
 */
static int getMacAddress(JNIEnv *env, int sock, const char *ifname,  const struct in_addr* addr, unsigned char *buf) {
    struct arpreq arpreq;
    struct sockaddr_in* sin;
    struct sockaddr_in ipAddr;
    int len, i;
    struct lifreq lif;

    /* First, try the new (S11) SIOCGLIFHWADDR ioctl(). If that fails
     * try the old way.
     */
    memset(&lif, 0, sizeof(lif));
    strlcpy(lif.lifr_name, ifname, sizeof(lif.lifr_name));

    if (ioctl(sock, SIOCGLIFHWADDR, &lif) != -1) {
        struct sockaddr_dl *sp;
        sp = (struct sockaddr_dl *)&lif.lifr_addr;
        memcpy(buf, &sp->sdl_data[0], sp->sdl_alen);
        return sp->sdl_alen;
    }

   /**
    * On Solaris we have to use DLPI, but it will only work if we have
    * privileged access (i.e. root). If that fails, we try a lookup
    * in the ARP table, which requires an IPv4 address.
    */
    if ((len = getMacFromDevice(env, ifname, buf))  == 0) {
        /*DLPI failed - trying to do arp lookup*/

        if (addr == NULL) {
            /**
             * No IPv4 address for that interface, so can't do an ARP lookup.
             */
             return -1;
         }

         len = 6; //???

         sin = (struct sockaddr_in *) &arpreq.arp_pa;
         memset((char *) &arpreq, 0, sizeof(struct arpreq));
         ipAddr.sin_port = 0;
         ipAddr.sin_family = AF_INET;
         memcpy(&ipAddr.sin_addr, addr, sizeof(struct in_addr));
         memcpy(&arpreq.arp_pa, &ipAddr, sizeof(struct sockaddr_in));
         arpreq.arp_flags= ATF_PUBL;

         if (ioctl(sock, SIOCGARP, &arpreq) < 0) {
             return -1;
         }

         memcpy(buf, &arpreq.arp_ha.sa_data[0], len );
    }

    /*
     * All bytes to 0 means no hardware address.
     */

    for (i = 0; i < len; i++) {
      if (buf[i] != 0)
         return len;
    }

    return -1;
}

static int getMTU(JNIEnv *env, int sock,  const char *ifname) {
    struct lifreq if2;

    memset((char *) &if2, 0, sizeof(if2));
    strcpy(if2.lifr_name, ifname);

    if (ioctl(sock, SIOCGLIFMTU, (char *)&if2) < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGLIFMTU failed");
        return -1;
    }

    return  if2.lifr_mtu;
}


static int getFlags(int sock, const char *ifname, int *flags) {
     struct   lifreq lifr;
     memset((caddr_t)&lifr, 0, sizeof(lifr));
     strcpy((caddr_t)&(lifr.lifr_name), ifname);

     if (ioctl(sock, SIOCGLIFFLAGS, (char *)&lifr) < 0) {
         return -1;
     }

     *flags = lifr.lifr_flags;
     return 0;
}


#endif


/** BSD **/
#ifdef _ALLBSD_SOURCE
/* Open socket for further ioct calls, try v4 socket first and
 * if it falls return v6 socket
 */

#ifdef AF_INET6
static int openSocketWithFallback(JNIEnv *env, const char *ifname){
    int sock;
    struct ifreq if2;

     if ((sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
         if (errno == EPROTONOSUPPORT){
              if ( (sock = socket(AF_INET6, SOCK_DGRAM, 0)) < 0 ){
                 NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "IPV6 Socket creation failed");
                 return -1;
              }
         }
         else{ // errno is not NOSUPPORT
             NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "IPV4 Socket creation failed");
             return -1;
         }
   }

   return sock;
}

#else
static int openSocketWithFallback(JNIEnv *env, const char *ifname){
    return openSocket(env,AF_INET);
}
#endif

/*
 * Enumerates and returns all IPv4 interfaces
 */
static netif *enumIPv4Interfaces(JNIEnv *env, int sock, netif *ifs) {
    struct ifaddrs *ifa, *origifa;

    if (getifaddrs(&origifa) != 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                         "getifaddrs() function failed");
        return ifs;
    }

    for (ifa = origifa; ifa != NULL; ifa = ifa->ifa_next) {

        /*
         * Skip non-AF_INET entries.
         */
        if (ifa->ifa_addr == NULL || ifa->ifa_addr->sa_family != AF_INET)
            continue;

        /*
         * Add to the list.
         */
        ifs = addif(env, sock, ifa->ifa_name, ifs, ifa->ifa_addr, AF_INET, 0);

        /*
         * If an exception occurred then free the list.
         */
        if ((*env)->ExceptionOccurred(env)) {
            freeifaddrs(origifa);
            freeif(ifs);
            return NULL;
        }
    }

    /*
     * Free socket and buffer
     */
    freeifaddrs(origifa);
    return ifs;
}


/*
 * Enumerates and returns all IPv6 interfaces on Linux
 */

#ifdef AF_INET6
/*
 * Determines the prefix on BSD for IPv6 interfaces.
 */
static
int prefix(void *val, int size) {
    u_char *name = (u_char *)val;
    int byte, bit, plen = 0;

    for (byte = 0; byte < size; byte++, plen += 8)
        if (name[byte] != 0xff)
            break;
    if (byte == size)
        return (plen);
    for (bit = 7; bit != 0; bit--, plen++)
        if (!(name[byte] & (1 << bit)))
            break;
    for (; bit != 0; bit--)
        if (name[byte] & (1 << bit))
            return (0);
    byte++;
    for (; byte < size; byte++)
        if (name[byte])
            return (0);
    return (plen);
}

/*
 * Enumerates and returns all IPv6 interfaces on BSD
 */
static netif *enumIPv6Interfaces(JNIEnv *env, int sock, netif *ifs) {
    struct ifaddrs *ifa, *origifa;
    struct sockaddr_in6 *sin6;
    struct in6_ifreq ifr6;

    if (getifaddrs(&origifa) != 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                         "getifaddrs() function failed");
        return ifs;
    }

    for (ifa = origifa; ifa != NULL; ifa = ifa->ifa_next) {

        /*
         * Skip non-AF_INET6 entries.
         */
        if (ifa->ifa_addr == NULL || ifa->ifa_addr->sa_family != AF_INET6)
            continue;

        memset(&ifr6, 0, sizeof(ifr6));
        strlcpy(ifr6.ifr_name, ifa->ifa_name, sizeof(ifr6.ifr_name));
        memcpy(&ifr6.ifr_addr, ifa->ifa_addr, MIN(sizeof(ifr6.ifr_addr), ifa->ifa_addr->sa_len));

        if (ioctl(sock, SIOCGIFNETMASK_IN6, (caddr_t)&ifr6) < 0) {
            NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                             "ioctl SIOCGIFNETMASK_IN6 failed");
            freeifaddrs(origifa);
            freeif(ifs);
            return NULL;
        }

        /* Add to the list.  */
        sin6 = (struct sockaddr_in6 *)&ifr6.ifr_addr;
        ifs = addif(env, sock, ifa->ifa_name, ifs, ifa->ifa_addr, AF_INET6,
                    prefix(&sin6->sin6_addr, sizeof(struct in6_addr)));

        /* If an exception occurred then free the list.  */
        if ((*env)->ExceptionOccurred(env)) {
            freeifaddrs(origifa);
            freeif(ifs);
            return NULL;
        }
    }

    /*
     * Free socket and ifaddrs buffer
     */
    freeifaddrs(origifa);
    return ifs;
}
#endif

static int getIndex(int sock, const char *name){
#ifdef __FreeBSD__
     /*
      * Try to get the interface index
      * (Not supported on Solaris 2.6 or 7)
      */
    struct ifreq if2;
    strcpy(if2.ifr_name, name);

    if (ioctl(sock, SIOCGIFINDEX, (char *)&if2) < 0) {
        return -1;
    }

    return if2.ifr_index;
#else
    /*
     * Try to get the interface index using BSD specific if_nametoindex
     */
    int index = if_nametoindex(name);
    return (index == 0) ? -1 : index;
#endif
}

/**
 * Returns the IPv4 broadcast address of a named interface, if it exists.
 * Returns 0 if it doesn't have one.
 */
static struct sockaddr *getBroadcast(JNIEnv *env, int sock, const char *ifname, struct sockaddr *brdcast_store) {
  struct sockaddr *ret = NULL;
  struct ifreq if2;

  memset((char *) &if2, 0, sizeof(if2));
  strcpy(if2.ifr_name, ifname);

  /* Let's make sure the interface does have a broadcast address */
  if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2) < 0) {
      NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFFLAGS failed");
      return ret;
  }

  if (if2.ifr_flags & IFF_BROADCAST) {
      /* It does, let's retrieve it*/
      if (ioctl(sock, SIOCGIFBRDADDR, (char *)&if2) < 0) {
          NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFBRDADDR failed");
          return ret;
      }

      ret = brdcast_store;
      memcpy(ret, &if2.ifr_broadaddr, sizeof(struct sockaddr));
  }

  return ret;
}

/**
 * Returns the IPv4 subnet prefix length (aka subnet mask) for the named
 * interface, if it has one, otherwise return -1.
 */
static short getSubnet(JNIEnv *env, int sock, const char *ifname) {
    unsigned int mask;
    short ret;
    struct ifreq if2;

    memset((char *) &if2, 0, sizeof(if2));
    strcpy(if2.ifr_name, ifname);

    if (ioctl(sock, SIOCGIFNETMASK, (char *)&if2) < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFNETMASK failed");
        return -1;
    }

    mask = ntohl(((struct sockaddr_in*)&(if2.ifr_addr))->sin_addr.s_addr);
    ret = 0;
    while (mask) {
       mask <<= 1;
       ret++;
    }

    return ret;
}

/**
 * Get the Hardware address (usually MAC address) for the named interface.
 * return puts the data in buf, and returns the length, in byte, of the
 * MAC address. Returns -1 if there is no hardware address on that interface.
 */
static int getMacAddress(JNIEnv *env, int sock, const char* ifname, const struct in_addr* addr, unsigned char *buf) {
    struct ifaddrs *ifa0, *ifa;
    struct sockaddr *saddr;
    int i;

    /* Grab the interface list */
    if (!getifaddrs(&ifa0)) {
        /* Cycle through the interfaces */
        for (i = 0, ifa = ifa0; ifa != NULL; ifa = ifa->ifa_next, i++) {
            saddr = ifa->ifa_addr;
            /* Link layer contains the MAC address */
            if (saddr->sa_family == AF_LINK && !strcmp(ifname, ifa->ifa_name)) {
                struct sockaddr_dl *sadl = (struct sockaddr_dl *) saddr;
                /* Check the address is the correct length */
                if (sadl->sdl_alen == ETHER_ADDR_LEN) {
                    memcpy(buf, (sadl->sdl_data + sadl->sdl_nlen), ETHER_ADDR_LEN);
                    freeifaddrs(ifa0);
                    return ETHER_ADDR_LEN;
                }
            }
        }
        freeifaddrs(ifa0);
    }

    return -1;
}

static int getMTU(JNIEnv *env, int sock,  const char *ifname) {
    struct ifreq if2;

    memset((char *) &if2, 0, sizeof(if2));
    strcpy(if2.ifr_name, ifname);

    if (ioctl(sock, SIOCGIFMTU, (char *)&if2) < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFMTU failed");
        return -1;
    }

    return  if2.ifr_mtu;
}

static int getFlags(int sock, const char *ifname, int *flags) {
  struct ifreq if2;
  int ret = -1;

  memset((char *) &if2, 0, sizeof(if2));
  strcpy(if2.ifr_name, ifname);

  if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2) < 0){
      return -1;
  }

  if (sizeof(if2.ifr_flags) == sizeof(short)) {
    *flags = (if2.ifr_flags & 0xffff);
  } else {
    *flags = if2.ifr_flags;
  }
  return 0;
}

#endif
