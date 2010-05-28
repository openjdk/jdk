/*
 * Copyright (c) 2000, 2002, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>
#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include "sun_nio_ch_IOUtil.h"
#include "nio.h"
#include "nio_util.h"

static jfieldID fd_fdID;        /* for jint 'fd' in java.io.FileDescriptor */


JNIEXPORT void JNICALL
Java_sun_nio_ch_IOUtil_initIDs(JNIEnv *env, jclass clazz)
{
    clazz = (*env)->FindClass(env, "java/io/FileDescriptor");
    fd_fdID = (*env)->GetFieldID(env, clazz, "fd", "I");
}

JNIEXPORT jboolean JNICALL
Java_sun_nio_ch_IOUtil_randomBytes(JNIEnv *env, jclass clazz,
                                  jbyteArray randArray)
{
    JNU_ThrowByName(env, "java/lang/UnsupportedOperationException", NULL);
    return JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_IOUtil_fdVal(JNIEnv *env, jclass clazz, jobject fdo)
{
    return (*env)->GetIntField(env, fdo, fd_fdID);
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_IOUtil_setfdVal(JNIEnv *env, jclass clazz, jobject fdo, jint val)
{
    (*env)->SetIntField(env, fdo, fd_fdID, val);
}

static int
configureBlocking(int fd, jboolean blocking)
{
    int flags = fcntl(fd, F_GETFL);

    if ((blocking == JNI_FALSE) && !(flags & O_NONBLOCK))
        return fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    else if ((blocking == JNI_TRUE) && (flags & O_NONBLOCK))
        return fcntl(fd, F_SETFL, flags & ~O_NONBLOCK);
    return 0;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_IOUtil_configureBlocking(JNIEnv *env, jclass clazz,
                                         jobject fdo, jboolean blocking)
{
    if (configureBlocking(fdval(env, fdo), blocking) < 0)
        JNU_ThrowIOExceptionWithLastError(env, "Configure blocking failed");
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_IOUtil_initPipe(JNIEnv *env, jobject this,
                                    jintArray intArray, jboolean block)
{
    int fd[2];
    jint *ptr = 0;

    if (pipe(fd) < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Pipe failed");
        return;
    }
    if (block == JNI_FALSE) {
        if ((configureBlocking(fd[0], JNI_FALSE) < 0)
            || (configureBlocking(fd[1], JNI_FALSE) < 0)) {
            JNU_ThrowIOExceptionWithLastError(env, "Configure blocking failed");
        }
    }
    ptr = (*env)->GetPrimitiveArrayCritical(env, intArray, 0);
    ptr[0] = fd[0];
    ptr[1] = fd[1];
    (*env)->ReleasePrimitiveArrayCritical(env, intArray, ptr, 0);
}

JNIEXPORT jboolean JNICALL
Java_sun_nio_ch_IOUtil_drain(JNIEnv *env, jclass cl, jint fd)
{
    char buf[128];
    int tn = 0;

    for (;;) {
        int n = read(fd, buf, sizeof(buf));
        tn += n;
        if ((n < 0) && (errno != EAGAIN))
            JNU_ThrowIOExceptionWithLastError(env, "Drain");
        if (n == (int)sizeof(buf))
            continue;
        return (tn > 0) ? JNI_TRUE : JNI_FALSE;
    }
}


/* Declared in nio_util.h for use elsewhere in NIO */

jint
convertReturnVal(JNIEnv *env, jint n, jboolean reading)
{
    if (n > 0) /* Number of bytes written */
        return n;
    if (n < 0) {
        if (errno == EAGAIN)
            return IOS_UNAVAILABLE;
        if (errno == EINTR)
            return IOS_INTERRUPTED;
    }
    if (n == 0) {
        if (reading) {
            return IOS_EOF; /* EOF is -1 in javaland */
        } else {
            return 0;
        }
    }
    JNU_ThrowIOExceptionWithLastError(env, "Read/write failed");
    return IOS_THROWN;
}

/* Declared in nio_util.h for use elsewhere in NIO */

jlong
convertLongReturnVal(JNIEnv *env, jlong n, jboolean reading)
{
    if (n > 0) /* Number of bytes written */
        return n;
    if (n < 0) {
        if (errno == EAGAIN)
            return IOS_UNAVAILABLE;
        if (errno == EINTR)
            return IOS_INTERRUPTED;
    }
    if (n == 0) {
        if (reading) {
            return IOS_EOF; /* EOF is -1 in javaland */
        } else {
            return 0;
        }
    }
    JNU_ThrowIOExceptionWithLastError(env, "Read/write failed");
    return IOS_THROWN;
}

jint
fdval(JNIEnv *env, jobject fdo)
{
    return (*env)->GetIntField(env, fdo, fd_fdID);
}
