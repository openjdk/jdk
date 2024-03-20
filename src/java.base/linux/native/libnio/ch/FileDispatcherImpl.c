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

#include <sys/sendfile.h>
#include <dlfcn.h>

#include "jni.h"
#include "nio.h"
#include "nio_util.h"
#include "sun_nio_ch_FileDispatcherImpl.h"

typedef ssize_t copy_file_range_func(int, loff_t*, int, loff_t*, size_t,
                                     unsigned int);
static copy_file_range_func* my_copy_file_range_func = NULL;

JNIEXPORT void JNICALL
Java_sun_nio_ch_FileDispatcherImpl_init0(JNIEnv *env, jclass klass)
{
    my_copy_file_range_func =
        (copy_file_range_func*) dlsym(RTLD_DEFAULT, "copy_file_range");
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcherImpl_transferFrom0(JNIEnv *env, jobject this,
                                              jobject srcFDO, jobject dstFDO,
                                              jlong position, jlong count,
                                              jboolean append)
{
    if (my_copy_file_range_func == NULL)
        return IOS_UNSUPPORTED;
    // copy_file_range fails with EBADF when appending
    if (append == JNI_TRUE)
        return IOS_UNSUPPORTED_CASE;

    jint srcFD = fdval(env, srcFDO);
    jint dstFD = fdval(env, dstFDO);

    loff_t offset = (loff_t)position;
    size_t len = (size_t)count;
    jlong n = my_copy_file_range_func(srcFD, NULL, dstFD, &offset, len, 0);
    if (n < 0) {
        if (errno == EAGAIN)
            return IOS_UNAVAILABLE;
        if (errno == ENOSYS)
            return IOS_UNSUPPORTED_CASE;
        if ((errno == EBADF || errno == EINVAL || errno == EXDEV) &&
            ((ssize_t)count >= 0))
            return IOS_UNSUPPORTED_CASE;
        if (errno == EINTR) {
            return IOS_INTERRUPTED;
        }
        JNU_ThrowIOExceptionWithLastError(env, "Transfer failed");
        return IOS_THROWN;
    }
    return n;
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcherImpl_transferTo0(JNIEnv *env, jobject this,
                                            jobject srcFDO,
                                            jlong position, jlong count,
                                            jobject dstFDO, jboolean append)
{
    jint srcFD = fdval(env, srcFDO);
    jint dstFD = fdval(env, dstFDO);

    // copy_file_range fails with EBADF when appending, and sendfile
    // fails with EINVAL
    if (append == JNI_TRUE)
        return IOS_UNSUPPORTED_CASE;

    loff_t offset = (loff_t)position;
    jlong n;
    if (my_copy_file_range_func != NULL) {
        size_t len = (size_t)count;
        n = my_copy_file_range_func(srcFD, &offset, dstFD, NULL, len, 0);
        if (n < 0) {
            switch (errno) {
                case EINTR:
                    return IOS_INTERRUPTED;
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
        if (n >= 0)
            return n;
    }

    n = sendfile64(dstFD, srcFD, &offset, (size_t)count);
    if (n < 0) {
        if (errno == EAGAIN)
            return IOS_UNAVAILABLE;
        if ((errno == EINVAL) && ((ssize_t)count >= 0))
            return IOS_UNSUPPORTED_CASE;
        if (errno == EINTR) {
            return IOS_INTERRUPTED;
        }
        JNU_ThrowIOExceptionWithLastError(env, "Transfer failed");
        return IOS_THROWN;
    }
    return n;
}
