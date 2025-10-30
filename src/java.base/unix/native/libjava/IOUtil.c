/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include <sys/resource.h>

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include "sun_nio_ch_IOUtil.h"
#include "java_lang_Integer.h"
#include "java_lang_Long.h"
#include "nio.h"
#include "nio_util.h"

static jfieldID fd_fdID;        /* for jint 'fd' in java.io.FileDescriptor */


JNIEXPORT void JNICALL
Java_sun_nio_ch_IOUtil_initIDs(JNIEnv *env, jclass clazz)
{
    CHECK_NULL(clazz = (*env)->FindClass(env, "java/io/FileDescriptor"));
    CHECK_NULL(fd_fdID = (*env)->GetFieldID(env, clazz, "fd", "I"));
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_IOUtil_fdVal(JNIEnv *env, jclass clazz, jobject fdo)
{
    return (*env)->GetIntField(env, fdo, fd_fdID);
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_IOUtil_setfdVal(JNIEnv *env, jclass clazz, jobject fdo, jint val)
{
    setfdval(env, fdo, val);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_IOUtil_iovMax(JNIEnv *env, jclass this)
{
    jlong iov_max = sysconf(_SC_IOV_MAX);
    if (iov_max == -1)
        iov_max = 16;
    return (jint)iov_max;
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_IOUtil_writevMax(JNIEnv *env, jclass this)
{
#if defined(MACOSX) || defined(__linux__)
    //
    // The man pages of writev() on both Linux and macOS specify this
    // constraint on the sum of all byte lengths in the iovec array:
    //
    // [EINVAL] The sum of the iov_len values in the iov array
    //          overflows a 32-bit integer.
    //
    // As of macOS 11 Big Sur, Darwin version 20, writev() started to
    // actually enforce the constraint which had been previously ignored.
    //
    // In practice on Linux writev() has been observed not to write more
    // than 0x7fff0000 (aarch64) or 0x7ffff000 (x64) bytes in one call.
    //
    return java_lang_Integer_MAX_VALUE;
#else
    return java_lang_Long_MAX_VALUE;
#endif
}

// Declared in nio_util.h for use elsewhere in NIO

JNIEXPORT jint
convertReturnVal(JNIEnv *env, jint n, jboolean reading)
{
    if (n > 0) // Number of bytes written
        return n;
    else if (n == 0) {
        if (reading) {
            return IOS_EOF; // EOF is -1 in javaland
        } else {
            return 0;
        }
    }
    else if (errno == EAGAIN || errno == EWOULDBLOCK)
        return IOS_UNAVAILABLE;
    else if (errno == EINTR)
        return IOS_INTERRUPTED;
    else {
        const char *msg = reading ? "Read failed" : "Write failed";
        JNU_ThrowIOExceptionWithLastError(env, msg);
        return IOS_THROWN;
    }
}

// Declared in nio_util.h for use elsewhere in NIO

JNIEXPORT jlong
convertLongReturnVal(JNIEnv *env, jlong n, jboolean reading)
{
    if (n > 0) // Number of bytes written
        return n;
    else if (n == 0) {
        if (reading) {
            return IOS_EOF; // EOF is -1 in javaland
        } else {
            return 0;
        }
    }
    else if (errno == EAGAIN || errno == EWOULDBLOCK)
        return IOS_UNAVAILABLE;
    else if (errno == EINTR)
        return IOS_INTERRUPTED;
    else {
        const char *msg = reading ? "Read failed" : "Write failed";
        JNU_ThrowIOExceptionWithLastError(env, msg);
        return IOS_THROWN;
    }
}

JNIEXPORT jint
fdval(JNIEnv *env, jobject fdo)
{
    return (*env)->GetIntField(env, fdo, fd_fdID);
}

JNIEXPORT void
setfdval(JNIEnv *env, jobject fdo, jint val) {
    (*env)->SetIntField(env, fdo, fd_fdID, val);
}
