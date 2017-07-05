/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#include <unistd.h>
#include <errno.h>

#include "jni.h"
#include "jni_util.h"
#include "jlong.h"
#include "jdk_internal_jimage_concurrent_ConcurrentPReader.h"

#ifdef _ALLBSD_SOURCE
  #define pread64 pread
#endif

#define RESTARTABLE(_cmd, _result) do { \
  do { \
    _result = _cmd; \
  } while((_result == -1) && (errno == EINTR)); \
} while(0)

static jfieldID fd_fdID;

JNIEXPORT void JNICALL
Java_jdk_internal_jimage_concurrent_ConcurrentPReader_initIDs(JNIEnv *env, jclass clazz)
{
    CHECK_NULL(clazz = (*env)->FindClass(env, "java/io/FileDescriptor"));
    CHECK_NULL(fd_fdID = (*env)->GetFieldID(env, clazz, "fd", "I"));
}

JNIEXPORT jint JNICALL
Java_jdk_internal_jimage_concurrent_ConcurrentPReader_pread(JNIEnv *env, jclass clazz,
                                                            jobject fdo, jlong address,
                                                            jint len, jlong offset)
{
    jint fd = (*env)->GetIntField(env, fdo, fd_fdID);
    void *buf = (void *)jlong_to_ptr(address);
    int res;
    RESTARTABLE(pread64(fd, buf, len, offset), res);
    if (res == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "pread failed");
    }
    return res;
}
