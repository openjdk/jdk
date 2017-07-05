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

#include <stdlib.h>
#include <dlfcn.h>
#include <sys/types.h>
#include <port.h>       // Solaris 10

#include "sun_nio_fs_SolarisWatchService.h"

static void throwUnixException(JNIEnv* env, int errnum) {
    jobject x = JNU_NewObjectByName(env, "sun/nio/fs/UnixException",
        "(I)V", errnum);
    if (x != NULL) {
        (*env)->Throw(env, x);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_SolarisWatchService_init(JNIEnv *env, jclass clazz)
{
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_SolarisWatchService_portCreate
    (JNIEnv* env, jclass clazz)
{
    int port = port_create();
    if (port == -1) {
        throwUnixException(env, errno);
    }
    return (jint)port;
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_SolarisWatchService_portAssociate
    (JNIEnv* env, jclass clazz, jint port, jint source, jlong objectAddress, jint events)
{
    uintptr_t object = (uintptr_t)jlong_to_ptr(objectAddress);

    if (port_associate((int)port, (int)source, object, (int)events, NULL) == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_SolarisWatchService_portDissociate
    (JNIEnv* env, jclass clazz, jint port, jint source, jlong objectAddress)
{
    uintptr_t object = (uintptr_t)jlong_to_ptr(objectAddress);

    if (port_dissociate((int)port, (int)source, object) == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_SolarisWatchService_portSend(JNIEnv* env, jclass clazz,
    jint port, jint events)
{
    if (port_send((int)port, (int)events, NULL) == -1) {
        throwUnixException(env, errno);
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_SolarisWatchService_portGetn(JNIEnv* env, jclass clazz,
    jint port, jlong arrayAddress, jint max)
{
    uint_t n = 1;
    port_event_t* list = (port_event_t*)jlong_to_ptr(arrayAddress);

    if (port_getn((int)port, list, (uint_t)max, &n, NULL) == -1) {
        throwUnixException(env, errno);
    }
    return (jint)n;
}
