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

#include <windows.h>
#include <winsock2.h>
#include <io.h>
#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"

#include "java_lang_Long.h"
#include "nio.h"
#include "nio_util.h"
#include "sun_nio_ch_IOUtil.h"

/* field id for jlong 'handle' in java.io.FileDescriptor used for file fds */
static jfieldID handle_fdID;

/* field id for jint 'fd' in java.io.FileDescriptor used for socket fds */
static jfieldID fd_fdID;

JNIEXPORT jboolean JNICALL
Java_sun_security_provider_NativeSeedGenerator_nativeGenerateSeed
(JNIEnv *env, jclass clazz, jbyteArray randArray);

/**************************************************************
 * static method to store field IDs in initializers
 */

JNIEXPORT void JNICALL
Java_sun_nio_ch_IOUtil_initIDs(JNIEnv *env, jclass clazz)
{
    CHECK_NULL(clazz = (*env)->FindClass(env, "java/io/FileDescriptor"));
    CHECK_NULL(fd_fdID = (*env)->GetFieldID(env, clazz, "fd", "I"));
    CHECK_NULL(handle_fdID = (*env)->GetFieldID(env, clazz, "handle", "J"));
}

/**************************************************************
 * IOUtil.c
 */
JNIEXPORT jint JNICALL
Java_sun_nio_ch_IOUtil_iovMax(JNIEnv *env, jclass this)
{
    return 16;
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_IOUtil_writevMax(JNIEnv *env, jclass this)
{
    return java_lang_Long_MAX_VALUE;
}

JNIEXPORT jint
convertReturnVal(JNIEnv *env, jint n, jboolean reading)
{
    if (n > 0) /* Number of bytes written */
        return n;
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

JNIEXPORT jlong
convertLongReturnVal(JNIEnv *env, jlong n, jboolean reading)
{
    if (n > 0) /* Number of bytes written */
        return n;
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

JNIEXPORT jint JNICALL
Java_sun_nio_ch_IOUtil_fdVal(JNIEnv *env, jclass clazz, jobject fdo)
{
    return fdval(env, fdo);
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_IOUtil_setfdVal(JNIEnv *env, jclass clazz, jobject fdo, jint val)
{
    setfdval(env, fdo, val);
}

/* Note: This function returns the int fd value from file descriptor.
   It is mostly used for sockets which should use the int fd value.
*/
JNIEXPORT jint
fdval(JNIEnv *env, jobject fdo)
{
    return (*env)->GetIntField(env, fdo, fd_fdID);
}

JNIEXPORT void
setfdval(JNIEnv *env, jobject fdo, jint val)
{
    (*env)->SetIntField(env, fdo, fd_fdID, val);
}

JNIEXPORT jlong
handleval(JNIEnv *env, jobject fdo)
{
    return (*env)->GetLongField(env, fdo, handle_fdID);
}
