/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include <winsock2.h>

#include "jni.h"

/**
 * The maximum buffer size for WSASend/WSARecv. Microsoft recommendation for
 * blocking operations is to use buffers no larger than 64k. We need the
 * maximum to be less than 128k to support asynchronous close on Windows
 * Server 2003 and newer editions of Windows.
 */
#define MAX_BUFFER_SIZE             ((128*1024)-1)

jint fdval(JNIEnv *env, jobject fdo);
jlong handleval(JNIEnv *env, jobject fdo);
jint convertReturnVal(JNIEnv *env, jint n, jboolean r);
jlong convertLongReturnVal(JNIEnv *env, jlong n, jboolean r);
jboolean purgeOutstandingICMP(JNIEnv *env, jclass clazz, jint fd);
jint handleSocketError(JNIEnv *env, int errorValue);

#ifdef _WIN64

struct iovec {
    jlong  iov_base;
    jint  iov_len;
};

#else

struct iovec {
    jint  iov_base;
    jint  iov_len;
};

#endif

#ifndef POLLIN
    /* WSAPoll()/WSAPOLLFD and the corresponding constants are only defined   */
    /* in Windows Vista / Windows Server 2008 and later. If we are on an      */
    /* older release we just use the Solaris constants as this was previously */
    /* done in PollArrayWrapper.java.                                         */
    #define POLLIN       0x0001
    #define POLLOUT      0x0004
    #define POLLERR      0x0008
    #define POLLHUP      0x0010
    #define POLLNVAL     0x0020
    #define POLLCONN     0x0002
#else
    /* POLLCONN must not equal any of the other constants (see winsock2.h).   */
    #define POLLCONN     0x2000
#endif
