/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, IBM Corp.
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

#include <errno.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <poll.h>
#include <string.h>
#include <sys/ahafs_evProds.h>

#include "sun_nio_fs_AhafsPoller.h"

#define INVALID_WD -1
#define EVENT_BUFFER_SIZE 2048 // Change in sync with below 'BUF_SIZE' parameter
#define AHA_INIT_STR "CHANGED=YES WAIT_TYPE=WAIT_IN_SELECT BUF_SIZE=2048"
#define SIZEOF_AHA_INIT_STR sizeof(AHA_INIT_STR)

static void throwUnixException(JNIEnv* env, int errnum) {
    jobject x = JNU_NewObjectByName(env, "sun/nio/fs/UnixException",
        "(I)V", errnum);
    if (x != NULL) {
        (*env)->Throw(env, x);
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_AhafsPoller_nPollfdSize(JNIEnv *env, jclass clazz)
{
    return (jint)sizeof(struct pollfd);
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_AhafsPoller_nInit(JNIEnv* env, jclass clazz, jlong buf, jint buf_size, jintArray nv, jint socketfd)
{
    struct pollfd* fds = (struct pollfd*)jlong_to_ptr(buf);

    memset(fds, 0, buf_size);
    fds[0].fd = (int)socketfd;
    fds[0].events = POLLIN;

    int nfds[] = { 1 };

    (*env)->SetIntArrayRegion(env, nv, 0, 1, &nfds[0]);
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_AhafsPoller_nCloseAll(JNIEnv* env, jclass clazz, jlong buf, jint nfds)
{
    struct pollfd* fds = (struct pollfd*)jlong_to_ptr(buf);

    // First fd is assumed to be the socketpair, which will be cancelled
    // in Java. Skip it here.
    for (struct pollfd* pfd = fds+1; pfd <= fds + nfds; pfd++) {
        close(pfd->fd);
        pfd->events  = 0;
        pfd->revents = 0;
        pfd->fd = INVALID_WD;
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_AhafsPoller_nSocketpair
    (JNIEnv* env, jclass clazz, jintArray sv)
{
    int sp[2];
    if (socketpair(PF_UNIX, SOCK_STREAM, 0, sp) == -1) {
        perror("Socketpair error");
        throwUnixException(env, errno);
    } else {
        jint res[2];
        res[0] = (jint)sp[0];
        res[1] = (jint)sp[1];
        (*env)->SetIntArrayRegion(env, sv, 0, 2, &res[0]);
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_AhafsPoller_nRegisterMonitorPath
    (JNIEnv* env, jclass clazz, jlong buf, jint nxt_fd, jlong pathv)
{
    struct pollfd* fds = (struct pollfd*)jlong_to_ptr(buf);
    char* path = (char*)jlong_to_ptr(pathv);

    int fd = open(path, O_CREAT | O_RDWR);
    if (fd < 0) {
        fprintf(stderr,"[nRegisterMonitorPath] Fd invalid (%d) while opening %s\n", fd, path);
        perror("Open error");
        throwUnixException(env, errno);
        return INVALID_WD;
    }
    // Write AIX Event Infrastructure monitor args
    int wlen = write(fd, AHA_INIT_STR, SIZEOF_AHA_INIT_STR);
    if (wlen <= 0) {
        perror("Write error");
        throwUnixException(env, errno);
        return INVALID_WD;
    }

    fds[nxt_fd].fd = fd;
    fds[nxt_fd].events = POLLIN; // TODO: Are other event types important?

    return (jint)fd;
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_AhafsPoller_nCancelWatchDescriptor
    (JNIEnv* env, jclass clazz, jlong buf, jint nfds, jint wd)
{
    struct pollfd* fds = (struct pollfd*)jlong_to_ptr(buf);

    for (struct pollfd* pfd = fds; pfd != fds + nfds; pfd += 1) {
        if (pfd->fd == wd) {
            if (close(pfd->fd) != 0) {
                perror("Close error");
                fprintf(stderr,"[nCancelWatchDescriptor] Close returned error while closing %d\n", wd);
                throwUnixException(env, errno);
                break;
            }
            pfd->fd = INVALID_WD;
            pfd->events  = 0;
            pfd->revents = 0;
            return wd;
        }
    }

    return (jint)INVALID_WD;
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_AhafsPoller_nPoll
    (JNIEnv* env, jclass clazz, jlong fdsv, jint nfds, jint timeout, jlong evbufv, jint evbuf_size)
{
    char tmpbuf[evbuf_size];
    int evcnt;
    struct pollfd* fds = (struct pollfd*)jlong_to_ptr(fdsv);
    char*        evbuf = (char*)jlong_to_ptr(evbufv);

    // Poll for changes
    if ((evcnt = poll(fds, nfds, timeout)) < 0) {
        perror("Poll error");
        throwUnixException(env, errno);
        return (jint) evcnt;
    }

    // The first fd in fds is assumed to be the socketpair. Detect
    // when the event count has included a wakeup event and remove it here.
    if (fds->revents != 0) {
        fds->revents = 0;
        evcnt--;
    }

    // Iterate over fds (skipping the socketpair)
    for (struct pollfd* pfd = fds+1; pfd != fds + nfds; pfd += 1) {
        if (pfd->revents & POLLIN) {
            int rlen;
            if ((rlen = read(pfd->fd, tmpbuf, evbuf_size)) >= 0) {
                tmpbuf[rlen] = (char)NULL;
                // Wrap and write event data to provided buffer
                sprintf(evbuf, "BEGIN_WD=%d\n%sEND_WD=%d\n", pfd->fd, tmpbuf, pfd->fd);
            } else {
                perror("Read error");
                fprintf(stderr,"[nPoll] Read returned error while reading fd: %d. Got %s\n", pfd->fd, tmpbuf);
                throwUnixException(env, errno);
                break;
            }

            // strip revent data to prevent re-read of an old update.
            pfd->revents = 0;
        }
    }

    return (jint) evcnt;
}
