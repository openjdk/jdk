/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

#include "nio.h"
#include "nio_util.h"
#include "sun_nio_ch_IOUtil.h"

/* field id for jlong 'handle' in java.io.FileDescriptor used for file fds */
static jfieldID handle_fdID;

/* field id for jint 'fd' in java.io.FileDescriptor used for socket fds */
static jfieldID fd_fdID;

/* false for 95/98/ME, true for NT/W2K */
static jboolean onNT = JNI_FALSE;

JNIEXPORT jboolean JNICALL
Java_sun_security_provider_NativeSeedGenerator_nativeGenerateSeed
(JNIEnv *env, jclass clazz, jbyteArray randArray);

/**************************************************************
 * static method to store field IDs in initializers
 */

JNIEXPORT void JNICALL
Java_sun_nio_ch_IOUtil_initIDs(JNIEnv *env, jclass clazz)
{
    OSVERSIONINFO ver;
    ver.dwOSVersionInfoSize = sizeof(ver);
    GetVersionEx(&ver);
    if (ver.dwPlatformId == VER_PLATFORM_WIN32_NT) {
        onNT = JNI_TRUE;
    }

    clazz = (*env)->FindClass(env, "java/io/FileDescriptor");
    fd_fdID = (*env)->GetFieldID(env, clazz, "fd", "I");
    handle_fdID = (*env)->GetFieldID(env, clazz, "handle", "J");
}

/**************************************************************
 * IOUtil.c
 */
JNIEXPORT jboolean JNICALL
Java_sun_nio_ch_IOUtil_randomBytes(JNIEnv *env, jclass clazz,
                                  jbyteArray randArray)
{
    return
        Java_sun_security_provider_NativeSeedGenerator_nativeGenerateSeed(env,
                                                                    clazz,
                                                                    randArray);
}

jint
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

jlong
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
    (*env)->SetIntField(env, fdo, fd_fdID, val);
}


#define SET_BLOCKING 0
#define SET_NONBLOCKING 1

JNIEXPORT void JNICALL
Java_sun_nio_ch_IOUtil_configureBlocking(JNIEnv *env, jclass clazz,
                                        jobject fdo, jboolean blocking)
{
    u_long argp;
    int result = 0;
    jint fd = fdval(env, fdo);

    if (blocking == JNI_FALSE) {
        argp = SET_NONBLOCKING;
    } else {
        argp = SET_BLOCKING;
        /* Blocking fd cannot be registered with EventSelect */
        WSAEventSelect(fd, NULL, 0);
    }
    result = ioctlsocket(fd, FIONBIO, &argp);
    if (result == SOCKET_ERROR) {
        int error = WSAGetLastError();
        handleSocketError(env, (jint)error);
    }
}

/* Note: Drain uses the int fd value. It is currently not called
   on windows.
*/
JNIEXPORT jboolean JNICALL
Java_sun_nio_ch_IOUtil_drain(JNIEnv *env, jclass cl, jint fd)
{
    DWORD read = 0;
    int totalRead = 0;
    BOOL result = 0;
    HANDLE h = (HANDLE)_get_osfhandle(fd);
    char buf[128];

    if (h == INVALID_HANDLE_VALUE) {
        JNU_ThrowIOExceptionWithLastError(env, "Read failed");
        return JNI_FALSE;
    }

    for (;;) {
        result = ReadFile(h,          /* File handle to read */
                          (LPVOID)&buf,    /* address to put data */
                          128,        /* number of bytes to read */
                          &read,      /* number of bytes read */
                          NULL);      /* no overlapped struct */

        if (result == 0) {
            int error = GetLastError();
            if (error == ERROR_NO_DATA) {
                return (totalRead > 0) ? JNI_TRUE : JNI_FALSE;
            }
            JNU_ThrowIOExceptionWithLastError(env, "Drain");
            return JNI_FALSE;
        }
        if (read > 0) {
            totalRead += read;
        } else {
            break;
        }
    }
    return (totalRead > 0) ? JNI_TRUE : JNI_FALSE;
}

/* Note: This function returns the int fd value from file descriptor.
   It is mostly used for sockets which should use the int fd value.
*/
jint
fdval(JNIEnv *env, jobject fdo)
{
    return (*env)->GetIntField(env, fdo, fd_fdID);
}

jlong
handleval(JNIEnv *env, jobject fdo)
{
    return (*env)->GetLongField(env, fdo, handle_fdID);
}

jboolean
isNT()
{
    return onNT;
}
