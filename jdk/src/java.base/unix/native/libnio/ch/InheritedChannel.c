/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>
#include <fcntl.h>

#include "jni.h"

#include "jni.h"
#include "jni_util.h"
#include "net_util.h"

#include "sun_nio_ch_InheritedChannel.h"

static int matchFamily(struct sockaddr *sa) {
    int family = sa->sa_family;
#ifdef AF_INET6
    if (ipv6_available()) {
        return (family == AF_INET6);
    }
#endif
    return (family == AF_INET);
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_InheritedChannel_initIDs(JNIEnv *env, jclass cla)
{
    /* Initialize InetAddress IDs before later use of NET_XXX functions */
    initInetAddressIDs(env);
}

JNIEXPORT jobject JNICALL
Java_sun_nio_ch_InheritedChannel_peerAddress0(JNIEnv *env, jclass cla, jint fd)
{
    struct sockaddr *sa;
    socklen_t sa_len;
    jobject remote_ia = NULL;
    jint remote_port;

    NET_AllocSockaddr(&sa, (int *)&sa_len);
    if (getpeername(fd, sa, &sa_len) == 0) {
        if (matchFamily(sa)) {
            remote_ia = NET_SockaddrToInetAddress(env, sa, (int *)&remote_port);
        }
    }
    free((void *)sa);

    return remote_ia;
}


JNIEXPORT jint JNICALL
Java_sun_nio_ch_InheritedChannel_peerPort0(JNIEnv *env, jclass cla, jint fd)
{
    struct sockaddr *sa;
    socklen_t sa_len;
    jint remote_port = -1;

    NET_AllocSockaddr(&sa, (int *)&sa_len);
    if (getpeername(fd, sa, &sa_len) == 0) {
        if (matchFamily(sa)) {
            NET_SockaddrToInetAddress(env, sa, (int *)&remote_port);
        }
    }
    free((void *)sa);

    return remote_port;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_InheritedChannel_soType0(JNIEnv *env, jclass cla, jint fd)
{
    int sotype;
    socklen_t arglen=sizeof(sotype);
    if (getsockopt(fd, SOL_SOCKET, SO_TYPE, (void *)&sotype, &arglen) == 0) {
        if (sotype == SOCK_STREAM)
            return sun_nio_ch_InheritedChannel_SOCK_STREAM;
        if (sotype == SOCK_DGRAM)
            return sun_nio_ch_InheritedChannel_SOCK_DGRAM;
    }
    return sun_nio_ch_InheritedChannel_UNKNOWN;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_InheritedChannel_dup(JNIEnv *env, jclass cla, jint fd)
{
   int newfd = dup(fd);
   if (newfd < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "dup failed");
   }
   return (jint)newfd;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_InheritedChannel_dup2(JNIEnv *env, jclass cla, jint fd, jint fd2)
{
   if (dup2(fd, fd2) < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "dup2 failed");
   }
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_InheritedChannel_open0(JNIEnv *env, jclass cla, jstring path, jint oflag)
{
    const char* str;
    int oflag_actual;

    /* convert to OS specific value */
    switch (oflag) {
        case sun_nio_ch_InheritedChannel_O_RDWR :
            oflag_actual = O_RDWR;
            break;
        case sun_nio_ch_InheritedChannel_O_RDONLY :
            oflag_actual = O_RDONLY;
            break;
        case sun_nio_ch_InheritedChannel_O_WRONLY :
            oflag_actual = O_WRONLY;
            break;
        default :
            JNU_ThrowInternalError(env, "Unrecognized file mode");
            return -1;
    }

    str = JNU_GetStringPlatformChars(env, path, NULL);
    if (str == NULL) {
        return (jint)-1;
    } else {
        int fd = open(str, oflag_actual);
        if (fd < 0) {
            JNU_ThrowIOExceptionWithLastError(env, str);
        }
        JNU_ReleaseStringPlatformChars(env, path, str);
        return (jint)fd;
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_InheritedChannel_close0(JNIEnv *env, jclass cla, jint fd)
{
    if (close(fd) < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "close failed");
    }
}
