/*
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <dlfcn.h>
#include <sys/types.h>
#include <port.h>

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include "nio.h"
#include "nio_util.h"

#include "sun_nio_ch_SolarisEventPort.h"

JNIEXPORT jint JNICALL
Java_sun_nio_ch_SolarisEventPort_port_1create
    (JNIEnv* env, jclass clazz)
{
    int port = port_create();
    if (port == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "port_create");
    }
    return (jint)port;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_SolarisEventPort_port_1close
    (JNIEnv* env, jclass clazz, jint port)
{
    int res = close(port);
    if (res < 0 && res != EINTR) {
        JNU_ThrowIOExceptionWithLastError(env, "close failed");
    }
}

JNIEXPORT jboolean JNICALL
Java_sun_nio_ch_SolarisEventPort_port_1associate
    (JNIEnv* env, jclass clazz, jint port, jint source, jlong objectAddress, jint events)
{
    uintptr_t object = (uintptr_t)jlong_to_ptr(objectAddress);
    if (port_associate((int)port, (int)source, object, (int)events, NULL) == 0) {
        return JNI_TRUE;
    } else {
        if (errno != EBADFD)
            JNU_ThrowIOExceptionWithLastError(env, "port_associate");
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_sun_nio_ch_SolarisEventPort_port_1dissociate
    (JNIEnv* env, jclass clazz, jint port, jint source, jlong objectAddress)
{
    uintptr_t object = (uintptr_t)jlong_to_ptr(objectAddress);

    if (port_dissociate((int)port, (int)source, object) == 0) {
        return JNI_TRUE;
    } else {
        if (errno != ENOENT)
            JNU_ThrowIOExceptionWithLastError(env, "port_dissociate");
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_SolarisEventPort_port_1send(JNIEnv* env, jclass clazz,
    jint port, jint events)
{
    if (port_send((int)port, (int)events, NULL) == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "port_send");
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_SolarisEventPort_port_1get(JNIEnv* env, jclass clazz,
    jint port, jlong eventAddress)
{
    int res;
    port_event_t* ev = (port_event_t*)jlong_to_ptr(eventAddress);

    res = port_get((int)port, ev, NULL);
    if (res == -1) {
        if (errno == EINTR) {
            return IOS_INTERRUPTED;
        } else {
            JNU_ThrowIOExceptionWithLastError(env, "port_get failed");
            return IOS_THROWN;
        }
    }
    return res;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_SolarisEventPort_port_1getn(JNIEnv* env, jclass clazz,
    jint port, jlong arrayAddress, jint max, jlong timeout)
{
    int res;
    uint_t n = 1;
    port_event_t* list = (port_event_t*)jlong_to_ptr(arrayAddress);
    timespec_t ts;
    timespec_t* tsp;

    if (timeout >= 0L) {
        ts.tv_sec = timeout / 1000;
        ts.tv_nsec = 1000000 * (timeout % 1000);
        tsp = &ts;
    } else {
        tsp = NULL;
    }

    res = port_getn((int)port, list, (uint_t)max, &n, tsp);
    if (res == -1 && errno != ETIME) {
        if (errno == EINTR) {
            return IOS_INTERRUPTED;
        } else {
            JNU_ThrowIOExceptionWithLastError(env, "port_getn failed");
            return IOS_THROWN;
        }
    }

    return (jint)n;
}
