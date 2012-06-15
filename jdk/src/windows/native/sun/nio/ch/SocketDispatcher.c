/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include <ctype.h>
#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include "sun_nio_ch_SocketDispatcher.h"
#include "nio.h"
#include "nio_util.h"


/**************************************************************
 * SocketDispatcher.c
 */

JNIEXPORT jint JNICALL
Java_sun_nio_ch_SocketDispatcher_read0(JNIEnv *env, jclass clazz, jobject fdo,
                                      jlong address, jint len)
{
    /* set up */
    int i = 0;
    DWORD read = 0;
    DWORD flags = 0;
    jint fd = fdval(env, fdo);
    WSABUF buf;

    /* limit size */
    if (len > MAX_BUFFER_SIZE)
        len = MAX_BUFFER_SIZE;

    /* destination buffer and size */
    buf.buf = (char *)address;
    buf.len = (u_long)len;

    /* read into the buffers */
    i = WSARecv((SOCKET)fd, /* Socket */
            &buf,           /* pointers to the buffers */
            (DWORD)1,       /* number of buffers to process */
            &read,          /* receives number of bytes read */
            &flags,         /* no flags */
            0,              /* no overlapped sockets */
            0);             /* no completion routine */

    if (i == SOCKET_ERROR) {
        int theErr = (jint)WSAGetLastError();
        if (theErr == WSAEWOULDBLOCK) {
            return IOS_UNAVAILABLE;
        }
        JNU_ThrowIOExceptionWithLastError(env, "Read failed");
        return IOS_THROWN;
    }

    return convertReturnVal(env, (jint)read, JNI_TRUE);
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_SocketDispatcher_readv0(JNIEnv *env, jclass clazz, jobject fdo,
                                       jlong address, jint len)
{
    /* set up */
    int i = 0;
    DWORD read = 0;
    DWORD flags = 0;
    jint fd = fdval(env, fdo);
    struct iovec *iovp = (struct iovec *)address;
    WSABUF *bufs = malloc(len * sizeof(WSABUF));
    jint rem = MAX_BUFFER_SIZE;

    if (bufs == 0) {
        JNU_ThrowOutOfMemoryError(env, 0);
        return IOS_THROWN;
    }

    if ((isNT() == JNI_FALSE) && (len > 16)) {
        len = 16;
    }

    /* copy iovec into WSABUF */
    for(i=0; i<len; i++) {
        jint iov_len = iovp[i].iov_len;
        if (iov_len > rem)
            iov_len = rem;
        bufs[i].buf = (char *)iovp[i].iov_base;
        bufs[i].len = (u_long)iov_len;
        rem -= iov_len;
        if (rem == 0) {
            len = i+1;
            break;
        }
    }

    /* read into the buffers */
    i = WSARecv((SOCKET)fd, /* Socket */
            bufs,           /* pointers to the buffers */
            (DWORD)len,     /* number of buffers to process */
            &read,          /* receives number of bytes read */
            &flags,         /* no flags */
            0,              /* no overlapped sockets */
            0);             /* no completion routine */

    /* clean up */
    free(bufs);

    if (i != 0) {
        int theErr = (jint)WSAGetLastError();
        if (theErr == WSAEWOULDBLOCK) {
            return IOS_UNAVAILABLE;
        }
        JNU_ThrowIOExceptionWithLastError(env, "Vector read failed");
        return IOS_THROWN;
    }

    return convertLongReturnVal(env, (jlong)read, JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_SocketDispatcher_write0(JNIEnv *env, jclass clazz, jobject fdo,
                                       jlong address, jint total)
{
    /* set up */
    int i = 0;
    DWORD written = 0;
    jint count = 0;
    jint fd = fdval(env, fdo);
    WSABUF buf;

    do {
        /* limit size */
        jint len = total - count;
        if (len > MAX_BUFFER_SIZE)
            len = MAX_BUFFER_SIZE;

        /* copy iovec into WSABUF */
        buf.buf = (char *)address;
        buf.len = (u_long)len;

        /* write from the buffer */
        i = WSASend((SOCKET)fd,     /* Socket */
                    &buf,           /* pointers to the buffers */
                    (DWORD)1,       /* number of buffers to process */
                    &written,       /* receives number of bytes written */
                    0,              /* no flags */
                    0,              /* no overlapped sockets */
                    0);             /* no completion routine */

        if (i == SOCKET_ERROR) {
            if (count > 0) {
                /* can't throw exception when some bytes have been written */
                break;
            } else {
               int theErr = (jint)WSAGetLastError();
               if (theErr == WSAEWOULDBLOCK) {
                   return IOS_UNAVAILABLE;
               }
               JNU_ThrowIOExceptionWithLastError(env, "Write failed");
               return IOS_THROWN;
            }
        }

        count += written;
        address += written;

    } while ((count < total) && (written == MAX_BUFFER_SIZE));

    return count;
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_SocketDispatcher_writev0(JNIEnv *env, jclass clazz,
                                         jobject fdo, jlong address, jint len)
{
    /* set up */
    int i = 0;
    DWORD written = 0;
    jint fd = fdval(env, fdo);
    struct iovec *iovp = (struct iovec *)address;
    WSABUF *bufs = malloc(len * sizeof(WSABUF));
    jint rem = MAX_BUFFER_SIZE;

    if (bufs == 0) {
        JNU_ThrowOutOfMemoryError(env, 0);
        return IOS_THROWN;
    }

    if ((isNT() == JNI_FALSE) && (len > 16)) {
        len = 16;
    }

    /* copy iovec into WSABUF */
    for(i=0; i<len; i++) {
        jint iov_len = iovp[i].iov_len;
        if (iov_len > rem)
            iov_len = rem;
        bufs[i].buf = (char *)iovp[i].iov_base;
        bufs[i].len = (u_long)iov_len;
        rem -= iov_len;
        if (rem == 0) {
            len = i+1;
            break;
        }
    }

    /* read into the buffers */
    i = WSASend((SOCKET)fd, /* Socket */
            bufs,           /* pointers to the buffers */
            (DWORD)len,     /* number of buffers to process */
            &written,       /* receives number of bytes written */
            0,              /* no flags */
            0,              /* no overlapped sockets */
            0);             /* no completion routine */

    /* clean up */
    free(bufs);

    if (i != 0) {
        int theErr = (jint)WSAGetLastError();
        if (theErr == WSAEWOULDBLOCK) {
            return IOS_UNAVAILABLE;
        }
        JNU_ThrowIOExceptionWithLastError(env, "Vector write failed");
        return IOS_THROWN;
    }

    return convertLongReturnVal(env, (jlong)written, JNI_FALSE);
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_SocketDispatcher_preClose0(JNIEnv *env, jclass clazz,
                                           jobject fdo)
{
    jint fd = fdval(env, fdo);
    struct linger l;
    int len = sizeof(l);
    if (getsockopt(fd, SOL_SOCKET, SO_LINGER, (char *)&l, &len) == 0) {
        if (l.l_onoff == 0) {
            WSASendDisconnect(fd, NULL);
        }
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_SocketDispatcher_close0(JNIEnv *env, jclass clazz,
                                         jobject fdo)
{
    jint fd = fdval(env, fdo);
    if (closesocket(fd) == SOCKET_ERROR) {
        JNU_ThrowIOExceptionWithLastError(env, "Socket close failed");
    }
}
