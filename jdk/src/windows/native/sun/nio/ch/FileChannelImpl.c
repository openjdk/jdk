/*
 * Copyright 2000-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include <io.h>
#include "nio.h"
#include "nio_util.h"
#include "sun_nio_ch_FileChannelImpl.h"

static jfieldID chan_fd; /* id for jobject 'fd' in java.io.FileChannel */


/* false for 95/98/ME, true for NT/W2K */
static jboolean onNT = JNI_FALSE;

/**************************************************************
 * static method to store field ID's in initializers
 * and retrieve the allocation granularity
 */
JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileChannelImpl_initIDs(JNIEnv *env, jclass clazz)
{
    SYSTEM_INFO si;
    jint align;
    OSVERSIONINFO ver;
    GetSystemInfo(&si);
    align = si.dwAllocationGranularity;
    chan_fd = (*env)->GetFieldID(env, clazz, "fd", "Ljava/io/FileDescriptor;");
    ver.dwOSVersionInfoSize = sizeof(ver);
    GetVersionEx(&ver);
    if (ver.dwPlatformId == VER_PLATFORM_WIN32_NT) {
        onNT = JNI_TRUE;
    }
    return align;
}


/**************************************************************
 * Channel
 */

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileChannelImpl_map0(JNIEnv *env, jobject this,
                               jint prot, jlong off, jlong len)
{
    void *mapAddress = 0;
    jint lowOffset = (jint)off;
    jint highOffset = (jint)(off >> 32);
    jlong maxSize = off + len;
    jint lowLen = (jint)(maxSize);
    jint highLen = (jint)(maxSize >> 32);
    jobject fdo = (*env)->GetObjectField(env, this, chan_fd);
    HANDLE fileHandle = (HANDLE)(handleval(env, fdo));
    HANDLE mapping;
    DWORD mapAccess = FILE_MAP_READ;
    DWORD fileProtect = PAGE_READONLY;
    DWORD mapError;
    BOOL result;

    if (prot == sun_nio_ch_FileChannelImpl_MAP_RO) {
        fileProtect = PAGE_READONLY;
        mapAccess = FILE_MAP_READ;
    } else if (prot == sun_nio_ch_FileChannelImpl_MAP_RW) {
        fileProtect = PAGE_READWRITE;
        mapAccess = FILE_MAP_WRITE;
    } else if (prot == sun_nio_ch_FileChannelImpl_MAP_PV) {
        fileProtect = PAGE_WRITECOPY;
        mapAccess = FILE_MAP_COPY;
    }

    mapping = CreateFileMapping(
        fileHandle,      /* Handle of file */
        NULL,            /* Not inheritable */
        fileProtect,     /* Read and write */
        highLen,         /* High word of max size */
        lowLen,          /* Low word of max size */
        NULL);           /* No name for object */

    if (mapping == NULL) {
        JNU_ThrowIOExceptionWithLastError(env, "Map failed");
        return IOS_THROWN;
    }

    mapAddress = MapViewOfFile(
        mapping,             /* Handle of file mapping object */
        mapAccess,           /* Read and write access */
        highOffset,          /* High word of offset */
        lowOffset,           /* Low word of offset */
        (DWORD)len);         /* Number of bytes to map */
    mapError = GetLastError();

    result = CloseHandle(mapping);
    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Map failed");
        return IOS_THROWN;
    }

    if (mapAddress == NULL) {
        if (mapError == ERROR_NOT_ENOUGH_MEMORY)
            JNU_ThrowOutOfMemoryError(env, "Map failed");
        else
            JNU_ThrowIOExceptionWithLastError(env, "Map failed");
        return IOS_THROWN;
    }

    return ptr_to_jlong(mapAddress);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileChannelImpl_unmap0(JNIEnv *env, jobject this,
                                 jlong address, jlong len)
{
    BOOL result;
    void *a = (void *) jlong_to_ptr(address);

    result = UnmapViewOfFile(a);
    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Unmap failed");
        return IOS_THROWN;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileChannelImpl_truncate0(JNIEnv *env, jobject this,
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


JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileChannelImpl_force0(JNIEnv *env, jobject this,
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

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileChannelImpl_position0(JNIEnv *env, jobject this,
                                          jobject fdo, jlong offset)
{
    DWORD lowPos = 0;
    long highPos = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));

    if (offset < 0) {
        lowPos = SetFilePointer(h, 0, &highPos, FILE_CURRENT);
    } else {
        lowPos = (DWORD)offset;
        highPos = (long)(offset >> 32);
        lowPos = SetFilePointer(h, lowPos, &highPos, FILE_BEGIN);
    }
    if (lowPos == ((DWORD)-1)) {
        if (GetLastError() != ERROR_SUCCESS) {
            JNU_ThrowIOExceptionWithLastError(env, "Seek failed");
            return IOS_THROWN;
        }
    }
    return (((jlong)highPos) << 32) | lowPos;
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileChannelImpl_size0(JNIEnv *env, jobject this, jobject fdo)
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

JNIEXPORT void JNICALL
Java_sun_nio_ch_FileChannelImpl_close0(JNIEnv *env, jobject this, jobject fdo)
{
    HANDLE h = (HANDLE)(handleval(env, fdo));
    if (h != INVALID_HANDLE_VALUE) {
        jint result = CloseHandle(h);
        if (result < 0) {
            JNU_ThrowIOExceptionWithLastError(env, "Close failed");
        }
    }
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileChannelImpl_transferTo0(JNIEnv *env, jobject this,
                                            jint srcFD,
                                            jlong position, jlong count,
                                            jint dstFD)
{
    return IOS_UNSUPPORTED;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileChannelImpl_lock0(JNIEnv *env, jobject this, jobject fdo,
                                      jboolean block, jlong pos, jlong size,
                                      jboolean shared)
{
    HANDLE h = (HANDLE)(handleval(env, fdo));
    DWORD lowPos = (DWORD)pos;
    long highPos = (long)(pos >> 32);
    DWORD lowNumBytes = (DWORD)size;
    DWORD highNumBytes = (DWORD)(size >> 32);
    jint result = 0;
    if (onNT) {
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
                return sun_nio_ch_FileChannelImpl_NO_LOCK;
            }
            if (flags & LOCKFILE_FAIL_IMMEDIATELY) {
                return sun_nio_ch_FileChannelImpl_NO_LOCK;
            }
            JNU_ThrowIOExceptionWithLastError(env, "Lock failed");
            return sun_nio_ch_FileChannelImpl_NO_LOCK;
        }
        return sun_nio_ch_FileChannelImpl_LOCKED;
    } else {
        for(;;) {
            if (size > 0x7fffffff) {
                size = 0x7fffffff;
            }
            lowNumBytes = (DWORD)size;
            highNumBytes = 0;
            result = LockFile(h, lowPos, highPos, lowNumBytes, highNumBytes);
            if (result != 0) {
                if (shared == JNI_TRUE) {
                    return sun_nio_ch_FileChannelImpl_RET_EX_LOCK;
                } else {
                    return sun_nio_ch_FileChannelImpl_LOCKED;
                }
            } else {
                int error = GetLastError();
                if (error != ERROR_LOCK_VIOLATION) {
                    JNU_ThrowIOExceptionWithLastError(env, "Lock failed");
                    return sun_nio_ch_FileChannelImpl_NO_LOCK;
                }
                if (block == JNI_FALSE) {
                    return sun_nio_ch_FileChannelImpl_NO_LOCK;
                }
            }
            Sleep(100);
        }
    }
    return sun_nio_ch_FileChannelImpl_NO_LOCK;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_FileChannelImpl_release0(JNIEnv *env, jobject this,
                                        jobject fdo, jlong pos, jlong size)
{
    HANDLE h = (HANDLE)(handleval(env, fdo));
    DWORD lowPos = (DWORD)pos;
    long highPos = (long)(pos >> 32);
    DWORD lowNumBytes = (DWORD)size;
    DWORD highNumBytes = (DWORD)(size >> 32);
    jint result = 0;
    if (onNT) {
        OVERLAPPED o;
        o.hEvent = 0;
        o.Offset = lowPos;
        o.OffsetHigh = highPos;
        result = UnlockFileEx(h, 0, lowNumBytes, highNumBytes, &o);
    } else {
        if (size > 0x7fffffff) {
            size = 0x7fffffff;
        }
        lowNumBytes = (DWORD)size;
        highNumBytes = 0;
        result = UnlockFile(h, lowPos, highPos, lowNumBytes, highNumBytes);
    }
    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Release failed");
    }
}
