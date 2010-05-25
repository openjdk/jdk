/*
 * Copyright (c) 2000, 2009, Oracle and/or its affiliates. All rights reserved.
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
#endif
#ifdef __linux__
#include <sys/ioctl.h>
#include <bits/ioctls.h>
#include <linux/sockios.h>
#include <sys/utsname.h>
#include <stdio.h>
#else
#include <sys/sockio.h>
#endif

#ifdef __linux__
#define ifr_index ifr_ifindex
#define _PATH_PROCNET_IFINET6           "/proc/net/if_inet6"
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
jmethodID ni_ctrID;

static jclass ni_iacls;
static jclass ni_ia4cls;
static jclass ni_ia6cls;
static jclass ni_ibcls;
static jmethodID ni_ia4ctrID;
static jmethodID ni_ia6ctrID;
static jmethodID ni_ibctrID;
static jfieldID ni_iaaddressID;
static jfieldID ni_iafamilyID;
static jfieldID ni_ia6ipaddressID;
static jfieldID ni_ibaddressID;
static jfieldID ni_ib4broadcastID;
static jfieldID ni_ib4maskID;

static jobject createNetworkInterface(JNIEnv *env, netif *ifs);

static netif *enumInterfaces(JNIEnv *env);
static netif *enumIPv4Interfaces(JNIEnv *env, netif *ifs);
#ifdef AF_INET6
static netif *enumIPv6Interfaces(JNIEnv *env, netif *ifs);
#endif

static netif *addif(JNIEnv *env, netif *ifs, char *if_name, int index,
                    int family, struct sockaddr *new_addrP, int new_addrlen,
                    short prefix);
static void freeif(netif *ifs);
static struct sockaddr *getBroadcast(JNIEnv *env, const char *ifname);
static short getSubnet(JNIEnv *env, const char *ifname);

/*
 * Class:     java_net_NetworkInterface
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_NetworkInterface_init(JNIEnv *env, jclass cls) {
    ni_class = (*env)->FindClass(env,"java/net/NetworkInterface");
    ni_class = (*env)->NewGlobalRef(env, ni_class);
    ni_nameID = (*env)->GetFieldID(env, ni_class,"name", "Ljava/lang/String;");
    ni_indexID = (*env)->GetFieldID(env, ni_class, "index", "I");
    ni_addrsID = (*env)->GetFieldID(env, ni_class, "addrs", "[Ljava/net/InetAddress;");
    ni_bindsID = (*env)->GetFieldID(env, ni_class, "bindings", "[Ljava/net/InterfaceAddress;");
    ni_descID = (*env)->GetFieldID(env, ni_class, "displayName", "Ljava/lang/String;");
    ni_virutalID = (*env)->GetFieldID(env, ni_class, "virtual", "Z");
    ni_childsID = (*env)->GetFieldID(env, ni_class, "childs", "[Ljava/net/NetworkInterface;");
    ni_parentID = (*env)->GetFieldID(env, ni_class, "parent", "Ljava/net/NetworkInterface;");
    ni_ctrID = (*env)->GetMethodID(env, ni_class, "<init>", "()V");

    ni_iacls = (*env)->FindClass(env, "java/net/InetAddress");
    ni_iacls = (*env)->NewGlobalRef(env, ni_iacls);
    ni_ia4cls = (*env)->FindClass(env, "java/net/Inet4Address");
    ni_ia4cls = (*env)->NewGlobalRef(env, ni_ia4cls);
    ni_ia6cls = (*env)->FindClass(env, "java/net/Inet6Address");
    ni_ia6cls = (*env)->NewGlobalRef(env, ni_ia6cls);
    ni_ibcls = (*env)->FindClass(env, "java/net/InterfaceAddress");
    ni_ibcls = (*env)->NewGlobalRef(env, ni_ibcls);
    ni_ia4ctrID = (*env)->GetMethodID(env, ni_ia4cls, "<init>", "()V");
    ni_ia6ctrID = (*env)->GetMethodID(env, ni_ia6cls, "<init>", "()V");
    ni_ibctrID = (*env)->GetMethodID(env, ni_ibcls, "<init>", "()V");
    ni_iaaddressID = (*env)->GetFieldID(env, ni_iacls, "address", "I");
    ni_iafamilyID = (*env)->GetFieldID(env, ni_iacls, "family", "I");
    ni_ia6ipaddressID = (*env)->GetFieldID(env, ni_ia6cls, "ipaddress", "[B");
    ni_ibaddressID = (*env)->GetFieldID(env, ni_ibcls, "address", "Ljava/net/InetAddress;");
    ni_ib4broadcastID = (*env)->GetFieldID(env, ni_ibcls, "broadcast", "Ljava/net/Inet4Address;");
    ni_ib4maskID = (*env)->GetFieldID(env, ni_ibcls, "maskLength", "S");
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
    const char* name_utf = (*env)->GetStringUTFChars(env, name, &isCopy);
    jobject obj = NULL;

    ifs = enumInterfaces(env);
    if (ifs == NULL) {
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
    int family = (*env)->GetIntField(env, iaObj, ni_iafamilyID) == IPv4?
        AF_INET : AF_INET6;
#else
    int family = AF_INET;
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
                    int address2 = (*env)->GetIntField(env, iaObj, ni_iaaddressID);

                    if (address1 == address2) {
                        match = JNI_TRUE;
                        break;
                    }
                }

#ifdef AF_INET6
                if (family == AF_INET6) {
                    jbyte *bytes = (jbyte *)&(((struct sockaddr_in6*)addrP->addr)->sin6_addr);
                    jbyteArray ipaddress = (*env)->GetObjectField(env, iaObj, ni_ia6ipaddressID);
                    jbyte caddr[16];
                    int i;

                    (*env)->GetByteArrayRegion(env, ipaddress, 0, 16, caddr);
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
 * Create a NetworkInterface object, populate the name and index, and
 * populate the InetAddress array based on the IP addresses for this
 * interface.
 */
jobject createNetworkInterface(JNIEnv *env, netif *ifs)
{
    jobject netifObj;
    jobject name;
    jobjectArray addrArr;
    jobjectArray bindArr;
    jobjectArray childArr;
    jint addr_index, addr_count, bind_index;
    jint child_count, child_index;
    netaddr *addrP;
    netif *childP;
    jobject tmp;

    /*
     * Create a NetworkInterface object and populate it
     */
    netifObj = (*env)->NewObject(env, ni_class, ni_ctrID);
    name = (*env)->NewStringUTF(env, ifs->name);
    if (netifObj == NULL || name == NULL) {
        return NULL;
    }
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
    addrArr = (*env)->NewObjectArray(env, addr_count,  ni_iacls, NULL);
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
            iaObj = (*env)->NewObject(env, ni_ia4cls, ni_ia4ctrID);
            if (iaObj) {
                 (*env)->SetIntField(env, iaObj, ni_iaaddressID,
                     htonl(((struct sockaddr_in*)addrP->addr)->sin_addr.s_addr));
            }
            ibObj = (*env)->NewObject(env, ni_ibcls, ni_ibctrID);
            if (ibObj) {
              (*env)->SetObjectField(env, ibObj, ni_ibaddressID, iaObj);
              if (addrP->brdcast) {
                jobject ia2Obj = NULL;
                ia2Obj = (*env)->NewObject(env, ni_ia4cls, ni_ia4ctrID);
                if (ia2Obj) {
                  (*env)->SetIntField(env, ia2Obj, ni_iaaddressID,
                                      htonl(((struct sockaddr_in*)addrP->brdcast)->sin_addr.s_addr));
                  (*env)->SetObjectField(env, ibObj, ni_ib4broadcastID, ia2Obj);
                  (*env)->SetShortField(env, ibObj, ni_ib4maskID, addrP->mask);
                }
              }
              (*env)->SetObjectArrayElement(env, bindArr, bind_index++, ibObj);
            }
        }

#ifdef AF_INET6
        if (addrP->family == AF_INET6) {
            int scope=0;
            iaObj = (*env)->NewObject(env, ni_ia6cls, ni_ia6ctrID);
            if (iaObj) {
                jbyteArray ipaddress = (*env)->NewByteArray(env, 16);
                if (ipaddress == NULL) {
                    return NULL;
                }
                (*env)->SetByteArrayRegion(env, ipaddress, 0, 16,
                    (jbyte *)&(((struct sockaddr_in6*)addrP->addr)->sin6_addr));
#ifdef __linux__
                if (!kernelIsV22()) {
                    scope = ((struct sockaddr_in6*)addrP->addr)->sin6_scope_id;
                }
#else
                scope = ((struct sockaddr_in6*)addrP->addr)->sin6_scope_id;
#endif
                if (scope != 0) { /* zero is default value, no need to set */
                    (*env)->SetIntField(env, iaObj, ia6_scopeidID, scope);
                    (*env)->SetBooleanField(env, iaObj, ia6_scopeidsetID, JNI_TRUE);
                    (*env)->SetObjectField(env, iaObj, ia6_scopeifnameID, netifObj);
                }
                (*env)->SetObjectField(env, iaObj, ni_ia6ipaddressID, ipaddress);
            }
            ibObj = (*env)->NewObject(env, ni_ibcls, ni_ibctrID);
            if (ibObj) {
              (*env)->SetObjectField(env, ibObj, ni_ibaddressID, iaObj);
              (*env)->SetShortField(env, ibObj, ni_ib4maskID, addrP->mask);
              (*env)->SetObjectArrayElement(env, bindArr, bind_index++, ibObj);
            }
        }
#endif

        if (iaObj == NULL) {
            return NULL;
        }

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

    /*
     * Enumerate IPv4 addresses
     */
    ifs = enumIPv4Interfaces(env, NULL);
    if (ifs == NULL) {
        if ((*env)->ExceptionOccurred(env)) {
            return NULL;
        }
    }

    /*
     * If IPv6 is available then enumerate IPv6 addresses.
     */
#ifdef AF_INET6
    if (ipv6_available()) {
        ifs = enumIPv6Interfaces(env, ifs);

        if ((*env)->ExceptionOccurred(env)) {
            freeif(ifs);
            return NULL;
        }
    }
#endif

    return ifs;
}


/*
 * Enumerates and returns all IPv4 interfaces
 */
static netif *enumIPv4Interfaces(JNIEnv *env, netif *ifs) {
    int sock;
    struct ifconf ifc;
    struct ifreq *ifreqP;
    char *buf;
    int numifs;
    unsigned i;
    unsigned bufsize;

    sock = JVM_Socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) {
        /*
         * If EPROTONOSUPPORT is returned it means we don't have
         * IPv4 support so don't throw an exception.
         */
        if (errno != EPROTONOSUPPORT) {
            NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                             "Socket creation failed");
        }
        return ifs;
    }

#ifdef __linux__
    /* need to do a dummy SIOCGIFCONF to determine the buffer size.
     * SIOCGIFCOUNT doesn't work
     */
    ifc.ifc_buf = NULL;
    if (ioctl(sock, SIOCGIFCONF, (char *)&ifc) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                         "ioctl SIOCGIFCONF failed");
        close(sock);
        return ifs;
    }
    bufsize = ifc.ifc_len;
#else
    if (ioctl(sock, SIOCGIFNUM, (char *)&numifs) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                         "ioctl SIOCGIFNUM failed");
        close(sock);
        return ifs;
    }
    bufsize = numifs * sizeof (struct ifreq);
#endif /* __linux__ */

    buf = (char *)malloc(bufsize);
    if (!buf) {
        JNU_ThrowOutOfMemoryError(env, "heap allocation failed");
        (void) close(sock);
        return ifs;
    }
    ifc.ifc_len = bufsize;
    ifc.ifc_buf = buf;
    if (ioctl(sock, SIOCGIFCONF, (char *)&ifc) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                         "ioctl SIOCGIFCONF failed");
        (void) close(sock);
        (void) free(buf);
        return ifs;
    }

    /*
     * Iterate through each interface
     */
    ifreqP = ifc.ifc_req;
    for (i=0; i<ifc.ifc_len/sizeof (struct ifreq); i++, ifreqP++) {
        int index;
        struct ifreq if2;

        memset((char *)&if2, 0, sizeof(if2));
        strcpy(if2.ifr_name, ifreqP->ifr_name);

        /*
         * Try to get the interface index
         * (Not supported on Solaris 2.6 or 7)
         */
        if (ioctl(sock, SIOCGIFINDEX, (char *)&if2) >= 0) {
            index = if2.ifr_index;
        } else {
            index = -1;
        }

        /*
         * Add to the list
         */
        ifs = addif(env, ifs, ifreqP->ifr_name, index, AF_INET,
                    (struct sockaddr *)&(ifreqP->ifr_addr),
                    sizeof(struct sockaddr_in), 0);

        /*
         * If an exception occurred then free the list
         */
        if ((*env)->ExceptionOccurred(env)) {
            close(sock);
            free(buf);
            freeif(ifs);
            return NULL;
        }
    }

    /*
     * Free socket and buffer
     */
    close(sock);
    free(buf);
    return ifs;
}


#if defined(__solaris__) && defined(AF_INET6)
/*
 * Enumerates and returns all IPv6 interfaces on Solaris
 */
static netif *enumIPv6Interfaces(JNIEnv *env, netif *ifs) {
    int sock;
    struct lifconf ifc;
    struct lifreq *ifr;
    int n;
    char *buf;
    struct lifnum numifs;
    unsigned bufsize;

    sock = JVM_Socket(AF_INET6, SOCK_DGRAM, 0);
    if (sock < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                         "Failed to create IPv6 socket");
        return ifs;
    }

    /*
     * Get the interface count
     */
    numifs.lifn_family = AF_UNSPEC;
    numifs.lifn_flags = 0;
    if (ioctl(sock, SIOCGLIFNUM, (char *)&numifs) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                         "ioctl SIOCGLIFNUM failed");
        close(sock);
        return ifs;
    }

    /*
     *  Enumerate the interface configurations
     */
    bufsize = numifs.lifn_count * sizeof (struct lifreq);
    buf = (char *)malloc(bufsize);
    if (!buf) {
        JNU_ThrowOutOfMemoryError(env, "heap allocation failed");
        (void) close(sock);
        return ifs;
    }
    ifc.lifc_family = AF_UNSPEC;
    ifc.lifc_flags = 0;
    ifc.lifc_len = bufsize;
    ifc.lifc_buf = buf;
    if (ioctl(sock, SIOCGLIFCONF, (char *)&ifc) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                         "ioctl SIOCGLIFCONF failed");
        close(sock);
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
         * Ignore non-IPv6 addresses
         */
        if (ifr->lifr_addr.ss_family != AF_INET6) {
            continue;
        }

        /*
         * Get the index
         */
        memset((char *)&if2, 0, sizeof(if2));
        strcpy(if2.lifr_name, ifr->lifr_name);
        if (ioctl(sock, SIOCGLIFINDEX, (char *)&if2) >= 0) {
            struct sockaddr_in6 *s6= (struct sockaddr_in6 *)&(ifr->lifr_addr);
            index = if2.lifr_index;
            s6->sin6_scope_id = index;
        }

        /* add to the list */
        ifs = addif(env, ifs, ifr->lifr_name, index, AF_INET6,
                    (struct sockaddr *)&(ifr->lifr_addr),
                    sizeof(struct sockaddr_in6), (short) ifr->lifr_addrlen);

        /*
         * If an exception occurred we return
         */
        if ((*env)->ExceptionOccurred(env)) {
            close(sock);
            free(buf);
            return ifs;
        }

    }

    close(sock);
    free(buf);
    return ifs;

}
#endif


#if defined(__linux__) && defined(AF_INET6)
/*
 * Enumerates and returns all IPv6 interfaces on Linux
 */
static netif *enumIPv6Interfaces(JNIEnv *env, netif *ifs) {
    FILE *f;
    char addr6[40], devname[20];
    char addr6p[8][5];
    int plen, scope, dad_status, if_idx;
    uint8_t ipv6addr[16];

    if ((f = fopen(_PATH_PROCNET_IFINET6, "r")) != NULL) {
        while (fscanf(f, "%4s%4s%4s%4s%4s%4s%4s%4s %02x %02x %02x %02x %20s\n",
                      addr6p[0], addr6p[1], addr6p[2], addr6p[3],
                      addr6p[4], addr6p[5], addr6p[6], addr6p[7],
                  &if_idx, &plen, &scope, &dad_status, devname) != EOF) {
            struct sockaddr_in6 addr;

            sprintf(addr6, "%s:%s:%s:%s:%s:%s:%s:%s",
                    addr6p[0], addr6p[1], addr6p[2], addr6p[3],
                    addr6p[4], addr6p[5], addr6p[6], addr6p[7]);
            inet_pton(AF_INET6, addr6, ipv6addr);

            memset(&addr, 0, sizeof(struct sockaddr_in6));
            memcpy((void*)addr.sin6_addr.s6_addr, (const void*)ipv6addr, 16);
            addr.sin6_scope_id = if_idx;

            ifs = addif(env, ifs, devname, if_idx, AF_INET6,
                        (struct sockaddr *)&addr,
                        sizeof(struct sockaddr_in6), plen);

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
 * Free an interface list (including any attached addresses)
 */
void freeif(netif *ifs) {
    netif *currif = ifs;

    while (currif != NULL) {
        netaddr *addrP = currif->addr;
        while (addrP != NULL) {
            netaddr *next = addrP->next;
            if (addrP->addr != NULL)
                free(addrP->addr);
            if (addrP->brdcast != NULL)
                free(addrP->brdcast);
            free(addrP);
            addrP = next;
        }

        free(currif->name);

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

/*
 * Add an interface to the list. If known interface just link
 * a new netaddr onto the list. If new interface create new
 * netif structure.
 */
netif *addif(JNIEnv *env, netif *ifs, char *if_name, int index, int family,
             struct sockaddr *new_addrP, int new_addrlen, short prefix) {
  netif *currif = ifs, *parent;
    netaddr *addrP;
#ifdef LIFNAMSIZ
    char name[LIFNAMSIZ];
    char vname[LIFNAMSIZ];
#else
    char name[IFNAMSIZ];
    char vname[IFNAMSIZ];
#endif
    char *unit;
    int isVirtual = 0;

    /*
     * If the interface name is a logical interface then we
     * remove the unit number so that we have the physical
     * interface (eg: hme0:1 -> hme0). NetworkInterface
     * currently doesn't have any concept of physical vs.
     * logical interfaces.
     */
    strcpy(name, if_name);

    /*
     * Create and populate the netaddr node. If allocation fails
     * return an un-updated list.
     */
    addrP = (netaddr *)malloc(sizeof(netaddr));
    if (addrP) {
        addrP->addr = (struct sockaddr *)malloc(new_addrlen);
        if (addrP->addr == NULL) {
            free(addrP);
            addrP = NULL;
        }
    }
    if (addrP == NULL) {
        JNU_ThrowOutOfMemoryError(env, "heap allocation failed");
        return ifs; /* return untouched list */
    }
    memcpy(addrP->addr, new_addrP, new_addrlen);
    addrP->family = family;

    addrP->brdcast = NULL;
    addrP->mask = prefix;
    if (family == AF_INET) {
      /*
       * Deal with brodcast addr & subnet mask
       */
      addrP->brdcast = getBroadcast(env, name);
      if (addrP->brdcast) {
        addrP->mask = getSubnet(env, name);
      }
    }

    vname[0] = 0;
    unit = strchr(name, ':');
    if (unit != NULL) {
      /**
       * This is a virtual interface. If we are able to access the parent
       * we need to create a new entry if it doesn't exist yet *and* update
       * the 'parent' interface with the new records.
       */
      struct ifreq if2;
      int sock;
      int len;

      sock = JVM_Socket(AF_INET, SOCK_DGRAM, 0);
      if (sock < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                     "Socket creation failed");
        return ifs; /* return untouched list */
      }

      len = unit - name;
      if (len > 0) {
        // temporarily use vname to hold the parent name of the interface
        // instead of creating another buffer.
        memcpy(&vname, name, len);
        vname[len] = '\0';

        memset((char *) &if2, 0, sizeof(if2));
        strcpy(if2.ifr_name, vname);

        if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2) >= 0) {
           // Got access to parent, so create it if necessary.
           strcpy(vname, name);
           *unit = '\0';
        } else {
#if defined(__solaris__) && defined(AF_INET6)
          struct   lifreq lifr;
          memset((char *) &lifr, 0, sizeof(lifr));
          strcpy(lifr.lifr_name, vname);

          /* Try with an IPv6 socket in case the interface has only IPv6
           * addresses assigned to it */
          close(sock);
          sock = JVM_Socket(AF_INET6, SOCK_DGRAM, 0);

          if (sock < 0) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                         "Socket creation failed");
            return ifs; /* return untouched list */
          }

          if (ioctl(sock, SIOCGLIFFLAGS, (char *)&lifr) >= 0) {
            // Got access to parent, so create it if necessary.
            strcpy(vname, name);
            *unit = '\0';
          } else {
            // failed to access parent interface do not create parent.
            // We are a virtual interface with no parent.
            isVirtual = 1;
            vname[0] = 0;
          }
#else
          // failed to access parent interface do not create parent.
          // We are a virtual interface with no parent.
          isVirtual = 1;
          vname[0] = 0;
#endif
        }
      }
      close(sock);
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
        currif = (netif *)malloc(sizeof(netif));
        if (currif) {
            currif->name = strdup(name);
            if (currif->name == NULL) {
                free(currif);
                currif = NULL;
            }
        }
        if (currif == NULL) {
            JNU_ThrowOutOfMemoryError(env, "heap allocation failed");
            return ifs;
        }
        currif->index = index;
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
        currif = (netif *)malloc(sizeof(netif));
        if (currif) {
          currif->name = strdup(vname);
          if (currif->name == NULL) {
            free(currif);
            currif = NULL;
          }
        }
        if (currif == NULL) {
          JNU_ThrowOutOfMemoryError(env, "heap allocation failed");
          return ifs;
        }
        currif->index = index;
        currif->addr = NULL;
        /* Need to duplicate the addr entry? */
        currif->virtual = 1;
        currif->childs = NULL;
        currif->next = parent->childs;
        parent->childs = currif;
      }

      tmpaddr = (netaddr *) malloc(sizeof(netaddr));
      if (tmpaddr == NULL) {
        JNU_ThrowOutOfMemoryError(env, "heap allocation failed");
        return ifs;
      }
      memcpy(tmpaddr, addrP, sizeof(netaddr));
      /**
       * Let's duplicate the address and broadcast address structures
       * if there are any.
       */
      if (addrP->addr != NULL) {
        tmpaddr->addr = malloc(new_addrlen);
        if (tmpaddr->addr != NULL)
          memcpy(tmpaddr->addr, addrP->addr, new_addrlen);
      }
      if (addrP->brdcast != NULL) {
        tmpaddr->brdcast = malloc(new_addrlen);
        if (tmpaddr->brdcast != NULL)
          memcpy(tmpaddr->brdcast, addrP->brdcast, new_addrlen);
      }
      tmpaddr->next = currif->addr;
      currif->addr = tmpaddr;
    }

    return ifs;
}

/**
 * Get flags from a NetworkInterface.
 */
static short getFlags(JNIEnv *env, jstring name) {
  int sock;
  struct ifreq if2;
  jboolean isCopy;
  const char* name_utf;
  short ret = -1;

  sock = JVM_Socket(AF_INET, SOCK_DGRAM, 0);
  if (sock < 0) {
    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "Socket creation failed");
    return -1;
  }

  name_utf = (*env)->GetStringUTFChars(env, name, &isCopy);
  memset((char *) &if2, 0, sizeof(if2));
  strcpy(if2.ifr_name, name_utf);

  if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2) >= 0) {
    ret = if2.ifr_flags;
  } else {
#if defined(__solaris__) && defined(AF_INET6)
    /* Try with an IPv6 socket in case the interface has only IPv6 addresses assigned to it */
    struct lifreq lifr;

    close(sock);
    sock = JVM_Socket(AF_INET6, SOCK_DGRAM, 0);

    if (sock < 0) {
      (*env)->ReleaseStringUTFChars(env, name, name_utf);
      NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                  "Socket creation failed");
      return -1;
    }

    memset((caddr_t)&lifr, 0, sizeof(lifr));
    strcpy((caddr_t)&(lifr.lifr_name), name_utf);

    if (ioctl(sock, SIOCGLIFFLAGS, (char *)&lifr) >= 0) {
      ret = lifr.lifr_flags;
    } else {
      NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                               "IOCTL failed");
    }
#else
    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "IOCTL failed");
#endif
  }
  close(sock);
  /* release the UTF string and interface list */
  (*env)->ReleaseStringUTFChars(env, name, name_utf);

  return ret;
}

/**
 * Returns the IPv4 broadcast address of a named interface, if it exists.
 * Returns 0 if it doesn't have one.
 */
static struct sockaddr *getBroadcast(JNIEnv *env, const char *ifname) {
  int sock;
  struct sockaddr *ret = NULL;
  struct ifreq if2;
  short flag = 0;

  sock = JVM_Socket(AF_INET, SOCK_DGRAM, 0);
  if (sock < 0) {
    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "Socket creation failed");
    return ret;
  }

  memset((char *) &if2, 0, sizeof(if2));
  strcpy(if2.ifr_name, ifname);
  /* Let's make sure the interface does have a broadcast address */
  if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2) >= 0) {
    flag = if2.ifr_flags;
  } else {
    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "IOCTL failed");
  }
  if (flag & IFF_BROADCAST) {
    /* It does, let's retrieve it*/
    if (ioctl(sock, SIOCGIFBRDADDR, (char *)&if2) >= 0) {
      ret = (struct sockaddr*) malloc(sizeof(struct sockaddr));
      memcpy(ret, &if2.ifr_broadaddr, sizeof(struct sockaddr));
    } else {
      NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                   "IOCTL failed");
    }
  }
  close(sock);
  return ret;
}

/**
 * Returns the IPv4 subnet prefix length (aka subnet mask) for the named
 * interface, if it has one, otherwise return -1.
 */
static short getSubnet(JNIEnv *env, const char *ifname) {
  int sock;
  unsigned int mask;
  short ret;
  struct ifreq if2;

  sock = JVM_Socket(AF_INET, SOCK_DGRAM, 0);
  if (sock < 0) {
    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "Socket creation failed");
    return -1;
  }

  memset((char *) &if2, 0, sizeof(if2));
  strcpy(if2.ifr_name, ifname);
  if (ioctl(sock, SIOCGIFNETMASK, (char *)&if2) >= 0) {
    mask = ntohl(((struct sockaddr_in*)&(if2.ifr_addr))->sin_addr.s_addr);
    ret = 0;
    while (mask) {
      mask <<= 1;
      ret++;
    }
    close(sock);
    return ret;
  }
  NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                               "IOCTL failed");
  close(sock);
  return -1;
}

#ifdef __solaris__
#define DEV_PREFIX      "/dev/"

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
  if ((fd = open(style1dev, O_RDWR)) == -1) {
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
    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "putmsg failed");
    return -1;
  }
  dlpaack = (dl_phys_addr_ack_t *)buf;
  msg.buf = (char *)buf;
  msg.len = 0;
  msg.maxlen = sizeof (buf);
  if (getmsg(fd, &msg, NULL, &flags) < 0) {
    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "getmsg failed");
    return -1;
  }
  if (msg.len < DL_PHYS_ADDR_ACK_SIZE ||
      dlpaack->dl_primitive != DL_PHYS_ADDR_ACK) {
    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                    "Couldn't obtain phys addr\n");
    return -1;
  }

  memcpy(retbuf, &buf[dlpaack->dl_addr_offset], dlpaack->dl_addr_length);
  return dlpaack->dl_addr_length;
}
#endif

/**
 * Get the Hardware address (usually MAC address) for the named interface.
 * return puts the data in buf, and returns the length, in byte, of the
 * MAC address. Returns -1 if there is no hardware address on that interface.
 */
int getMacAddress(JNIEnv *env, const struct in_addr* addr, const char* ifname,
                  unsigned char *buf) {
  int sock;
#ifdef __linux__
  static struct ifreq ifr;
  int i;

  sock = JVM_Socket(AF_INET, SOCK_DGRAM, 0);

  if (sock < 0) {
    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "Socket creation failed");
    return -1;
  }

  strcpy(ifr.ifr_name, ifname);

  if (ioctl(sock, SIOCGIFHWADDR, &ifr) < 0) {
    fprintf(stderr, "SIOCIFHWADDR: %s\n",
            strerror(errno));
    close(sock);
    return -1;
  }
  memcpy(buf, &ifr.ifr_hwaddr.sa_data, IFHWADDRLEN);
  close(sock);
  for (i = 0; i < IFHWADDRLEN; i++) {
    if (buf[i] != 0)
      return IFHWADDRLEN;
  }
  /*
   * All bytes to 0 means no hardware address.
   */
  return -1;
#else
  struct arpreq arpreq;
  struct sockaddr_in* sin;
  struct sockaddr_in ipAddr;
  int len;

  /**
   * On Solaris we have to use DLPI, but it will only work if we have
   * privileged access (i.e. root). If that fails, we try a lookup
   * in the ARP table, which requires an IPv4 address.
   */
  if ((len = getMacFromDevice(env, ifname, buf)) > 0) {
    return len;
  }
  if (addr == NULL) {
    /**
     * No IPv4 address for that interface, so can't do an ARP lookup.
     */
    return -1;
  }
  sin = (struct sockaddr_in *) &arpreq.arp_pa;
  memset((char *) &arpreq, 0, sizeof(struct arpreq));
  ipAddr.sin_port = 0;
  ipAddr.sin_family = AF_INET;
  memcpy(&ipAddr.sin_addr, addr, sizeof(struct in_addr));
  memcpy(&arpreq.arp_pa, &ipAddr, sizeof(struct sockaddr_in));
  arpreq.arp_flags= ATF_PUBL;
  sock = JVM_Socket(AF_INET, SOCK_DGRAM, 0);

  if (sock < 0) {
    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "Socket creation failed");
    return -1;
  }

  if (ioctl(sock, SIOCGARP, &arpreq) >= 0) {
    close(sock);
    memcpy(buf, &arpreq.arp_ha.sa_data[0], 6);
    return 6;
  }

  if (errno != ENXIO) {
    // "No such device or address" means no hardware address, so it's
    // normal don't throw an exception
    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "IOCTL failed");
  }
  close(sock);
#endif
  return -1;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    isUp0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isUp0
    (JNIEnv *env, jclass cls, jstring name, jint index) {
    short val;

    val = getFlags(env, name);
    if ( (val & IFF_UP) && (val &  IFF_RUNNING))
      return JNI_TRUE;
    return JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    isP2P0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isP2P0
    (JNIEnv *env, jclass cls, jstring name, jint index) {
    if (getFlags(env, name) & IFF_POINTOPOINT)
      return JNI_TRUE;
    return JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    isLoopback0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isLoopback0
    (JNIEnv *env, jclass cls, jstring name, jint index) {
    if (getFlags(env, name) & IFF_LOOPBACK)
      return JNI_TRUE;
    return JNI_FALSE;
}

/*
 * Class:     java_net_NetworkInterface
 * Method:    supportsMulticast0
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_supportsMulticast0
(JNIEnv *env, jclass cls, jstring name, jint index) {
  short val;

  val = getFlags(env, name);
  if (val & IFF_MULTICAST)
    return JNI_TRUE;
  return JNI_FALSE;
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
  jboolean isCopy;
  const char* name_utf = (*env)->GetStringUTFChars(env, name, &isCopy);

  if (!IS_NULL(addrArray)) {
    (*env)->GetByteArrayRegion(env, addrArray, 0, 4, caddr);
    addr = ((caddr[0]<<24) & 0xff000000);
    addr |= ((caddr[1] <<16) & 0xff0000);
    addr |= ((caddr[2] <<8) & 0xff00);
    addr |= (caddr[3] & 0xff);
    iaddr.s_addr = htonl(addr);
    len = getMacAddress(env, &iaddr, name_utf, mac);
  } else {
    len = getMacAddress(env, NULL, name_utf, mac);
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
  return ret;
}

/*
 * Class:       java_net_NetworkInterface
 * Method:      getMTU0
 * Signature:   ([bLjava/lang/String;I)I
 */

JNIEXPORT jint JNICALL Java_java_net_NetworkInterface_getMTU0(JNIEnv *env, jclass class, jstring name, jint index) {
  jboolean isCopy;
  int sock;
  struct ifreq if2;
  int ret = -1;
  const char* name_utf = (*env)->GetStringUTFChars(env, name, &isCopy);

  sock = JVM_Socket(AF_INET, SOCK_DGRAM, 0);
  if (sock < 0) {
    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "Socket creation failed");
  } else {

#ifdef __linux__
    memset((char *) &if2, 0, sizeof(if2));
    strcpy(if2.ifr_name, name_utf);

    if (ioctl(sock, SIOCGIFMTU, (char *)&if2) >= 0) {
      ret= if2.ifr_mtu;
    } else {
      NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                   "IOCTL failed");
    }
#else /* Solaris */
    struct   lifreq lifr;
    memset((caddr_t)&lifr, 0, sizeof(lifr));
    strcpy((caddr_t)&(lifr.lifr_name), name_utf);
    if (ioctl(sock, SIOCGLIFMTU, (caddr_t)&lifr) >= 0) {
      ret = lifr.lifr_mtu;
#ifdef AF_INET6
    } else {
      /* Try wIth an IPv6 socket in case the interface has only IPv6 addresses assigned to it */
      close(sock);
      sock = JVM_Socket(AF_INET6, SOCK_DGRAM, 0);

      if (sock < 0) {
        (*env)->ReleaseStringUTFChars(env, name, name_utf);
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                     "Socket creation failed");
        return -1;
      }

      if (ioctl(sock, SIOCGLIFMTU, (caddr_t)&lifr) >= 0) {
        ret = lifr.lifr_mtu;
      } else {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                     "IOCTL failed");
      }
    }
#else
    } else {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                     "IOCTL failed");
    }
#endif
#endif
    close(sock);
  }
  /* release the UTF string and interface list */
  (*env)->ReleaseStringUTFChars(env, name, name_utf);
  return ret;
}
