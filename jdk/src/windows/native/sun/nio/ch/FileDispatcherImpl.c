/*
 * Copyright 2000-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "sun_nio_ch_FileDispatcherImpl.h"
#include <io.h>
#include "nio.h"
#include "nio_util.h"


/**************************************************************
 * FileDispatcherImpl.c
 */

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_read0(JNIEnv *env, jclass clazz, jobject fdo,
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
Java_sun_nio_ch_FileDispatcherImpl_readv0(JNIEnv *env, jclass clazz, jobject fdo,
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
Java_sun_nio_ch_FileDispatcherImpl_pread0(JNIEnv *env, jclass clazz, jobject fdo,
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
Java_sun_nio_ch_FileDispatcherImpl_write0(JNIEnv *env, jclass clazz, jobject fdo,
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
Java_sun_nio_ch_FileDispatcherImpl_writev0(JNIEnv *env, jclass clazz, jobject fdo,
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
Java_sun_nio_ch_FileDispatcherImpl_pwrite0(JNIEnv *env, jclass clazz, jobject fdo,
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

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_force0(JNIEnv *env, jobject this,
                                          jobject fdo, jboolean md)
{
    int result = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));

    if (h != INVALID_HANDLE_VALUE) {
        result = FlushFileBuffers(h);
        if (result == 0) {
            int error = GetLastError();
            if (error != ERROR_ACCESS_DENIED) {
                JNU_ThrowIOExceptionWithLastError(env, "Force failed");
                return IOS_THROWN;
            }
        }
    } else {
        JNU_ThrowIOExceptionWithLastError(env, "Force failed");
        return IOS_THROWN;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_truncate0(JNIEnv *env, jobject this,
                                             jobject fdo, jlong size)
{
    DWORD lowPos = 0;
    long highPos = 0;
    BOOL result = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));

    lowPos = (DWORD)size;
    highPos = (long)(size >> 32);
    lowPos = SetFilePointer(h, lowPos, &highPos, FILE_BEGIN);
    if (lowPos == ((DWORD)-1)) {
        if (GetLastError() != ERROR_SUCCESS) {
            JNU_ThrowIOExceptionWithLastError(env, "Truncation failed");
            return IOS_THROWN;
        }
    }
    result = SetEndOfFile(h);
    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Truncation failed");
        return IOS_THROWN;
    }
    return 0;
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcherImpl_size0(JNIEnv *env, jobject this, jobject fdo)
{
    DWORD sizeLow = 0;
    DWORD sizeHigh = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));

    sizeLow = GetFileSize(h, &sizeHigh);
    if (sizeLow == ((DWORD)-1)) {
        if (GetLastError() != ERROR_SUCCESS) {
            JNU_ThrowIOExceptionWithLastError(env, "Size failed");
            return IOS_THROWN;
        }
    }
    return (((jlong)sizeHigh) << 32) | sizeLow;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_lock0(JNIEnv *env, jobject this, jobject fdo,
                                      jboolean block, jlong pos, jlong size,
                                      jboolean shared)
{
    HANDLE h = (HANDLE)(handleval(env, fdo));
    DWORD lowPos = (DWORD)pos;
    long highPos = (long)(pos >> 32);
    DWORD lowNumBytes = (DWORD)size;
    DWORD highNumBytes = (DWORD)(size >> 32);
    BOOL result;
    DWORD flags = 0;
    OVERLAPPED o;
    o.hEvent = 0;
    o.Offset = lowPos;
    o.OffsetHigh = highPos;
    if (block == JNI_FALSE) {
        flags |= LOCKFILE_FAIL_IMMEDIATELY;
    }
    if (shared == JNI_FALSE) {
        flags |= LOCKFILE_EXCLUSIVE_LOCK;
    }
    result = LockFileEx(h, flags, 0, lowNumBytes, highNumBytes, &o);
    if (result == 0) {
        int error = GetLastError();
        if (error != ERROR_LOCK_VIOLATION) {
            JNU_ThrowIOExceptionWithLastError(env, "Lock failed");
            return sun_nio_ch_FileDispatcherImpl_NO_LOCK;
        }
        if (flags & LOCKFILE_FAIL_IMMEDIATELY) {
            return sun_nio_ch_FileDispatcherImpl_NO_LOCK;
        }
        JNU_ThrowIOExceptionWithLastError(env, "Lock failed");
        return sun_nio_ch_FileDispatcherImpl_NO_LOCK;
    }
    return sun_nio_ch_FileDispatcherImpl_LOCKED;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_FileDispatcherImpl_release0(JNIEnv *env, jobject this,
                                        jobject fdo, jlong pos, jlong size)
{
    HANDLE h = (HANDLE)(handleval(env, fdo));
    DWORD lowPos = (DWORD)pos;
    long highPos = (long)(pos >> 32);
    DWORD lowNumBytes = (DWORD)size;
    DWORD highNumBytes = (DWORD)(size >> 32);
    jint result = 0;
    OVERLAPPED o;
    o.hEvent = 0;
    o.Offset = lowPos;
    o.OffsetHigh = highPos;
    result = UnlockFileEx(h, 0, lowNumBytes, highNumBytes, &o);
    if (result == 0 && GetLastError() != ERROR_NOT_LOCKED) {
        JNU_ThrowIOExceptionWithLastError(env, "Release failed");
    }
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
Java_sun_nio_ch_FileDispatcherImpl_close0(JNIEnv *env, jclass clazz, jobject fdo)
{
    jlong fd = handleval(env, fdo);
    closeFile(env, fd);
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_FileDispatcherImpl_closeByHandle(JNIEnv *env, jclass clazz,
                                             jlong fd)
{
    closeFile(env, fd);
}
