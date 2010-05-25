/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

#include "sun_nio_ch_EPollPort.h"

#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>

JNIEXPORT void JNICALL
Java_sun_nio_ch_EPollPort_socketpair(JNIEnv* env, jclass clazz, jintArray sv) {
    int sp[2];
    if (socketpair(PF_UNIX, SOCK_STREAM, 0, sp) == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "socketpair failed");
    } else {
        jint res[2];
        res[0] = (jint)sp[0];
        res[1] = (jint)sp[1];
        (*env)->SetIntArrayRegion(env, sv, 0, 2, &res[0]);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_EPollPort_interrupt(JNIEnv *env, jclass c, jint fd) {
    int res;
    int buf[1];
    buf[0] = 1;
    RESTARTABLE(write(fd, buf, 1), res);
    if (res < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "write failed");
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_EPollPort_drain1(JNIEnv *env, jclass cl, jint fd) {
    int res;
    char buf[1];
    RESTARTABLE(read(fd, buf, 1), res);
    if (res < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "drain1 failed");
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_EPollPort_close0(JNIEnv *env, jclass c, jint fd) {
    int res;
    RESTARTABLE(close(fd), res);
}
