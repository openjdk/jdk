/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include <ws2tcpip.h>
#include <afunix.h>

#include "jni.h"

/* Defined in IOUtil.c */

JNIEXPORT jint fdval(JNIEnv *env, jobject fdo);
JNIEXPORT void setfdval(JNIEnv *env, jobject fdo, jint val);
JNIEXPORT jlong handleval(JNIEnv *env, jobject fdo);
JNIEXPORT jint convertReturnVal(JNIEnv *env, jint n, jboolean r);
JNIEXPORT jlong convertLongReturnVal(JNIEnv *env, jlong n, jboolean r);

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

/* Defined in UnixDomainSockets.c */

jbyteArray sockaddrToUnixAddressBytes(JNIEnv *env, struct sockaddr_un *sa, socklen_t len);

jint unixSocketAddressToSockaddr(JNIEnv *env, jbyteArray uaddr,
                                struct sockaddr_un *sa, int *len);
