/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "sun_nio_ch_KQueue.h"

#include <strings.h>
#include <sys/types.h>
#include <sys/event.h>
#include <sys/time.h>

JNIEXPORT jint JNICALL
Java_sun_nio_ch_KQueue_keventSize(JNIEnv* env, jclass this)
{
    return sizeof(struct kevent);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_KQueue_identOffset(JNIEnv* env, jclass this)
{
    return offsetof(struct kevent, ident);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_KQueue_filterOffset(JNIEnv* env, jclass this)
{
    return offsetof(struct kevent, filter);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_KQueue_flagsOffset(JNIEnv* env, jclass this)
{
    return offsetof(struct kevent, flags);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_KQueue_kqueue(JNIEnv *env, jclass c) {
    int kqfd = kqueue();
    if (kqfd < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "kqueue failed");
    }
    return kqfd;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_KQueue_keventRegister(JNIEnv *env, jclass c, jint kqfd,
                                      jint fd, jint filter, jint flags)

{
    struct kevent changes[1];
    struct timespec timeout = {0, 0};
    int res;

    EV_SET(&changes[0], fd, filter, flags, 0, 0, 0);
    RESTARTABLE(kevent(kqfd, &changes[0], 1, NULL, 0, &timeout), res);
    return (res == -1) ? errno : 0;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_KQueue_keventPoll(JNIEnv *env, jclass c,
                                  jint kqfd, jlong address, jint nevents)
{
    struct kevent *events = jlong_to_ptr(address);
    int res;

    RESTARTABLE(kevent(kqfd, NULL, 0, events, nevents, NULL), res);
    if (res < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "kqueue failed");
    }
    return res;
}
