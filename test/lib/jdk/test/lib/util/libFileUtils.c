/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>

#ifdef _WIN32

#include "jni.h"
#include "jni_util.h"
#include <string.h>
#include <windows.h>
#include <fileapi.h>
#include <handleapi.h>
#include <ioapiset.h>
#include <winioctl.h>
#include <errhandlingapi.h>

// Based on Microsoft documentation
#define MAX_REPARSE_BUFFER_SIZE 16384

// Unavailable in standard header files:
// copied from Microsoft documentation
typedef struct _REPARSE_DATA_BUFFER {
    ULONG  ReparseTag;
    USHORT ReparseDataLength;
    USHORT Reserved;
    union {
        struct {
            USHORT SubstituteNameOffset;
            USHORT SubstituteNameLength;
            USHORT PrintNameOffset;
            USHORT PrintNameLength;
            ULONG  Flags;
            WCHAR  PathBuffer[1];
        } SymbolicLinkReparseBuffer;
        struct {
            USHORT SubstituteNameOffset;
            USHORT SubstituteNameLength;
            USHORT PrintNameOffset;
            USHORT PrintNameLength;
            WCHAR  PathBuffer[1];
        } MountPointReparseBuffer;
        struct {
            UCHAR DataBuffer[1];
        } GenericReparseBuffer;
    } DUMMYUNIONNAME;
} REPARSE_DATA_BUFFER, * PREPARSE_DATA_BUFFER;

JNIEXPORT jlong JNICALL
Java_jdk_test_lib_util_FileUtils_getWinProcessHandleCount0
    (JNIEnv* env)
{
    DWORD handleCount;
    HANDLE handle = GetCurrentProcess();
    if (GetProcessHandleCount(handle, &handleCount)) {
        return (jlong)handleCount;
    } else {
        return -1L;
    }
}

void throwIOExceptionWithLastError(JNIEnv* env) {
#define BUFSIZE 256
    DWORD errval;
    WCHAR buf[BUFSIZE];

    if ((errval = GetLastError()) != 0) {
        jsize n = FormatMessageW(
            FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
            NULL, errval, 0, buf, BUFSIZE, NULL);

        jclass ioExceptionClass = (*env)->FindClass(env, "java/io/IOException");
        (*env)->ThrowNew(env, ioExceptionClass, (const char*) buf);
    }
}

JNIEXPORT jboolean JNICALL
Java_jdk_test_lib_util_FileUtils_createWinDirectoryJunction0
    (JNIEnv* env, jclass unused, jstring sjunction, jstring starget)
{
    BOOL error = FALSE;

    const jshort bpc = sizeof(wchar_t); // bytes per character
    HANDLE hJunction = INVALID_HANDLE_VALUE;

    const jchar* junction = (*env)->GetStringChars(env, sjunction, NULL);
    const jchar* target   = (*env)->GetStringChars(env, starget, NULL);
    if (junction == NULL || target == NULL) {
        jclass npeClass = (*env)->FindClass(env, "java/lang/NullPointerException");
        (*env)->ThrowNew(env, npeClass, NULL);
        error = TRUE;
    }

    USHORT wlen = (USHORT)0;
    USHORT blen = (USHORT)0;
    void* lpInBuffer = NULL;
    if (!error) {
        wlen = (USHORT)wcslen(target);
        blen = (USHORT)(wlen * sizeof(wchar_t));
        lpInBuffer = calloc(MAX_REPARSE_BUFFER_SIZE, sizeof(char));
        if (lpInBuffer == NULL) {
            jclass oomeClass = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
            (*env)->ThrowNew(env, oomeClass, NULL);
            error = TRUE;
        }
    }

    if (!error) {
        if (CreateDirectoryW(junction, NULL) == 0) {
            throwIOExceptionWithLastError(env);
            error = TRUE;
        }
    }

    if (!error) {
        hJunction = CreateFileW(junction, GENERIC_READ | GENERIC_WRITE,
                                FILE_SHARE_READ | FILE_SHARE_WRITE, NULL,
                                OPEN_EXISTING,
                                FILE_FLAG_OPEN_REPARSE_POINT
                                | FILE_FLAG_BACKUP_SEMANTICS, NULL);
        if (hJunction == INVALID_HANDLE_VALUE) {
            throwIOExceptionWithLastError(env);
            error = TRUE;
        }
    }

    if (!error) {
        PREPARSE_DATA_BUFFER reparseBuffer = (PREPARSE_DATA_BUFFER)lpInBuffer;
        reparseBuffer->ReparseTag = IO_REPARSE_TAG_MOUNT_POINT;
        reparseBuffer->Reserved = 0;
        WCHAR* prefix = L"\\??\\";
        USHORT prefixLength = (USHORT)(bpc * wcslen(prefix));
        reparseBuffer->MountPointReparseBuffer.SubstituteNameOffset = 0;
        reparseBuffer->MountPointReparseBuffer.SubstituteNameLength =
            prefixLength + blen;
        reparseBuffer->MountPointReparseBuffer.PrintNameOffset =
            prefixLength + blen + sizeof(WCHAR);
        reparseBuffer->MountPointReparseBuffer.PrintNameLength = blen;
        memcpy(&reparseBuffer->MountPointReparseBuffer.PathBuffer,
               prefix, prefixLength);
        memcpy(&reparseBuffer->MountPointReparseBuffer.PathBuffer[prefixLength/bpc],
               target, blen);
        memcpy(&reparseBuffer->MountPointReparseBuffer.PathBuffer[prefixLength/bpc + blen/bpc + 1],
               target, blen);
        reparseBuffer->ReparseDataLength =
            (USHORT)(sizeof(reparseBuffer->MountPointReparseBuffer) +
                     prefixLength + bpc*blen + bpc);
        DWORD nInBufferSize = FIELD_OFFSET(REPARSE_DATA_BUFFER,
            MountPointReparseBuffer) + reparseBuffer->ReparseDataLength;
        BOOL result = DeviceIoControl(hJunction, FSCTL_SET_REPARSE_POINT,
                                      lpInBuffer, nInBufferSize,
                                      NULL, 0, NULL, NULL);
        if (result == 0) {
            throwIOExceptionWithLastError(env);
            error = TRUE;
        }
    }

    if (junction != NULL) {
        (*env)->ReleaseStringChars(env, sjunction, junction);
        if (target != NULL) {
            (*env)->ReleaseStringChars(env, starget, target);
            if (lpInBuffer != NULL) {
                free(lpInBuffer);
                if (hJunction != INVALID_HANDLE_VALUE) {
                    // Ignore any error in CloseHandle
                    CloseHandle(hJunction);
                }
            }
        }
    }

    return error ? JNI_FALSE : JNI_TRUE;
}

#endif  /*  _WIN32 */
