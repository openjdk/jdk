/*
 * Copyright 1998-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
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
    return JNI_VERSION_1_2;
}

static int initialized = 0;

void init(JNIEnv *env) {
    if (!initialized) {
        Java_java_net_InetAddress_init(env, 0);
        Java_java_net_Inet4Address_init(env, 0);
        Java_java_net_Inet6Address_init(env, 0);
        initialized = 1;
    }
}

JNIEXPORT jobject JNICALL
NET_SockaddrToInetAddress(JNIEnv *env, struct sockaddr *him, int *port) {
    jobject iaObj;
    init(env);
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
            (*env)->SetIntField(env, iaObj, ia_addressID, address);
            (*env)->SetIntField(env, iaObj, ia_familyID, IPv4);
        } else {
            static jclass inet6Cls = 0;
            jint scope;
            if (inet6Cls == 0) {
                jclass c = (*env)->FindClass(env, "java/net/Inet6Address");
                CHECK_NULL_RETURN(c, NULL);
                inet6Cls = (*env)->NewGlobalRef(env, c);
                CHECK_NULL_RETURN(inet6Cls, NULL);
                (*env)->DeleteLocalRef(env, c);
            }
            iaObj = (*env)->NewObject(env, inet6Cls, ia6_ctrID);
            CHECK_NULL_RETURN(iaObj, NULL);
            ipaddress = (*env)->NewByteArray(env, 16);
            CHECK_NULL_RETURN(ipaddress, NULL);
            (*env)->SetByteArrayRegion(env, ipaddress, 0, 16,
                                       (jbyte *)&(him6->sin6_addr));

            (*env)->SetObjectField(env, iaObj, ia6_ipaddressID, ipaddress);

            (*env)->SetIntField(env, iaObj, ia_familyID, IPv6);
            scope = getScopeID(him);
            (*env)->SetIntField(env, iaObj, ia6_scopeidID, scope);
            if (scope > 0)
                (*env)->SetBooleanField(env, iaObj, ia6_scopeidsetID, JNI_TRUE);
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
            (*env)->SetIntField(env, iaObj, ia_familyID, IPv4);
            (*env)->SetIntField(env, iaObj, ia_addressID,
                                ntohl(him4->sin_addr.s_addr));
            *port = ntohs(him4->sin_port);
        }
    return iaObj;
}

JNIEXPORT jint JNICALL
NET_SockaddrEqualsInetAddress(JNIEnv *env, struct sockaddr *him, jobject iaObj)
{
    jint family = (*env)->GetIntField(env, iaObj, ia_familyID) == IPv4?
        AF_INET : AF_INET6;

#ifdef AF_INET6
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
            addrCur = (*env)->GetIntField(env, iaObj, ia_addressID);
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
            ipaddress = (*env)->GetObjectField(env, iaObj, ia6_ipaddressID);
            scope = (*env)->GetIntField(env, iaObj, ia6_scopeidID);
            (*env)->GetByteArrayRegion(env, ipaddress, 0, 16, caddrCur);
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
            addrCur = (*env)->GetIntField(env, iaObj, ia_addressID);
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
