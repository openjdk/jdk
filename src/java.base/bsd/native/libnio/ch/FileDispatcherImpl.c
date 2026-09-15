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

// <sys/param.h> first: OpenBSD's <sys/mount.h> names u_char, uid_t and the
// rest without pulling in what declares them.
#include <sys/param.h>
#include <sys/mount.h>

#include "jni.h"
#include "nio.h"
#include "nio_util.h"
#include "sun_nio_ch_FileDispatcherImpl.h"

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

JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileDispatcherImpl_force0(JNIEnv *env, jobject this,
                                          jobject fdo, jboolean md)
{
    jint fd = fdval(env, fdo);
    int result = 0;

    /* F_FULLFSYNC is a macOS extension; see macosx for that path. */
    if (md == JNI_FALSE) {
        result = fdatasync(fd);
    } else {
        result = fsync(fd);
    }

    return handle(env, result, "Force failed");
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileDispatcherImpl_transferTo0(JNIEnv *env, jobject this,
                                               jobject srcFDO,
                                               jlong position, jlong count,
                                               jobject dstFDO, jboolean append)
{
    /*
     * NetBSD and OpenBSD have no sendfile(2).  Report the transfer as
     * unsupported and let the shared code read and write.
     */
#if defined(__NetBSD__) || defined(__OpenBSD__)
    return IOS_UNSUPPORTED;
#else
    jint srcFD = fdval(env, srcFDO);
    jint dstFD = fdval(env, dstFDO);

    off_t numBytes;
    int result;

#if defined(__FreeBSD__) || defined(__DragonFly__)
    /*
     * FreeBSD takes the count in by value and hands the tally back through
     * a separate argument, with a flags word after it:
     *
     *   int sendfile(int, int, off_t, size_t, struct sf_hdtr *, off_t *, int)
     *
     * macOS carries both directions in the single off_t * below.
     */
    numBytes = 0;

    result = sendfile(srcFD, dstFD, position, (size_t)count, NULL, &numBytes, 0);
#else
    numBytes = count;

    result = sendfile(srcFD, dstFD, position, &numBytes, NULL, 0);
#endif

    if (numBytes > 0)
        return numBytes;

    if (result == -1) {
        if (errno == EAGAIN)
            return IOS_UNAVAILABLE;
        if (errno == EOPNOTSUPP || errno == ENOTSOCK || errno == ENOTCONN)
            return IOS_UNSUPPORTED_CASE;
        if ((errno == EINVAL) && ((ssize_t)count >= 0))
            return IOS_UNSUPPORTED_CASE;
        if (errno == EINTR)
            return IOS_INTERRUPTED;
        JNU_ThrowIOExceptionWithLastError(env, "Transfer failed");
        return IOS_THROWN;
    }

    return result;
#endif
}
