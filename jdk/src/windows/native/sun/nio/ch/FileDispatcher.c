/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

#include <windows.h>
#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include "sun_nio_ch_FileDispatcher.h"
#include <io.h>
#include "nio.h"
#include "nio_util.h"


/**************************************************************
 * FileDispatcher.c
 */

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcher_read0(JNIEnv *env, jclass clazz, jobject fdo,
                                      jlong address, jint len)
{
    DWORD read = 0;
    BOOL result = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));

    if (h == INVALID_HANDLE_VALUE) {
        JNU_ThrowIOExceptionWithLastError(env, "Invalid handle");
        return IOS_THROWN;
    }
    result = ReadFile(h,          /* File handle to read */
                      (LPVOID)address,    /* address to put data */
                      len,        /* number of bytes to read */
                      &read,      /* number of bytes read */
                      NULL);      /* no overlapped struct */
    if (result == 0) {
        int error = GetLastError();
        if (error == ERROR_BROKEN_PIPE) {
            return IOS_EOF;
        }
        if (error == ERROR_NO_DATA) {
            return IOS_UNAVAILABLE;
        }
        JNU_ThrowIOExceptionWithLastError(env, "Read failed");
        return IOS_THROWN;
    }
    return convertReturnVal(env, (jint)read, JNI_TRUE);
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcher_readv0(JNIEnv *env, jclass clazz, jobject fdo,
                                       jlong address, jint len)
{
    DWORD read = 0;
    BOOL result = 0;
    jlong totalRead = 0;
    LPVOID loc;
    int i = 0;
    DWORD num = 0;
    struct iovec *iovecp = (struct iovec *)jlong_to_ptr(address);
    HANDLE h = (HANDLE)(handleval(env, fdo));

    if (h == INVALID_HANDLE_VALUE) {
        JNU_ThrowIOExceptionWithLastError(env, "Invalid handle");
        return IOS_THROWN;
    }

    for(i=0; i<len; i++) {
        loc = (LPVOID)jlong_to_ptr(iovecp[i].iov_base);
        num = iovecp[i].iov_len;
        result = ReadFile(h,                /* File handle to read */
                          loc,              /* address to put data */
                          num,              /* number of bytes to read */
                          &read,            /* number of bytes read */
                          NULL);            /* no overlapped struct */
        if (read > 0) {
            totalRead += read;
        }
        if (read < num) {
            break;
        }
    }

    if (result == 0) {
        int error = GetLastError();
        if (error == ERROR_BROKEN_PIPE) {
            return IOS_EOF;
        }
        if (error == ERROR_NO_DATA) {
            return IOS_UNAVAILABLE;
        }
        JNU_ThrowIOExceptionWithLastError(env, "Read failed");
        return IOS_THROWN;
    }

    return convertLongReturnVal(env, totalRead, JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcher_pread0(JNIEnv *env, jclass clazz, jobject fdo,
                            jlong address, jint len, jlong offset)
{
    DWORD read = 0;
    BOOL result = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));
    DWORD lowPos = 0;
    long highPos = 0;
    DWORD lowOffset = 0;
    long highOffset = 0;

    if (h == INVALID_HANDLE_VALUE) {
        JNU_ThrowIOExceptionWithLastError(env, "Invalid handle");
        return IOS_THROWN;
    }

    lowPos = SetFilePointer(h, 0, &highPos, FILE_CURRENT);
    if (lowPos == ((DWORD)-1)) {
        if (GetLastError() != ERROR_SUCCESS) {
            JNU_ThrowIOExceptionWithLastError(env, "Seek failed");
            return IOS_THROWN;
        }
    }

    lowOffset = (DWORD)offset;
    highOffset = (DWORD)(offset >> 32);
    lowOffset = SetFilePointer(h, lowOffset, &highOffset, FILE_BEGIN);
    if (lowOffset == ((DWORD)-1)) {
        if (GetLastError() != ERROR_SUCCESS) {
            JNU_ThrowIOExceptionWithLastError(env, "Seek failed");
            return IOS_THROWN;
        }
    }

    result = ReadFile(h,                /* File handle to read */
                      (LPVOID)address,  /* address to put data */
                      len,              /* number of bytes to read */
                      &read,            /* number of bytes read */
                      NULL);              /* struct with offset */

    if (result == 0) {
        int error = GetLastError();
        if (error == ERROR_BROKEN_PIPE) {
            return IOS_EOF;
        }
        if (error == ERROR_NO_DATA) {
            return IOS_UNAVAILABLE;
        }
        JNU_ThrowIOExceptionWithLastError(env, "Read failed");
        return IOS_THROWN;
    }

    lowPos = SetFilePointer(h, lowPos, &highPos, FILE_BEGIN);
    if (lowPos == ((DWORD)-1)) {
        if (GetLastError() != ERROR_SUCCESS) {
            JNU_ThrowIOExceptionWithLastError(env, "Seek failed");
            return IOS_THROWN;
        }
    }
    return convertReturnVal(env, (jint)read, JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcher_write0(JNIEnv *env, jclass clazz, jobject fdo,
                                       jlong address, jint len)
{
    BOOL result = 0;
    DWORD written = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));

    if (h != INVALID_HANDLE_VALUE) {
        result = WriteFile(h,           /* File handle to write */
                      (LPCVOID)address, /* pointers to the buffers */
                      len,              /* number of bytes to write */
                      &written,         /* receives number of bytes written */
                      NULL);            /* no overlapped struct */
    }

    if ((h == INVALID_HANDLE_VALUE) || (result == 0)) {
        JNU_ThrowIOExceptionWithLastError(env, "Write failed");
    }

    return convertReturnVal(env, (jint)written, JNI_FALSE);
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcher_writev0(JNIEnv *env, jclass clazz, jobject fdo,
                                       jlong address, jint len)
{
    BOOL result = 0;
    DWORD written = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));
    jlong totalWritten = 0;

    if (h != INVALID_HANDLE_VALUE) {
        LPVOID loc;
        int i = 0;
        DWORD num = 0;
        struct iovec *iovecp = (struct iovec *)jlong_to_ptr(address);

        for(i=0; i<len; i++) {
            loc = (LPVOID)jlong_to_ptr(iovecp[i].iov_base);
            num = iovecp[i].iov_len;
            result = WriteFile(h,       /* File handle to write */
                               loc,     /* pointers to the buffers */
                               num,     /* number of bytes to write */
                               &written,/* receives number of bytes written */
                               NULL);   /* no overlapped struct */
            if (written > 0) {
                totalWritten += written;
            }
            if (written < num) {
                break;
            }
        }
    }

    if ((h == INVALID_HANDLE_VALUE) || (result == 0)) {
        JNU_ThrowIOExceptionWithLastError(env, "Write failed");
    }

    return convertLongReturnVal(env, totalWritten, JNI_FALSE);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcher_pwrite0(JNIEnv *env, jclass clazz, jobject fdo,
                            jlong address, jint len, jlong offset)
{
    BOOL result = 0;
    DWORD written = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));
    DWORD lowPos = 0;
    long highPos = 0;
    DWORD lowOffset = 0;
    long highOffset = 0;

    lowPos = SetFilePointer(h, 0, &highPos, FILE_CURRENT);
    if (lowPos == ((DWORD)-1)) {
        if (GetLastError() != ERROR_SUCCESS) {
            JNU_ThrowIOExceptionWithLastError(env, "Seek failed");
            return IOS_THROWN;
        }
    }

    lowOffset = (DWORD)offset;
    highOffset = (DWORD)(offset >> 32);
    lowOffset = SetFilePointer(h, lowOffset, &highOffset, FILE_BEGIN);
    if (lowOffset == ((DWORD)-1)) {
        if (GetLastError() != ERROR_SUCCESS) {
            JNU_ThrowIOExceptionWithLastError(env, "Seek failed");
            return IOS_THROWN;
        }
    }

    result = WriteFile(h,               /* File handle to write */
                      (LPCVOID)address, /* pointers to the buffers */
                      len,              /* number of bytes to write */
                      &written,         /* receives number of bytes written */
                      NULL);            /* no overlapped struct */

    if ((h == INVALID_HANDLE_VALUE) || (result == 0)) {
        JNU_ThrowIOExceptionWithLastError(env, "Write failed");
        return IOS_THROWN;
    }

    lowPos = SetFilePointer(h, lowPos, &highPos, FILE_BEGIN);
    if (lowPos == ((DWORD)-1)) {
        if (GetLastError() != ERROR_SUCCESS) {
            JNU_ThrowIOExceptionWithLastError(env, "Seek failed");
            return IOS_THROWN;
        }
    }

    return convertReturnVal(env, (jint)written, JNI_FALSE);
}

static void closeFile(JNIEnv *env, jlong fd) {
    HANDLE h = (HANDLE)fd;
    if (h != INVALID_HANDLE_VALUE) {
        int result = CloseHandle(h);
        if (result < 0)
            JNU_ThrowIOExceptionWithLastError(env, "Close failed");
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_FileDispatcher_close0(JNIEnv *env, jclass clazz, jobject fdo)
{
    jlong fd = handleval(env, fdo);
    closeFile(env, fd);
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_FileDispatcher_closeByHandle(JNIEnv *env, jclass clazz,
                                             jlong fd)
{
    closeFile(env, fd);
}
