/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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

#include <sys/mman.h>
#include <sys/uio.h>
#include <sys/stat.h>
#include <sys/statvfs.h>

#if defined(_ALLBSD_SOURCE)
#define lseek64 lseek
#define stat64 stat
#define flock64 flock
#define off64_t off_t
#define F_SETLKW64 F_SETLKW
#define F_SETLK64 F_SETLK
#define pread64 pread
#define pwrite64 pwrite
#define ftruncate64 ftruncate
#define fstat64 fstat
#define fdatasync fsync
#define mmap64 mmap
#define statvfs64 statvfs
#define fstatvfs64 fstatvfs
#endif

#if defined(__linux__)
#include <linux/fs.h>
#include <sys/ioctl.h>
#endif

#include "jni.h"
#include "nio.h"
#include "nio_util.h"
#include "sun_nio_ch_UnixFileDispatcherImpl.h"
#include "java_lang_Long.h"
#include <assert.h>

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_read0(JNIEnv *env, jclass clazz,
                             jobject fdo, jlong address, jint len)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);

    return convertReturnVal(env, read(fd, buf, len), JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_pread0(JNIEnv *env, jclass clazz, jobject fdo,
                            jlong address, jint len, jlong offset)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);

    return convertReturnVal(env, pread64(fd, buf, len, offset), JNI_TRUE);
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_readv0(JNIEnv *env, jclass clazz,
                              jobject fdo, jlong address, jint len)
{
    jint fd = fdval(env, fdo);
    struct iovec *iov = (struct iovec *)jlong_to_ptr(address);
    return convertLongReturnVal(env, readv(fd, iov, len), JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_write0(JNIEnv *env, jclass clazz,
                              jobject fdo, jlong address, jint len)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);

    return convertReturnVal(env, write(fd, buf, len), JNI_FALSE);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_pwrite0(JNIEnv *env, jclass clazz, jobject fdo,
                            jlong address, jint len, jlong offset)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);

    return convertReturnVal(env, pwrite64(fd, buf, len, offset), JNI_FALSE);
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_writev0(JNIEnv *env, jclass clazz,
                                       jobject fdo, jlong address, jint len)
{
    jint fd = fdval(env, fdo);
    struct iovec *iov = (struct iovec *)jlong_to_ptr(address);
    return convertLongReturnVal(env, writev(fd, iov, len), JNI_FALSE);
}

static jlong
handle(JNIEnv *env, jlong rv, char *msg)
{
    if (rv >= 0)
        return rv;
    if (errno == EINTR)
        return IOS_INTERRUPTED;
    JNU_ThrowIOExceptionWithLastError(env, msg);
    return IOS_THROWN;
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_seek0(JNIEnv *env, jclass clazz,
                                         jobject fdo, jlong offset)
{
    jint fd = fdval(env, fdo);
    off64_t result;
    if (offset < 0) {
        result = lseek64(fd, 0, SEEK_CUR);
    } else {
        result = lseek64(fd, offset, SEEK_SET);
    }
    return handle(env, (jlong)result, "lseek64 failed");
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_force0(JNIEnv *env, jobject this,
                                          jobject fdo, jboolean md)
{
    jint fd = fdval(env, fdo);
    int result = 0;

    if (md == JNI_FALSE) {
        result = fdatasync(fd);
    } else {
        result = fsync(fd);
    }

    return handle(env, result, "Force failed");
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_truncate0(JNIEnv *env, jobject this,
                                             jobject fdo, jlong size)
{
    return handle(env,
                  ftruncate64(fdval(env, fdo), size),
                  "Truncation failed");
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_size0(JNIEnv *env, jobject this, jobject fdo)
{
    jint fd = fdval(env, fdo);
    struct stat64 fbuf;

    if (fstat64(fd, &fbuf) < 0)
        return handle(env, -1, "Size failed");

#if defined(__linux__)
    if (S_ISBLK(fbuf.st_mode)) {
        uint64_t size;
        if (ioctl(fd, BLKGETSIZE64, &size) < 0)
            return handle(env, -1, "Size failed");
        return (jlong)size;
    }
#endif

    return fbuf.st_size;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_lock0(JNIEnv *env, jobject this, jobject fdo,
                                      jboolean block, jlong pos, jlong size,
                                      jboolean shared)
{
    jint fd = fdval(env, fdo);
    jint lockResult = 0;
    int cmd = 0;
    struct flock64 fl;

    fl.l_whence = SEEK_SET;
    if (size == (jlong)java_lang_Long_MAX_VALUE) {
        fl.l_len = (off64_t)0;
    } else {
        fl.l_len = (off64_t)size;
    }
    fl.l_start = (off64_t)pos;
    if (shared == JNI_TRUE) {
        fl.l_type = F_RDLCK;
    } else {
        fl.l_type = F_WRLCK;
    }
    if (block == JNI_TRUE) {
        cmd = F_SETLKW64;
    } else {
        cmd = F_SETLK64;
    }
    lockResult = fcntl(fd, cmd, &fl);
    if (lockResult < 0) {
        if ((cmd == F_SETLK64) && (errno == EAGAIN || errno == EACCES))
            return sun_nio_ch_UnixFileDispatcherImpl_NO_LOCK;
        if (errno == EINTR)
            return sun_nio_ch_UnixFileDispatcherImpl_INTERRUPTED;
        JNU_ThrowIOExceptionWithLastError(env, "Lock failed");
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_release0(JNIEnv *env, jobject this,
                                         jobject fdo, jlong pos, jlong size)
{
    jint fd = fdval(env, fdo);
    jint lockResult = 0;
    struct flock64 fl;
    int cmd = F_SETLK64;

    fl.l_whence = SEEK_SET;
    if (size == (jlong)java_lang_Long_MAX_VALUE) {
        fl.l_len = (off64_t)0;
    } else {
        fl.l_len = (off64_t)size;
    }
    fl.l_start = (off64_t)pos;
    fl.l_type = F_UNLCK;
    lockResult = fcntl(fd, cmd, &fl);
    if (lockResult < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Release failed");
    }
}

static void closeFileDescriptor(JNIEnv *env, int fd) {
    if (fd != -1) {
        int result = close(fd);
        if (result < 0)
            JNU_ThrowIOExceptionWithLastError(env, "Close failed");
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_closeIntFD(JNIEnv *env, jclass clazz, jint fd)
{
    closeFileDescriptor(env, fd);
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_allocationGranularity0(JNIEnv *env, jclass klass)
{
    jlong pageSize = sysconf(_SC_PAGESIZE);
    return pageSize;
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_map0(JNIEnv *env, jclass klass, jobject fdo,
                                        jint prot, jlong off, jlong len,
                                        jboolean map_sync)
{
    void *mapAddress = 0;
    jint fd = fdval(env, fdo);
    int protections = 0;
    int flags = 0;

    // should never be called with map_sync and prot == PRIVATE
    assert((prot != sun_nio_ch_UnixFileDispatcherImpl_MAP_PV) || !map_sync);

    if (prot == sun_nio_ch_UnixFileDispatcherImpl_MAP_RO) {
        protections = PROT_READ;
        flags = MAP_SHARED;
    } else if (prot == sun_nio_ch_UnixFileDispatcherImpl_MAP_RW) {
        protections = PROT_WRITE | PROT_READ;
        flags = MAP_SHARED;
    } else if (prot == sun_nio_ch_UnixFileDispatcherImpl_MAP_PV) {
        protections =  PROT_WRITE | PROT_READ;
        flags = MAP_PRIVATE;
    }

    // if MAP_SYNC and MAP_SHARED_VALIDATE are not defined then it is
    // best to define them here. This ensures the code compiles on old
    // OS releases which do not provide the relevant headers. If run
    // on the same machine then it will work if the kernel contains
    // the necessary support otherwise mmap should fail with an
    // invalid argument error

#ifndef MAP_SYNC
#define MAP_SYNC 0x80000
#endif
#ifndef MAP_SHARED_VALIDATE
#define MAP_SHARED_VALIDATE 0x03
#endif

    if (map_sync) {
        // ensure
        //  1) this is Linux on AArch64, x86_64, or PPC64 LE
        //  2) the mmap APIs are available at compile time
#if !defined(LINUX) || ! (defined(aarch64) || (defined(amd64) && defined(_LP64)) || defined(ppc64le))
        // TODO - implement for solaris/AIX/BSD/WINDOWS and for 32 bit
        JNU_ThrowInternalError(env, "should never call map on platform where MAP_SYNC is unimplemented");
        return IOS_THROWN;
#else
        flags |= MAP_SYNC | MAP_SHARED_VALIDATE;
#endif
    }

    mapAddress = mmap64(
        0,                    /* Let OS decide location */
        len,                  /* Number of bytes to map */
        protections,          /* File permissions */
        flags,                /* Changes are shared */
        fd,                   /* File descriptor of mapped file */
        off);                 /* Offset into file */

    if (mapAddress == MAP_FAILED) {
        if (map_sync && errno == ENOTSUP) {
            JNU_ThrowIOExceptionWithLastError(env, "map with mode MAP_SYNC unsupported");
            return IOS_THROWN;
        }

        if (errno == ENOMEM) {
            JNU_ThrowOutOfMemoryError(env, "Map failed");
            return IOS_THROWN;
        }
        return handle(env, -1, "Map failed");
    }

    return ((jlong) (unsigned long) mapAddress);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_unmap0(JNIEnv *env, jclass klass,
                                          jlong address, jlong len)
{
    void *a = (void *)jlong_to_ptr(address);
    return handle(env,
                  munmap(a, (size_t)len),
                  "Unmap failed");
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixFileDispatcherImpl_setDirect0(JNIEnv *env, jclass clazz,
                                              jobject fdo)
{
    jint fd = fdval(env, fdo);
    jint result;
    struct statvfs64 file_stat;

#if defined(O_DIRECT) || defined(F_NOCACHE) || defined(DIRECTIO_ON)
#ifdef O_DIRECT
    jint orig_flag;
    orig_flag = fcntl(fd, F_GETFL);
    if (orig_flag == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "DirectIO setup failed");
        return -1;
    }
    result = fcntl(fd, F_SETFL, orig_flag | O_DIRECT);
    if (result == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "DirectIO setup failed");
        return result;
    }
#elif defined(F_NOCACHE)
    result = fcntl(fd, F_NOCACHE, 1);
    if (result == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "DirectIO setup failed");
        return result;
    }
#elif defined(DIRECTIO_ON)
    result = directio(fd, DIRECTIO_ON);
    if (result == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "DirectIO setup failed");
        return result;
    }
#endif
    result = fstatvfs64(fd, &file_stat);
    if(result == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "DirectIO setup failed");
        return result;
    } else {
        result = (int)file_stat.f_frsize;
    }
#else
    result = -1;
#endif
    return result;
}
