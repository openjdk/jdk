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

#include <string.h>
#include <stdio.h>
#include "jvmti.h"

static jvmtiEnv* jvmti = nullptr;
static jvmtiExtensionFunction RequestStackTrace_func = nullptr;
static jvmtiExtensionFunction EnableRequestStackTrace_func = nullptr;
static jvmtiExtensionFunction DisableRequestStackTrace_func = nullptr;

static jvmtiExtensionFunction find_ext_function(const char* full_id) {
    jint extCount = 0;
    jvmtiExtensionFunctionInfo* extList = nullptr;
    jvmtiError err = jvmti->GetExtensionFunctions(&extCount, &extList);
    if (err != JVMTI_ERROR_NONE) {
        fprintf(stderr, "GetExtensionFunctions failed: %d\n", err);
        return nullptr;
    }
    for (int i = 0; i < extCount; i++) {
        if (strcmp(extList[i].id, full_id) == 0) {
            return extList[i].func;
        }
    }
    fprintf(stderr, "Extension function not found: %s\n", full_id);
    return nullptr;
}

extern "C" {

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
    if (jvm->GetEnv((void **)&jvmti, JVMTI_VERSION) != JNI_OK) {
        fprintf(stderr, "Failed to get JVMTI environment\n");
        return JNI_ERR;
    }

    RequestStackTrace_func = find_ext_function(
        "com.sun.hotspot.functions.RequestStackTrace");
    EnableRequestStackTrace_func = find_ext_function(
        "com.sun.hotspot.functions.EnableRequestStackTrace");
    DisableRequestStackTrace_func = find_ext_function(
        "com.sun.hotspot.functions.DisableRequestStackTrace");

    if (!RequestStackTrace_func || !EnableRequestStackTrace_func
            || !DisableRequestStackTrace_func) {
        fprintf(stderr, "Failed to find required extension functions\n");
        return JNI_ERR;
    }

    printf("JVMTI agent loaded successfully\n");
    fflush(stdout);
    return JNI_OK;
}

JNIEXPORT void JNICALL
Java_TestSamplerIndependence_enableJvmtiStackTrace(JNIEnv *env, jclass cls) {
    jvmtiError err = EnableRequestStackTrace_func(jvmti);
    if (err != JVMTI_ERROR_NONE) {
        fprintf(stderr, "EnableRequestStackTrace failed: %d\n", err);
    }
}

JNIEXPORT void JNICALL
Java_TestSamplerIndependence_disableJvmtiStackTrace(JNIEnv *env, jclass cls) {
    jvmtiError err = DisableRequestStackTrace_func(jvmti);
    if (err != JVMTI_ERROR_NONE) {
        fprintf(stderr, "DisableRequestStackTrace failed: %d\n", err);
    }
}

JNIEXPORT jint JNICALL
Java_TestSamplerIndependence_requestStackTrace(JNIEnv *env, jclass cls, jlong userData) {
    jvmtiError err = RequestStackTrace_func(jvmti,
        (jthread)nullptr, (void*)nullptr, (jlong)userData);
    return (jint)err;
}

} // extern "C"
