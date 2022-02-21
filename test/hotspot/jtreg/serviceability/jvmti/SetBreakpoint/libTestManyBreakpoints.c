/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

#define SUBCLASS_NAME "Lserviceability/jvmti/SetBreakpoint/TestManyBreakpoints$Target"

static jvmtiEnv *jvmti = NULL;

void JNICALL classprepare(jvmtiEnv* jvmti_env, JNIEnv* jni_env, jthread thread, jclass klass) {
    char* buf;
    (*jvmti)->GetClassSignature(jvmti, klass, &buf, NULL);
    if (strncmp(buf, SUBCLASS_NAME, strlen(SUBCLASS_NAME)) == 0) {
        jint nMethods;
        jmethodID* methods;
        int i;

        (*jvmti)->GetClassMethods(jvmti, klass, &nMethods, &methods);
        printf("Setting breakpoints in %s\n", buf);
        fflush(stdout);
        for (i = 0; i < nMethods; i++) {
            (*jvmti)->SetBreakpoint(jvmti, methods[i], 0);
        }
    }
}


void JNICALL breakpoint(jvmtiEnv* jvmti_env, JNIEnv* jni_env, jthread thread, jmethodID method, jlocation location) {
   // Do nothing
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
    jvmtiCapabilities capa;
    jvmtiEventCallbacks cbs = {0};

    (*vm)->GetEnv(vm, (void**)&jvmti, JVMTI_VERSION_1_0);

    memset(&capa, 0, sizeof(capa));
    capa.can_generate_breakpoint_events = 1;
    capa.can_generate_single_step_events = 1;
    (*jvmti)->AddCapabilities(jvmti, &capa);

    cbs.ClassPrepare = classprepare;
    cbs.Breakpoint = breakpoint;
    (*jvmti)->SetEventCallbacks(jvmti, &cbs, sizeof(cbs));
    (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);
    (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, NULL);

    return 0;
}
