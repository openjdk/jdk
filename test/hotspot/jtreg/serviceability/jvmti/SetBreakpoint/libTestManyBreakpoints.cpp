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

#include <string.h>
#include <stdio.h>

#include "jvmti.h"

#define TARGET_CLASS_NAME "LTarget;"

static jvmtiEnv *jvmti = NULL;

static void
check_jvmti_status(JNIEnv* jni, jvmtiError err, const char* msg) {
  if (err != JVMTI_ERROR_NONE) {
    printf("check_jvmti_status: %s, JVMTI function returned error: %d\n", msg, err);
    jni->FatalError(msg);
  }
}

void JNICALL classprepare(jvmtiEnv* jvmti_env, JNIEnv* jni_env, jthread thread, jclass klass) {
    char* buf;
    jvmtiError err;

    err = jvmti->GetClassSignature(klass, &buf, NULL);
    check_jvmti_status(jni_env, err, "classprepare: GetClassSignature error");

    if (strncmp(buf, TARGET_CLASS_NAME, strlen(TARGET_CLASS_NAME)) == 0) {
        jint nMethods;
        jmethodID* methods;
        int i;

        err = jvmti->GetClassMethods(klass, &nMethods, &methods);
        check_jvmti_status(jni_env, err, "classprepare: GetClassMethods error");
        printf("Setting breakpoints in %s\n", buf);
        fflush(stdout);
        for (i = 0; i < nMethods; i++) {
            err = jvmti->SetBreakpoint(methods[i], 0);
            check_jvmti_status(jni_env, err, "classprepare: SetBreakpoint error");
        }
    }
}


void JNICALL breakpoint(jvmtiEnv* jvmti_env, JNIEnv* jni_env, jthread thread, jmethodID method, jlocation location) {
   // Do nothing
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
    jvmtiCapabilities capa;
    jvmtiEventCallbacks cbs;
    jint err;

    err = vm->GetEnv((void**)&jvmti, JVMTI_VERSION_1_0);
    if (err != JNI_OK) {
        printf("Agent_OnLoad: GetEnv error\n");
        return JNI_ERR;
    }

    memset(&capa, 0, sizeof(capa));
    capa.can_generate_breakpoint_events = 1;
    capa.can_generate_single_step_events = 1;
    err = jvmti->AddCapabilities(&capa);
    if (err != JNI_OK) {
        printf("Agent_OnLoad: AddCapabilities error\n");
        return JNI_ERR;
    }

    memset(&cbs, 0, sizeof(cbs));
    cbs.ClassPrepare = classprepare;
    cbs.Breakpoint = breakpoint;
    err = jvmti->SetEventCallbacks(&cbs, sizeof(cbs));
    if (err != JNI_OK) {
        printf("Agent_OnLoad: SetEventCallbacks error\n");
        return JNI_ERR;
    }

    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);
    if (err != JNI_OK) {
        printf("Agent_OnLoad: SetEventNotificationMode CLASS_PREPARE error\n");
        return JNI_ERR;
    }

    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, NULL);
    if (err != JNI_OK) {
        printf("Agent_OnLoad: SetEventNotificationMode BREAKPOINT error\n");
        return JNI_ERR;
    }

    return JNI_OK;
}
