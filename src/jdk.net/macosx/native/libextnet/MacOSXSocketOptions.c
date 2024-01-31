/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include <sys/socket.h>
#include <sys/types.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>

#include <jni.h>
#include <netinet/tcp.h>

#define __APPLE_USE_RFC_3542
#include <netinet/in.h>

#ifndef IP_DONTFRAG
#define IP_DONTFRAG             28
#endif

#ifndef IPV6_DONTFRAG
#define IPV6_DONTFRAG           62
#endif

#include "jni_util.h"

/*
 * Declare library specific JNI_Onload entry if static build
 */
DEF_STATIC_JNI_OnLoad

static jint socketOptionSupported(jint sockopt) {
    jint one = 1;
    jint rv, s;
    /* First try IPv6; fall back to IPv4. */
    s = socket(AF_INET6, SOCK_STREAM, IPPROTO_TCP);
    if (s < 0) {
        if (errno == EPFNOSUPPORT || errno == EAFNOSUPPORT) {
            s = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        }
        if (s < 0) {
            return 0;
        }
    }
    rv = setsockopt(s, IPPROTO_TCP, sockopt, (void *) &one, sizeof (one));
    if (rv != 0 && errno == ENOPROTOOPT) {
        rv = 0;
    } else {
        rv = 1;
    }
    close(s);
    return rv;
}

static void handleError(JNIEnv *env, jint rv, const char *errmsg) {
    if (rv < 0) {
        if (errno == ENOPROTOOPT) {
            JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                    "unsupported socket option");
        } else {
            JNU_ThrowByNameWithLastError(env, "java/net/SocketException", errmsg);
        }
    }
}

/*
 * Class:     jdk_net_MacOSXSocketOptions
 * Method:    keepAliveOptionsSupported0
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_MacOSXSocketOptions_keepAliveOptionsSupported0
(JNIEnv *env, jobject unused) {
    return socketOptionSupported(TCP_KEEPALIVE) && socketOptionSupported(TCP_KEEPCNT)
            && socketOptionSupported(TCP_KEEPINTVL);
}

/*
 * Class:     jdk_net_MacOSXSocketOptions
 * Method:    setTcpKeepAliveProbes0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_MacOSXSocketOptions_setTcpKeepAliveProbes0
(JNIEnv *env, jobject unused, jint fd, jint optval) {
    jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, &optval, sizeof (optval));
    handleError(env, rv, "set option TCP_KEEPCNT failed");
}

/*
 * Class:     jdk_net_MacOSXSocketOptions
 * Method:    setTcpKeepAliveTime0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_MacOSXSocketOptions_setTcpKeepAliveTime0
(JNIEnv *env, jobject unused, jint fd, jint optval) {
    jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPALIVE, &optval, sizeof (optval));
    handleError(env, rv, "set option TCP_KEEPALIVE failed");// mac TCP_KEEPIDLE ->TCP_KEEPALIVE
}

/*
 * Class:     jdk_net_MacOSXSocketOptions
 * Method:    setTcpKeepAliveIntvl0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_MacOSXSocketOptions_setTcpKeepAliveIntvl0
(JNIEnv *env, jobject unused, jint fd, jint optval) {
    jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, &optval, sizeof (optval));
    handleError(env, rv, "set option TCP_KEEPINTVL failed");
}

/*
 * Class:     jdk_net_MacOSXSocketOptions
 * Method:    getTcpKeepAliveProbes0
 * Signature: (I)I;
 */
JNIEXPORT jint JNICALL Java_jdk_net_MacOSXSocketOptions_getTcpKeepAliveProbes0
(JNIEnv *env, jobject unused, jint fd) {
    jint optval, rv;
    socklen_t sz = sizeof (optval);
    rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, &optval, &sz);
    handleError(env, rv, "get option TCP_KEEPCNT failed");
    return optval;
}

/*
 * Class:     jdk_net_MacOSXSocketOptions
 * Method:    getSoPeerCred0
 * Signature: (I)L
 */
JNIEXPORT jlong JNICALL Java_jdk_net_MacOSXSocketOptions_getSoPeerCred0
  (JNIEnv *env, jclass clazz, jint fd) {

    jint rv;
    int uid, gid;
    rv = getpeereid(fd, (uid_t *)&uid, (gid_t *)&gid);
    handleError(env, rv, "get peer eid failed");
    if (rv == -1) {
        uid = gid = -1;
    }
    return (((long)uid) << 32) | (gid & 0xffffffffL);
}


/*
 * Class:     jdk_net_MacOSXSocketOptions
 * Method:    getTcpKeepAliveTime0
 * Signature: (I)I;
 */
JNIEXPORT jint JNICALL Java_jdk_net_MacOSXSocketOptions_getTcpKeepAliveTime0
(JNIEnv *env, jobject unused, jint fd) {
    jint optval, rv;
    socklen_t sz = sizeof (optval);
    rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPALIVE, &optval, &sz);
    handleError(env, rv, "get option TCP_KEEPALIVE failed");// mac TCP_KEEPIDLE ->TCP_KEEPALIVE
    return optval;
}

/*
 * Class:     jdk_net_MacOSXSocketOptions
 * Method:    getTcpKeepAliveIntvl0
 * Signature: (I)I;
 */
JNIEXPORT jint JNICALL Java_jdk_net_MacOSXSocketOptions_getTcpKeepAliveIntvl0
(JNIEnv *env, jobject unused, jint fd) {
    jint optval, rv;
    socklen_t sz = sizeof (optval);
    rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, &optval, &sz);
    handleError(env, rv, "get option TCP_KEEPINTVL failed");
    return optval;
}

/*
 * Class:     jdk_net_MacOSXSocketOptions
 * Method:    ipDontFragmentSupported0
 * Signature: ()Z;
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_MacOSXSocketOptions_ipDontFragmentSupported0
(JNIEnv *env, jobject unused) {
    jint rv, fd, value;
    fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd != -1) {
        value = 1;
        rv = setsockopt(fd, IPPROTO_IP, IP_DONTFRAG, &value, sizeof(value));
        close(fd);
        if (rv == -1) {
            return JNI_FALSE;
        }
    }
    fd = socket(AF_INET6, SOCK_DGRAM, 0);
    if (fd != -1) {
        value = 1;
        rv = setsockopt(fd, IPPROTO_IPV6, IPV6_DONTFRAG, &value, sizeof(value));
        close(fd);
        if (rv == -1) {
            return JNI_FALSE;
        }
    }
    return JNI_TRUE;
}

/*
 * Class:     jdk_net_MacOSXSocketOptions
 * Method:    setIpDontFragment0
 * Signature: (IZZ)V
 */
JNIEXPORT void JNICALL Java_jdk_net_MacOSXSocketOptions_setIpDontFragment0
(JNIEnv *env, jobject unused, jint fd, jboolean optval, jboolean isIPv6) {
    jint rv;
    jint value = optval ? 1 : 0;

    if (!isIPv6) {
        rv = setsockopt(fd, IPPROTO_IP, IP_DONTFRAG, &value, sizeof(value));
    } else {
        rv = setsockopt(fd, IPPROTO_IPV6, IPV6_DONTFRAG, &value, sizeof(value));
    }
    handleError(env, rv, "set option IP_DONTFRAGMENT failed");
}

/*
 * Class:     jdk_net_MacOSXSocketOptions
 * Method:    getIpDontFragment0
 * Signature: (IZ)Z;
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_MacOSXSocketOptions_getIpDontFragment0
(JNIEnv *env, jobject unused, jint fd, jboolean isIPv6) {
    jint optval, rv;
    socklen_t sz = sizeof (optval);
    if (!isIPv6) {
        rv = getsockopt(fd, IPPROTO_IP, IP_DONTFRAG, &optval, &sz);
    } else {
        rv = getsockopt(fd, IPPROTO_IPV6, IPV6_DONTFRAG, &optval, &sz);
    }
    handleError(env, rv, "get option IP_DONTFRAGMENT failed");
    return optval;
}
