/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
#include <netinet/in.h>
#include "jni_util.h"

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
 * Class:     jdk_net_BsdSocketOptions
 * Method:    keepAliveOptionsSupported0
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_BsdSocketOptions_keepAliveOptionsSupported0
(JNIEnv *env, jobject unused) {
#ifdef __OpenBSD__
    return JNI_FALSE;
#else
    return socketOptionSupported(TCP_KEEPIDLE) && socketOptionSupported(TCP_KEEPCNT)
            && socketOptionSupported(TCP_KEEPINTVL);
#endif
}

/*
 * Class:     jdk_net_BsdSocketOptions
 * Method:    setTcpKeepAliveProbes0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_BsdSocketOptions_setTcpKeepAliveProbes0
(JNIEnv *env, jobject unused, jint fd, jint optval) {
#ifdef __OpenBSD__
    JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                    "unsupported socket option");
#else
    jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, &optval, sizeof (optval));
    handleError(env, rv, "set option TCP_KEEPCNT failed");
#endif
}

/*
 * Class:     jdk_net_BsdSocketOptions
 * Method:    setTcpKeepAliveTime0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_BsdSocketOptions_setTcpKeepAliveTime0
(JNIEnv *env, jobject unused, jint fd, jint optval) {
#ifdef __OpenBSD__
    JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                    "unsupported socket option");
#else
    jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPIDLE, &optval, sizeof (optval));
    handleError(env, rv, "set option TCP_KEEPIDLE failed");
#endif
}

/*
 * Class:     jdk_net_BsdSocketOptions
 * Method:    getSoPeerCred0
 * Signature: (I)L
 */
JNIEXPORT jlong JNICALL Java_jdk_net_BsdSocketOptions_getSoPeerCred0
  (JNIEnv *env, jclass clazz, jint fd) {

    jint rv;
    int uid, gid;
    rv = getpeereid(fd, (uid_t *)&uid, (gid_t *)&gid);
    handleError(env, rv, "get peer eid failed");
    if (rv == -1) {
        uid = gid = -1;
    }
    return (((int64_t)uid) << 32) | ((int64_t)gid & 0xffffffffL);
}

/*
 * Class:     jdk_net_BsdSocketOptions
 * Method:    setTcpKeepAliveIntvl0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_BsdSocketOptions_setTcpKeepAliveIntvl0
(JNIEnv *env, jobject unused, jint fd, jint optval) {
#ifdef __OpenBSD__
    JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                    "unsupported socket option");
#else
    jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, &optval, sizeof (optval));
    handleError(env, rv, "set option TCP_KEEPINTVL failed");
#endif
}

/*
 * Class:     jdk_net_BsdSocketOptions
 * Method:    getTcpKeepAliveProbes0
 * Signature: (I)I;
 */
JNIEXPORT jint JNICALL Java_jdk_net_BsdSocketOptions_getTcpKeepAliveProbes0
(JNIEnv *env, jobject unused, jint fd) {
#ifdef __OpenBSD__
    JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                    "unsupported socket option");
    return -1;
#else
    jint optval, rv;
    socklen_t sz = sizeof (optval);
    rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, &optval, &sz);
    handleError(env, rv, "get option TCP_KEEPCNT failed");
    return optval;
#endif
}

/*
 * Class:     jdk_net_BsdSocketOptions
 * Method:    getTcpKeepAliveTime0
 * Signature: (I)I;
 */
JNIEXPORT jint JNICALL Java_jdk_net_BsdSocketOptions_getTcpKeepAliveTime0
(JNIEnv *env, jobject unused, jint fd) {
#ifdef __OpenBSD__
    JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                    "unsupported socket option");
    return -1;
#else
    jint optval, rv;
    socklen_t sz = sizeof (optval);
    rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPIDLE, &optval, &sz);
    handleError(env, rv, "get option TCP_KEEPIDLE failed");
    return optval;
#endif
}

/*
 * Class:     jdk_net_BsdSocketOptions
 * Method:    getTcpKeepAliveIntvl0
 * Signature: (I)I;
 */
JNIEXPORT jint JNICALL Java_jdk_net_BsdSocketOptions_getTcpKeepAliveIntvl0
(JNIEnv *env, jobject unused, jint fd) {
#ifdef __OpenBSD__
    JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                    "unsupported socket option");
    return -1;
#else
    jint optval, rv;
    socklen_t sz = sizeof (optval);
    rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, &optval, &sz);
    handleError(env, rv, "get option TCP_KEEPINTVL failed");
    return optval;
#endif
}
