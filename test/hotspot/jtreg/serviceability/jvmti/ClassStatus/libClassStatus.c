/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jvmti.h"

#ifdef __cplusplus
extern "C" {
#endif

#ifndef JNI_ENV_ARG

#ifdef __cplusplus
#define JNI_ENV_ARG(x, y) y
#define JNI_ENV_PTR(x) x
#else
#define JNI_ENV_ARG(x,y) x, y
#define JNI_ENV_PTR(x) (*x)
#endif

#endif

#define PASSED 0
#define FAILED 2

static jvmtiEnv* jvmti = NULL;

static jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved);

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *jvm, void *reserved) {
    return JNI_VERSION_9;
}

static void
check_jvmti_error(jvmtiEnv *jvmti, char* fname, jvmtiError err) {
    if (err != JVMTI_ERROR_NONE) {
        printf("  ## %s error: %d\n", fname, err);
        fflush(0);
        exit(err);
    }
}

static char*
get_class_signature(jvmtiEnv *jvmti, jclass klass) {
    char* sign = NULL;
    jvmtiError err = (*jvmti)->GetClassSignature(jvmti, klass, &sign, NULL);

    check_jvmti_error(jvmti, "GetClassSignature", err);
    return sign;
}

static jboolean
is_class_status_prepared(jvmtiEnv *jvmti, jclass klass) {
    char* sign = get_class_signature(jvmti, klass);
    jint status = 0;
    jvmtiError err = (*jvmti)->GetClassStatus(jvmti, klass, &status);

    check_jvmti_error(jvmti, "GetClassStatus", err);
    printf("    Class %s status: 0x%08x\n", sign, status);
    printf("    Class %s is prepared: %d\n", sign, (status & JVMTI_CLASS_STATUS_PREPARED) != 0);
    fflush(0);

    return (status & JVMTI_CLASS_STATUS_PREPARED) != 0;
}

static jboolean
is_class_in_loaded_classes(JNIEnv *env, jclass klass) {
    char* sign = get_class_signature(jvmti, klass);
    jint class_count = 0;
    jclass* classes = NULL;
    jvmtiError err = (*jvmti)->GetLoadedClasses(jvmti, &class_count, &classes);

    check_jvmti_error(jvmti, "GetLoadedClasses", err);

    for (int i = 0; i < class_count; i++) {
        jclass cls = classes[i];
        jboolean same = (*env)->IsSameObject(env, cls, klass);
        if (same) {
            printf("Found class %s in the list of loaded classes\n", sign);
            fflush(0);
            return JNI_TRUE;
        }
    }
    printf("Error: Have not found class %s in the list of loaded classes\n", sign);
    fflush(0);
    return JNI_FALSE;
}

static void JNICALL
ClassPrepare(jvmtiEnv *jvmti, JNIEnv *env, jthread thread, jclass klass) {
    char* sign = get_class_signature(jvmti, klass);

    sign = (sign == NULL) ? "NULL" : sign;

    if (strcmp(sign, "LFoo2;") == 0 || strcmp(sign, "LFoo3;") == 0) {
        printf("ClassPrepare event for class: %s\n", sign);
        fflush(0);
    }
}

static jint
Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jvmtiError err;
    jint size;
    jint res;
    jvmtiEventCallbacks callbacks;

    printf("Agent_Initialize started\n");
    fflush(0);
    res = JNI_ENV_PTR(jvm)->GetEnv(JNI_ENV_ARG(jvm, (void **) &jvmti), JVMTI_VERSION_9);
    if (res != JNI_OK || jvmti == NULL) {
        printf("## Agent_Initialize: Error in GetEnv: res: %d, jvmti env: %p\n", res, jvmti);
        return JNI_ERR;
    }

    size = (jint)sizeof(callbacks);
    memset(&callbacks, 0, size);
    callbacks.ClassPrepare = ClassPrepare;

    err = (*jvmti)->SetEventCallbacks(jvmti, &callbacks, size);
    check_jvmti_error(jvmti, "## Agent_Initialize: SetEventCallbacks", err);

    err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);
    check_jvmti_error(jvmti, "## Agent_Initialize: SetEventNotificationMode CLASS_PREPARE", err);
    return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_ClassStatus_check(JNIEnv *env, jclass cls, jclass klass) {
    if (is_class_in_loaded_classes(env, klass) != JNI_TRUE ||
        is_class_status_prepared(jvmti, klass)  != JNI_TRUE) {
        return FAILED;
    }
    return PASSED;
}

#ifdef __cplusplus
}
#endif
