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

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"

#include "nio.h"

#include <stdio.h>
#include <string.h>
#include <dlfcn.h>
#include <errno.h>
#include <mntent.h>
#include <fcntl.h>

#include <sys/sendfile.h>

#include "sun_nio_fs_LinuxNativeDispatcher.h"

static jfieldID entry_name;
static jfieldID entry_dir;
static jfieldID entry_fstype;
static jfieldID entry_options;

typedef ssize_t copy_file_range_func(int, loff_t*, int, loff_t*, size_t,
                                     unsigned int);
static copy_file_range_func* my_copy_file_range_func = NULL;

#define RESTARTABLE(_cmd, _result) do { \
  do { \
    _result = _cmd; \
  } while((_result == -1) && (errno == EINTR)); \
} while(0)

static void throwUnixException(JNIEnv* env, int errnum) {
    jobject x = JNU_NewObjectByName(env, "sun/nio/fs/UnixException",
        "(I)V", errnum);
    if (x != NULL) {
        (*env)->Throw(env, x);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_init(JNIEnv *env, jclass clazz)
{
    clazz = (*env)->FindClass(env, "sun/nio/fs/UnixMountEntry");
    CHECK_NULL(clazz);
    entry_name = (*env)->GetFieldID(env, clazz, "name", "[B");
    CHECK_NULL(entry_name);
    entry_dir = (*env)->GetFieldID(env, clazz, "dir", "[B");
    CHECK_NULL(entry_dir);
    entry_fstype = (*env)->GetFieldID(env, clazz, "fstype", "[B");
    CHECK_NULL(entry_fstype);
    entry_options = (*env)->GetFieldID(env, clazz, "opts", "[B");
    CHECK_NULL(entry_options);

    my_copy_file_range_func =
        (copy_file_range_func*) dlsym(RTLD_DEFAULT, "copy_file_range");
}

JNIEXPORT jlong JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_setmntent0(JNIEnv* env, jclass this, jlong pathAddress,
                                                 jlong modeAddress)
{
    FILE* fp = NULL;
    const char* path = (const char*)jlong_to_ptr(pathAddress);
    const char* mode = (const char*)jlong_to_ptr(modeAddress);

    do {
        fp = setmntent(path, mode);
    } while (fp == NULL && errno == EINTR);
    if (fp == NULL) {
        throwUnixException(env, errno);
    }
    return ptr_to_jlong(fp);
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_getmntent0(JNIEnv* env, jclass this,
    jlong value, jobject entry, jlong buffer, jint bufLen)
{
    struct mntent ent;
    char * buf = (char*)jlong_to_ptr(buffer);
    struct mntent* m;
    FILE* fp = jlong_to_ptr(value);
    jsize len;
    jbyteArray bytes;
    char* name;
    char* dir;
    char* fstype;
    char* options;

    m = getmntent_r(fp, &ent, buf, (int)bufLen);
    if (m == NULL)
        return -1;
    name = m->mnt_fsname;
    dir = m->mnt_dir;
    fstype = m->mnt_type;
    options = m->mnt_opts;

    len = strlen(name);
    bytes = (*env)->NewByteArray(env, len);
    if (bytes == NULL)
        return -1;
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (jbyte*)name);
    (*env)->SetObjectField(env, entry, entry_name, bytes);

    len = strlen(dir);
    bytes = (*env)->NewByteArray(env, len);
    if (bytes == NULL)
        return -1;
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (jbyte*)dir);
    (*env)->SetObjectField(env, entry, entry_dir, bytes);

    len = strlen(fstype);
    bytes = (*env)->NewByteArray(env, len);
    if (bytes == NULL)
        return -1;
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (jbyte*)fstype);
    (*env)->SetObjectField(env, entry, entry_fstype, bytes);

    len = strlen(options);
    bytes = (*env)->NewByteArray(env, len);
    if (bytes == NULL)
        return -1;
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (jbyte*)options);
    (*env)->SetObjectField(env, entry, entry_options, bytes);

    return 0;
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_endmntent(JNIEnv* env, jclass this, jlong stream)
{
    FILE* fp = jlong_to_ptr(stream);
    // The endmntent() function always returns 1.
    endmntent(fp);
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_posix_1fadvise(JNIEnv* env, jclass this,
    jint fd, jlong offset, jlong len, jint advice)
{
    return posix_fadvise((int)fd, (off_t)offset, (off_t)len, (int)advice);
}

// Copy all bytes from src to dst, within the kernel if possible,
// and return zero, otherwise return the appropriate status code.
//
// Return value
//   0 on success
//   IOS_UNAVAILABLE if the platform function would block
//   IOS_UNSUPPORTED_CASE if the call does not work with the given parameters
//   IOS_UNSUPPORTED if direct copying is not supported on this platform
//   IOS_THROWN if a Java exception is thrown
//
JNIEXPORT jint JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_directCopy0
    (JNIEnv* env, jclass this, jint dst, jint src, jlong cancelAddress)
{
    volatile jint* cancel = (jint*)jlong_to_ptr(cancelAddress);

    // Transfer within the kernel
    const size_t count = cancel != NULL ?
        1048576 :   // 1 MB to give cancellation a chance
        0x7ffff000; // maximum number of bytes that sendfile() can transfer

    ssize_t bytes_sent;
    if (my_copy_file_range_func != NULL) {
        do {
            RESTARTABLE(my_copy_file_range_func(src, NULL, dst, NULL, count, 0),
                                                bytes_sent);
            if (bytes_sent < 0) {
                switch (errno) {
                    case EINVAL:
                    case ENOSYS:
                    case EXDEV:
                        // ignore and try sendfile()
                        break;
                    default:
                        JNU_ThrowIOExceptionWithLastError(env, "Copy failed");
                        return IOS_THROWN;
                }
            }
            if (cancel != NULL && *cancel != 0) {
                throwUnixException(env, ECANCELED);
                return IOS_THROWN;
            }
        } while (bytes_sent > 0);

        if (bytes_sent == 0)
            return 0;
    }

    do {
        RESTARTABLE(sendfile64(dst, src, NULL, count), bytes_sent);
        if (bytes_sent < 0) {
            if (errno == EAGAIN)
                return IOS_UNAVAILABLE;
            if (errno == EINVAL || errno == ENOSYS)
                return IOS_UNSUPPORTED_CASE;
            throwUnixException(env, errno);
            return IOS_THROWN;
        }
        if (cancel != NULL && *cancel != 0) {
            throwUnixException(env, ECANCELED);
            return IOS_THROWN;
        }
    } while (bytes_sent > 0);

    return 0;
}
