/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <fcntl.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <dlfcn.h>
#include <sys/types.h>
#include <sys/stat.h>
#ifdef MACOSX
#include <sys/param.h>
#include <sys/mount.h>
#else
#include <sys/statvfs.h>
#endif
#include <sys/time.h>

#if defined(__linux__) || defined(_ALLBSD_SOURCE)
#include <sys/xattr.h>
#endif

/* For POSIX-compliant getpwuid_r */
#include <pwd.h>
#include <grp.h>

#ifdef __linux__
#include <sys/syscall.h>
#include <sys/sysmacros.h> // makedev macros
#endif

#if defined(_AIX)
  #define statvfs statvfs64
#endif

#if defined(__linux__)
// Account for the case where we compile on a system without statx
// support. We still want to ensure we can call statx at runtime
// if the runtime glibc version supports it (>= 2.28). We do this
// by defining binary compatible statx structs in this file and
// not relying on included headers.

#ifndef __GLIBC__
// Alpine doesn't know these types, define them
typedef unsigned int       __uint32_t;
typedef unsigned short     __uint16_t;
typedef unsigned long int  __uint64_t;
#endif

/*
 * Timestamp structure for the timestamps in struct statx.
 */
struct my_statx_timestamp {
        int64_t   tv_sec;
        __uint32_t  tv_nsec;
        int32_t   __reserved;
};

/*
 * struct statx used by statx system call on >= glibc 2.28
 * systems
 */
struct my_statx
{
  __uint32_t stx_mask;
  __uint32_t stx_blksize;
  __uint64_t stx_attributes;
  __uint32_t stx_nlink;
  __uint32_t stx_uid;
  __uint32_t stx_gid;
  __uint16_t stx_mode;
  __uint16_t __statx_pad1[1];
  __uint64_t stx_ino;
  __uint64_t stx_size;
  __uint64_t stx_blocks;
  __uint64_t stx_attributes_mask;
  struct my_statx_timestamp stx_atime;
  struct my_statx_timestamp stx_btime;
  struct my_statx_timestamp stx_ctime;
  struct my_statx_timestamp stx_mtime;
  __uint32_t stx_rdev_major;
  __uint32_t stx_rdev_minor;
  __uint32_t stx_dev_major;
  __uint32_t stx_dev_minor;
  __uint64_t __statx_pad2[14];
};

// statx masks, flags, constants

#ifndef AT_SYMLINK_NOFOLLOW
#define AT_SYMLINK_NOFOLLOW 0x100
#endif

#ifndef AT_STATX_SYNC_AS_STAT
#define AT_STATX_SYNC_AS_STAT 0x0000
#endif

#ifndef AT_EMPTY_PATH
#define AT_EMPTY_PATH 0x1000
#endif

#ifndef STATX_BASIC_STATS
#define STATX_BASIC_STATS 0x000007ffU
#endif

#ifndef STATX_BTIME
#define STATX_BTIME 0x00000800U
#endif

#ifndef STATX_ALL
#define STATX_ALL (STATX_BTIME | STATX_BASIC_STATS)
#endif

#ifndef AT_FDCWD
#define AT_FDCWD -100
#endif

#ifndef RTLD_DEFAULT
#define RTLD_DEFAULT RTLD_LOCAL
#endif

#define NO_FOLLOW_SYMLINK 1
#define FOLLOW_SYMLINK 0

#endif // __linux__


#include "jni.h"
#include "jni_util.h"
#include "jlong.h"

#include "sun_nio_fs_UnixNativeDispatcher.h"

/**
 * Size of password or group entry when not available via sysconf
 */
#define ENT_BUF_SIZE   1024

static jfieldID attrs_st_mode;
static jfieldID attrs_st_ino;
static jfieldID attrs_st_dev;
static jfieldID attrs_st_rdev;
static jfieldID attrs_st_nlink;
static jfieldID attrs_st_uid;
static jfieldID attrs_st_gid;
static jfieldID attrs_st_size;
static jfieldID attrs_st_atime_sec;
static jfieldID attrs_st_atime_nsec;
static jfieldID attrs_st_mtime_sec;
static jfieldID attrs_st_mtime_nsec;
static jfieldID attrs_st_ctime_sec;
static jfieldID attrs_st_ctime_nsec;

#if defined(_DARWIN_FEATURE_64_BIT_INODE) || defined(__linux__)
static jfieldID attrs_st_birthtime_sec;
#endif
#if defined(__linux__) // Linux has nsec granularity if supported
static jfieldID attrs_st_birthtime_nsec;
#endif

static jfieldID attrs_f_frsize;
static jfieldID attrs_f_blocks;
static jfieldID attrs_f_bfree;
static jfieldID attrs_f_bavail;

static jfieldID entry_name;
static jfieldID entry_dir;
static jfieldID entry_fstype;
static jfieldID entry_options;
static jfieldID entry_dev;

/**
 * System calls that may not be available at run time.
 */
typedef int openat_func(int, const char *, int, ...);
typedef int fstatat_func(int, const char *, struct stat *, int);
typedef int unlinkat_func(int, const char*, int);
typedef int renameat_func(int, const char*, int, const char*);
typedef int futimesat_func(int, const char *, const struct timeval *);
typedef int futimens_func(int, const struct timespec *);
typedef int lutimes_func(const char *, const struct timeval *);
typedef DIR* fdopendir_func(int);
#if defined(__linux__)
typedef int statx_func(int dirfd, const char *restrict pathname, int flags,
                       unsigned int mask, struct my_statx *restrict statxbuf);
#endif

static openat_func* my_openat_func = NULL;
static fstatat_func* my_fstatat_func = NULL;
static unlinkat_func* my_unlinkat_func = NULL;
static renameat_func* my_renameat_func = NULL;
static futimesat_func* my_futimesat_func = NULL;
static futimens_func* my_futimens_func = NULL;
static lutimes_func* my_lutimes_func = NULL;
static fdopendir_func* my_fdopendir_func = NULL;
#if defined(__linux__)
static statx_func* my_statx_func = NULL;
#endif

/**
 * fstatat missing from glibc on Linux.
 */
#if defined(__linux__) && (defined(__i386) || defined(__arm__))
#define FSTATAT64_SYSCALL_AVAILABLE
static int fstatat_wrapper(int dfd, const char *path,
                             struct stat *statbuf, int flag)
{
    #ifndef __NR_fstatat64
    #define __NR_fstatat64  300
    #endif
    return syscall(__NR_fstatat64, dfd, path, statbuf, flag);
}
#endif

#if defined(__linux__) && defined(_LP64) && defined(__NR_newfstatat)
#define FSTATAT64_SYSCALL_AVAILABLE
static int fstatat_wrapper(int dfd, const char *path,
                           struct stat *statbuf, int flag)
{
    return syscall(__NR_newfstatat, dfd, path, statbuf, flag);
}
#endif

#if defined(__linux__)
static int statx_wrapper(int dirfd, const char *restrict pathname, int flags,
                         unsigned int mask, struct my_statx *restrict statxbuf) {
    return (*my_statx_func)(dirfd, pathname, flags, mask, statxbuf);
}
#endif

/**
 * Call this to throw an internal UnixException when a system/library
 * call fails
 */
static void throwUnixException(JNIEnv* env, int errnum) {
    jobject x = JNU_NewObjectByName(env, "sun/nio/fs/UnixException",
        "(I)V", errnum);
    if (x != NULL) {
        (*env)->Throw(env, x);
    }
}

/**
 * Initialization
 */
JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_init(JNIEnv* env, jclass this)
{
    jint capabilities = 0;
    jclass clazz;

    clazz = (*env)->FindClass(env, "sun/nio/fs/UnixFileAttributes");
    CHECK_NULL_RETURN(clazz, 0);
    attrs_st_mode = (*env)->GetFieldID(env, clazz, "st_mode", "I");
    CHECK_NULL_RETURN(attrs_st_mode, 0);
    attrs_st_ino = (*env)->GetFieldID(env, clazz, "st_ino", "J");
    CHECK_NULL_RETURN(attrs_st_ino, 0);
    attrs_st_dev = (*env)->GetFieldID(env, clazz, "st_dev", "J");
    CHECK_NULL_RETURN(attrs_st_dev, 0);
    attrs_st_rdev = (*env)->GetFieldID(env, clazz, "st_rdev", "J");
    CHECK_NULL_RETURN(attrs_st_rdev, 0);
    attrs_st_nlink = (*env)->GetFieldID(env, clazz, "st_nlink", "I");
    CHECK_NULL_RETURN(attrs_st_nlink, 0);
    attrs_st_uid = (*env)->GetFieldID(env, clazz, "st_uid", "I");
    CHECK_NULL_RETURN(attrs_st_uid, 0);
    attrs_st_gid = (*env)->GetFieldID(env, clazz, "st_gid", "I");
    CHECK_NULL_RETURN(attrs_st_gid, 0);
    attrs_st_size = (*env)->GetFieldID(env, clazz, "st_size", "J");
    CHECK_NULL_RETURN(attrs_st_size, 0);
    attrs_st_atime_sec = (*env)->GetFieldID(env, clazz, "st_atime_sec", "J");
    CHECK_NULL_RETURN(attrs_st_atime_sec, 0);
    attrs_st_atime_nsec = (*env)->GetFieldID(env, clazz, "st_atime_nsec", "J");
    CHECK_NULL_RETURN(attrs_st_atime_nsec, 0);
    attrs_st_mtime_sec = (*env)->GetFieldID(env, clazz, "st_mtime_sec", "J");
    CHECK_NULL_RETURN(attrs_st_mtime_sec, 0);
    attrs_st_mtime_nsec = (*env)->GetFieldID(env, clazz, "st_mtime_nsec", "J");
    CHECK_NULL_RETURN(attrs_st_mtime_nsec, 0);
    attrs_st_ctime_sec = (*env)->GetFieldID(env, clazz, "st_ctime_sec", "J");
    CHECK_NULL_RETURN(attrs_st_ctime_sec, 0);
    attrs_st_ctime_nsec = (*env)->GetFieldID(env, clazz, "st_ctime_nsec", "J");
    CHECK_NULL_RETURN(attrs_st_ctime_nsec, 0);

#if defined(_DARWIN_FEATURE_64_BIT_INODE) || defined(__linux__)
    attrs_st_birthtime_sec = (*env)->GetFieldID(env, clazz, "st_birthtime_sec", "J");
    CHECK_NULL_RETURN(attrs_st_birthtime_sec, 0);
#endif
#if defined (__linux__) // Linux has nsec granularity
    attrs_st_birthtime_nsec = (*env)->GetFieldID(env, clazz, "st_birthtime_nsec", "J");
    CHECK_NULL_RETURN(attrs_st_birthtime_nsec, 0);
#endif

    clazz = (*env)->FindClass(env, "sun/nio/fs/UnixFileStoreAttributes");
    CHECK_NULL_RETURN(clazz, 0);
    attrs_f_frsize = (*env)->GetFieldID(env, clazz, "f_frsize", "J");
    CHECK_NULL_RETURN(attrs_f_frsize, 0);
    attrs_f_blocks = (*env)->GetFieldID(env, clazz, "f_blocks", "J");
    CHECK_NULL_RETURN(attrs_f_blocks, 0);
    attrs_f_bfree = (*env)->GetFieldID(env, clazz, "f_bfree", "J");
    CHECK_NULL_RETURN(attrs_f_bfree, 0);
    attrs_f_bavail = (*env)->GetFieldID(env, clazz, "f_bavail", "J");
    CHECK_NULL_RETURN(attrs_f_bavail, 0);

    clazz = (*env)->FindClass(env, "sun/nio/fs/UnixMountEntry");
    CHECK_NULL_RETURN(clazz, 0);
    entry_name = (*env)->GetFieldID(env, clazz, "name", "[B");
    CHECK_NULL_RETURN(entry_name, 0);
    entry_dir = (*env)->GetFieldID(env, clazz, "dir", "[B");
    CHECK_NULL_RETURN(entry_dir, 0);
    entry_fstype = (*env)->GetFieldID(env, clazz, "fstype", "[B");
    CHECK_NULL_RETURN(entry_fstype, 0);
    entry_options = (*env)->GetFieldID(env, clazz, "opts", "[B");
    CHECK_NULL_RETURN(entry_options, 0);
    entry_dev = (*env)->GetFieldID(env, clazz, "dev", "J");
    CHECK_NULL_RETURN(entry_dev, 0);

    /* system calls that might not be available at run time */

#if defined(_ALLBSD_SOURCE)
    my_openat_func = (openat_func*)dlsym(RTLD_DEFAULT, "openat");
    my_fstatat_func = (fstatat_func*)dlsym(RTLD_DEFAULT, "fstatat");
#else
    // Make sure we link to the 64-bit version of the functions
    my_openat_func = (openat_func*) dlsym(RTLD_DEFAULT, "openat64");
    my_fstatat_func = (fstatat_func*) dlsym(RTLD_DEFAULT, "fstatat64");
#endif
    my_unlinkat_func = (unlinkat_func*) dlsym(RTLD_DEFAULT, "unlinkat");
    my_renameat_func = (renameat_func*) dlsym(RTLD_DEFAULT, "renameat");
#ifndef _ALLBSD_SOURCE
    my_futimesat_func = (futimesat_func*) dlsym(RTLD_DEFAULT, "futimesat");
    my_lutimes_func = (lutimes_func*) dlsym(RTLD_DEFAULT, "lutimes");
#endif
    my_futimens_func = (futimens_func*) dlsym(RTLD_DEFAULT, "futimens");
#if defined(_AIX)
    // Make sure we link to the 64-bit version of the function
    my_fdopendir_func = (fdopendir_func*) dlsym(RTLD_DEFAULT, "fdopendir64");
#else
    my_fdopendir_func = (fdopendir_func*) dlsym(RTLD_DEFAULT, "fdopendir");
#endif

#if defined(FSTATAT64_SYSCALL_AVAILABLE)
    /* fstatat64 missing from glibc */
    if (my_fstatat_func == NULL)
        my_fstatat_func = (fstatat_func*)&fstatat_wrapper;
#endif

    /* supports futimes or futimesat, futimens, and/or lutimes */

#ifdef _ALLBSD_SOURCE
    capabilities |= sun_nio_fs_UnixNativeDispatcher_SUPPORTS_FUTIMES;
    capabilities |= sun_nio_fs_UnixNativeDispatcher_SUPPORTS_LUTIMES;
#else
    if (my_futimesat_func != NULL)
        capabilities |= sun_nio_fs_UnixNativeDispatcher_SUPPORTS_FUTIMES;
    if (my_lutimes_func != NULL)
        capabilities |= sun_nio_fs_UnixNativeDispatcher_SUPPORTS_LUTIMES;
#endif
    if (my_futimens_func != NULL)
        capabilities |= sun_nio_fs_UnixNativeDispatcher_SUPPORTS_FUTIMENS;

    /* supports openat, etc. */

    if (my_openat_func != NULL &&  my_fstatat_func != NULL &&
        my_unlinkat_func != NULL && my_renameat_func != NULL &&
        my_futimesat_func != NULL && my_fdopendir_func != NULL)
    {
        capabilities |= sun_nio_fs_UnixNativeDispatcher_SUPPORTS_OPENAT;
    }

    /* supports file birthtime */

#ifdef _DARWIN_FEATURE_64_BIT_INODE
    capabilities |= sun_nio_fs_UnixNativeDispatcher_SUPPORTS_BIRTHTIME;
#endif
#if defined(__linux__)
    my_statx_func = (statx_func*) dlsym(RTLD_DEFAULT, "statx");
    if (my_statx_func != NULL) {
        capabilities |= sun_nio_fs_UnixNativeDispatcher_SUPPORTS_BIRTHTIME;
    }
#endif

    /* supports extended attributes */

#if defined(_SYS_XATTR_H) || defined(_SYS_XATTR_H_)
    capabilities |= sun_nio_fs_UnixNativeDispatcher_SUPPORTS_XATTR;
#endif

    return capabilities;
}

JNIEXPORT jbyteArray JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_getcwd(JNIEnv* env, jclass this) {
    jbyteArray result = NULL;
    char buf[PATH_MAX+1];

    /* EINTR not listed as a possible error */
    char* cwd = getcwd(buf, sizeof(buf));
    if (cwd == NULL) {
        throwUnixException(env, errno);
    } else {
        jsize len = (jsize)strlen(buf);
        result = (*env)->NewByteArray(env, len);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, len, (jbyte*)buf);
        }
    }
    return result;
}

JNIEXPORT jbyteArray
Java_sun_nio_fs_UnixNativeDispatcher_strerror(JNIEnv* env, jclass this, jint error)
{
    char tmpbuf[1024];
    jsize len;
    jbyteArray bytes;

    getErrorString((int)errno, tmpbuf, sizeof(tmpbuf));
    len = strlen(tmpbuf);
    bytes = (*env)->NewByteArray(env, len);
    if (bytes != NULL) {
        (*env)->SetByteArrayRegion(env, bytes, 0, len, (jbyte*)tmpbuf);
    }
    return bytes;
}

JNIEXPORT jint
Java_sun_nio_fs_UnixNativeDispatcher_dup(JNIEnv* env, jclass this, jint fd) {

    int res = -1;

    RESTARTABLE(dup((int)fd), res);
    if (res == -1) {
        throwUnixException(env, errno);
    }
    return (jint)res;
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_rewind(JNIEnv* env, jclass this, jlong stream)
{
    FILE* fp = jlong_to_ptr(stream);
    int saved_errno;

    errno = 0;
    rewind(fp);
    saved_errno = errno;
    if (ferror(fp)) {
        throwUnixException(env, saved_errno);
    }
}

/**
 * This function returns line length without NUL terminator or -1 on EOF.
 */
JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_getlinelen(JNIEnv* env, jclass this, jlong stream)
{
    FILE* fp = jlong_to_ptr(stream);
    size_t lineSize = 0;
    char * lineBuffer = NULL;
    int saved_errno;

    ssize_t res = getline(&lineBuffer, &lineSize, fp);
    saved_errno = errno;

    /* Should free lineBuffer no matter result, according to man page */
    if (lineBuffer != NULL)
        free(lineBuffer);

    if (feof(fp))
        return -1;

    /* On successful return res >= 0, otherwise res is -1 */
    if (res == -1)
        throwUnixException(env, saved_errno);

    if (res > INT_MAX)
        throwUnixException(env, EOVERFLOW);

    return (jint)res;
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_open0(JNIEnv* env, jclass this,
    jlong pathAddress, jint oflags, jint mode)
{
    jint fd;
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    RESTARTABLE(open(path, (int)oflags, (mode_t)mode), fd);
    if (fd == -1) {
        throwUnixException(env, errno);
    }
    return fd;
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_openat0(JNIEnv* env, jclass this, jint dfd,
    jlong pathAddress, jint oflags, jint mode)
{
    jint fd;
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    if (my_openat_func == NULL) {
        JNU_ThrowInternalError(env, "should not reach here");
        return -1;
    }

    RESTARTABLE((*my_openat_func)(dfd, path, (int)oflags, (mode_t)mode), fd);
    if (fd == -1) {
        throwUnixException(env, errno);
    }
    return fd;
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_close0(JNIEnv* env, jclass this, jint fd) {
    int res;

#if defined(_AIX)
    /* AIX allows close to be restarted after EINTR */
    RESTARTABLE(close((int)fd), res);
#else
    res = close((int)fd);
#endif
    if (res == -1 && errno != EINTR) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_read0(JNIEnv* env, jclass this, jint fd,
    jlong address, jint nbytes)
{
    ssize_t n;
    void* bufp = jlong_to_ptr(address);
    RESTARTABLE(read((int)fd, bufp, (size_t)nbytes), n);
    if (n == -1) {
        throwUnixException(env, errno);
    }
    return (jint)n;
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_write0(JNIEnv* env, jclass this, jint fd,
    jlong address, jint nbytes)
{
    ssize_t n;
    void* bufp = jlong_to_ptr(address);
    RESTARTABLE(write((int)fd, bufp, (size_t)nbytes), n);
    if (n == -1) {
        throwUnixException(env, errno);
    }
    return (jint)n;
}

#if defined(__linux__)
/**
 * Copy statx members into sun.nio.fs.UnixFileAttributes
 */
static void copy_statx_attributes(JNIEnv* env, struct my_statx* buf, jobject attrs) {
    (*env)->SetIntField(env, attrs, attrs_st_mode, (jint)buf->stx_mode);
    (*env)->SetLongField(env, attrs, attrs_st_ino, (jlong)buf->stx_ino);
    (*env)->SetIntField(env, attrs, attrs_st_nlink, (jint)buf->stx_nlink);
    (*env)->SetIntField(env, attrs, attrs_st_uid, (jint)buf->stx_uid);
    (*env)->SetIntField(env, attrs, attrs_st_gid, (jint)buf->stx_gid);
    (*env)->SetLongField(env, attrs, attrs_st_size, (jlong)buf->stx_size);
    (*env)->SetLongField(env, attrs, attrs_st_atime_sec, (jlong)buf->stx_atime.tv_sec);
    (*env)->SetLongField(env, attrs, attrs_st_mtime_sec, (jlong)buf->stx_mtime.tv_sec);
    (*env)->SetLongField(env, attrs, attrs_st_ctime_sec, (jlong)buf->stx_ctime.tv_sec);
    (*env)->SetLongField(env, attrs, attrs_st_birthtime_sec, (jlong)buf->stx_btime.tv_sec);
    (*env)->SetLongField(env, attrs, attrs_st_birthtime_nsec, (jlong)buf->stx_btime.tv_nsec);
    (*env)->SetLongField(env, attrs, attrs_st_atime_nsec, (jlong)buf->stx_atime.tv_nsec);
    (*env)->SetLongField(env, attrs, attrs_st_mtime_nsec, (jlong)buf->stx_mtime.tv_nsec);
    (*env)->SetLongField(env, attrs, attrs_st_ctime_nsec, (jlong)buf->stx_ctime.tv_nsec);
    // convert statx major:minor to dev_t using makedev
    dev_t dev = makedev(buf->stx_dev_major, buf->stx_dev_minor);
    dev_t rdev = makedev(buf->stx_rdev_major, buf->stx_rdev_minor);
    (*env)->SetLongField(env, attrs, attrs_st_dev, (jlong)dev);
    (*env)->SetLongField(env, attrs, attrs_st_rdev, (jlong)rdev);
}
#endif

/**
 * Copy stat members into sun.nio.fs.UnixFileAttributes
 */
static void copy_stat_attributes(JNIEnv* env, struct stat* buf, jobject attrs) {
    (*env)->SetIntField(env, attrs, attrs_st_mode, (jint)buf->st_mode);
    (*env)->SetLongField(env, attrs, attrs_st_ino, (jlong)buf->st_ino);
    (*env)->SetLongField(env, attrs, attrs_st_dev, (jlong)buf->st_dev);
    (*env)->SetLongField(env, attrs, attrs_st_rdev, (jlong)buf->st_rdev);
    (*env)->SetIntField(env, attrs, attrs_st_nlink, (jint)buf->st_nlink);
    (*env)->SetIntField(env, attrs, attrs_st_uid, (jint)buf->st_uid);
    (*env)->SetIntField(env, attrs, attrs_st_gid, (jint)buf->st_gid);
    (*env)->SetLongField(env, attrs, attrs_st_size, (jlong)buf->st_size);
    (*env)->SetLongField(env, attrs, attrs_st_atime_sec, (jlong)buf->st_atime);
    (*env)->SetLongField(env, attrs, attrs_st_mtime_sec, (jlong)buf->st_mtime);
    (*env)->SetLongField(env, attrs, attrs_st_ctime_sec, (jlong)buf->st_ctime);

#ifdef _DARWIN_FEATURE_64_BIT_INODE
    (*env)->SetLongField(env, attrs, attrs_st_birthtime_sec, (jlong)buf->st_birthtime);
    // rely on default value of 0 for st_birthtime_nsec field on Darwin
#endif

#ifndef MACOSX
    (*env)->SetLongField(env, attrs, attrs_st_atime_nsec, (jlong)buf->st_atim.tv_nsec);
    (*env)->SetLongField(env, attrs, attrs_st_mtime_nsec, (jlong)buf->st_mtim.tv_nsec);
    (*env)->SetLongField(env, attrs, attrs_st_ctime_nsec, (jlong)buf->st_ctim.tv_nsec);
#else
    (*env)->SetLongField(env, attrs, attrs_st_atime_nsec, (jlong)buf->st_atimespec.tv_nsec);
    (*env)->SetLongField(env, attrs, attrs_st_mtime_nsec, (jlong)buf->st_mtimespec.tv_nsec);
    (*env)->SetLongField(env, attrs, attrs_st_ctime_nsec, (jlong)buf->st_ctimespec.tv_nsec);
#endif
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_stat0(JNIEnv* env, jclass this,
    jlong pathAddress, jobject attrs)
{
    int err;
    struct stat buf;
    const char* path = (const char*)jlong_to_ptr(pathAddress);
#if defined(__linux__)
    struct my_statx statx_buf;
    int flags = AT_STATX_SYNC_AS_STAT;
    unsigned int mask = STATX_ALL;

    if (my_statx_func != NULL) {
        // Prefer statx over stat on Linux if it's available
        RESTARTABLE(statx_wrapper(AT_FDCWD, path, flags, mask, &statx_buf), err);
        if (err == 0) {
            copy_statx_attributes(env, &statx_buf, attrs);
            return 0;
        } else {
            return errno;
        }
    }
#endif
    RESTARTABLE(stat(path, &buf), err);
    if (err == 0) {
        copy_stat_attributes(env, &buf, attrs);
        return 0;
    } else {
        return errno;
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_lstat0(JNIEnv* env, jclass this,
    jlong pathAddress, jobject attrs)
{
    int err;
    struct stat buf;
    const char* path = (const char*)jlong_to_ptr(pathAddress);
#if defined(__linux__)
    struct my_statx statx_buf;
    int flags = AT_STATX_SYNC_AS_STAT | AT_SYMLINK_NOFOLLOW;
    unsigned int mask = STATX_ALL;

    if (my_statx_func != NULL) {
        // Prefer statx over stat on Linux if it's available
        RESTARTABLE(statx_wrapper(AT_FDCWD, path, flags, mask, &statx_buf), err);
        if (err == 0) {
            copy_statx_attributes(env, &statx_buf, attrs);
        } else {
            throwUnixException(env, errno);
        }
        // statx was available, so return now
        return;
    }
#endif
    RESTARTABLE(lstat(path, &buf), err);
    if (err == -1) {
        throwUnixException(env, errno);
    } else {
        copy_stat_attributes(env, &buf, attrs);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_fstat0(JNIEnv* env, jclass this, jint fd,
    jobject attrs)
{
    int err;
    struct stat buf;
#if defined(__linux__)
    struct my_statx statx_buf;
    int flags = AT_EMPTY_PATH | AT_STATX_SYNC_AS_STAT;
    unsigned int mask = STATX_ALL;

    if (my_statx_func != NULL) {
        // statx supports FD use via dirfd iff pathname is an empty string and the
        // AT_EMPTY_PATH flag is specified in flags
        RESTARTABLE(statx_wrapper((int)fd, "", flags, mask, &statx_buf), err);
        if (err == 0) {
            copy_statx_attributes(env, &statx_buf, attrs);
        } else {
            throwUnixException(env, errno);
        }
        // statx was available, so return now
        return;
    }
#endif
    RESTARTABLE(fstat((int)fd, &buf), err);
    if (err == -1) {
        throwUnixException(env, errno);
    } else {
        copy_stat_attributes(env, &buf, attrs);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_fstatat0(JNIEnv* env, jclass this, jint dfd,
    jlong pathAddress, jint flag, jobject attrs)
{
    int err;
    struct stat buf;
    const char* path = (const char*)jlong_to_ptr(pathAddress);
#if defined(__linux__)
    struct my_statx statx_buf;
    int flags = AT_STATX_SYNC_AS_STAT;
    unsigned int mask = STATX_ALL;

    if (my_statx_func != NULL) {
        // Prefer statx over stat on Linux if it's available
        if (((int)flag & AT_SYMLINK_NOFOLLOW) > 0) { // flag set in java code
            flags |= AT_SYMLINK_NOFOLLOW;
        }
        RESTARTABLE(statx_wrapper((int)dfd, path, flags, mask, &statx_buf), err);
        if (err == 0) {
            copy_statx_attributes(env, &statx_buf, attrs);
        } else {
            throwUnixException(env, errno);
        }
        // statx was available, so return now
        return;
    }
#endif

    if (my_fstatat_func == NULL) {
        JNU_ThrowInternalError(env, "should not reach here");
        return;
    }
    RESTARTABLE((*my_fstatat_func)((int)dfd, path, &buf, (int)flag), err);
    if (err == -1) {
        throwUnixException(env, errno);
    } else {
        copy_stat_attributes(env, &buf, attrs);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_chmod0(JNIEnv* env, jclass this,
    jlong pathAddress, jint mode)
{
    int err;
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    RESTARTABLE(chmod(path, (mode_t)mode), err);
    if (err == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_fchmod0(JNIEnv* env, jclass this, jint filedes,
    jint mode)
{
    int err;

    RESTARTABLE(fchmod((int)filedes, (mode_t)mode), err);
    if (err == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_chown0(JNIEnv* env, jclass this,
    jlong pathAddress, jint uid, jint gid)
{
    int err;
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    RESTARTABLE(chown(path, (uid_t)uid, (gid_t)gid), err);
    if (err == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_lchown0(JNIEnv* env, jclass this, jlong pathAddress, jint uid, jint gid)
{
    int err;
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    RESTARTABLE(lchown(path, (uid_t)uid, (gid_t)gid), err);
    if (err == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_fchown0(JNIEnv* env, jclass this, jint filedes, jint uid, jint gid)
{
    int err;

    RESTARTABLE(fchown(filedes, (uid_t)uid, (gid_t)gid), err);
    if (err == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_utimes0(JNIEnv* env, jclass this,
    jlong pathAddress, jlong accessTime, jlong modificationTime)
{
    int err;
    struct timeval times[2];
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    times[0].tv_sec = accessTime / 1000000;
    times[0].tv_usec = accessTime % 1000000;

    times[1].tv_sec = modificationTime / 1000000;
    times[1].tv_usec = modificationTime % 1000000;

    RESTARTABLE(utimes(path, &times[0]), err);
    if (err == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_futimes0(JNIEnv* env, jclass this, jint filedes,
    jlong accessTime, jlong modificationTime)
{
    struct timeval times[2];
    int err = 0;

    times[0].tv_sec = accessTime / 1000000;
    times[0].tv_usec = accessTime % 1000000;

    times[1].tv_sec = modificationTime / 1000000;
    times[1].tv_usec = modificationTime % 1000000;

#ifdef _ALLBSD_SOURCE
    RESTARTABLE(futimes(filedes, &times[0]), err);
#else
    if (my_futimesat_func == NULL) {
        JNU_ThrowInternalError(env, "my_futimesat_func is NULL");
        return;
    }
    RESTARTABLE((*my_futimesat_func)(filedes, NULL, &times[0]), err);
#endif
    if (err == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_futimens0(JNIEnv* env, jclass this, jint filedes,
    jlong accessTime, jlong modificationTime)
{
    struct timespec times[2];
    int err = 0;

    times[0].tv_sec = accessTime / 1000000000;
    times[0].tv_nsec = accessTime % 1000000000;

    times[1].tv_sec = modificationTime / 1000000000;
    times[1].tv_nsec = modificationTime % 1000000000;

    if (my_futimens_func == NULL) {
        JNU_ThrowInternalError(env, "my_futimens_func is NULL");
        return;
    }
    RESTARTABLE((*my_futimens_func)(filedes, &times[0]), err);
    if (err == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_lutimes0(JNIEnv* env, jclass this,
    jlong pathAddress, jlong accessTime, jlong modificationTime)
{
    int err;
    struct timeval times[2];
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    times[0].tv_sec = accessTime / 1000000;
    times[0].tv_usec = accessTime % 1000000;

    times[1].tv_sec = modificationTime / 1000000;
    times[1].tv_usec = modificationTime % 1000000;

#ifdef _ALLBSD_SOURCE
    RESTARTABLE(lutimes(path, &times[0]), err);
#else
    if (my_lutimes_func == NULL) {
        JNU_ThrowInternalError(env, "my_lutimes_func is NULL");
        return;
    }
    RESTARTABLE((*my_lutimes_func)(path, &times[0]), err);
#endif
    if (err == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT jlong JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_opendir0(JNIEnv* env, jclass this,
    jlong pathAddress)
{
    DIR* dir;
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    /* EINTR not listed as a possible error */
    dir = opendir(path);
    if (dir == NULL) {
        throwUnixException(env, errno);
    }
    return ptr_to_jlong(dir);
}

JNIEXPORT jlong JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_fdopendir(JNIEnv* env, jclass this, int dfd) {
    DIR* dir;

    if (my_fdopendir_func == NULL) {
        JNU_ThrowInternalError(env, "should not reach here");
        return (jlong)-1;
    }

    /* EINTR not listed as a possible error */
    dir = (*my_fdopendir_func)((int)dfd);
    if (dir == NULL) {
        throwUnixException(env, errno);
    }
    return ptr_to_jlong(dir);
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_closedir(JNIEnv* env, jclass this, jlong dir) {
    DIR* dirp = jlong_to_ptr(dir);

    if (closedir(dirp) == -1 && errno != EINTR) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT jbyteArray JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_readdir0(JNIEnv* env, jclass this, jlong value) {
    DIR* dirp = jlong_to_ptr(value);
    struct dirent* ptr;

    errno = 0;
    ptr = readdir(dirp);
    if (ptr == NULL) {
        if (errno != 0) {
            throwUnixException(env, errno);
        }
        return NULL;
    } else {
        jsize len = strlen(ptr->d_name);
        jbyteArray bytes = (*env)->NewByteArray(env, len);
        if (bytes != NULL) {
            (*env)->SetByteArrayRegion(env, bytes, 0, len, (jbyte*)(ptr->d_name));
        }
        return bytes;
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_mkdir0(JNIEnv* env, jclass this,
    jlong pathAddress, jint mode)
{
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    /* EINTR not listed as a possible error */
    if (mkdir(path, (mode_t)mode) == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_rmdir0(JNIEnv* env, jclass this,
    jlong pathAddress)
{
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    /* EINTR not listed as a possible error */
    if (rmdir(path) == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_link0(JNIEnv* env, jclass this,
    jlong existingAddress, jlong newAddress)
{
    int err;
    const char* existing = (const char*)jlong_to_ptr(existingAddress);
    const char* newname = (const char*)jlong_to_ptr(newAddress);

    RESTARTABLE(link(existing, newname), err);
    if (err == -1) {
        throwUnixException(env, errno);
    }
}


JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_unlink0(JNIEnv* env, jclass this,
    jlong pathAddress)
{
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    /* EINTR not listed as a possible error */
    if (unlink(path) == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_unlinkat0(JNIEnv* env, jclass this, jint dfd,
                                               jlong pathAddress, jint flags)
{
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    if (my_unlinkat_func == NULL) {
        JNU_ThrowInternalError(env, "should not reach here");
        return;
    }

    /* EINTR not listed as a possible error */
    if ((*my_unlinkat_func)((int)dfd, path, (int)flags) == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_rename0(JNIEnv* env, jclass this,
    jlong fromAddress, jlong toAddress)
{
    const char* from = (const char*)jlong_to_ptr(fromAddress);
    const char* to = (const char*)jlong_to_ptr(toAddress);

    /* EINTR not listed as a possible error */
    if (rename(from, to) == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_renameat0(JNIEnv* env, jclass this,
    jint fromfd, jlong fromAddress, jint tofd, jlong toAddress)
{
    const char* from = (const char*)jlong_to_ptr(fromAddress);
    const char* to = (const char*)jlong_to_ptr(toAddress);

    if (my_renameat_func == NULL) {
        JNU_ThrowInternalError(env, "should not reach here");
        return;
    }

    /* EINTR not listed as a possible error */
    if ((*my_renameat_func)((int)fromfd, from, (int)tofd, to) == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_symlink0(JNIEnv* env, jclass this,
    jlong targetAddress, jlong linkAddress)
{
    const char* target = (const char*)jlong_to_ptr(targetAddress);
    const char* link = (const char*)jlong_to_ptr(linkAddress);

    /* EINTR not listed as a possible error */
    if (symlink(target, link) == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT jbyteArray JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_readlink0(JNIEnv* env, jclass this,
    jlong pathAddress)
{
    jbyteArray result = NULL;
    char target[PATH_MAX+1];
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    /* EINTR not listed as a possible error */
    int n = readlink(path, target, sizeof(target));
    if (n == -1) {
        throwUnixException(env, errno);
    } else {
        jsize len;
        if (n == sizeof(target)) {
            /* Traditionally readlink(2) should not return more than */
            /* PATH_MAX bytes (no terminating null byte is appended). */
            throwUnixException(env, ENAMETOOLONG);
            return NULL;
        }
        target[n] = '\0';
        len = (jsize)strlen(target);
        result = (*env)->NewByteArray(env, len);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, len, (jbyte*)target);
        }
    }
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_realpath0(JNIEnv* env, jclass this,
    jlong pathAddress)
{
    jbyteArray result = NULL;
    char resolved[PATH_MAX+1];
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    /* EINTR not listed as a possible error */
    if (realpath(path, resolved) == NULL) {
        throwUnixException(env, errno);
    } else {
        jsize len = (jsize)strlen(resolved);
        result = (*env)->NewByteArray(env, len);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, len, (jbyte*)resolved);
        }
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_access0(JNIEnv* env, jclass this,
    jlong pathAddress, jint amode)
{
    int err;
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    RESTARTABLE(access(path, (int)amode), err);

    return (err == -1) ? errno : 0;
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_statvfs0(JNIEnv* env, jclass this,
    jlong pathAddress, jobject attrs)
{
    int err;
#ifdef MACOSX
    struct statfs buf;
#else
    struct statvfs buf;
#endif
    const char* path = (const char*)jlong_to_ptr(pathAddress);

#ifdef MACOSX
    RESTARTABLE(statfs(path, &buf), err);
#else
    RESTARTABLE(statvfs(path, &buf), err);
#endif
    if (err == -1) {
        throwUnixException(env, errno);
    } else {
#ifdef _AIX
        /* AIX returns ULONG_MAX in buf.f_blocks for the /proc file system. */
        /* This is too big for a Java signed long and fools various tests.  */
        if (buf.f_blocks == ULONG_MAX) {
            buf.f_blocks = 0;
        }
        /* The number of free or available blocks can never exceed the total number of blocks */
        if (buf.f_blocks == 0) {
            buf.f_bfree = 0;
            buf.f_bavail = 0;
        }
#endif
#ifdef MACOSX
        (*env)->SetLongField(env, attrs, attrs_f_frsize, long_to_jlong(buf.f_bsize));
#else
        (*env)->SetLongField(env, attrs, attrs_f_frsize, long_to_jlong(buf.f_frsize));
#endif
        (*env)->SetLongField(env, attrs, attrs_f_blocks, long_to_jlong(buf.f_blocks));
        (*env)->SetLongField(env, attrs, attrs_f_bfree,  long_to_jlong(buf.f_bfree));
        (*env)->SetLongField(env, attrs, attrs_f_bavail, long_to_jlong(buf.f_bavail));
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_mknod0(JNIEnv* env, jclass this,
    jlong pathAddress, jint mode, jlong dev)
{
    int err;
    const char* path = (const char*)jlong_to_ptr(pathAddress);

    RESTARTABLE(mknod(path, (mode_t)mode, (dev_t)dev), err);
    if (err == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT jbyteArray JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_getpwuid(JNIEnv* env, jclass this, jint uid)
{
    jbyteArray result = NULL;
    int buflen;
    char* pwbuf;

    /* allocate buffer for password record */
    buflen = (int)sysconf(_SC_GETPW_R_SIZE_MAX);
    if (buflen == -1)
        buflen = ENT_BUF_SIZE;
    pwbuf = (char*)malloc(buflen);
    if (pwbuf == NULL) {
        JNU_ThrowOutOfMemoryError(env, "native heap");
    } else {
        struct passwd pwent;
        struct passwd* p = NULL;
        int res = 0;

        errno = 0;
        RESTARTABLE(getpwuid_r((uid_t)uid, &pwent, pwbuf, (size_t)buflen, &p), res);

        if (res != 0 || p == NULL || p->pw_name == NULL || *(p->pw_name) == '\0') {
            /* not found or error */
            if (errno == 0)
                errno = ENOENT;
            throwUnixException(env, errno);
        } else {
            jsize len = strlen(p->pw_name);
            result = (*env)->NewByteArray(env, len);
            if (result != NULL) {
                (*env)->SetByteArrayRegion(env, result, 0, len, (jbyte*)(p->pw_name));
            }
        }
        free(pwbuf);
    }

    return result;
}


JNIEXPORT jbyteArray JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_getgrgid(JNIEnv* env, jclass this, jint gid)
{
    jbyteArray result = NULL;
    int buflen;
    int retry;

    /* initial size of buffer for group record */
    buflen = (int)sysconf(_SC_GETGR_R_SIZE_MAX);
    if (buflen == -1)
        buflen = ENT_BUF_SIZE;

    do {
        struct group grent;
        struct group* g = NULL;
        int res = 0;

        char* grbuf = (char*)malloc(buflen);
        if (grbuf == NULL) {
            JNU_ThrowOutOfMemoryError(env, "native heap");
            return NULL;
        }

        errno = 0;
        RESTARTABLE(getgrgid_r((gid_t)gid, &grent, grbuf, (size_t)buflen, &g), res);

        retry = 0;
        if (res != 0 || g == NULL || g->gr_name == NULL || *(g->gr_name) == '\0') {
            /* not found or error */
            if (errno == ERANGE) {
                /* insufficient buffer size so need larger buffer */
                buflen += ENT_BUF_SIZE;
                retry = 1;
            } else {
                if (errno == 0)
                    errno = ENOENT;
                throwUnixException(env, errno);
            }
        } else {
            jsize len = strlen(g->gr_name);
            result = (*env)->NewByteArray(env, len);
            if (result != NULL) {
                (*env)->SetByteArrayRegion(env, result, 0, len, (jbyte*)(g->gr_name));
            }
        }

        free(grbuf);

    } while (retry);

    return result;
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_getpwnam0(JNIEnv* env, jclass this,
    jlong nameAddress)
{
    jint uid = -1;
    int buflen;
    char* pwbuf;

    /* allocate buffer for password record */
    buflen = (int)sysconf(_SC_GETPW_R_SIZE_MAX);
    if (buflen == -1)
        buflen = ENT_BUF_SIZE;
    pwbuf = (char*)malloc(buflen);
    if (pwbuf == NULL) {
        JNU_ThrowOutOfMemoryError(env, "native heap");
    } else {
        struct passwd pwent;
        struct passwd* p = NULL;
        int res = 0;
        const char* name = (const char*)jlong_to_ptr(nameAddress);

        errno = 0;
        RESTARTABLE(getpwnam_r(name, &pwent, pwbuf, (size_t)buflen, &p), res);

        if (res != 0 || p == NULL || p->pw_name == NULL || *(p->pw_name) == '\0') {
            /* not found or error */
            if (errno != 0 && errno != ENOENT && errno != ESRCH &&
                errno != EBADF && errno != EPERM)
            {
                throwUnixException(env, errno);
            }
        } else {
            uid = p->pw_uid;
        }
        free(pwbuf);
    }

    return uid;
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_getgrnam0(JNIEnv* env, jclass this,
    jlong nameAddress)
{
    jint gid = -1;
    int buflen, retry;

    /* initial size of buffer for group record */
    buflen = (int)sysconf(_SC_GETGR_R_SIZE_MAX);
    if (buflen == -1)
        buflen = ENT_BUF_SIZE;

    do {
        struct group grent;
        struct group* g = NULL;
        int res = 0;
        char *grbuf;
        const char* name = (const char*)jlong_to_ptr(nameAddress);

        grbuf = (char*)malloc(buflen);
        if (grbuf == NULL) {
            JNU_ThrowOutOfMemoryError(env, "native heap");
            return -1;
        }

        errno = 0;
        RESTARTABLE(getgrnam_r(name, &grent, grbuf, (size_t)buflen, &g), res);

        retry = 0;
        if (res != 0 || g == NULL || g->gr_name == NULL || *(g->gr_name) == '\0') {
            /* not found or error */
            if (errno != 0 && errno != ENOENT && errno != ESRCH &&
                errno != EBADF && errno != EPERM)
            {
                if (errno == ERANGE) {
                    /* insufficient buffer size so need larger buffer */
                    buflen += ENT_BUF_SIZE;
                    retry = 1;
                } else {
                    throwUnixException(env, errno);
                }
            }
        } else {
            gid = g->gr_gid;
        }

        free(grbuf);

    } while (retry);

    return gid;
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_fgetxattr0(JNIEnv* env, jclass clazz,
    jint fd, jlong nameAddress, jlong valueAddress, jint valueLen)
{
    size_t res = -1;
    const char* name = jlong_to_ptr(nameAddress);
    void* value = jlong_to_ptr(valueAddress);

#ifdef __linux__
    res = fgetxattr(fd, name, value, valueLen);
#elif defined(_ALLBSD_SOURCE)
    res = fgetxattr(fd, name, value, valueLen, 0, 0);
#else
    throwUnixException(env, ENOTSUP);
#endif

    if (res == (size_t)-1)
        throwUnixException(env, errno);
    return (jint)res;
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_fsetxattr0(JNIEnv* env, jclass clazz,
    jint fd, jlong nameAddress, jlong valueAddress, jint valueLen)
{
    int res = -1;
    const char* name = jlong_to_ptr(nameAddress);
    void* value = jlong_to_ptr(valueAddress);

#ifdef __linux__
    res = fsetxattr(fd, name, value, valueLen, 0);
#elif defined(_ALLBSD_SOURCE)
    res = fsetxattr(fd, name, value, valueLen, 0, 0);
#else
    throwUnixException(env, ENOTSUP);
#endif

    if (res == -1)
        throwUnixException(env, errno);
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_fremovexattr0(JNIEnv* env, jclass clazz,
    jint fd, jlong nameAddress)
{
    int res = -1;
    const char* name = jlong_to_ptr(nameAddress);

#ifdef __linux__
    res = fremovexattr(fd, name);
#elif defined(_ALLBSD_SOURCE)
    res = fremovexattr(fd, name, 0);
#else
    throwUnixException(env, ENOTSUP);
#endif

    if (res == -1)
        throwUnixException(env, errno);
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixNativeDispatcher_flistxattr(JNIEnv* env, jclass clazz,
    jint fd, jlong listAddress, jint size)
{
    size_t res = -1;
    char* list = jlong_to_ptr(listAddress);

#ifdef __linux__
    res = flistxattr(fd, list, (size_t)size);
#elif defined(_ALLBSD_SOURCE)
    res = flistxattr(fd, list, (size_t)size, 0);
#else
    throwUnixException(env, ENOTSUP);
#endif

    if (res == (size_t)-1)
        throwUnixException(env, errno);
    return (jint)res;
}
