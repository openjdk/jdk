/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include <sys/sendfile.h>
#include <fcntl.h>
#include <dlfcn.h>

#include "sun_nio_fs_LinuxFileSystem.h"

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
Java_sun_nio_fs_LinuxFileSystem_init
    (JNIEnv* env, jclass this)
{
    my_copy_file_range_func =
        (copy_file_range_func*) dlsym(RTLD_DEFAULT, "copy_file_range");
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
Java_sun_nio_fs_LinuxFileSystem_directCopy0
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
