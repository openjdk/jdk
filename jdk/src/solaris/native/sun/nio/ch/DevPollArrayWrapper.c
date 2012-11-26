/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "sun_nio_ch_DevPollArrayWrapper.h"
#include <sys/poll.h>
#include <unistd.h>
#include <sys/time.h>

#ifdef  __cplusplus
extern "C" {
#endif

typedef uint32_t        caddr32_t;

/* /dev/poll ioctl */
#define         DPIOC   (0xD0 << 8)
#define DP_POLL         (DPIOC | 1)     /* poll on fds in cached in /dev/poll */
#define DP_ISPOLLED     (DPIOC | 2)     /* is this fd cached in /dev/poll */
#define DEVPOLLSIZE     1000            /* /dev/poll table size increment */
#define POLLREMOVE      0x0800          /* Removes fd from monitored set */

/*
 * /dev/poll DP_POLL ioctl format
 */
typedef struct dvpoll {
        pollfd_t        *dp_fds;        /* pollfd array */
        nfds_t          dp_nfds;        /* num of pollfd's in dp_fds[] */
        int             dp_timeout;     /* time out in millisec */
} dvpoll_t;

typedef struct dvpoll32 {
        caddr32_t       dp_fds;         /* pollfd array */
        uint32_t        dp_nfds;        /* num of pollfd's in dp_fds[] */
        int32_t         dp_timeout;     /* time out in millisec */
} dvpoll32_t;

#ifdef  __cplusplus
}
#endif

#define RESTARTABLE(_cmd, _result) do { \
  do { \
    _result = _cmd; \
  } while((_result == -1) && (errno == EINTR)); \
} while(0)

static int
idevpoll(jint wfd, int dpctl, struct dvpoll a)
{
    jlong start, now;
    int remaining = a.dp_timeout;
    struct timeval t;
    int diff;

    gettimeofday(&t, NULL);
    start = t.tv_sec * 1000 + t.tv_usec / 1000;

    for (;;) {
        /*  poll(7d) ioctl does not return remaining count */
        int res = ioctl(wfd, dpctl, &a);
        if (res < 0 && errno == EINTR) {
            if (remaining >= 0) {
                gettimeofday(&t, NULL);
                now = t.tv_sec * 1000 + t.tv_usec / 1000;
                diff = now - start;
                remaining -= diff;
                if (diff < 0 || remaining <= 0) {
                    return 0;
                }
                start = now;
            }
        } else {
            return res;
        }
    }
}

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
    int result = 0;

    a.dp_fds = pfd;
    a.dp_nfds = numfds;
    a.dp_timeout = (int)timeout;

    if (timeout <= 0) {             /* Indefinite or no wait */
        RESTARTABLE (ioctl(wfd, DP_POLL, &a), result);
    } else {                        /* Bounded wait; bounded restarts */
        result = idevpoll(wfd, DP_POLL, a);
    }

    if (result < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "Error reading driver");
        return -1;
    }
    return result;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_DevPollArrayWrapper_interrupt(JNIEnv *env, jclass this, jint fd)
{
    int fakebuf[1];
    fakebuf[0] = 1;
    if (write(fd, fakebuf, 1) < 0) {
        JNU_ThrowIOExceptionWithLastError(env,
                                          "Write to interrupt fd failed");
    }
}
