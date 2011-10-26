/*
 * Copyright (c) 2004, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>

#include "jni.h"
#include "jni_util.h"
#include "jlong.h"
#include "jvm.h"
#include "jdk_util.h"

#include "sun_misc_VM.h"

typedef jintArray (JNICALL *GET_THREAD_STATE_VALUES_FN)(JNIEnv *, jint);
typedef jobjectArray (JNICALL *GET_THREAD_STATE_NAMES_FN)(JNIEnv *, jint, jintArray);

static GET_THREAD_STATE_VALUES_FN GetThreadStateValues_fp = NULL;
static GET_THREAD_STATE_NAMES_FN GetThreadStateNames_fp = NULL;

static void get_thread_state_info(JNIEnv *env, jint state,
                                      jobjectArray stateValues,
                                      jobjectArray stateNames) {
    char errmsg[128];
    jintArray values;
    jobjectArray names;

    values = (*GetThreadStateValues_fp)(env, state);
    if (values == NULL) {
        sprintf(errmsg, "Mismatched VM version: Thread state (%d) "
                        "not supported", state);
        JNU_ThrowInternalError(env, errmsg);
        return;
    }
    /* state is also used as the index in the array */
    (*env)->SetObjectArrayElement(env, stateValues, state, values);

    names = (*GetThreadStateNames_fp)(env, state, values);
    if (names == NULL) {
        sprintf(errmsg, "Mismatched VM version: Thread state (%d) "
                        "not supported", state);
        JNU_ThrowInternalError(env, errmsg);
        return;
    }
    (*env)->SetObjectArrayElement(env, stateNames, state, names);
}

JNIEXPORT void JNICALL
Java_sun_misc_VM_getThreadStateValues(JNIEnv *env, jclass cls,
                                      jobjectArray values,
                                      jobjectArray names)
{
    char errmsg[128];

    // check if the number of Thread.State enum constants
    // matches the number of states defined in jvm.h
    jsize len1 = (*env)->GetArrayLength(env, values);
    jsize len2 = (*env)->GetArrayLength(env, names);
    if (len1 != JAVA_THREAD_STATE_COUNT || len2 != JAVA_THREAD_STATE_COUNT) {
        sprintf(errmsg, "Mismatched VM version: JAVA_THREAD_STATE_COUNT = %d "
                " but JDK expects %d / %d",
                JAVA_THREAD_STATE_COUNT, len1, len2);
        JNU_ThrowInternalError(env, errmsg);
        return;
    }

    if (GetThreadStateValues_fp == NULL) {
        GetThreadStateValues_fp = (GET_THREAD_STATE_VALUES_FN)
            JDK_FindJvmEntry("JVM_GetThreadStateValues");
        if (GetThreadStateValues_fp == NULL) {
            JNU_ThrowInternalError(env,
                 "Mismatched VM version: JVM_GetThreadStateValues not found");
            return;
        }

        GetThreadStateNames_fp = (GET_THREAD_STATE_NAMES_FN)
            JDK_FindJvmEntry("JVM_GetThreadStateNames");
        if (GetThreadStateNames_fp == NULL) {
            JNU_ThrowInternalError(env,
                 "Mismatched VM version: JVM_GetThreadStateNames not found");
            return ;
        }
    }

    get_thread_state_info(env, JAVA_THREAD_STATE_NEW, values, names);
    get_thread_state_info(env, JAVA_THREAD_STATE_RUNNABLE, values, names);
    get_thread_state_info(env, JAVA_THREAD_STATE_BLOCKED, values, names);
    get_thread_state_info(env, JAVA_THREAD_STATE_WAITING, values, names);
    get_thread_state_info(env, JAVA_THREAD_STATE_TIMED_WAITING, values, names);
    get_thread_state_info(env, JAVA_THREAD_STATE_TERMINATED, values, names);
}

JNIEXPORT jobject JNICALL
Java_sun_misc_VM_latestUserDefinedLoader(JNIEnv *env, jclass cls) {
    return JVM_LatestUserDefinedLoader(env);
}

typedef void (JNICALL *GetJvmVersionInfo_fp)(JNIEnv*, jvm_version_info*, size_t);

JNIEXPORT void JNICALL
Java_sun_misc_VM_initialize(JNIEnv *env, jclass cls) {
    GetJvmVersionInfo_fp func_p;

    if (!JDK_InitJvmHandle()) {
        JNU_ThrowInternalError(env, "Handle for JVM not found for symbol lookup");
        return;
    }

    func_p = (GetJvmVersionInfo_fp) JDK_FindJvmEntry("JVM_GetVersionInfo");
     if (func_p != NULL) {
        jvm_version_info info;

        memset(&info, 0, sizeof(info));

        /* obtain the JVM version info */
        (*func_p)(env, &info, sizeof(info));
    }
}

