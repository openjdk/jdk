/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include "nio.h"
#include "nio_util.h"
#include "net_util.h"

#include "java_net_InetAddress.h"
#include "sun_nio_ch_Net.h"
#include "sun_nio_ch_PollArrayWrapper.h"

jbyteArray sockaddrToUnixAddressBytes(JNIEnv *env, struct sockaddr_un *sa, socklen_t len) {

    if (sa->sun_family == AF_UNIX) {
        int namelen = (int)strlen(sa->sun_path);
        jbyteArray name = (*env)->NewByteArray(env, namelen);
        if (name != NULL) {
            (*env)->SetByteArrayRegion(env, name, 0, namelen, (jbyte*)sa->sun_path);
            if ((*env)->ExceptionOccurred(env)) {
                return NULL;
            }
        }
        return name;
    }
    return NULL;
}

jint unixSocketAddressToSockaddr(JNIEnv *env, jbyteArray addr, struct sockaddr_un *sa, int *len)
{
    memset(sa, 0, sizeof(struct sockaddr_un));
    sa->sun_family = AF_UNIX;
    if (addr == 0L) {
        /* Do explicit bind on Windows */
        *len = (int)(offsetof(struct sockaddr_un, sun_path));
        return 0;
    }
    int ret;
    jboolean isCopy;
    char *pname = (*env)->GetByteArrayElements(env, addr, &isCopy);
    if (pname == NULL) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Unix domain path not present");
        return 1;
    }

    size_t name_len = (size_t)(*env)->GetArrayLength(env, addr);
    if (name_len > MAX_UNIX_DOMAIN_PATH_LEN) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Unix domain path too long");
        ret=1;
    } else {
        strncpy(sa->sun_path, pname, name_len);
        *len = (int)(offsetof(struct sockaddr_un, sun_path) + name_len);
        ret = 0;
    }
    (*env)->ReleaseByteArrayElements(env, addr, pname, JNI_ABORT);
    return ret;
}


JNIEXPORT jboolean JNICALL
Java_sun_nio_ch_UnixDomainSockets_socketSupported(JNIEnv *env, jclass cl)
{
    SOCKET fd = socket(PF_UNIX, SOCK_STREAM, 0);
    if (fd == INVALID_SOCKET) {
        return JNI_FALSE;
    }
    closesocket(fd);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixDomainSockets_maxNameLen0(JNIEnv *env, jclass cl)
{
    return MAX_UNIX_DOMAIN_PATH_LEN - 1;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixDomainSockets_socket0(JNIEnv *env, jclass cl)
{
    SOCKET fd = socket(PF_UNIX, SOCK_STREAM, 0);
    if (fd == INVALID_SOCKET) {
        return handleSocketError(env, WSAGetLastError());
    }
    return (int)fd;
}

/**
 * Windows does not support auto bind. So, the windows version of unixSocketAddressToSockaddr
 * looks out for a null 'uaddr' and handles it specially
 */
JNIEXPORT void JNICALL
Java_sun_nio_ch_UnixDomainSockets_bind0(JNIEnv *env, jclass clazz, jobject fdo, jbyteArray addr)
{
    struct sockaddr_un sa;
    int sa_len = 0;
    int rv = 0;

    if (unixSocketAddressToSockaddr(env, addr, &sa, &sa_len) != 0)
        return;

    int fd = fdval(env, fdo);

    rv = bind(fdval(env, fdo), (struct sockaddr *)&sa, sa_len);
    if (rv != 0) {
        int err = WSAGetLastError();
        NET_ThrowNew(env, err, "bind");
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixDomainSockets_connect0(JNIEnv *env, jclass clazz, jobject fdo, jbyteArray addr)
{
    struct sockaddr_un sa;
    int sa_len = 0;
    int rv;

    if (unixSocketAddressToSockaddr(env, addr, &sa, &sa_len) != 0) {
        return IOS_THROWN;
    }

    rv = connect(fdval(env, fdo), (const struct sockaddr *)&sa, sa_len);
    if (rv != 0) {
        int err = WSAGetLastError();
        if (err == WSAEINPROGRESS || err == WSAEWOULDBLOCK) {
            return IOS_UNAVAILABLE;
        }
        NET_ThrowNew(env, err, "connect");
        return IOS_THROWN;
    }
    return 1;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixDomainSockets_accept0(JNIEnv *env, jclass clazz, jobject fdo, jobject newfdo,
                           jobjectArray usaa)
{
    jint fd = fdval(env, fdo);
    jint newfd;
    struct sockaddr_un sa;
    socklen_t sa_len = sizeof(sa);
    jobject usa;

    memset((char *)&sa, 0, sizeof(sa));
    newfd = (jint) accept(fd, (struct sockaddr *)&sa, &sa_len);
    if (newfd == INVALID_SOCKET) {
        int theErr = (jint)WSAGetLastError();
        if (theErr == WSAEWOULDBLOCK) {
            return IOS_UNAVAILABLE;
        }
        JNU_ThrowIOExceptionWithLastError(env, "Accept failed");
        return IOS_THROWN;
    }

    SetHandleInformation((HANDLE)(UINT_PTR)newfd, HANDLE_FLAG_INHERIT, 0);
    setfdval(env, newfdo, newfd);

    usa = sockaddrToUnixAddressBytes(env, &sa, sa_len);
    CHECK_NULL_RETURN(usa, IOS_THROWN);

    (*env)->SetObjectArrayElement(env, usaa, 0, usa);

    return 1;
}

JNIEXPORT jbyteArray JNICALL
Java_sun_nio_ch_UnixDomainSockets_localAddress0(JNIEnv *env, jclass clazz, jobject fdo)
{
    struct sockaddr_un sa;
    int sa_len = sizeof(sa);

    if (getsockname(fdval(env, fdo), (struct sockaddr *)&sa, &sa_len) == SOCKET_ERROR) {
        JNU_ThrowIOExceptionWithLastError(env, "getsockname");
        return NULL;
    }
    return sockaddrToUnixAddressBytes(env, &sa, sa_len);
}

JNIEXPORT jbyteArray JNICALL
Java_sun_nio_ch_UnixDomainSockets_remoteAddress0(JNIEnv *env, jclass clazz, jobject fdo)
{
    struct sockaddr_un sa;
    int sa_len = sizeof(sa);

    if (getpeername(fdval(env, fdo), (struct sockaddr *)&sa, &sa_len) == SOCKET_ERROR) {
        JNU_ThrowIOExceptionWithLastError(env, "getpeername");
        return NULL;
    }
    return sockaddrToUnixAddressBytes(env, &sa, sa_len);
}
