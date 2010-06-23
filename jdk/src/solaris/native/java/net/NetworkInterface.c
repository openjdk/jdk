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
#include <sys/sockio.h>
#endif

#ifdef __linux__
#include <sys/ioctl.h>
#include <bits/ioctls.h>
#include <linux/sockios.h>
#include <sys/utsname.h>
#include <stdio.h>
#endif

#ifdef __linux__
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
static int     getIndex(JNIEnv *env, int sock, const char *ifname);

static int     getFlags(JNIEnv *env, int sock, const char *ifname);
static int     getMacAddress(JNIEnv *env, int sock,  const char* ifname, const struct in_addr* addr, unsigned char *buf);
static int     getMTU(JNIEnv *env, int sock, const char *ifname);



#ifdef __solaris__
static netif *enumIPvXInterfaces(JNIEnv *env, int sock, netif *ifs, int family);
static int    getMacFromDevice(JNIEnv *env, const char* ifname, unsigned char* retbuf);
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
    const char* name_utf;
    jobject obj = NULL;

    ifs = enumInterfaces(env);
    if (ifs == NULL) {
        return NULL;
    }

    name_utf = (*env)->GetStringUTFChars(env, name, &isCopy);

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
    int family = (  (*env)->GetIntField(env, iaObj, ni_iafamilyID) == IPv4 ) ? AF_INET : AF_INET6;
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
    const char* name_utf;

    name_utf = (*env)->GetStringUTFChars(env, name, &isCopy);

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

    name_utf = (*env)->GetStringUTFChars(env, name, &isCopy);

    if ((sock = openSocketWithFallback(env, name_utf)) < 0) {
        (*env)->ReleaseStringUTFChars(env, name, name_utf);
         return -1;
    }

    name_utf = (*env)->GetStringUTFChars(env, name, &isCopy);

    ret = getFlags(env, sock, name_utf);

    close(sock);
    (*env)->ReleaseStringUTFChars(env, name, name_utf);

    if (ret < 0) {
        NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL  SIOCGLIFFLAGS failed");
        return -1;
    }

    return ret;
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
                 (*env)->SetIntField(env, iaObj, ni_iaaddressID, htonl(((struct sockaddr_in*)addrP->addr)->sin_addr.s_addr));
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

                scope = ((struct sockaddr_in6*)addrP->addr)->sin6_scope_id;

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

    /* return partial list if exception occure in the middle of process ???*/

    /*
     * If IPv6 is available then enumerate IPv6 addresses.
     */
#ifdef AF_INET6
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
#endif

    return ifs;
}

#define CHECKED_MALLOC3(_pointer,_type,_size) \
       do{ \
        _pointer = (_type)malloc( _size ); \
        if (_pointer == NULL) { \
            JNU_ThrowOutOfMemoryError(env, "heap allocation failed"); \
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

netif *addif(JNIEnv *env, int sock, const char * if_name, netif *ifs, struct sockaddr* ifr_addrP, int family, short prefix) {
    netif *currif = ifs, *parent;
    netaddr *addrP;

    #ifdef __solaris__
    char name[LIFNAMSIZ],  vname[LIFNAMSIZ];
    #else
    char name[IFNAMSIZ],  vname[IFNAMSIZ];
    #endif

    char  *name_colonP;
    int mask;
    int isVirtual = 0;
    int addr_size;

    /*
     * If the interface name is a logical interface then we
     * remove the unit number so that we have the physical
     * interface (eg: hme0:1 -> hme0). NetworkInterface
     * currently doesn't have any concept of physical vs.
     * logical interfaces.
     */
    strcpy(name, if_name);
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
      /*
       * Deal with brodcast addr & subnet mask
       */
       struct sockaddr * brdcast_to = (struct sockaddr *) ((char *) addrP + sizeof(netaddr) + addr_size);
       addrP->brdcast = getBroadcast(env, sock, name,  brdcast_to );

       if (addrP->brdcast && (mask = getSubnet(env, sock, name)) != -1) {
           addrP->mask = mask;
       }
     }

    /**
     * Deal with virtual interface with colon notaion e.g. eth0:1
     */
    name_colonP = strchr(name, ':');
    if (name_colonP != NULL) {
      /**
       * This is a virtual interface. If we are able to access the parent
       * we need to create a new entry if it doesn't exist yet *and* update
       * the 'parent' interface with the new records.
       */
        *name_colonP = 0;
        if (getFlags(env,sock,name) < 0) {
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
         CHECKED_MALLOC3(currif, netif *, sizeof(netif)+IFNAMSIZ );
         currif->name = (char *) currif+sizeof(netif);
         strcpy(currif->name, name);
         currif->index = getIndex(env,sock,name);
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
            CHECKED_MALLOC3(currif, netif *, sizeof(netif)+ IFNAMSIZ );
            currif->name = (char *) currif + sizeof(netif);
            strcpy(currif->name, vname);
            currif->index = getIndex(env,sock,vname);
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

    if ((sock = JVM_Socket(proto, SOCK_DGRAM, 0)) < 0) {
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


/** Linux **/
#ifdef __linux__
/* Open socket for further ioct calls, try v4 socket first and
 * if it falls return v6 socket
 */

#ifdef AF_INET6
static int openSocketWithFallback(JNIEnv *env, const char *ifname){
    int sock;
    struct ifreq if2;

     if ((sock = JVM_Socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
         if (errno == EPROTONOSUPPORT){
              if ( (sock = JVM_Socket(AF_INET6, SOCK_DGRAM, 0)) < 0 ){
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
    char *buf;
    int numifs;
    unsigned i;


    /* need to do a dummy SIOCGIFCONF to determine the buffer size.
     * SIOCGIFCOUNT doesn't work
     */
    ifc.ifc_buf = NULL;
    if (ioctl(sock, SIOCGIFCONF, (char *)&ifc) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "ioctl SIOCGIFCONF failed");
        return ifs;
    }

    CHECKED_MALLOC3(buf,char *, ifc.ifc_len);

    ifc.ifc_buf = buf;
    if (ioctl(sock, SIOCGIFCONF, (char *)&ifc) < 0) {
        NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "ioctl SIOCGIFCONF failed");
        (void) free(buf);
        return ifs;
    }

    /*
     * Iterate through each interface
     */
    ifreqP = ifc.ifc_req;
    for (i=0; i<ifc.ifc_len/sizeof (struct ifreq); i++, ifreqP++) {
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

#ifdef AF_INET6
static netif *enumIPv6Interfaces(JNIEnv *env, int sock, netif *ifs) {
    FILE *f;
    char addr6[40], devname[20];
    char addr6p[8][5];
    int plen, scope, dad_status, if_idx;
    uint8_t ipv6addr[16];

    if ((f = fopen(_PATH_PROCNET_IFINET6, "r")) != NULL) {
        while (fscanf(f, "%4s%4s%4s%4s%4s%4s%4s%4s %02x %02x %02x %02x %20s\n",
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


static int getIndex(JNIEnv *env, int sock, const char *name){
     /*
      * Try to get the interface index
      * (Not supported on Solaris 2.6 or 7)
      */
    struct ifreq if2;
    strcpy(if2.ifr_name, name);

    if (ioctl(sock, SIOCGIFINDEX, (char *)&if2) < 0) {
        return -1;
    }

    return if2.ifr_ifindex;
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

static int getFlags(JNIEnv *env, int sock, const char *ifname) {
  struct ifreq if2;
  int ret = -1;

  memset((char *) &if2, 0, sizeof(if2));
  strcpy(if2.ifr_name, ifname);

  if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2) < 0){
      return -1;
  }

  return if2.ifr_flags;
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

     if ((sock = JVM_Socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
         if (errno == EPROTONOSUPPORT){
              if ( (sock = JVM_Socket(AF_INET6, SOCK_DGRAM, 0)) < 0 ){
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
      * Solaris requires that we have IPv6 socket to query an
      * interface without IPv4 address - check it here
      * POSIX 1 require the kernell to return ENOTTY if the call is
      * unappropriate for device e.g. NETMASK for device having IPv6
      * only address but not all devices follows the standart so
      * fallback on any error.  It's not an ecology friendly but more
      * reliable.
      */

    if (! alreadyV6 ){
        memset((char *) &if2, 0, sizeof(if2));
        strcpy(if2.lifr_name, ifname);
        if (ioctl(sock, SIOCGLIFNETMASK, (char *)&if2) < 0) {
                close(sock);
                if ( (sock = JVM_Socket(AF_INET6, SOCK_DGRAM, 0)) < 0 ){
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
 * (linux verison)
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

static int getIndex(JNIEnv *env, int sock, const char *name){
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
          if (errno != ENXIO) {
              // "No such device or address" means no hardware address, so it's
              // normal don't throw an exception
              NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL failed");
              return -1;
          }
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


static int getFlags(JNIEnv *env, int sock, const char *ifname) {
     struct   lifreq lifr;
     memset((caddr_t)&lifr, 0, sizeof(lifr));
     strcpy((caddr_t)&(lifr.lifr_name), ifname);

     if (ioctl(sock, SIOCGLIFFLAGS, (char *)&lifr) < 0) {
         NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGLIFFLAGS failed");
         return -1;
     }

     return  lifr.lifr_flags;
}


#endif


