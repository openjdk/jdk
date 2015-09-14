/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include <sys/types.h>
#include <sys/socket.h>
#include <errno.h>

#if defined(__solaris__)
  #if !defined(PROTO_SDP)
    #define PROTO_SDP       257
  #endif
#elif defined(__linux__)
  #if !defined(AF_INET_SDP)
    #define AF_INET_SDP     27
  #endif
#endif

#include "jni.h"
#include "jni_util.h"
#include "net_util.h"

#define RESTARTABLE(_cmd, _result) do { \
  do { \
    _result = _cmd; \
  } while((_result == -1) && (errno == EINTR)); \
} while(0)


/**
 * Creates a SDP socket.
 */
static int create(JNIEnv* env)
{
    int s;

#if defined(__solaris__)
  #ifdef AF_INET6
    int domain = ipv6_available() ? AF_INET6 : AF_INET;
  #else
    int domain = AF_INET;
  #endif
    s = socket(domain, SOCK_STREAM, PROTO_SDP);
#elif defined(__linux__)
    /**
     * IPv6 not supported by SDP on Linux
     */
    if (ipv6_available()) {
        JNU_ThrowIOException(env, "IPv6 not supported");
        return -1;
    }
    s = socket(AF_INET_SDP, SOCK_STREAM, 0);
#else
    /* not supported on other platforms at this time */
    s = -1;
    errno = EPROTONOSUPPORT;
#endif

    if (s < 0)
        JNU_ThrowIOExceptionWithLastError(env, "socket");
    return s;
}

/**
 * Creates a SDP socket, returning file descriptor referencing the socket.
 */
JNIEXPORT jint JNICALL
Java_sun_net_sdp_SdpSupport_create0(JNIEnv *env, jclass cls)
{
    return create(env);
}

/**
 * Converts an existing file descriptor, that references an unbound TCP socket,
 * to SDP.
 */
JNIEXPORT void JNICALL
Java_sun_net_sdp_SdpSupport_convert0(JNIEnv *env, jclass cls, int fd)
{
    int s = create(env);
    if (s >= 0) {
        socklen_t len;
        int arg, res;
        struct linger linger;

        /* copy socket options that are relevant to SDP */
        len = sizeof(arg);
        if (getsockopt(fd, SOL_SOCKET, SO_REUSEADDR, (char*)&arg, &len) == 0)
            setsockopt(s, SOL_SOCKET, SO_REUSEADDR, (char*)&arg, len);
        len = sizeof(arg);
        if (getsockopt(fd, SOL_SOCKET, SO_OOBINLINE, (char*)&arg, &len) == 0)
            setsockopt(s, SOL_SOCKET, SO_OOBINLINE, (char*)&arg, len);
        len = sizeof(linger);
        if (getsockopt(fd, SOL_SOCKET, SO_LINGER, (void*)&linger, &len) == 0)
            setsockopt(s, SOL_SOCKET, SO_LINGER, (char*)&linger, len);

        RESTARTABLE(dup2(s, fd), res);
        if (res < 0)
            JNU_ThrowIOExceptionWithLastError(env, "dup2");
        res = close(s);
        if (res < 0 && !(*env)->ExceptionOccurred(env))
            JNU_ThrowIOExceptionWithLastError(env, "close");
    }
}
