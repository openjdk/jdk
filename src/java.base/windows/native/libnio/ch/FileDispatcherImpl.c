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
#include <winioctl.h>
#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include <io.h>
#include "nio.h"
#include "nio_util.h"
#include "java_lang_Integer.h"
#include "sun_nio_ch_FileDispatcherImpl.h"
#include "io_util_md.h"

#include <Mswsock.h> // Requires Mswsock.lib

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
        JNU_ThrowIOException(env, "Invalid handle");
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
        JNU_ThrowIOException(env, "Invalid handle");
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
    LARGE_INTEGER currPos;
    OVERLAPPED ov;

    if (h == INVALID_HANDLE_VALUE) {
        JNU_ThrowIOException(env, "Invalid handle");
        return IOS_THROWN;
    }

    currPos.QuadPart = 0;
    result = SetFilePointerEx(h, currPos, &currPos, FILE_CURRENT);
    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Seek failed");
        return IOS_THROWN;
    }

    ZeroMemory(&ov, sizeof(ov));
    ov.Offset = (DWORD)offset;
    ov.OffsetHigh = (DWORD)(offset >> 32);

    result = ReadFile(h,                /* File handle to read */
                      (LPVOID)address,  /* address to put data */
                      len,              /* number of bytes to read */
                      &read,            /* number of bytes read */
                      &ov);             /* position to read from */

    if (result == 0) {
        int error = GetLastError();
        if (error == ERROR_BROKEN_PIPE) {
            return IOS_EOF;
        }
        if (error == ERROR_NO_DATA) {
            return IOS_UNAVAILABLE;
        }
        if (error != ERROR_HANDLE_EOF) {
            JNU_ThrowIOExceptionWithLastError(env, "Read failed");
            return IOS_THROWN;
        }
    }

    result = SetFilePointerEx(h, currPos, NULL, FILE_BEGIN);
    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Seek failed");
        return IOS_THROWN;
    }

    return convertReturnVal(env, (jint)read, JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_write0(JNIEnv *env, jclass clazz, jobject fdo,
                                          jlong address, jint len, jboolean append)
{
    BOOL result = 0;
    DWORD written = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));

    if (h != INVALID_HANDLE_VALUE) {
        OVERLAPPED ov;
        LPOVERLAPPED lpOv;
        if (append == JNI_TRUE) {
            ZeroMemory(&ov, sizeof(ov));
            ov.Offset = (DWORD)0xFFFFFFFF;
            ov.OffsetHigh = (DWORD)0xFFFFFFFF;
            lpOv = &ov;
        } else {
            lpOv = NULL;
        }
        result = WriteFile(h,                /* File handle to write */
                           (LPCVOID)address, /* pointer to the buffer */
                           len,              /* number of bytes to write */
                           &written,         /* receives number of bytes written */
                           lpOv);            /* overlapped struct */
    } else {
        JNU_ThrowIOException(env, "Invalid handle");
        return IOS_THROWN;
    }

    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Write failed");
        return IOS_THROWN;
    }

    return convertReturnVal(env, (jint)written, JNI_FALSE);
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcherImpl_writev0(JNIEnv *env, jclass clazz, jobject fdo,
                                           jlong address, jint len, jboolean append)
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
        OVERLAPPED ov;
        LPOVERLAPPED lpOv;
        if (append == JNI_TRUE) {
            ZeroMemory(&ov, sizeof(ov));
            ov.Offset = (DWORD)0xFFFFFFFF;
            ov.OffsetHigh = (DWORD)0xFFFFFFFF;
            lpOv = &ov;
        } else {
            lpOv = NULL;
        }
        for(i=0; i<len; i++) {
            loc = (LPVOID)jlong_to_ptr(iovecp[i].iov_base);
            num = iovecp[i].iov_len;
            result = WriteFile(h,       /* File handle to write */
                               loc,     /* pointers to the buffers */
                               num,     /* number of bytes to write */
                               &written,/* receives number of bytes written */
                               lpOv);   /* overlapped struct */
            if (written > 0) {
                totalWritten += written;
            }
            if (written < num) {
                break;
            }
        }
    } else {
        JNU_ThrowIOException(env, "Invalid handle");
        return IOS_THROWN;
    }

    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Write failed");
        return IOS_THROWN;
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
    LARGE_INTEGER currPos;
    OVERLAPPED ov;

    if (h == INVALID_HANDLE_VALUE) {
        JNU_ThrowIOException(env, "Invalid handle");
        return IOS_THROWN;
    }
    currPos.QuadPart = 0;
    result = SetFilePointerEx(h, currPos, &currPos, FILE_CURRENT);
    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Seek failed");
        return IOS_THROWN;
    }

    ZeroMemory(&ov, sizeof(ov));
    ov.Offset = (DWORD)offset;
    ov.OffsetHigh = (DWORD)(offset >> 32);

    result = WriteFile(h,                /* File handle to write */
                       (LPCVOID)address, /* pointer to the buffer */
                       len,              /* number of bytes to write */
                       &written,         /* receives number of bytes written */
                       &ov);             /* position to write at */

    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Write failed");
        return IOS_THROWN;
    }

    result = SetFilePointerEx(h, currPos, NULL, FILE_BEGIN);
    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Seek failed");
        return IOS_THROWN;
    }

    return convertReturnVal(env, (jint)written, JNI_FALSE);
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcherImpl_seek0(JNIEnv *env, jclass clazz,
                                         jobject fdo, jlong offset)
{
    BOOL result = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));
    LARGE_INTEGER where;
    DWORD whence;

    if (offset < 0) {
        where.QuadPart = 0;
        whence = FILE_CURRENT;
    } else {
        where.QuadPart = offset;
        whence = FILE_BEGIN;
    }

    result = SetFilePointerEx(h, where, &where, whence);
    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "SetFilePointerEx failed");
        return IOS_THROWN;
    }
    return (jlong)where.QuadPart;
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
        JNU_ThrowIOException(env, "Invalid handle");
        return IOS_THROWN;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_truncate0(JNIEnv *env, jobject this,
                                             jobject fdo, jlong size)
{
    BOOL result = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));
    FILE_END_OF_FILE_INFO eofInfo;

    eofInfo.EndOfFile.QuadPart = size;
    result = SetFileInformationByHandle(h,
                                        FileEndOfFileInfo,
                                        &eofInfo,
                                        sizeof(eofInfo));
    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Truncation failed");
        return IOS_THROWN;
    }
    return 0;
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcherImpl_size0(JNIEnv *env, jobject this, jobject fdo)
{
    BOOL result = 0;
    HANDLE h = (HANDLE)(handleval(env, fdo));
    LARGE_INTEGER size;

    result = GetFileSizeEx(h, &size);
    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Size failed");
        return IOS_THROWN;
    }
    return (jlong)size.QuadPart;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_available0(JNIEnv *env, jobject this, jobject fdo)
{
    HANDLE handle = (HANDLE)(handleval(env, fdo));
    DWORD type = GetFileType(handle);
    jlong available = 0;

    // Calculate the number of bytes available for a regular file,
    // and return the default (zero) for other types.
    if (type == FILE_TYPE_DISK) {
        jlong current, end;
        LARGE_INTEGER distance, pos, filesize;
        distance.QuadPart = 0;
        if (SetFilePointerEx(handle, distance, &pos, FILE_CURRENT) == 0) {
            JNU_ThrowIOExceptionWithLastError(env, "Available failed");
            return IOS_THROWN;
        }
        current = (jlong)pos.QuadPart;
        if (GetFileSizeEx(handle, &filesize) == 0) {
            JNU_ThrowIOExceptionWithLastError(env, "Available failed");
            return IOS_THROWN;
        }
        end = (jlong)filesize.QuadPart;
        available = end - current;
        if (available > java_lang_Integer_MAX_VALUE) {
            available = java_lang_Integer_MAX_VALUE;
        } else if (available < 0) {
            available = 0;
        }
    }

    return (jint)available;
}


JNIEXPORT jboolean JNICALL
Java_sun_nio_ch_FileDispatcherImpl_isOther0(JNIEnv *env, jobject this, jobject fdo)
{
    HANDLE handle = (HANDLE)(handleval(env, fdo));

    BY_HANDLE_FILE_INFORMATION finfo;
    if (!GetFileInformationByHandle(handle, &finfo))
        JNU_ThrowIOExceptionWithLastError(env, "isOther failed");
    DWORD fattr = finfo.dwFileAttributes;

    if ((fattr & FILE_ATTRIBUTE_DEVICE) != 0)
        return (jboolean)JNI_TRUE;

    if ((fattr & FILE_ATTRIBUTE_REPARSE_POINT) != 0) {
        int size = MAXIMUM_REPARSE_DATA_BUFFER_SIZE;
        void* lpOutBuffer = (void*)malloc(size*sizeof(char));
        if (lpOutBuffer == NULL)
            JNU_ThrowOutOfMemoryError(env, "isOther failed");

        DWORD bytesReturned;
        if (!DeviceIoControl(handle, FSCTL_GET_REPARSE_POINT, NULL, 0,
                             lpOutBuffer, (DWORD)size, &bytesReturned, NULL)) {
            free(lpOutBuffer);
            JNU_ThrowIOExceptionWithLastError(env, "isOther failed");
        }
        ULONG reparseTag = (*((PULONG)lpOutBuffer));
        free(lpOutBuffer);
        return reparseTag == IO_REPARSE_TAG_SYMLINK ?
            (jboolean)JNI_FALSE : (jboolean)JNI_TRUE;
    }

    return (jboolean)JNI_FALSE;
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
        if (error == ERROR_IO_PENDING) {
            DWORD dwBytes;
            result = GetOverlappedResult(h, &o, &dwBytes, TRUE);
            if (result != 0) {
                return sun_nio_ch_FileDispatcherImpl_LOCKED;
            }
            error = GetLastError();
        }
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
    BOOL result = 0;
    OVERLAPPED o;
    o.hEvent = 0;
    o.Offset = lowPos;
    o.OffsetHigh = highPos;
    result = UnlockFileEx(h, 0, lowNumBytes, highNumBytes, &o);
    if (result == 0) {
        int error = GetLastError();
        if (error == ERROR_IO_PENDING) {
            DWORD dwBytes;
            result = GetOverlappedResult(h, &o, &dwBytes, TRUE);
            if (result != 0) {
                return;
            }
            error = GetLastError();
        }
        if (error != ERROR_NOT_LOCKED) {
            JNU_ThrowIOExceptionWithLastError(env, "Release failed");
        }
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_FileDispatcherImpl_close0(JNIEnv *env, jclass clazz, jobject fdo)
{
    HANDLE h = (HANDLE)handleval(env, fdo);
    if (h != INVALID_HANDLE_VALUE) {
        int result = CloseHandle(h);
        if (result == 0)
            JNU_ThrowIOExceptionWithLastError(env, "Close failed");
    }
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcherImpl_duplicateHandle(JNIEnv *env, jclass this, jlong handle)
{
    HANDLE hProcess = GetCurrentProcess();
    HANDLE hFile = jlong_to_ptr(handle);
    HANDLE hResult;
    BOOL res = DuplicateHandle(hProcess, hFile, hProcess, &hResult, 0, FALSE,
                               DUPLICATE_SAME_ACCESS);
    if (res == 0)
       JNU_ThrowIOExceptionWithLastError(env, "DuplicateHandle failed");
    return ptr_to_jlong(hResult);
}

/**************************************************************
 * static method to retrieve the allocation granularity
 */
JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcherImpl_allocationGranularity0(JNIEnv *env, jclass klass)
{
    SYSTEM_INFO si;
    jint align;
    GetSystemInfo(&si);
    align = si.dwAllocationGranularity;
    return align;
}


/**************************************************************
 * Channel
 */

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcherImpl_map0(JNIEnv *env, jclass klass, jobject fdo,
                                        jint prot, jlong off, jlong len,
                                        jboolean map_sync)
{
    void *mapAddress = 0;
    jint lowOffset = (jint)off;
    jint highOffset = (jint)(off >> 32);
    jlong maxSize = off + len;
    jint lowLen = (jint)(maxSize);
    jint highLen = (jint)(maxSize >> 32);
    HANDLE fileHandle = (HANDLE)(handleval(env, fdo));
    HANDLE mapping;
    DWORD mapAccess = FILE_MAP_READ;
    DWORD fileProtect = PAGE_READONLY;
    DWORD mapError;
    BOOL result;

    if (prot == sun_nio_ch_FileDispatcherImpl_MAP_RO) {
        fileProtect = PAGE_READONLY;
        mapAccess = FILE_MAP_READ;
    } else if (prot == sun_nio_ch_FileDispatcherImpl_MAP_RW) {
        fileProtect = PAGE_READWRITE;
        mapAccess = FILE_MAP_WRITE;
    } else if (prot == sun_nio_ch_FileDispatcherImpl_MAP_PV) {
        fileProtect = PAGE_WRITECOPY;
        mapAccess = FILE_MAP_COPY;
    }

    if (map_sync) {
        JNU_ThrowInternalError(env, "should never call map on platform where MAP_SYNC is unimplemented");
        return IOS_THROWN;
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
        (SIZE_T)len);        /* Number of bytes to map */
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
Java_sun_nio_ch_FileDispatcherImpl_unmap0(JNIEnv *env, jclass klass,
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

// Integer.MAX_VALUE - 1 is the maximum transfer size for TransmitFile()
#define MAX_TRANSMIT_SIZE (java_lang_Integer_MAX_VALUE - 1)

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_maxDirectTransferSize0(JNIEnv* env, jclass klass)
{
    return MAX_TRANSMIT_SIZE;
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcherImpl_transferTo0(JNIEnv *env, jclass klass,
                                               jobject srcFD,
                                            jlong position, jlong count,
                                            jobject dstFD, jboolean append)
{
    const int PACKET_SIZE = 524288;

    LARGE_INTEGER where;
    HANDLE src = (HANDLE)(handleval(env, srcFD));
    SOCKET dst = (SOCKET)(fdval(env, dstFD));
    DWORD chunkSize = (count > MAX_TRANSMIT_SIZE) ?
        MAX_TRANSMIT_SIZE : (DWORD)count;
    BOOL result;

    where.QuadPart = position;
    result = SetFilePointerEx(src, where, &where, FILE_BEGIN);
    if (result == 0) {
        JNU_ThrowIOExceptionWithLastError(env, "SetFilePointerEx failed");
        return IOS_THROWN;
    }

    result = TransmitFile(
        dst,
        src,
        chunkSize,
        PACKET_SIZE,
        NULL,
        NULL,
        TF_USE_KERNEL_APC
    );
    if (!result) {
        int error = WSAGetLastError();
        if (WSAEINVAL == error && count >= 0) {
            return IOS_UNSUPPORTED_CASE;
        }
        if (WSAENOTSOCK == error) {
            return IOS_UNSUPPORTED_CASE;
        }
        JNU_ThrowIOExceptionWithLastError(env, "transfer failed");
        return IOS_THROWN;
    }
    return chunkSize;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_setDirect0(JNIEnv *env, jclass this,
                                              jobject fdObj, jobject buffer)
{
    jint result = -1;

    HANDLE orig = (HANDLE)(handleval(env, fdObj));

    HANDLE modify = ReOpenFile(orig, 0, 0,
            FILE_FLAG_NO_BUFFERING | FILE_FLAG_WRITE_THROUGH);

    if (modify != INVALID_HANDLE_VALUE) {
        DWORD sectorsPerCluster;
        DWORD bytesPerSector;
        DWORD numberOfFreeClusters;
        DWORD totalNumberOfClusters;
        LPCWSTR lpRootPathName = (*env)->GetDirectBufferAddress(env, buffer);
        BOOL res = GetDiskFreeSpaceW(lpRootPathName,
                                     &sectorsPerCluster,
                                     &bytesPerSector,
                                     &numberOfFreeClusters,
                                     &totalNumberOfClusters);
        if (res == 0) {
            JNU_ThrowIOExceptionWithLastError(env, "DirectIO setup failed");
        }
        result = bytesPerSector;
    }
    return result;
}
