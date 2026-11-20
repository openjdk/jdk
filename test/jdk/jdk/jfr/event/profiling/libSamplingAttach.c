/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#include <pthread.h>
#include <stdio.h>
#include "jni.h"

static void* error(const char* msg) {
    fprintf(stderr, "%s\n", msg);
    return NULL;
}

static void* attach_thread(void* arg) {
    JavaVM* vm = (JavaVM*)arg;
    JNIEnv* env;
    JavaVMAttachArgs args = { JNI_VERSION_1_8, "AttachedThread", NULL };

    if ((*vm)->AttachCurrentThreadAsDaemon(vm, (void**)&env, &args) != JNI_OK) {
        return error("AttachCurrentThreadAsDaemon failed");
    }

    jclass cls = (*env)->FindClass(env, "jdk/jfr/event/profiling/TestCPUTimeSamplingJNIAttach");
    if (cls == NULL) {
        return error("FindClass failed");
    }
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "busyLoop", "()V");
    if (mid == NULL) {
        return error("GetStaticMethodID failed");
    }
    (*env)->CallStaticVoidMethod(env, cls, mid);

    (*vm)->DetachCurrentThread(vm);
    return NULL;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    pthread_t t;
    if (pthread_create(&t, NULL, attach_thread, vm) != 0) {
        perror("pthread_create");
        return JNI_ERR;
    }
    return JNI_VERSION_1_8;
}
