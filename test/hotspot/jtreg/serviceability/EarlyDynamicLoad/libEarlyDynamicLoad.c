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

#include <jvmti.h>

#include <errno.h>
#include <stdlib.h>
#include <stdio.h>
#ifdef WINDOWS
#include "process.h"
#define PID() _getpid()
#else
#include "unistd.h"
#define PID() getpid()
#endif // WINDOWS

static void JNICALL VMStartJcmd(jvmtiEnv* jvmti, JNIEnv* env) {
    char cmd[256];
    snprintf(cmd, sizeof(cmd), "%s %d JVMTI.agent_load some.jar", getenv("JCMD_PATH"), PID());
    int res = system(cmd);
    if (res == -1) {
        printf("jcmd call failed: %s\n", strerror(errno));
    } else {
        printf("jcmd result = %d\n", res);
    }
}

static void JNICALL VMStartAttach(jvmtiEnv* jvmti, JNIEnv* env) {
    char cmd[1024];
    snprintf(cmd, sizeof(cmd), "%s -cp %s AttachAgent %d", getenv("JAVA_PATH"), getenv("CLASSPATH"),
                                                           PID());
    int res = system(cmd);
    if (res == -1) {
        printf("attach call failed: %s\n", strerror(errno));
    } else {
        printf("attach result = %d\n", res);
    }
}

JNIEXPORT int Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
    jvmtiEnv* jvmti;
    if ((*vm)->GetEnv(vm, (void**)&jvmti, JVMTI_VERSION_1_0) != 0) {
        return 1;
    }

    jvmtiEventCallbacks callbacks = {0};
    if (getenv("JCMD_PATH") != NULL) {
        callbacks.VMStart = VMStartJcmd;
    } else {
        callbacks.VMStart = VMStartAttach;
    }

    (*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof(callbacks));
    (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_START, NULL);

    return 0;
}
