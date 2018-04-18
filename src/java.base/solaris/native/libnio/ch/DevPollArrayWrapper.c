/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <sys/devpoll.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <poll.h>

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include "nio.h"
#include "nio_util.h"

#include "sun_nio_ch_DevPollArrayWrapper.h"

JNIEXPORT jint JNICALL
Java_sun_nio_ch_DevPollArrayWrapper_init(JNIEnv *env, jobject this)
{
    int wfd = open("/dev/poll", O_RDWR);
    if (wfd < 0) {
       JNU_ThrowIOExceptionWithLastError(env, "Error opening driver");
       return -1;
    }
    return wfd;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_DevPollArrayWrapper_register(JNIEnv *env, jobject this,
                                             jint wfd, jint fd, jint mask)
{
    struct pollfd a[1];
    int n;

    a[0].fd = fd;
    a[0].events = mask;
    a[0].revents = 0;

    n = write(wfd, &a[0], sizeof(a));
    if (n != sizeof(a)) {
        if (n < 0) {
            JNU_ThrowIOExceptionWithLastError(env, "Error writing pollfds");
        } else {
            JNU_ThrowIOException(env, "Unexpected number of bytes written");
        }
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_DevPollArrayWrapper_registerMultiple(JNIEnv *env, jobject this,
                                                     jint wfd, jlong address,
                                                     jint len)
{
    unsigned char *pollBytes = (unsigned char *)jlong_to_ptr(address);
    unsigned char *pollEnd = pollBytes + sizeof(struct pollfd) * len;
    while (pollBytes < pollEnd) {
        int bytesWritten = write(wfd, pollBytes, (int)(pollEnd - pollBytes));
        if (bytesWritten < 0) {
            JNU_ThrowIOExceptionWithLastError(env, "Error writing pollfds");
            return;
        }
        pollBytes += bytesWritten;
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_DevPollArrayWrapper_poll0(JNIEnv *env, jobject this,
                                          jlong address, jint numfds,
                                          jlong timeout, jint wfd)
{
    struct dvpoll a;
    void *pfd = (void *) jlong_to_ptr(address);
    int result;

    a.dp_fds = pfd;
    a.dp_nfds = numfds;
    a.dp_timeout = (int)timeout;
    result = ioctl(wfd, DP_POLL, &a);
    if (result < 0) {
        if (errno == EINTR) {
            return IOS_INTERRUPTED;
        } else {
            JNU_ThrowIOExceptionWithLastError(env, "Error reading driver");
            return IOS_THROWN;
        }
    }
    return result;
}
