/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include <WS2tcpip.h>

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"

static void handleError(JNIEnv *env, jint rv, const char *errmsg) {
    if (rv < 0) {
        int error = WSAGetLastError();
        if (error == WSAENOPROTOOPT) {
            JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                    "unsupported socket option");
        } else {
            JNU_ThrowByNameWithLastError(env, "java/net/SocketException", errmsg);
        }
    }
}

static jint socketOptionSupported(jint level, jint optname) {
    WSADATA wsaData;
    jint error = WSAStartup(MAKEWORD(2, 2), &wsaData);

    if (error != 0) {
        return 0;
    }

    SOCKET sock;
    jint one = 1;
    jint rv;
    socklen_t sz = sizeof(one);

    /* First try IPv6; fall back to IPv4. */
    sock = socket(PF_INET6, SOCK_STREAM, IPPROTO_TCP);
    if (sock == INVALID_SOCKET) {
        error = WSAGetLastError();
        if (error == WSAEPFNOSUPPORT || error == WSAEAFNOSUPPORT) {
            sock = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
        }
        if (sock == INVALID_SOCKET) {
            return 0;
        }
    }

    rv = getsockopt(sock, level, optname, (char*) &one, &sz);
    error = WSAGetLastError();

    if (rv != 0 && error == WSAENOPROTOOPT) {
        rv = 0;
    } else {
        rv = 1;
    }

    closesocket(sock);
    WSACleanup();

    return rv;
}

/*
 * Class:     jdk_net_WindowsSocketOptions
 * Method:    keepAliveOptionsSupported0
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_WindowsSocketOptions_keepAliveOptionsSupported0
(JNIEnv *env, jobject unused) {
    return socketOptionSupported(IPPROTO_TCP, TCP_KEEPIDLE) && socketOptionSupported(IPPROTO_TCP, TCP_KEEPCNT)
            && socketOptionSupported(IPPROTO_TCP, TCP_KEEPINTVL);
}

/*
 * Class:     jdk_net_WindowsSocketOptions
 * Method:    setIpDontFragment0
 * Signature: (IZZ)V
 */
JNIEXPORT void JNICALL Java_jdk_net_WindowsSocketOptions_setIpDontFragment0
(JNIEnv *env, jobject unused, jint fd, jboolean optval, jboolean isIPv6) {
    int rv, opt;

    if (!isIPv6) {
        opt = optval ? IP_PMTUDISC_DO : IP_PMTUDISC_DONT;
        rv = setsockopt(fd, IPPROTO_IP, IP_MTU_DISCOVER, (char *)&opt, sizeof(int));
        if (rv == SOCKET_ERROR && WSAGetLastError() == WSAENOPROTOOPT) {
            opt = optval;
            rv = setsockopt(fd, IPPROTO_IP, IP_DONTFRAGMENT, (char *)&opt, sizeof(int));
        }
    } else {
        opt = optval ? IP_PMTUDISC_DO : IP_PMTUDISC_DONT;
        rv = setsockopt(fd, IPPROTO_IPV6, IPV6_MTU_DISCOVER, (char *)&opt, sizeof(int));
        if (rv == SOCKET_ERROR && WSAGetLastError() == WSAENOPROTOOPT) {
            /* IPV6_MTU_DISCOVER not supported on W 2016 and older, can use old option */
            opt = optval;
            rv = setsockopt(fd, IPPROTO_IPV6, IPV6_DONTFRAG, (char *)&opt, sizeof(int));
        }
    }
    handleError(env, rv, "set option IP_DONTFRAGMENT failed");
}

/*
 * Class:     jdk_net_WindowsSocketOptions
 * Method:    getIpDontFragment0
 * Signature: (IZ)Z;
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_WindowsSocketOptions_getIpDontFragment0
(JNIEnv *env, jobject unused, jint fd, jboolean isIPv6) {
    int optval, rv, sz = sizeof(optval);

    if (!isIPv6) {
        rv = getsockopt(fd, IPPROTO_IP, IP_MTU_DISCOVER, (char *)&optval, &sz);
        if (rv == SOCKET_ERROR && WSAGetLastError() == WSAENOPROTOOPT) {
            sz = sizeof(optval);
            rv = getsockopt(fd, IPPROTO_IP, IP_DONTFRAGMENT, (char *)&optval, &sz);
            handleError(env, rv, "get option IP_DONTFRAGMENT failed");
            return optval;
        }
        handleError(env, rv, "get option IP_DONTFRAGMENT failed");
        return optval == IP_PMTUDISC_DO ? JNI_TRUE : JNI_FALSE;
    } else {
        rv = getsockopt(fd, IPPROTO_IPV6, IPV6_MTU_DISCOVER, (char *)&optval, &sz);
        if (rv == SOCKET_ERROR && WSAGetLastError() == WSAENOPROTOOPT) {
            sz = sizeof(optval);
            rv = getsockopt(fd, IPPROTO_IPV6, IPV6_DONTFRAG, (char *)&optval, &sz);
            handleError(env, rv, "get option IP_DONTFRAGMENT failed");
            return optval;
        }
        handleError(env, rv, "get option IP_DONTFRAGMENT failed");
        return optval == IP_PMTUDISC_DO ? JNI_TRUE : JNI_FALSE;
    }
}

/*
 * Class:     jdk_net_WindowsSocketOptions
 * Method:    setTcpKeepAliveProbes0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_WindowsSocketOptions_setTcpKeepAliveProbes0
(JNIEnv *env, jobject unused, jint fd, jint optval) {
    jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, (char*) &optval, sizeof(optval));
    handleError(env, rv, "set option TCP_KEEPCNT failed");
}

/*
 * Class:     jdk_net_WindowsSocketOptions
 * Method:    getTcpKeepAliveProbes0
 * Signature: (I)I;
 */
JNIEXPORT jint JNICALL Java_jdk_net_WindowsSocketOptions_getTcpKeepAliveProbes0
(JNIEnv *env, jobject unused, jint fd) {
    jint optval, rv;
    socklen_t sz = sizeof(optval);
    rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, (char*) &optval, &sz);
    handleError(env, rv, "get option TCP_KEEPCNT failed");
    return optval;
}

/*
 * Class:     jdk_net_WindowsSocketOptions
 * Method:    setTcpKeepAliveTime0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_WindowsSocketOptions_setTcpKeepAliveTime0
(JNIEnv *env, jobject unused, jint fd, jint optval) {
    jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPIDLE, (char*) &optval, sizeof(optval));
    handleError(env, rv, "set option TCP_KEEPIDLE failed");
}

/*
 * Class:     jdk_net_WindowsSocketOptions
 * Method:    getTcpKeepAliveTime0
 * Signature: (I)I;
 */
JNIEXPORT jint JNICALL Java_jdk_net_WindowsSocketOptions_getTcpKeepAliveTime0
(JNIEnv *env, jobject unused, jint fd) {
    jint optval, rv;
    socklen_t sz = sizeof(optval);
    rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPIDLE, (char*) &optval, &sz);
    handleError(env, rv, "get option TCP_KEEPIDLE failed");
    return optval;
}

/*
 * Class:     jdk_net_WindowsSocketOptions
 * Method:    setTcpKeepAliveIntvl0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_WindowsSocketOptions_setTcpKeepAliveIntvl0
(JNIEnv *env, jobject unused, jint fd, jint optval) {
    jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, (char*) &optval, sizeof(optval));
    handleError(env, rv, "set option TCP_KEEPINTVL failed");
}

/*
 * Class:     jdk_net_WindowsSocketOptions
 * Method:    getTcpKeepAliveIntvl0
 * Signature: (I)I;
 */
JNIEXPORT jint JNICALL Java_jdk_net_WindowsSocketOptions_getTcpKeepAliveIntvl0
(JNIEnv *env, jobject unused, jint fd) {
    jint optval, rv;
    socklen_t sz = sizeof(optval);
    rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, (char*) &optval, &sz);
    handleError(env, rv, "get option TCP_KEEPINTVL failed");
    return optval;
}

