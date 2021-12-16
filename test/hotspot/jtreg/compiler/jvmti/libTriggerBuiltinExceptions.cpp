/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmti.h"

static jint exceptions_caught = 0;

extern "C" {

void JNICALL
callbackException(jvmtiEnv *jvmti_env, JNIEnv* jni_env,
                  jthread thread, jmethodID method,
                  jlocation location, jobject exception,
                  jmethodID catch_method, jlocation catch_location) {
    exceptions_caught += 1;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
    jvmtiEnv *jvmti = nullptr;

    jint result = JNI_OK;
    result = jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION);
    if (result != JNI_OK) {
        printf("Agent_OnLoad: Error in GetEnv in obtaining jvmtiEnv: %d\n", result);
        return JNI_ERR;
    }

    jvmtiEventCallbacks callbacks;
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.Exception = &callbackException;

    result = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
    if (result != JVMTI_ERROR_NONE) {
        printf("Agent_OnLoad: Error in JVMTI SetEventCallbacks: %d\n", result);
        return JNI_ERR;
    }

    jvmtiCapabilities capabilities;
    memset(&capabilities, 0, sizeof(capabilities));
    capabilities.can_generate_exception_events = 1;
    result = jvmti->AddCapabilities(&capabilities);
    if (result != JVMTI_ERROR_NONE) {
        printf("Agent_OnLoad: Error in JVMTI AddCapabilities: %d\n", result);
        return JNI_ERR;
    }

    result = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_EXCEPTION, (jthread)NULL);
    if (result != JVMTI_ERROR_NONE) {
        printf("Agent_OnLoad: Error in JVMTI SetEventNotificationMode: %d\n", result);
        return JNI_ERR;
    }

    return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_compiler_jvmti_TriggerBuiltinExceptionsTest_caughtByJVMTIAgent(JNIEnv *env, jclass cls) {
    return exceptions_caught;
}


} // extern "C"
