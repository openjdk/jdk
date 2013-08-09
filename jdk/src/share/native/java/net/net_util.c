/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jvm.h"
#include "jni_util.h"
#include "net_util.h"

int IPv6_supported() ;

static int IPv6_available;

JNIEXPORT jint JNICALL ipv6_available()
{
    return IPv6_available ;
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv *env;
    jclass iCls;
    jmethodID mid;
    jstring s;
    jint preferIPv4Stack;

    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_2) == JNI_OK) {
        if (JVM_InitializeSocketLibrary() < 0) {
            JNU_ThrowByName(env, "java/lang/UnsatisfiedLinkError",
                            "failed to initialize net library.");
            return JNI_VERSION_1_2;
        }
    }
    iCls = (*env)->FindClass(env, "java/lang/Boolean");
    CHECK_NULL_RETURN(iCls, JNI_VERSION_1_2);
    mid = (*env)->GetStaticMethodID(env, iCls, "getBoolean", "(Ljava/lang/String;)Z");
    CHECK_NULL_RETURN(mid, JNI_VERSION_1_2);
    s = (*env)->NewStringUTF(env, "java.net.preferIPv4Stack");
    CHECK_NULL_RETURN(s, JNI_VERSION_1_2);
    preferIPv4Stack = (*env)->CallStaticBooleanMethod(env, iCls, mid, s);

    /*
       Since we have initialized and loaded the Socket library we will
       check now to whether we have IPv6 on this platform and if the
       supporting socket APIs are available
    */
    IPv6_available = IPv6_supported() & (!preferIPv4Stack);
    initLocalAddrTable ();
    parseExclusiveBindProperty(env);

    return JNI_VERSION_1_2;
}

static int initialized = 0;

static void initInetAddrs(JNIEnv *env) {
    if (!initialized) {
        Java_java_net_InetAddress_init(env, 0);
        Java_java_net_Inet4Address_init(env, 0);
        Java_java_net_Inet6Address_init(env, 0);
        initialized = 1;
    }
}

/* The address, and family fields used to be in InetAddress
 * but are now in an implementation object. So, there is an extra
 * level of indirection to access them now.
 */

extern jclass iac_class;
extern jfieldID ia_holderID;
extern jfieldID iac_addressID;
extern jfieldID iac_familyID;

/**
 * set_ methods return JNI_TRUE on success JNI_FALSE on error
 * get_ methods that return +ve int return -1 on error
 * get_ methods that return objects return NULL on error.
 */
jobject getInet6Address_scopeifname(JNIEnv *env, jobject iaObj) {
    jobject holder;

    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
    CHECK_NULL_RETURN(holder, NULL);
    return (*env)->GetObjectField(env, holder, ia6_scopeifnameID);
}

int setInet6Address_scopeifname(JNIEnv *env, jobject iaObj, jobject scopeifname) {
    jobject holder;

    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
    CHECK_NULL_RETURN(holder, JNI_FALSE);
    (*env)->SetObjectField(env, holder, ia6_scopeifnameID, scopeifname);
    return JNI_TRUE;
}

int getInet6Address_scopeid_set(JNIEnv *env, jobject iaObj) {
    jobject holder;

    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
    CHECK_NULL_RETURN(holder, -1);
    return (*env)->GetBooleanField(env, holder, ia6_scopeidsetID);
}

int getInet6Address_scopeid(JNIEnv *env, jobject iaObj) {
    jobject holder;

    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
    CHECK_NULL_RETURN(holder, -1);
    return (*env)->GetIntField(env, holder, ia6_scopeidID);
}

int setInet6Address_scopeid(JNIEnv *env, jobject iaObj, int scopeid) {
    jobject holder;

    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
    CHECK_NULL_RETURN(holder, JNI_FALSE);
    (*env)->SetIntField(env, holder, ia6_scopeidID, scopeid);
    if (scopeid > 0) {
            (*env)->SetBooleanField(env, holder, ia6_scopeidsetID, JNI_TRUE);
    }
    return JNI_TRUE;
}


int getInet6Address_ipaddress(JNIEnv *env, jobject iaObj, char *dest) {
    jobject holder, addr;
    jbyteArray barr;

    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
    CHECK_NULL_RETURN(holder, JNI_FALSE);
    addr =  (*env)->GetObjectField(env, holder, ia6_ipaddressID);
    CHECK_NULL_RETURN(addr, JNI_FALSE);
    (*env)->GetByteArrayRegion(env, addr, 0, 16, (jbyte *)dest);
    return JNI_TRUE;
}

int setInet6Address_ipaddress(JNIEnv *env, jobject iaObj, char *address) {
    jobject holder;
    jbyteArray addr;

    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
    CHECK_NULL_RETURN(holder, JNI_FALSE);
    addr =  (jbyteArray)(*env)->GetObjectField(env, holder, ia6_ipaddressID);
    if (addr == NULL) {
        addr = (*env)->NewByteArray(env, 16);
        CHECK_NULL_RETURN(addr, JNI_FALSE);
        (*env)->SetObjectField(env, holder, ia6_ipaddressID, addr);
    }
    (*env)->SetByteArrayRegion(env, addr, 0, 16, (jbyte *)address);
    return JNI_TRUE;
}

void setInetAddress_addr(JNIEnv *env, jobject iaObj, int address) {
    jobject holder;
    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia_holderID);
    (*env)->SetIntField(env, holder, iac_addressID, address);
}

void setInetAddress_family(JNIEnv *env, jobject iaObj, int family) {
    jobject holder;
    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia_holderID);
    (*env)->SetIntField(env, holder, iac_familyID, family);
}

void setInetAddress_hostName(JNIEnv *env, jobject iaObj, jobject host) {
    jobject holder;
    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia_holderID);
    (*env)->SetObjectField(env, holder, iac_hostNameID, host);
}

int getInetAddress_addr(JNIEnv *env, jobject iaObj) {
    jobject holder;
    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia_holderID);
    return (*env)->GetIntField(env, holder, iac_addressID);
}

int getInetAddress_family(JNIEnv *env, jobject iaObj) {
    jobject holder;

    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia_holderID);
    return (*env)->GetIntField(env, holder, iac_familyID);
}

jobject getInetAddress_hostName(JNIEnv *env, jobject iaObj) {
    jobject holder;
    initInetAddrs(env);
    holder = (*env)->GetObjectField(env, iaObj, ia_holderID);
    return (*env)->GetObjectField(env, holder, iac_hostNameID);
}

JNIEXPORT jobject JNICALL
NET_SockaddrToInetAddress(JNIEnv *env, struct sockaddr *him, int *port) {
    jobject iaObj;
    initInetAddrs(env);
#ifdef AF_INET6
    if (him->sa_family == AF_INET6) {
        jbyteArray ipaddress;
#ifdef WIN32
        struct SOCKADDR_IN6 *him6 = (struct SOCKADDR_IN6 *)him;
#else
        struct sockaddr_in6 *him6 = (struct sockaddr_in6 *)him;
#endif
        jbyte *caddr = (jbyte *)&(him6->sin6_addr);
        if (NET_IsIPv4Mapped(caddr)) {
            int address;
            static jclass inet4Cls = 0;
            if (inet4Cls == 0) {
                jclass c = (*env)->FindClass(env, "java/net/Inet4Address");
                CHECK_NULL_RETURN(c, NULL);
                inet4Cls = (*env)->NewGlobalRef(env, c);
                CHECK_NULL_RETURN(inet4Cls, NULL);
                (*env)->DeleteLocalRef(env, c);
            }
            iaObj = (*env)->NewObject(env, inet4Cls, ia4_ctrID);
            CHECK_NULL_RETURN(iaObj, NULL);
            address = NET_IPv4MappedToIPv4(caddr);
            setInetAddress_addr(env, iaObj, address);
            setInetAddress_family(env, iaObj, IPv4);
        } else {
            static jclass inet6Cls = 0;
            jint scope;
            int ret;
            if (inet6Cls == 0) {
                jclass c = (*env)->FindClass(env, "java/net/Inet6Address");
                CHECK_NULL_RETURN(c, NULL);
                inet6Cls = (*env)->NewGlobalRef(env, c);
                CHECK_NULL_RETURN(inet6Cls, NULL);
                (*env)->DeleteLocalRef(env, c);
            }
            iaObj = (*env)->NewObject(env, inet6Cls, ia6_ctrID);
            CHECK_NULL_RETURN(iaObj, NULL);
            ret = setInet6Address_ipaddress(env, iaObj, (char *)&(him6->sin6_addr));
            CHECK_NULL_RETURN(ret, NULL);
            setInetAddress_family(env, iaObj, IPv6);
            scope = getScopeID(him);
            setInet6Address_scopeid(env, iaObj, scope);
        }
        *port = ntohs(him6->sin6_port);
    } else
#endif /* AF_INET6 */
        {
            struct sockaddr_in *him4 = (struct sockaddr_in *)him;
            static jclass inet4Cls = 0;

            if (inet4Cls == 0) {
                jclass c = (*env)->FindClass(env, "java/net/Inet4Address");
                CHECK_NULL_RETURN(c, NULL);
                inet4Cls = (*env)->NewGlobalRef(env, c);
                CHECK_NULL_RETURN(inet4Cls, NULL);
                (*env)->DeleteLocalRef(env, c);
            }
            iaObj = (*env)->NewObject(env, inet4Cls, ia4_ctrID);
            CHECK_NULL_RETURN(iaObj, NULL);
            setInetAddress_family(env, iaObj, IPv4);
            setInetAddress_addr(env, iaObj, ntohl(him4->sin_addr.s_addr));
            *port = ntohs(him4->sin_port);
        }
    return iaObj;
}

JNIEXPORT jint JNICALL
NET_SockaddrEqualsInetAddress(JNIEnv *env, struct sockaddr *him, jobject iaObj)
{
    jint family = AF_INET;

#ifdef AF_INET6
    family = getInetAddress_family(env, iaObj) == IPv4? AF_INET : AF_INET6;
    if (him->sa_family == AF_INET6) {
#ifdef WIN32
        struct SOCKADDR_IN6 *him6 = (struct SOCKADDR_IN6 *)him;
#else
        struct sockaddr_in6 *him6 = (struct sockaddr_in6 *)him;
#endif
        jbyte *caddrNew = (jbyte *)&(him6->sin6_addr);
        if (NET_IsIPv4Mapped(caddrNew)) {
            int addrNew;
            int addrCur;
            if (family == AF_INET6) {
                return JNI_FALSE;
            }
            addrNew = NET_IPv4MappedToIPv4(caddrNew);
            addrCur = getInetAddress_addr(env, iaObj);
            if (addrNew == addrCur) {
                return JNI_TRUE;
            } else {
                return JNI_FALSE;
            }
        } else {
            jbyteArray ipaddress;
            jbyte caddrCur[16];
            int scope;

            if (family == AF_INET) {
                return JNI_FALSE;
            }
            scope = getInet6Address_scopeid(env, iaObj);
            getInet6Address_ipaddress(env, iaObj, (char *)caddrCur);
            if (NET_IsEqual(caddrNew, caddrCur) && cmpScopeID(scope, him)) {
                return JNI_TRUE;
            } else {
                return JNI_FALSE;
            }
        }
    } else
#endif /* AF_INET6 */
        {
            struct sockaddr_in *him4 = (struct sockaddr_in *)him;
            int addrNew, addrCur;
            if (family != AF_INET) {
                return JNI_FALSE;
            }
            addrNew = ntohl(him4->sin_addr.s_addr);
            addrCur = getInetAddress_addr(env, iaObj);
            if (addrNew == addrCur) {
                return JNI_TRUE;
            } else {
                return JNI_FALSE;
            }
        }
}

unsigned short
in_cksum(unsigned short *addr, int len) {
    int nleft = len;
    int sum = 0;
    unsigned short *w = addr;
    unsigned short answer = 0;
    while(nleft > 1) {
        sum += *w++;
        nleft -= 2;
    }

    if (nleft == 1) {
        *(unsigned char *) (&answer) = *(unsigned char *)w;
        sum += answer;
    }

    sum = (sum >> 16) + (sum & 0xffff);
    sum += (sum >> 16);
    answer = ~sum;
    return (answer);
}
