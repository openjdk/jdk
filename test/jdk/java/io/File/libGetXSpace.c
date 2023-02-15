/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
#include <stdlib.h>
#include "jni.h"
#ifdef _WIN64
#include <fileapi.h>
#include <winerror.h>
#else
#include <sys/errno.h>
#include <string.h>
#if __APPLE__
#include <sys/param.h>
#include <sys/mount.h>
#else
#include <sys/statfs.h>
#endif
#endif

#ifdef __cplusplus
extern "C" {
#endif
/*
 * Throws an exception with the given class name and detail message
 */
static void ThrowException(JNIEnv *env, const char *name, const char *msg) {
    jclass cls = (*env)->FindClass(env, name);
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, msg);
    }
}

JNIEXPORT void JNICALL
Java_GetXSpace_getSpace0
    (JNIEnv *env, jclass cls, jstring root, jlongArray sizes)
{
    jlong array[4];
    const jchar* chars = (*env)->GetStringChars(env, root, NULL);
    if (chars == NULL) {
        ThrowException(env, "java/lang/RuntimeException", "GetStringChars");
    }

#ifdef _WIN64
    DISK_SPACE_INFORMATION diskSpaceInfo;
    LPCWSTR path = (LPCWSTR)chars;
    HRESULT result = GetDiskSpaceInformationW(path, &diskSpaceInfo);
    (*env)->ReleaseStringChars(env, root, chars);
    if (FAILED(result)) {
        ThrowException(env, "java/lang/RuntimeException",
                       "GetDiskSpaceInformationW");
        return;
    }

    ULONGLONG bytesPerAllocationUnit =
        diskSpaceInfo.SectorsPerAllocationUnit*diskSpaceInfo.BytesPerSector;
    array[0] = (jlong)(diskSpaceInfo.ActualTotalAllocationUnits*
                       bytesPerAllocationUnit);
    array[1] = (jlong)(diskSpaceInfo.CallerTotalAllocationUnits*
                       bytesPerAllocationUnit);
    array[2] = (jlong)(diskSpaceInfo.ActualAvailableAllocationUnits*
                       bytesPerAllocationUnit);
    array[3] = (jlong)(diskSpaceInfo.CallerAvailableAllocationUnits*
                       bytesPerAllocationUnit);
#else
    struct statfs buf;
    int result = statfs((const char*)chars, &buf);
    (*env)->ReleaseStringChars(env, root, chars);
    if (result < 0) {
        ThrowException(env, "java/lang/RuntimeException", strerror(errno));
        return;
    }

    array[0] = (jlong)(buf.f_blocks*buf.f_bsize);
    array[1] = array[0]; // number visible is the same as the total size
    array[2] = (jlong)(buf.f_bfree*buf.f_bsize);
    array[3] = (jlong)(buf.f_bavail*buf.f_bsize);
#endif
    (*env)->SetLongArrayRegion(env, sizes, 0, 4, array);
}
#ifdef __cplusplus
}
#endif
