/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
 #include <sys/uio.h>
 #include <unistd.h>

 #include "jni.h"
 #include "jni_util.h"
 #include "jlong.h"
 #include "nio.h"
 #include "nio_util.h"
 #include "sun_nio_ch_SocketDispatcher.h"

// MAX_SKIP_BUFFER_SIZE is used to determine the maximum buffer size to
// use when skipping.
static const ssize_t MAX_SKIP_BUFFER_SIZE = 4096;

 JNIEXPORT jint JNICALL
 Java_sun_nio_ch_SocketDispatcher_read0(JNIEnv *env, jclass clazz,
                                        jobject fdo, jlong address, jint len)
 {
     jint fd = fdval(env, fdo);
     void *buf = (void *)jlong_to_ptr(address);
     jint n = read(fd, buf, len);
     if ((n == -1) && (errno == ECONNRESET || errno == EPIPE)) {
         JNU_ThrowByName(env, "sun/net/ConnectionResetException", "Connection reset");
         return IOS_THROWN;
     } else {
         return convertReturnVal(env, n, JNI_TRUE);
     }
 }

 JNIEXPORT jlong JNICALL
 Java_sun_nio_ch_SocketDispatcher_readv0(JNIEnv *env, jclass clazz,
                                         jobject fdo, jlong address, jint len)
 {
     jint fd = fdval(env, fdo);
     struct iovec *iov = (struct iovec *)jlong_to_ptr(address);
     jlong n = readv(fd, iov, len);
     if ((n == -1) && (errno == ECONNRESET || errno == EPIPE)) {
         JNU_ThrowByName(env, "sun/net/ConnectionResetException", "Connection reset");
         return IOS_THROWN;
     } else {
         return convertLongReturnVal(env, n, JNI_TRUE);
     }
 }

JNIEXPORT jint JNICALL
Java_sun_nio_ch_SocketDispatcher_write0(JNIEnv *env, jclass clazz,
                                        jobject fdo, jlong address, jint len)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);

    return convertReturnVal(env, write(fd, buf, len), JNI_FALSE);
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_SocketDispatcher_writev0(JNIEnv *env, jclass clazz,
                                         jobject fdo, jlong address, jint len)
{
    jint fd = fdval(env, fdo);
    struct iovec *iov = (struct iovec *)jlong_to_ptr(address);
    return convertLongReturnVal(env, writev(fd, iov, len), JNI_FALSE);
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_SocketDispatcher_skip0(JNIEnv *env, jclass cl, jobject fdo, jlong n)
{
    if (n < 1)
        return 0;

    const jint fd = fdval(env, fdo);

    const long bs = n < MAX_SKIP_BUFFER_SIZE ? (long) n : MAX_SKIP_BUFFER_SIZE;
    char buf[bs];
    jlong tn = 0;

    for (;;) {
        const jlong remaining = n - tn;
        const ssize_t count = remaining < bs ? (ssize_t) remaining : bs;
        const ssize_t nr = read(fd, buf, count);
        if (nr < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                return tn;
            } else if (errno == EINTR) {
                return IOS_INTERRUPTED;
            } else {
                JNU_ThrowIOExceptionWithLastError(env, "read");
                return IOS_THROWN;
            }
        }
        if (nr > 0)
            tn += nr;
        if (nr == bs)
            continue;
        return tn;
    }
}
