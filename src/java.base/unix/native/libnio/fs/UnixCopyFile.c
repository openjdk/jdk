/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "jlong.h"

#include "nio.h"

#include <stdlib.h>
#include <unistd.h>
#include <errno.h>

#if defined(__linux__)
#include <sys/sendfile.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <linux/fs.h>
#include <sys/ioctl.h>
#elif defined(_ALLBSD_SOURCE)
#include <copyfile.h>
#include <sys/attr.h>
#include <sys/clonefile.h>
#endif
#include "sun_nio_fs_UnixCopyFile.h"

#ifndef FICLONE
#define FICLONE      1074041865
#endif

#if defined(__linux__)
typedef ssize_t copy_file_range_func(int, loff_t*, int, loff_t*, size_t,
                                     unsigned int);
static copy_file_range_func* my_copy_file_range_func = NULL;
#endif

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
Java_sun_nio_fs_UnixCopyFile_init
    (JNIEnv* env, jclass this)
{
#if defined(__linux__)
    my_copy_file_range_func =
        (copy_file_range_func*) dlsym(RTLD_DEFAULT, "copy_file_range");
#endif
}

#if defined(_ALLBSD_SOURCE)
int fcopyfile_callback(int what, int stage, copyfile_state_t state,
    const char* src, const char* dst, void* cancel)
{
    if (what == COPYFILE_COPY_DATA) {
        if (stage == COPYFILE_ERR
                || (stage == COPYFILE_PROGRESS && *((int*)cancel) != 0)) {
            // errno will be set to ECANCELED if the operation is cancelled,
            // or to the appropriate error number if there is an error,
            // but in either case we need to quit.
            return COPYFILE_QUIT;
        }
    }
    return COPYFILE_CONTINUE;
}
#endif

// Copy via file cloning
JNIEXPORT jint JNICALL
Java_sun_nio_fs_UnixCopyFile_cloneFile0
    (JNIEnv* env, jclass this, jlong sourceAddress, jlong targetAddress,
     jboolean followLinks)
{
    const char* src = (const char*)jlong_to_ptr(sourceAddress);
    const char* dst = (const char*)jlong_to_ptr(targetAddress);

#if defined(_ALLBSD_SOURCE)
    int flags = followLinks == JNI_FALSE ? CLONE_NOFOLLOW : 0;
    int res = clonefile(src, dst, flags);
    if (res < 0) {
        if (errno == ENOTSUP) { // cloning not supported by filesystem
            // disable further attempts to clone in this instance
            return IOS_UNSUPPORTED;
        } else if (errno == EXDEV   || // src and dst on different filesystems
                   errno == ENOTDIR) { // problematic path parameter(s)
            // cannot clone: fall back to direct or buffered copy
            return IOS_UNSUPPORTED_CASE;
        } else {
            // unrecoverable errors
            throwUnixException(env, errno);
            return IOS_THROWN;
        }
    }
    return 0;
#elif defined(__linux__)
    // disable ioctl_ficlone if copy_file_range() is available
    if (my_copy_file_range_func != NULL) {
        return IOS_UNSUPPORTED;
    }

    int srcFD = open(src, O_RDONLY);
    if (srcFD < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Open src failed");
        return IOS_THROWN;
    }
    int dstFD = open(dst, O_CREAT | O_WRONLY, 0666);
    if (dstFD < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Open dst failed");
        close(srcFD);
        return IOS_THROWN;
    }

    int res = ioctl(dstFD, FICLONE, srcFD);
    const int errno_ioctl = errno;

    // ignore close errors
    close(srcFD);
    close(dstFD);

    if (res != -1) {
        return 0;
    }

    if (errno_ioctl == EPERM) {
        // dst is immutable
        throwUnixException(env, errno_ioctl);
        return IOS_THROWN;
    }

    // delete dst to avoid later exception when re-creating in Java layer
    if (access(dst, F_OK) == 0) {
        if (unlink(dst) != 0) {
            const int errno_unlink = errno;
            if (access(dst, F_OK) == 0) {
                throwUnixException(env, errno_unlink);
                return IOS_THROWN;
            }
        }
    }

    if (errno_ioctl == EINVAL) {
        // interpret EINVAL as indicating that FICLONE is an invalid
        // ioctl request code hence unsupported on this platform;
        // disable ioctl_ficlone
        return IOS_UNSUPPORTED;
    }

    // cannot clone: fall back to direct or buffered copy
    return IOS_UNSUPPORTED_CASE;
#else
    return IOS_UNSUPPORTED;
#endif
}

// Copy via an intermediate temporary direct buffer
JNIEXPORT void JNICALL
Java_sun_nio_fs_UnixCopyFile_bufferedCopy0
    (JNIEnv* env, jclass this, jint dst, jint src, jlong address,
    jint transferSize, jlong cancelAddress)
{
    volatile jint* cancel = (jint*)jlong_to_ptr(cancelAddress);

    char* buf = (char*)jlong_to_ptr(address);

#if defined(__linux__)
    int advice = POSIX_FADV_SEQUENTIAL | // sequential data access
                 POSIX_FADV_NOREUSE    | // will access only once
                 POSIX_FADV_WILLNEED;    // will access in near future

    // ignore the return value hence any failure
    posix_fadvise(src, 0, 0, advice);
#endif

    for (;;) {
        ssize_t n, pos, len;
        RESTARTABLE(read((int)src, buf, transferSize), n);
        if (n <= 0) {
            if (n < 0)
                throwUnixException(env, errno);
            return;
        }
        if (cancel != NULL && *cancel != 0) {
            throwUnixException(env, ECANCELED);
            return;
        }
        pos = 0;
        len = n;
        do {
            char* bufp = buf;
            bufp += pos;
            RESTARTABLE(write((int)dst, bufp, len), n);
            if (n == -1) {
                throwUnixException(env, errno);
                return;
            }
            pos += n;
            len -= n;
        } while (len > 0);
    }
}

// Copy all bytes from src to dst, within the kernel if possible (Linux),
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
Java_sun_nio_fs_UnixCopyFile_directCopy0
    (JNIEnv* env, jclass this, jint dst, jint src, jlong cancelAddress)
{
    volatile jint* cancel = (jint*)jlong_to_ptr(cancelAddress);

#if defined(__linux__)
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
#elif defined(_ALLBSD_SOURCE)
    copyfile_state_t state;
    if (cancel != NULL) {
        state = copyfile_state_alloc();
        copyfile_state_set(state, COPYFILE_STATE_STATUS_CB, fcopyfile_callback);
        copyfile_state_set(state, COPYFILE_STATE_STATUS_CTX, (void*)cancel);
    } else {
        state = NULL;
    }
    if (fcopyfile(src, dst, state, COPYFILE_DATA) < 0) {
        int errno_fcopyfile = errno;
        if (state != NULL)
            copyfile_state_free(state);
        throwUnixException(env, errno_fcopyfile);
        return IOS_THROWN;
    }
    if (state != NULL)
        copyfile_state_free(state);

    return 0;
#else
    return IOS_UNSUPPORTED;
#endif
}
