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
#include "jni_util.h"
#ifdef WINDOWS
#include <windows.h>
#include <fileapi.h>
#include <winerror.h>
#else
#include <errno.h>
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

#ifdef WINDOWS
jboolean initialized = JNI_FALSE;
BOOL(WINAPI * pfnGetDiskSpaceInformation)(LPCWSTR, LPVOID) = NULL;
#endif

//
// root      the root of the volume
// sizes[0]  total size:   number of bytes in the volume
// sizes[1]  total space:  number of bytes visible to the caller
// sizes[2]  free space:   number of free bytes in the volume
// sizes[3]  usable space: number of bytes available to the caller
//
JNIEXPORT jboolean JNICALL
Java_GetXSpace_getSpace0
    (JNIEnv *env, jclass cls, jstring root, jlongArray sizes)
{
    jboolean totalSpaceIsEstimated = JNI_FALSE;
    jlong array[4];
    const jchar* strchars = (*env)->GetStringChars(env, root, NULL);
    if (strchars == NULL) {
        JNU_ThrowByNameWithLastError(env, "java/lang/RuntimeException",
                                     "GetStringChars");
        return JNI_FALSE;
    }

#ifdef WINDOWS
    if (initialized == JNI_FALSE) {
        initialized = JNI_TRUE;
        HMODULE hmod = GetModuleHandleW(L"kernel32");
        if (hmod != NULL) {
            *(FARPROC*)&pfnGetDiskSpaceInformation =
                GetProcAddress(hmod, "GetDiskSpaceInformationW");
        }
    }

    LPCWSTR path = (LPCWSTR)strchars;

    if (pfnGetDiskSpaceInformation != NULL) {
        // use GetDiskSpaceInformationW
        DISK_SPACE_INFORMATION diskSpaceInfo;
        BOOL hres = pfnGetDiskSpaceInformation(path, &diskSpaceInfo);
        (*env)->ReleaseStringChars(env, root, strchars);
        if (FAILED(hres)) {
            JNU_ThrowByNameWithLastError(env, "java/lang/RuntimeException",
                                         "GetDiskSpaceInformationW");
            return totalSpaceIsEstimated;
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
    } else {
        totalSpaceIsEstimated = JNI_TRUE;

        // if GetDiskSpaceInformationW is unavailable ("The specified
        // procedure could not be found"), fall back to GetDiskFreeSpaceExW
        ULARGE_INTEGER freeBytesAvailable;
        ULARGE_INTEGER totalNumberOfBytes;
        ULARGE_INTEGER totalNumberOfFreeBytes;

        BOOL hres = GetDiskFreeSpaceExW(path, &freeBytesAvailable,
            &totalNumberOfBytes, &totalNumberOfFreeBytes);
        (*env)->ReleaseStringChars(env, root, strchars);
        if (FAILED(hres)) {
            JNU_ThrowByNameWithLastError(env, "java/lang/RuntimeException",
                                         "GetDiskFreeSpaceExW");
            return totalSpaceIsEstimated;
        }

        // If quotas are in effect, it is impossible to obtain the volume size,
        // so estimate it as free + used = free + (visible - available)
        ULONGLONG used = totalNumberOfBytes.QuadPart - freeBytesAvailable.QuadPart;
        array[0] = (jlong)(totalNumberOfFreeBytes.QuadPart + used);
        array[1] = (jlong)totalNumberOfBytes.QuadPart;
        array[2] = (jlong)totalNumberOfFreeBytes.QuadPart;
        array[3] = (jlong)freeBytesAvailable.QuadPart;
    }
#else
    int len = (int)(*env)->GetStringLength(env, root);
    char* chars = (char*)malloc((len + 1)*sizeof(char));
    if (chars == NULL) {
        (*env)->ReleaseStringChars(env, root, strchars);
        JNU_ThrowByNameWithLastError(env, "java/lang/RuntimeException",
                                     "malloc");
        return JNI_FALSE;
    }

    for (int i = 0; i < len; i++) {
        chars[i] = (char)strchars[i];
    }
    chars[len] = '\0';
    (*env)->ReleaseStringChars(env, root, strchars);

    struct statfs buf;
    int result = statfs(chars, &buf);
    free(chars);
    if (result < 0) {
        JNU_ThrowByNameWithLastError(env, "java/lang/RuntimeException",
                                     strerror(errno));
        return totalSpaceIsEstimated;
    }

    array[0] = (jlong)(buf.f_blocks*buf.f_bsize);
    array[1] = array[0]; // number visible is the same as the total size
    array[2] = (jlong)(buf.f_bfree*buf.f_bsize);
    array[3] = (jlong)(buf.f_bavail*buf.f_bsize);
#endif
    (*env)->SetLongArrayRegion(env, sizes, 0, 4, array);
    return totalSpaceIsEstimated;
}
#ifdef __cplusplus
}
#endif
