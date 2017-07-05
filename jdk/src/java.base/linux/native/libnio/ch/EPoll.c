/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "nio_util.h"

#include "sun_nio_ch_EPoll.h"

#include <dlfcn.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/epoll.h>

JNIEXPORT jint JNICALL
Java_sun_nio_ch_EPoll_eventSize(JNIEnv* env, jclass this)
{
    return sizeof(struct epoll_event);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_EPoll_eventsOffset(JNIEnv* env, jclass this)
{
    return offsetof(struct epoll_event, events);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_EPoll_dataOffset(JNIEnv* env, jclass this)
{
    return offsetof(struct epoll_event, data);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_EPoll_epollCreate(JNIEnv *env, jclass c) {
    /*
     * epoll_create expects a size as a hint to the kernel about how to
     * dimension internal structures. We can't predict the size in advance.
     */
    int epfd = epoll_create(256);
    if (epfd < 0) {
       JNU_ThrowIOExceptionWithLastError(env, "epoll_create failed");
    }
    return epfd;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_EPoll_epollCtl(JNIEnv *env, jclass c, jint epfd,
                                   jint opcode, jint fd, jint events)
{
    struct epoll_event event;
    int res;

    event.events = events;
    event.data.fd = fd;

    RESTARTABLE(epoll_ctl(epfd, (int)opcode, (int)fd, &event), res);

    return (res == 0) ? 0 : errno;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_EPoll_epollWait(JNIEnv *env, jclass c,
                                    jint epfd, jlong address, jint numfds)
{
    struct epoll_event *events = jlong_to_ptr(address);
    int res;

    RESTARTABLE(epoll_wait(epfd, events, numfds, -1), res);
    if (res < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "epoll_wait failed");
    }
    return res;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_EPoll_close0(JNIEnv *env, jclass c, jint epfd) {
    int res;
    RESTARTABLE(close(epfd), res);
}
