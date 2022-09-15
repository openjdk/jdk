/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
        if (errno == ENOPROTOOPT) {
            JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                    "unsupported socket option");
        } else {
            JNU_ThrowByNameWithLastError(env, "java/net/SocketException", errmsg);
        }
    }
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

