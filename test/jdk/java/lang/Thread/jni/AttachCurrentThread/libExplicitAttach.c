/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include <stdio.h>
#include <pthread.h>
#include "jni.h"

#define STACK_SIZE 0x100000

/**
 * Attach the current thread with JNI AttachCurrentThread, call a method, and detach.
 */
void* thread_main(void* arg) {
    JavaVM *vm;
    JNIEnv *env;
    JavaVMInitArgs vm_args;
    jsize count;
    jint res;

    res = JNI_GetCreatedJavaVMs(&vm, 1, &count);
    if (res != JNI_OK) {
        fprintf(stderr, "JNI_GetCreatedJavaVMs failed: %d\n", res);
        return NULL;
    }

    res = (*vm)->AttachCurrentThread(vm, (void **) &env, NULL);
    if (res != JNI_OK) {
        fprintf(stderr, "AttachCurrentThreadAsDaemon failed: %d\n", res);
        return NULL;
    }

    // call ExplicitAttach.callback()
    jclass clazz = (*env)->FindClass(env, "ExplicitAttach");
    if (clazz == NULL) {
        fprintf(stderr, "FindClass failed\n");
        goto detach;
    }
    jmethodID mid = (*env)->GetStaticMethodID(env, clazz, "callback", "()V");
    if (mid == NULL) {
        fprintf(stderr, "GetStaticMethodID failed\n");
        goto detach;
    }
    (*env)->CallStaticVoidMethod(env, clazz, mid);
    if ((*env)->ExceptionOccurred(env)) {
        fprintf(stderr, "CallStaticVoidMethod failed\n");
        goto detach;
    }

  detach:
    res = (*vm)->DetachCurrentThread(vm);
    if (res != JNI_OK) {
        fprintf(stderr, "DetachCurrentThread failed: %d\n", res);
    }

    return NULL;
}

JNIEXPORT void JNICALL Java_ExplicitAttach_startThreads(JNIEnv *env, jclass clazz, int n) {
    pthread_t tid;
    pthread_attr_t attr;
    int i;

    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, STACK_SIZE);
    for (i = 0; i < n ; i++) {
        int res = pthread_create(&tid, &attr, thread_main, NULL);
        if (res != 0) {
            fprintf(stderr, "pthread_create failed: %d\n", res);
        }
    }
}
