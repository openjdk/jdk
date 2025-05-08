/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

static JavaVM *vm;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void* reserved) {
    vm = jvm;
    return JNI_VERSION_1_8;
}

/**
 * Invokes JNIMutator.getObject()
 */
jobject getObject(JNIEnv* env) {
    jclass clazz = (*env)->FindClass(env, "JNIMutator");
    if (clazz == NULL) {
        fprintf(stderr, "FindClass failed\n");
        return NULL;
    }
    jmethodID mid = (*env)->GetStaticMethodID(env, clazz, "getObject", "()Ljava/lang/Object;");
    if (mid == NULL) {
        fprintf(stderr, "GetMethodID for getObject failed\n");
        return NULL;
    }
    jobject obj = (*env)->CallStaticObjectMethod(env, clazz, mid);
    if (obj == NULL) {
        fprintf(stderr, "CallObjectMethod to getObject failed\n");
        return NULL;
    }
    return obj;
}

/**
 * Invokes JNIMutator.getField()
 */
jobject getField(JNIEnv* env) {
    jclass clazz = (*env)->FindClass(env, "JNIMutator");
    if (clazz == NULL) {
        fprintf(stderr, "FindClass failed\n");
        return NULL;
    }
    jmethodID mid = (*env)->GetStaticMethodID(env, clazz, "getField", "()Ljava/lang/reflect/Field;");
    if (mid == NULL) {
        fprintf(stderr, "GetStaticMethodID for getField failed\n");
        return NULL;
    }
    jobject obj = (*env)->CallStaticObjectMethod(env, clazz, mid);
    if (obj == NULL) {
        fprintf(stderr, "CallObjectMethod to getField failed\n");
        return NULL;
    }
    return obj;
}

/**
 * Invokes Field.setInt
 */
jboolean setInt(JNIEnv* env, jobject obj, jobject fieldObj, jint newValue) {
    jclass fieldClass = (*env)->GetObjectClass(env, fieldObj);
    jmethodID mid = (*env)->GetMethodID(env, fieldClass, "setInt", "(Ljava/lang/Object;I)V");
    if (mid == NULL) {
        fprintf(stderr, "GetMethodID for Field.setInt failed\n");
        return JNI_FALSE;
    }
    (*env)->CallObjectMethod(env, fieldObj, mid, obj, newValue);
    return JNI_TRUE;
}

/**
 * Invokes JNIMutator.finish
 */
void finish(JNIEnv* env, jthrowable ex) {
    jclass clazz = (*env)->FindClass(env, "JNIMutator");
    if (clazz == NULL) {
        fprintf(stderr, "FindClass failed\n");
        return;
    }

    // invoke finish
    jmethodID mid = (*env)->GetStaticMethodID(env, clazz, "finish", "(Ljava/lang/Throwable;)V");
    if (mid == NULL) {
        fprintf(stderr, "GetStaticMethodID failed\n");
        return;
    }
    (*env)->CallStaticVoidMethod(env, clazz, mid, ex);
    if ((*env)->ExceptionOccurred(env)) {
        fprintf(stderr, "CallStaticVoidMethod failed\n");
    }
}

/**
 * Attach the current thread with JNI AttachCurrentThread.
 */
void* thread_main(void* arg) {
    JNIEnv *env;
    jint res;
    jthrowable ex;

    res = (*vm)->AttachCurrentThread(vm, (void **) &env, NULL);
    if (res != JNI_OK) {
        fprintf(stderr, "AttachCurrentThread failed: %d\n", res);
        return NULL;
    }

    // invoke JNIMutator.getObject to get the object to test
    jobject obj = getObject(env);
    if (obj == NULL) {
        goto done;
    }

    // invoke JNIMutator.getField to get the Field object with access enabled
    jobject fieldObj = getField(env);
    if (fieldObj == NULL) {
        goto done;
    }

    // invoke Field.setInt to attempt to set the value to 200
    if (!setInt(env, obj, fieldObj, 200)) {
        goto done;
    }

  done:

    ex = (*env)->ExceptionOccurred(env);
    if (ex != NULL) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    finish(env, ex);

    res = (*vm)->DetachCurrentThread(vm);
    if (res != JNI_OK) {
        fprintf(stderr, "DetachCurrentThread failed: %d\n", res);
    }

    return NULL;
}

JNIEXPORT void JNICALL Java_JNIMutator_startThread(JNIEnv *env, jclass clazz) {
    pthread_t tid;
    pthread_attr_t attr;

    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, STACK_SIZE);
    int res = pthread_create(&tid, &attr, thread_main, NULL);
    if (res != 0) {
        fprintf(stderr, "pthread_create failed: %d\n", res);
    }
}
